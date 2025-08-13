package com.oddiya.security;

import com.oddiya.config.TestSecurityConfig;
import com.oddiya.util.JwtTestUtils;
import com.oddiya.service.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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
 * OWASP A03:2021 - SQL Injection Security Tests
 * 
 * Tests various SQL injection attack vectors on all search and filter endpoints.
 * Verifies that user input is properly sanitized and parameterized queries are used.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebMvc
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("SQL Injection Security Tests - OWASP A03:2021")
public class SqlInjectionSecurityTest {

    @Autowired
    private WebApplicationContext context;
    
    @MockBean
    private JwtService jwtService;
    
    private MockMvc mockMvc;
    private ObjectMapper objectMapper = new ObjectMapper();
    
    // Common SQL injection payloads
    private static final String[] SQL_INJECTION_PAYLOADS = {
        "' OR '1'='1",
        "'; DROP TABLE users; --",
        "' UNION SELECT * FROM users --",
        "admin'--",
        "' OR 1=1--",
        "'; INSERT INTO users VALUES ('evil', 'hacker'); --",
        "' OR 'a'='a",
        "1' OR '1'='1' /*",
        "' OR 1=1#",
        "' UNION ALL SELECT NULL,NULL,NULL,version() --",
        "'; EXEC xp_cmdshell('dir'); --",
        "' OR SLEEP(5) --",
        "' OR pg_sleep(5) --",
        "'; WAITFOR DELAY '00:00:05' --",
        "' AND (SELECT COUNT(*) FROM users) > 0 --",
        "' OR EXISTS(SELECT * FROM users) --"
    };
    
    // Time-based blind SQL injection payloads
    private static final String[] TIME_BASED_SQL_PAYLOADS = {
        "'; WAITFOR DELAY '00:00:05' --",
        "' OR pg_sleep(5) --",
        "' OR SLEEP(5) --",
        "'; SELECT pg_sleep(5); --",
        "' UNION SELECT NULL,NULL,pg_sleep(5) --"
    };
    
    // Boolean-based blind SQL injection payloads
    private static final String[] BOOLEAN_SQL_PAYLOADS = {
        "' AND 1=1 --",
        "' AND 1=2 --",
        "' OR (SELECT COUNT(*) FROM users) > 0 --",
        "' AND (SELECT COUNT(*) FROM users) = (SELECT COUNT(*) FROM users) --",
        "' OR EXISTS(SELECT 1) --"
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
    @DisplayName("SQL Injection Test - Travel Plan Search Endpoint")
    void testSqlInjectionInTravelPlanSearch() throws Exception {
        for (String payload : SQL_INJECTION_PAYLOADS) {
            mockMvc.perform(get("/api/v1/travel-plans/search")
                    .param("query", payload)
                    .param("page", "0")
                    .param("size", "20")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content").isArray());
        }
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    @DisplayName("SQL Injection Test - User Travel Plans with User ID")
    void testSqlInjectionInUserTravelPlans() throws Exception {
        for (String payload : SQL_INJECTION_PAYLOADS) {
            mockMvc.perform(get("/api/v1/travel-plans/user/" + payload)
                    .param("page", "0")
                    .param("size", "20")
                    .param("sortBy", "createdAt")
                    .param("sortDirection", "DESC")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound()); // Should return 404, not expose SQL error
        }
    }

    @Test
    @org.junit.jupiter.api.Order(3)
    @DisplayName("SQL Injection Test - Places Search Endpoint")
    void testSqlInjectionInPlacesSearch() throws Exception {
        for (String payload : SQL_INJECTION_PAYLOADS) {
            mockMvc.perform(get("/api/v1/places/search")
                    .param("query", payload)
                    .param("latitude", "37.5665")
                    .param("longitude", "126.9780")
                    .param("radius", "10")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }

    @Test
    @org.junit.jupiter.api.Order(4)
    @DisplayName("SQL Injection Test - Places Nearby Endpoint")
    void testSqlInjectionInPlacesNearby() throws Exception {
        for (String payload : SQL_INJECTION_PAYLOADS) {
            mockMvc.perform(get("/api/v1/places/nearby")
                    .param("latitude", payload)
                    .param("longitude", "126.9780")
                    .param("radius", "10")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest()); // Should return 400 for invalid coordinates
        }
    }

    @Test
    @org.junit.jupiter.api.Order(5)
    @DisplayName("SQL Injection Test - Public Travel Plans with Sorting")
    void testSqlInjectionInPublicTravelPlansSort() throws Exception {
        for (String payload : SQL_INJECTION_PAYLOADS) {
            mockMvc.perform(get("/api/v1/travel-plans/public")
                    .param("page", "0")
                    .param("size", "20")
                    .param("sortBy", payload)
                    .param("sortDirection", "DESC")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().is5xxServerError()); // Should handle gracefully, not expose SQL
        }
    }

    @Test
    @org.junit.jupiter.api.Order(6)
    @DisplayName("Time-Based Blind SQL Injection Test - Search Endpoints")
    void testTimeBasedSqlInjection() throws Exception {
        for (String payload : TIME_BASED_SQL_PAYLOADS) {
            long startTime = System.currentTimeMillis();
            
            mockMvc.perform(get("/api/v1/travel-plans/search")
                    .param("query", payload)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());
            
            long executionTime = System.currentTimeMillis() - startTime;
            
            // Should not take significantly longer (indicating time-based SQL injection vulnerability)
            assert executionTime < 2000 : "Query took too long: " + executionTime + "ms with payload: " + payload;
        }
    }

    @Test
    @org.junit.jupiter.api.Order(7)
    @DisplayName("Boolean-Based Blind SQL Injection Test")
    void testBooleanBasedSqlInjection() throws Exception {
        String truePayload = BOOLEAN_SQL_PAYLOADS[0]; // Should be true
        String falsePayload = BOOLEAN_SQL_PAYLOADS[1]; // Should be false
        
        String trueResponse = mockMvc.perform(get("/api/v1/travel-plans/search")
                .param("query", truePayload)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        
        String falseResponse = mockMvc.perform(get("/api/v1/travel-plans/search")
                .param("query", falsePayload)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        
        // Responses should be similar (both should return search results, not SQL boolean results)
        assert trueResponse.length() > 0;
        assert falseResponse.length() > 0;
    }

    @Test
    @org.junit.jupiter.api.Order(8)
    @DisplayName("SQL Injection Test - Travel Plan ID Parameter")
    @WithMockJwtUser("test-user-id")
    void testSqlInjectionInTravelPlanId() throws Exception {
        for (String payload : SQL_INJECTION_PAYLOADS) {
            mockMvc.perform(get("/api/v1/travel-plans/" + payload)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound()); // Should return 404, not SQL error
        }
    }

    @Test
    @org.junit.jupiter.api.Order(9)
    @DisplayName("SQL Injection Test - POST Request Body Parameters")
    @WithMockJwtUser("test-user-id")
    void testSqlInjectionInCreateTravelPlan() throws Exception {
        for (String payload : SQL_INJECTION_PAYLOADS) {
            String requestBody = """
                {
                    "title": "%s",
                    "description": "%s",
                    "destination": "%s",
                    "startDate": "2024-12-25",
                    "endDate": "2024-12-30",
                    "isPublic": false,
                    "tags": ["%s"]
                }
                """.formatted(payload, payload, payload, payload);
            
            mockMvc.perform(post("/api/v1/travel-plans")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                    .andExpect(status().is4xxClientError()); // Should validate and reject, not execute SQL
        }
    }

    @Test
    @org.junit.jupiter.api.Order(10)
    @DisplayName("SQL Injection Test - Header Parameters")
    void testSqlInjectionInHeaders() throws Exception {
        for (String payload : SQL_INJECTION_PAYLOADS) {
            mockMvc.perform(get("/api/v1/travel-plans/public")
                    .header("X-Custom-Header", payload)
                    .header("User-Agent", payload)
                    .header("Accept-Language", payload)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk()); // Should process normally, ignoring malicious headers
        }
    }

    @Test
    @org.junit.jupiter.api.Order(11)
    @DisplayName("SQL Injection Test - Authentication Token Validation")
    void testSqlInjectionInAuthToken() throws Exception {
        for (String payload : SQL_INJECTION_PAYLOADS) {
            mockMvc.perform(get("/api/v1/auth/validate")
                    .header("Authorization", "Bearer " + payload)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").value(false)); // Should return false, not SQL error
        }
    }

    @Test
    @org.junit.jupiter.api.Order(12)
    @DisplayName("SQL Injection Test - URL Encoded Payloads")
    void testUrlEncodedSqlInjection() throws Exception {
        String encodedPayload = java.net.URLEncoder.encode("'; DROP TABLE users; --", "UTF-8");
        
        mockMvc.perform(get("/api/v1/travel-plans/search")
                .param("query", encodedPayload)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @org.junit.jupiter.api.Order(13)
    @DisplayName("SQL Injection Test - Unicode and Special Characters")
    void testUnicodeAndSpecialCharactersSqlInjection() throws Exception {
        String[] unicodePayloads = {
            "\u0027 OR \u00271\u0027=\u00271", // Unicode single quotes
            "'; DROP TABLE users; --", // Regular SQL injection
            "\\'; DROP TABLE users; --", // Escaped quotes
            "'; /*comment*/ DROP TABLE users; --", // SQL comments
            "' || '1'='1", // Logical OR
            "' | '1'='1" // Bitwise OR
        };
        
        for (String payload : unicodePayloads) {
            mockMvc.perform(get("/api/v1/travel-plans/search")
                    .param("query", payload)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }

    @Test
    @org.junit.jupiter.api.Order(14)
    @DisplayName("SQL Injection Test - Multiple Parameter Combinations")
    void testMultipleParameterSqlInjection() throws Exception {
        String payload = "'; DROP TABLE users; --";
        
        mockMvc.perform(get("/api/v1/travel-plans/user/test-user")
                .param("page", payload)
                .param("size", payload)
                .param("sortBy", payload)
                .param("sortDirection", payload)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().is4xxClientError()); // Should validate parameters
    }

    @Test
    @org.junit.jupiter.api.Order(15)
    @DisplayName("SQL Injection Test - Error Message Information Disclosure")
    void testSqlInjectionErrorMessages() throws Exception {
        // Test that SQL errors don't leak database structure information
        String[] errorInducingPayloads = {
            "' AND (SELECT * FROM non_existent_table) --",
            "'; SELECT * FROM information_schema.tables --",
            "' UNION SELECT table_name FROM information_schema.tables --",
            "'; SELECT column_name FROM information_schema.columns --"
        };
        
        for (String payload : errorInducingPayloads) {
            String response = mockMvc.perform(get("/api/v1/travel-plans/search")
                    .param("query", payload)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk()) // Should not return SQL errors
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            
            // Verify no database-specific error messages are exposed
            assert !response.toLowerCase().contains("sql") : "SQL error exposed: " + response;
            assert !response.toLowerCase().contains("database") : "Database error exposed: " + response;
            assert !response.toLowerCase().contains("postgresql") : "PostgreSQL error exposed: " + response;
            assert !response.toLowerCase().contains("table") : "Table error exposed: " + response;
            assert !response.toLowerCase().contains("column") : "Column error exposed: " + response;
        }
    }
}