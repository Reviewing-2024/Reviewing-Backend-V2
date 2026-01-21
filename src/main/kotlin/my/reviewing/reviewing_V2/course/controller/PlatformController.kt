package my.reviewing.reviewing_V2.course.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import my.reviewing.reviewing_V2.course.dto.PlatformResponseDto
import my.reviewing.reviewing_V2.course.service.PlatformService
import my.reviewing.reviewing_V2.global.api.ApiResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "플랫폼 API", description = "강의 플랫폼 조회 API")
@RestController
@RequestMapping("/api/v1/platforms")
class PlatformController(
    private val platformService: PlatformService
) {

    @Operation(
        summary = "전체 플랫폼 목록 조회"
    )
    @GetMapping
    fun getAllPlatforms(): ResponseEntity<ApiResponse<List<PlatformResponseDto>>> {
        return ResponseEntity.ok().body(platformService.getAllPlatforms())
    }

}