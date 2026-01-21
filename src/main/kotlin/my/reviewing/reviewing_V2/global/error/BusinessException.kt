package my.reviewing.reviewing_V2.global.error

class BusinessException(
    val errorCode: ErrorCode,
    override val message: String = errorCode.message
) : RuntimeException(message)