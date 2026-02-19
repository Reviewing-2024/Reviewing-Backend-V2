package my.reviewing.reviewing_V2.global.jwt

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import javax.crypto.SecretKey

@Component
class JWTUtil(
    @Value("\${spring.jwt.secret}") secret: String
) {

    private val secretKey: SecretKey = Keys.hmacShaKeyFor(
        Decoders.BASE64.decode(secret)
    )

    // 토큰을 한 번만 파싱해서 Claims 반환 (만료 시 ExpiredJwtException throw)
    fun getClaims(token: String): Claims {
        return Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .payload
    }

    fun getUserId(token: String): Long {
        return Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .payload
            .get("userId", java.lang.Long::class.java).toLong()
    }

    fun getRole(token: String): String {
        return Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .payload
            .get("role", String::class.java)
    }

    fun isExpired(token: String): Boolean {
        return Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .payload
            .expiration
            .before(java.util.Date())
    }

    fun createAccessToken(userId: Long, role: String): String {
        val expiredMs = 60 * 60 * 1000L  // 1시간
        return Jwts.builder()
            .claim("category", "access")
            .claim("userId", userId)
            .claim("role", role)
            .issuedAt(java.util.Date(System.currentTimeMillis()))
            .expiration(java.util.Date(System.currentTimeMillis() + expiredMs))
            .signWith(secretKey)
            .compact()
    }

    fun createRefreshToken(userId: Long): String {
        val expiredMs = 24 * 60 * 60 * 1000L  // 24시간
        return Jwts.builder()
            .claim("category", "refresh")
            .claim("userId", userId)
            .issuedAt(java.util.Date(System.currentTimeMillis()))
            .expiration(java.util.Date(System.currentTimeMillis() + expiredMs))
            .signWith(secretKey)
            .compact()
    }

    fun getCategory(token: String): String {
        return Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .payload
            .get("category", String::class.java)
    }
}