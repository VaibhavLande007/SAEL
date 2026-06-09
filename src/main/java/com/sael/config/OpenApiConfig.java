package com.sael.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        final String bearerSchemeName = "bearerAuth";
        final String adminSecretSchemeName = "x-admin-secret";

        return new OpenAPI()
                .addSecurityItem(new SecurityRequirement()
                        .addList(bearerSchemeName)
                        .addList(adminSecretSchemeName))
                .components(new Components()
                        .addSecuritySchemes(bearerSchemeName,
                                new SecurityScheme()
                                        .name(bearerSchemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Enter your JWT token in the format: Bearer <token>"))
                        .addSecuritySchemes(adminSecretSchemeName,
                                new SecurityScheme()
                                        .name("x-admin-secret")
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.HEADER)
                                        .name("x-admin-secret")
                                        .description("Enter the admin secret key for platform administration endpoints")));
    }
}
