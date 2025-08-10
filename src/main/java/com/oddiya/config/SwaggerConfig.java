package com.oddiya.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {
    
    @Value("${server.port:8080}")
    private String serverPort;
    
    @Value("${app.version:1.0.0}")
    private String appVersion;
    
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Oddiya Travel Planning API")
                .version(appVersion)
                .description("""
                    AI-powered travel planning and video generation service API.
                    
                    ## Features
                    - OAuth2 authentication (Google, Apple)
                    - AI-powered travel plan generation using AWS Bedrock
                    - Photo to video shorts generation
                    - Place discovery and recommendations
                    - Real-time notifications
                    
                    ## Authentication
                    Most endpoints require Bearer token authentication.
                    Include the token in the Authorization header: `Bearer {accessToken}`
                    """)
                .contact(new Contact()
                    .name("Oddiya API Support")
                    .email("api-support@oddiya.com"))
                .license(new License()
                    .name("Apache 2.0")
                    .url("https://www.apache.org/licenses/LICENSE-2.0.html")))
            .servers(List.of(
                new Server().url("https://api.oddiya.com/api/v1").description("Production server"),
                new Server().url("https://api-test.oddiya.com/api/v1").description("Test server"),
                new Server().url("http://localhost:" + serverPort + "/api/v1").description("Local development")
            ))
            .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
            .components(new Components()
                .addSecuritySchemes("bearerAuth", new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("JWT token obtained from login endpoint")));
    }
}