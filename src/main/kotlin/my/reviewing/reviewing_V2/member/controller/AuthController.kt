package my.reviewing.reviewing_V2.member.controller

import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import my.reviewing.reviewing_V2.global.api.ApiResponse
import my.reviewing.reviewing_V2.global.error.BusinessException
import my.reviewing.reviewing_V2.global.error.ErrorCode
import my.reviewing.reviewing_V2.global.jwt.JWTUtil
import my.reviewing.reviewing_V2.member.repository.MemberRepository
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val jwtUtil: JWTUtil,
    private val memberRepository: MemberRepository
) {

    companion object {
        private val logger = LoggerFactory.getLogger(AuthController::class.java)
    }

    // OAuth2 로그인 후 refresh token(쿠카)으로 access token 발급
    @PostMapping("/access")
    fun issueAccessToken(request: HttpServletRequest, response: HttpServletResponse): ApiResponse<Unit> {
        // 1. 쿠키에서 refresh token 가져오기 (무조건 있어야 함)
        val refreshToken = request.cookies
            ?.firstOrNull { it.name == "refresh" }
            ?.value
            ?: throw BusinessException(ErrorCode.UNAUTHORIZED, "Refresh token이 없습니다.")

        // 2. refresh token 검증
        try {
            if (jwtUtil.isExpired(refreshToken)) {
                throw BusinessException(ErrorCode.EXPIRED_REFRESH)
            }

            if (jwtUtil.getCategory(refreshToken) != "refresh") {
                throw BusinessException(ErrorCode.INVALID_REFRESH)
            }
        } catch (e: BusinessException) {
            throw e
        } catch (e: Exception) {
            logger.error("JWT validation failed: ${e.javaClass.simpleName} - ${e.message}")
            throw BusinessException(ErrorCode.INVALID_REFRESH)
        }

        // 3. userId 추출 및 role 조회
        val userId = jwtUtil.getUserId(refreshToken)
        val member = memberRepository.findById(userId).orElseThrow {
            BusinessException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다.")
        }

        // 4. access token 생성 (헤더로 전달)
        val accessToken = jwtUtil.createAccessToken(userId, member.role)
        response.setHeader("Authorization", "Bearer $accessToken")

        return ApiResponse.ok()
    }

    // refresh 받아서 access, refresh 다시 받는 api
    @PostMapping("/reissue")
    fun reissue(request: HttpServletRequest, response: HttpServletResponse): ApiResponse<Unit> {
        // 1. 쿠키에서 refresh token 가져오기
        val refreshToken = request.cookies
            ?.firstOrNull { it.name == "refresh" }
            ?.value
            ?: throw BusinessException(ErrorCode.UNAUTHORIZED, "Refresh token이 없습니다.")

        // 2. refresh token 검증
        try {
            if (jwtUtil.isExpired(refreshToken)) {
                throw BusinessException(ErrorCode.EXPIRED_REFRESH)
            }

            if (jwtUtil.getCategory(refreshToken) != "refresh") {
                throw BusinessException(ErrorCode.INVALID_REFRESH)
            }
        } catch (e: BusinessException) {
            throw e
        } catch (e: Exception) {
            logger.error("JWT validation failed: ${e.javaClass.simpleName} - ${e.message}")
            throw BusinessException(ErrorCode.INVALID_REFRESH)
        }

        // 3. userId 추출 및 role 조회
        val userId = jwtUtil.getUserId(refreshToken)
        val member = memberRepository.findById(userId).orElseThrow {
            BusinessException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다.")
        }

        // 4. 새로운 access token 생성 (헤더로 전달)
        val newAccessToken = jwtUtil.createAccessToken(userId, member.role)
        response.setHeader("Authorization", "Bearer $newAccessToken")

        // 5. 새로운 refresh token 생성 (쿠키로 전달)
        val newRefreshToken = jwtUtil.createRefreshToken(userId)
        response.addCookie(createCookie("refresh", newRefreshToken))

        return ApiResponse.ok()
    }

    // 로그아웃
    @PostMapping("/logout")
    fun logout(response: HttpServletResponse): ApiResponse<Unit> {
        // refresh token 쿠키 삭제
        val cookie = Cookie("refresh", null).apply {
            maxAge = 0
            path = "/"
        }
        response.addCookie(cookie)

        return ApiResponse.ok()
    }

    private fun createCookie(key: String, value: String): Cookie {
        return Cookie(key, value).apply {
            maxAge = 24 * 60 * 60  // 24시간
            path = "/"
            isHttpOnly = true
            // secure = true  // HTTPS 환경에서만 전송 (프로덕션에서 활성화)
        }
    }
}