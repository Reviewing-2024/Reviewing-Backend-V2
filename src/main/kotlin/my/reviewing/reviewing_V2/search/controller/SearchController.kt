package my.reviewing.reviewing_V2.search.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import my.reviewing.reviewing_V2.crawling.service.CrawlingService
import my.reviewing.reviewing_V2.global.api.ApiResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@Tag(name = "검색/추천 API")
@RestController
@RequestMapping("/api/v1/search")
class SearchController(
    private val crawlingService: CrawlingService
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
}
