package com.oddiya.security;

import com.oddiya.config.TestSecurityConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.Base64;
import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * OWASP A02:2021 - Cryptographic Failures & A07:2021 - Identification and Authentication Failures
 * JWT Token Manipulation Security Tests
 * 
 * Tests various JWT token manipulation attacks including signature bypass, algorithm confusion,
 * claim manipulation, and token structure attacks.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebMvc
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("JWT Token Manipulation Security Tests - OWASP A02:2021 & A07:2021")
public class JwtTokenManipulationSecurityTest {

    @Autowired
    private WebApplicationContext context;
    
    private MockMvc mockMvc;
    private ObjectMapper objectMapper = new ObjectMapper();
    
    // Valid JWT structure components for manipulation
    private static final String VALID_HEADER_HS256 = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
    private static final String VALID_HEADER_NONE = "{\"alg\":\"none\",\"typ\":\"JWT\"}";
    private static final String VALID_PAYLOAD = "{\"sub\":\"test-user\",\"iat\":1640995200,\"exp\":9999999999}";
    private static final String EXPIRED_PAYLOAD = "{\"sub\":\"test-user\",\"iat\":1640995200,\"exp\":1640995200}";
    private static final String ADMIN_PAYLOAD = "{\"sub\":\"admin\",\"role\":\"admin\",\"iat\":1640995200,\"exp\":9999999999}";
    private static final String INVALID_PAYLOAD = "{\"sub\":\"test-user\",\"iat\":\"invalid\",\"exp\":\"invalid\"}";
    
    // Algorithm confusion attack payloads
    private static final String[] ALGORITHM_CONFUSION_HEADERS = {
        "{\"alg\":\"none\",\"typ\":\"JWT\"}",
        "{\"alg\":\"None\",\"typ\":\"JWT\"}",
        "{\"alg\":\"NONE\",\"typ\":\"JWT\"}",
        "{\"alg\":\"RS256\",\"typ\":\"JWT\"}", // If using HS256 key as RSA public key
        "{\"alg\":\"HS512\",\"typ\":\"JWT\"}", // Different HMAC algorithm
        "{\"alg\":\"ES256\",\"typ\":\"JWT\"}", // ECDSA algorithm
        "{\"alg\":\"\",\"typ\":\"JWT\"}", // Empty algorithm
        "{\"typ\":\"JWT\"}", // Missing algorithm
        "{\"alg\":null,\"typ\":\"JWT\"}" // Null algorithm
    };

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
            .webAppContextSetup(context)
            .apply(SecurityMockMvcConfigurers.springSecurity())
            .build();
    }

    @Test
    @org.junit.jupiter.api.Order(1)
    @DisplayName("JWT Manipulation Test - None Algorithm Attack")
    void testNoneAlgorithmAttack() throws Exception {
        // Create JWT with "none" algorithm (no signature required)
        String noneToken = createJwtToken(VALID_HEADER_NONE, VALID_PAYLOAD, "");
        
        mockMvc.perform(get("/api/v1/travel-plans")
                .header("Authorization", "Bearer " + noneToken)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized()); // Should reject "none" algorithm
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    @DisplayName("JWT Manipulation Test - Algorithm Confusion")
    void testAlgorithmConfusion() throws Exception {
        for (String maliciousHeader : ALGORITHM_CONFUSION_HEADERS) {
            String maliciousToken = createJwtToken(maliciousHeader, VALID_PAYLOAD, "fake-signature");
            
            mockMvc.perform(get("/api/v1/travel-plans")
                    .header("Authorization", "Bearer " + maliciousToken)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized()); // Should reject algorithm confusion
        }
    }

    @Test
    @org.junit.jupiter.api.Order(3)
    @DisplayName("JWT Manipulation Test - Signature Bypass")
    void testSignatureBypass() throws Exception {
        String[] bypassAttempts = {
            createJwtToken(VALID_HEADER_HS256, VALID_PAYLOAD, ""), // No signature
            createJwtToken(VALID_HEADER_HS256, VALID_PAYLOAD, "invalid"), // Invalid signature
            createJwtToken(VALID_HEADER_HS256, VALID_PAYLOAD, "fake.signature.here"), // Fake signature
            createJwtTokenNoSignature(VALID_HEADER_HS256, VALID_PAYLOAD), // Missing signature part
            VALID_PAYLOAD, // Just payload without header and signature
            Base64.getEncoder().encodeToString(VALID_PAYLOAD.getBytes(StandardCharsets.UTF_8)) // Just encoded payload
        };
        
        for (String token : bypassAttempts) {
            mockMvc.perform(get("/api/v1/travel-plans")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized()); // Should reject invalid tokens
        }
    }

    @Test
    @org.junit.jupiter.api.Order(4)
    @DisplayName("JWT Manipulation Test - Claims Manipulation")
    void testClaimsManipulation() throws Exception {
        String[] maliciousPayloads = {
            "{\"sub\":\"admin\",\"role\":\"admin\",\"iat\":1640995200,\"exp\":9999999999}",
            "{\"sub\":\"test-user\",\"role\":\"admin\",\"iat\":1640995200,\"exp\":9999999999}",
            "{\"sub\":\"test-user\",\"permissions\":[\"admin\",\"write\",\"delete\"],\"iat\":1640995200,\"exp\":9999999999}",
            "{\"sub\":\"test-user\",\"isAdmin\":true,\"iat\":1640995200,\"exp\":9999999999}",
            "{\"sub\":\"../../../admin\",\"iat\":1640995200,\"exp\":9999999999}",
            "{\"sub\":\"test-user\",\"aud\":\"admin-service\",\"iat\":1640995200,\"exp\":9999999999}",
            "{\"sub\":\"test-user\",\"scope\":\"admin\",\"iat\":1640995200,\"exp\":9999999999}"
        };
        
        for (String payload : maliciousPayloads) {
            String token = createJwtToken(VALID_HEADER_HS256, payload, "fake-signature");
            
            mockMvc.perform(get("/api/v1/travel-plans")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized()); // Should reject manipulated claims
        }
    }

    @Test
    @org.junit.jupiter.api.Order(5)
    @DisplayName("JWT Manipulation Test - Expired Token")
    void testExpiredToken() throws Exception {
        String expiredToken = createJwtToken(VALID_HEADER_HS256, EXPIRED_PAYLOAD, "fake-signature");
        
        mockMvc.perform(get("/api/v1/travel-plans")
                .header("Authorization", "Bearer " + expiredToken)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized()); // Should reject expired tokens
    }

    @Test
    @org.junit.jupiter.api.Order(6)
    @DisplayName("JWT Manipulation Test - Malformed Token Structure")
    void testMalformedTokenStructure() throws Exception {
        String[] malformedTokens = {
            "invalid-token-format",
            "header.payload", // Missing signature
            "header.payload.signature.extra", // Extra parts
            ".payload.signature", // Missing header
            "header..signature", // Missing payload
            "header.payload.", // Empty signature
            "..", // Only dots
            "", // Empty token
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9", // Only header
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.", // Header with dot
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..", // Header with two dots
            "not-base64.not-base64.not-base64" // Invalid base64
        };
        
        for (String token : malformedTokens) {
            mockMvc.perform(get("/api/v1/travel-plans")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized()); // Should reject malformed tokens
        }
    }

    @Test
    @org.junit.jupiter.api.Order(7)
    @DisplayName("JWT Manipulation Test - Invalid Base64 Encoding")
    void testInvalidBase64Encoding() throws Exception {
        String[] invalidBase64Tokens = {
            "invalid!@#$.invalid!@#$.invalid!@#$",
            "header with spaces.payload with spaces.signature with spaces",
            "header\nwith\nnewlines.payload\nwith\nnewlines.signature\nwith\nnewlines",
            "中文.中文.中文", // Non-ASCII characters
            "header===.payload===.signature===", // Invalid padding
            "header=.payload=.signature=", // Invalid padding
            "header.payload.signature with spaces and special chars !@#$%^&*()"
        };
        
        for (String token : invalidBase64Tokens) {
            mockMvc.perform(get("/api/v1/travel-plans")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized()); // Should reject invalid base64
        }
    }

    @Test
    @org.junit.jupiter.api.Order(8)
    @DisplayName("JWT Manipulation Test - Token Size Limits")
    void testTokenSizeLimits() throws Exception {
        // Create oversized tokens
        String largePayload = "{\"sub\":\"test-user\",\"data\":\"" + "x".repeat(10000) + "\",\"iat\":1640995200,\"exp\":9999999999}";
        String largeToken = createJwtToken(VALID_HEADER_HS256, largePayload, "fake-signature");
        
        mockMvc.perform(get("/api/v1/travel-plans")
                .header("Authorization", "Bearer " + largeToken)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized()); // Should handle large tokens gracefully
        
        // Create extremely long signature
        String longSignatureToken = createJwtToken(VALID_HEADER_HS256, VALID_PAYLOAD, "x".repeat(10000));
        
        mockMvc.perform(get("/api/v1/travel-plans")
                .header("Authorization", "Bearer " + longSignatureToken)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized()); // Should handle long signatures gracefully
    }

    @Test
    @org.junit.jupiter.api.Order(9)
    @DisplayName("JWT Manipulation Test - JSON Injection in Claims")
    void testJsonInjectionInClaims() throws Exception {
        String[] jsonInjectionPayloads = {
            "{\"sub\":\"test-user\",\"role\":\"user\",\"admin\":true,\"iat\":1640995200,\"exp\":9999999999}",
            "{\"sub\":\"test-user\\\",\\\"role\\\":\\\"admin\",\"iat\":1640995200,\"exp\":9999999999}",
            "{\"sub\":\"test-user\",\"nested\":{\"role\":\"admin\"},\"iat\":1640995200,\"exp\":9999999999}",
            "{\"sub\":\"test-user\",\"array\":[\"user\",\"admin\"],\"iat\":1640995200,\"exp\":9999999999}",
            "{\"sub\":\"test-user\",\"script\":\"<script>alert('xss')</script>\",\"iat\":1640995200,\"exp\":9999999999}"
        };
        
        for (String payload : jsonInjectionPayloads) {
            String token = createJwtToken(VALID_HEADER_HS256, payload, "fake-signature");
            
            mockMvc.perform(get("/api/v1/travel-plans")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized()); // Should reject JSON injection attempts
        }
    }

    @Test
    @org.junit.jupiter.api.Order(10)
    @DisplayName("JWT Manipulation Test - Critical Claims Missing")
    void testCriticalClaimsMissing() throws Exception {
        String[] incompletePayloads = {
            "{\"iat\":1640995200,\"exp\":9999999999}", // Missing subject
            "{\"sub\":\"test-user\",\"exp\":9999999999}", // Missing issued at
            "{\"sub\":\"test-user\",\"iat\":1640995200}", // Missing expiration
            "{\"sub\":\"\",\"iat\":1640995200,\"exp\":9999999999}", // Empty subject
            "{\"sub\":null,\"iat\":1640995200,\"exp\":9999999999}", // Null subject
            "{\"sub\":\"test-user\",\"iat\":null,\"exp\":9999999999}", // Null issued at
            "{\"sub\":\"test-user\",\"iat\":1640995200,\"exp\":null}" // Null expiration
        };
        
        for (String payload : incompletePayloads) {
            String token = createJwtToken(VALID_HEADER_HS256, payload, "fake-signature");
            
            mockMvc.perform(get("/api/v1/travel-plans")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized()); // Should reject incomplete claims
        }
    }

    @Test
    @org.junit.jupiter.api.Order(11)
    @DisplayName("JWT Manipulation Test - Token Replay Attack")
    void testTokenReplayAttack() throws Exception {
        // Test that the same token used multiple times is handled correctly
        String replayToken = createJwtToken(VALID_HEADER_HS256, VALID_PAYLOAD, "fake-signature");
        
        // Multiple requests with same token should be consistently rejected
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/api/v1/travel-plans")
                    .header("Authorization", "Bearer " + replayToken)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    @org.junit.jupiter.api.Order(12)
    @DisplayName("JWT Manipulation Test - Token in Different Locations")
    void testTokenInDifferentLocations() throws Exception {
        String token = createJwtToken(VALID_HEADER_HS256, VALID_PAYLOAD, "fake-signature");
        
        // Test token in query parameter
        mockMvc.perform(get("/api/v1/travel-plans")
                .param("token", token)
                .param("access_token", token)
                .param("jwt", token)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized()); // Should not accept token in query params
        
        // Test token in POST body
        String requestBody = "{\"token\":\"" + token + "\",\"access_token\":\"" + token + "\"}";
        mockMvc.perform(post("/api/v1/travel-plans")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isUnauthorized()); // Should not accept token in body
        
        // Test token in cookie
        mockMvc.perform(get("/api/v1/travel-plans")
                .header("Cookie", "token=" + token + "; jwt=" + token)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized()); // Should not accept token in cookies
    }

    @Test
    @org.junit.jupiter.api.Order(13)
    @DisplayName("JWT Manipulation Test - Header Parameter Injection")
    void testHeaderParameterInjection() throws Exception {
        String[] maliciousHeaders = {
            "{\"alg\":\"HS256\",\"typ\":\"JWT\",\"kid\":\"../../../etc/passwd\"}",
            "{\"alg\":\"HS256\",\"typ\":\"JWT\",\"jku\":\"http://evil.com/keys\"}",
            "{\"alg\":\"HS256\",\"typ\":\"JWT\",\"x5u\":\"http://evil.com/cert\"}",
            "{\"alg\":\"HS256\",\"typ\":\"JWT\",\"crit\":[\"exp\",\"iat\"]}",
            "{\"alg\":\"HS256\",\"typ\":\"JWT\",\"zip\":\"DEF\"}",
            "{\"alg\":\"HS256\",\"typ\":\"JWT\",\"enc\":\"A256GCM\"}",
            "{\"alg\":\"HS256\",\"typ\":\"JWT\",\"kid\":\"<script>alert('xss')</script>\"}"
        };
        
        for (String header : maliciousHeaders) {
            String token = createJwtToken(header, VALID_PAYLOAD, "fake-signature");
            
            mockMvc.perform(get("/api/v1/travel-plans")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized()); // Should reject malicious header parameters
        }
    }

    @Test
    @org.junit.jupiter.api.Order(14)
    @DisplayName("JWT Manipulation Test - Time-based Attacks")
    void testTimeBasedAttacks() throws Exception {
        // Test future issued-at time
        String futurePayload = "{\"sub\":\"test-user\",\"iat\":9999999999,\"exp\":9999999999}";
        String futureToken = createJwtToken(VALID_HEADER_HS256, futurePayload, "fake-signature");
        
        mockMvc.perform(get("/api/v1/travel-plans")
                .header("Authorization", "Bearer " + futureToken)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized()); // Should reject future-dated tokens
        
        // Test negative timestamp
        String negativePayload = "{\"sub\":\"test-user\",\"iat\":-1,\"exp\":9999999999}";
        String negativeToken = createJwtToken(VALID_HEADER_HS256, negativePayload, "fake-signature");
        
        mockMvc.perform(get("/api/v1/travel-plans")
                .header("Authorization", "Bearer " + negativeToken)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized()); // Should reject negative timestamps
    }

    @Test
    @org.junit.jupiter.api.Order(15)
    @DisplayName("JWT Manipulation Test - Special Character Injection")
    void testSpecialCharacterInjection() throws Exception {
        String[] specialCharPayloads = {
            "{\"sub\":\"test\\u0000user\",\"iat\":1640995200,\"exp\":9999999999}", // Null byte
            "{\"sub\":\"test\\nuser\",\"iat\":1640995200,\"exp\":9999999999}", // Newline
            "{\"sub\":\"test\\ruser\",\"iat\":1640995200,\"exp\":9999999999}", // Carriage return
            "{\"sub\":\"test\\tuser\",\"iat\":1640995200,\"exp\":9999999999}", // Tab
            "{\"sub\":\"test\\\"user\",\"iat\":1640995200,\"exp\":9999999999}", // Escaped quote
            "{\"sub\":\"test\\\\user\",\"iat\":1640995200,\"exp\":9999999999}", // Escaped backslash
            "{\"sub\":\"test/../admin\",\"iat\":1640995200,\"exp\":9999999999}", // Path traversal
            "{\"sub\":\"test%00admin\",\"iat\":1640995200,\"exp\":9999999999}" // URL encoded null
        };
        
        for (String payload : specialCharPayloads) {
            String token = createJwtToken(VALID_HEADER_HS256, payload, "fake-signature");
            
            mockMvc.perform(get("/api/v1/travel-plans")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized()); // Should handle special characters safely
        }
    }

    /**
     * Helper method to create a JWT token with given components
     */
    private String createJwtToken(String header, String payload, String signature) {
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
     * Helper method to create a JWT token without signature part
     */
    private String createJwtTokenNoSignature(String header, String payload) {
        String encodedHeader = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(header.getBytes(StandardCharsets.UTF_8));
        String encodedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        
        return encodedHeader + "." + encodedPayload; // Missing signature part entirely
    }
}