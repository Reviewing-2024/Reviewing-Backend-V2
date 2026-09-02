package my.reviewing.reviewing_V2.member.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import my.reviewing.reviewing_V2.course.dto.CourseResponseDto
import my.reviewing.reviewing_V2.course.service.CourseService
import my.reviewing.reviewing_V2.global.api.ApiResponse
import my.reviewing.reviewing_V2.global.channeltalk.ChannelTalkService
import my.reviewing.reviewing_V2.member.dto.UpdateNicknameRequestDto
import my.reviewing.reviewing_V2.member.service.MemberService
import my.reviewing.reviewing_V2.review.dto.MyReviewResponseDto
import my.reviewing.reviewing_V2.review.entity.ReviewStateType
import my.reviewing.reviewing_V2.review.service.ReviewService
import org.springframework.data.domain.Page
import org.springframework.data.domain.Slice

import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@Tag(name = "마이페이지 API")
@RestController
@RequestMapping("/api/v1/members")
class MemberController(
    private val memberService: MemberService,
    private val courseService: CourseService,
    private val reviewService: ReviewService,
    private val channelTalkService: ChannelTalkService
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

    @Operation(
        summary = "프로필 통합 변경 (닉네임 + 이미지)",
        description = "닉네임, 이미지 중 하나만 보내도 됩니다.",
        security = [SecurityRequirement(name = "JWT")]
    )
    @PatchMapping("/me/profile", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun updateProfile(
        @RequestParam(required = false) nickname: String?,
        @RequestParam(required = false) file: MultipartFile?,
        authentication: Authentication
    ): ResponseEntity<ApiResponse<Unit>> {
        val memberId = authentication.principal as Long
        memberService.updateProfile(memberId, nickname, file)
        return ResponseEntity.ok(ApiResponse.ok())
    }

    @Operation(
        summary = "닉네임 변경",
        security = [SecurityRequirement(name = "JWT")]
    )
    @PatchMapping("/me/nickname")
    fun updateNickname(
        @Valid @RequestBody dto: UpdateNicknameRequestDto,
        authentication: Authentication
    ): ResponseEntity<ApiResponse<Unit>> {
        val memberId = authentication.principal as Long
        memberService.updateNickname(memberId, dto.nickname)
        return ResponseEntity.ok(ApiResponse.ok())
    }

    @Operation(
        summary = "프로필 이미지 변경",
        security = [SecurityRequirement(name = "JWT")]
    )
    @PatchMapping("/me/profile-image", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun updateProfileImage(
        @RequestParam("file") file: MultipartFile,
        authentication: Authentication
    ): ResponseEntity<ApiResponse<Unit>> {
        val memberId = authentication.principal as Long
        memberService.updateProfileImage(memberId, file)
        return ResponseEntity.ok(ApiResponse.ok())
    }

    @Operation(
        summary = "채널톡 멤버 ID 해시 인코딩",
        security = [SecurityRequirement(name = "JWT")]
    )
    @GetMapping("/channelTalk/encode")
    fun channelTalkMemberIdEncode(authentication: Authentication): ResponseEntity<ApiResponse<String>> {
        val memberId = authentication.principal as Long
        return ResponseEntity.ok(ApiResponse.ok(channelTalkService.encode(memberId.toString())))
    }

    // TODO: 임시 API - 프로덕션 배포 전 삭제
    @Operation(
        summary = "[임시] 관리자 권한 부여",
        security = [SecurityRequirement(name = "JWT")]
    )
    @PatchMapping("/{id}/role/admin")
    fun grantAdmin(@PathVariable id: Long): ResponseEntity<ApiResponse<Unit>> {
        memberService.grantAdmin(id)
        return ResponseEntity.ok(ApiResponse.ok())
    }

}