package my.reviewing.reviewing_V2.global.api

class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val error: ApiError? = null
) {

    companion object {
        fun <T> ok(data: T): ApiResponse<T> =
            ApiResponse(success = true, data = data)

        fun ok(): ApiResponse<Unit> =
            ApiResponse(success = true, data = Unit)

        fun fail(error: ApiError): ApiResponse<Unit> =
            ApiResponse(success = false, error = error)

    }
}

data class ApiError(
    val code: String,
    val message: String,
    val details: List<FieldErrorDetail>? = null
)

data class FieldErrorDetail(
    val field: String,
    val reason: String
)