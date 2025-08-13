package com.oddiya.contract.versioning;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oddiya.contract.ContractTestBase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Comprehensive backward compatibility tests
 * Ensures that API changes don't break existing client integrations
 */
@SpringBootTest
@ActiveProfiles("contract-test")
public class BackwardCompatibilityTest extends ContractTestBase {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @Override
    public void setup() {
        super.setup();
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/api/v1/auth/validate",
        "/api/v1/travel-plans/tp123",
        "/api/v1/places/place123",
        "/api/v1/users/user123"
    })
    public void should_maintain_consistent_response_structure_across_endpoints(String endpoint) throws Exception {
        // Test that all endpoints maintain consistent response structure
        
        MvcResult result = mockMvc.perform(get(endpoint)
                .header("Authorization", "Bearer valid-token")
                .accept(MediaType.APPLICATION_JSON))
                .andExpected(status().isOk().or().isBadRequest().or().isNotFound())
                .andReturn();

        String responseContent = result.getResponse().getContentAsString();
        JsonNode responseJson = objectMapper.readTree(responseContent);

        // All responses should have the same top-level structure
        assertThat(responseJson.has("success")).isTrue();
        assertThat(responseJson.has("message")).isTrue();
        assertThat(responseJson.has("data")).isTrue();
        
        // success should always be a boolean
        assertThat(responseJson.get("success").isBoolean()).isTrue();
        
        // message should always be a string
        assertThat(responseJson.get("message").isTextual()).isTrue();
    }

    @Test
    public void should_handle_legacy_request_formats() throws Exception {
        // Test that API can handle legacy request formats for backward compatibility
        
        // Test legacy login request format (if format changed)
        String legacyLoginRequest = """
            {
                "provider": "GOOGLE",
                "idToken": "valid-google-id-token",
                "deviceType": "MOBILE"
            }
            """;

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(legacyLoginRequest))
                .andExpected(status().isOk())
                .andExpected(jsonPath("$.success").value(true))
                .andExpected(jsonPath("$.data.accessToken").exists())
                .andExpected(jsonPath("$.data.refreshToken").exists());
    }

    @Test
    public void should_maintain_pagination_parameters_compatibility() throws Exception {
        // Test that old pagination parameter names still work
        
        List<String> paginatedEndpoints = Arrays.asList(
            "/api/v1/travel-plans/public",
            "/api/v1/places/popular",
            "/api/v1/places/search?query=seoul"
        );

        for (String endpoint : paginatedEndpoints) {
            // Test with current parameter names
            mockMvc.perform(get(endpoint)
                    .param("page", "0")
                    .param("size", "10")
                    .accept(MediaType.APPLICATION_JSON))
                    .andExpected(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpected(jsonPath("$.data.page").exists())
                    .andExpected(jsonPath("$.data.size").exists());

            // Test with legacy parameter names (if any existed)
            // Example: if "limit" was used instead of "size" in older versions
            mockMvc.perform(get(endpoint)
                    .param("page", "0")
                    .param("limit", "10") // Legacy parameter name
                    .accept(MediaType.APPLICATION_JSON))
                    .andExpected(status().isOk())
                    .andExpected(jsonPath("$.success").value(true));
        }
    }

    @Test
    public void should_maintain_authentication_backward_compatibility() throws Exception {
        // Test that different authentication methods still work
        
        String[] authEndpoints = {
            "/api/v1/auth/validate",
            "/api/v1/users/profile",
            "/api/files/upload"
        };

        for (String endpoint : authEndpoints) {
            // Test with Bearer token (current standard)
            mockMvc.perform(get(endpoint)
                    .header("Authorization", "Bearer valid-jwt-token")
                    .accept(MediaType.APPLICATION_JSON))
                    .andExpected(status().isOk().or().isBadRequest())
                    .andExpected(content().contentType(MediaType.APPLICATION_JSON));

            // Test that API key header is still supported (if it was supported before)
            mockMvc.perform(get(endpoint)
                    .header("X-API-Key", "legacy-api-key")
                    .accept(MediaType.APPLICATION_JSON))
                    .andExpected(status().isOk().or().isUnauthorized().or().isForbidden());
        }
    }

    @Test
    public void should_maintain_field_naming_consistency() throws Exception {
        // Test that field names haven't changed from v1
        
        MvcResult result = mockMvc.perform(get("/api/v1/travel-plans/tp123")
                .accept(MediaType.APPLICATION_JSON))
                .andExpected(status().isOk())
                .andReturn();

        String responseContent = result.getResponse().getContentAsString();
        JsonNode responseJson = objectMapper.readTree(responseContent);
        JsonNode data = responseJson.get("data");

        // Essential field names that should never change in v1
        String[] requiredFields = {
            "id", "title", "description", "userId", "userName", "status",
            "startDate", "endDate", "isPublic", "viewCount", "likeCount",
            "tags", "createdAt", "updatedAt"
        };

        for (String field : requiredFields) {
            assertThat(data.has(field))
                .as("Field '%s' should be present for backward compatibility", field)
                .isTrue();
        }
    }

    @Test
    public void should_handle_null_and_missing_values_consistently() throws Exception {
        // Test that null and missing values are handled the same way across versions
        
        MvcResult result = mockMvc.perform(get("/api/v1/places/place123")
                .accept(MediaType.APPLICATION_JSON))
                .andExpected(status().isOk())
                .andReturn();

        String responseContent = result.getResponse().getContentAsString();
        JsonNode responseJson = objectMapper.readTree(responseContent);
        JsonNode place = responseJson.get("data");

        // Optional fields should either be:
        // 1. Present with a value
        // 2. Present with null
        // 3. Completely missing (depending on API design choice)
        // The key is consistency across all endpoints

        if (place.has("description")) {
            // If present, it should be either null or a string
            assertThat(place.get("description").isNull() || place.get("description").isTextual()).isTrue();
        }

        if (place.has("imageUrls")) {
            // If present, it should be either null or an array
            assertThat(place.get("imageUrls").isNull() || place.get("imageUrls").isArray()).isTrue();
        }
    }

    @Test
    public void should_maintain_http_status_code_consistency() throws Exception {
        // Test that HTTP status codes are consistent across similar operations
        
        // Test successful GET operations
        mockMvc.perform(get("/api/v1/travel-plans/tp123"))
                .andExpected(status().isOk());

        mockMvc.perform(get("/api/v1/places/place123"))
                .andExpect(status().isOk());

        // Test successful POST operations (creation)
        String travelPlanRequest = """
            {
                "title": "Test Plan",
                "description": "Test Description",
                "startDate": "2024-06-01T09:00:00",
                "endDate": "2024-06-03T18:00:00",
                "isPublic": true,
                "tags": ["test"]
            }
            """;

        mockMvc.perform(post("/api/v1/travel-plans")
                .header("Authorization", "Bearer valid-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(travelPlanRequest))
                .andExpected(status().isCreated());

        // Test not found responses
        mockMvc.perform(get("/api/v1/travel-plans/nonexistent"))
                .andExpected(status().isNotFound());

        mockMvc.perform(get("/api/v1/places/nonexistent"))
                .andExpected(status().isNotFound());

        // Test unauthorized responses
        mockMvc.perform(get("/api/v1/users/profile"))
                .andExpected(status().isUnauthorized().or().isForbidden());
    }

    @Test
    public void should_maintain_content_type_support() throws Exception {
        // Test that content types are supported consistently
        
        String[] endpoints = {
            "/api/v1/travel-plans/public",
            "/api/v1/places/popular",
            "/api/v1/auth/validate"
        };

        for (String endpoint : endpoints) {
            // Test JSON content type (primary)
            mockMvc.perform(get(endpoint)
                    .header("Authorization", "Bearer valid-token")
                    .accept(MediaType.APPLICATION_JSON))
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON));

            // Test that wildcard accept header works
            mockMvc.perform(get(endpoint)
                    .header("Authorization", "Bearer valid-token")
                    .accept("*/*"))
                    .andExpected(status().isOk());
        }
    }

    @Test
    public void should_maintain_error_code_consistency() throws Exception {
        // Test that error codes and structures are consistent
        
        // Test validation error structure
        String invalidRequest = """
            {
                "title": "",
                "description": null
            }
            """;

        MvcResult result = mockMvc.perform(post("/api/v1/travel-plans")
                .header("Authorization", "Bearer valid-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidRequest))
                .andExpected(status().isBadRequest())
                .andReturn();

        String responseContent = result.getResponse().getContentAsString();
        JsonNode responseJson = objectMapper.readTree(responseContent);

        // Error responses should have consistent structure
        assertThat(responseJson.get("success").booleanValue()).isFalse();
        assertThat(responseJson.has("message")).isTrue();
        assertThat(responseJson.has("error")).isTrue();
        assertThat(responseJson.get("data").isNull()).isTrue();
    }

    @Test
    public void should_support_legacy_sorting_parameters() throws Exception {
        // Test that old sorting parameter formats still work
        
        // Current sorting format
        mockMvc.perform(get("/api/v1/travel-plans/public")
                .param("sortBy", "viewCount")
                .param("sortDirection", "DESC")
                .accept(MediaType.APPLICATION_JSON))
                .andExpected(status().isOk())
                .andExpected(jsonPath("$.success").value(true));

        // Legacy sorting format (if it existed)
        // Example: ?sort=viewCount,desc
        mockMvc.perform(get("/api/v1/travel-plans/public")
                .param("sort", "viewCount,desc")
                .accept(MediaType.APPLICATION_JSON))
                .andExpected(status().isOk())
                .andExpected(jsonPath("$.success").value(true));
    }
}