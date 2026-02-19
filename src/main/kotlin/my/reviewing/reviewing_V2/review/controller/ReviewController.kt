package my.reviewing.reviewing_V2.review.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import my.reviewing.reviewing_V2.global.api.ApiResponse
import my.reviewing.reviewing_V2.review.dto.ReviewRequestDto
import my.reviewing.reviewing_V2.review.service.ReviewService
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@Tag(name = "리뷰 API")
@RestController
@RequestMapping("/api/v1/reviews")
class ReviewController(
    private val reviewService: ReviewService
) {

    @Operation(
        summary = "리뷰 생성 API",
        description = "로그인 필수, 강의 작성 가능 체크(작성x,거절)",
        security = [SecurityRequirement(name = "JWT")]
    )
    @PostMapping(
        value = ["/{courseId}"],
        consumes = [MediaType.MULTIPART_FORM_DATA_VALUE]
    )
    fun createReview(
        @PathVariable courseId: UUID,
        @Valid @RequestPart reviewRequestDto: ReviewRequestDto,
        @RequestPart certificationFile: MultipartFile,
        authentication: Authentication
    ): ResponseEntity<ApiResponse<Unit>> {
        val memberId = authentication.principal as Long
        reviewService.createReview(courseId, memberId, reviewRequestDto, certificationFile)
        return ResponseEntity.ok(ApiResponse.ok())
    }

}
