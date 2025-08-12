package com.oddiya.contract.versioning;

import com.oddiya.contract.ContractTestBase;
import com.oddiya.dto.response.ApiResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Contract tests for API versioning and backward compatibility
 * Ensures that API changes don't break existing consumers
 */
@SpringBootTest
@ActiveProfiles("contract-test")
public class ApiVersioningContractTest extends ContractTestBase {

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

    @Test
    public void should_maintain_v1_api_response_structure() throws Exception {
        // Test that v1 API response structure is maintained for backward compatibility
        
        // Given: A request to v1 API endpoint
        String v1Endpoint = "/api/v1/auth/validate";
        
        // When: Making a request to the endpoint
        MvcResult result = mockMvc.perform(get(v1Endpoint)
                .header("Authorization", "Bearer valid-token")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpected(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        // Then: Response should maintain the expected v1 structure
        String responseContent = result.getResponse().getContentAsString();
        JsonNode responseJson = objectMapper.readTree(responseContent);

        // Validate core v1 API response structure
        assertThat(responseJson.has("success")).isTrue();
        assertThat(responseJson.has("message")).isTrue();
        assertThat(responseJson.has("data")).isTrue();
        
        // Optional fields should be present if they were in v1
        if (responseJson.has("error")) {
            assertThat(responseJson.get("error")).isNotNull();
        }
        
        // Validate that success is a boolean
        assertThat(responseJson.get("success").isBoolean()).isTrue();
        
        // Validate that message is a string
        assertThat(responseJson.get("message").isTextual()).isTrue();
    }

    @Test
    public void should_support_content_type_negotiation() throws Exception {
        // Test that API supports content type negotiation for different versions
        
        // Test JSON response (default)
        mockMvc.perform(get("/api/v1/places/popular")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpected(content().contentType(MediaType.APPLICATION_JSON))
                .andExpected(jsonPath("$.success").exists())
                .andExpected(jsonPath("$.data").exists());

        // Test that API version can be specified via accept header
        mockMvc.perform(get("/api/v1/places/popular")
                .accept("application/vnd.oddiya.v1+json"))
                .andExpected(status().isOk())
                .andExpected(content().contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    public void should_maintain_field_compatibility_across_versions() throws Exception {
        // Test that existing fields are not removed or changed in type
        
        MvcResult result = mockMvc.perform(get("/api/v1/travel-plans/tp123")
                .accept(MediaType.APPLICATION_JSON))
                .andExpected(status().isOk())
                .andReturn();

        String responseContent = result.getResponse().getContentAsString();
        JsonNode responseJson = objectMapper.readTree(responseContent);
        JsonNode travelPlan = responseJson.get("data");

        // Validate that essential v1 fields are present and have correct types
        assertThat(travelPlan.has("id")).isTrue();
        assertThat(travelPlan.get("id").isTextual()).isTrue();
        
        assertThat(travelPlan.has("title")).isTrue();
        assertThat(travelPlan.get("title").isTextual()).isTrue();
        
        assertThat(travelPlan.has("description")).isTrue();
        assertThat(travelPlan.get("description").isTextual()).isTrue();
        
        assertThat(travelPlan.has("userId")).isTrue();
        assertThat(travelPlan.get("userId").isTextual()).isTrue();
        
        assertThat(travelPlan.has("status")).isTrue();
        assertThat(travelPlan.get("status").isTextual()).isTrue();
        
        assertThat(travelPlan.has("isPublic")).isTrue();
        assertThat(travelPlan.get("isPublic").isBoolean()).isTrue();
        
        assertThat(travelPlan.has("viewCount")).isTrue();
        assertThat(travelPlan.get("viewCount").isNumber()).isTrue();
        
        assertThat(travelPlan.has("likeCount")).isTrue();
        assertThat(travelPlan.get("likeCount").isNumber()).isTrue();
        
        assertThat(travelPlan.has("tags")).isTrue();
        assertThat(travelPlan.get("tags").isArray()).isTrue();
        
        assertThat(travelPlan.has("createdAt")).isTrue();
        assertThat(travelPlan.get("createdAt").isTextual()).isTrue();
        
        assertThat(travelPlan.has("updatedAt")).isTrue();
        assertThat(travelPlan.get("updatedAt").isTextual()).isTrue();
    }

    @Test
    public void should_handle_optional_fields_gracefully() throws Exception {
        // Test that optional fields can be missing without breaking consumers
        
        MvcResult result = mockMvc.perform(get("/api/v1/places/place123")
                .accept(MediaType.APPLICATION_JSON))
                .andExpected(status().isOk())
                .andReturn();

        String responseContent = result.getResponse().getContentAsString();
        JsonNode responseJson = objectMapper.readTree(responseContent);
        JsonNode place = responseJson.get("data");

        // Required fields must always be present
        assertThat(place.has("id")).isTrue();
        assertThat(place.has("name")).isTrue();
        assertThat(place.has("category")).isTrue();
        assertThat(place.has("latitude")).isTrue();
        assertThat(place.has("longitude")).isTrue();

        // Optional fields should be handled gracefully (can be null or missing)
        if (place.has("description")) {
            if (!place.get("description").isNull()) {
                assertThat(place.get("description").isTextual()).isTrue();
            }
        }

        if (place.has("imageUrls")) {
            if (!place.get("imageUrls").isNull()) {
                assertThat(place.get("imageUrls").isArray()).isTrue();
            }
        }

        if (place.has("tags")) {
            if (!place.get("tags").isNull()) {
                assertThat(place.get("tags").isArray()).isTrue();
            }
        }
    }

    @Test
    public void should_maintain_error_response_structure() throws Exception {
        // Test that error responses maintain consistent structure across versions
        
        MvcResult result = mockMvc.perform(get("/api/v1/travel-plans/nonexistent")
                .accept(MediaType.APPLICATION_JSON))
                .andExpected(status().isNotFound())
                .andReturn();

        String responseContent = result.getResponse().getContentAsString();
        JsonNode responseJson = objectMapper.readTree(responseContent);

        // Validate error response structure
        assertThat(responseJson.has("success")).isTrue();
        assertThat(responseJson.get("success").booleanValue()).isFalse();
        
        assertThat(responseJson.has("message")).isTrue();
        assertThat(responseJson.get("message").isTextual()).isTrue();
        
        assertThat(responseJson.has("error")).isTrue();
        assertThat(responseJson.get("error").isTextual()).isTrue();
        
        assertThat(responseJson.has("data")).isTrue();
        assertThat(responseJson.get("data").isNull()).isTrue();
    }

    @Test
    public void should_support_pagination_contract() throws Exception {
        // Test that pagination structure is consistent across paginated endpoints
        
        MvcResult result = mockMvc.perform(get("/api/v1/travel-plans/public")
                .param("page", "0")
                .param("size", "20")
                .accept(MediaType.APPLICATION_JSON))
                .andExpected(status().isOk())
                .andReturn();

        String responseContent = result.getResponse().getContentAsString();
        JsonNode responseJson = objectMapper.readTree(responseContent);
        JsonNode pageData = responseJson.get("data");

        // Validate pagination structure
        assertThat(pageData.has("content")).isTrue();
        assertThat(pageData.get("content").isArray()).isTrue();
        
        assertThat(pageData.has("page")).isTrue();
        assertThat(pageData.get("page").isNumber()).isTrue();
        
        assertThat(pageData.has("size")).isTrue();
        assertThat(pageData.get("size").isNumber()).isTrue();
        
        assertThat(pageData.has("totalElements")).isTrue();
        assertThat(pageData.get("totalElements").isNumber()).isTrue();
        
        assertThat(pageData.has("totalPages")).isTrue();
        assertThat(pageData.get("totalPages").isNumber()).isTrue();
        
        assertThat(pageData.has("first")).isTrue();
        assertThat(pageData.get("first").isBoolean()).isTrue();
        
        assertThat(pageData.has("last")).isTrue();
        assertThat(pageData.get("last").isBoolean()).isTrue();
    }

    @Test
    public void should_maintain_authentication_header_compatibility() throws Exception {
        // Test that authentication headers work consistently across versions
        
        // Test with Bearer token (current standard)
        mockMvc.perform(get("/api/v1/auth/validate")
                .header("Authorization", "Bearer valid-jwt-token")
                .accept(MediaType.APPLICATION_JSON))
                .andExpected(status().isOk())
                .andExpected(jsonPath("$.success").value(true));

        // Test without token (should handle gracefully)
        mockMvc.perform(get("/api/v1/auth/validate")
                .accept(MediaType.APPLICATION_JSON))
                .andExpected(status().isUnauthorized().or().isBadRequest());
    }

    @Test
    public void should_maintain_date_format_consistency() throws Exception {
        // Test that date formats are consistent across API versions
        
        MvcResult result = mockMvc.perform(get("/api/v1/travel-plans/tp123")
                .accept(MediaType.APPLICATION_JSON))
                .andExpected(status().isOk())
                .andReturn();

        String responseContent = result.getResponse().getContentAsString();
        JsonNode responseJson = objectMapper.readTree(responseContent);
        JsonNode travelPlan = responseJson.get("data");

        // Validate ISO 8601 date format with timezone
        String createdAt = travelPlan.get("createdAt").textValue();
        String updatedAt = travelPlan.get("updatedAt").textValue();
        String startDate = travelPlan.get("startDate").textValue();
        String endDate = travelPlan.get("endDate").textValue();

        // All dates should follow ISO 8601 format
        assertThat(createdAt).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{3})?Z?([+-]\\d{2}:\\d{2})?");
        assertThat(updatedAt).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{3})?Z?([+-]\\d{2}:\\d{2})?");
        assertThat(startDate).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{3})?Z?([+-]\\d{2}:\\d{2})?");
        assertThat(endDate).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{3})?Z?([+-]\\d{2}:\\d{2})?");
    }

    @Test
    public void should_support_backward_compatible_query_parameters() throws Exception {
        // Test that query parameters work across versions and are backward compatible
        
        // Test with old parameter names (if any were changed)
        mockMvc.perform(get("/api/v1/travel-plans/search")
                .param("query", "seoul")
                .param("page", "0")
                .param("size", "10")
                .accept(MediaType.APPLICATION_JSON))
                .andExpected(status().isOk())
                .andExpected(jsonPath("$.success").value(true))
                .andExpected(jsonPath("$.data.page").value(0))
                .andExpected(jsonPath("$.data.size").value(10));

        // Test with sorting parameters
        mockMvc.perform(get("/api/v1/travel-plans/public")
                .param("sortBy", "viewCount")
                .param("sortDirection", "DESC")
                .accept(MediaType.APPLICATION_JSON))
                .andExpected(status().isOk())
                .andExpected(jsonPath("$.success").value(true));
    }
}