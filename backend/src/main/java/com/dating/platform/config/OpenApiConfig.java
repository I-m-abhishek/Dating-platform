package com.dating.platform.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    @Bean
    public OpenAPI datingPlatformOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Dating Platform API")
                        .version("v1")
                        .description("""
                                REST + WebSocket API for the dating platform.

                                All responses use the ApiResponse envelope:
                                `{ "success": true, "data": ... }` or
                                `{ "success": false, "error": { "code", "message", "details" } }`.

                                Authenticate with `Authorization: Bearer <accessToken>`.
                                """)
                        .contact(new Contact().name("Platform Team"))
                        .license(new License().name("Proprietary")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local"),
                        new Server().url("https://api.example.com").description("Production")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER))
                .components(new Components().addSecuritySchemes(BEARER,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
