package my.reviewing.reviewing_V2.search.service

import co.elastic.clients.elasticsearch.ElasticsearchClient
import my.reviewing.reviewing_V2.course.dto.CourseResponseDto
import my.reviewing.reviewing_V2.course.repository.CourseWishRepository
import my.reviewing.reviewing_V2.crawling.entity.Course
import my.reviewing.reviewing_V2.crawling.repository.CourseRepository
import org.slf4j.LoggerFactory
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class SearchService(
    private val elasticsearchClient: ElasticsearchClient,
    private val embeddingModel: EmbeddingModel,
    private val courseRepository: CourseRepository,
    private val courseWishRepository: CourseWishRepository
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun searchCourses(
        keyword: String,
        page: Int,
        size: Int,
        memberId: Long?
    ): Page<CourseResponseDto> {

        val vector: FloatArray = embeddingModel.embed(keyword)

        val response = elasticsearchClient.search({ s ->
            s.index("courses")
                .from(page * size)
                .size(size)
                .query { q ->
                    q.multiMatch { mm ->
                        mm.query(keyword)
                            .fields(
                                "title^2",
                                "title.korean^2",
                                "title.english",
                                "teacher",
                                "teacher.korean"
                            )
                            .fuzziness("AUTO")
                            .prefixLength(2)
                    }
                }
                .knn { knn ->
                    knn.field("embedding")
                        .queryVector(vector.toList())
                        .k(size)
                        .numCandidates(size * 10)
                }
        }, Map::class.java)

        val ids = response.hits().hits().mapNotNull { hit ->
            runCatching { UUID.fromString(hit.id()) }.getOrNull()
        }

        val courseMap = courseRepository.findAllById(ids).associateBy { it.id }

        val wishedIds: Set<UUID> = if (memberId != null) {
            courseWishRepository.findWishedCourseIds(ids, memberId).toSet()
        } else emptySet()

        val content = ids.mapNotNull { courseMap[it] }.map { course: Course ->
            CourseResponseDto(
                id = course.id!!,
                slug = course.slug,
                title = course.title,
                teacher = course.teacher ?: "",
                thumbnailImage = course.thumbnailImage,
                thumbnailVideo = course.thumbnailVideo,
                url = course.url,
                rating = course.rating,
                wishes = course.wishes,
                comments = course.comments,
                platform = course.platform.englishName,
                wished = wishedIds.contains(course.id)
            )
        }

        val total = response.hits().total()?.value() ?: 0L
        return PageImpl(content, PageRequest.of(page, size), total)
    }
}
