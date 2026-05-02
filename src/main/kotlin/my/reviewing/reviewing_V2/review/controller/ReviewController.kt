package my.reviewing.reviewing_V2.review.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import my.reviewing.reviewing_V2.global.api.ApiResponse
import my.reviewing.reviewing_V2.review.dto.ReviewRequestDto
import my.reviewing.reviewing_V2.review.dto.ReviewResponseDto
import my.reviewing.reviewing_V2.review.dto.ReviewSortType
import my.reviewing.reviewing_V2.review.service.ReviewService
import org.springframework.data.domain.Slice
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
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
        summary = "리뷰 작성",
        security = [SecurityRequirement(name = "JWT")]
    )
    @ApiResponses(value = [
        SwaggerApiResponse(responseCode = "200", description = "리뷰 작성 성공"),
        SwaggerApiResponse(
            responseCode = "400", description = "유효성 검사 실패",
            content = [Content(examples = [ExampleObject(
                value = """{"success": false, "data": null, "error": {"code": "COMMON_400_VALIDATION_ERROR", "message": "요청 값이 올바르지 않습니다.", "details": [{"field": "rating", "reason": "Rating은 1.0 이상이어야 합니다."}]}}"""
            )])]
        ),
        SwaggerApiResponse(
            responseCode = "401", description = "로그인 필요 또는 토큰 만료",
            content = [Content(examples = [ExampleObject(
                value = """{"success": false, "data": null, "error": {"code": "AUTH_401_EXPIRED_ACCESS", "message": "로그인이 만료되었습니다.", "details": null}}"""
            )])]
        ),
        SwaggerApiResponse(
            responseCode = "404", description = "강의 또는 사용자를 찾을 수 없음",
            content = [Content(examples = [ExampleObject(
                value = """{"success": false, "data": null, "error": {"code": "COMMON_404_NOT_FOUND", "message": "강의를 찾을 수 없습니다.", "details": null}}"""
            )])]
        ),
        SwaggerApiResponse(
            responseCode = "409", description = "이미 리뷰를 작성한 강의 (PENDING 또는 APPROVED 상태)",
            content = [Content(examples = [ExampleObject(
                value = """{"success": false, "data": null, "error": {"code": "COMMON_409_CONFLICT", "message": "이미 리뷰를 작성하셨습니다.", "details": null}}"""
            )])]
        )
    ])
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

    @Operation(
        summary = "리뷰 작성 검사",
        security = [SecurityRequirement(name = "JWT")]
    )
    @GetMapping("/{courseId}/check")
    fun checkBeforeCreateReview(
        @PathVariable courseId: UUID,
        authentication: Authentication
    ): ResponseEntity<ApiResponse<Unit>> {
        val memberId = authentication.principal as Long
        reviewService.checkBeforeCreateReview(courseId, memberId)
        return ResponseEntity.ok(ApiResponse.ok())
    }


    @Operation(
        summary = "강의별 리뷰 조회",
        description = """
            - sort: LATEST(최신순, 기본값) / HIGH_RATING(평점 높은순) / LOW_RATING(평점 낮은순)
            - 무한스크롤: hasNext=true이면 page+1로 추가 요청
        """
    )
    @GetMapping("/{courseId}")
    fun findReviewsByCourse(
        @PathVariable courseId: UUID,
        @RequestParam(defaultValue = "LATEST") sort: ReviewSortType,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int,
        authentication: Authentication?
    ): ResponseEntity<ApiResponse<Slice<ReviewResponseDto>>> {
        val memberId = authentication?.principal as? Long
        val reviews = reviewService.findReviewsByCourse(courseId, sort, page, size, memberId)
        return ResponseEntity.ok(ApiResponse.ok(reviews))
    }

    @Operation(
        summary = "리뷰 삭제 (소프트 삭제)",
        security = [SecurityRequirement(name = "JWT")]
    )
    @DeleteMapping("/{reviewId}")
    fun deleteReview(
        @PathVariable reviewId: Long,
        authentication: Authentication
    ): ResponseEntity<ApiResponse<Unit>> {
        val memberId = authentication.principal as Long
        reviewService.deleteReview(reviewId, memberId)
        return ResponseEntity.ok(ApiResponse.ok())
    }

    @Operation(
        summary = "리뷰 좋아요",
        security = [SecurityRequirement(name = "JWT")]
    )
    @PostMapping("/{reviewId}/like")
    fun addLike(
        @PathVariable reviewId: Long,
        authentication: Authentication
    ): ResponseEntity<ApiResponse<Unit>> {
        val memberId = authentication.principal as Long
        reviewService.addLike(reviewId, memberId)
        return ResponseEntity.ok(ApiResponse.ok())
    }

    @Operation(
        summary = "리뷰 좋아요 취소",
        security = [SecurityRequirement(name = "JWT")]
    )
    @DeleteMapping("/{reviewId}/like")
    fun removeLike(
        @PathVariable reviewId: Long,
        authentication: Authentication
    ): ResponseEntity<ApiResponse<Unit>> {
        val memberId = authentication.principal as Long
        reviewService.removeLike(reviewId, memberId)
        return ResponseEntity.ok(ApiResponse.ok())
    }

    @Operation(
        summary = "리뷰 싫어요",
        security = [SecurityRequirement(name = "JWT")]
    )
    @PostMapping("/{reviewId}/dislike")
    fun addDislike(
        @PathVariable reviewId: Long,
        authentication: Authentication
    ): ResponseEntity<ApiResponse<Unit>> {
        val memberId = authentication.principal as Long
        reviewService.addDislike(reviewId, memberId)
        return ResponseEntity.ok(ApiResponse.ok())
    }

    @Operation(
        summary = "리뷰 싫어요 취소",
        security = [SecurityRequirement(name = "JWT")]
    )
    @DeleteMapping("/{reviewId}/dislike")
    fun removeDislike(
        @PathVariable reviewId: Long,
        authentication: Authentication
    ): ResponseEntity<ApiResponse<Unit>> {
        val memberId = authentication.principal as Long
        reviewService.removeDislike(reviewId, memberId)
        return ResponseEntity.ok(ApiResponse.ok())
    }

}
