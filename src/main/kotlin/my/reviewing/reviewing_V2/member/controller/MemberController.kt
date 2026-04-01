package my.reviewing.reviewing_V2.member.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import my.reviewing.reviewing_V2.course.dto.CourseResponseDto
import my.reviewing.reviewing_V2.course.service.CourseService
import my.reviewing.reviewing_V2.global.api.ApiResponse
import my.reviewing.reviewing_V2.global.error.BusinessException
import my.reviewing.reviewing_V2.global.error.ErrorCode
import my.reviewing.reviewing_V2.member.repository.MemberRepository
import my.reviewing.reviewing_V2.review.dto.MyReviewResponseDto
import my.reviewing.reviewing_V2.review.entity.ReviewStateType
import my.reviewing.reviewing_V2.review.service.ReviewService
import org.springframework.data.domain.Page
import org.springframework.data.domain.Slice

import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "테스트 API")
@RestController
@RequestMapping("/api/v1/members")
class MemberController(
    private val memberRepository: MemberRepository,
    private val courseService: CourseService,
    private val reviewService: ReviewService
) {

    @Operation(
        summary = "테스트",
        security = [SecurityRequirement(name = "JWT")]
    )
    @GetMapping("/test")
    fun hello(): ResponseEntity<ApiResponse<String>> {

        val body = ApiResponse.ok("hello world")

        return ResponseEntity.ok().body(body)
    }

    @Operation(
        summary = "내 찜 강의 목록",
        security = [SecurityRequirement(name = "JWT")]
    )
    @GetMapping("/me/wishes")
    fun getMyWishes(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        authentication: Authentication
    ): ResponseEntity<ApiResponse<Page<CourseResponseDto>>> {
        val memberId = authentication.principal as Long
        return ResponseEntity.ok(ApiResponse.ok(courseService.getWishedCourses(memberId, page, size)))
    }

    @Operation(
        summary = "내 리뷰 목록",
        description = "state 미입력 시 전체 조회. PENDING / APPROVED / REJECTED 선택 가능",
        security = [SecurityRequirement(name = "JWT")]
    )
    @GetMapping("/me/reviews")
    fun getMyReviews(
        @RequestParam(required = false) state: ReviewStateType?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int,
        authentication: Authentication
    ): ResponseEntity<ApiResponse<Slice<MyReviewResponseDto>>> {
        val memberId = authentication.principal as Long
        return ResponseEntity.ok(ApiResponse.ok(reviewService.findMyReviews(memberId, state, page, size)))
    }

    // TODO: 임시 API - 프로덕션 배포 전 삭제
    @Operation(
        summary = "[임시] 관리자 권한 부여",
        security = [SecurityRequirement(name = "JWT")]
    )
    @PatchMapping("/{id}/role/admin")
    fun grantAdmin(@PathVariable id: Long): ResponseEntity<ApiResponse<Unit>> {
        val member = memberRepository.findById(id).orElseThrow {
            BusinessException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다.")
        }
        member.role = "ROLE_ADMIN"
        memberRepository.save(member)
        return ResponseEntity.ok(ApiResponse.ok())
    }

}