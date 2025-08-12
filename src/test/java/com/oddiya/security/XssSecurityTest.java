package com.oddiya.security;

import com.oddiya.config.TestSecurityConfig;
import com.oddiya.dto.request.CreateTravelPlanRequest;
import com.oddiya.dto.request.UpdateUserProfileRequest;
import com.oddiya.dto.request.CreatePlaceRequest;
import com.oddiya.dto.request.CreateReviewRequest;
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
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

/**
 * OWASP A07:2021 - Cross-Site Scripting (XSS) Security Tests
 * 
 * Tests various XSS attack vectors on user input fields.
 * Verifies that user input is properly sanitized and encoded before storage/display.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebMvc
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Cross-Site Scripting (XSS) Security Tests - OWASP A07:2021")
public class XssSecurityTest {

    @Autowired
    private WebApplicationContext context;
    
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    
    // Common XSS payloads
    private static final String[] XSS_PAYLOADS = {
        "<script>alert('XSS')</script>",
        "<img src=\"x\" onerror=\"alert('XSS')\">",
        "<svg onload=\"alert('XSS')\">",
        "javascript:alert('XSS')",
        "<iframe src=\"javascript:alert('XSS')\"></iframe>",
        "<body onload=\"alert('XSS')\">",
        "<div onclick=\"alert('XSS')\">Click me</div>",
        "<a href=\"javascript:alert('XSS')\">Click</a>",
        "';alert('XSS');//",
        "\"><script>alert('XSS')</script>",
        "<script>document.cookie='stolen'</script>",
        "<img src=x onerror=alert(String.fromCharCode(88,83,83))>",
        "<svg><script>alert('XSS')</script></svg>",
        "<math><mi xlink:href=\"data:x,<script>alert('XSS')</script>\">",
        "<details open ontoggle=\"alert('XSS')\">",
        "<marquee onstart=\"alert('XSS')\">",
        "<audio src=x onerror=alert('XSS')>",
        "<video src=x onerror=alert('XSS')>",
        "<source src=x onerror=alert('XSS')>",
        "<track src=x onerror=alert('XSS')>"
    };
    
    // HTML entity encoded XSS payloads
    private static final String[] ENCODED_XSS_PAYLOADS = {
        "&lt;script&gt;alert('XSS')&lt;/script&gt;",
        "&#60;script&#62;alert('XSS')&#60;/script&#62;",
        "%3Cscript%3Ealert('XSS')%3C/script%3E",
        "&quot;&gt;&lt;script&gt;alert('XSS')&lt;/script&gt;"
    };
    
    // Event handler XSS payloads
    private static final String[] EVENT_HANDLER_PAYLOADS = {
        "onload=\"alert('XSS')\"",
        "onerror=\"alert('XSS')\"",
        "onclick=\"alert('XSS')\"",
        "onmouseover=\"alert('XSS')\"",
        "onfocus=\"alert('XSS')\"",
        "onblur=\"alert('XSS')\"",
        "onchange=\"alert('XSS')\"",
        "onsubmit=\"alert('XSS')\""
    };
    
    // CSS-based XSS payloads
    private static final String[] CSS_XSS_PAYLOADS = {
        "style=\"background:url(javascript:alert('XSS'))\"",
        "style=\"expression(alert('XSS'))\"",
        "style=\"behavior:url(data:text/html,<script>alert('XSS')</script>)\"",
        "@import 'javascript:alert(\"XSS\")';"
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
    @DisplayName("XSS Test - Travel Plan Creation Input Fields")
    @WithMockJwtUser("test-user-id")
    void testXssInTravelPlanCreation() throws Exception {
        for (String payload : XSS_PAYLOADS) {
            CreateTravelPlanRequest request = CreateTravelPlanRequest.builder()
                .title(payload)
                .description(payload)
                .destination(payload)
                .startDate(LocalDate.now().plusDays(1))
                .endDate(LocalDate.now().plusDays(5))
                .isPublic(false)
                .tags(Arrays.asList(payload))
                .build();
            
            String requestJson = objectMapper.writeValueAsString(request);
            
            String response = mockMvc.perform(post("/api/v1/travel-plans")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestJson))
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            
            // Verify XSS payload is sanitized in response
            assertXssSanitized(response, payload);
        }
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    @DisplayName("XSS Test - User Profile Update Input Fields")
    @WithMockJwtUser("test-user-id")
    void testXssInUserProfileUpdate() throws Exception {
        for (String payload : XSS_PAYLOADS) {
            UpdateUserProfileRequest request = UpdateUserProfileRequest.builder()
                .nickname(payload)
                .bio(payload)
                .build();
            
            String requestJson = objectMapper.writeValueAsString(request);
            
            String response = mockMvc.perform(put("/api/v1/users/profile")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestJson))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            
            // Verify XSS payload is sanitized in response
            assertXssSanitized(response, payload);
        }
    }

    @Test
    @org.junit.jupiter.api.Order(3)
    @DisplayName("XSS Test - Search Query Parameters")
    void testXssInSearchQueries() throws Exception {
        for (String payload : XSS_PAYLOADS) {
            String response = mockMvc.perform(get("/api/v1/travel-plans/search")
                    .param("query", payload)
                    .param("page", "0")
                    .param("size", "10")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            
            // Verify XSS payload is not reflected in response
            assertXssSanitized(response, payload);
        }
    }

    @Test
    @org.junit.jupiter.api.Order(4)
    @DisplayName("XSS Test - HTTP Headers")
    void testXssInHttpHeaders() throws Exception {
        for (String payload : XSS_PAYLOADS) {
            String response = mockMvc.perform(get("/api/v1/travel-plans/public")
                    .header("User-Agent", payload)
                    .header("Referer", payload)
                    .header("X-Forwarded-For", payload)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            
            // Verify headers don't cause XSS in response
            assertXssSanitized(response, payload);
        }
    }

    @Test
    @org.junit.jupiter.api.Order(5)
    @DisplayName("XSS Test - URL Path Parameters")
    void testXssInUrlPathParameters() throws Exception {
        for (String payload : XSS_PAYLOADS) {
            // Encode payload for URL safety
            String encodedPayload = java.net.URLEncoder.encode(payload, "UTF-8");
            
            String response = mockMvc.perform(get("/api/v1/travel-plans/" + encodedPayload)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound()) // Expected for non-existent IDs
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            
            // Verify error response doesn't reflect XSS payload
            assertXssSanitized(response, payload);
        }
    }

    @Test
    @org.junit.jupiter.api.Order(6)
    @DisplayName("XSS Test - HTML Entity Encoded Payloads")
    @WithMockJwtUser("test-user-id")
    void testHtmlEntityEncodedXss() throws Exception {
        for (String payload : ENCODED_XSS_PAYLOADS) {
            CreateTravelPlanRequest request = CreateTravelPlanRequest.builder()
                .title(payload)
                .description(payload)
                .destination("Seoul")
                .startDate(LocalDate.now().plusDays(1))
                .endDate(LocalDate.now().plusDays(5))
                .build();
            
            String requestJson = objectMapper.writeValueAsString(request);
            
            String response = mockMvc.perform(post("/api/v1/travel-plans")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestJson))
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            
            // Verify encoded payload is properly handled
            assert !response.contains("<script>") : "Encoded XSS payload decoded unsafely: " + payload;
        }
    }

    @Test
    @org.junit.jupiter.api.Order(7)
    @DisplayName("XSS Test - Event Handler Attributes")
    @WithMockJwtUser("test-user-id")
    void testEventHandlerXss() throws Exception {
        for (String payload : EVENT_HANDLER_PAYLOADS) {
            CreateTravelPlanRequest request = CreateTravelPlanRequest.builder()
                .title("Test " + payload)
                .description("Description " + payload)
                .destination("Seoul")
                .startDate(LocalDate.now().plusDays(1))
                .endDate(LocalDate.now().plusDays(5))
                .build();
            
            String requestJson = objectMapper.writeValueAsString(request);
            
            String response = mockMvc.perform(post("/api/v1/travel-plans")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestJson))
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            
            // Verify event handlers are stripped/sanitized
            assertEventHandlersSanitized(response, payload);
        }
    }

    @Test
    @org.junit.jupiter.api.Order(8)
    @DisplayName("XSS Test - CSS-based XSS Attacks")
    @WithMockJwtUser("test-user-id")
    void testCssBasedXss() throws Exception {
        for (String payload : CSS_XSS_PAYLOADS) {
            CreateTravelPlanRequest request = CreateTravelPlanRequest.builder()
                .title("Test " + payload)
                .description("Description " + payload)
                .destination("Seoul")
                .startDate(LocalDate.now().plusDays(1))
                .endDate(LocalDate.now().plusDays(5))
                .build();
            
            String requestJson = objectMapper.writeValueAsString(request);
            
            String response = mockMvc.perform(post("/api/v1/travel-plans")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestJson))
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            
            // Verify CSS-based XSS is prevented
            assertCssXssSanitized(response, payload);
        }
    }

    @Test
    @org.junit.jupiter.api.Order(9)
    @DisplayName("XSS Test - JSON Response Content-Type Validation")
    void testJsonContentTypeHeaders() throws Exception {
        String response = mockMvc.perform(get("/api/v1/travel-plans/public")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("application/json")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        
        // Verify response is proper JSON and not HTML that could execute scripts
        assert response.startsWith("{") || response.startsWith("[") : "Response is not JSON format";
    }

    @Test
    @org.junit.jupiter.api.Order(10)
    @DisplayName("XSS Test - DOM-based XSS via URL Fragment")
    void testDomBasedXssViaFragment() throws Exception {
        String[] domPayloads = {
            "#<script>alert('XSS')</script>",
            "#javascript:alert('XSS')",
            "#<img src=x onerror=alert('XSS')>",
            "#<svg onload=alert('XSS')>"
        };
        
        for (String payload : domPayloads) {
            // Test that fragments aren't processed server-side
            mockMvc.perform(get("/api/v1/travel-plans/public" + payload)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk()); // Should ignore fragment
        }
    }

    @Test
    @org.junit.jupiter.api.Order(11)
    @DisplayName("XSS Test - File Upload Content")
    @WithMockJwtUser("test-user-id")
    void testXssInFileUploadContent() throws Exception {
        String maliciousContent = "<script>alert('XSS')</script>";
        
        mockMvc.perform(multipart("/api/v1/files/upload")
                .file("file", maliciousContent.getBytes())
                .param("fileName", "test<script>alert('XSS')</script>.txt")
                .param("contentType", "text/html<script>alert('XSS')</script>"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fileName", not(containsString("<script>"))));
    }

    @Test
    @org.junit.jupiter.api.Order(12)
    @DisplayName("XSS Test - Error Messages")
    void testXssInErrorMessages() throws Exception {
        String payload = "<script>alert('XSS')</script>";
        
        // Test 404 error with malicious path
        String response = mockMvc.perform(get("/api/v1/travel-plans/" + payload)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andReturn()
                .getResponse()
                .getContentAsString();
        
        // Verify error message doesn't contain unescaped XSS payload
        assertXssSanitized(response, payload);
    }

    @Test
    @org.junit.jupiter.api.Order(13)
    @DisplayName("XSS Test - Stored XSS via Database")
    @WithMockJwtUser("test-user-id")
    void testStoredXssViaDatabase() throws Exception {
        String xssPayload = "<script>alert('Stored XSS')</script>";
        
        // Create travel plan with XSS payload
        CreateTravelPlanRequest createRequest = CreateTravelPlanRequest.builder()
            .title(xssPayload)
            .description(xssPayload)
            .destination("Seoul")
            .startDate(LocalDate.now().plusDays(1))
            .endDate(LocalDate.now().plusDays(5))
            .isPublic(true)
            .build();
        
        String createJson = objectMapper.writeValueAsString(createRequest);
        
        String createResponse = mockMvc.perform(post("/api/v1/travel-plans")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createJson))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        
        // Extract created plan ID
        String planId = objectMapper.readTree(createResponse).get("data").get("id").asText();
        
        // Retrieve the stored data
        String retrieveResponse = mockMvc.perform(get("/api/v1/travel-plans/" + planId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        
        // Verify stored XSS payload is sanitized when retrieved
        assertXssSanitized(retrieveResponse, xssPayload);
    }

    @Test
    @org.junit.jupiter.api.Order(14)
    @DisplayName("XSS Test - MIME Type Validation")
    @WithMockJwtUser("test-user-id")
    void testMimeTypeValidation() throws Exception {
        // Test that responses have proper MIME types and security headers
        mockMvc.perform(get("/api/v1/travel-plans/public")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("application/json")))
                .andExpect(header().exists("X-Content-Type-Options")) // Should be 'nosniff'
                .andExpect(header().exists("X-XSS-Protection")); // Should be enabled
    }

    @Test
    @org.junit.jupiter.api.Order(15)
    @DisplayName("XSS Test - Content Security Policy Headers")
    void testContentSecurityPolicyHeaders() throws Exception {
        mockMvc.perform(get("/api/v1/travel-plans/public")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                // Verify security headers are present
                .andExpect(header().exists("X-Frame-Options"))
                .andExpect(header().exists("X-Content-Type-Options"));
    }

    /**
     * Helper method to assert XSS payloads are properly sanitized
     */
    private void assertXssSanitized(String response, String payload) {
        // Check for common XSS indicators
        String lowerResponse = response.toLowerCase();
        String lowerPayload = payload.toLowerCase();
        
        assert !lowerResponse.contains("<script") : "Script tags not sanitized: " + payload;
        assert !lowerResponse.contains("javascript:") : "JavaScript protocol not sanitized: " + payload;
        assert !lowerResponse.contains("onerror=") : "onerror handler not sanitized: " + payload;
        assert !lowerResponse.contains("onload=") : "onload handler not sanitized: " + payload;
        assert !lowerResponse.contains("onclick=") : "onclick handler not sanitized: " + payload;
        assert !response.equals(payload) : "Payload reflected without sanitization: " + payload;
    }
    
    /**
     * Helper method to assert event handlers are sanitized
     */
    private void assertEventHandlersSanitized(String response, String payload) {
        String lowerResponse = response.toLowerCase();
        
        assert !lowerResponse.contains("onload=") : "onload handler not sanitized: " + payload;
        assert !lowerResponse.contains("onerror=") : "onerror handler not sanitized: " + payload;
        assert !lowerResponse.contains("onclick=") : "onclick handler not sanitized: " + payload;
        assert !lowerResponse.contains("onmouseover=") : "onmouseover handler not sanitized: " + payload;
        assert !lowerResponse.contains("onfocus=") : "onfocus handler not sanitized: " + payload;
    }
    
    /**
     * Helper method to assert CSS-based XSS is sanitized
     */
    private void assertCssXssSanitized(String response, String payload) {
        String lowerResponse = response.toLowerCase();
        
        assert !lowerResponse.contains("javascript:") : "CSS javascript protocol not sanitized: " + payload;
        assert !lowerResponse.contains("expression(") : "CSS expression not sanitized: " + payload;
        assert !lowerResponse.contains("behavior:url") : "CSS behavior not sanitized: " + payload;
        assert !lowerResponse.contains("@import") : "CSS import not sanitized: " + payload;
    }
}