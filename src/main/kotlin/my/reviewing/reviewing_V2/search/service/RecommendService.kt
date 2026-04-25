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
                recommendations = emptyList()
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

        // 5. ChatClient로 RAG 프롬프트 실행 → GPT는 index만 반환
        val rawContent = chatClient.prompt()
            .system("""
                당신은 온라인 강의 추천 전문가입니다.
                아래 강의 목록에서 사용자의 질문과 관련된 강의를 골라주세요.
                목록에 없는 강의는 절대 언급하지 마세요.

                [필수 규칙]
                1. recommendations에 반드시 5개 이상 포함. 10개 목록 중 관련성이 조금이라도 있으면 모두 포함할 것.
                2. intro는 1문장으로, 사용자의 질문 의도를 요약하고 어떤 기준으로 골랐는지만 간결하게 작성. 개별 강의 제목, 강사명은 intro에 절대 언급하지 말 것.
                3. intro 존댓말(~해요, ~있어요, ~골라봤어요). 반말 금지.
                4. intro 예시: "AI 에이전트 개발에 관심 있으시다면, 실습 중심의 강의 위주로 골라봤어요."

                [응답 JSON 형식]
                {"intro": "...", "recommendations": [{"index": 1}, {"index": 3}, {"index": 5}, {"index": 7}, {"index": 9}]}

                recommendations.index = 강의 목록의 번호 (1부터 시작)

                [참고 강의 목록]
                $courseContext
            """.trimIndent())
            .user(query)
            .call()
            .content()

        val objectMapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
        val gptResponse = try {
            objectMapper.readValue(rawContent, GptRecommendResponse::class.java)
        } catch (e: Exception) {
            return RecommendResponseDto(
                intro = "추천 답변을 생성하지 못했습니다.",
                recommendations = emptyList()
            )
        }

        log.info("RAG GPT 응답 - intro: '{}', recommendations: {}", gptResponse.intro, gptResponse.recommendations)

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
            recommendations = recommendations
        )
    }
}
