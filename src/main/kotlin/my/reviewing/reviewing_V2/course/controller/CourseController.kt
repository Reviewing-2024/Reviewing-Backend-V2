package my.reviewing.reviewing_V2.course.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import my.reviewing.reviewing_V2.course.dto.CourseResponseDto
import my.reviewing.reviewing_V2.course.service.CourseService
import my.reviewing.reviewing_V2.global.api.ApiResponse
import org.springframework.data.domain.Page
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Tag(name = "강의 API", description = "강의 조회 API")
@RestController
@RequestMapping("/api/v1/courses")
class CourseController(
    private val courseService: CourseService
) {

    @Operation(summary = "강의 목록 조회 (필터 + 페이지네이션)", security = [SecurityRequirement(name = "JWT")])
    @GetMapping
    fun getCourses(
        @Parameter(description = "플랫폼 영어이름 (단일 선택)")
        @RequestParam(required = false) platform: String?,

        @Parameter(description = "카테고리 slug (단일 선택)")
        @RequestParam(required = false) category: String?,

        @Parameter(description = "서브카테고리 slug 목록 (복수 선택, OR)")
        @RequestParam(required = false) subCategories: List<String>?,

        @Parameter(description = "정렬 기준 (createdAt, rating, reviews)", example = "createdAt")
        @RequestParam(defaultValue = "createdAt") sort: String,

        @Parameter(description = "페이지 번호 (0부터 시작)", example = "0")
        @RequestParam(defaultValue = "0") page: Int,

        @Parameter(description = "페이지당 항목 수", example = "20")
        @RequestParam(defaultValue = "20") size: Int,
        authentication: Authentication?
    ): ResponseEntity<ApiResponse<Page<CourseResponseDto>>> {
        val memberId = authentication?.principal as? Long
        return ResponseEntity.ok().body(
            courseService.getCourses(platform, category, subCategories, sort, page, size, memberId)
        )
    }

    @Operation(summary = "단건 강의 조회 API")
    @GetMapping("/{platform}/{slug}")
    fun getCourse(
        @PathVariable platform: String,
        @PathVariable slug: String,
        authentication: Authentication?
    ): ResponseEntity<ApiResponse<CourseResponseDto>> {
        val memberId = authentication?.principal as? Long
        return ResponseEntity.ok().body(courseService.getCourse(platform, slug, memberId))
    }

    @Operation(summary = "강의 찜 추가", security = [SecurityRequirement(name = "JWT")])
    @PostMapping("/{courseId}/wish")
    fun createCourseWish(
        @PathVariable courseId: UUID,
        authentication: Authentication
    ): ResponseEntity<ApiResponse<Unit>> {

        val memberId = authentication.principal as Long
        courseService.addWish(courseId, memberId)
        return ResponseEntity.ok(ApiResponse.ok())
    }

    @Operation(summary = "강의 찜 취소", security = [SecurityRequirement(name = "JWT")])
    @DeleteMapping("/{courseId}/wish")
    fun removeWish(
        @PathVariable courseId: UUID,
        authentication: Authentication
    ): ResponseEntity<ApiResponse<Unit>> {
        val memberId = authentication.principal as Long
        courseService.removeWish(courseId, memberId)
        return ResponseEntity.ok(ApiResponse.ok())
    }


}
