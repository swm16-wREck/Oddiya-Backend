package com.oddiya.security;

import com.oddiya.config.TestSecurityConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * OWASP A07:2021 - Identification and Authentication Failures Security Tests
 * 
 * Tests various authentication bypass attack vectors on protected endpoints.
 * Verifies that protected resources require proper authentication and authorization.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebMvc
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Authentication Bypass Security Tests - OWASP A07:2021")
public class AuthenticationBypassSecurityTest {

    @Autowired
    private WebApplicationContext context;
    
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    
    // Common authentication bypass payloads
    private static final String[] AUTH_BYPASS_HEADERS = {
        "X-Forwarded-User: admin",
        "X-Remote-User: admin",
        "X-User: admin",
        "X-Username: admin",
        "X-Forwarded-For: 127.0.0.1",
        "X-Real-IP: 127.0.0.1",
        "X-Originating-IP: 127.0.0.1",
        "X-Remote-IP: 127.0.0.1",
        "X-Client-IP: 127.0.0.1"
    };
    
    // JWT bypass attempts
    private static final String[] JWT_BYPASS_TOKENS = {
        "Bearer null",
        "Bearer undefined", 
        "Bearer ",
        "Bearer admin",
        "Bearer guest",
        "Bearer anonymous",
        "Bearer eyJhbGciOiJub25lIn0.eyJzdWIiOiJhZG1pbiJ9.", // None algorithm
        "Bearer " + "x".repeat(500), // Oversized token
        "null",
        "undefined",
        "",
        "admin",
        "guest"
    };
    
    // HTTP method bypass attempts
    private static final String[] PROTECTED_ENDPOINTS = {
        "/api/v1/travel-plans",
        "/api/v1/users/profile",
        "/api/v1/travel-plans/saved",
        "/api/v1/files/upload",
        "/api/v1/users/me"
    };

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
            .webAppContextSetup(context)
            .apply(SecurityMockMvcConfigurers.springSecurity())
            .build();
            
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    @org.junit.jupiter.api.Order(1)
    @DisplayName("Authentication Bypass Test - No Authorization Header")
    void testNoAuthorizationHeader() throws Exception {
        for (String endpoint : PROTECTED_ENDPOINTS) {
            mockMvc.perform(get(endpoint)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error").exists());
        }
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    @DisplayName("Authentication Bypass Test - Invalid JWT Tokens")
    void testInvalidJwtTokens() throws Exception {
        for (String token : JWT_BYPASS_TOKENS) {
            mockMvc.perform(get("/api/v1/travel-plans")
                    .header("Authorization", token)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    @org.junit.jupiter.api.Order(3)
    @DisplayName("Authentication Bypass Test - Custom Headers")
    void testCustomHeaderBypass() throws Exception {
        for (String header : AUTH_BYPASS_HEADERS) {
            String[] parts = header.split(": ");
            String headerName = parts[0];
            String headerValue = parts[1];
            
            mockMvc.perform(get("/api/v1/travel-plans")
                    .header(headerName, headerValue)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized()); // Should still require proper JWT
        }
    }

    @Test
    @org.junit.jupiter.api.Order(4)
    @DisplayName("Authentication Bypass Test - HTTP Method Override")
    void testHttpMethodOverride() throws Exception {
        // Test various method override headers
        String[] methodOverrideHeaders = {
            "X-HTTP-Method-Override",
            "X-HTTP-Method",
            "X-Method-Override",
            "_method"
        };
        
        String[] methods = {"GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS"};
        
        for (String methodHeader : methodOverrideHeaders) {
            for (String method : methods) {
                mockMvc.perform(post("/api/v1/travel-plans")
                        .header(methodHeader, method)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                        .andExpect(status().isUnauthorized()); // Should still require auth regardless of method
            }
        }
    }

    @Test
    @org.junit.jupiter.api.Order(5)
    @DisplayName("Authentication Bypass Test - URL Path Traversal")
    void testUrlPathTraversal() throws Exception {
        String[] pathTraversalAttempts = {
            "/api/v1/../auth/validate",
            "/api/v1/travel-plans/../../../auth/validate",
            "/api/v1/travel-plans/..%2F..%2Fauth%2Fvalidate",
            "/api/v1/travel-plans/....//....//auth/validate",
            "/api/v1/travel-plans/%2e%2e%2fauth%2fvalidate"
        };
        
        for (String path : pathTraversalAttempts) {
            mockMvc.perform(get(path)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound()); // Should not bypass to unprotected endpoint
        }
    }

    @Test
    @org.junit.jupiter.api.Order(6)
    @DisplayName("Authentication Bypass Test - Case Sensitivity")
    void testCaseSensitiveBypass() throws Exception {
        String[] caseSensitiveAttempts = {
            "/API/V1/TRAVEL-PLANS",
            "/Api/V1/Travel-Plans",
            "/api/V1/TRAVEL-PLANS",
            "/api/v1/TRAVEL-PLANS",
            "/API/v1/travel-plans"
        };
        
        for (String path : caseSensitiveAttempts) {
            mockMvc.perform(get(path)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized()); // Should require auth regardless of case
        }
    }

    @Test
    @org.junit.jupiter.api.Order(7)
    @DisplayName("Authentication Bypass Test - Unicode and URL Encoding")
    void testUnicodeUrlEncodingBypass() throws Exception {
        String[] encodedPaths = {
            "/api/v1/travel%2Dplans", // URL encoded dash
            "/api/v1/travel%2dplans", // URL encoded dash (lowercase)
            "/api/v1/%74%72%61%76%65%6C%2D%70%6C%61%6E%73", // Full URL encoding
            "/api/v1/travel\u002Dplans", // Unicode dash
            "/api/v1/travel%u002Dplans", // Unicode URL encoding
            "/api/v1/travel-plans%00", // Null byte
            "/api/v1/travel-plans%20", // Space
            "/api/v1/travel-plans%0a", // Line feed
            "/api/v1/travel-plans%0d" // Carriage return
        };
        
        for (String path : encodedPaths) {
            mockMvc.perform(get(path)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized()); // Should require auth for any encoding variation
        }
    }

    @Test
    @org.junit.jupiter.api.Order(8)
    @DisplayName("Authentication Bypass Test - Request Parameter Pollution")
    void testParameterPollutionBypass() throws Exception {
        mockMvc.perform(get("/api/v1/travel-plans")
                .param("Authorization", "Bearer fake-token")
                .param("authorization", "Bearer fake-token")
                .param("AUTHORIZATION", "Bearer fake-token")
                .param("auth", "admin")
                .param("user", "admin")
                .param("role", "admin")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized()); // Should not bypass via query parameters
    }

    @Test
    @org.junit.jupiter.api.Order(9)
    @DisplayName("Authentication Bypass Test - Host Header Manipulation")
    void testHostHeaderManipulation() throws Exception {
        String[] maliciousHosts = {
            "localhost",
            "127.0.0.1", 
            "admin.example.com",
            "internal.example.com",
            "trusted.example.com",
            "0.0.0.0",
            "::1",
            "[::1]"
        };
        
        for (String host : maliciousHosts) {
            mockMvc.perform(get("/api/v1/travel-plans")
                    .header("Host", host)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized()); // Should require auth regardless of host
        }
    }

    @Test
    @org.junit.jupiter.api.Order(10)
    @DisplayName("Authentication Bypass Test - Content-Type Confusion")
    void testContentTypeConfusion() throws Exception {
        String[] contentTypes = {
            "application/x-www-form-urlencoded",
            "multipart/form-data",
            "text/plain",
            "text/html",
            "application/xml",
            "text/xml",
            "application/octet-stream",
            "",
            "application/json; charset=utf-8",
            "application/json;charset=utf-8",
            "APPLICATION/JSON"
        };
        
        for (String contentType : contentTypes) {
            mockMvc.perform(post("/api/v1/travel-plans")
                    .contentType(contentType)
                    .content("{}"))
                    .andExpect(status().isUnauthorized()); // Should require auth regardless of content type
        }
    }

    @Test
    @org.junit.jupiter.api.Order(11)
    @DisplayName("Authentication Bypass Test - Cookie-based Authentication")
    void testCookieBasedAuthBypass() throws Exception {
        String[] authCookies = {
            "auth=admin",
            "user=admin", 
            "role=admin",
            "session=admin",
            "token=admin",
            "jwt=fake-token",
            "access_token=fake-token",
            "sessionid=123456",
            "JSESSIONID=admin"
        };
        
        for (String cookie : authCookies) {
            mockMvc.perform(get("/api/v1/travel-plans")
                    .header("Cookie", cookie)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized()); // Should not accept cookie auth (JWT only)
        }
    }

    @Test
    @org.junit.jupiter.api.Order(12)
    @DisplayName("Authentication Bypass Test - HTTP Basic Auth Bypass")
    void testBasicAuthBypass() throws Exception {
        String[] basicAuthHeaders = {
            "Basic YWRtaW46YWRtaW4=", // admin:admin
            "Basic Z3Vlc3Q6Z3Vlc3Q=", // guest:guest
            "Basic dGVzdDp0ZXN0", // test:test
            "Basic cm9vdDpyb290", // root:root
            "Basic " // Empty
        };
        
        for (String basicAuth : basicAuthHeaders) {
            mockMvc.perform(get("/api/v1/travel-plans")
                    .header("Authorization", basicAuth)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized()); // Should not accept basic auth (JWT only)
        }
    }

    @Test
    @org.junit.jupiter.api.Order(13)
    @DisplayName("Authentication Bypass Test - HTTP Verb Tampering")
    void testHttpVerbTampering() throws Exception {
        // Test that all HTTP methods require authentication
        mockMvc.perform(head("/api/v1/travel-plans"))
                .andExpect(status().isUnauthorized());
                
        mockMvc.perform(options("/api/v1/travel-plans"))
                .andExpect(status().isUnauthorized());
                
        mockMvc.perform(patch("/api/v1/travel-plans")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isUnauthorized());
                
        mockMvc.perform(delete("/api/v1/travel-plans/123"))
                .andExpect(status().isUnauthorized());
                
        mockMvc.perform(put("/api/v1/travel-plans/123")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @org.junit.jupiter.api.Order(14)
    @DisplayName("Authentication Bypass Test - Request Smuggling Simulation")
    void testRequestSmugglingSimulation() throws Exception {
        // Simulate HTTP request smuggling attempts
        String[] smugglingHeaders = {
            "Transfer-Encoding: chunked",
            "Content-Length: 0",
            "Connection: close", 
            "Connection: keep-alive"
        };
        
        for (String header : smugglingHeaders) {
            String[] parts = header.split(": ");
            mockMvc.perform(post("/api/v1/travel-plans")
                    .header(parts[0], parts[1])
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
                    .andExpect(status().isUnauthorized()); // Should require auth despite smuggling attempts
        }
    }

    @Test
    @org.junit.jupiter.api.Order(15)
    @DisplayName("Authentication Bypass Test - Public Endpoint Access")
    void testPublicEndpointAccess() throws Exception {
        // Verify public endpoints are actually accessible without auth
        mockMvc.perform(get("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().is4xxClientError()); // Bad request, but not unauthorized
                
        mockMvc.perform(get("/api/v1/health")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()); // Should be accessible
                
        mockMvc.perform(get("/api/v1/places/search")
                .param("query", "seoul")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()); // Should be accessible
    }

    @Test
    @org.junit.jupiter.api.Order(16)
    @DisplayName("Authentication Bypass Test - Session Fixation")
    void testSessionFixation() throws Exception {
        // Test that session IDs in various forms don't bypass auth
        String[] sessionHeaders = {
            "X-Session-ID: admin-session",
            "X-Session: fixed-session-123",
            "Session-ID: attacker-session",
            "PHPSESSID: attacker-controlled"
        };
        
        for (String header : sessionHeaders) {
            String[] parts = header.split(": ");
            mockMvc.perform(get("/api/v1/travel-plans")
                    .header(parts[0], parts[1])
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized()); // Should not bypass via session headers
        }
    }

    @Test
    @org.junit.jupiter.api.Order(17)
    @DisplayName("Authentication Bypass Test - Authorization Header Variations")
    void testAuthorizationHeaderVariations() throws Exception {
        String[] authHeaderVariations = {
            "authorization: Bearer fake-token", // Lowercase
            "AUTHORIZATION: Bearer fake-token", // Uppercase
            "Authorization: bearer fake-token", // Lowercase scheme
            "Authorization: BEARER fake-token", // Uppercase scheme
            "Authorization:Bearer fake-token", // No space after colon
            "Authorization : Bearer fake-token", // Extra space
            "Authorization: Bearer\tfake-token", // Tab character
            "Authorization: Bearer\nfake-token", // Newline
            "Authorization: Bearer fake-token\r" // Carriage return
        };
        
        for (String authHeader : authHeaderVariations) {
            String[] parts = authHeader.split(": ", 2);
            mockMvc.perform(get("/api/v1/travel-plans")
                    .header(parts[0], parts[1])
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized()); // Should require valid JWT
        }
    }

    @Test
    @org.junit.jupiter.api.Order(18)
    @DisplayName("Authentication Bypass Test - Double Encoding")
    void testDoubleEncodingBypass() throws Exception {
        String[] doubleEncodedPaths = {
            "/api/v1/%2574%2572%2561%2576%2565%256C%252D%2570%256C%2561%256E%2573", // Double URL encoding
            "/api/v1/%252E%252E%252F%252E%252E%252Fauth%252Fvalidate", // Double encoded path traversal
            "/api/v1/travel%252Dplans" // Double encoded dash
        };
        
        for (String path : doubleEncodedPaths) {
            mockMvc.perform(get(path)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized()); // Should require auth for double encoded paths
        }
    }
}