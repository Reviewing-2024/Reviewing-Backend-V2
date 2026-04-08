package my.reviewing.reviewing_V2.member.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import my.reviewing.reviewing_V2.global.api.ApiResponse
import my.reviewing.reviewing_V2.global.error.BusinessException
import my.reviewing.reviewing_V2.global.error.ErrorCode
import my.reviewing.reviewing_V2.global.jwt.JWTUtil
import my.reviewing.reviewing_V2.member.repository.MemberRepository
import io.jsonwebtoken.ExpiredJwtException
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "로그인/회원가입 API")
@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val jwtUtil: JWTUtil,
    private val memberRepository: MemberRepository
) {

    companion object {
        private val logger = LoggerFactory.getLogger(AuthController::class.java)
    }

    @Operation(
        summary = "로그인 후 Access Token 발급",
        description = "OAuth2 로그인 후 refresh token(쿠키)으로 access token 발급. Authorization 헤더로 Bearer 형식의 access token이 반환"
    )
    @ApiResponses(
        value = [
            SwaggerApiResponse(
                responseCode = "200",
                description = "Access token 발급 성공",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ApiResponse::class),
                    examples = [ExampleObject(
                        value = """{"success": true, "data": null, "error": null}"""
                    )]
                )],
                headers = [io.swagger.v3.oas.annotations.headers.Header(
                    name = "Authorization",
                    description = "Bearer {access_token}",
                    schema = Schema(type = "string", example = "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
                )]
            ),
            SwaggerApiResponse(
                responseCode = "401",
                description = "Refresh token 없음, 만료, 또는 유효하지 않음",
                content = [Content(
                    mediaType = "application/json",
                    examples = [
                        ExampleObject(
                            name = "Refresh token 없음",
                            value = """{"success": false, "data": null, "error": {"code": "AUTH_401_UNAUTHORIZED", "message": "Refresh token이 없습니다.", "details": null}}"""
                        ),
                        ExampleObject(
                            name = "Refresh token 만료",
                            value = """{"success": false, "data": null, "error": {"code": "AUTH_401_EXPIRED_REFRESH", "message": "Refresh token이 만료되었습니다.", "details": null}}"""
                        ),
                        ExampleObject(
                            name = "유효하지 않은 Refresh token",
                            value = """{"success": false, "data": null, "error": {"code": "AUTH_401_INVALID_REFRESH", "message": "유효하지 않은 refresh token입니다.", "details": null}}"""
                        )
                    ]
                )]
            ),
            SwaggerApiResponse(
                responseCode = "404",
                description = "사용자를 찾을 수 없음",
                content = [Content(
                    mediaType = "application/json",
                    examples = [ExampleObject(
                        value = """{"success": false, "data": null, "error": {"code": "COMMON_404_NOT_FOUND", "message": "사용자를 찾을 수 없습니다.", "details": null}}"""
                    )]
                )]
            )
        ]
    )
    @PostMapping("/access")
    fun issueAccessToken(request: HttpServletRequest, response: HttpServletResponse): ResponseEntity<ApiResponse<Map<String, String>>> {
        // 1. 쿠키에서 refresh token 가져오기 (무조건 있어야 함)
        val refreshToken = request.cookies
            ?.firstOrNull { it.name == "refresh" }
            ?.value
            ?: throw BusinessException(ErrorCode.UNAUTHORIZED, "Refresh token이 없습니다.")

        // 2. refresh token 검증
        try {
            if (jwtUtil.isExpired(refreshToken)) {
                throw BusinessException(ErrorCode.EXPIRED_REFRESH)
            }

            if (jwtUtil.getCategory(refreshToken) != "refresh") {
                throw BusinessException(ErrorCode.INVALID_REFRESH)
            }
        } catch (e: BusinessException) {
            throw e
        } catch (e: ExpiredJwtException) {
            throw BusinessException(ErrorCode.EXPIRED_REFRESH)
        } catch (e: Exception) {
            logger.error("JWT validation failed: ${e.javaClass.simpleName} - ${e.message}")
            throw BusinessException(ErrorCode.INVALID_REFRESH)
        }

        // 3. userId 추출 및 role 조회
        val userId = jwtUtil.getUserId(refreshToken)
        val member = memberRepository.findById(userId).orElseThrow {
            BusinessException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다.")
        }

        // 4. access token 생성 (헤더로 전달)
        val accessToken = jwtUtil.createAccessToken(userId, member.role)
        println(accessToken)
        response.setHeader("Authorization", "Bearer $accessToken")

        return ResponseEntity.ok().body(ApiResponse.ok(mapOf("name" to member.name)))
    }

    @Operation(
        summary = "Access, Refresh Token 재발급",
        description = "Refresh token(쿠키)으로 새로운 access token과 refresh token을 재발급합니다. Authorization 헤더로 Bearer 형식의 access token이, refresh 쿠키로 새 refresh token이 반환됩니다."
    )
    @ApiResponses(
        value = [
            SwaggerApiResponse(
                responseCode = "200",
                description = "토큰 재발급 성공",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ApiResponse::class),
                    examples = [ExampleObject(
                        value = """{"success": true, "data": null, "error": null}"""
                    )]
                )],
                headers = [io.swagger.v3.oas.annotations.headers.Header(
                    name = "Authorization",
                    description = "Bearer {access_token}",
                    schema = Schema(type = "string", example = "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
                ), io.swagger.v3.oas.annotations.headers.Header(
                    name = "Set-Cookie",
                    description = "refresh={refresh_token}; Max-Age=86400; Path=/; HttpOnly",
                    schema = Schema(type = "string", example = "refresh=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...; Max-Age=86400; Path=/; HttpOnly")
                )]
            ),
            SwaggerApiResponse(
                responseCode = "401",
                description = "Refresh token 없음, 만료, 또는 유효하지 않음",
                content = [Content(
                    mediaType = "application/json",
                    examples = [
                        ExampleObject(
                            name = "Refresh token 없음",
                            value = """{"success": false, "data": null, "error": {"code": "AUTH_401_UNAUTHORIZED", "message": "Refresh token이 없습니다.", "details": null}}"""
                        ),
                        ExampleObject(
                            name = "Refresh token 만료",
                            value = """{"success": false, "data": null, "error": {"code": "AUTH_401_EXPIRED_REFRESH", "message": "Refresh token이 만료되었습니다.", "details": null}}"""
                        ),
                        ExampleObject(
                            name = "유효하지 않은 Refresh token",
                            value = """{"success": false, "data": null, "error": {"code": "AUTH_401_INVALID_REFRESH", "message": "유효하지 않은 refresh token입니다.", "details": null}}"""
                        )
                    ]
                )]
            ),
            SwaggerApiResponse(
                responseCode = "404",
                description = "사용자를 찾을 수 없음",
                content = [Content(
                    mediaType = "application/json",
                    examples = [ExampleObject(
                        value = """{"success": false, "data": null, "error": {"code": "COMMON_404_NOT_FOUND", "message": "사용자를 찾을 수 없습니다.", "details": null}}"""
                    )]
                )]
            )
        ]
    )
    @PostMapping("/reissue")
    fun reissue(request: HttpServletRequest, response: HttpServletResponse): ApiResponse<Unit> {
        // 1. 쿠키에서 refresh token 가져오기
        val refreshToken = request.cookies
            ?.firstOrNull { it.name == "refresh" }
            ?.value
            ?: throw BusinessException(ErrorCode.UNAUTHORIZED, "Refresh token이 없습니다.")

        // 2. refresh token 검증
        try {
            if (jwtUtil.isExpired(refreshToken)) {
                throw BusinessException(ErrorCode.EXPIRED_REFRESH)
            }

            if (jwtUtil.getCategory(refreshToken) != "refresh") {
                throw BusinessException(ErrorCode.INVALID_REFRESH)
            }
        } catch (e: BusinessException) {
            throw e
        } catch (e: ExpiredJwtException) {
            throw BusinessException(ErrorCode.EXPIRED_REFRESH)
        } catch (e: Exception) {
            logger.error("JWT validation failed: ${e.javaClass.simpleName} - ${e.message}")
            throw BusinessException(ErrorCode.INVALID_REFRESH)
        }

        // 3. userId 추출 및 role 조회
        val userId = jwtUtil.getUserId(refreshToken)
        val member = memberRepository.findById(userId).orElseThrow {
            BusinessException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다.")
        }

        // 4. 새로운 access token 생성 (헤더로 전달)
        val newAccessToken = jwtUtil.createAccessToken(userId, member.role)
        response.setHeader("Authorization", "Bearer $newAccessToken")

        // 5. 새로운 refresh token 생성 (쿠키로 전달)
        val newRefreshToken = jwtUtil.createRefreshToken(userId)
        response.addCookie(createCookie("refresh", newRefreshToken))

        return ApiResponse.ok()
    }

    @Operation(
        summary = "로그아웃",
        description = "Refresh token 쿠키를 삭제하여 로그아웃합니다."
    )
    @ApiResponses(
        value = [
            SwaggerApiResponse(
                responseCode = "200",
                description = "로그아웃 성공",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ApiResponse::class),
                    examples = [ExampleObject(
                        value = """{"success": true, "data": null, "error": null}"""
                    )]
                )],
                headers = [io.swagger.v3.oas.annotations.headers.Header(
                    name = "Set-Cookie",
                    description = "refresh=; Max-Age=0; Path=/",
                    schema = Schema(type = "string", example = "refresh=; Max-Age=0; Path=/")
                )]
            )
        ]
    )
    @PostMapping("/logout")
    fun logout(response: HttpServletResponse): ApiResponse<Unit> {
        // refresh token 쿠키 삭제
        val cookie = Cookie("refresh", null).apply {
            maxAge = 0
            path = "/"
        }
        response.addCookie(cookie)

        return ApiResponse.ok()
    }

    private fun createCookie(key: String, value: String): Cookie {
        return Cookie(key, value).apply {
            maxAge = 24 * 60 * 60  // 24시간
            path = "/"
            isHttpOnly = true
            // secure = true  // HTTPS 환경에서만 전송 (프로덕션에서 활성화)
        }
    }
}