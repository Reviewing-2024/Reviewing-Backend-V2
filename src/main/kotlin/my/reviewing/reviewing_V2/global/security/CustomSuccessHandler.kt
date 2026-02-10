package my.reviewing.reviewing_V2.global.security

import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import my.reviewing.reviewing_V2.global.jwt.JWTUtil
import my.reviewing.reviewing_V2.member.dto.CustomOAuth2User
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler
import org.springframework.stereotype.Component

@Component
class CustomSuccessHandler(
    private val jwtUtil: JWTUtil
): SimpleUrlAuthenticationSuccessHandler() {

    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication
    ) {

        val customUserDetails = authentication.principal as CustomOAuth2User

        val userId = customUserDetails.id

        // 로그인시 refresh만 쿠키로 발급
        val refreshToken = jwtUtil.createRefreshToken(userId)

        response.addCookie(createCookie("refresh", refreshToken))
        response.sendRedirect("http://localhost:5173/login/redirect")

    }

    private fun createCookie(key: String, value: String): Cookie {

        val cookie: Cookie = Cookie(key, value)
        cookie.maxAge = 24 * 60 * 60 // 24시간
        cookie.path = "/"
//        cookie.secure = true https일 경우에 활성화
        cookie.isHttpOnly = true

        return cookie
    }

}