package my.reviewing.reviewing_V2.member.dto

class KakaoResponse(
    private val attributes: Map<String, Any>
) : OAuth2Response {

    override val provider: String = "kakao"

    override val providerId: String
        get() = attributes["id"].toString()

    override val name: String
        get() {
            val kakaoAccount = attributes["kakao_account"] as? Map<*, *>
            val profile = kakaoAccount?.get("profile") as? Map<*, *>
            return profile?.get("nickname")?.toString() ?: "Unknown"
        }

}