package my.reviewing.reviewing_V2.global.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import my.reviewing.reviewing_V2.global.error.ErrorCode
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.stereotype.Component
import com.fasterxml.jackson.databind.ObjectMapper

@Component
class JwtAccessDeniedHandler(
    private val objectMapper: ObjectMapper
): AccessDeniedHandler {

    override fun handle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        accessDeniedException: AccessDeniedException
    ) {

        writeErrorResponse(response, objectMapper, ErrorCode.FORBIDDEN)

    }
}