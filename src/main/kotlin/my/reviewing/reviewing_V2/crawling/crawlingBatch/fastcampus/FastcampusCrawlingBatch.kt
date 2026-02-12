package my.reviewing.reviewing_V2.crawling.crawlingBatch.fastcampus

import my.reviewing.reviewing_V2.crawling.dto.CrawlingCourseDto
import my.reviewing.reviewing_V2.crawling.entity.Course
import my.reviewing.reviewing_V2.crawling.entity.SubCategoryCourse
import my.reviewing.reviewing_V2.crawling.repository.CategoryRepository
import my.reviewing.reviewing_V2.crawling.repository.CourseRepository
import my.reviewing.reviewing_V2.crawling.repository.PlatformRepository
import my.reviewing.reviewing_V2.crawling.repository.SubCategoryCourseRepository
import my.reviewing.reviewing_V2.crawling.repository.SubCategoryRepository
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
 * 패스트캠퍼스 크롤링 Batch 설정
 *
 * 흐름:
 * 1. Reader: SubCategory별 무한 스크롤 → CrawlingCourseDto 반환
 * 2. Processor: 중복 체크 + SubCategoryCourse 매핑
 * 3. Writer: Course 저장 + SubCategoryCourse 저장
 */
@Configuration
class FastcampusCrawlingBatch(
    private val jobRepository: JobRepository,
    private val platformTransactionManager: PlatformTransactionManager,
    private val courseRepository: CourseRepository,
    private val platformRepository: PlatformRepository,
    private val categoryRepository: CategoryRepository,
    private val subCategoryRepository: SubCategoryRepository,
    private val subCategoryCourseRepository: SubCategoryCourseRepository
) {

    private val log = LoggerFactory.getLogger(FastcampusCrawlingBatch::class.java)

    @Bean
    fun fastcampusJob(fastcampusStep: Step): Job {
        return JobBuilder("fastcampusCrawlingJob", jobRepository)
            .start(fastcampusStep)
            .build()
    }

    @Bean
    fun fastcampusStep(fastcampusReader: ItemStreamReader<CrawlingCourseDto>): Step {
        return StepBuilder("fastcampusCrawlingStep", jobRepository)
            .chunk<CrawlingCourseDto, SubCategoryCourse>(20, platformTransactionManager)
            .reader(fastcampusReader)
            .processor(fastcampusProcessor())
            .writer(fastcampusWriter())
            .faultTolerant()
            .retryLimit(3)
            .retry(ItemStreamException::class.java)
            .skipLimit(200)
            .skip(Exception::class.java)
            .build()
    }

    /**
     * 패스트캠퍼스 Reader (테스트용 제한 옵션 지원)
     *
     * JobParameters:
     * - maxCategories: 최대 카테고리 수 (기본값 0 = 무제한)
     * - maxSubCategories: 카테고리당 최대 서브카테고리 수 (기본값 0 = 무제한)
     */
    @Bean
    @StepScope
    fun fastcampusReader(
        @Value("#{jobParameters['maxCategories'] ?: 0}") maxCategories: Long,
        @Value("#{jobParameters['maxSubCategories'] ?: 0}") maxSubCategories: Long
    ): ItemStreamReader<CrawlingCourseDto> {
        return FastcampusReader(
            platformRepository = platformRepository,
            categoryRepository = categoryRepository,
            subCategoryRepository = subCategoryRepository,
            maxCategories = maxCategories.toInt(),
            maxSubCategoriesPerCategory = maxSubCategories.toInt()
        )
    }

    @Bean
    fun fastcampusProcessor(): ItemProcessor<CrawlingCourseDto, SubCategoryCourse> {
        return ItemProcessor { dto ->
            val existingCourse = courseRepository.findByPlatformAndSlug(dto.platform, dto.courseSlug)

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
                    thumbnailVideo = null,
                    teacher = dto.teacher
                )
            }

            SubCategoryCourse(
                course = course,
                subCategory = dto.subCategory
            )
        }
    }

    @Bean
    fun fastcampusWriter(): ItemWriter<SubCategoryCourse> {
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
