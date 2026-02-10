package my.reviewing.reviewing_V2.global.security

import jakarta.servlet.http.HttpServletRequest
import my.reviewing.reviewing_V2.global.jwt.JWTFilter
import my.reviewing.reviewing_V2.member.service.CustomOAuth2MemberService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
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
//                .requestMatchers("/").permitAll()
//                .requestMatchers("/api/auth/refresh").permitAll()
//                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .anyRequest().permitAll()
        }

        http.sessionManagement { session ->
            session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
        }

        return http.build()
    }

}