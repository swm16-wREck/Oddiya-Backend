package com.oddiya.security;

import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import java.util.Base64;
import java.nio.charset.StandardCharsets;

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
}