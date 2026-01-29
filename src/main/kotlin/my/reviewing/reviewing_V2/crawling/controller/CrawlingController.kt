package my.reviewing.reviewing_V2.crawling.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import my.reviewing.reviewing_V2.crawling.entity.Course
import my.reviewing.reviewing_V2.crawling.entity.Platform
import my.reviewing.reviewing_V2.crawling.repository.CourseRepository
import my.reviewing.reviewing_V2.crawling.service.CrawlingService
import my.reviewing.reviewing_V2.global.api.ApiResponse
import org.springframework.batch.core.JobParametersBuilder
import org.springframework.batch.core.configuration.JobRegistry
import org.springframework.batch.core.launch.JobLauncher
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "크롤링 API (백엔드에서만 사용)", description = "크롤링 관련 API")
@RestController
@RequestMapping("/api/v1/crawling")
class CrawlingController(
    private val jobLauncher: JobLauncher,
    private val jobRegistry: JobRegistry,
    private val courseRepository: CourseRepository,
    private val crawlingService: CrawlingService
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
        summary = "노마드코더 강의 크롤링 실행"
    )
    @PostMapping("/courses/nomadcoders")
    fun crawlingNomadcodersCourses(
        @RequestParam jobName: String
    ): ResponseEntity<ApiResponse<List<Course>>> {

        val jobParameters = JobParametersBuilder()
            .addLong("timestamp", System.currentTimeMillis())
            .toJobParameters()

        jobLauncher.run(jobRegistry.getJob(jobName), jobParameters)

        return ResponseEntity.ok().body(ApiResponse.ok(courseRepository.findAll()))
    }
}
