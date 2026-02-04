package my.reviewing.reviewing_V2.crawling.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import my.reviewing.reviewing_V2.crawling.entity.Platform
import my.reviewing.reviewing_V2.crawling.service.CrawlingService
import my.reviewing.reviewing_V2.global.api.ApiResponse
import org.springframework.batch.core.explore.JobExplorer
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.LocalDateTime

@Tag(name = "크롤링 API (백엔드에서만 사용)", description = "크롤링 관련 API")
@RestController
@RequestMapping("/api/v1/crawling")
class CrawlingController(
    private val crawlingService: CrawlingService,
    private val jobExplorer: JobExplorer
) {

    @Operation(
        summary = "플랫폼 생성"
    )
    @PostMapping("/platform")
    fun createPlatform(
        @RequestParam koreanName: String,
        @RequestParam englishName: String
    ): ResponseEntity<ApiResponse<Platform>> {

        return ResponseEntity.ok()
            .body(ApiResponse.ok(crawlingService.createPlatform(koreanName, englishName)))

    }

    @Operation(
        summary = "인프런 카테고리 생성",
        description = "inflearn_categories.json 파일을 읽어 Category, SubCategory 테이블에 저장"
    )
    @PostMapping("/categories/inflearn")
    fun createInflearnCategory(): ResponseEntity<ApiResponse<Map<String, Int>>> {
        val result = crawlingService.createInflearnCategories()
        return ResponseEntity.ok().body(ApiResponse.ok(result))
    }

    @Operation(
        summary = "비동기 강의 크롤링 실행")
    @PostMapping("/courses")
    fun crawlingCourses(
        @RequestParam jobName: String,
        @RequestParam(required = false) jobId: String?,
        @RequestParam(defaultValue = "0") maxCategories: Int,
        @RequestParam(defaultValue = "0") maxSubCategories: Int,
        @RequestParam(defaultValue = "0") maxPages: Int
    ): ResponseEntity<ApiResponse<Map<String, Any>>> {

        val id = jobId ?: "${jobName}-${LocalDateTime.now()}"

        val additionalParams = mapOf(
            "maxCategories" to maxCategories.toLong(),
            "maxSubCategories" to maxSubCategories.toLong(),
            "maxPages" to maxPages.toLong()
        )

        crawlingService.runCrawlingJobAsync(jobName, id, additionalParams)

        val response = mutableMapOf<String, Any>(
            "jobId" to id,
            "message" to "크롤링 시작"
        )

        if (maxCategories > 0 || maxSubCategories > 0 || maxPages > 0) {
            response["testMode"] = true
            response["limits"] = mapOf(
                "maxCategories" to if (maxCategories > 0) maxCategories else "무제한",
                "maxSubCategories" to if (maxSubCategories > 0) maxSubCategories else "무제한",
                "maxPages" to if (maxPages > 0) maxPages else "무제한"
            )
        }

        return ResponseEntity.ok().body(ApiResponse.ok(response))
    }

    @Operation(
        summary = "크롤링 상태 조회",
        description = "jobId로 크롤링 진행 상태 확인"
    )
    @GetMapping("/status")
    fun getCrawlingStatus(
        @RequestParam jobId: String,
        @RequestParam jobName: String
    ): ResponseEntity<ApiResponse<Map<String, Any?>>> {

        val jobInstance = jobExplorer.getJobInstances(jobName, 0, 100)
            .find { instance ->
                jobExplorer.getJobExecutions(instance).any { execution ->
                    execution.jobParameters.getString("jobId") == jobId
                }
            }

        if (jobInstance == null) {
            return ResponseEntity.ok().body(
                ApiResponse.ok(
                    mapOf(
                        "jobId" to jobId,
                        "status" to "NOT_FOUND",
                        "message" to "해당 jobId의 크롤링 작업을 찾을 수 없습니다."
                    )
                )
            )
        }

        val jobExecution = jobExplorer.getLastJobExecution(jobInstance)

        return ResponseEntity.ok().body(
            ApiResponse.ok(
                mapOf(
                    "jobId" to jobId,
                    "status" to jobExecution?.status?.name,
                    "startTime" to jobExecution?.startTime?.toString(),
                    "endTime" to jobExecution?.endTime?.toString(),
                    "exitStatus" to jobExecution?.exitStatus?.exitCode
                )
            )
        )
    }
}
