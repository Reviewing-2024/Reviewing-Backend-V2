package my.reviewing.reviewing_V2.global.jwt

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import my.reviewing.reviewing_V2.global.error.ErrorCode
import my.reviewing.reviewing_V2.global.security.writeErrorResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import com.fasterxml.jackson.databind.ObjectMapper
import io.jsonwebtoken.ExpiredJwtException

@Component
class JWTFilter(
    private val jwtUtil: JWTUtil,
    private val objectMapper: ObjectMapper
) : OncePerRequestFilter() {

    // header에 담긴 access token만 검사
    // 토큰 없으면 그냥 넘기기

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {

        if (SecurityContextHolder.getContext().authentication != null) {
            filterChain.doFilter(request, response)
            return
        }

        val accessToken = resolveBearerToken(request)

        if (accessToken == null) { // 로그인 안 했을 때
            filterChain.doFilter(request, response)
            return
        }

        try {
            val claims = jwtUtil.getClaims(accessToken) // 1번만 파싱 (만료 시 ExpiredJwtException)

            if (claims["category"] != "access") {
                writeErrorResponse(response, objectMapper, ErrorCode.UNAUTHORIZED) // 로그인 시도
                return
            }

            val userId = claims.get("userId", java.lang.Long::class.java).toLong()
            val role = claims.get("role", String::class.java)

            val authorities = listOf(GrantedAuthority { role })
            val auth = UsernamePasswordAuthenticationToken(userId, null, authorities)
            SecurityContextHolder.getContext().authentication = auth

            filterChain.doFilter(request, response)

        } catch (e: ExpiredJwtException) {
            writeErrorResponse(response, objectMapper, ErrorCode.EXPIRED_ACCESS) // refresh 시도
            return
        } catch (e: Exception) {
            writeErrorResponse(response, objectMapper, ErrorCode.INVALID_ACCESS) // 로그아웃 + 재로그인 유도
            return
        }

    }

    private fun resolveBearerToken(request: HttpServletRequest): String? {
        val header = request.getHeader("Authorization") ?: return null
        if (!header.startsWith("Bearer ")) return null
        return header.removePrefix("Bearer ").trim().takeIf { it.isNotEmpty() }
    }

}