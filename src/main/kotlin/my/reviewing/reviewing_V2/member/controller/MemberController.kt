package my.reviewing.reviewing_V2.member.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import my.reviewing.reviewing_V2.global.api.ApiResponse
import my.reviewing.reviewing_V2.member.repository.MemberRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "테스트 API")
@RestController
@RequestMapping("/api/v1/members")
class MemberController(
    private val memberRepository: MemberRepository
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

}