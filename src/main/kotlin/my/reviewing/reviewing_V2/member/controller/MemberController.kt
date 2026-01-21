package my.reviewing.reviewing_V2.member.controller

import my.reviewing.reviewing_V2.global.api.ApiResponse
import my.reviewing.reviewing_V2.member.repository.MemberRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/members")
class MemberController(
    private val memberRepository: MemberRepository
) {

    // 테스트
    @GetMapping("/test")
    fun hello(): ResponseEntity<ApiResponse<String>> {

        val body = ApiResponse.ok("hello world")

        return ResponseEntity.ok().body(body)
    }

}