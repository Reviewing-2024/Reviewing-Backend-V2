package my.reviewing.reviewing_V2.course.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import my.reviewing.reviewing_V2.course.dto.CourseResponseDto
import my.reviewing.reviewing_V2.course.service.CourseService
import my.reviewing.reviewing_V2.global.api.ApiResponse
import org.springframework.data.domain.Page
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "강의 API", description = "강의 조회 API")
@RestController
@RequestMapping("/api/v1/courses")
class CourseController(
    private val courseService: CourseService
) {

    @Operation(summary = "강의 목록 조회 (필터 + 페이지네이션)")
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
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<ApiResponse<Page<CourseResponseDto>>> {
        return ResponseEntity.ok().body(
            courseService.getCourses(platform, category, subCategories, sort, page, size)
        )
    }
}
