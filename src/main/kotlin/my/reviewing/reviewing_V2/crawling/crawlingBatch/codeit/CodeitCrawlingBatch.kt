package my.reviewing.reviewing_V2.crawling.crawlingBatch.codeit

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
 * 코드잇 크롤링 Batch 설정
 *
 * CrawlingCourseDto를 그대로 재사용 (thumbnailImage/Video = null, teacher = "")
 *
 * 흐름:
 * 1. Reader: SubCategory별로 페이지네이션하며 강의 크롤링 → CrawlingCourseDto 반환
 * 2. Processor: 중복 체크 + SubCategoryCourse 매핑 처리 → SubCategoryCourse 반환
 * 3. Writer: Course 저장 + SubCategoryCourse 저장
 */
@Configuration
class CodeitCrawlingBatch(
    @Value("\${cloud.aws.s3.base-url}") private val s3BaseUrl: String,
    private val jobRepository: JobRepository,
    private val platformTransactionManager: PlatformTransactionManager,
    private val courseRepository: CourseRepository,
    private val platformRepository: PlatformRepository,
    private val categoryRepository: CategoryRepository,
    private val subCategoryRepository: SubCategoryRepository,
    private val subCategoryCourseRepository: SubCategoryCourseRepository
) {

    private val log = LoggerFactory.getLogger(CodeitCrawlingBatch::class.java)

    @Bean
    fun codeitJob(codeitStep: Step): Job {
        return JobBuilder("codeitCrawlingJob", jobRepository)
            .start(codeitStep)
            .build()
    }

    @Bean
    fun codeitStep(codeitReader: ItemStreamReader<CrawlingCourseDto>): Step {
        return StepBuilder("codeitCrawlingStep", jobRepository)
            .chunk<CrawlingCourseDto, SubCategoryCourse>(20, platformTransactionManager)
            .reader(codeitReader)
            .processor(codeitProcessor())
            .writer(codeitWriter())
            .faultTolerant()
            .retryLimit(3)
            .retry(ItemStreamException::class.java)
            .retry(NoSuchElementException::class.java)
            .retry(TimeoutException::class.java)
            .skipLimit(200)
            .skip(Exception::class.java)
            .build()
    }

    /**
     * 코드잇 Reader (테스트용 제한 옵션 지원)
     *
     * JobParameters:
     * - maxCategories: 최대 카테고리 수 (기본값 0 = 무제한)
     * - maxSubCategories: 카테고리당 최대 서브카테고리 수 (기본값 0 = 무제한)
     * - maxPages: 서브카테고리당 최대 페이지 수 (기본값 0 = 무제한)
     */
    @Bean
    @StepScope
    fun codeitReader(
        @Value("#{jobParameters['maxCategories'] ?: 0}") maxCategories: Long,
        @Value("#{jobParameters['maxSubCategories'] ?: 0}") maxSubCategories: Long,
        @Value("#{jobParameters['maxPages'] ?: 0}") maxPages: Long
    ): CodeitReader {
        return CodeitReader(
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
    fun codeitProcessor(): ItemProcessor<CrawlingCourseDto, SubCategoryCourse> {
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
                    thumbnailImage = "$s3BaseUrl/lecture_thumbnail_codeit.png",
                    thumbnailVideo = null,
                    teacher = dto.teacher.ifEmpty { null }
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
    fun codeitWriter(): ItemWriter<SubCategoryCourse> {
        return ItemWriter { items ->
            var newCourseCount = 0
            var newMappingCount = 0

            for (item in items) {
                if (item.course.id == null) {
                    courseRepository.save(item.course)
                    newCourseCount++
                }
                subCategoryCourseRepository.save(item)
                newMappingCount++
            }

            log.info("저장 완료 - 새 강의: {}개, 카테고리 매핑: {}개", newCourseCount, newMappingCount)
        }
    }
}