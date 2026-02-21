package my.reviewing.reviewing_V2.admin.controller

import io.swagger.v3.oas.annotations.tags.Tag
import my.reviewing.reviewing_V2.admin.dto.AdminReviewListResponseDto
import my.reviewing.reviewing_V2.admin.dto.ReviewRejectRequestDto
import my.reviewing.reviewing_V2.admin.service.AdminService
import my.reviewing.reviewing_V2.global.api.ApiResponse
import my.reviewing.reviewing_V2.review.entity.ReviewStateType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "관리자 기능 API")
@RestController
@RequestMapping("/api/v1/admin")
class AdminController(
    private val adminService: AdminService
) {

    @GetMapping("/reviews")
    fun findReviewsByState(
        @RequestParam(defaultValue = "PENDING") state: ReviewStateType,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int
    ): ResponseEntity<ApiResponse<AdminReviewListResponseDto>> {
        val reviews = adminService.findReviewsByState(state, page, size)
        return ResponseEntity.ok().body(ApiResponse.ok(reviews))
    }

    @PatchMapping("/reviews/{reviewId}/approve")
    fun changeReviewApprove(@PathVariable reviewId: Long): ResponseEntity<ApiResponse<Unit>> {
        adminService.changeReviewApprove(reviewId)
        return ResponseEntity.ok().body(ApiResponse.ok())
    }

    @PatchMapping("/reviews/{reviewId}/reject")
    fun changeReviewReject(
        @PathVariable reviewId: Long,
        @RequestBody reviewRejectRequestDto: ReviewRejectRequestDto
    ): ResponseEntity<ApiResponse<Unit>> {
        adminService.changeReviewReject(reviewId, reviewRejectRequestDto)
        return ResponseEntity.ok().body(ApiResponse.ok())
    }

}