package com.oddiya.dto.request;

import com.oddiya.utils.DTOValidationTestUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CreateTravelPlanRequest Validation Tests")
class CreateTravelPlanRequestTest {

    private CreateTravelPlanRequest createValidRequest() {
        return CreateTravelPlanRequest.builder()
            .title("Amazing Tokyo Adventure")
            .description("A wonderful journey through Tokyo exploring traditional and modern culture")
            .destination("Tokyo, Japan")
            .startDate(LocalDate.now().plusDays(7))
            .endDate(LocalDate.now().plusDays(14))
            .isPublic(true)
            .aiGenerated(false)
            .imageUrl("https://example.com/image.jpg")
            .tags(Arrays.asList("culture", "food", "city"))
            .build();
    }

    @Nested
    @DisplayName("Valid Request Tests")
    class ValidRequestTests {

        @Test
        @DisplayName("Valid complete request should pass validation")
        void validCompleteRequest() {
            CreateTravelPlanRequest request = createValidRequest();
            DTOValidationTestUtils.assertValidDTO(request);
        }

        @Test
        @DisplayName("Valid minimal request should pass validation")
        void validMinimalRequest() {
            CreateTravelPlanRequest request = CreateTravelPlanRequest.builder()
                .title("T")  // Minimum length
                .destination("D") // Minimum length
                .startDate(LocalDate.now().plusDays(1))
                .endDate(LocalDate.now().plusDays(2))
                .build();
            
            DTOValidationTestUtils.assertValidDTO(request);
        }

        @Test
        @DisplayName("Request with maximum length fields should pass validation")
        void validMaximumLengthRequest() {
            CreateTravelPlanRequest request = CreateTravelPlanRequest.builder()
                .title("A".repeat(200))  // Maximum title length
                .description("D".repeat(2000))  // Maximum description length
                .destination("D".repeat(200))  // Maximum destination length
                .startDate(LocalDate.now().plusDays(1))
                .endDate(LocalDate.now().plusDays(2))
                .build();
            
            DTOValidationTestUtils.assertValidDTO(request);
        }

        @Test
        @DisplayName("Request with null optional fields should pass validation")
        void validWithNullOptionalFields() {
            CreateTravelPlanRequest request = CreateTravelPlanRequest.builder()
                .title("Tokyo Trip")
                .destination("Tokyo")
                .startDate(LocalDate.now().plusDays(1))
                .endDate(LocalDate.now().plusDays(2))
                .description(null)  // Optional
                .imageUrl(null)     // Optional
                .tags(null)         // Optional
                .itineraryItems(null) // Optional
                .build();
            
            DTOValidationTestUtils.assertValidDTO(request);
        }
    }

    @Nested
    @DisplayName("Title Validation Tests")
    class TitleValidationTests {

        @Test
        @DisplayName("Null title should fail validation")
        void nullTitleShouldFail() {
            CreateTravelPlanRequest request = createValidRequest();
            request.setTitle(null);
            
            DTOValidationTestUtils.assertViolationCount(request, 1);
            DTOValidationTestUtils.assertHasViolationMessage(request, "Title is required");
        }

        @Test
        @DisplayName("Empty title should fail validation")
        void emptyTitleShouldFail() {
            CreateTravelPlanRequest request = createValidRequest();
            request.setTitle("");
            
            DTOValidationTestUtils.assertViolationCount(request, 1);
            DTOValidationTestUtils.assertHasViolationMessage(request, "Title is required");
        }

        @Test
        @DisplayName("Blank title should fail validation")
        void blankTitleShouldFail() {
            CreateTravelPlanRequest request = createValidRequest();
            request.setTitle("   ");
            
            DTOValidationTestUtils.assertViolationCount(request, 1);
            DTOValidationTestUtils.assertHasViolationMessage(request, "Title is required");
        }

        @Test
        @DisplayName("Title exceeding maximum length should fail validation")
        void titleTooLongShouldFail() {
            CreateTravelPlanRequest request = createValidRequest();
            request.setTitle("A".repeat(201)); // Exceeds 200 character limit
            
            DTOValidationTestUtils.assertViolationCount(request, 1);
            DTOValidationTestUtils.assertHasViolationMessage(request, 
                "Title must be between 1 and 200 characters");
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 50, 100, 150, 200})
        @DisplayName("Valid title lengths should pass validation")
        void validTitleLengths(int length) {
            CreateTravelPlanRequest request = createValidRequest();
            request.setTitle("A".repeat(length));
            
            DTOValidationTestUtils.assertValidDTO(request);
        }
    }

    @Nested
    @DisplayName("Description Validation Tests")
    class DescriptionValidationTests {

        @Test
        @DisplayName("Description exceeding maximum length should fail validation")
        void descriptionTooLongShouldFail() {
            CreateTravelPlanRequest request = createValidRequest();
            request.setDescription("A".repeat(2001)); // Exceeds 2000 character limit
            
            DTOValidationTestUtils.assertViolationCount(request, 1);
            DTOValidationTestUtils.assertHasViolationMessage(request, 
                "Description cannot exceed 2000 characters");
        }

        @Test
        @DisplayName("Null description should pass validation")
        void nullDescriptionShouldPass() {
            CreateTravelPlanRequest request = createValidRequest();
            request.setDescription(null);
            
            DTOValidationTestUtils.assertValidDTO(request);
        }

        @Test
        @DisplayName("Empty description should pass validation")
        void emptyDescriptionShouldPass() {
            CreateTravelPlanRequest request = createValidRequest();
            request.setDescription("");
            
            DTOValidationTestUtils.assertValidDTO(request);
        }

        @ParameterizedTest
        @ValueSource(ints = {0, 500, 1000, 1500, 2000})
        @DisplayName("Valid description lengths should pass validation")
        void validDescriptionLengths(int length) {
            CreateTravelPlanRequest request = createValidRequest();
            request.setDescription("A".repeat(length));
            
            DTOValidationTestUtils.assertValidDTO(request);
        }
    }

    @Nested
    @DisplayName("Destination Validation Tests")
    class DestinationValidationTests {

        @Test
        @DisplayName("Null destination should fail validation")
        void nullDestinationShouldFail() {
            CreateTravelPlanRequest request = createValidRequest();
            request.setDestination(null);
            
            DTOValidationTestUtils.assertViolationCount(request, 1);
            DTOValidationTestUtils.assertHasViolationMessage(request, "Destination is required");
        }

        @Test
        @DisplayName("Empty destination should fail validation")
        void emptyDestinationShouldFail() {
            CreateTravelPlanRequest request = createValidRequest();
            request.setDestination("");
            
            DTOValidationTestUtils.assertViolationCount(request, 1);
            DTOValidationTestUtils.assertHasViolationMessage(request, "Destination is required");
        }

        @Test
        @DisplayName("Blank destination should fail validation")
        void blankDestinationShouldFail() {
            CreateTravelPlanRequest request = createValidRequest();
            request.setDestination("   ");
            
            DTOValidationTestUtils.assertViolationCount(request, 1);
            DTOValidationTestUtils.assertHasViolationMessage(request, "Destination is required");
        }

        @Test
        @DisplayName("Destination exceeding maximum length should fail validation")
        void destinationTooLongShouldFail() {
            CreateTravelPlanRequest request = createValidRequest();
            request.setDestination("A".repeat(201)); // Exceeds 200 character limit
            
            DTOValidationTestUtils.assertViolationCount(request, 1);
            DTOValidationTestUtils.assertHasViolationMessage(request, 
                "Destination must be between 1 and 200 characters");
        }
    }

    @Nested
    @DisplayName("Date Validation Tests")
    class DateValidationTests {

        @Test
        @DisplayName("Null start date should fail validation")
        void nullStartDateShouldFail() {
            CreateTravelPlanRequest request = createValidRequest();
            request.setStartDate(null);
            
            DTOValidationTestUtils.assertViolationCount(request, 1);
            DTOValidationTestUtils.assertHasViolationMessage(request, "Start date is required");
        }

        @Test
        @DisplayName("Null end date should fail validation")
        void nullEndDateShouldFail() {
            CreateTravelPlanRequest request = createValidRequest();
            request.setEndDate(null);
            
            DTOValidationTestUtils.assertViolationCount(request, 1);
            DTOValidationTestUtils.assertHasViolationMessage(request, "End date is required");
        }

        @Test
        @DisplayName("Past start date should fail validation")
        void pastStartDateShouldFail() {
            CreateTravelPlanRequest request = createValidRequest();
            request.setStartDate(LocalDate.now().minusDays(1));
            
            DTOValidationTestUtils.assertViolationCount(request, 1);
            DTOValidationTestUtils.assertHasViolationMessage(request, "Start date must be in the future");
        }

        @Test
        @DisplayName("Past end date should fail validation")
        void pastEndDateShouldFail() {
            CreateTravelPlanRequest request = createValidRequest();
            request.setEndDate(LocalDate.now().minusDays(1));
            
            DTOValidationTestUtils.assertViolationCount(request, 1);
            DTOValidationTestUtils.assertHasViolationMessage(request, "End date must be in the future");
        }

        @Test
        @DisplayName("Today's date should fail validation")
        void todaysDateShouldFail() {
            CreateTravelPlanRequest request = createValidRequest();
            request.setStartDate(LocalDate.now());
            request.setEndDate(LocalDate.now());
            
            // Both dates should fail @Future validation
            DTOValidationTestUtils.assertViolationCount(request, 2);
            DTOValidationTestUtils.assertHasViolationMessage(request, "Start date must be in the future");
            DTOValidationTestUtils.assertHasViolationMessage(request, "End date must be in the future");
        }

        @Test
        @DisplayName("Future dates should pass validation")
        void futureDatesShouldPass() {
            CreateTravelPlanRequest request = createValidRequest();
            request.setStartDate(LocalDate.now().plusDays(30));
            request.setEndDate(LocalDate.now().plusDays(37));
            
            DTOValidationTestUtils.assertValidDTO(request);
        }
    }

    @Nested
    @DisplayName("Boolean Fields Tests")
    class BooleanFieldsTests {

        @Test
        @DisplayName("Default boolean values should be set correctly")
        void defaultBooleanValues() {
            CreateTravelPlanRequest request = CreateTravelPlanRequest.builder()
                .title("Test")
                .destination("Test")
                .startDate(LocalDate.now().plusDays(1))
                .endDate(LocalDate.now().plusDays(2))
                .build();
            
            // Builder defaults should be applied
            assertEquals(Boolean.FALSE, request.getIsPublic());
            assertEquals(Boolean.FALSE, request.getAiGenerated());
        }

        @Test
        @DisplayName("Explicit boolean values should be preserved")
        void explicitBooleanValues() {
            CreateTravelPlanRequest request = CreateTravelPlanRequest.builder()
                .title("Test")
                .destination("Test")
                .startDate(LocalDate.now().plusDays(1))
                .endDate(LocalDate.now().plusDays(2))
                .isPublic(true)
                .aiGenerated(true)
                .build();
            
            assertEquals(Boolean.TRUE, request.getIsPublic());
            assertEquals(Boolean.TRUE, request.getAiGenerated());
        }

        @Test
        @DisplayName("Null boolean values should be handled properly")
        void nullBooleanValues() {
            CreateTravelPlanRequest request = createValidRequest();
            request.setIsPublic(null);
            request.setAiGenerated(null);
            
            // Should still pass validation as these fields are not annotated with @NotNull
            DTOValidationTestUtils.assertValidDTO(request);
        }
    }

    @Nested
    @DisplayName("Collections Tests")
    class CollectionsTests {

        @Test
        @DisplayName("Valid tags list should pass validation")
        void validTagsList() {
            CreateTravelPlanRequest request = createValidRequest();
            request.setTags(Arrays.asList("adventure", "culture", "food", "nightlife"));
            
            DTOValidationTestUtils.assertValidDTO(request);
        }

        @Test
        @DisplayName("Empty tags list should pass validation")
        void emptyTagsList() {
            CreateTravelPlanRequest request = createValidRequest();
            request.setTags(Arrays.asList());
            
            DTOValidationTestUtils.assertValidDTO(request);
        }

        @Test
        @DisplayName("Null tags list should pass validation")
        void nullTagsList() {
            CreateTravelPlanRequest request = createValidRequest();
            request.setTags(null);
            
            DTOValidationTestUtils.assertValidDTO(request);
        }

        @Test
        @DisplayName("Valid itinerary items list should pass validation")
        void validItineraryItemsList() {
            CreateTravelPlanRequest request = createValidRequest();
            
            List<CreateItineraryItemRequest> items = Arrays.asList(
                CreateItineraryItemRequest.builder()
                    .dayNumber(1)
                    .order(1)
                    .placeId("place123")
                    .startTime(LocalDate.now().plusDays(7).atTime(9, 0))
                    .endTime(LocalDate.now().plusDays(7).atTime(12, 0))
                    .build()
            );
            
            request.setItineraryItems(items);
            DTOValidationTestUtils.assertValidDTO(request);
        }
    }

    @Nested
    @DisplayName("Multiple Violations Tests")
    class MultipleViolationsTests {

        @Test
        @DisplayName("Multiple field violations should be detected")
        void multipleFieldViolations() {
            CreateTravelPlanRequest request = CreateTravelPlanRequest.builder()
                .title(null)  // Violation: required
                .destination("")  // Violation: required
                .startDate(null)  // Violation: required
                .endDate(LocalDate.now().minusDays(1))  // Violation: must be future
                .description("A".repeat(2001))  // Violation: too long
                .build();
            
            DTOValidationTestUtils.assertViolationCount(request, 5);
            DTOValidationTestUtils.assertHasViolationMessage(request, "Title is required");
            DTOValidationTestUtils.assertHasViolationMessage(request, "Destination is required");
            DTOValidationTestUtils.assertHasViolationMessage(request, "Start date is required");
            DTOValidationTestUtils.assertHasViolationMessage(request, "End date must be in the future");
            DTOValidationTestUtils.assertHasViolationMessage(request, "Description cannot exceed 2000 characters");
        }
    }

    @Nested
    @DisplayName("Serialization Tests")
    class SerializationTests {

        @Test
        @DisplayName("Valid request should serialize and deserialize correctly")
        void serializationRoundTrip() {
            CreateTravelPlanRequest request = createValidRequest();
            DTOValidationTestUtils.testSerialization(request, CreateTravelPlanRequest.class);
        }

        @Test
        @DisplayName("Request with null optional fields should serialize correctly")
        void serializationWithNullFields() {
            CreateTravelPlanRequest request = CreateTravelPlanRequest.builder()
                .title("Test")
                .destination("Test")
                .startDate(LocalDate.now().plusDays(1))
                .endDate(LocalDate.now().plusDays(2))
                .description(null)
                .imageUrl(null)
                .tags(null)
                .build();
            
            DTOValidationTestUtils.testSerialization(request, CreateTravelPlanRequest.class);
        }

        @Test
        @DisplayName("Request with collections should serialize correctly")
        void serializationWithCollections() {
            CreateTravelPlanRequest request = createValidRequest();
            request.setTags(Arrays.asList("tag1", "tag2", "tag3"));
            
            DTOValidationTestUtils.testSerialization(request, CreateTravelPlanRequest.class);
        }
    }

    @Nested
    @DisplayName("Builder Pattern Tests")
    class BuilderPatternTests {

        @Test
        @DisplayName("Builder should create valid instance")
        void builderCreatesValidInstance() {
            CreateTravelPlanRequest request = CreateTravelPlanRequest.builder()
                .title("Test Trip")
                .destination("Test Destination")
                .startDate(LocalDate.now().plusDays(1))
                .endDate(LocalDate.now().plusDays(3))
                .build();
            
            assertNotNull(request);
            assertEquals("Test Trip", request.getTitle());
            assertEquals("Test Destination", request.getDestination());
            // Verify builder defaults
            assertEquals(Boolean.FALSE, request.getIsPublic());
            assertEquals(Boolean.FALSE, request.getAiGenerated());
        }

        @Test
        @DisplayName("Builder should handle all fields correctly")
        void builderHandlesAllFields() {
            List<String> tags = Arrays.asList("test", "trip");
            
            CreateTravelPlanRequest request = CreateTravelPlanRequest.builder()
                .title("Complete Test")
                .description("Test description")
                .destination("Test destination")
                .startDate(LocalDate.now().plusDays(1))
                .endDate(LocalDate.now().plusDays(3))
                .isPublic(true)
                .aiGenerated(true)
                .imageUrl("http://test.com/image.jpg")
                .tags(tags)
                .build();
            
            assertNotNull(request);
            assertEquals("Complete Test", request.getTitle());
            assertEquals("Test description", request.getDescription());
            assertEquals("Test destination", request.getDestination());
            assertEquals(Boolean.TRUE, request.getIsPublic());
            assertEquals(Boolean.TRUE, request.getAiGenerated());
            assertEquals("http://test.com/image.jpg", request.getImageUrl());
            assertEquals(tags, request.getTags());
        }
    }

    @Nested
    @DisplayName("Edge Cases Tests")
    class EdgeCasesTests {

        @Test
        @DisplayName("Unicode characters in text fields should be handled correctly")
        void unicodeCharactersHandling() {
            CreateTravelPlanRequest request = createValidRequest();
            request.setTitle("Tokyo 東京 Adventure 🗾");
            request.setDescription("Exploring Japan 日本 with amazing food 🍜🍣");
            request.setDestination("Tokyo, Japan 日本");
            
            DTOValidationTestUtils.assertValidDTO(request);
            DTOValidationTestUtils.testSerialization(request, CreateTravelPlanRequest.class);
        }

        @Test
        @DisplayName("Special characters in text fields should be handled correctly")
        void specialCharactersHandling() {
            CreateTravelPlanRequest request = createValidRequest();
            request.setTitle("Trip & Adventure - Part 1 (2024)");
            request.setDescription("A trip with quotes \"amazing\", apostrophes 'great', and symbols @#$%");
            request.setDestination("New York, NY - USA");
            
            DTOValidationTestUtils.assertValidDTO(request);
            DTOValidationTestUtils.testSerialization(request, CreateTravelPlanRequest.class);
        }

        @Test
        @DisplayName("Boundary date values should be handled correctly")
        void boundaryDateValues() {
            CreateTravelPlanRequest request = createValidRequest();
            // Test with dates far in the future
            request.setStartDate(LocalDate.now().plusYears(10));
            request.setEndDate(LocalDate.now().plusYears(10).plusDays(7));
            
            DTOValidationTestUtils.assertValidDTO(request);
            DTOValidationTestUtils.testSerialization(request, CreateTravelPlanRequest.class);
        }
    }

    @Nested
    @DisplayName("POJO Tests")
    class POJOTests {

        @Test
        @DisplayName("Should pass comprehensive POJO tests")
        void comprehensivePOJOTests() {
            DTOValidationTestUtils.runComprehensiveValidationTests(
                createValidRequest(), 
                CreateTravelPlanRequest.class
            );
        }
    }
}