package com.oddiya.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDateTime;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.ArrayList;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * Utility class for security testing with common security test helpers.
 * 
 * Provides helper methods for:
 * - JWT token manipulation
 * - Authentication bypass testing
 * - SQL injection payload generation
 * - XSS payload generation
 * - Input validation testing
 */
public class SecurityTestUtils {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    // Command Injection test payloads
    public static final String[] COMMAND_INJECTION_PAYLOADS = {
        "; ls -la",
        "| whoami",
        "& net user",
        "`cat /etc/passwd`",
        "$(id)",
        "; rm -rf /",
        "&& ping google.com",
        "|| echo vulnerable",
        "`whoami`",
        "$(cat /etc/passwd)"
    };
    
    // SSRF test payloads
    public static final String[] SSRF_PAYLOADS = {
        "http://localhost:8080",
        "http://127.0.0.1:8080",
        "http://192.168.1.1",
        "http://169.254.169.254/latest/meta-data/",
        "file:///etc/passwd",
        "ftp://localhost",
        "http://internal.company.com",
        "http://0.0.0.0:22",
        "http://[::1]:8080",
        "gopher://127.0.0.1:6379/_INFO"
    };
    
    // Suspicious User Agents
    public static final String[] SUSPICIOUS_USER_AGENTS = {
        "sqlmap/1.0",
        "nikto/2.1.6",
        "Burp Suite Professional",
        "python-requests/2.25.1",
        "curl/7.68.0",
        "wget/1.20.3",
        "gobuster/3.1.0",
        "dirb/2.22",
        "nmap scripting engine",
        "ZAP/2.10.0"
    };

    // Common XSS payloads for testing
    public static final String[] XSS_PAYLOADS = {
        "<script>alert('XSS')</script>",
        "<img src=\"x\" onerror=\"alert('XSS')\">",
        "<svg onload=\"alert('XSS')\">",
        "javascript:alert('XSS')",
        "<iframe src=\"javascript:alert('XSS')\"></iframe>",
        "<body onload=\"alert('XSS')\">",
        "<div onclick=\"alert('XSS')\">Click me</div>",
        "<a href=\"javascript:alert('XSS')\">Click</a>",
        "';alert('XSS');//",
        "\"><script>alert('XSS')</script>"
    };

    // Common SQL injection payloads for testing
    public static final String[] SQL_INJECTION_PAYLOADS = {
        "' OR '1'='1",
        "'; DROP TABLE users; --",
        "' UNION SELECT * FROM users --",
        "admin'--",
        "' OR 1=1--",
        "'; INSERT INTO users VALUES ('evil', 'hacker'); --",
        "' OR 'a'='a",
        "1' OR '1'='1' /*",
        "' OR 1=1#",
        "' UNION ALL SELECT NULL,NULL,NULL,version() --"
    };

    // Common authentication bypass payloads
    public static final String[] AUTH_BYPASS_PAYLOADS = {
        "Bearer null",
        "Bearer undefined", 
        "Bearer ",
        "Bearer admin",
        "Bearer guest",
        "Bearer anonymous",
        "Bearer eyJhbGciOiJub25lIn0.eyJzdWIiOiJhZG1pbiJ9.", // None algorithm
        "null",
        "undefined",
        "",
        "admin",
        "guest"
    };

    // Path traversal payloads
    public static final String[] PATH_TRAVERSAL_PAYLOADS = {
        "../../../etc/passwd",
        "..\\..\\..\\windows\\system32\\config\\sam",
        "%2e%2e%2f%2e%2e%2f%2e%2e%2fetc%2fpasswd",
        "....//....//....//etc/passwd",
        "/etc/passwd",
        "C:\\windows\\system32\\config\\sam"
    };

    /**
     * Creates a malicious JWT token with the "none" algorithm (no signature verification)
     */
    public static String createNoneAlgorithmJWT(String payload) {
        String header = "{\"alg\":\"none\",\"typ\":\"JWT\"}";
        String encodedHeader = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(header.getBytes(StandardCharsets.UTF_8));
        String encodedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        
        return encodedHeader + "." + encodedPayload + ".";
    }

    /**
     * Creates a JWT token with custom header and payload (for manipulation testing)
     */
    public static String createCustomJWT(String header, String payload, String signature) {
        String encodedHeader = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(header.getBytes(StandardCharsets.UTF_8));
        String encodedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        
        if (signature.isEmpty()) {
            return encodedHeader + "." + encodedPayload + ".";
        } else {
            String encodedSignature = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(signature.getBytes(StandardCharsets.UTF_8));
            return encodedHeader + "." + encodedPayload + "." + encodedSignature;
        }
    }

    /**
     * Adds malicious headers commonly used in authentication bypass attempts
     */
    public static MockHttpServletRequestBuilder addMaliciousHeaders(MockHttpServletRequestBuilder request) {
        return request
            .header("X-Forwarded-User", "admin")
            .header("X-Remote-User", "admin")
            .header("X-User", "admin")
            .header("X-Username", "admin")
            .header("X-Forwarded-For", "127.0.0.1")
            .header("X-Real-IP", "127.0.0.1")
            .header("X-Originating-IP", "127.0.0.1");
    }

    /**
     * Adds suspicious user agent headers for rate limiting tests
     */
    public static MockHttpServletRequestBuilder addSuspiciousUserAgent(MockHttpServletRequestBuilder request, String userAgent) {
        return request.header("User-Agent", userAgent);
    }

    /**
     * Creates a request with parameter pollution (multiple values for same parameter)
     */
    public static MockHttpServletRequestBuilder addParameterPollution(MockHttpServletRequestBuilder request, 
                                                                      String paramName, String... values) {
        for (String value : values) {
            request = request.param(paramName, value);
        }
        return request;
    }

    /**
     * Adds headers that attempt to manipulate host-based security
     */
    public static MockHttpServletRequestBuilder addHostManipulationHeaders(MockHttpServletRequestBuilder request) {
        return request
            .header("Host", "admin.localhost")
            .header("X-Forwarded-Host", "internal.admin")
            .header("X-Host", "127.0.0.1")
            .header("Referer", "http://admin.localhost/");
    }

    /**
     * Adds headers that attempt session fixation attacks
     */
    public static MockHttpServletRequestBuilder addSessionFixationHeaders(MockHttpServletRequestBuilder request) {
        return request
            .header("X-Session-ID", "admin-session")
            .header("X-Session", "fixed-session-123")
            .header("Session-ID", "attacker-session")
            .header("PHPSESSID", "attacker-controlled");
    }

    /**
     * Creates a request that attempts to bypass rate limiting through various headers
     */
    public static MockHttpServletRequestBuilder addRateLimitBypassHeaders(MockHttpServletRequestBuilder request) {
        return request
            .header("X-Forwarded-For", generateRandomIP())
            .header("X-Real-IP", generateRandomIP())
            .header("X-Client-IP", generateRandomIP())
            .header("CF-Connecting-IP", generateRandomIP())
            .header("True-Client-IP", generateRandomIP());
    }

    /**
     * Generates a random IP address for testing
     */
    private static String generateRandomIP() {
        return (int)(Math.random() * 255) + "." +
               (int)(Math.random() * 255) + "." +
               (int)(Math.random() * 255) + "." +
               (int)(Math.random() * 255);
    }

    /**
     * Creates a basic authentication header (for testing that it's not accepted)
     */
    public static String createBasicAuthHeader(String username, String password) {
        String credentials = username + ":" + password;
        String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }

    /**
     * Creates a request with content type confusion headers
     */
    public static MockHttpServletRequestBuilder addContentTypeConfusion(MockHttpServletRequestBuilder request) {
        return request
            .header("Content-Type", "application/json")
            .header("X-Content-Type", "text/html")
            .header("Accept", "text/html,application/xml");
    }

    /**
     * URL encodes a string (useful for testing URL encoding bypass attempts)
     */
    public static String urlEncode(String input) {
        try {
            return java.net.URLEncoder.encode(input, "UTF-8");
        } catch (Exception e) {
            return input;
        }
    }

    /**
     * Double URL encodes a string (for advanced bypass testing)
     */
    public static String doubleUrlEncode(String input) {
        return urlEncode(urlEncode(input));
    }

    /**
     * Creates a JSON payload with mass assignment attempt
     */
    public static String createMassAssignmentPayload(String basePayload) {
        // Remove the closing brace and add malicious fields
        if (basePayload.endsWith("}")) {
            basePayload = basePayload.substring(0, basePayload.length() - 1);
        }
        
        return basePayload + 
            ",\"role\":\"admin\"" +
            ",\"permissions\":[\"admin\",\"delete\",\"modify\"]" +
            ",\"isActive\":true" +
            ",\"isPremium\":true" +
            ",\"isAdmin\":true" +
            ",\"userId\":\"admin\"" +
            "}";
    }

    /**
     * Creates a payload with NoSQL injection attempts
     */
    public static String createNoSQLInjectionPayload(String field, String value) {
        return "\"" + field + "\":{\"$ne\":null,\"$gt\":\"\",\"$regex\":\"" + value + "\"}";
    }

    /**
     * Validates that a response doesn't contain sensitive information
     */
    public static void assertNoSensitiveDataExposed(String response) {
        String lowerResponse = response.toLowerCase();
        
        assert !lowerResponse.contains("password") : "Password field exposed in response";
        assert !lowerResponse.contains("secret") : "Secret field exposed in response";
        assert !lowerResponse.contains("token") || lowerResponse.contains("\"token\":false") : "Token exposed in response";
        assert !lowerResponse.contains("key") || lowerResponse.contains("\"key\":") : "Key field exposed in response";
        assert !lowerResponse.contains("private") : "Private field exposed in response";
        assert !lowerResponse.contains("internal") : "Internal field exposed in response";
        assert !lowerResponse.contains("admin") || lowerResponse.contains("\"admin\":false") : "Admin field exposed in response";
    }

    /**
     * Validates that XSS payloads are properly sanitized
     */
    public static void assertXSSSanitized(String response, String originalPayload) {
        String lowerResponse = response.toLowerCase();
        String lowerPayload = originalPayload.toLowerCase();
        
        assert !lowerResponse.contains("<script") : "Script tags not sanitized: " + originalPayload;
        assert !lowerResponse.contains("javascript:") : "JavaScript protocol not sanitized: " + originalPayload;
        assert !lowerResponse.contains("onerror=") : "onerror handler not sanitized: " + originalPayload;
        assert !lowerResponse.contains("onload=") : "onload handler not sanitized: " + originalPayload;
        assert !lowerResponse.contains("onclick=") : "onclick handler not sanitized: " + originalPayload;
        assert !response.equals(originalPayload) : "Payload reflected without sanitization: " + originalPayload;
    }

    /**
     * Validates that SQL injection payloads don't cause information disclosure
     */
    public static void assertSQLInjectionPrevented(String response, String sqlPayload) {
        String lowerResponse = response.toLowerCase();
        
        assert !lowerResponse.contains("sql") : "SQL error exposed: " + response;
        assert !lowerResponse.contains("database") : "Database error exposed: " + response;
        assert !lowerResponse.contains("postgresql") : "PostgreSQL error exposed: " + response;
        assert !lowerResponse.contains("mysql") : "MySQL error exposed: " + response;
        assert !lowerResponse.contains("oracle") : "Oracle error exposed: " + response;
        assert !lowerResponse.contains("table") || lowerResponse.contains("\"table\"") : "Table error exposed: " + response;
        assert !lowerResponse.contains("column") || lowerResponse.contains("\"column\"") : "Column error exposed: " + response;
        assert !lowerResponse.contains("syntax error") : "Syntax error exposed: " + response;
    }

    /**
     * Security test result tracking
     */
    public static class SecurityTestResult {
        private final String testName;
        private final boolean passed;
        private final String message;
        private final String recommendation;

        public SecurityTestResult(String testName, boolean passed, String message, String recommendation) {
            this.testName = testName;
            this.passed = passed;
            this.message = message;
            this.recommendation = recommendation;
        }

        public String getTestName() { return testName; }
        public boolean isPassed() { return passed; }
        public String getMessage() { return message; }
        public String getRecommendation() { return recommendation; }

        @Override
        public String toString() {
            return (passed ? "✅ " : "❌ ") + testName + ": " + message +
                   (recommendation != null && !recommendation.isEmpty() ? " | Recommendation: " + recommendation : "");
        }
    }

    /**
     * Creates a security test result
     */
    public static SecurityTestResult createResult(String testName, boolean passed, String message, String recommendation) {
        return new SecurityTestResult(testName, passed, message, recommendation);
    }

    /**
     * Creates a passed security test result
     */
    public static SecurityTestResult passed(String testName, String message) {
        return new SecurityTestResult(testName, true, message, null);
    }

    /**
     * Creates a failed security test result with recommendation
     */
    public static SecurityTestResult failed(String testName, String message, String recommendation) {
        return new SecurityTestResult(testName, false, message, recommendation);
    }
    
    // ==================== ENHANCED SECURITY TEST METHODS ====================
    
    /**
     * Create a POST request with JSON payload
     */
    public static MockHttpServletRequestBuilder createJsonPostRequest(String url, Object payload) throws Exception {
        return post(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload));
    }
    
    /**
     * Create a GET request with malicious parameters
     */
    public static MockHttpServletRequestBuilder createMaliciousGetRequest(String url, String paramName, String payload) {
        return get(url).param(paramName, payload);
    }
    
    /**
     * Create a request with malicious headers
     */
    public static MockHttpServletRequestBuilder createMaliciousHeaderRequest(String url, String headerName, String payload) {
        return get(url).header(headerName, payload);
    }
    
    /**
     * Create a request with suspicious User-Agent
     */
    public static MockHttpServletRequestBuilder createSuspiciousUserAgentRequest(String url, String userAgent) {
        return get(url).header("User-Agent", userAgent);
    }
    
    /**
     * Create an authenticated request
     */
    public static MockHttpServletRequestBuilder createAuthenticatedRequest(String url, String token) {
        return get(url).header("Authorization", "Bearer " + token);
    }
    
    /**
     * Perform rate limit testing by making multiple requests
     */
    public static List<ResultActions> performRateLimitTest(MockMvc mockMvc, String url, int requestCount) throws Exception {
        List<ResultActions> results = new ArrayList<>();
        for (int i = 0; i < requestCount; i++) {
            results.add(mockMvc.perform(get(url)));
        }
        return results;
    }
    
    /**
     * Validate security headers in response
     */
    public static void validateSecurityHeaders(ResultActions resultActions) throws Exception {
        resultActions
            .andExpect(result -> {
                String hstsHeader = result.getResponse().getHeader("Strict-Transport-Security");
                if (hstsHeader == null || hstsHeader.isEmpty()) {
                    throw new AssertionError("Missing HSTS header");
                }
            })
            .andExpect(result -> {
                String cspHeader = result.getResponse().getHeader("Content-Security-Policy");
                if (cspHeader == null || cspHeader.isEmpty()) {
                    throw new AssertionError("Missing CSP header");
                }
            })
            .andExpect(result -> {
                String frameOptionsHeader = result.getResponse().getHeader("X-Frame-Options");
                if (frameOptionsHeader == null || !frameOptionsHeader.equals("DENY")) {
                    throw new AssertionError("Invalid or missing X-Frame-Options header");
                }
            })
            .andExpect(result -> {
                String contentTypeHeader = result.getResponse().getHeader("X-Content-Type-Options");
                if (contentTypeHeader == null || !contentTypeHeader.equals("nosniff")) {
                    throw new AssertionError("Invalid or missing X-Content-Type-Options header");
                }
            });
    }
    
    /**
     * Validate rate limit headers in response
     */
    public static void validateRateLimitHeaders(ResultActions resultActions) throws Exception {
        resultActions
            .andExpect(result -> {
                String limitHeader = result.getResponse().getHeader("X-RateLimit-Limit");
                String remainingHeader = result.getResponse().getHeader("X-RateLimit-Remaining");
                String resetHeader = result.getResponse().getHeader("X-RateLimit-Reset");
                
                if (limitHeader == null || remainingHeader == null || resetHeader == null) {
                    throw new AssertionError("Missing rate limit headers");
                }
            });
    }
    
    /**
     * Create a large payload to test size limits
     */
    public static String createLargePayload(int sizeInKB) {
        StringBuilder builder = new StringBuilder();
        String pattern = "A";
        int targetSize = sizeInKB * 1024;
        
        while (builder.length() < targetSize) {
            builder.append(pattern);
        }
        
        return builder.toString();
    }
    
    /**
     * Test data for user registration with malicious inputs
     */
    public static Object createMaliciousUserData(String maliciousInput) {
        return new Object() {
            public final String username = maliciousInput;
            public final String email = "test@example.com";
            public final String password = "password123";
            public final String firstName = "Test";
            public final String lastName = "User";
        };
    }
    
    /**
     * Test data for travel plan creation with malicious inputs
     */
    public static Object createMaliciousTravelPlanData(String maliciousInput) {
        return new Object() {
            public final String title = maliciousInput;
            public final String description = "Test description";
            public final String destination = "Seoul, Korea";
            public final String startDate = "2024-01-01";
            public final String endDate = "2024-01-07";
            public final Double budget = 1000.0;
        };
    }
    
    /**
     * Test data for place creation with malicious inputs
     */
    public static Object createMaliciousPlaceData(String maliciousInput) {
        return new Object() {
            public final String name = maliciousInput;
            public final String address = "Test Address";
            public final String description = "Test Description";
            public final Double latitude = 37.5665;
            public final Double longitude = 126.9780;
            public final String category = "RESTAURANT";
        };
    }
    
    /**
     * Validate that response doesn't contain sensitive information
     */
    public static void validateNoSensitiveInfoLeakage(ResultActions resultActions) throws Exception {
        resultActions
            .andExpect(result -> {
                String responseBody = result.getResponse().getContentAsString();
                
                // Check for stack traces
                if (responseBody.contains("Exception") && responseBody.contains("at ")) {
                    throw new AssertionError("Response contains stack trace information");
                }
                
                // Check for database information
                if (responseBody.toLowerCase().contains("sql") && 
                    (responseBody.toLowerCase().contains("error") || responseBody.toLowerCase().contains("exception"))) {
                    throw new AssertionError("Response contains database error information");
                }
                
                // Check for file system paths
                if (responseBody.contains("/Users/") || responseBody.contains("C:\\") || 
                    responseBody.contains("/var/") || responseBody.contains("/etc/")) {
                    throw new AssertionError("Response contains file system path information");
                }
                
                // Check for internal class names
                if (responseBody.contains("com.oddiya") && responseBody.contains(".class")) {
                    throw new AssertionError("Response contains internal class information");
                }
                
                // Check for password fields
                if (responseBody.toLowerCase().contains("\"password\"") && 
                    !responseBody.toLowerCase().contains("\"password\":null")) {
                    throw new AssertionError("Response contains password field");
                }
            });
    }
    
    /**
     * Create a request with header injection attempt
     */
    public static MockHttpServletRequestBuilder createHeaderInjectionRequest(String url) {
        return get(url)
                .header("X-Custom-Header", "normal\r\nInjected-Header: malicious")
                .header("User-Agent", "normal\nX-Injected: evil")
                .header("Referer", "http://example.com\r\nX-Evil: attack");
    }
    
    /**
     * Create a request simulating various attack vectors
     */
    public static MockHttpServletRequestBuilder createMultiVectorAttackRequest(String url) {
        return post(url)
                .contentType(MediaType.APPLICATION_JSON)
                .header("User-Agent", "sqlmap/1.0")
                .header("X-Forwarded-For", "127.0.0.1")
                .param("callback", "http://attacker.com/steal")
                .param("search", "'; DROP TABLE users; --")
                .content("{\"name\":\"<script>alert('xss')</script>\",\"file\":\"../../../etc/passwd\"}");
    }
    
    /**
     * Test encryption/decryption functionality
     */
    public static class EncryptionTestHelper {
        public static final String TEST_PLAINTEXT = "sensitive-data-12345";
        public static final String TEST_PII = "john.doe@example.com";
        public static final String TEST_PASSWORD = "super-secret-password";
        
        public static void validateEncryptionWorking(String encrypted, String original) {
            assert !encrypted.equals(original) : "Data not encrypted properly";
            assert encrypted != null && !encrypted.isEmpty() : "Encrypted data is null or empty";
            assert encrypted.length() > original.length() : "Encrypted data should be longer than original";
        }
        
        public static void validateDecryptionWorking(String decrypted, String original) {
            assert decrypted.equals(original) : "Decryption failed: expected " + original + " but got " + decrypted;
        }
    }
    
    /**
     * Test rate limiting functionality
     */
    public static class RateLimitTestHelper {
        
        public static void validateRateLimitApplied(List<ResultActions> results, int expectedAllowed) throws Exception {
            int allowedRequests = 0;
            int blockedRequests = 0;
            
            for (ResultActions result : results) {
                int status = result.andReturn().getResponse().getStatus();
                if (status == 200 || status == 201) {
                    allowedRequests++;
                } else if (status == 429) {
                    blockedRequests++;
                }
            }
            
            assert allowedRequests <= expectedAllowed : 
                "Too many requests allowed: " + allowedRequests + " (expected max: " + expectedAllowed + ")";
            assert blockedRequests > 0 : "No requests were rate limited";
        }
        
        public static void validateRateLimitHeaders(ResultActions result) throws Exception {
            result.andExpect(res -> {
                String limit = res.getResponse().getHeader("X-RateLimit-Limit");
                String remaining = res.getResponse().getHeader("X-RateLimit-Remaining");
                String reset = res.getResponse().getHeader("X-RateLimit-Reset");
                
                assert limit != null : "Missing X-RateLimit-Limit header";
                assert remaining != null : "Missing X-RateLimit-Remaining header";
                assert reset != null : "Missing X-RateLimit-Reset header";
                
                assert Integer.parseInt(limit) > 0 : "Invalid rate limit value";
                assert Integer.parseInt(remaining) >= 0 : "Invalid remaining count";
            });
        }
    }
    
    /**
     * Security audit helper for comprehensive testing
     */
    public static class SecurityAuditHelper {
        
        public static void auditEndpointSecurity(MockMvc mockMvc, String endpoint) throws Exception {
            List<SecurityTestResult> results = new ArrayList<>();
            
            // Test 1: SQL Injection
            for (String payload : SQL_INJECTION_PAYLOADS) {
                try {
                    ResultActions result = mockMvc.perform(get(endpoint).param("search", payload));
                    validateNoSensitiveInfoLeakage(result);
                    assertSQLInjectionPrevented(result.andReturn().getResponse().getContentAsString(), payload);
                    results.add(passed("SQL Injection Prevention", "Payload blocked: " + payload));
                } catch (Exception e) {
                    results.add(failed("SQL Injection Prevention", "Payload not blocked: " + payload, 
                                     "Implement input validation for parameter 'search'"));
                }
            }
            
            // Test 2: XSS Prevention
            for (String payload : XSS_PAYLOADS) {
                try {
                    ResultActions result = mockMvc.perform(get(endpoint).param("name", payload));
                    String response = result.andReturn().getResponse().getContentAsString();
                    assertXSSSanitized(response, payload);
                    results.add(passed("XSS Prevention", "Payload sanitized: " + payload));
                } catch (Exception e) {
                    results.add(failed("XSS Prevention", "Payload not sanitized: " + payload,
                                     "Implement output encoding for parameter 'name'"));
                }
            }
            
            // Test 3: Security Headers
            try {
                ResultActions result = mockMvc.perform(get(endpoint));
                validateSecurityHeaders(result);
                results.add(passed("Security Headers", "All required security headers present"));
            } catch (Exception e) {
                results.add(failed("Security Headers", "Missing security headers: " + e.getMessage(),
                                 "Configure security headers in SecurityConfig"));
            }
            
            // Print audit results
            System.out.println("\n=== Security Audit Results for " + endpoint + " ===");
            for (SecurityTestResult result : results) {
                System.out.println(result);
            }
        }
    }
    
    /**
     * Create test data with various injection attempts
     */
    public static class TestDataFactory {
        
        public static Object createTestUser(String maliciousField, String payload) {
            return new Object() {
                public final String username = maliciousField.equals("username") ? payload : "testuser";
                public final String email = maliciousField.equals("email") ? payload : "test@example.com";
                public final String firstName = maliciousField.equals("firstName") ? payload : "John";
                public final String lastName = maliciousField.equals("lastName") ? payload : "Doe";
                public final String password = maliciousField.equals("password") ? payload : "ValidPass123!";
            };
        }
        
        public static Object createTestTravelPlan(String maliciousField, String payload) {
            return new Object() {
                public final String title = maliciousField.equals("title") ? payload : "Test Trip";
                public final String description = maliciousField.equals("description") ? payload : "A test travel plan";
                public final String destination = maliciousField.equals("destination") ? payload : "Seoul, Korea";
                public final String startDate = maliciousField.equals("startDate") ? payload : "2024-06-01";
                public final String endDate = maliciousField.equals("endDate") ? payload : "2024-06-07";
                public final Double budget = 1000.0;
            };
        }
        
        public static Object createTestPlace(String maliciousField, String payload) {
            return new Object() {
                public final String name = maliciousField.equals("name") ? payload : "Test Place";
                public final String address = maliciousField.equals("address") ? payload : "123 Test St, Seoul";
                public final String description = maliciousField.equals("description") ? payload : "A test place";
                public final Double latitude = 37.5665;
                public final Double longitude = 126.9780;
                public final String category = "RESTAURANT";
                public final String imageUrl = maliciousField.equals("imageUrl") ? payload : "https://example.com/image.jpg";
            };
        }
    }
}