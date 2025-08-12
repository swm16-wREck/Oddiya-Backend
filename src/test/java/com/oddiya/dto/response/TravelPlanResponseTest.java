package com.oddiya.dto.response;

import com.oddiya.utils.DTOValidationTestUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TravelPlanResponse Serialization Tests")
class TravelPlanResponseTest {

    private final ObjectMapper objectMapper;

    public TravelPlanResponseTest() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    private TravelPlanResponse createValidResponse() {
        return TravelPlanResponse.builder()
            .id("tp-123")
            .userId("user-456")
            .userName("John Doe")
            .userProfilePicture("https://example.com/profile.jpg")
            .title("Amazing Tokyo Adventure")
            .description("A wonderful journey through Tokyo exploring traditional and modern culture")
            .destination("Tokyo, Japan")
            .startDate(LocalDate.of(2024, 6, 15))
            .endDate(LocalDate.of(2024, 6, 22))
            .status("ACTIVE")
            .isPublic(true)
            .aiGenerated(false)
            .imageUrl("https://example.com/tokyo.jpg")
            .tags(Arrays.asList("culture", "food", "city"))
            .viewCount(150L)
            .saveCount(25L)
            .itineraryItems(createSampleItineraryItems())
            .collaboratorIds(Arrays.asList("user-789", "user-012"))
            .createdAt(LocalDateTime.of(2024, 1, 15, 10, 30, 0))
            .updatedAt(LocalDateTime.of(2024, 1, 20, 14, 45, 30))
            .build();
    }

    private List<ItineraryItemResponse> createSampleItineraryItems() {
        return Arrays.asList(
            ItineraryItemResponse.builder()
                .id("item-1")
                .dayNumber(1)
                .order(1)
                .startTime(LocalDateTime.of(2024, 6, 15, 9, 0))
                .endTime(LocalDateTime.of(2024, 6, 15, 11, 0))
                .notes("Early morning visit to avoid crowds")
                .transportMode("WALKING")
                .transportDuration(15)
                .build(),
            ItineraryItemResponse.builder()
                .id("item-2")
                .dayNumber(1)
                .order(2)
                .startTime(LocalDateTime.of(2024, 6, 15, 11, 30))
                .endTime(LocalDateTime.of(2024, 6, 15, 13, 30))
                .notes("Fresh sushi lunch")
                .transportMode("SUBWAY")
                .transportDuration(20)
                .build()
        );
    }

    @Nested
    @DisplayName("Basic Serialization Tests")
    class BasicSerializationTests {

        @Test
        @DisplayName("Complete response should serialize and deserialize correctly")
        void completeResponseSerialization() {
            TravelPlanResponse response = createValidResponse();
            DTOValidationTestUtils.testSerialization(response, TravelPlanResponse.class);
        }

        @Test
        @DisplayName("Response with null optional fields should serialize correctly")
        void responseWithNullFields() {
            TravelPlanResponse response = TravelPlanResponse.builder()
                .id("tp-123")
                .userId("user-456")
                .title("Minimal Travel Plan")
                .destination("Tokyo")
                .startDate(LocalDate.of(2024, 6, 15))
                .endDate(LocalDate.of(2024, 6, 22))
                .status("ACTIVE")
                .isPublic(false)
                .aiGenerated(false)
                .viewCount(0L)
                .saveCount(0L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                // All other fields are null
                .build();
            
            DTOValidationTestUtils.testSerialization(response, TravelPlanResponse.class);
        }

        @Test
        @DisplayName("Response with empty collections should serialize correctly")
        void responseWithEmptyCollections() {
            TravelPlanResponse response = createValidResponse();
            response.setTags(Arrays.asList());
            response.setItineraryItems(Arrays.asList());
            response.setCollaboratorIds(Arrays.asList());
            
            DTOValidationTestUtils.testSerialization(response, TravelPlanResponse.class);
        }
    }

    @Nested
    @DisplayName("Date and Time Serialization Tests")
    class DateTimeSerializationTests {

        @Test
        @DisplayName("LocalDate fields should serialize to ISO format")
        void localDateSerialization() throws JsonProcessingException {
            TravelPlanResponse response = createValidResponse();
            response.setStartDate(LocalDate.of(2024, 6, 15));
            response.setEndDate(LocalDate.of(2024, 6, 22));
            
            String json = objectMapper.writeValueAsString(response);
            
            assertTrue(json.contains("\"startDate\":\"2024-06-15\""));
            assertTrue(json.contains("\"endDate\":\"2024-06-22\""));
            
            TravelPlanResponse deserialized = objectMapper.readValue(json, TravelPlanResponse.class);
            assertEquals(LocalDate.of(2024, 6, 15), deserialized.getStartDate());
            assertEquals(LocalDate.of(2024, 6, 22), deserialized.getEndDate());
        }

        @Test
        @DisplayName("LocalDateTime fields should serialize to ISO format")
        void localDateTimeSerialization() throws JsonProcessingException {
            LocalDateTime createdAt = LocalDateTime.of(2024, 1, 15, 10, 30, 45);
            LocalDateTime updatedAt = LocalDateTime.of(2024, 1, 20, 14, 45, 30);
            
            TravelPlanResponse response = createValidResponse();
            response.setCreatedAt(createdAt);
            response.setUpdatedAt(updatedAt);
            
            String json = objectMapper.writeValueAsString(response);
            TravelPlanResponse deserialized = objectMapper.readValue(json, TravelPlanResponse.class);
            
            assertEquals(createdAt, deserialized.getCreatedAt());
            assertEquals(updatedAt, deserialized.getUpdatedAt());
        }

        @Test
        @DisplayName("Null date fields should be handled correctly")
        void nullDateSerialization() throws JsonProcessingException {
            TravelPlanResponse response = createValidResponse();
            response.setStartDate(null);
            response.setEndDate(null);
            response.setCreatedAt(null);
            response.setUpdatedAt(null);
            
            String json = objectMapper.writeValueAsString(response);
            TravelPlanResponse deserialized = objectMapper.readValue(json, TravelPlanResponse.class);
            
            assertNull(deserialized.getStartDate());
            assertNull(deserialized.getEndDate());
            assertNull(deserialized.getCreatedAt());
            assertNull(deserialized.getUpdatedAt());
        }
    }

    @Nested
    @DisplayName("Nested Object Serialization Tests")
    class NestedObjectSerializationTests {

        @Test
        @DisplayName("Itinerary items should serialize correctly")
        void itineraryItemsSerialization() throws JsonProcessingException {
            TravelPlanResponse response = createValidResponse();
            
            String json = objectMapper.writeValueAsString(response);
            TravelPlanResponse deserialized = objectMapper.readValue(json, TravelPlanResponse.class);
            
            assertNotNull(deserialized.getItineraryItems());
            assertEquals(2, deserialized.getItineraryItems().size());
            
            ItineraryItemResponse firstItem = deserialized.getItineraryItems().get(0);
            assertEquals("item-1", firstItem.getId());
            assertEquals(Integer.valueOf(1), firstItem.getDayNumber());
            assertEquals("Early morning visit to avoid crowds", firstItem.getNotes());
        }

        @Test
        @DisplayName("Complex nested objects should maintain data integrity")
        void complexNestedObjectsSerialization() {
            TravelPlanResponse response = createValidResponse();
            
            // Add complex itinerary items
            ItineraryItemResponse complexItem = ItineraryItemResponse.builder()
                .id("complex-item")
                .dayNumber(2)
                .order(1)
                .startTime(LocalDateTime.of(2024, 6, 16, 9, 30, 15))
                .endTime(LocalDateTime.of(2024, 6, 16, 11, 45, 30))
                .notes("Notes with emojis 🗼📸 and \"quotes\" and 'apostrophes' at Complex Location 東京タワー")
                .transportMode("TRAIN")
                .transportDuration(25)
                .build();
            
            response.getItineraryItems().add(complexItem);
            
            DTOValidationTestUtils.testSerialization(response, TravelPlanResponse.class);
        }

        @Test
        @DisplayName("Empty nested objects should serialize correctly")
        void emptyNestedObjectsSerialization() throws JsonProcessingException {
            TravelPlanResponse response = createValidResponse();
            response.setItineraryItems(Arrays.asList());
            
            String json = objectMapper.writeValueAsString(response);
            TravelPlanResponse deserialized = objectMapper.readValue(json, TravelPlanResponse.class);
            
            assertNotNull(deserialized.getItineraryItems());
            assertTrue(deserialized.getItineraryItems().isEmpty());
        }

        @Test
        @DisplayName("Null nested collections should serialize correctly")
        void nullNestedCollectionsSerialization() throws JsonProcessingException {
            TravelPlanResponse response = createValidResponse();
            response.setItineraryItems(null);
            response.setTags(null);
            response.setCollaboratorIds(null);
            
            String json = objectMapper.writeValueAsString(response);
            TravelPlanResponse deserialized = objectMapper.readValue(json, TravelPlanResponse.class);
            
            assertNull(deserialized.getItineraryItems());
            assertNull(deserialized.getTags());
            assertNull(deserialized.getCollaboratorIds());
        }
    }

    @Nested
    @DisplayName("Special Characters and Unicode Tests")
    class SpecialCharactersTests {

        @Test
        @DisplayName("Unicode characters should serialize correctly")
        void unicodeCharactersSerialization() {
            TravelPlanResponse response = createValidResponse();
            response.setTitle("東京 Adventure 🗾");
            response.setDescription("Exploring Japan 日本 with amazing food 🍜🍣");
            response.setDestination("Tokyo, Japan 日本");
            response.setUserName("田中太郎");
            
            DTOValidationTestUtils.testSerialization(response, TravelPlanResponse.class);
        }

        @Test
        @DisplayName("Special characters should be escaped properly")
        void specialCharactersEscaping() throws JsonProcessingException {
            TravelPlanResponse response = createValidResponse();
            response.setTitle("Title with \"quotes\" and 'apostrophes'");
            response.setDescription("Description with\nnewlines\tand\ttabs");
            response.setUserName("User\\with\\backslashes");
            
            String json = objectMapper.writeValueAsString(response);
            
            // Verify JSON is valid and properly escaped
            assertTrue(json.contains("\\\"quotes\\\""));
            assertTrue(json.contains("\\n"));
            assertTrue(json.contains("\\t"));
            
            // Verify deserialization preserves original values
            TravelPlanResponse deserialized = objectMapper.readValue(json, TravelPlanResponse.class);
            assertEquals("Title with \"quotes\" and 'apostrophes'", deserialized.getTitle());
            assertEquals("Description with\nnewlines\tand\ttabs", deserialized.getDescription());
            assertEquals("User\\with\\backslashes", deserialized.getUserName());
        }

        @Test
        @DisplayName("Emojis should serialize correctly")
        void emojiSerialization() {
            TravelPlanResponse response = createValidResponse();
            response.setTitle("Amazing Trip 🌟✈️🗾");
            response.setDescription("Love this place! 😍🎌🏔️⛩️");
            response.setTags(Arrays.asList("fun 🎉", "culture 🎭", "food 🍣"));
            
            DTOValidationTestUtils.testSerialization(response, TravelPlanResponse.class);
        }
    }

    @Nested
    @DisplayName("Boolean and Numeric Field Tests")
    class BooleanAndNumericFieldTests {

        @Test
        @DisplayName("Boolean fields should serialize correctly")
        void booleanFieldsSerialization() throws JsonProcessingException {
            TravelPlanResponse response = createValidResponse();
            response.setIsPublic(true);
            response.setAiGenerated(false);
            
            String json = objectMapper.writeValueAsString(response);
            
            assertTrue(json.contains("\"isPublic\":true"));
            assertTrue(json.contains("\"aiGenerated\":false"));
            
            TravelPlanResponse deserialized = objectMapper.readValue(json, TravelPlanResponse.class);
            assertEquals(Boolean.TRUE, deserialized.getIsPublic());
            assertEquals(Boolean.FALSE, deserialized.getAiGenerated());
        }

        @Test
        @DisplayName("Null boolean fields should serialize correctly")
        void nullBooleanFieldsSerialization() throws JsonProcessingException {
            TravelPlanResponse response = createValidResponse();
            response.setIsPublic(null);
            response.setAiGenerated(null);
            
            String json = objectMapper.writeValueAsString(response);
            TravelPlanResponse deserialized = objectMapper.readValue(json, TravelPlanResponse.class);
            
            assertNull(deserialized.getIsPublic());
            assertNull(deserialized.getAiGenerated());
        }

        @Test
        @DisplayName("Numeric fields should serialize correctly")
        void numericFieldsSerialization() throws JsonProcessingException {
            TravelPlanResponse response = createValidResponse();
            response.setViewCount(9999999L);
            response.setSaveCount(0L);
            
            String json = objectMapper.writeValueAsString(response);
            
            assertTrue(json.contains("\"viewCount\":9999999"));
            assertTrue(json.contains("\"saveCount\":0"));
            
            TravelPlanResponse deserialized = objectMapper.readValue(json, TravelPlanResponse.class);
            assertEquals(Long.valueOf(9999999L), deserialized.getViewCount());
            assertEquals(Long.valueOf(0L), deserialized.getSaveCount());
        }

        @Test
        @DisplayName("Null numeric fields should serialize correctly")
        void nullNumericFieldsSerialization() throws JsonProcessingException {
            TravelPlanResponse response = createValidResponse();
            response.setViewCount(null);
            response.setSaveCount(null);
            
            String json = objectMapper.writeValueAsString(response);
            TravelPlanResponse deserialized = objectMapper.readValue(json, TravelPlanResponse.class);
            
            assertNull(deserialized.getViewCount());
            assertNull(deserialized.getSaveCount());
        }
    }

    @Nested
    @DisplayName("Collections Serialization Tests")
    class CollectionsSerializationTests {

        @Test
        @DisplayName("String collections should serialize correctly")
        void stringCollectionsSerialization() throws JsonProcessingException {
            TravelPlanResponse response = createValidResponse();
            response.setTags(Arrays.asList("adventure", "culture", "food", "nightlife"));
            response.setCollaboratorIds(Arrays.asList("user-1", "user-2", "user-3"));
            
            String json = objectMapper.writeValueAsString(response);
            TravelPlanResponse deserialized = objectMapper.readValue(json, TravelPlanResponse.class);
            
            assertEquals(4, deserialized.getTags().size());
            assertTrue(deserialized.getTags().contains("adventure"));
            assertTrue(deserialized.getTags().contains("culture"));
            
            assertEquals(3, deserialized.getCollaboratorIds().size());
            assertTrue(deserialized.getCollaboratorIds().contains("user-1"));
        }

        @Test
        @DisplayName("Collections with special characters should serialize correctly")
        void collectionsWithSpecialCharactersSerialization() {
            TravelPlanResponse response = createValidResponse();
            response.setTags(Arrays.asList(
                "culture & tradition",
                "food/dining",
                "nightlife (bars)",
                "sightseeing \"must-see\"",
                "adventure 🎯"
            ));
            
            DTOValidationTestUtils.testSerialization(response, TravelPlanResponse.class);
        }

        @Test
        @DisplayName("Large collections should serialize efficiently")
        void largeCollectionsSerialization() {
            TravelPlanResponse response = createValidResponse();
            
            // Create large tag list
            List<String> largeTags = Arrays.asList(
                "tag1", "tag2", "tag3", "tag4", "tag5", "tag6", "tag7", "tag8", "tag9", "tag10",
                "tag11", "tag12", "tag13", "tag14", "tag15", "tag16", "tag17", "tag18", "tag19", "tag20"
            );
            response.setTags(largeTags);
            
            // Create large collaborator list
            List<String> largeCollaborators = Arrays.asList(
                "user-1", "user-2", "user-3", "user-4", "user-5", "user-6", "user-7", "user-8", "user-9", "user-10"
            );
            response.setCollaboratorIds(largeCollaborators);
            
            DTOValidationTestUtils.testSerialization(response, TravelPlanResponse.class);
        }
    }

    @Nested
    @DisplayName("JSON Field Mapping Tests")
    class JsonFieldMappingTests {

        @Test
        @DisplayName("All fields should map correctly in JSON")
        void fieldMappingVerification() throws JsonProcessingException {
            TravelPlanResponse response = createValidResponse();
            
            Map<String, Object> expectedFields = new HashMap<>();
            expectedFields.put("id", "tp-123");
            expectedFields.put("userId", "user-456");
            expectedFields.put("userName", "John Doe");
            expectedFields.put("title", "Amazing Tokyo Adventure");
            expectedFields.put("destination", "Tokyo, Japan");
            expectedFields.put("status", "ACTIVE");
            expectedFields.put("isPublic", true);
            expectedFields.put("aiGenerated", false);
            expectedFields.put("viewCount", 150L);
            expectedFields.put("saveCount", 25L);
            
            DTOValidationTestUtils.testSerializationWithFieldVerification(
                response, TravelPlanResponse.class, expectedFields
            );
        }

        @Test
        @DisplayName("Field names should match expected JSON keys")
        void jsonKeyMappingVerification() throws JsonProcessingException {
            TravelPlanResponse response = createValidResponse();
            String json = objectMapper.writeValueAsString(response);
            
            // Verify all expected field names are present in JSON
            String[] expectedFields = {
                "id", "userId", "userName", "userProfilePicture", "title", "description",
                "destination", "startDate", "endDate", "status", "isPublic", "aiGenerated",
                "imageUrl", "tags", "viewCount", "saveCount", "itineraryItems",
                "collaboratorIds", "createdAt", "updatedAt"
            };
            
            for (String field : expectedFields) {
                assertTrue(json.contains("\"" + field + "\":"), 
                    "JSON should contain field: " + field);
            }
        }
    }

    @Nested
    @DisplayName("Builder Pattern Tests")
    class BuilderPatternTests {

        @Test
        @DisplayName("Builder should create serializable instance")
        void builderCreatesSerializableInstance() {
            TravelPlanResponse response = TravelPlanResponse.builder()
                .id("test-id")
                .title("Test Title")
                .destination("Test Destination")
                .build();
            
            assertNotNull(response);
            DTOValidationTestUtils.testSerialization(response, TravelPlanResponse.class);
        }

        @Test
        @DisplayName("Builder with all fields should be serializable")
        void builderWithAllFields() {
            TravelPlanResponse response = TravelPlanResponse.builder()
                .id("tp-001")
                .userId("user-001")
                .userName("Test User")
                .userProfilePicture("https://example.com/user.jpg")
                .title("Complete Test Plan")
                .description("Full description")
                .destination("Test City")
                .startDate(LocalDate.now().plusDays(1))
                .endDate(LocalDate.now().plusDays(7))
                .status("DRAFT")
                .isPublic(false)
                .aiGenerated(true)
                .imageUrl("https://example.com/plan.jpg")
                .tags(Arrays.asList("test", "complete"))
                .viewCount(0L)
                .saveCount(0L)
                .itineraryItems(Arrays.asList())
                .collaboratorIds(Arrays.asList())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
            
            DTOValidationTestUtils.testSerialization(response, TravelPlanResponse.class);
        }
    }

    @Nested
    @DisplayName("Edge Cases Tests")
    class EdgeCasesTests {

        @Test
        @DisplayName("Very long text fields should serialize correctly")
        void veryLongTextFields() {
            TravelPlanResponse response = createValidResponse();
            response.setTitle("A".repeat(1000));
            response.setDescription("B".repeat(5000));
            response.setDestination("C".repeat(500));
            response.setUserName("D".repeat(200));
            
            DTOValidationTestUtils.testSerialization(response, TravelPlanResponse.class);
        }

        @Test
        @DisplayName("Boundary date values should serialize correctly")
        void boundaryDateValues() {
            TravelPlanResponse response = createValidResponse();
            response.setStartDate(LocalDate.of(1900, 1, 1));
            response.setEndDate(LocalDate.of(2099, 12, 31));
            response.setCreatedAt(LocalDateTime.of(1900, 1, 1, 0, 0, 0));
            response.setUpdatedAt(LocalDateTime.of(2099, 12, 31, 23, 59, 59));
            
            DTOValidationTestUtils.testSerialization(response, TravelPlanResponse.class);
        }

        @Test
        @DisplayName("Extreme numeric values should serialize correctly")
        void extremeNumericValues() {
            TravelPlanResponse response = createValidResponse();
            response.setViewCount(Long.MAX_VALUE);
            response.setSaveCount(0L);
            
            DTOValidationTestUtils.testSerialization(response, TravelPlanResponse.class);
        }

        @Test
        @DisplayName("Mixed null and non-null fields should serialize correctly")
        void mixedNullAndNonNullFields() {
            TravelPlanResponse response = TravelPlanResponse.builder()
                .id("mixed-test")
                .userId("user-test")
                .userName(null)  // null
                .userProfilePicture("https://example.com/pic.jpg")  // non-null
                .title("Mixed Test")  // non-null
                .description(null)  // null
                .destination("Test City")  // non-null
                .startDate(LocalDate.now())  // non-null
                .endDate(null)  // null
                .status("ACTIVE")  // non-null
                .isPublic(null)  // null
                .aiGenerated(false)  // non-null
                .imageUrl(null)  // null
                .tags(Arrays.asList("tag1"))  // non-null
                .viewCount(null)  // null
                .saveCount(10L)  // non-null
                .itineraryItems(null)  // null
                .collaboratorIds(Arrays.asList())  // non-null empty
                .createdAt(LocalDateTime.now())  // non-null
                .updatedAt(null)  // null
                .build();
            
            DTOValidationTestUtils.testSerialization(response, TravelPlanResponse.class);
        }
    }

    @Nested
    @DisplayName("Comprehensive Tests")
    class ComprehensiveTests {

        @Test
        @DisplayName("Should pass comprehensive serialization tests")
        void comprehensiveSerializationTests() {
            DTOValidationTestUtils.runComprehensiveValidationTests(
                createValidResponse(),
                TravelPlanResponse.class
            );
        }

        @Test
        @DisplayName("Multiple serialization rounds should maintain data integrity")
        void multipleSerializationRounds() throws JsonProcessingException {
            TravelPlanResponse original = createValidResponse();
            
            // First round
            String json1 = objectMapper.writeValueAsString(original);
            TravelPlanResponse deserialized1 = objectMapper.readValue(json1, TravelPlanResponse.class);
            
            // Second round
            String json2 = objectMapper.writeValueAsString(deserialized1);
            TravelPlanResponse deserialized2 = objectMapper.readValue(json2, TravelPlanResponse.class);
            
            // Third round
            String json3 = objectMapper.writeValueAsString(deserialized2);
            TravelPlanResponse deserialized3 = objectMapper.readValue(json3, TravelPlanResponse.class);
            
            // All should maintain the same data
            assertEquals(original.getId(), deserialized3.getId());
            assertEquals(original.getTitle(), deserialized3.getTitle());
            assertEquals(original.getStartDate(), deserialized3.getStartDate());
            assertEquals(original.getViewCount(), deserialized3.getViewCount());
        }
    }
}