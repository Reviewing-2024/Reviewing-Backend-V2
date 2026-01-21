package my.reviewing.reviewing_V2.member.dto

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.oauth2.core.user.OAuth2User

class CustomOAuth2User(private val memberDto: MemberDto) : OAuth2User {

    override fun getAttributes(): Map<String?, Any?> = emptyMap()

    override fun getAuthorities(): Collection<GrantedAuthority> {
        return listOf(GrantedAuthority { memberDto.role })
    }

    override
    fun getName(): String = memberDto.name ?: "Unknown"

    val id: Long
        get() = memberDto.id

    val username: String
        get() = memberDto.username

    val role: String
        get() = memberDto.role
}
