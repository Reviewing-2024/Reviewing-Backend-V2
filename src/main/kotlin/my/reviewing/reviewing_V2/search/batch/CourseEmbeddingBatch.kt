package my.reviewing.reviewing_V2.search.batch

import co.elastic.clients.elasticsearch.ElasticsearchClient
import jakarta.persistence.EntityManagerFactory
import my.reviewing.reviewing_V2.crawling.entity.Course
import my.reviewing.reviewing_V2.crawling.repository.SubCategoryCourseRepository
import my.reviewing.reviewing_V2.search.dto.CourseDocument
import org.slf4j.LoggerFactory
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.batch.core.Job
import org.springframework.batch.core.Step
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.item.ItemWriter
import org.springframework.batch.item.database.JpaPagingItemReader
import org.springframework.batch.item.database.builder.JpaPagingItemReaderBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.PlatformTransactionManager

@Configuration
class CourseEmbeddingBatch(
    private val jobRepository: JobRepository,
    private val platformTransactionManager: PlatformTransactionManager,
    private val entityManagerFactory: EntityManagerFactory,
    private val embeddingModel: EmbeddingModel,
    private val elasticsearchClient: ElasticsearchClient,
    private val subCategoryCourseRepository: SubCategoryCourseRepository
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    fun courseEmbeddingJob(): Job {
        return JobBuilder("courseEmbeddingJob", jobRepository)
            .start(courseEmbeddingStep())
            .build()
    }

    @Bean
    fun courseEmbeddingStep(): Step {
        return StepBuilder("courseEmbeddingStep", jobRepository)
            .chunk<Course, Course>(10, platformTransactionManager)
            .reader(courseEmbeddingReader())
            .writer(courseEmbeddingWriter())
            .faultTolerant()
            .retryLimit(3)
            .retry(Exception::class.java)
            .skipLimit(100)
            .skip(Exception::class.java)
            .build()
    }

    @Bean
    fun courseEmbeddingReader(): JpaPagingItemReader<Course> {
        return  JpaPagingItemReaderBuilder<Course>()
            .name("courseEmbeddingReader")
            .entityManagerFactory(entityManagerFactory)
            .queryString("SELECT c FROM Course c ORDER BY c.createdAt ASC")
            .pageSize(10)
            .build()
    }

    @Bean
    fun courseEmbeddingWriter(): ItemWriter<Course> {
        return ItemWriter { items ->
            val courses = items.items

            val texts = courses.map { course ->
                val subCategoryCourses = subCategoryCourseRepository.findByCourse(course)
                val categories = subCategoryCourses.map { it.subCategory.category.name }.distinct()
                    .joinToString(", ")
                val subCategories =
                    subCategoryCourses.map { it.subCategory.name }.joinToString(", ")

                "Title: ${course.title}, Platform: ${course.platform.koreanName} ${course.platform.englishName}, Teacher: ${course.teacher ?: ""}, Category: $categories, SubCategory: $subCategories"
            }

            val embeddings = embeddingModel.embedForResponse(texts).results

            elasticsearchClient.bulk { bulk ->
                courses.forEachIndexed { i, course ->
                    bulk.operations { op ->
                        op.index { idx ->
                            idx.index("courses")
                                .id(course.id.toString())
                                .document(
                                    CourseDocument(
                                        id = course.id.toString(),
                                        platform = course.platform.englishName,
                                        title = course.title,
                                        teacher = course.teacher ?: "",
                                        embedding = embeddings[i].output.map { it.toDouble() }
                                    )
                                )
                        }
                    }
                }
                bulk
            }

            log.info("임베딩 저장 완료: {}개", courses.size)

        }
    }

}