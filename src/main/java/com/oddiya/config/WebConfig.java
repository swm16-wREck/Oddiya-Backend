package com.oddiya.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * Enhanced Web Configuration with Security Hardening
 * 
 * Implements:
 * - Secure CORS configuration
 * - Security interceptors
 * - Secure WebClient configuration
 * 
 * OWASP Top 10 2021:
 * - A05: Security Misconfiguration - Secure CORS and headers
 * - A02: Cryptographic Failures - Secure communication
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {
    
    private final SecurityValidationInterceptor securityValidationInterceptor;
    
    @Value("${app.cors.allowed-origins:http://localhost:3000}")
    private List<String> allowedOrigins;
    
    @Value("${app.cors.allowed-methods:GET,POST,PUT,PATCH,DELETE,OPTIONS}")
    private List<String> allowedMethods;
    
    @Value("${app.cors.allowed-headers:Content-Type,Authorization,X-Requested-With,Accept,Accept-Language,Cache-Control}")
    private List<String> allowedHeaders;
    
    @Value("${app.cors.exposed-headers:X-Total-Count,X-RateLimit-Limit,X-RateLimit-Remaining,X-RateLimit-Reset}")
    private List<String> exposedHeaders;
    
    @Value("${app.cors.allow-credentials:true}")
    private boolean allowCredentials;
    
    @Value("${app.cors.max-age:3600}")
    private long maxAge;
    
    @Value("${app.security.strict-cors:true}")
    private boolean strictCors;
    
    /**
     * Secure WebClient configuration
     */
    @Bean
    public WebClient webClient() {
        return WebClient.builder()
                .codecs(configurer -> configurer
                        .defaultCodecs()
                        .maxInMemorySize(10 * 1024 * 1024)) // 10MB limit
                .build();
    }
    
    /**
     * Production-ready WebClient for external API calls
     */
    @Bean("secureWebClient")
    public WebClient secureWebClient() {
        return WebClient.builder()
                .codecs(configurer -> configurer
                        .defaultCodecs()
                        .maxInMemorySize(5 * 1024 * 1024)) // 5MB limit for external calls
                .build();
    }
    
    /**
     * Enhanced CORS configuration with security hardening
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        if (strictCors) {
            // Strict CORS configuration for production
            configuration.setAllowedOriginPatterns(allowedOrigins);
            configuration.setAllowedMethods(allowedMethods);
            configuration.setAllowedHeaders(allowedHeaders);
            configuration.setExposedHeaders(exposedHeaders);
            configuration.setAllowCredentials(allowCredentials);
            configuration.setMaxAge(maxAge);
            
            // Additional security settings
            configuration.setAllowPrivateNetwork(false);
            
        } else {
            // Development CORS configuration (more permissive)
            configuration.setAllowedOrigins(allowedOrigins);
            configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
            configuration.setAllowedHeaders(Arrays.asList("*"));
            configuration.setExposedHeaders(Arrays.asList("X-Total-Count", "X-RateLimit-Limit", 
                    "X-RateLimit-Remaining", "X-RateLimit-Reset", "Authorization"));
            configuration.setAllowCredentials(true);
            configuration.setMaxAge(maxAge);
        }
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        
        // More restrictive CORS for auth endpoints
        CorsConfiguration authCorsConfig = new CorsConfiguration();
        authCorsConfig.setAllowedOriginPatterns(allowedOrigins);
        authCorsConfig.setAllowedMethods(Arrays.asList("POST", "OPTIONS"));
        authCorsConfig.setAllowedHeaders(Arrays.asList("Content-Type", "Authorization"));
        authCorsConfig.setExposedHeaders(Arrays.asList("Authorization"));
        authCorsConfig.setAllowCredentials(true);
        authCorsConfig.setMaxAge(300L); // Shorter cache for auth endpoints
        source.registerCorsConfiguration("/api/v1/auth/**", authCorsConfig);
        
        return source;
    }
    
    /**
     * Register security interceptors
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(securityValidationInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/v1/health", "/actuator/**");
    }
}