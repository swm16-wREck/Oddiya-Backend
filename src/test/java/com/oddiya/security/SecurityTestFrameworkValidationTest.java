package com.oddiya.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Security Test Framework Validation Test
 * 
 * This test validates that our security testing framework and utilities
 * are working correctly. It tests the testing tools themselves to ensure
 * they provide accurate security validations.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("🧪 Security Test Framework Validation")
class SecurityTestFrameworkValidationTest {

    @Nested
    @DisplayName("🔍 SecurityTestUtils Validation")
    class SecurityTestUtilsValidationTests {

        @Test
        @DisplayName("SQL injection payload detection should work correctly")
        void testSQLInjectionPayloadValidation() {
            // Test that our SQL injection payloads are comprehensive
            String[] payloads = SecurityTestUtils.SQL_INJECTION_PAYLOADS;
            
            assertThat(payloads).as("SQL injection payloads should exist").isNotEmpty();
            assertThat(payloads.length).as("Should have comprehensive SQL injection payloads").isGreaterThan(5);
            
            // Verify payloads contain various SQL injection techniques
            String allPayloads = String.join(" ", payloads);
            assertThat(allPayloads).as("Should include OR-based injection")
                .containsIgnoringCase("OR");
            assertThat(allPayloads).as("Should include UNION-based injection")
                .containsIgnoringCase("UNION");
            assertThat(allPayloads).as("Should include comment-based injection")
                .contains("--");
            assertThat(allPayloads).as("Should include DROP TABLE attacks")
                .containsIgnoringCase("DROP TABLE");
        }

        @Test
        @DisplayName("XSS payload detection should work correctly")
        void testXSSPayloadValidation() {
            String[] payloads = SecurityTestUtils.XSS_PAYLOADS;
            
            assertThat(payloads).as("XSS payloads should exist").isNotEmpty();
            assertThat(payloads.length).as("Should have comprehensive XSS payloads").isGreaterThan(5);
            
            // Verify payloads contain various XSS techniques
            String allPayloads = String.join(" ", payloads);
            assertThat(allPayloads).as("Should include script-based XSS")
                .containsIgnoringCase("<script>");
            assertThat(allPayloads).as("Should include event handler XSS")
                .containsIgnoringCase("onerror");
            assertThat(allPayloads).as("Should include javascript protocol XSS")
                .containsIgnoringCase("javascript:");
            assertThat(allPayloads).as("Should include SVG-based XSS")
                .containsIgnoringCase("<svg");
        }

        @Test
        @DisplayName("Command injection payload detection should work correctly")
        void testCommandInjectionPayloadValidation() {
            String[] payloads = SecurityTestUtils.COMMAND_INJECTION_PAYLOADS;
            
            assertThat(payloads).as("Command injection payloads should exist").isNotEmpty();
            assertThat(payloads.length).as("Should have comprehensive command injection payloads").isGreaterThan(5);
            
            // Verify payloads contain various command injection techniques
            String allPayloads = String.join(" ", payloads);
            assertThat(allPayloads).as("Should include semicolon separation")
                .contains(";");
            assertThat(allPayloads).as("Should include pipe operations")
                .contains("|");
            assertThat(allPayloads).as("Should include backtick execution")
                .contains("`");
            assertThat(allPayloads).as("Should include dollar execution")
                .contains("$(");
        }

        @Test
        @DisplayName("SSRF payload detection should work correctly")
        void testSSRFPayloadValidation() {
            String[] payloads = SecurityTestUtils.SSRF_PAYLOADS;
            
            assertThat(payloads).as("SSRF payloads should exist").isNotEmpty();
            assertThat(payloads.length).as("Should have comprehensive SSRF payloads").isGreaterThan(5);
            
            // Verify payloads contain various SSRF techniques
            String allPayloads = String.join(" ", payloads);
            assertThat(allPayloads).as("Should include localhost variants")
                .contains("localhost")
                .contains("127.0.0.1");
            assertThat(allPayloads).as("Should include metadata endpoints")
                .contains("169.254.169.254");
            assertThat(allPayloads).as("Should include file protocol")
                .containsIgnoringCase("file:");
            assertThat(allPayloads).as("Should include internal networks")
                .contains("192.168");
        }

        @Test
        @DisplayName("Suspicious user agent detection should work correctly")
        void testSuspiciousUserAgentValidation() {
            String[] userAgents = SecurityTestUtils.SUSPICIOUS_USER_AGENTS;
            
            assertThat(userAgents).as("Suspicious user agents should exist").isNotEmpty();
            assertThat(userAgents.length).as("Should have comprehensive suspicious user agents").isGreaterThan(5);
            
            // Verify user agents contain various scanning tools
            String allUserAgents = String.join(" ", userAgents).toLowerCase();
            assertThat(allUserAgents).as("Should include sqlmap")
                .contains("sqlmap");
            assertThat(allUserAgents).as("Should include nikto")
                .contains("nikto");
            assertThat(allUserAgents).as("Should include burp suite")
                .contains("burp");
            assertThat(allUserAgents).as("Should include scripting languages")
                .contains("python");
        }

        @Test
        @DisplayName("Path traversal payload detection should work correctly")
        void testPathTraversalPayloadValidation() {
            String[] payloads = SecurityTestUtils.PATH_TRAVERSAL_PAYLOADS;
            
            assertThat(payloads).as("Path traversal payloads should exist").isNotEmpty();
            assertThat(payloads.length).as("Should have comprehensive path traversal payloads").isGreaterThan(3);
            
            // Verify payloads contain various path traversal techniques
            String allPayloads = String.join(" ", payloads);
            assertThat(allPayloads).as("Should include basic traversal")
                .contains("../");
            assertThat(allPayloads).as("Should include Windows traversal")
                .contains("..\\");
            assertThat(allPayloads).as("Should include URL encoded traversal")
                .contains("%2e%2e");
            assertThat(allPayloads).as("Should target sensitive files")
                .contains("/etc/passwd");
        }
    }

    @Nested
    @DisplayName("🔧 Security Test Utility Methods Validation")  
    class SecurityTestUtilityMethodsValidationTests {

        @Test
        @DisplayName("JWT creation utilities should work correctly")
        void testJWTCreationUtilities() {
            // Test none algorithm JWT creation
            String payload = "{\"sub\":\"admin\",\"role\":\"ADMIN\"}";
            String noneJWT = SecurityTestUtils.createNoneAlgorithmJWT(payload);
            
            assertThat(noneJWT).as("None JWT should be created").isNotNull();
            assertThat(noneJWT).as("None JWT should have correct structure")
                .matches("[^.]+\\.[^.]+\\.");
            assertThat(noneJWT).as("None JWT should end with empty signature")
                .endsWith(".");
            
            // Test custom JWT creation
            String header = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
            String customJWT = SecurityTestUtils.createCustomJWT(header, payload, "signature");
            
            assertThat(customJWT).as("Custom JWT should be created").isNotNull();
            assertThat(customJWT.split("\\.")).as("Custom JWT should have 3 parts")
                .hasSize(3);
        }

        @Test
        @DisplayName("Large payload creation should work correctly")
        void testLargePayloadCreation() {
            String largePayload = SecurityTestUtils.createLargePayload(5); // 5KB
            
            assertThat(largePayload).as("Large payload should be created").isNotNull();
            assertThat(largePayload.length()).as("Large payload should be approximately 5KB")
                .isBetween(5000, 5200); // Allow some overhead
        }

        @Test
        @DisplayName("URL encoding utilities should work correctly")
        void testURLEncodingUtilities() {
            String testString = "'; DROP TABLE users; --";
            String encoded = SecurityTestUtils.urlEncode(testString);
            String doubleEncoded = SecurityTestUtils.doubleUrlEncode(testString);
            
            assertThat(encoded).as("URL encoding should work").isNotEqualTo(testString);
            assertThat(doubleEncoded).as("Double URL encoding should work")
                .isNotEqualTo(testString)
                .isNotEqualTo(encoded);
            
            // Verify specific characters are encoded
            assertThat(encoded).as("Single quotes should be encoded")
                .doesNotContain("'");
            assertThat(encoded).as("Spaces should be encoded")  
                .doesNotContain(" ");
        }

        @Test
        @DisplayName("Mass assignment payload creation should work correctly")
        void testMassAssignmentPayloadCreation() {
            String basePayload = "{\"name\":\"John\",\"email\":\"john@test.com\"}";
            String massAssignmentPayload = SecurityTestUtils.createMassAssignmentPayload(basePayload);
            
            assertThat(massAssignmentPayload).as("Mass assignment payload should be created").isNotNull();
            assertThat(massAssignmentPayload).as("Should contain admin role")
                .contains("\"role\":\"admin\"");
            assertThat(massAssignmentPayload).as("Should contain permissions")
                .contains("\"permissions\"");
            assertThat(massAssignmentPayload).as("Should contain isAdmin flag")
                .contains("\"isAdmin\":true");
        }

        @Test
        @DisplayName("NoSQL injection payload creation should work correctly")
        void testNoSQLInjectionPayloadCreation() {
            String payload = SecurityTestUtils.createNoSQLInjectionPayload("username", "admin");
            
            assertThat(payload).as("NoSQL injection payload should be created").isNotNull();
            assertThat(payload).as("Should contain $ne operator")
                .contains("$ne");
            assertThat(payload).as("Should contain $gt operator")
                .contains("$gt");
            assertThat(payload).as("Should contain $regex operator")
                .contains("$regex");
        }

        @Test
        @DisplayName("Basic auth header creation should work correctly")
        void testBasicAuthHeaderCreation() {
            String authHeader = SecurityTestUtils.createBasicAuthHeader("admin", "password");
            
            assertThat(authHeader).as("Basic auth header should be created").isNotNull();
            assertThat(authHeader).as("Should start with Basic")
                .startsWith("Basic ");
            assertThat(authHeader).as("Should be base64 encoded")
                .matches("Basic [A-Za-z0-9+/=]+");
        }
    }

    @Nested
    @DisplayName("🧪 Security Assertion Methods Validation")
    class SecurityAssertionMethodsValidationTests {

        @Test
        @DisplayName("Sensitive data exposure assertion should work correctly")
        void testSensitiveDataExposureAssertion() {
            // Test that assertion correctly identifies sensitive data
            String responseWithPassword = "{\"username\":\"user\",\"password\":\"secret123\"}";
            
            assertThrows(AssertionError.class, () -> {
                SecurityTestUtils.assertNoSensitiveDataExposed(responseWithPassword);
            }, "Should detect password field");
            
            String responseWithSecret = "{\"username\":\"user\",\"secret_key\":\"abc123\"}";
            
            assertThrows(AssertionError.class, () -> {
                SecurityTestUtils.assertNoSensitiveDataExposed(responseWithSecret);
            }, "Should detect secret field");
            
            // Test that assertion passes for safe responses
            String safeResponse = "{\"username\":\"user\",\"email\":\"user@test.com\"}";
            
            assertDoesNotThrow(() -> {
                SecurityTestUtils.assertNoSensitiveDataExposed(safeResponse);
            }, "Should pass for safe responses");
        }

        @Test
        @DisplayName("XSS sanitization assertion should work correctly")
        void testXSSSanitizationAssertion() {
            String xssPayload = "<script>alert('xss')</script>";
            
            // Should detect unsanitized XSS
            assertThrows(AssertionError.class, () -> {
                SecurityTestUtils.assertXSSSanitized(xssPayload, xssPayload);
            }, "Should detect unsanitized XSS payload");
            
            // Should pass for sanitized response
            String sanitizedResponse = "&lt;script&gt;alert('xss')&lt;/script&gt;";
            assertDoesNotThrow(() -> {
                SecurityTestUtils.assertXSSSanitized(sanitizedResponse, xssPayload);
            }, "Should pass for sanitized XSS");
        }

        @Test
        @DisplayName("SQL injection prevention assertion should work correctly") 
        void testSQLInjectionPreventionAssertion() {
            String sqlPayload = "'; DROP TABLE users; --";
            
            // Should detect SQL information disclosure
            String responseWithSQLError = "SQL error: table 'users' does not exist";
            
            assertThrows(AssertionError.class, () -> {
                SecurityTestUtils.assertSQLInjectionPrevented(responseWithSQLError, sqlPayload);
            }, "Should detect SQL error disclosure");
            
            // Should pass for safe response
            String safeResponse = "{\"results\":[],\"message\":\"No results found\"}";
            assertDoesNotThrow(() -> {
                SecurityTestUtils.assertSQLInjectionPrevented(safeResponse, sqlPayload);
            }, "Should pass for safe SQL response");
        }
    }

    @Nested
    @DisplayName("🔍 Test Data Factory Validation")
    class TestDataFactoryValidationTests {

        @Test
        @DisplayName("Test user creation should work correctly")
        void testUserCreation() {
            Object maliciousUser = SecurityTestUtils.TestDataFactory.createTestUser("username", "<script>alert('xss')</script>");
            
            assertThat(maliciousUser).as("User test data should be created").isNotNull();
            
            // Verify the malicious input is in the specified field
            String userJson = maliciousUser.toString();
            assertThat(userJson).as("Should contain malicious username").contains("<script>");
        }

        @Test
        @DisplayName("Test travel plan creation should work correctly")
        void testTravelPlanCreation() {
            Object maliciousPlan = SecurityTestUtils.TestDataFactory.createTestTravelPlan("title", "'; DROP TABLE travel_plans; --");
            
            assertThat(maliciousPlan).as("Travel plan test data should be created").isNotNull();
            
            String planJson = maliciousPlan.toString();
            assertThat(planJson).as("Should contain malicious title").contains("DROP TABLE");
        }

        @Test
        @DisplayName("Test place creation should work correctly")
        void testPlaceCreation() {
            Object maliciousPlace = SecurityTestUtils.TestDataFactory.createTestPlace("imageUrl", "http://localhost:8080/admin");
            
            assertThat(maliciousPlace).as("Place test data should be created").isNotNull();
            
            String placeJson = maliciousPlace.toString();
            assertThat(placeJson).as("Should contain malicious image URL").contains("localhost");
        }
    }

    @Nested
    @DisplayName("📊 Security Test Result Tracking Validation")
    class SecurityTestResultValidationTests {

        @Test
        @DisplayName("Security test result creation should work correctly")
        void testSecurityTestResultCreation() {
            // Test passed result
            SecurityTestUtils.SecurityTestResult passedResult = SecurityTestUtils.passed("Test", "Success message");
            
            assertThat(passedResult).as("Passed result should be created").isNotNull();
            assertThat(passedResult.isPassed()).as("Should be marked as passed").isTrue();
            assertThat(passedResult.getTestName()).as("Should have test name").isEqualTo("Test");
            assertThat(passedResult.getMessage()).as("Should have message").isEqualTo("Success message");
            
            // Test failed result
            SecurityTestUtils.SecurityTestResult failedResult = SecurityTestUtils.failed("Test", "Failure message", "Fix recommendation");
            
            assertThat(failedResult).as("Failed result should be created").isNotNull();
            assertThat(failedResult.isPassed()).as("Should be marked as failed").isFalse();
            assertThat(failedResult.getRecommendation()).as("Should have recommendation").isEqualTo("Fix recommendation");
        }

        @Test
        @DisplayName("Security test result string representation should work correctly")
        void testSecurityTestResultStringRepresentation() {
            SecurityTestUtils.SecurityTestResult passedResult = SecurityTestUtils.passed("SQL Injection Test", "All payloads blocked");
            SecurityTestUtils.SecurityTestResult failedResult = SecurityTestUtils.failed("XSS Test", "Payload not sanitized", "Implement output encoding");
            
            String passedString = passedResult.toString();
            String failedString = failedResult.toString();
            
            assertThat(passedString).as("Passed result should show success")
                .contains("✅")
                .contains("SQL Injection Test")
                .contains("All payloads blocked");
                
            assertThat(failedString).as("Failed result should show failure")
                .contains("❌")
                .contains("XSS Test")
                .contains("Payload not sanitized")
                .contains("Implement output encoding");
        }
    }

    @Test
    @DisplayName("🎯 Comprehensive security test framework integration")
    void testComprehensiveSecurityTestFrameworkIntegration() {
        // This test validates that all components of our security testing framework work together
        
        // 1. Verify payloads are available
        assertThat(SecurityTestUtils.SQL_INJECTION_PAYLOADS).as("SQL payloads available").isNotEmpty();
        assertThat(SecurityTestUtils.XSS_PAYLOADS).as("XSS payloads available").isNotEmpty();
        assertThat(SecurityTestUtils.COMMAND_INJECTION_PAYLOADS).as("Command injection payloads available").isNotEmpty();
        assertThat(SecurityTestUtils.SSRF_PAYLOADS).as("SSRF payloads available").isNotEmpty();
        assertThat(SecurityTestUtils.SUSPICIOUS_USER_AGENTS).as("Suspicious user agents available").isNotEmpty();
        assertThat(SecurityTestUtils.PATH_TRAVERSAL_PAYLOADS).as("Path traversal payloads available").isNotEmpty();
        
        // 2. Verify utility methods work
        String largePayload = SecurityTestUtils.createLargePayload(1);
        assertThat(largePayload).as("Large payload creation works").isNotNull();
        
        String noneJWT = SecurityTestUtils.createNoneAlgorithmJWT("{\"sub\":\"test\"}");
        assertThat(noneJWT).as("JWT creation works").isNotNull();
        
        String encoded = SecurityTestUtils.urlEncode("test string");
        assertThat(encoded).as("URL encoding works").isNotEqualTo("test string");
        
        // 3. Verify test data factories work
        Object testUser = SecurityTestUtils.TestDataFactory.createTestUser("username", "malicious");
        Object testPlan = SecurityTestUtils.TestDataFactory.createTestTravelPlan("title", "malicious");
        Object testPlace = SecurityTestUtils.TestDataFactory.createTestPlace("name", "malicious");
        
        assertThat(testUser).as("User factory works").isNotNull();
        assertThat(testPlan).as("Travel plan factory works").isNotNull();
        assertThat(testPlace).as("Place factory works").isNotNull();
        
        // 4. Verify assertion methods work (test safe cases)
        assertDoesNotThrow(() -> {
            SecurityTestUtils.assertNoSensitiveDataExposed("{\"username\":\"user\"}");
            SecurityTestUtils.assertXSSSanitized("safe text", "<script>alert('xss')</script>");
            SecurityTestUtils.assertSQLInjectionPrevented("safe response", "'; DROP TABLE users; --");
        }, "Security assertions should work for safe cases");
        
        // 5. Verify test result tracking works
        SecurityTestUtils.SecurityTestResult result = SecurityTestUtils.passed("Framework Test", "All components working");
        assertThat(result.isPassed()).as("Result tracking works").isTrue();
        
        System.out.println("🎉 Comprehensive Security Test Framework Validation Complete!");
        System.out.println("✅ All security testing utilities are functioning correctly");
        System.out.println("✅ Ready for comprehensive security testing of Oddiya application");
    }
}