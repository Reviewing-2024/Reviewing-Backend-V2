package my.reviewing.reviewing_V2.search.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import my.reviewing.reviewing_V2.course.dto.CourseResponseDto
import my.reviewing.reviewing_V2.crawling.service.CrawlingService
import my.reviewing.reviewing_V2.global.api.ApiResponse
import my.reviewing.reviewing_V2.search.dto.RecommendResponseDto
import my.reviewing.reviewing_V2.search.service.RecommendService
import my.reviewing.reviewing_V2.search.service.SearchService
import org.springframework.data.domain.Page
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@Tag(name = "검색/추천 API")
@RestController
@RequestMapping("/api/v1/search")
class SearchController(
    private val crawlingService: CrawlingService,
    private val searchService: SearchService,
    private val recommendService: RecommendService
) {

    @Operation(summary = "강의 임베딩 생성")
    @PostMapping("/embeddings")
    fun createEmbeddings(
        @RequestParam(required = false) jobId: String?
    ): ResponseEntity<ApiResponse<Map<String, String>>> {
        val id = jobId ?: "courseEmbedding-${LocalDate.now()}"
        crawlingService.runCrawlingJobAsync("courseEmbeddingJob", id)
        return ResponseEntity.ok(ApiResponse.ok(mapOf(
            "jobId" to id,
            "message" to "임베딩 생성이 시작되었습니다."
        )))
    }

    @Operation(
        summary = "강의 하이브리드 검색"
    )
    @GetMapping
    fun searchCourses(
        @RequestParam keyword: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int,
        authentication: Authentication?
    ): ResponseEntity<ApiResponse<Page<CourseResponseDto>>> {
        val memberId = authentication?.principal as? Long
        val result = searchService.searchCourses(keyword, page, size, memberId)
        return ResponseEntity.ok(ApiResponse.ok(result))
    }

    @Operation(
        summary = "강의 추천 챗봇 (RAG)",
        description = "질문을 입력하면 관련 강의를 벡터 검색으로 찾아 GPT-4o가 추천 이유와 함께 답변"
    )
    @GetMapping("/recommend")
    fun recommend(
        @RequestParam query: String
    ): ResponseEntity<ApiResponse<RecommendResponseDto>> {
        val result = recommendService.recommend(query)
        return ResponseEntity.ok(ApiResponse.ok(result))
    }
}
