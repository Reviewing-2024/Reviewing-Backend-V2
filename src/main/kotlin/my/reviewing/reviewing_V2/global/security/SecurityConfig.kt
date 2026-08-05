package my.reviewing.reviewing_V2.global.security

import jakarta.servlet.http.HttpServletRequest
import my.reviewing.reviewing_V2.global.jwt.JWTFilter
import my.reviewing.reviewing_V2.member.service.CustomOAuth2MemberService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val customOAuth2MemberService: CustomOAuth2MemberService,
    private val customSuccessHandler: CustomSuccessHandler,
    private val jwtFilter: JWTFilter,
    private val jwtAuthenticationEntryPoint: JwtAuthenticationEntryPoint,
    private val jwtAccessDeniedHandler: JwtAccessDeniedHandler
) {

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {

        http.cors {
            it.configurationSource { _: HttpServletRequest ->
                CorsConfiguration().apply {
                    allowedOrigins = listOf("http://localhost:5173")
                    allowedMethods = listOf("*")
                    allowedHeaders = listOf("*")
                    allowCredentials = true
                    maxAge = 3600L

                    exposedHeaders = listOf("Set-Cookie", "Authorization")
                }
            }
        }

        http.csrf { it.disable() }
        http.formLogin { it.disable() }
        http.httpBasic { it.disable() }

        http.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter::class.java)

        http.oauth2Login { oauth2 ->
            oauth2.userInfoEndpoint { userInfo ->
                userInfo.userService(customOAuth2MemberService) // 회원 저장
            }
                .successHandler(customSuccessHandler) // 로그인 성공 후 refresh token 발급
        }

        http
            .exceptionHandling {
                it.authenticationEntryPoint(jwtAuthenticationEntryPoint) // 401
                it.accessDeniedHandler(jwtAccessDeniedHandler)           // 403
            }

        // path, role 설정하기
        http.authorizeHttpRequests { auth ->
            auth
                // 관리자 전용
                .requestMatchers("/api/v1/admin/**").hasAuthority("ROLE_ADMIN")
                .requestMatchers("/api/v1/crawling/**").hasAuthority("ROLE_ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/api/v1/members/*/role/admin").hasAuthority("ROLE_ADMIN")

                // 마이페이지 (로그인 필요)
                .requestMatchers("/api/v1/members/me/**").authenticated()

                // 리뷰 작성, 좋아요/싫어요
                .requestMatchers(HttpMethod.POST, "/api/v1/reviews/**").authenticated()
                .requestMatchers(HttpMethod.DELETE, "/api/v1/reviews/**").authenticated()

                // 찜
                .requestMatchers(HttpMethod.POST, "/api/v1/courses/*/wish").authenticated()
                .requestMatchers(HttpMethod.DELETE, "/api/v1/courses/*/wish").authenticated()

                .anyRequest().permitAll()
        }

        http.sessionManagement { session ->
            session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
        }

        return http.build()
    }

}