package my.reviewing.reviewing_V2.member.service

import my.reviewing.reviewing_V2.member.dto.CustomOAuth2User
import my.reviewing.reviewing_V2.member.dto.KakaoResponse
import my.reviewing.reviewing_V2.member.dto.MemberDto
import my.reviewing.reviewing_V2.member.dto.OAuth2Response
import my.reviewing.reviewing_V2.member.entity.Member
import my.reviewing.reviewing_V2.member.repository.MemberRepository
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class CustomOAuth2MemberService(
    private val memberRepository: MemberRepository
) : DefaultOAuth2UserService() {

    override fun loadUser(userRequest: OAuth2UserRequest?): OAuth2User? {

        val oAuth2User = super.loadUser(userRequest)

        val registrationId = userRequest?.clientRegistration?.registrationId

        val oAuth2Response: OAuth2Response? = when (registrationId) {
            "kakao" -> KakaoResponse(oAuth2User?.attributes ?: emptyMap())
            else -> null
        }

        val username = "${oAuth2Response?.provider} ${oAuth2Response?.providerId}"
        val name = oAuth2Response?.name ?: "Unknown"
        val role = "ROLE_USER"

        val existMember = memberRepository.findByUsername(username)

        val savedMember = if (existMember == null) {
            val member = Member(
                username = username,
                name = name,
                role = role
            )
            memberRepository.save(member)
        } else {
            existMember.name = name
            memberRepository.save(existMember)
        }

        val memberDto = MemberDto(
            id = savedMember.id!!,
            username = savedMember.username,
            name = savedMember.name,
            role = savedMember.role
        )

        return CustomOAuth2User(memberDto)

    }
}