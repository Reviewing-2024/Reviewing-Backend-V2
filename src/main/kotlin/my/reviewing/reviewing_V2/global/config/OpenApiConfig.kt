package my.reviewing.reviewing_V2.global.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.servers.Server
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {

    @Bean
    fun openApi(): OpenAPI {

        return OpenAPI()
            .info(Info()
                .title("Reviewing-V2 API 목록 ")
                .version("v1.0.0"))
            .servers(listOf(
                Server().url("http://localhost:8080").description("local"),
                Server().url("https://api.reviewing.kr").description("production")
            ))
            .components(Components()
                .addSecuritySchemes("JWT", SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .`in`(SecurityScheme.In.HEADER)
                    .name("Authorization")))

    }



}