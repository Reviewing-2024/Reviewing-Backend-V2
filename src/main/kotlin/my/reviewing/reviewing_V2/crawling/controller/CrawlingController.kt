package my.reviewing.reviewing_V2.crawling.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import my.reviewing.reviewing_V2.crawling.entity.Platform
import my.reviewing.reviewing_V2.crawling.repository.PlatformRepository
import my.reviewing.reviewing_V2.global.api.ApiResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "크롤링 API (백엔드에서만 사용)", description = "크롤링 관련 API")
@RestController
@RequestMapping("/api/v1/crawling")
class CrawlingController(
    private val platformRepository: PlatformRepository
) {

    @Operation(
        summary = "플랫폼 생성"
    )
    @PostMapping("/platform")
    fun createPlatform(
        @RequestParam koreanName: String,
        @RequestParam englishName: String
    ): ResponseEntity<ApiResponse<Platform>> {

        val platform = Platform(
            koreanName = koreanName,
            englishName = englishName
        )

        val savePlatform = platformRepository.save(platform)

        return ResponseEntity.ok().body(ApiResponse.ok(savePlatform))
    }


}