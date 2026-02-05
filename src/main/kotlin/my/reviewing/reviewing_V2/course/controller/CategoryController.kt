package my.reviewing.reviewing_V2.course.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import my.reviewing.reviewing_V2.course.dto.CategoryResponseDto
import my.reviewing.reviewing_V2.course.service.CategoryService
import my.reviewing.reviewing_V2.global.api.ApiResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "카테고리 API", description = "카테고리/서브 카테고리 조회 API")
@RestController
@RequestMapping("/api/v1")
class CategoryController(
    private val categoryService: CategoryService
) {

    @Operation(
        summary = "카테고리 조회 (플랫폼 영어이름으로 조회)"
    )
    @GetMapping("/categories")
    fun getCategoriesByPlatform(
        @RequestParam platformName: String
    ): ResponseEntity<ApiResponse<List<CategoryResponseDto>>> {
        return ResponseEntity.ok().body(categoryService.getCategoriesByPlatform(platformName));
    }

    @Operation(
        summary = "서브 카테고리 조회 (플랫폼 영어이름, 카테고리 slug로 조회)"
    )
    @GetMapping("/subcategories")
    fun getSubCategoriesByCategory(
        @RequestParam platformName: String,
        @RequestParam categorySlug: String
    ): ResponseEntity<ApiResponse<List<CategoryResponseDto>>> {
        return ResponseEntity.ok()
            .body(categoryService.getSubCategoriesByCategory(platformName, categorySlug))
    }

}