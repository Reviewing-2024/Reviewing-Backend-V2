package my.reviewing.reviewing_V2.crawling.crawlingBatch.inflearn

import my.reviewing.reviewing_V2.crawling.dto.CrawlingCourseDto
import my.reviewing.reviewing_V2.crawling.entity.Course
import my.reviewing.reviewing_V2.crawling.entity.SubCategoryCourse
import my.reviewing.reviewing_V2.crawling.repository.CategoryRepository
import my.reviewing.reviewing_V2.crawling.repository.CourseRepository
import my.reviewing.reviewing_V2.crawling.repository.PlatformRepository
import my.reviewing.reviewing_V2.crawling.repository.SubCategoryCourseRepository
import my.reviewing.reviewing_V2.crawling.repository.SubCategoryRepository
import org.openqa.selenium.NoSuchElementException
import org.openqa.selenium.TimeoutException
import org.slf4j.LoggerFactory
import org.springframework.batch.core.Job
import org.springframework.batch.core.Step
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.item.ItemProcessor
import org.springframework.batch.item.ItemStreamException
import org.springframework.batch.item.ItemStreamReader
import org.springframework.batch.item.ItemWriter
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.PlatformTransactionManager

/**
 * 인프런 크롤링 Batch 설정
 *
 * 흐름:
 * 1. Reader: SubCategory별로 페이지네이션하며 강의 크롤링 → CrawlingCourseDto 반환
 * 2. Processor: 중복 체크 + SubCategoryCourse 매핑 처리 → SubCategoryCourse 반환
 * 3. Writer: Course 저장 + SubCategoryCourse 저장
 */
@Configuration
class InflearnCrawlingBatch(
    private val jobRepository: JobRepository,
    private val platformTransactionManager: PlatformTransactionManager,
    private val courseRepository: CourseRepository,
    private val platformRepository: PlatformRepository,
    private val categoryRepository: CategoryRepository,
    private val subCategoryRepository: SubCategoryRepository,
    private val subCategoryCourseRepository: SubCategoryCourseRepository
) {

    private val log = LoggerFactory.getLogger(InflearnCrawlingBatch::class.java)

    @Bean
    fun inflearnJob(inflearnStep: Step): Job {
        return JobBuilder("inflearnCrawlingJob", jobRepository)
            .start(inflearnStep)
            .build()
    }

    @Bean
    fun inflearnStep(inflearnReader: ItemStreamReader<CrawlingCourseDto>): Step {
        return StepBuilder("inflearnCrawlingStep", jobRepository)
            .chunk<CrawlingCourseDto, SubCategoryCourse>(20, platformTransactionManager)
            .reader(inflearnReader)
            .processor(inflearnProcessor())
            .writer(inflearnWriter())
            .faultTolerant()
            .retryLimit(5)
            .retry(ItemStreamException::class.java)
            .retry(NoSuchElementException::class.java)
            .retry(TimeoutException::class.java)
            .skipLimit(100)
            .skip(ItemStreamException::class.java)
            .skip(NoSuchElementException::class.java)
            .skip(TimeoutException::class.java)
            .build()
    }

    /**
     * 인프런 Reader (테스트용 제한 옵션 지원)
     *
     * JobParameters:
     * - maxCategories: 최대 카테고리 수 (기본값 0 = 무제한)
     * - maxSubCategories: 카테고리당 최대 서브카테고리 수 (기본값 0 = 무제한)
     * - maxPages: 서브카테고리당 최대 페이지 수 (기본값 0 = 무제한)
     */
    @Bean
    @StepScope
    fun inflearnReader(
        @Value("#{jobParameters['maxCategories'] ?: 0}") maxCategories: Long,
        @Value("#{jobParameters['maxSubCategories'] ?: 0}") maxSubCategories: Long,
        @Value("#{jobParameters['maxPages'] ?: 0}") maxPages: Long
    ): InflearnReader {
        return InflearnReader(
            platformRepository = platformRepository,
            categoryRepository = categoryRepository,
            subCategoryRepository = subCategoryRepository,
            maxCategories = maxCategories.toInt(),
            maxSubCategoriesPerCategory = maxSubCategories.toInt(),
            maxPagesPerSubCategory = maxPages.toInt()
        )
    }

    @Bean
    @StepScope
    fun inflearnProcessor(): ItemProcessor<CrawlingCourseDto, SubCategoryCourse> {
        val courseCache = HashMap<String, Course>()
        return ItemProcessor { dto ->
            val cacheKey = "${dto.platform.id}:${dto.courseSlug}"
            val existingCourse = courseCache[cacheKey]
                ?: courseRepository.findFirstByPlatformAndSlug(dto.platform, dto.courseSlug)
                    ?.also { courseCache[cacheKey] = it }

            val course: Course
            if (existingCourse != null) {
                course = existingCourse

                val existingMapping = subCategoryCourseRepository.findByCourseAndSubCategory(
                    course, dto.subCategory
                )
                if (existingMapping != null) {
                    return@ItemProcessor null
                }
            } else {
                course = Course(
                    platform = dto.platform,
                    title = dto.title,
                    url = dto.courseUrl,
                    slug = dto.courseSlug,
                    thumbnailImage = dto.thumbnailImage,
                    thumbnailVideo = dto.thumbnailVideo,
                    teacher = dto.teacher
                )
                courseCache[cacheKey] = course
            }

            SubCategoryCourse(
                course = course,
                subCategory = dto.subCategory
            )
        }
    }

    @Bean
    fun inflearnWriter(): ItemWriter<SubCategoryCourse> {
        return ItemWriter { items ->
            var newCourseCount = 0
            var newMappingCount = 0

            for (item in items) {
                // Course가 새로 생성된 경우 저장
                if (item.course.id == null) {
                    courseRepository.save(item.course)
                    newCourseCount++
                }

                // SubCategoryCourse 저장
                subCategoryCourseRepository.save(item)
                newMappingCount++
            }

            log.info("저장 완료 - 새 강의: {}개, 카테고리 매핑: {}개", newCourseCount, newMappingCount)
        }
    }
}
