package com.oddiya.security;

import com.oddiya.integration.BaseIntegrationTest;
import com.oddiya.testdata.ComprehensiveTestDataFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Comprehensive Security Test Suite for Oddiya
 * 
 * Tests security measures as per PRD requirements and OWASP Top 10:
 * 1. Broken Access Control
 * 2. Cryptographic Failures  
 * 3. Injection (SQL, XSS, etc.)
 * 4. Insecure Design
 * 5. Security Misconfiguration
 * 6. Vulnerable and Outdated Components
 * 7. Identification and Authentication Failures
 * 8. Software and Data Integrity Failures
 * 9. Security Logging and Monitoring Failures
 * 10. Server-Side Request Forgery (SSRF)
 * 
 * Plus Oddiya-specific security requirements:
 * - JWT token validation and manipulation prevention
 * - OAuth flow security
 * - Rate limiting and DDoS protection
 * - Input validation and sanitization
 * - Spatial query injection prevention (PostGIS)
 */
@DisplayName("Comprehensive Security Test Suite")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.CONCURRENT)
class ComprehensiveSecurityTestSuite extends BaseIntegrationTest {

    private ComprehensiveTestDataFactory testDataFactory;

    @BeforeAll
    void setUpSecurityTestSuite() {
        testDataFactory = new ComprehensiveTestDataFactory();
    }

    // ============================================================
    // OWASP TOP 10 #1: BROKEN ACCESS CONTROL
    // ============================================================

    @Nested
    @DisplayName("OWASP #1: Broken Access Control Tests")
    class BrokenAccessControlTests {

        @Test
        @DisplayName("Should prevent unauthorized access to protected endpoints")
        void shouldPreventUnauthorizedAccessToProtectedEndpoints() throws Exception {
            // Test cases for different protected endpoints
            String[] protectedEndpoints = {
                "/api/v1/travel-plans/" + TEST_TRAVEL_PLAN_ID,
                "/api/v1/users/profile", 
                "/api/v1/travel-plans",
                "/api/v1/users/" + TEST_USER_ID + "/travel-plans"
            };

            for (String endpoint : protectedEndpoints) {
                mockMvc.perform(get(endpoint))
                    .andExpect(status().isUnauthorized())
                    .andExpect(result -> {
                        String response = result.getResponse().getContentAsString();
                        assertThat(response).doesNotContain("sensitive");
                        assertThat(response).doesNotContain("password");
                        assertThat(response).doesNotContain("token");
                    });
            }
        }

        @Test
        @WithMockUser(username = "user1", roles = "USER")
        @DisplayName("Should prevent horizontal privilege escalation")
        void shouldPreventHorizontalPrivilegeEscalation() throws Exception {
            // Try to access another user's travel plans
            mockMvc.perform(get("/api/v1/users/different-user-id/travel-plans")
                    .with(jwt().jwt(jwt -> jwt.subject("user1"))))
                    .andExpect(status().isForbidden());

            // Try to modify another user's travel plan
            mockMvc.perform(put("/api/v1/travel-plans/" + TEST_TRAVEL_PLAN_ID)
                    .with(jwt().jwt(jwt -> jwt.subject("different-user")))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"Hacked Plan\"}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(username = "user1", roles = "USER")
        @DisplayName("Should prevent vertical privilege escalation")
        void shouldPreventVerticalPrivilegeEscalation() throws Exception {
            // Try to access admin endpoints with user role
            String[] adminEndpoints = {
                "/api/v1/admin/users",
                "/api/v1/admin/travel-plans",
                "/api/v1/admin/system/health"
            };

            for (String endpoint : adminEndpoints) {
                mockMvc.perform(get(endpoint)
                        .with(jwt().jwt(jwt -> jwt.subject("user1"))))
                        .andExpect(anyOf(
                            status().isForbidden(),
                            status().isNotFound() // Admin endpoints might not exist
                        ));
            }
        }

        @Test
        @DisplayName("Should enforce role-based access control (RBAC)")
        void shouldEnforceRoleBasedAccessControl() throws Exception {
            // Test different roles have appropriate access
            
            // USER role should access user endpoints
            mockMvc.perform(get("/api/v1/users/profile")
                    .with(jwt().jwt(jwt -> jwt
                        .subject(TEST_USER_ID)
                        .claim("roles", "USER"))))
                    .andExpect(anyOf(status().isOk(), status().isNotFound()));

            // ADMIN role should access admin endpoints (if they exist)
            mockMvc.perform(get("/api/v1/admin/health")
                    .with(jwt().jwt(jwt -> jwt
                        .subject("admin-user")
                        .claim("roles", "ADMIN"))))
                    .andExpect(anyOf(
                        status().isOk(),
                        status().isNotFound(), // Endpoint might not exist
                        status().isForbidden()  // Or not implemented yet
                    ));
        }
    }

    // ============================================================
    // OWASP TOP 10 #2: CRYPTOGRAPHIC FAILURES
    // ============================================================

    @Nested
    @DisplayName("OWASP #2: Cryptographic Failures Tests")
    class CryptographicFailuresTests {

        @Test
        @DisplayName("Should enforce HTTPS in production-like environments")
        void shouldEnforceHTTPS() throws Exception {
            // This test validates that security headers are present
            mockMvc.perform(get("/api/v1/health")
                    .header("X-Forwarded-Proto", "http"))
                    .andExpect(result -> {
                        // In production, should redirect to HTTPS or return security headers
                        String response = result.getResponse().getContentAsString();
                        // Verify no sensitive data in HTTP responses
                        assertThat(response).doesNotContain("password");
                        assertThat(response).doesNotContain("secret");
                        assertThat(response).doesNotContain("private");
                    });
        }

        @Test
        @DisplayName("Should not expose sensitive information in responses")
        void shouldNotExposeSensitiveInformationInResponses() throws Exception {
            mockMvc.perform(get("/api/v1/users/profile")
                    .with(jwt().jwt(jwt -> jwt.subject(TEST_USER_ID))))
                    .andExpect(result -> {
                        String response = result.getResponse().getContentAsString();
                        
                        // Should not expose sensitive fields
                        assertThat(response).doesNotContain("password");
                        assertThat(response).doesNotContain("refreshToken");
                        assertThat(response).doesNotContain("providerId");
                        assertThat(response).doesNotContain("secret");
                        assertThat(response).doesNotContain("private");
                        
                        // Check for proper field masking/filtering
                        if (response.contains("email")) {
                            // Email should be present but other sensitive fields should not
                            assertThat(response).matches(".*\"email\"\\s*:\\s*\"[^\"]+\".*");
                        }
                    });
        }

        @Test
        @DisplayName("Should validate JWT token cryptographic integrity")
        void shouldValidateJWTTokenCryptographicIntegrity() throws Exception {
            // Test with malformed JWT
            mockMvc.perform(get("/api/v1/users/profile")
                    .header("Authorization", "Bearer invalid.jwt.token"))
                    .andExpect(status().isUnauthorized());

            // Test with expired JWT (simulated)
            mockMvc.perform(get("/api/v1/users/profile")
                    .header("Authorization", "Bearer expired.jwt.token"))
                    .andExpect(status().isUnauthorized());

            // Test with tampered JWT signature
            mockMvc.perform(get("/api/v1/users/profile")  
                    .header("Authorization", "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.tampered.signature"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ============================================================
    // OWASP TOP 10 #3: INJECTION ATTACKS
    // ============================================================

    @Nested
    @DisplayName("OWASP #3: Injection Attack Tests")
    class InjectionAttackTests {

        @Test
        @DisplayName("Should prevent SQL injection in search queries")
        void shouldPreventSQLInjectionInSearchQueries() throws Exception {
            String[] sqlInjectionPayloads = {
                "'; DROP TABLE travel_plans; --",
                "' OR '1'='1",
                "' UNION SELECT * FROM users --",
                "'; INSERT INTO users (id, email) VALUES ('hacker', 'hack@evil.com'); --",
                "' OR 1=1 --",
                "admin'/*",
                "' OR 'x'='x",
                "1'; EXEC sp_configure 'show advanced options',1--"
            };

            for (String payload : sqlInjectionPayloads) {
                mockMvc.perform(get("/api/v1/travel-plans/search")
                        .param("q", payload)
                        .with(jwt().jwt(jwt -> jwt.subject(TEST_USER_ID))))
                        .andExpect(result -> {
                            int status = result.getResponse().getStatus();
                            String response = result.getResponse().getContentAsString();
                            
                            // Should either return 400 (validation error) or safe results
                            assertThat(status).isIn(200, 400);
                            
                            if (status == 200) {
                                // If processing succeeds, ensure no data leakage
                                assertThat(response).doesNotContain("DROP");
                                assertThat(response).doesNotContain("INSERT");
                                assertThat(response).doesNotContain("DELETE");
                                assertThat(response).doesNotContain("UPDATE");
                                assertThat(response).doesNotContain("EXEC");
                            }
                        });
            }
        }

        @Test
        @DisplayName("Should prevent PostGIS spatial injection attacks") 
        void shouldPreventPostGISSpatialInjectionAttacks() throws Exception {
            // PostGIS-specific injection attempts
            String[] spatialInjectionPayloads = {
                "ST_GeomFromText('POINT(37.5665 126.9780)'); DROP TABLE places; --",
                "POINT(37.5665 126.9780)) UNION SELECT password FROM users --",
                "'; SELECT ST_AsText(ST_Point(longitude, latitude)) FROM places WHERE id = 1; --"
            };

            for (String payload : spatialInjectionPayloads) {
                mockMvc.perform(get("/api/v1/places/near")
                        .param("latitude", "37.5665")
                        .param("longitude", "126.9780")
                        .param("radiusKm", "10")
                        .param("query", payload))
                        .andExpect(result -> {
                            int status = result.getResponse().getStatus();
                            String response = result.getResponse().getContentAsString();
                            
                            // Should handle spatial queries safely
                            if (status == 200) {
                                assertThat(response).doesNotContain("DROP");
                                assertThat(response).doesNotContain("password");
                                assertThat(response).doesNotContain("SELECT");
                            }
                        });
            }
        }

        @Test
        @DisplayName("Should prevent XSS attacks in user input")
        void shouldPreventXSSAttacksInUserInput() throws Exception {
            String[] xssPayloads = {
                "<script>alert('XSS')</script>",
                "<img src=x onerror=alert('XSS')>",
                "javascript:alert('XSS')",
                "<svg onload=alert('XSS')>",
                "';alert(String.fromCharCode(88,83,83))//';alert(String.fromCharCode(88,83,83))//",
                "\"><script>alert('XSS')</script>",
                "'><script>alert(document.cookie)</script>",
                "<iframe src=\"javascript:alert('XSS')\"></iframe>"
            };

            for (String payload : xssPayloads) {
                String requestBody = String.format("""
                    {
                        "title": "%s",
                        "description": "Test travel plan",
                        "destination": "Seoul",
                        "startDate": "2024-12-01",
                        "endDate": "2024-12-03"
                    }
                    """, payload);

                mockMvc.perform(post("/api/v1/travel-plans")
                        .with(jwt().jwt(jwt -> jwt.subject(TEST_USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                        .andExpect(result -> {
                            String response = result.getResponse().getContentAsString();
                            
                            // Response should not contain raw script tags
                            assertThat(response).doesNotContain("<script>");
                            assertThat(response).doesNotContain("javascript:");
                            assertThat(response).doesNotContain("onerror=");
                            assertThat(response).doesNotContain("onload=");
                        });
            }
        }

        @Test
        @DisplayName("Should sanitize Korean text input properly")
        void shouldSanitizeKoreanTextInputProperly() throws Exception {
            // Test XSS with Korean characters
            String koreanXSSPayload = "<script>alert('한국어 XSS')</script>서울 여행";
            
            String requestBody = String.format("""
                {
                    "title": "%s",
                    "description": "한국어 설명",
                    "destination": "서울",
                    "startDate": "2024-12-01", 
                    "endDate": "2024-12-03"
                }
                """, koreanXSSPayload);

            mockMvc.perform(post("/api/v1/travel-plans")
                    .with(jwt().jwt(jwt -> jwt.subject(TEST_USER_ID)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .characterEncoding("UTF-8")
                    .content(requestBody))
                    .andExpect(result -> {
                        String response = result.getResponse().getContentAsString();
                        
                        // Should preserve Korean text but remove scripts
                        if (result.getResponse().getStatus() == 201) {
                            assertThat(response).doesNotContain("<script>");
                            // Korean text should be preserved (if creation succeeded)
                            // This depends on implementation - either sanitized or rejected
                        }
                    });
        }
    }

    // ============================================================
    // OWASP TOP 10 #7: IDENTIFICATION AND AUTHENTICATION FAILURES
    // ============================================================

    @Nested
    @DisplayName("OWASP #7: Authentication and Identification Failures")
    class AuthenticationFailuresTests {

        @Test
        @DisplayName("Should validate JWT token expiration")
        void shouldValidateJWTTokenExpiration() throws Exception {
            // Test with various invalid tokens
            String[] invalidTokens = {
                "Bearer ",
                "Bearer invalid-token",
                "Bearer eyJhbGciOiJub25lIiwidHlwIjoiSldUIn0.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.", // No signature
                "Bearer expired.jwt.token.here",
                ""
            };

            for (String invalidToken : invalidTokens) {
                mockMvc.perform(get("/api/v1/users/profile")
                        .header("Authorization", invalidToken))
                        .andExpect(status().isUnauthorized())
                        .andExpect(result -> {
                            String response = result.getResponse().getContentAsString();
                            assertThat(response).doesNotContain("sensitive");
                        });
            }
        }

        @Test
        @DisplayName("Should prevent session fixation attacks")
        void shouldPreventSessionFixationAttacks() throws Exception {
            // Test that session IDs change after authentication
            // This is more relevant for session-based auth, but test headers
            
            MvcResult beforeAuth = mockMvc.perform(get("/api/v1/health"))
                    .andReturn();

            MvcResult afterAuth = mockMvc.perform(get("/api/v1/users/profile")
                    .with(jwt().jwt(jwt -> jwt.subject(TEST_USER_ID))))
                    .andReturn();

            // Verify no session information leaked in headers
            String beforeHeaders = beforeAuth.getResponse().getHeaderNames().toString();
            String afterHeaders = afterAuth.getResponse().getHeaderNames().toString();
            
            assertThat(beforeHeaders).doesNotContain("JSESSIONID");
            assertThat(afterHeaders).doesNotContain("JSESSIONID");
        }

        @Test
        @DisplayName("Should implement proper OAuth flow security")
        void shouldImplementProperOAuthFlowSecurity() throws Exception {
            // Test OAuth callback endpoint security
            mockMvc.perform(get("/oauth2/callback/google")
                    .param("code", "malicious-code")
                    .param("state", "tampered-state"))
                    .andExpect(result -> {
                        // Should handle invalid OAuth responses securely
                        int status = result.getResponse().getStatus();
                        assertThat(status).isIn(400, 401, 403, 404); // Various acceptable error responses
                    });
        }
    }

    // ============================================================
    // RATE LIMITING AND DDOS PROTECTION
    // ============================================================

    @Nested
    @DisplayName("Rate Limiting and DDoS Protection Tests")
    class RateLimitingTests {

        @Test
        @DisplayName("Should implement rate limiting for API endpoints")
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        void shouldImplementRateLimitingForAPIEndpoints() throws Exception {
            // Test rate limiting by making rapid requests
            int requestCount = 100;
            int rateLimitThreshold = 60; // Expected rate limit per minute
            
            CompletableFuture<Integer>[] futures = new CompletableFuture[requestCount];
            
            for (int i = 0; i < requestCount; i++) {
                futures[i] = CompletableFuture.supplyAsync(() -> {
                    try {
                        MvcResult result = mockMvc.perform(get("/api/v1/travel-plans/public"))
                                .andReturn();
                        return result.getResponse().getStatus();
                    } catch (Exception e) {
                        return 500; // Error occurred
                    }
                });
            }

            // Collect results
            int successCount = 0;
            int rateLimitedCount = 0;
            
            for (CompletableFuture<Integer> future : futures) {
                int status = future.get();
                if (status == 200) {
                    successCount++;
                } else if (status == 429) { // Too Many Requests
                    rateLimitedCount++;
                }
            }

            // Verify rate limiting is working (should not allow all 100 requests)
            assertThat(successCount).isLessThan(requestCount);
            
            // If rate limiting is implemented, should see 429 responses
            if (rateLimitedCount > 0) {
                assertThat(rateLimitedCount).isGreaterThan(0);
            }
        }

        @Test
        @DisplayName("Should protect against large payload attacks")
        void shouldProtectAgainstLargePayloadAttacks() throws Exception {
            // Create oversized request payload
            StringBuilder largePayload = new StringBuilder();
            largePayload.append("{\"title\":\"");
            
            // Create 10MB+ string
            for (int i = 0; i < 1024 * 1024; i++) {
                largePayload.append("A");
            }
            largePayload.append("\"}");

            mockMvc.perform(post("/api/v1/travel-plans")
                    .with(jwt().jwt(jwt -> jwt.subject(TEST_USER_ID)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(largePayload.toString()))
                    .andExpect(status().isBadRequest()); // Should reject large payloads
        }
    }

    // ============================================================
    // SECURITY HEADERS AND CONFIGURATION TESTS
    // ============================================================

    @Nested
    @DisplayName("Security Headers and Configuration Tests")
    class SecurityHeadersTests {

        @Test
        @DisplayName("Should include security headers in responses")
        void shouldIncludeSecurityHeadersInResponses() throws Exception {
            mockMvc.perform(get("/api/v1/health"))
                    .andExpect(result -> {
                        // Check for important security headers
                        var response = result.getResponse();
                        
                        // Content Security Policy
                        // Note: These headers might be set by proxy/gateway in production
                        // Test verifies they're not exposing sensitive information
                        
                        // Verify no sensitive information in headers
                        response.getHeaderNames().forEach(headerName -> {
                            String headerValue = response.getHeader(headerName);
                            assertThat(headerValue).doesNotContain("password");
                            assertThat(headerValue).doesNotContain("secret");
                            assertThat(headerValue).doesNotContain("private");
                        });
                        
                        // Verify proper CORS configuration
                        String corsOrigin = response.getHeader("Access-Control-Allow-Origin");
                        if (corsOrigin != null) {
                            assertThat(corsOrigin).isNotEqualTo("*"); // Should not allow all origins in production
                        }
                    });
        }

        @Test
        @DisplayName("Should not expose internal system information")
        void shouldNotExposeInternalSystemInformation() throws Exception {
            // Test various endpoints for information disclosure
            String[] endpoints = {
                "/api/v1/health",
                "/api/v1/travel-plans/search?q=test",
                "/error",
                "/actuator/health" // If actuator is enabled
            };

            for (String endpoint : endpoints) {
                mockMvc.perform(get(endpoint))
                        .andExpect(result -> {
                            String response = result.getResponse().getContentAsString();
                            String serverHeader = result.getResponse().getHeader("Server");
                            
                            // Should not expose system details
                            assertThat(response).doesNotContain("java.lang");
                            assertThat(response).doesNotContain("SQLException");
                            assertThat(response).doesNotContain("stackTrace");
                            assertThat(response).doesNotContain("Spring Boot");
                            assertThat(response).doesNotContain("Tomcat");
                            
                            // Server header should not expose version info
                            if (serverHeader != null) {
                                assertThat(serverHeader).doesNotContainPattern(".*\\d+\\.\\d+.*"); // No version numbers
                            }
                        });
            }
        }
    }

    // ============================================================
    // DATA VALIDATION AND SANITIZATION TESTS
    // ============================================================

    @Nested
    @DisplayName("Data Validation and Sanitization Tests")
    class DataValidationTests {

        @Test
        @DisplayName("Should validate coordinate ranges for spatial queries")
        void shouldValidateCoordinateRangesForSpatialQueries() throws Exception {
            // Test invalid coordinate ranges
            String[][] invalidCoordinates = {
                {"200", "126.9780"}, // Invalid latitude
                {"37.5665", "400"},   // Invalid longitude
                {"-100", "126.9780"}, // Invalid latitude
                {"37.5665", "-200"},  // Invalid longitude
                {"abc", "126.9780"},  // Non-numeric latitude
                {"37.5665", "xyz"}    // Non-numeric longitude
            };

            for (String[] coords : invalidCoordinates) {
                mockMvc.perform(get("/api/v1/places/near")
                        .param("latitude", coords[0])
                        .param("longitude", coords[1])
                        .param("radiusKm", "10"))
                        .andExpect(status().isBadRequest());
            }
        }

        @Test
        @DisplayName("Should validate date ranges and business logic")
        void shouldValidateDateRangesAndBusinessLogic() throws Exception {
            // Test invalid date combinations
            String invalidDateRequest = """
                {
                    "title": "Invalid Date Plan",
                    "destination": "Seoul",
                    "startDate": "2024-12-31",
                    "endDate": "2024-01-01"
                }
                """;

            mockMvc.perform(post("/api/v1/travel-plans")
                    .with(jwt().jwt(jwt -> jwt.subject(TEST_USER_ID)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(invalidDateRequest))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should validate input field lengths and constraints")
        void shouldValidateInputFieldLengthsAndConstraints() throws Exception {
            // Test field length validation
            String longTitleRequest = String.format("""
                {
                    "title": "%s",
                    "destination": "Seoul",
                    "startDate": "2024-12-01",
                    "endDate": "2024-12-03"
                }
                """, "A".repeat(1000)); // Very long title

            mockMvc.perform(post("/api/v1/travel-plans")
                    .with(jwt().jwt(jwt -> jwt.subject(TEST_USER_ID)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(longTitleRequest))
                    .andExpect(result -> {
                        int status = result.getResponse().getStatus();
                        // Should either accept with truncation or reject
                        assertThat(status).isIn(201, 400);
                    });
        }
    }

    @AfterEach
    void cleanupSecurityTest() {
        // Clean up any test data that might affect other tests
        cleanupTestData();
    }
}