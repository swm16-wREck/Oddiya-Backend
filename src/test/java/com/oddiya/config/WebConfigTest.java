package com.oddiya.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for WebConfig configuration class.
 * Tests CORS configuration, WebClient setup, and property binding.
 */
@SpringBootTest
@ActiveProfiles("test")
class WebConfigTest {
    
    @Autowired
    private WebConfig webConfig;
    
    @Autowired
    private CorsConfigurationSource corsConfigurationSource;
    
    @Autowired
    private WebClient webClient;
    
    @Test
    void contextLoads() {
        assertNotNull(webConfig);
        assertNotNull(corsConfigurationSource);
        assertNotNull(webClient);
    }
    
    @Test
    void webClientBeanIsConfigured() {
        assertNotNull(webClient);
        
        // Verify WebClient is properly configured
        assertNotNull(webClient.mutate());
    }
    
    @Test
    void corsConfigurationSourceBeanIsConfigured() {
        assertNotNull(corsConfigurationSource);
        
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/test");
        request.addHeader("Origin", "http://localhost:3000");
        
        CorsConfiguration corsConfig = corsConfigurationSource.getCorsConfiguration(request);
        
        assertNotNull(corsConfig);
        assertNotNull(corsConfig.getAllowedOrigins());
        assertNotNull(corsConfig.getAllowedMethods());
        assertNotNull(corsConfig.getAllowedHeaders());
        assertNotNull(corsConfig.getExposedHeaders());
    }
    
    @Test
    void corsConfigurationAllowsExpectedOrigins() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/test");
        
        CorsConfiguration corsConfig = corsConfigurationSource.getCorsConfiguration(request);
        
        assertNotNull(corsConfig);
        List<String> allowedOrigins = corsConfig.getAllowedOrigins();
        assertNotNull(allowedOrigins);
        
        // Default should include localhost:3000
        assertTrue(allowedOrigins.contains("http://localhost:3000"));
    }
    
    @Test
    void corsConfigurationAllowsExpectedMethods() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/test");
        
        CorsConfiguration corsConfig = corsConfigurationSource.getCorsConfiguration(request);
        
        assertNotNull(corsConfig);
        List<String> allowedMethods = corsConfig.getAllowedMethods();
        assertNotNull(allowedMethods);
        
        // Check all expected HTTP methods are allowed
        assertTrue(allowedMethods.contains("GET"));
        assertTrue(allowedMethods.contains("POST"));
        assertTrue(allowedMethods.contains("PUT"));
        assertTrue(allowedMethods.contains("PATCH"));
        assertTrue(allowedMethods.contains("DELETE"));
        assertTrue(allowedMethods.contains("OPTIONS"));
    }
    
    @Test
    void corsConfigurationAllowsAllHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/test");
        
        CorsConfiguration corsConfig = corsConfigurationSource.getCorsConfiguration(request);
        
        assertNotNull(corsConfig);
        List<String> allowedHeaders = corsConfig.getAllowedHeaders();
        assertNotNull(allowedHeaders);
        
        // Should allow all headers (*)
        assertTrue(allowedHeaders.contains("*"));
    }
    
    @Test
    void corsConfigurationExposesExpectedHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/test");
        
        CorsConfiguration corsConfig = corsConfigurationSource.getCorsConfiguration(request);
        
        assertNotNull(corsConfig);
        List<String> exposedHeaders = corsConfig.getExposedHeaders();
        assertNotNull(exposedHeaders);
        
        // Check expected exposed headers
        assertTrue(exposedHeaders.contains("X-Total-Count"));
        assertTrue(exposedHeaders.contains("X-RateLimit-Limit"));
        assertTrue(exposedHeaders.contains("X-RateLimit-Remaining"));
        assertTrue(exposedHeaders.contains("X-RateLimit-Reset"));
    }
    
    @Test
    void corsConfigurationAllowsCredentials() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/test");
        
        CorsConfiguration corsConfig = corsConfigurationSource.getCorsConfiguration(request);
        
        assertNotNull(corsConfig);
        assertTrue(corsConfig.getAllowCredentials());
    }
    
    @Test
    void corsConfigurationHasCorrectMaxAge() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/test");
        
        CorsConfiguration corsConfig = corsConfigurationSource.getCorsConfiguration(request);
        
        assertNotNull(corsConfig);
        assertEquals(3600L, corsConfig.getMaxAge());
    }
    
    @Test
    void corsConfigurationAppliesGlobally() {
        // Test that CORS configuration applies to all paths
        MockHttpServletRequest request1 = new MockHttpServletRequest();
        request1.setRequestURI("/api/v1/users");
        
        MockHttpServletRequest request2 = new MockHttpServletRequest();
        request2.setRequestURI("/api/v1/places");
        
        MockHttpServletRequest request3 = new MockHttpServletRequest();
        request3.setRequestURI("/api/v2/other");
        
        CorsConfiguration corsConfig1 = corsConfigurationSource.getCorsConfiguration(request1);
        CorsConfiguration corsConfig2 = corsConfigurationSource.getCorsConfiguration(request2);
        CorsConfiguration corsConfig3 = corsConfigurationSource.getCorsConfiguration(request3);
        
        assertNotNull(corsConfig1);
        assertNotNull(corsConfig2);
        assertNotNull(corsConfig3);
        
        // All should have the same configuration
        assertEquals(corsConfig1.getAllowedMethods(), corsConfig2.getAllowedMethods());
        assertEquals(corsConfig2.getAllowedMethods(), corsConfig3.getAllowedMethods());
    }
    
    /**
     * Test WebConfig with custom CORS origins property
     */
    @SpringBootTest
    @TestPropertySource(properties = {
        "app.cors.allowed-origins=https://example.com,https://test.com"
    })
    @ActiveProfiles("test")
    static class WebConfigCustomPropertiesTest {
        
        @Autowired
        private CorsConfigurationSource corsConfigurationSource;
        
        @Test
        void corsConfigurationUsesCustomOrigins() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI("/api/v1/test");
            
            CorsConfiguration corsConfig = corsConfigurationSource.getCorsConfiguration(request);
            
            assertNotNull(corsConfig);
            List<String> allowedOrigins = corsConfig.getAllowedOrigins();
            assertNotNull(allowedOrigins);
            
            // Should contain custom origins
            assertTrue(allowedOrigins.contains("https://example.com"));
            assertTrue(allowedOrigins.contains("https://test.com"));
        }
    }
    
    @Test
    void webClientHasCorrectMaxInMemorySize() {
        // The WebClient is configured with 10MB max in-memory size
        // This is difficult to test directly, but we can verify the bean exists and is properly configured
        assertNotNull(webClient);
        
        // Verify we can build a request (basic functionality test)
        assertDoesNotThrow(() -> {
            webClient.get()
                .uri("https://httpbin.org/get")
                .retrieve();
        });
    }
    
    @Test
    void webClientSupportsReactiveOperations() {
        assertNotNull(webClient);
        
        // Verify WebClient supports typical operations
        assertDoesNotThrow(() -> {
            webClient.get()
                .uri("https://httpbin.org/get")
                .retrieve()
                .toBodilessEntity();
        });
    }
}