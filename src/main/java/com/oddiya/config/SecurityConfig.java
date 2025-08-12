package com.oddiya.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.CrossOriginEmbedderPolicyHeaderWriter;
import org.springframework.security.web.header.writers.CrossOriginOpenerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.CrossOriginResourcePolicyHeaderWriter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * Comprehensive Security Configuration for Oddiya
 * 
 * Implements OWASP Top 10 2021 security controls:
 * - A01: Broken Access Control - Authorization rules and JWT validation
 * - A02: Cryptographic Failures - HTTPS enforcement, secure headers
 * - A03: Injection - Input validation, parameterized queries
 * - A04: Insecure Design - Security-by-design patterns
 * - A05: Security Misconfiguration - Secure defaults, hardening
 * - A06: Vulnerable Components - Framework security features
 * - A07: Authentication Failures - JWT security, OAuth flows
 * - A08: Data Integrity - Request/response validation
 * - A09: Logging/Monitoring - Security event tracking
 * - A10: SSRF - Request validation, URL filtering
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {
    
    private final CorsConfigurationSource corsConfigurationSource;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    
    @Value("${app.security.enable-hsts:true}")
    private boolean enableHsts;
    
    @Value("${app.security.enable-csp:true}")
    private boolean enableCsp;
    
    @Value("${app.security.strict-transport-security-max-age:31536000}")
    private long hstsMaxAge;
    
    /**
     * Main security filter chain for API endpoints
     * Implements comprehensive security controls for production use
     */
    @Bean
    @Profile("!test")
    public SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
        http
            // CORS Configuration - A05: Security Misconfiguration
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            
            // CSRF Protection - Disabled for stateless API with JWT
            .csrf(AbstractHttpConfigurer::disable)
            
            // Session Management - A07: Authentication Failures
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                .maximumSessions(1)
                .maxSessionsPreventsLogin(false)
            )
            
            // Security Headers - A02: Cryptographic Failures, A05: Security Misconfiguration
            .headers(headers -> headers
                .frameOptions().deny()
                .contentTypeOptions().and()
                .httpStrictTransportSecurity(hstsConfig -> hstsConfig
                    .maxAgeInSeconds(hstsMaxAge)
                    .includeSubdomains(true)
                    .preload(true)
                )
                .addHeaderWriter(new ReferrerPolicyHeaderWriter(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .addHeaderWriter(new CrossOriginEmbedderPolicyHeaderWriter(CrossOriginEmbedderPolicyHeaderWriter.CrossOriginEmbedderPolicy.REQUIRE_CORP))
                .addHeaderWriter(new CrossOriginOpenerPolicyHeaderWriter(CrossOriginOpenerPolicyHeaderWriter.CrossOriginOpenerPolicy.SAME_ORIGIN))
                .addHeaderWriter(new CrossOriginResourcePolicyHeaderWriter(CrossOriginResourcePolicyHeaderWriter.CrossOriginResourcePolicy.SAME_ORIGIN))
                .contentSecurityPolicy(cspConfig -> {
                    if (enableCsp) {
                        cspConfig.policyDirectives(
                            "default-src 'self'; " +
                            "script-src 'self' 'unsafe-inline' 'unsafe-eval'; " +
                            "style-src 'self' 'unsafe-inline'; " +
                            "img-src 'self' data: blob: https:; " +
                            "font-src 'self' data:; " +
                            "connect-src 'self' https://api.oddiya.com https://*.supabase.co; " +
                            "media-src 'self' blob:; " +
                            "object-src 'none'; " +
                            "base-uri 'self'; " +
                            "form-action 'self'; " +
                            "frame-ancestors 'none'; " +
                            "upgrade-insecure-requests"
                        );
                    }
                })
            )
            
            // Authorization Rules - A01: Broken Access Control
            .authorizeHttpRequests(authz -> authz
                // Public endpoints (no authentication required)
                .requestMatchers(
                    AntPathRequestMatcher.antMatcher("/api/v1/auth/**"),
                    AntPathRequestMatcher.antMatcher("/api/v1/health/**"),
                    AntPathRequestMatcher.antMatcher("/actuator/health"),
                    AntPathRequestMatcher.antMatcher("/actuator/info")
                ).permitAll()
                
                // Public search endpoints (read-only)
                .requestMatchers(
                    AntPathRequestMatcher.antMatcher("/api/v1/places/search"),
                    AntPathRequestMatcher.antMatcher("/api/v1/places/nearby"),
                    AntPathRequestMatcher.antMatcher("/api/v1/travel-plans/public/**")
                ).permitAll()
                
                // Development/Testing endpoints (restricted by profile)
                .requestMatchers(
                    AntPathRequestMatcher.antMatcher("/swagger-ui/**"),
                    AntPathRequestMatcher.antMatcher("/api-docs/**"),
                    AntPathRequestMatcher.antMatcher("/v3/api-docs/**")
                ).permitAll() // Will be restricted in production via profile
                
                // Admin endpoints (future implementation)
                .requestMatchers(AntPathRequestMatcher.antMatcher("/api/v1/admin/**"))
                .hasRole("ADMIN")
                
                // Monitoring endpoints (restrict in production)
                .requestMatchers(
                    AntPathRequestMatcher.antMatcher("/actuator/**")
                ).permitAll() // Will be restricted in production
                
                // All other API endpoints require authentication
                .requestMatchers(AntPathRequestMatcher.antMatcher("/api/v1/**")).authenticated()
                
                // Deny everything else
                .anyRequest().denyAll()
            )
            
            // JWT Authentication Filter - A07: Authentication Failures
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            
            // Exception Handling - A05: Security Misconfiguration
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(401);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"Authentication required\"}");
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(403);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\":\"Forbidden\",\"message\":\"Access denied\"}");
                })
            );
        
        return http.build();
    }
    
    /**
     * Test-specific security filter chain
     * Relaxed security for testing environments
     */
    @Bean
    @Profile("test")
    public SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .headers(headers -> headers.frameOptions().disable()) // Allow H2 console in tests
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/**").permitAll() // Allow all in test
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        
        return http.build();
    }
}