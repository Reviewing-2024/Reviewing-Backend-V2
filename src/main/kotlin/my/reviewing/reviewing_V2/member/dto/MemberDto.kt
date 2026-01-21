package my.reviewing.reviewing_V2.member.dto

data class MemberDto(
    val id: Long,
    val username: String,
    val name: String?,
    val role: String
)