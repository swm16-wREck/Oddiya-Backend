package com.oddiya.security;

import com.oddiya.config.TestSecurityConfig;
import com.oddiya.dto.request.*;
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

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.containsString;

/**
 * OWASP A04:2021 - Insecure Design & A03:2021 - Injection
 * Input Validation Security Tests
 * 
 * Tests input validation security across all DTOs to prevent injection attacks,
 * buffer overflows, and other input-based security vulnerabilities.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebMvc
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Input Validation Security Tests - OWASP A04:2021 & A03:2021")
public class InputValidationSecurityTest {

    @Autowired
    private WebApplicationContext context;
    
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    
    // Malicious input payloads for testing
    private static final String[] MALICIOUS_STRINGS = {
        // XSS payloads
        "<script>alert('XSS')</script>",
        "<img src=\"x\" onerror=\"alert('XSS')\">",
        "javascript:alert('XSS')",
        
        // SQL injection payloads
        "'; DROP TABLE users; --",
        "' OR '1'='1",
        "admin'--",
        
        // Command injection payloads
        "; rm -rf /",
        "&& cat /etc/passwd",
        "| nc -l 4444",
        
        // Path traversal payloads
        "../../../etc/passwd",
        "..\\..\\..\\windows\\system32\\config\\sam",
        "%2e%2e%2f%2e%2e%2f%2e%2e%2fetc%2fpasswd",
        
        // LDAP injection payloads
        "*()|&'",
        "admin*)((|)(|(password=*)))",
        
        // XML injection payloads
        "<?xml version=\"1.0\"?><!DOCTYPE test [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]><test>&xxe;</test>",
        
        // NoSQL injection payloads
        "{\"$ne\": null}",
        "{\"$gt\": \"\"}",
        
        // Buffer overflow attempts
        "A".repeat(10000),
        "🚀".repeat(5000), // Unicode characters
        
        // Format string attacks
        "%s%s%s%s%s%s%s%s",
        "%x%x%x%x%x%x%x%x",
        
        // Null bytes and special characters
        "test\u0000admin",
        "test%00admin",
        "test\r\nadmin",
        "test\n\radmin"
    };
    
    // Invalid email formats
    private static final String[] INVALID_EMAILS = {
        "invalid-email",
        "@domain.com",
        "user@",
        "user..name@domain.com",
        "user@domain",
        "user@.domain.com",
        "user@domain..com",
        "user name@domain.com",
        "user@domain.c",
        "user@-domain.com",
        "user@domain-.com",
        "<script>@domain.com",
        "user@<script>.com"
    };
    
    // Invalid date formats and values
    private static final String[] INVALID_DATES = {
        "2024-13-01", // Invalid month
        "2024-02-30", // Invalid day
        "invalid-date",
        "2024/02/15", // Wrong format
        "15-02-2024", // Wrong format
        "2024-2-1", // Missing zero padding
        "2024-02-01T10:30:00", // DateTime instead of Date
        "1900-01-01", // Too old
        "3000-01-01", // Too far future
        "", // Empty
        "null", // String null
        "undefined" // String undefined
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
    @DisplayName("Input Validation Test - CreateTravelPlanRequest Malicious Input")
    @WithMockJwtUser("test-user-id")
    void testCreateTravelPlanRequestMaliciousInput() throws Exception {
        for (String maliciousString : MALICIOUS_STRINGS) {
            CreateTravelPlanRequest request = CreateTravelPlanRequest.builder()
                .title(maliciousString)
                .description(maliciousString)
                .destination(maliciousString)
                .startDate(LocalDate.now().plusDays(1))
                .endDate(LocalDate.now().plusDays(5))
                .isPublic(false)
                .tags(Arrays.asList(maliciousString))
                .build();
            
            String requestJson = objectMapper.writeValueAsString(request);
            
            mockMvc.perform(post("/api/v1/travel-plans")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestJson))
                    .andExpect(status().is4xxClientError()); // Should validate and reject malicious input
        }
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    @DisplayName("Input Validation Test - Field Length Limits")
    @WithMockJwtUser("test-user-id")
    void testFieldLengthLimits() throws Exception {
        // Test title length limit (200 characters)
        String longTitle = "A".repeat(201);
        CreateTravelPlanRequest titleRequest = CreateTravelPlanRequest.builder()
            .title(longTitle)
            .description("Valid description")
            .destination("Seoul")
            .startDate(LocalDate.now().plusDays(1))
            .endDate(LocalDate.now().plusDays(5))
            .build();
        
        String titleJson = objectMapper.writeValueAsString(titleRequest);
        
        mockMvc.perform(post("/api/v1/travel-plans")
                .contentType(MediaType.APPLICATION_JSON)
                .content(titleJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("Title must be between 1 and 200 characters")));
        
        // Test description length limit (2000 characters)
        String longDescription = "A".repeat(2001);
        CreateTravelPlanRequest descRequest = CreateTravelPlanRequest.builder()
            .title("Valid title")
            .description(longDescription)
            .destination("Seoul")
            .startDate(LocalDate.now().plusDays(1))
            .endDate(LocalDate.now().plusDays(5))
            .build();
        
        String descJson = objectMapper.writeValueAsString(descRequest);
        
        mockMvc.perform(post("/api/v1/travel-plans")
                .contentType(MediaType.APPLICATION_JSON)
                .content(descJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("Description cannot exceed 2000 characters")));
    }

    @Test
    @org.junit.jupiter.api.Order(3)
    @DisplayName("Input Validation Test - Required Field Validation")
    @WithMockJwtUser("test-user-id")
    void testRequiredFieldValidation() throws Exception {
        // Test missing required fields
        CreateTravelPlanRequest emptyRequest = CreateTravelPlanRequest.builder().build();
        String emptyJson = objectMapper.writeValueAsString(emptyRequest);
        
        mockMvc.perform(post("/api/v1/travel-plans")
                .contentType(MediaType.APPLICATION_JSON)
                .content(emptyJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
        
        // Test null required fields
        String nullJson = """
            {
                "title": null,
                "description": "Valid description",
                "destination": null,
                "startDate": null,
                "endDate": null
            }
            """;
        
        mockMvc.perform(post("/api/v1/travel-plans")
                .contentType(MediaType.APPLICATION_JSON)
                .content(nullJson))
                .andExpect(status().isBadRequest());
        
        // Test empty string required fields
        CreateTravelPlanRequest emptyStringRequest = CreateTravelPlanRequest.builder()
            .title("")
            .destination("")
            .startDate(LocalDate.now().plusDays(1))
            .endDate(LocalDate.now().plusDays(5))
            .build();
        
        String emptyStringJson = objectMapper.writeValueAsString(emptyStringRequest);
        
        mockMvc.perform(post("/api/v1/travel-plans")
                .contentType(MediaType.APPLICATION_JSON)
                .content(emptyStringJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    @org.junit.jupiter.api.Order(4)
    @DisplayName("Input Validation Test - Date Validation")
    @WithMockJwtUser("test-user-id")
    void testDateValidation() throws Exception {
        // Test past dates (should be rejected)
        CreateTravelPlanRequest pastDateRequest = CreateTravelPlanRequest.builder()
            .title("Valid title")
            .destination("Seoul")
            .startDate(LocalDate.now().minusDays(1))
            .endDate(LocalDate.now().plusDays(5))
            .build();
        
        String pastDateJson = objectMapper.writeValueAsString(pastDateRequest);
        
        mockMvc.perform(post("/api/v1/travel-plans")
                .contentType(MediaType.APPLICATION_JSON)
                .content(pastDateJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("Start date must be in the future")));
        
        // Test end date before start date
        CreateTravelPlanRequest invalidDateRangeRequest = CreateTravelPlanRequest.builder()
            .title("Valid title")
            .destination("Seoul")
            .startDate(LocalDate.now().plusDays(5))
            .endDate(LocalDate.now().plusDays(1))
            .build();
        
        String invalidRangeJson = objectMapper.writeValueAsString(invalidDateRangeRequest);
        
        mockMvc.perform(post("/api/v1/travel-plans")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidRangeJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    @org.junit.jupiter.api.Order(5)
    @DisplayName("Input Validation Test - LoginRequest Validation")
    void testLoginRequestValidation() throws Exception {
        // Test missing provider
        LoginRequest missingProviderRequest = LoginRequest.builder()
            .idToken("valid-token")
            .build();
        
        String missingProviderJson = objectMapper.writeValueAsString(missingProviderRequest);
        
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(missingProviderJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("Provider is required")));
        
        // Test missing token
        LoginRequest missingTokenRequest = LoginRequest.builder()
            .provider("google")
            .build();
        
        String missingTokenJson = objectMapper.writeValueAsString(missingTokenRequest);
        
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(missingTokenJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("ID token is required")));
        
        // Test malicious provider values
        for (String malicious : MALICIOUS_STRINGS) {
            LoginRequest maliciousRequest = LoginRequest.builder()
                .provider(malicious)
                .idToken("valid-token")
                .build();
            
            String maliciousJson = objectMapper.writeValueAsString(maliciousRequest);
            
            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(maliciousJson))
                    .andExpect(status().is4xxClientError()); // Should reject malicious provider
        }
    }

    @Test
    @org.junit.jupiter.api.Order(6)
    @DisplayName("Input Validation Test - UpdateUserProfileRequest Validation")
    @WithMockJwtUser("test-user-id")
    void testUpdateUserProfileRequestValidation() throws Exception {
        // Test malicious input in profile fields
        for (String malicious : MALICIOUS_STRINGS) {
            UpdateUserProfileRequest maliciousRequest = UpdateUserProfileRequest.builder()
                .nickname(malicious)
                .bio(malicious)
                .build();
            
            String maliciousJson = objectMapper.writeValueAsString(maliciousRequest);
            
            mockMvc.perform(put("/api/v1/users/profile")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(maliciousJson))
                    .andExpect(status().is4xxClientError()); // Should validate and sanitize
        }
        
        // Test field length limits
        String longBio = "A".repeat(501); // Assuming 500 char limit
        UpdateUserProfileRequest longBioRequest = UpdateUserProfileRequest.builder()
            .nickname("Valid nickname")
            .bio(longBio)
            .build();
        
        String longBioJson = objectMapper.writeValueAsString(longBioRequest);
        
        mockMvc.perform(put("/api/v1/users/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content(longBioJson))
                .andExpect(status().is4xxClientError()); // Should reject long bio
    }

    @Test
    @org.junit.jupiter.api.Order(7)
    @DisplayName("Input Validation Test - SignUpRequest Email Validation")
    void testSignUpRequestEmailValidation() throws Exception {
        for (String invalidEmail : INVALID_EMAILS) {
            SignUpRequest invalidEmailRequest = SignUpRequest.builder()
                .email(invalidEmail)
                .password("ValidPassword123!")
                .nickname("ValidNickname")
                .build();
            
            String invalidEmailJson = objectMapper.writeValueAsString(invalidEmailRequest);
            
            mockMvc.perform(post("/api/v1/auth/signup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(invalidEmailJson))
                    .andExpect(status().isBadRequest()); // Should reject invalid emails
        }
    }

    @Test
    @org.junit.jupiter.api.Order(8)
    @DisplayName("Input Validation Test - JSON Structure Attacks")
    @WithMockJwtUser("test-user-id")
    void testJsonStructureAttacks() throws Exception {
        // Test deeply nested JSON
        String deeplyNestedJson = """
            {
                "title": "Test",
                "destination": "Seoul",
                "startDate": "2024-12-25",
                "endDate": "2024-12-30",
                "nested": {
                    "level1": {
                        "level2": {
                            "level3": {
                                "level4": {
                                    "level5": "deep"
                                }
                            }
                        }
                    }
                }
            }
            """;
        
        mockMvc.perform(post("/api/v1/travel-plans")
                .contentType(MediaType.APPLICATION_JSON)
                .content(deeplyNestedJson))
                .andExpect(status().is4xxClientError()); // Should handle gracefully
        
        // Test JSON with large arrays
        String largeArrayJson = """
            {
                "title": "Test",
                "destination": "Seoul", 
                "startDate": "2024-12-25",
                "endDate": "2024-12-30",
                "tags": [%s]
            }
            """.formatted("\"tag\"," .repeat(10000));
        
        mockMvc.perform(post("/api/v1/travel-plans")
                .contentType(MediaType.APPLICATION_JSON)
                .content(largeArrayJson))
                .andExpect(status().is4xxClientError()); // Should reject large arrays
        
        // Test JSON bomb (exponential expansion)
        String jsonBomb = """
            {
                "title": "Test",
                "destination": "Seoul",
                "startDate": "2024-12-25", 
                "endDate": "2024-12-30",
                "data": "%s"
            }
            """.formatted("x".repeat(100000));
        
        mockMvc.perform(post("/api/v1/travel-plans")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonBomb))
                .andExpect(status().is4xxClientError()); // Should handle large payloads
    }

    @Test
    @org.junit.jupiter.api.Order(9)
    @DisplayName("Input Validation Test - Unicode and Encoding Attacks")
    @WithMockJwtUser("test-user-id")
    void testUnicodeAndEncodingAttacks() throws Exception {
        String[] unicodeAttacks = {
            "Admin\u202E\u0000\u0001", // Right-to-left override + control chars
            "test\uFEFF\u200B\u200C\u200D", // Zero-width characters
            "\u001F\u007F\u009F", // Control characters
            "🚀🔥💯🎯", // Emoji overload
            "א״ב", // Hebrew characters
            "测试", // Chinese characters
            "\u0000\u0001\u0002\u0003\u0004\u0005\u0006\u0007", // Null and control bytes
            "café", // Accented characters
            "Ñoño", // Spanish characters
            "\uD83D\uDE00".repeat(1000) // Emoji flood
        };
        
        for (String unicodeAttack : unicodeAttacks) {
            CreateTravelPlanRequest request = CreateTravelPlanRequest.builder()
                .title(unicodeAttack)
                .description(unicodeAttack)
                .destination("Seoul")
                .startDate(LocalDate.now().plusDays(1))
                .endDate(LocalDate.now().plusDays(5))
                .build();
            
            String requestJson = objectMapper.writeValueAsString(request);
            
            mockMvc.perform(post("/api/v1/travel-plans")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestJson))
                    .andExpect(status().isCreated()); // Should handle Unicode gracefully
        }
    }

    @Test
    @org.junit.jupiter.api.Order(10)
    @DisplayName("Input Validation Test - Boundary Value Testing")
    @WithMockJwtUser("test-user-id")
    void testBoundaryValues() throws Exception {
        // Test exact boundary values
        
        // Title: exactly 200 characters (boundary)
        String exactTitle = "A".repeat(200);
        CreateTravelPlanRequest boundaryRequest = CreateTravelPlanRequest.builder()
            .title(exactTitle)
            .description("Valid description")
            .destination("Seoul")
            .startDate(LocalDate.now().plusDays(1))
            .endDate(LocalDate.now().plusDays(5))
            .build();
        
        String boundaryJson = objectMapper.writeValueAsString(boundaryRequest);
        
        mockMvc.perform(post("/api/v1/travel-plans")
                .contentType(MediaType.APPLICATION_JSON)
                .content(boundaryJson))
                .andExpect(status().isCreated()); // Should accept boundary value
        
        // Description: exactly 2000 characters (boundary)
        String exactDescription = "A".repeat(2000);
        CreateTravelPlanRequest descBoundaryRequest = CreateTravelPlanRequest.builder()
            .title("Valid title")
            .description(exactDescription)
            .destination("Seoul")
            .startDate(LocalDate.now().plusDays(1))
            .endDate(LocalDate.now().plusDays(5))
            .build();
        
        String descBoundaryJson = objectMapper.writeValueAsString(descBoundaryRequest);
        
        mockMvc.perform(post("/api/v1/travel-plans")
                .contentType(MediaType.APPLICATION_JSON)
                .content(descBoundaryJson))
                .andExpect(status().isCreated()); // Should accept boundary value
        
        // Test minimum values
        CreateTravelPlanRequest minRequest = CreateTravelPlanRequest.builder()
            .title("A") // Minimum 1 character
            .destination("S") // Minimum 1 character
            .startDate(LocalDate.now().plusDays(1))
            .endDate(LocalDate.now().plusDays(1)) // Same day trip
            .build();
        
        String minJson = objectMapper.writeValueAsString(minRequest);
        
        mockMvc.perform(post("/api/v1/travel-plans")
                .contentType(MediaType.APPLICATION_JSON)
                .content(minJson))
                .andExpect(status().isCreated()); // Should accept minimum values
    }

    @Test
    @org.junit.jupiter.api.Order(11)
    @DisplayName("Input Validation Test - Content-Type Validation")
    @WithMockJwtUser("test-user-id")
    void testContentTypeValidation() throws Exception {
        String validJson = """
            {
                "title": "Valid Title",
                "destination": "Seoul",
                "startDate": "2024-12-25",
                "endDate": "2024-12-30"
            }
            """;
        
        // Test wrong content type
        mockMvc.perform(post("/api/v1/travel-plans")
                .contentType(MediaType.TEXT_PLAIN)
                .content(validJson))
                .andExpect(status().isUnsupportedMediaType()); // Should reject wrong content type
        
        // Test missing content type
        mockMvc.perform(post("/api/v1/travel-plans")
                .content(validJson))
                .andExpect(status().isUnsupportedMediaType()); // Should reject missing content type
        
        // Test malformed JSON
        String malformedJson = "{ \"title\": \"Valid Title\", \"destination\": \"Seoul\" ";
        
        mockMvc.perform(post("/api/v1/travel-plans")
                .contentType(MediaType.APPLICATION_JSON)
                .content(malformedJson))
                .andExpect(status().isBadRequest()); // Should reject malformed JSON
    }

    @Test
    @org.junit.jupiter.api.Order(12)
    @DisplayName("Input Validation Test - Parameter Pollution")
    void testParameterPollution() throws Exception {
        // Test parameter pollution in query parameters
        mockMvc.perform(get("/api/v1/travel-plans/search")
                .param("query", "value1")
                .param("query", "value2")
                .param("query", "value3")
                .param("page", "0")
                .param("page", "999")
                .param("size", "10")
                .param("size", "999999")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()); // Should handle parameter pollution gracefully
    }
}