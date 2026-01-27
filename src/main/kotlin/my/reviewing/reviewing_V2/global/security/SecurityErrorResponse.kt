package my.reviewing.reviewing_V2.global.security

import jakarta.servlet.http.HttpServletResponse
import my.reviewing.reviewing_V2.global.api.ApiError
import my.reviewing.reviewing_V2.global.api.ApiResponse
import my.reviewing.reviewing_V2.global.error.ErrorCode
import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.charset.StandardCharsets

fun writeErrorResponse(
    response: HttpServletResponse,
    objectMapper: ObjectMapper,
    errorCode: ErrorCode
) {
    response.status = errorCode.status.value()
    response.characterEncoding = StandardCharsets.UTF_8.name()
    response.contentType = "application/json"

    val body = ApiResponse.fail(
        ApiError(
            code = errorCode.code,
            message = errorCode.message
        )
    )

    response.writer.write(
        objectMapper.writeValueAsString(body)
    )
}