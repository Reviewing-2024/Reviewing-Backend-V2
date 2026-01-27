package my.reviewing.reviewing_V2.crawling.service.nomadcoders

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
import org.springframework.batch.item.ItemReader
import org.springframework.batch.item.ItemStreamException
import org.springframework.batch.item.ItemWriter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.PlatformTransactionManager

@Configuration
class NomadcodersCrawlingBatch(
    private val jobRepository: JobRepository,
    private val platformTransactionManager: PlatformTransactionManager,
    private val courseRepository: CourseRepository,
    private val platformRepository: PlatformRepository
) {

    private val log = LoggerFactory.getLogger(NomadcodersCrawlingBatch::class.java)

    @Bean
    fun nomadcodersJob(): Job {
        return JobBuilder("nomadcodersCrawlingJob", jobRepository)
            .start(nomadcodersStep())
            .build()
    }

    @Bean
    fun nomadcodersStep(): Step {
        return StepBuilder("nomadcodersCrawlingStep", jobRepository)
            .chunk<Course, Course>(5, platformTransactionManager)
            .reader(nomadcodersReader())
            .processor(nomadcodersProcessor())
            .writer(nomadcodersWriter())
            .faultTolerant()
            .retryLimit(5)
            .retry(ItemStreamException::class.java)
            .skipLimit(10)
            .skip(ItemStreamException::class.java)
            .build()
    }

    @Bean
    fun nomadcodersReader(): ItemReader<Course> {
        return NomadcodersReader(platformRepository)
    }

    @Bean
    fun nomadcodersProcessor(): ItemProcessor<Course, Course> {
        return ItemProcessor { course ->
            val existingCourse = courseRepository.findBySlug(course.slug)
            if (existingCourse != null) {
                log.debug("이미 존재하는 강의, 건너뛰기: {}", course.title)
                null  // 이미 존재하면 건너뛰기
            } else {
                log.debug("새 강의 저장 대상: {}", course.title)
                course  // 새 강의면 저장
            }
        }
    }

    @Bean
    fun nomadcodersWriter(): ItemWriter<Course> {
        return ItemWriter { courses ->
            log.info("{}개 강의 저장", courses.size())
            courseRepository.saveAll(courses)
        }
    }
}