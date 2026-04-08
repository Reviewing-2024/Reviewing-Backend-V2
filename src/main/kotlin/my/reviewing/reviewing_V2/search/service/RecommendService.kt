package my.reviewing.reviewing_V2.search.service

import co.elastic.clients.elasticsearch.ElasticsearchClient
import my.reviewing.reviewing_V2.crawling.repository.CourseRepository
import my.reviewing.reviewing_V2.search.dto.GptRecommendResponse
import my.reviewing.reviewing_V2.search.dto.RecommendItem
import my.reviewing.reviewing_V2.search.dto.RecommendResponseDto

import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class RecommendService(
    private val elasticsearchClient: ElasticsearchClient,
    private val embeddingModel: EmbeddingModel,
    private val chatClient: ChatClient,
    private val courseRepository: CourseRepository
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun recommend(query: String): RecommendResponseDto {

        // 1. 쿼리 임베딩 생성
        val vector: FloatArray = embeddingModel.embed(query)

        // 2. KNN 벡터 검색으로 관련 강의 10개 추출
        val response = elasticsearchClient.search({ s ->
            s.index("courses")
                .size(10)
                .knn { knn ->
                    knn.field("embedding")
                        .queryVector(vector.toList())
                        .k(10)
                        .numCandidates(100)
                }
        }, Map::class.java)

        val ids = response.hits().hits().mapNotNull { hit ->
            runCatching { UUID.fromString(hit.id()) }.getOrNull()
        }

        log.info("RAG 추천 - query: '{}', 관련 강의: {}개", query, ids.size)

        // 3. PostgreSQL에서 강의 상세 정보 조회 (KNN 순서 유지)
        val courseMap = courseRepository.findAllById(ids).associateBy { it.id }
        val courses = ids.mapNotNull { courseMap[it] }

        if (courses.isEmpty()) {
            return RecommendResponseDto(
                intro = "관련 강의를 찾지 못했습니다. 다른 키워드로 질문해보세요.",
                recommendations = emptyList(),
                closing = null
            )
        }

        // 4. 강의 목록 텍스트 구성 (프롬프트 컨텍스트)
        val courseContext = courses.mapIndexed { index, course ->
            "${index + 1}. id=${course.id} | slug=${course.slug} | [${course.platform.englishName}] ${course.title}" +
                    " | 강사: ${course.teacher ?: "미상"}" +
                    " | 평점: ${course.rating}" +
                    " | 리뷰수: ${course.comments}" +
                    " | 찜수: ${course.wishes}"
        }.joinToString("\n")

        // 5. ChatClient로 RAG 프롬프트 실행 → GPT는 id, reason만 반환
        val gptResponse = chatClient.prompt()
            .system("""
                당신은 온라인 강의 추천 전문가입니다.
                아래 강의 목록만을 참고하여 사용자의 질문에 맞는 강의를 추천해주세요.
                목록에 없는 강의는 절대 언급하지 마세요.
                한국어로 답변해주세요.

                응답 작성 지침:
                - intro: 사용자의 질문을 한 문장으로 언급하고, 선정 기준(평점, 인기도 등)을 간결하게 설명. 같은 단어 반복 금지
                - closing: 수강 순서나 난이도 조합 등 실용적인 조언을 1~2문장으로 작성. intro와 중복되는 내용 금지
                - recommendations의 각 항목에는 반드시 강의 목록의 번호(index)만 포함할 것

                [참고 강의 목록]
                $courseContext
            """.trimIndent())
            .user(query)
            .call()
            .entity(GptRecommendResponse::class.java)
            ?: return RecommendResponseDto(
                intro = "추천 답변을 생성하지 못했습니다.",
                recommendations = emptyList(),
                closing = null
            )

        // 6. GPT가 반환한 번호(index)로 courses 리스트와 매핑
        val recommendations = gptResponse.recommendations.mapNotNull { gptItem ->
            val course = courses.getOrNull(gptItem.index - 1) ?: return@mapNotNull null
            RecommendItem(
                id = course.id!!,
                title = course.title,
                teacher = course.teacher,
                platform = course.platform.englishName,
                slug = course.slug
            )
        }

        return RecommendResponseDto(
            intro = gptResponse.intro,
            recommendations = recommendations,
            closing = gptResponse.closing
        )
    }
}
