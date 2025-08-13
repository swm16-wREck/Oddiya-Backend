package com.oddiya.dto.response;

import com.oddiya.utils.DTOValidationTestUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ApiResponse Generic Response Wrapper Tests")
class ApiResponseTest {

    private final ObjectMapper objectMapper;

    public ApiResponseTest() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @Nested
    @DisplayName("Success Response Tests")
    class SuccessResponseTests {

        @Test
        @DisplayName("Success response with string data should serialize correctly")
        void successResponseWithStringData() throws JsonProcessingException {
            ApiResponse<String> response = ApiResponse.success("Hello World");
            
            assertTrue(response.isSuccess());
            assertEquals("Hello World", response.getData());
            assertNotNull(response.getMeta());
            assertNull(response.getError());
            
            String json = objectMapper.writeValueAsString(response);
            ApiResponse<String> deserialized = objectMapper.readValue(json, 
                new TypeReference<ApiResponse<String>>() {});
            
            assertTrue(deserialized.isSuccess());
            assertEquals("Hello World", deserialized.getData());
            assertNotNull(deserialized.getMeta());
        }

        @Test
        @DisplayName("Success response with complex object should serialize correctly")
        void successResponseWithComplexObject() throws JsonProcessingException {
            UserProfileResponse userData = UserProfileResponse.builder()
                .id("user-123")
                .email("test@example.com")
                .name("Test User")
                .bio("Test bio")
                .isPublic(true)
                .followersCount(100L)
                .createdAt(LocalDateTime.now())
                .build();
            
            ApiResponse<UserProfileResponse> response = ApiResponse.success(userData);
            
            assertTrue(response.isSuccess());
            assertEquals(userData, response.getData());
            assertNotNull(response.getMeta());
            assertNull(response.getError());
            
            String json = objectMapper.writeValueAsString(response);
            ApiResponse<UserProfileResponse> deserialized = objectMapper.readValue(json, 
                new TypeReference<ApiResponse<UserProfileResponse>>() {});
            
            assertTrue(deserialized.isSuccess());
            assertNotNull(deserialized.getData());
            assertEquals("user-123", deserialized.getData().getId());
            assertEquals("test@example.com", deserialized.getData().getEmail());
        }

        @Test
        @DisplayName("Success response with list should serialize correctly")
        void successResponseWithList() throws JsonProcessingException {
            List<String> listData = Arrays.asList("item1", "item2", "item3");
            ApiResponse<List<String>> response = ApiResponse.success(listData);
            
            assertTrue(response.isSuccess());
            assertEquals(listData, response.getData());
            
            String json = objectMapper.writeValueAsString(response);
            ApiResponse<List<String>> deserialized = objectMapper.readValue(json, 
                new TypeReference<ApiResponse<List<String>>>() {});
            
            assertTrue(deserialized.isSuccess());
            assertNotNull(deserialized.getData());
            assertEquals(3, deserialized.getData().size());
            assertTrue(deserialized.getData().contains("item1"));
        }

        @Test
        @DisplayName("Success response with null data should serialize correctly")
        void successResponseWithNullData() throws JsonProcessingException {
            ApiResponse<String> response = ApiResponse.success(null);
            
            assertTrue(response.isSuccess());
            assertNull(response.getData());
            assertNotNull(response.getMeta());
            assertNull(response.getError());
            
            String json = objectMapper.writeValueAsString(response);
            ApiResponse<String> deserialized = objectMapper.readValue(json, 
                new TypeReference<ApiResponse<String>>() {});
            
            assertTrue(deserialized.isSuccess());
            assertNull(deserialized.getData());
        }
    }

    @Nested
    @DisplayName("Error Response Tests")
    class ErrorResponseTests {

        @Test
        @DisplayName("Error response with code and message should serialize correctly")
        void errorResponseWithCodeAndMessage() throws JsonProcessingException {
            ApiResponse<String> response = ApiResponse.error("USER_NOT_FOUND", "User not found");
            
            assertFalse(response.isSuccess());
            assertNull(response.getData());
            assertNull(response.getMeta());
            assertNotNull(response.getError());
            assertEquals("USER_NOT_FOUND", response.getError().getCode());
            assertEquals("User not found", response.getError().getMessage());
            assertNotNull(response.getError().getTimestamp());
            
            String json = objectMapper.writeValueAsString(response);
            ApiResponse<String> deserialized = objectMapper.readValue(json, 
                new TypeReference<ApiResponse<String>>() {});
            
            assertFalse(deserialized.isSuccess());
            assertNotNull(deserialized.getError());
            assertEquals("USER_NOT_FOUND", deserialized.getError().getCode());
            assertEquals("User not found", deserialized.getError().getMessage());
        }

        @Test
        @DisplayName("Error response with ErrorDetail object should serialize correctly")
        void errorResponseWithErrorDetail() throws JsonProcessingException {
            ApiResponse.ErrorDetail errorDetail = ApiResponse.ErrorDetail.builder()
                .code("VALIDATION_ERROR")
                .message("Invalid input data")
                .details(Map.of("field", "email", "value", "invalid-email"))
                .timestamp(LocalDateTime.now())
                .build();
            
            ApiResponse<String> response = ApiResponse.error(errorDetail);
            
            assertFalse(response.isSuccess());
            assertNull(response.getData());
            assertNull(response.getMeta());
            assertNotNull(response.getError());
            assertEquals("VALIDATION_ERROR", response.getError().getCode());
            assertEquals("Invalid input data", response.getError().getMessage());
            assertNotNull(response.getError().getDetails());
            
            String json = objectMapper.writeValueAsString(response);
            ApiResponse<String> deserialized = objectMapper.readValue(json, 
                new TypeReference<ApiResponse<String>>() {});
            
            assertFalse(deserialized.isSuccess());
            assertNotNull(deserialized.getError());
            assertEquals("VALIDATION_ERROR", deserialized.getError().getCode());
        }

        @Test
        @DisplayName("Error response with complex details should serialize correctly")
        void errorResponseWithComplexDetails() throws JsonProcessingException {
            Map<String, Object> complexDetails = Map.of(
                "validationErrors", Arrays.asList(
                    Map.of("field", "email", "message", "Invalid email format"),
                    Map.of("field", "password", "message", "Password too short")
                ),
                "requestId", "req-123456",
                "timestamp", System.currentTimeMillis()
            );
            
            ApiResponse.ErrorDetail errorDetail = ApiResponse.ErrorDetail.builder()
                .code("VALIDATION_FAILED")
                .message("Multiple validation errors")
                .details(complexDetails)
                .timestamp(LocalDateTime.now())
                .build();
            
            ApiResponse<Object> response = ApiResponse.error(errorDetail);
            
            String json = objectMapper.writeValueAsString(response);
            ApiResponse<Object> deserialized = objectMapper.readValue(json, 
                new TypeReference<ApiResponse<Object>>() {});
            
            assertFalse(deserialized.isSuccess());
            assertNotNull(deserialized.getError());
            assertEquals("VALIDATION_FAILED", deserialized.getError().getCode());
            assertNotNull(deserialized.getError().getDetails());
        }
    }

    @Nested
    @DisplayName("Meta Data Tests")
    class MetaDataTests {

        @Test
        @DisplayName("ResponseMeta should serialize correctly")
        void responseMetaSerialization() throws JsonProcessingException {
            ApiResponse.ResponseMeta meta = ApiResponse.ResponseMeta.builder()
                .timestamp(LocalDateTime.of(2024, 1, 15, 10, 30, 0))
                .version("2.0.0")
                .build();
            
            String json = objectMapper.writeValueAsString(meta);
            ApiResponse.ResponseMeta deserialized = objectMapper.readValue(json, ApiResponse.ResponseMeta.class);
            
            assertEquals(LocalDateTime.of(2024, 1, 15, 10, 30, 0), deserialized.getTimestamp());
            assertEquals("2.0.0", deserialized.getVersion());
        }

        @Test
        @DisplayName("Success response should include meta with timestamp and version")
        void successResponseMetaData() {
            ApiResponse<String> response = ApiResponse.success("test data");
            
            assertNotNull(response.getMeta());
            assertNotNull(response.getMeta().getTimestamp());
            assertEquals("1.0.0", response.getMeta().getVersion());
        }

        @Test
        @DisplayName("ResponseMeta with null values should serialize correctly")
        void responseMetaWithNullValues() throws JsonProcessingException {
            ApiResponse.ResponseMeta meta = ApiResponse.ResponseMeta.builder()
                .timestamp(null)
                .version(null)
                .build();
            
            DTOValidationTestUtils.testSerialization(meta, ApiResponse.ResponseMeta.class);
        }
    }

    @Nested
    @DisplayName("Error Detail Tests")
    class ErrorDetailTests {

        @Test
        @DisplayName("ErrorDetail should serialize correctly")
        void errorDetailSerialization() throws JsonProcessingException {
            ApiResponse.ErrorDetail errorDetail = ApiResponse.ErrorDetail.builder()
                .code("TEST_ERROR")
                .message("Test error message")
                .details("Additional details")
                .timestamp(LocalDateTime.of(2024, 1, 15, 10, 30, 0))
                .build();
            
            String json = objectMapper.writeValueAsString(errorDetail);
            ApiResponse.ErrorDetail deserialized = objectMapper.readValue(json, ApiResponse.ErrorDetail.class);
            
            assertEquals("TEST_ERROR", deserialized.getCode());
            assertEquals("Test error message", deserialized.getMessage());
            assertEquals("Additional details", deserialized.getDetails());
            assertEquals(LocalDateTime.of(2024, 1, 15, 10, 30, 0), deserialized.getTimestamp());
        }

        @Test
        @DisplayName("ErrorDetail with null details should serialize correctly")
        void errorDetailWithNullDetails() throws JsonProcessingException {
            ApiResponse.ErrorDetail errorDetail = ApiResponse.ErrorDetail.builder()
                .code("SIMPLE_ERROR")
                .message("Simple error")
                .details(null)
                .timestamp(LocalDateTime.now())
                .build();
            
            DTOValidationTestUtils.testSerialization(errorDetail, ApiResponse.ErrorDetail.class);
        }

        @Test
        @DisplayName("ErrorDetail with various detail types should serialize correctly")
        void errorDetailWithVariousDetailTypes() throws JsonProcessingException {
            // Test with String details
            ApiResponse.ErrorDetail stringDetails = ApiResponse.ErrorDetail.builder()
                .code("STRING_DETAILS")
                .message("Error with string details")
                .details("Simple string details")
                .timestamp(LocalDateTime.now())
                .build();
            
            DTOValidationTestUtils.testSerialization(stringDetails, ApiResponse.ErrorDetail.class);
            
            // Test with Map details
            ApiResponse.ErrorDetail mapDetails = ApiResponse.ErrorDetail.builder()
                .code("MAP_DETAILS")
                .message("Error with map details")
                .details(Map.of("key1", "value1", "key2", 42))
                .timestamp(LocalDateTime.now())
                .build();
            
            DTOValidationTestUtils.testSerialization(mapDetails, ApiResponse.ErrorDetail.class);
            
            // Test with List details
            ApiResponse.ErrorDetail listDetails = ApiResponse.ErrorDetail.builder()
                .code("LIST_DETAILS")
                .message("Error with list details")
                .details(Arrays.asList("error1", "error2", "error3"))
                .timestamp(LocalDateTime.now())
                .build();
            
            DTOValidationTestUtils.testSerialization(listDetails, ApiResponse.ErrorDetail.class);
        }
    }

    @Nested
    @DisplayName("Generic Type Tests")
    class GenericTypeTests {

        @Test
        @DisplayName("ApiResponse with Integer data should serialize correctly")
        void apiResponseWithIntegerData() throws JsonProcessingException {
            ApiResponse<Integer> response = ApiResponse.success(42);
            
            assertEquals(Integer.valueOf(42), response.getData());
            
            String json = objectMapper.writeValueAsString(response);
            ApiResponse<Integer> deserialized = objectMapper.readValue(json, 
                new TypeReference<ApiResponse<Integer>>() {});
            
            assertEquals(Integer.valueOf(42), deserialized.getData());
        }

        @Test
        @DisplayName("ApiResponse with Boolean data should serialize correctly")
        void apiResponseWithBooleanData() throws JsonProcessingException {
            ApiResponse<Boolean> response = ApiResponse.success(true);
            
            assertEquals(Boolean.TRUE, response.getData());
            
            String json = objectMapper.writeValueAsString(response);
            ApiResponse<Boolean> deserialized = objectMapper.readValue(json, 
                new TypeReference<ApiResponse<Boolean>>() {});
            
            assertEquals(Boolean.TRUE, deserialized.getData());
        }

        @Test
        @DisplayName("ApiResponse with Map data should serialize correctly")
        void apiResponseWithMapData() throws JsonProcessingException {
            Map<String, Object> mapData = Map.of(
                "name", "Test",
                "count", 100,
                "active", true
            );
            
            ApiResponse<Map<String, Object>> response = ApiResponse.success(mapData);
            
            assertEquals(mapData, response.getData());
            
            String json = objectMapper.writeValueAsString(response);
            ApiResponse<Map<String, Object>> deserialized = objectMapper.readValue(json, 
                new TypeReference<ApiResponse<Map<String, Object>>>() {});
            
            assertNotNull(deserialized.getData());
            assertEquals("Test", deserialized.getData().get("name"));
            assertEquals(100, deserialized.getData().get("count"));
            assertEquals(true, deserialized.getData().get("active"));
        }
    }

    @Nested
    @DisplayName("JsonInclude Annotation Tests")
    class JsonIncludeTests {

        @Test
        @DisplayName("Null fields should be excluded from JSON due to @JsonInclude")
        void nullFieldsExclusionTest() throws JsonProcessingException {
            ApiResponse<String> response = ApiResponse.<String>builder()
                .success(true)
                .data("test data")
                .meta(null)  // Should be excluded
                .error(null) // Should be excluded
                .build();
            
            String json = objectMapper.writeValueAsString(response);
            
            // These fields should not appear in JSON due to @JsonInclude(NON_NULL)
            assertFalse(json.contains("\"meta\":"));
            assertFalse(json.contains("\"error\":"));
            
            // These fields should appear
            assertTrue(json.contains("\"success\":true"));
            assertTrue(json.contains("\"data\":\"test data\""));
        }

        @Test
        @DisplayName("ErrorDetail null fields should be excluded from JSON")
        void errorDetailNullFieldsExclusion() throws JsonProcessingException {
            ApiResponse.ErrorDetail errorDetail = ApiResponse.ErrorDetail.builder()
                .code("ERROR_CODE")
                .message("Error message")
                .details(null)    // Should be excluded
                .timestamp(LocalDateTime.now())
                .build();
            
            String json = objectMapper.writeValueAsString(errorDetail);
            
            // details field should not appear due to @JsonInclude(NON_NULL)
            assertFalse(json.contains("\"details\":"));
            
            // Other fields should appear
            assertTrue(json.contains("\"code\":\"ERROR_CODE\""));
            assertTrue(json.contains("\"message\":\"Error message\""));
            assertTrue(json.contains("\"timestamp\":"));
        }
    }

    @Nested
    @DisplayName("Builder Pattern Tests")
    class BuilderPatternTests {

        @Test
        @DisplayName("ApiResponse builder should create valid instances")
        void apiResponseBuilder() {
            ApiResponse<String> response = ApiResponse.<String>builder()
                .success(true)
                .data("test data")
                .meta(ApiResponse.ResponseMeta.builder()
                    .timestamp(LocalDateTime.now())
                    .version("1.0")
                    .build())
                .error(null)
                .build();
            
            assertTrue(response.isSuccess());
            assertEquals("test data", response.getData());
            assertNotNull(response.getMeta());
            assertNull(response.getError());
        }

        @Test
        @DisplayName("ResponseMeta builder should work correctly")
        void responseMetaBuilder() {
            LocalDateTime now = LocalDateTime.now();
            ApiResponse.ResponseMeta meta = ApiResponse.ResponseMeta.builder()
                .timestamp(now)
                .version("2.0.0")
                .build();
            
            assertEquals(now, meta.getTimestamp());
            assertEquals("2.0.0", meta.getVersion());
        }

        @Test
        @DisplayName("ErrorDetail builder should work correctly")
        void errorDetailBuilder() {
            LocalDateTime now = LocalDateTime.now();
            Map<String, String> details = Map.of("field", "email");
            
            ApiResponse.ErrorDetail errorDetail = ApiResponse.ErrorDetail.builder()
                .code("VALIDATION_ERROR")
                .message("Validation failed")
                .details(details)
                .timestamp(now)
                .build();
            
            assertEquals("VALIDATION_ERROR", errorDetail.getCode());
            assertEquals("Validation failed", errorDetail.getMessage());
            assertEquals(details, errorDetail.getDetails());
            assertEquals(now, errorDetail.getTimestamp());
        }
    }

    @Nested
    @DisplayName("Static Factory Methods Tests")
    class StaticFactoryMethodsTests {

        @Test
        @DisplayName("success() static method should create proper response")
        void successStaticMethod() {
            String testData = "success data";
            ApiResponse<String> response = ApiResponse.success(testData);
            
            assertTrue(response.isSuccess());
            assertEquals(testData, response.getData());
            assertNotNull(response.getMeta());
            assertNotNull(response.getMeta().getTimestamp());
            assertEquals("1.0.0", response.getMeta().getVersion());
            assertNull(response.getError());
        }

        @Test
        @DisplayName("error(code, message) static method should create proper response")
        void errorStaticMethodWithCodeAndMessage() {
            ApiResponse<Object> response = ApiResponse.error("NOT_FOUND", "Resource not found");
            
            assertFalse(response.isSuccess());
            assertNull(response.getData());
            assertNull(response.getMeta());
            assertNotNull(response.getError());
            assertEquals("NOT_FOUND", response.getError().getCode());
            assertEquals("Resource not found", response.getError().getMessage());
            assertNotNull(response.getError().getTimestamp());
        }

        @Test
        @DisplayName("error(ErrorDetail) static method should create proper response")
        void errorStaticMethodWithErrorDetail() {
            ApiResponse.ErrorDetail errorDetail = ApiResponse.ErrorDetail.builder()
                .code("CUSTOM_ERROR")
                .message("Custom error message")
                .details("Additional details")
                .timestamp(LocalDateTime.now())
                .build();
            
            ApiResponse<String> response = ApiResponse.error(errorDetail);
            
            assertFalse(response.isSuccess());
            assertNull(response.getData());
            assertNull(response.getMeta());
            assertEquals(errorDetail, response.getError());
        }
    }

    @Nested
    @DisplayName("Edge Cases Tests")
    class EdgeCasesTests {

        @Test
        @DisplayName("Very large data should serialize correctly")
        void veryLargeDataSerialization() throws JsonProcessingException {
            String largeString = "A".repeat(10000);
            ApiResponse<String> response = ApiResponse.success(largeString);
            
            String json = objectMapper.writeValueAsString(response);
            ApiResponse<String> deserialized = objectMapper.readValue(json, 
                new TypeReference<ApiResponse<String>>() {});
            
            assertEquals(largeString, deserialized.getData());
        }

        @Test
        @DisplayName("Complex nested data should serialize correctly")
        void complexNestedDataSerialization() throws JsonProcessingException {
            Map<String, Object> complexData = Map.of(
                "users", Arrays.asList(
                    Map.of("id", 1, "name", "User1", "active", true),
                    Map.of("id", 2, "name", "User2", "active", false)
                ),
                "metadata", Map.of(
                    "total", 2,
                    "page", 1,
                    "timestamp", System.currentTimeMillis()
                ),
                "settings", Map.of(
                    "theme", "dark",
                    "notifications", true,
                    "features", Arrays.asList("feature1", "feature2")
                )
            );
            
            ApiResponse<Map<String, Object>> response = ApiResponse.success(complexData);
            
            String json = objectMapper.writeValueAsString(response);
            ApiResponse<Map<String, Object>> deserialized = objectMapper.readValue(json, 
                new TypeReference<ApiResponse<Map<String, Object>>>() {});
            
            assertNotNull(deserialized.getData());
            assertTrue(deserialized.getData().containsKey("users"));
            assertTrue(deserialized.getData().containsKey("metadata"));
            assertTrue(deserialized.getData().containsKey("settings"));
        }

        @Test
        @DisplayName("Special characters in error messages should serialize correctly")
        void specialCharactersInErrorMessages() throws JsonProcessingException {
            String specialMessage = "Error with special characters: áéíóú, 中文, emojis 🚨⚠️, \"quotes\", and 'apostrophes'";
            ApiResponse<String> response = ApiResponse.error("SPECIAL_CHARS", specialMessage);
            
            String json = objectMapper.writeValueAsString(response);
            ApiResponse<String> deserialized = objectMapper.readValue(json, 
                new TypeReference<ApiResponse<String>>() {});
            
            assertEquals(specialMessage, deserialized.getError().getMessage());
        }
    }

    @Nested
    @DisplayName("Comprehensive Tests")
    class ComprehensiveTests {

        @Test
        @DisplayName("ApiResponse should pass comprehensive validation tests")
        void comprehensiveApiResponseValidation() {
            ApiResponse<String> response = ApiResponse.success("test data");
            DTOValidationTestUtils.runComprehensiveValidationTests(response, ApiResponse.class);
        }

        @Test
        @DisplayName("ResponseMeta should pass comprehensive validation tests")
        void comprehensiveResponseMetaValidation() {
            ApiResponse.ResponseMeta meta = ApiResponse.ResponseMeta.builder()
                .timestamp(LocalDateTime.now())
                .version("1.0.0")
                .build();
            
            DTOValidationTestUtils.runComprehensiveValidationTests(meta, ApiResponse.ResponseMeta.class);
        }

        @Test
        @DisplayName("ErrorDetail should pass comprehensive validation tests")
        void comprehensiveErrorDetailValidation() {
            ApiResponse.ErrorDetail errorDetail = ApiResponse.ErrorDetail.builder()
                .code("TEST_ERROR")
                .message("Test error message")
                .details("Test details")
                .timestamp(LocalDateTime.now())
                .build();
            
            DTOValidationTestUtils.runComprehensiveValidationTests(errorDetail, ApiResponse.ErrorDetail.class);
        }
    }
}