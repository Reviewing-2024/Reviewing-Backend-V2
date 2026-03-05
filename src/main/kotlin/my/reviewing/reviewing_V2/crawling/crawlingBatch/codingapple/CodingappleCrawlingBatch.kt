package my.reviewing.reviewing_V2.crawling.crawlingBatch.codingapple

import my.reviewing.reviewing_V2.crawling.entity.Course
import my.reviewing.reviewing_V2.crawling.repository.CourseRepository
import my.reviewing.reviewing_V2.crawling.repository.PlatformRepository
import org.slf4j.LoggerFactory
import org.springframework.batch.core.Job
import org.springframework.batch.core.Step
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.item.ItemProcessor
import org.springframework.batch.item.ItemStreamException
import org.springframework.batch.item.ItemStreamReader
import org.springframework.batch.item.ItemWriter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.PlatformTransactionManager

/**
 * 코딩애플 크롤링 Batch 설정
 * 흐름:
 * 1. Reader: 단일 페이지 파싱 → Course 반환
 * 2. Processor: slug 기준 중복 체크
 * 3. Writer: Course 저장
 */
@Configuration
class CodingappleCrawlingBatch(
    private val jobRepository: JobRepository,
    private val platformTransactionManager: PlatformTransactionManager,
    private val courseRepository: CourseRepository,
    private val platformRepository: PlatformRepository
) {

    private val log = LoggerFactory.getLogger(CodingappleCrawlingBatch::class.java)

    @Bean
    fun codingappleJob(codingappleStep: Step): Job {
        return JobBuilder("codingappleCrawlingJob", jobRepository)
            .start(codingappleStep)
            .build()
    }

    @Bean
    fun codingappleStep(codingappleReader: ItemStreamReader<Course>): Step {
        return StepBuilder("codingappleCrawlingStep", jobRepository)
            .chunk<Course, Course>(5, platformTransactionManager)
            .reader(codingappleReader)
            .processor(codingappleProcessor())
            .writer(codingappleWriter())
            .faultTolerant()
            .retryLimit(3)
            .retry(ItemStreamException::class.java)
            .skipLimit(50)
            .skip(Exception::class.java)
            .build()
    }

    @Bean
    fun codingappleReader(): CodingappleReader {
        return CodingappleReader(platformRepository)
    }

    @Bean
    fun codingappleProcessor(): ItemProcessor<Course, Course> {
        return ItemProcessor { course ->
            val existing = courseRepository.findByPlatformAndSlug(course.platform, course.slug)
            if (existing != null) null else course
        }
    }

    @Bean
    fun codingappleWriter(): ItemWriter<Course> {
        return ItemWriter { courses ->
            log.info("{}개 강의 저장", courses.size())
            courseRepository.saveAll(courses)
        }
    }
}