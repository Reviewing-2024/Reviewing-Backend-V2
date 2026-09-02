package my.reviewing.reviewing_V2.global.channeltalk

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Service
class ChannelTalkService(
    @Value("\${channelTalk.secret-key}") private val secretKey: String
) {

    fun encode(memberId: String): String {
        return try {
            val keyBytes = hexStringToByteArray(secretKey)
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(keyBytes, "HmacSHA256"))
            val hash = mac.doFinal(memberId.toByteArray())

            val sb = StringBuilder(hash.size * 2)
            for (b in hash) {
                sb.append(String.format("%02x", b))
            }
            sb.toString()
        } catch (e: Exception) {
            throw RuntimeException("HMAC encoding failed", e)
        }
    }

    private fun hexStringToByteArray(hex: String): ByteArray {
        val len = hex.length
        require(len % 2 == 0) { "Invalid hex string" }
        return ByteArray(len / 2) { i ->
            ((Character.digit(hex[i * 2], 16) shl 4) + Character.digit(hex[i * 2 + 1], 16)).toByte()
        }
    }
}
