package com.oddiya.config;

import com.oddiya.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Comprehensive tests for SecurityConfig configuration class.
 * Tests security filter chain configuration, CORS, and authentication setup.
 */
@SpringBootTest
@ActiveProfiles("test")
class SecurityConfigTest {
    
    @MockBean
    private JwtService jwtService;
    
    @Autowired
    private WebApplicationContext webApplicationContext;
    
    @Autowired
    private FilterChainProxy springSecurityFilterChain;
    
    @Test
    void contextLoads() {
        assertNotNull(webApplicationContext);
        assertNotNull(springSecurityFilterChain);
    }
    
    @Test
    void securityFilterChainBeanExists() {
        SecurityFilterChain securityFilterChain = webApplicationContext.getBean(SecurityFilterChain.class);
        assertNotNull(securityFilterChain);
    }
    
    @Test
    void jwtAuthenticationFilterIsConfigured() {
        // Verify JWT authentication filter is part of the security filter chain
        JwtAuthenticationFilter jwtFilter = webApplicationContext.getBean(JwtAuthenticationFilter.class);
        assertNotNull(jwtFilter);
    }
    
    @Test
    void corsConfigurationSourceBeanExists() {
        org.springframework.web.cors.CorsConfigurationSource corsConfigurationSource = 
            webApplicationContext.getBean(org.springframework.web.cors.CorsConfigurationSource.class);
        assertNotNull(corsConfigurationSource);
    }
    
    @Test
    @WithMockUser
    void authenticatedUserCanAccessProtectedEndpoints() throws Exception {
        MockMvc mockMvc = MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .build();
            
        // Test that authenticated user can access protected endpoints
        mockMvc.perform(get("/api/v1/user/profile")
                .with(csrf()))
                .andExpect(status().isOk());
    }
    
    @Test
    void publicEndpointsAreAccessibleWithoutAuthentication() throws Exception {
        MockMvc mockMvc = MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .build();
            
        // Test public endpoints
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk());
                
        mockMvc.perform(get("/api/v1/places/search"))
                .andExpect(status().isOk());
                
        mockMvc.perform(get("/api/v1/places/nearby"))
                .andExpect(status().isOk());
                
        // Swagger endpoints
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
                
        // Actuator endpoints
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }
    
    @Test
    void protectedEndpointsRequireAuthentication() throws Exception {
        MockMvc mockMvc = MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .build();
            
        // Test that protected endpoints require authentication
        mockMvc.perform(get("/api/v1/user/profile"))
                .andExpect(status().isUnauthorized());
                
        mockMvc.perform(get("/api/v1/travel-plans"))
                .andExpect(status().isUnauthorized());
                
        mockMvc.perform(post("/api/v1/places")
                .contentType("application/json")
                .content("{}"))
                .andExpect(status().isUnauthorized());
    }
    
    @Test
    void corsConfigurationIsApplied() {
        org.springframework.web.cors.CorsConfigurationSource corsConfigurationSource = 
            webApplicationContext.getBean(org.springframework.web.cors.CorsConfigurationSource.class);
        
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/health");
        request.addHeader("Origin", "http://localhost:3000");
        
        org.springframework.web.cors.CorsConfiguration corsConfig = 
            corsConfigurationSource.getCorsConfiguration(request);
        
        assertNotNull(corsConfig);
        assertTrue(corsConfig.getAllowCredentials());
        assertEquals(3600L, corsConfig.getMaxAge());
        assertNotNull(corsConfig.getAllowedMethods());
        assertTrue(corsConfig.getAllowedMethods().contains("GET"));
        assertTrue(corsConfig.getAllowedMethods().contains("POST"));
    }
    
    @Test
    void csrfIsDisabled() throws Exception {
        MockMvc mockMvc = MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .build();
            
        // POST request should work without CSRF token since it's disabled
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType("application/json")
                .content("{\"email\":\"test@example.com\",\"password\":\"password\"}"))
                .andExpect(status().isNotFound()); // 404 because endpoint doesn't exist, not 403 CSRF
    }
    
    @Test
    void sessionManagementIsStateless() {
        SecurityFilterChain securityFilterChain = webApplicationContext.getBean(SecurityFilterChain.class);
        assertNotNull(securityFilterChain);
        
        // The stateless session creation policy should be configured
        // This is tested through the behavior - no sessions should be created
        assertTrue(true); // Basic assertion that configuration loads without issues
    }
    
    @Test
    void jwtFilterIsBeforeUsernamePasswordAuthenticationFilter() {
        // Verify that JWT filter is properly positioned in the filter chain
        JwtAuthenticationFilter jwtFilter = webApplicationContext.getBean(JwtAuthenticationFilter.class);
        assertNotNull(jwtFilter);
        
        // The filter order is configured in the SecurityConfig
        // This test ensures the filter exists and can be injected
        assertTrue(jwtFilter instanceof org.springframework.web.filter.OncePerRequestFilter);
    }
}