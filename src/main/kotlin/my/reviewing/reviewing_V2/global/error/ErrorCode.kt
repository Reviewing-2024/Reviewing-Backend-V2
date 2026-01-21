package my.reviewing.reviewing_V2.global.error

import org.springframework.http.HttpStatus

enum class ErrorCode(
    val status: HttpStatus,
    val code: String,
    val message: String
) {

    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "COMMON_400_VALIDATION_ERROR", "요청 값이 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "AUTH_401_UNAUTHORIZED", "로그인이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "AUTH_403_FORBIDDEN", "권한이 없습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON_404_NOT_FOUND", "대상을 찾을 수 없습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_500_INTERNAL_ERROR", "서버 오류가 발생했습니다."),
    EXPIRED_ACCESS(HttpStatus.UNAUTHORIZED, "AUTH_401_EXPIRED_ACCESS", "로그인이 만료되었습니다."),
    INVALID_ACCESS(HttpStatus.UNAUTHORIZED, "AUTH_401_INVALID_ACCESS", "유효하지 않은 토큰입니다."),
    EXPIRED_REFRESH(HttpStatus.UNAUTHORIZED, "AUTH_401_EXPIRED_REFRESH", "Refresh token이 만료되었습니다."),
    INVALID_REFRESH(HttpStatus.UNAUTHORIZED, "AUTH_401_INVALID_REFRESH", "유효하지 않은 refresh token입니다.")
}