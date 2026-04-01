package my.reviewing.reviewing_V2.member.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class UpdateNicknameRequestDto(
    @field:NotBlank
    @field:Size(max = 50)
    val nickname: String
)
