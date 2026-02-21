package my.reviewing.reviewing_V2.global.error

import my.reviewing.reviewing_V2.global.api.ApiError
import my.reviewing.reviewing_V2.global.api.ApiResponse
import my.reviewing.reviewing_V2.global.api.FieldErrorDetail
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.resource.NoResourceFoundException

@RestControllerAdvice
class GlobalExceptionHandler {

    companion object {
        private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)
    }

    // 의도적인 에러
    @ExceptionHandler(BusinessException::class)
    fun handlerBusiness(e: BusinessException): ResponseEntity<ApiResponse<Unit>> {
        logger.warn("Business exception: ${e.errorCode.code} - ${e.message}")
        val body = ApiResponse.fail(
            ApiError(code = e.errorCode.code, message = e.message)
        )
        return ResponseEntity.status(e.errorCode.status).body(body)
    }

    // @valid 검증실패
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(e: MethodArgumentNotValidException): ResponseEntity<ApiResponse<Unit>> {
        val details = e.bindingResult.fieldErrors.map {
            FieldErrorDetail(
                field = it.field,
                reason = it.defaultMessage?: "invalid"
            )
        }
        logger.warn("Validation failed: ${e.bindingResult.fieldErrors.map { "${it.field}: ${it.defaultMessage}" }}")

        val body = ApiResponse.fail(
            ApiError(
                code = ErrorCode.INVALID_REQUEST.code,
                message = ErrorCode.INVALID_REQUEST.message,
                details = details
            )
        )
        return ResponseEntity.status(ErrorCode.INVALID_REQUEST.status).body(body)
    }

    // 브라우저 자동 요청 (favicon.ico, devtools 등) - 로그 무시
    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNoResourceFound(e: NoResourceFoundException): ResponseEntity<Unit> {
        logger.debug("No resource found: ${e.message}")
        return ResponseEntity.notFound().build()
    }

    // 500 에러 - 예상치 못한 에러
    @ExceptionHandler(Exception::class)
    fun handleUnexpected(e: Exception): ResponseEntity<ApiResponse<Unit>> {
        logger.error("Unexpected error occurred: ${e.javaClass.simpleName} - ${e.message}", e)

        val body = ApiResponse.fail(
            ApiError(
                code = ErrorCode.INTERNAL_ERROR.code,
                message = ErrorCode.INTERNAL_ERROR.message
            )
        )
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.status).body(body)
    }
}