package com.oddiya.dto;

import com.oddiya.dto.request.CreateTravelPlanRequest;
import com.oddiya.dto.request.SignUpRequest;
import com.oddiya.dto.request.UpdateUserProfileRequest;
import com.oddiya.dto.response.*;
import com.oddiya.entity.*;
import com.oddiya.utils.DTOValidationTestUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DTO Transformation Integration Tests")
class DTOTransformationIntegrationTest {

    private final ObjectMapper objectMapper;

    public DTOTransformationIntegrationTest() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @Nested
    @DisplayName("User Entity ↔ DTO Transformation Tests")
    class UserTransformationTests {

        @Test
        @DisplayName("SignUpRequest should transform to User entity correctly")
        void signUpRequestToUserEntity() {
            SignUpRequest request = SignUpRequest.builder()
                .email("test@example.com")
                .password("password123")
                .nickname("testuser")
                .fullName("Test User")
                .build();

            // Simulate transformation from SignUpRequest to User entity
            User user = User.builder()
                .email(request.getEmail())
                .nickname(request.getNickname() != null ? request.getNickname() : "user")
                .provider("local")
                .providerId("local-" + request.getEmail())
                .password(request.getPassword()) // Would be hashed in real app
                .bio(request.getFullName()) // Mapping fullName to bio as example
                .isEmailVerified(false)
                .isPremium(false)
                .isActive(true)
                .id("user-123")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

            // Verify transformation
            assertEquals(request.getEmail(), user.getEmail());
            assertEquals(request.getNickname(), user.getNickname());
            assertEquals(request.getPassword(), user.getPassword());
            assertEquals(request.getFullName(), user.getBio());
            assertEquals("local", user.getProvider());
            assertFalse(user.isEmailVerified());
        }

        @Test
        @DisplayName("User entity should transform to UserProfileResponse correctly")
        void userEntityToProfileResponse() {
            User user = User.builder()
                .id("user-123")
                .email("test@example.com")
                .nickname("testuser")
                .bio("Passionate traveler and photographer")
                .profileImageUrl("https://example.com/profile.jpg")
                .isActive(true)
                .isPremium(false)
                .preferences(Map.of("theme", "dark", "notifications", "true"))
                .travelPreferences(Map.of("budget", "medium", "style", "adventure"))
                .createdAt(LocalDateTime.of(2024, 1, 15, 10, 30))
                .updatedAt(LocalDateTime.of(2024, 1, 20, 14, 45))
                .build();

            // Simulate transformation from User entity to UserProfileResponse
            UserProfileResponse response = UserProfileResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .name(user.getNickname())
                .bio(user.getBio())
                .profilePicture(user.getProfileImageUrl())
                .phoneNumber(user.getPreferences().get("phoneNumber"))
                .preferredLanguage(user.getPreferences().get("language"))
                .timezone(user.getPreferences().get("timezone"))
                .notificationsEnabled(Boolean.valueOf(user.getPreferences().getOrDefault("notifications", "false")))
                .isPublic(Boolean.valueOf(user.getPreferences().getOrDefault("isPublic", "false")))
                .followersCount(0)
                .followingCount(0)
                .travelPlansCount(0)
                .reviewsCount(0)
                .videosCount(0)
                .isFollowing(false)
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();

            // Verify transformation
            assertEquals(user.getId(), response.getId());
            assertEquals(user.getEmail(), response.getEmail());
            assertEquals(user.getNickname(), response.getName());
            assertEquals(user.getBio(), response.getBio());
            assertEquals(user.getProfileImageUrl(), response.getProfilePicture());
            assertEquals(user.getCreatedAt(), response.getCreatedAt());
            assertEquals(user.getUpdatedAt(), response.getUpdatedAt());

            // Verify response is valid DTO
            DTOValidationTestUtils.assertValidDTO(response);
        }

        @Test
        @DisplayName("UpdateUserProfileRequest should apply to User entity correctly")
        void updateProfileRequestToUserEntity() {
            // Existing user
            User user = User.builder()
                .id("user-123")
                .email("test@example.com")
                .nickname("oldnickname")
                .bio("Old bio")
                .preferences(new HashMap<>(Map.of("theme", "light")))
                .build();

            // Update request
            UpdateUserProfileRequest request = UpdateUserProfileRequest.builder()
                .name("newnickname")
                .bio("New bio about my travels")
                .profilePicture("https://example.com/newprofile.jpg")
                .phoneNumber("+1234567890")
                .preferredLanguage("en")
                .timezone("UTC")
                .notificationsEnabled(true)
                .isPublic(false)
                .build();

            // Simulate applying update request to user entity
            if (request.getName() != null) {
                user.setNickname(request.getName());
            }
            if (request.getBio() != null) {
                user.setBio(request.getBio());
            }
            if (request.getProfilePicture() != null) {
                user.setProfileImageUrl(request.getProfilePicture());
            }
            
            // Update preferences
            if (request.getPhoneNumber() != null) {
                user.getPreferences().put("phoneNumber", request.getPhoneNumber());
            }
            if (request.getPreferredLanguage() != null) {
                user.getPreferences().put("language", request.getPreferredLanguage());
            }
            if (request.getTimezone() != null) {
                user.getPreferences().put("timezone", request.getTimezone());
            }
            if (request.getNotificationsEnabled() != null) {
                user.getPreferences().put("notifications", request.getNotificationsEnabled().toString());
            }
            if (request.getIsPublic() != null) {
                user.getPreferences().put("isPublic", request.getIsPublic().toString());
            }

            // Verify transformations
            assertEquals(request.getName(), user.getNickname());
            assertEquals(request.getBio(), user.getBio());
            assertEquals(request.getProfilePicture(), user.getProfileImageUrl());
            assertEquals(request.getPhoneNumber(), user.getPreferences().get("phoneNumber"));
            assertEquals(request.getPreferredLanguage(), user.getPreferences().get("language"));
            assertEquals(request.getTimezone(), user.getPreferences().get("timezone"));
            assertEquals(request.getNotificationsEnabled().toString(), user.getPreferences().get("notifications"));
            assertEquals(request.getIsPublic().toString(), user.getPreferences().get("isPublic"));
        }
    }

    @Nested
    @DisplayName("TravelPlan Entity ↔ DTO Transformation Tests")
    class TravelPlanTransformationTests {

        @Test
        @DisplayName("CreateTravelPlanRequest should transform to TravelPlan entity correctly")
        void createTravelPlanRequestToEntity() {
            CreateTravelPlanRequest request = CreateTravelPlanRequest.builder()
                .title("Amazing Tokyo Adventure")
                .description("A wonderful journey through Tokyo exploring traditional and modern culture")
                .destination("Tokyo, Japan")
                .startDate(LocalDate.of(2024, 6, 15))
                .endDate(LocalDate.of(2024, 6, 22))
                .isPublic(true)
                .aiGenerated(false)
                .imageUrl("https://example.com/tokyo.jpg")
                .tags(Arrays.asList("culture", "food", "city"))
                .build();

            // Simulate user who creates the plan
            User user = User.builder()
                .id("user-123")
                .email("test@example.com")
                .nickname("testuser")
                .build();

            // Simulate transformation from CreateTravelPlanRequest to TravelPlan entity
            TravelPlan travelPlan = TravelPlan.builder()
                .user(user)
                .title(request.getTitle())
                .description(request.getDescription())
                .destination(request.getDestination())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .isPublic(request.getIsPublic() != null ? request.getIsPublic() : false)
                .isAiGenerated(request.getAiGenerated() != null ? request.getAiGenerated() : false)
                .coverImageUrl(request.getImageUrl())
                .tags(request.getTags())
                .status(TravelPlanStatus.DRAFT)
                .viewCount(0L)
                .likeCount(0L)
                .shareCount(0L)
                .saveCount(0L)
                .id("tp-123")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

            // Verify transformation
            assertEquals(request.getTitle(), travelPlan.getTitle());
            assertEquals(request.getDescription(), travelPlan.getDescription());
            assertEquals(request.getDestination(), travelPlan.getDestination());
            assertEquals(request.getStartDate(), travelPlan.getStartDate());
            assertEquals(request.getEndDate(), travelPlan.getEndDate());
            assertEquals(request.getIsPublic(), travelPlan.isPublic());
            assertEquals(request.getAiGenerated(), travelPlan.isAiGenerated());
            assertEquals(request.getImageUrl(), travelPlan.getCoverImageUrl());
            assertEquals(request.getTags(), travelPlan.getTags());
            assertEquals(TravelPlanStatus.DRAFT, travelPlan.getStatus());
        }

        @Test
        @DisplayName("TravelPlan entity should transform to TravelPlanResponse correctly")
        void travelPlanEntityToResponse() {
            User user = User.builder()
                .id("user-123")
                .nickname("testuser")
                .profileImageUrl("https://example.com/profile.jpg")
                .build();

            TravelPlan travelPlan = TravelPlan.builder()
                .id("tp-123")
                .user(user)
                .title("Amazing Tokyo Adventure")
                .description("A wonderful journey through Tokyo")
                .destination("Tokyo, Japan")
                .startDate(LocalDate.of(2024, 6, 15))
                .endDate(LocalDate.of(2024, 6, 22))
                .status(TravelPlanStatus.CONFIRMED)
                .isPublic(true)
                .isAiGenerated(false)
                .coverImageUrl("https://example.com/tokyo.jpg")
                .tags(Arrays.asList("culture", "food", "city"))
                .viewCount(150L)
                .saveCount(25L)
                .createdAt(LocalDateTime.of(2024, 1, 15, 10, 30))
                .updatedAt(LocalDateTime.of(2024, 1, 20, 14, 45))
                .build();

            // Simulate transformation from TravelPlan entity to TravelPlanResponse
            TravelPlanResponse response = TravelPlanResponse.builder()
                .id(travelPlan.getId())
                .userId(travelPlan.getUser().getId())
                .userName(travelPlan.getUser().getNickname())
                .userProfilePicture(travelPlan.getUser().getProfileImageUrl())
                .title(travelPlan.getTitle())
                .description(travelPlan.getDescription())
                .destination(travelPlan.getDestination())
                .startDate(travelPlan.getStartDate())
                .endDate(travelPlan.getEndDate())
                .status(travelPlan.getStatus().name())
                .isPublic(travelPlan.isPublic())
                .aiGenerated(travelPlan.isAiGenerated())
                .imageUrl(travelPlan.getCoverImageUrl())
                .tags(travelPlan.getTags())
                .viewCount(travelPlan.getViewCount())
                .saveCount(travelPlan.getSaveCount())
                .itineraryItems(Arrays.asList()) // Would be mapped from actual items
                .collaboratorIds(Arrays.asList()) // Would be mapped from actual collaborators
                .createdAt(travelPlan.getCreatedAt())
                .updatedAt(travelPlan.getUpdatedAt())
                .build();

            // Verify transformation
            assertEquals(travelPlan.getId(), response.getId());
            assertEquals(travelPlan.getUser().getId(), response.getUserId());
            assertEquals(travelPlan.getUser().getNickname(), response.getUserName());
            assertEquals(travelPlan.getUser().getProfileImageUrl(), response.getUserProfilePicture());
            assertEquals(travelPlan.getTitle(), response.getTitle());
            assertEquals(travelPlan.getDescription(), response.getDescription());
            assertEquals(travelPlan.getDestination(), response.getDestination());
            assertEquals(travelPlan.getStartDate(), response.getStartDate());
            assertEquals(travelPlan.getEndDate(), response.getEndDate());
            assertEquals(travelPlan.getStatus().name(), response.getStatus());
            assertEquals(travelPlan.isPublic(), response.getIsPublic());
            assertEquals(travelPlan.isAiGenerated(), response.getAiGenerated());
            assertEquals(travelPlan.getCoverImageUrl(), response.getImageUrl());
            assertEquals(travelPlan.getTags(), response.getTags());
            assertEquals(travelPlan.getViewCount(), response.getViewCount());
            assertEquals(travelPlan.getSaveCount(), response.getSaveCount());

            // Verify response is valid DTO
            DTOValidationTestUtils.assertValidDTO(response);
        }

        @Test
        @DisplayName("TravelPlan with complex data should transform correctly")
        void complexTravelPlanTransformation() {
            User owner = User.builder()
                .id("owner-123")
                .nickname("owner")
                .profileImageUrl("https://example.com/owner.jpg")
                .build();

            List<User> collaborators = Arrays.asList(
                User.builder().id("collab-1").nickname("collaborator1").build(),
                User.builder().id("collab-2").nickname("collaborator2").build()
            );

            TravelPlan travelPlan = TravelPlan.builder()
                .id("tp-complex")
                .user(owner)
                .title("Complex Travel Plan")
                .description("A complex travel plan with multiple collaborators")
                .destination("Multiple Cities")
                .startDate(LocalDate.of(2024, 7, 1))
                .endDate(LocalDate.of(2024, 7, 15))
                .numberOfPeople(4)
                .budget(new BigDecimal("5000.00"))
                .status(TravelPlanStatus.CONFIRMED)
                .isPublic(true)
                .isAiGenerated(true)
                .preferences(Map.of("accommodation", "hotel", "transport", "plane"))
                .tags(Arrays.asList("adventure", "family", "multiple-cities"))
                .collaborators(collaborators)
                .viewCount(500L)
                .likeCount(75L)
                .shareCount(15L)
                .saveCount(50L)
                .createdAt(LocalDateTime.of(2024, 1, 1, 12, 0))
                .updatedAt(LocalDateTime.of(2024, 1, 15, 18, 30))
                .build();

            // Transform to response
            TravelPlanResponse response = TravelPlanResponse.builder()
                .id(travelPlan.getId())
                .userId(travelPlan.getUser().getId())
                .userName(travelPlan.getUser().getNickname())
                .userProfilePicture(travelPlan.getUser().getProfileImageUrl())
                .title(travelPlan.getTitle())
                .description(travelPlan.getDescription())
                .destination(travelPlan.getDestination())
                .startDate(travelPlan.getStartDate())
                .endDate(travelPlan.getEndDate())
                .status(travelPlan.getStatus().name())
                .isPublic(travelPlan.isPublic())
                .aiGenerated(travelPlan.isAiGenerated())
                .tags(travelPlan.getTags())
                .viewCount(travelPlan.getViewCount())
                .saveCount(travelPlan.getSaveCount())
                .collaboratorIds(travelPlan.getCollaborators().stream()
                    .map(User::getId)
                    .toList())
                .itineraryItems(Arrays.asList())
                .createdAt(travelPlan.getCreatedAt())
                .updatedAt(travelPlan.getUpdatedAt())
                .build();

            // Verify complex transformation
            assertEquals(travelPlan.getId(), response.getId());
            assertEquals(2, response.getCollaboratorIds().size());
            assertTrue(response.getCollaboratorIds().contains("collab-1"));
            assertTrue(response.getCollaboratorIds().contains("collab-2"));
            assertEquals(travelPlan.getTags(), response.getTags());
            assertEquals(travelPlan.getViewCount(), response.getViewCount());
            assertEquals(travelPlan.getSaveCount(), response.getSaveCount());

            // Verify response serializes correctly
            DTOValidationTestUtils.testSerialization(response, TravelPlanResponse.class);
        }
    }

    @Nested
    @DisplayName("Collection Transformation Tests")
    class CollectionTransformationTests {

        @Test
        @DisplayName("List of entities should transform to paginated response correctly")
        void entityListToPaginatedResponse() {
            List<User> users = Arrays.asList(
                User.builder()
                    .id("user-1")
                    .email("user1@example.com")
                    .nickname("user1")
                    .bio("First user")
                    .createdAt(LocalDateTime.now())
                    .build(),
                User.builder()
                    .id("user-2")
                    .email("user2@example.com")
                    .nickname("user2")
                    .bio("Second user")
                    .createdAt(LocalDateTime.now())
                    .build(),
                User.builder()
                    .id("user-3")
                    .email("user3@example.com")
                    .nickname("user3")
                    .bio("Third user")
                    .createdAt(LocalDateTime.now())
                    .build()
            );

            // Transform to UserProfileResponse list
            List<UserProfileResponse> userResponses = users.stream()
                .map(user -> UserProfileResponse.builder()
                    .id(user.getId())
                    .email(user.getEmail())
                    .name(user.getNickname())
                    .bio(user.getBio())
                    .followersCount(0)
                    .followingCount(0)
                    .travelPlansCount(0)
                    .reviewsCount(0)
                    .videosCount(0)
                    .isFollowing(false)
                    .createdAt(user.getCreatedAt())
                    .updatedAt(user.getUpdatedAt())
                    .build())
                .toList();

            // Create paginated response
            PageResponse<UserProfileResponse> pageResponse = PageResponse.<UserProfileResponse>builder()
                .content(userResponses)
                .pageNumber(0)
                .pageSize(10)
                .totalElements((long) users.size())
                .totalPages(1)
                .first(true)
                .last(true)
                .empty(false)
                .build();

            // Verify transformation
            assertEquals(3, pageResponse.getContent().size());
            assertEquals(0, pageResponse.getPageNumber());
            assertEquals(10, pageResponse.getPageSize());
            assertEquals(3L, pageResponse.getTotalElements());
            assertEquals(1, pageResponse.getTotalPages());
            assertTrue(pageResponse.isFirst());
            assertTrue(pageResponse.isLast());
            assertFalse(pageResponse.isEmpty());

            // Verify individual transformations
            UserProfileResponse firstUser = pageResponse.getContent().get(0);
            assertEquals("user-1", firstUser.getId());
            assertEquals("user1@example.com", firstUser.getEmail());
            assertEquals("user1", firstUser.getName());
            assertEquals("First user", firstUser.getBio());

            // Verify response serializes correctly
            DTOValidationTestUtils.testSerialization(pageResponse, PageResponse.class);
        }

        @Test
        @DisplayName("Nested collection transformation should work correctly")
        void nestedCollectionTransformation() {
            // Create travel plan with itinerary items
            User user = User.builder()
                .id("user-123")
                .nickname("traveler")
                .build();

            List<ItineraryItem> itineraryItems = Arrays.asList(
                ItineraryItem.builder()
                    .dayNumber(1)
                    .sequence(1)
                    .title("Morning temple visit")
                    .notes("Morning temple visit")
                    .build(),
                ItineraryItem.builder()
                    .dayNumber(1)
                    .sequence(2)
                    .title("Lunch at market")
                    .notes("Lunch at market")
                    .build()
            );

            TravelPlan travelPlan = TravelPlan.builder()
                .id("tp-123")
                .user(user)
                .title("Japan Trip")
                .destination("Japan")
                .startDate(LocalDate.of(2024, 6, 1))
                .endDate(LocalDate.of(2024, 6, 7))
                .status(TravelPlanStatus.CONFIRMED)
                .isPublic(true)
                .itineraryItems(itineraryItems)
                .tags(Arrays.asList("culture", "food"))
                .viewCount(100L)
                .saveCount(10L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

            // Transform to response with nested collections
            List<ItineraryItemResponse> itemResponses = travelPlan.getItineraryItems().stream()
                .map(item -> ItineraryItemResponse.builder()
                    .id(item.getId())
                    .dayNumber(item.getDayNumber())
                    .order(item.getSequence())
                    .notes(item.getNotes())
                    .build())
                .toList();

            TravelPlanResponse response = TravelPlanResponse.builder()
                .id(travelPlan.getId())
                .userId(travelPlan.getUser().getId())
                .userName(travelPlan.getUser().getNickname())
                .title(travelPlan.getTitle())
                .destination(travelPlan.getDestination())
                .startDate(travelPlan.getStartDate())
                .endDate(travelPlan.getEndDate())
                .status(travelPlan.getStatus().name())
                .isPublic(travelPlan.isPublic())
                .tags(travelPlan.getTags())
                .viewCount(travelPlan.getViewCount())
                .saveCount(travelPlan.getSaveCount())
                .itineraryItems(itemResponses)
                .collaboratorIds(Arrays.asList())
                .createdAt(travelPlan.getCreatedAt())
                .updatedAt(travelPlan.getUpdatedAt())
                .build();

            // Verify nested collection transformation
            assertEquals(2, response.getItineraryItems().size());
            assertEquals("item-1", response.getItineraryItems().get(0).getId());
            assertEquals(Integer.valueOf(1), response.getItineraryItems().get(0).getDayNumber());
            assertEquals("Morning temple visit", response.getItineraryItems().get(0).getNotes());

            assertEquals("item-2", response.getItineraryItems().get(1).getId());
            assertEquals(Integer.valueOf(1), response.getItineraryItems().get(1).getDayNumber());
            assertEquals("Lunch at market", response.getItineraryItems().get(1).getNotes());

            // Verify tags transformation
            assertEquals(2, response.getTags().size());
            assertTrue(response.getTags().contains("culture"));
            assertTrue(response.getTags().contains("food"));

            DTOValidationTestUtils.testSerialization(response, TravelPlanResponse.class);
        }
    }

    @Nested
    @DisplayName("Error Case Transformation Tests")
    class ErrorCaseTransformationTests {

        @Test
        @DisplayName("Null entity fields should be handled gracefully in DTO transformation")
        void nullEntityFieldsHandling() {
            // User with minimal data (many null fields)
            User user = User.builder()
                .id("user-123")
                .email("test@example.com")
                .nickname("testuser")
                .bio(null)
                .profileImageUrl(null)
                .preferences(new HashMap<>())
                .createdAt(LocalDateTime.now())
                .updatedAt(null) // Null updatedAt
                .build();

            // Transform to response, handling nulls
            UserProfileResponse response = UserProfileResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .name(user.getNickname())
                .bio(user.getBio()) // null
                .profilePicture(user.getProfileImageUrl()) // null
                .phoneNumber(user.getPreferences().get("phoneNumber")) // null
                .preferredLanguage(user.getPreferences().get("language")) // null
                .timezone(user.getPreferences().get("timezone")) // null
                .notificationsEnabled(null)
                .isPublic(null)
                .followersCount(0)
                .followingCount(0)
                .travelPlansCount(0)
                .reviewsCount(0)
                .videosCount(0)
                .isFollowing(false)
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt()) // null
                .build();

            // Verify null handling
            assertEquals(user.getId(), response.getId());
            assertEquals(user.getEmail(), response.getEmail());
            assertEquals(user.getNickname(), response.getName());
            assertNull(response.getBio());
            assertNull(response.getProfilePicture());
            assertNull(response.getPhoneNumber());
            assertNull(response.getPreferredLanguage());
            assertNull(response.getTimezone());
            assertNull(response.getNotificationsEnabled());
            assertNull(response.getIsPublic());
            assertNull(response.getUpdatedAt());

            // Should still be a valid DTO
            DTOValidationTestUtils.assertValidDTO(response);
            DTOValidationTestUtils.testSerialization(response, UserProfileResponse.class);
        }

        @Test
        @DisplayName("Empty collections should be handled correctly in transformations")
        void emptyCollectionsHandling() {
            TravelPlan travelPlan = TravelPlan.builder()
                .id("tp-123")
                .user(User.builder().id("user-123").nickname("user").build())
                .title("Empty Plan")
                .destination("Nowhere")
                .startDate(LocalDate.now().plusDays(1))
                .endDate(LocalDate.now().plusDays(2))
                .status(TravelPlanStatus.DRAFT)
                .isPublic(false)
                .tags(Arrays.asList()) // Empty list
                .itineraryItems(Arrays.asList()) // Empty list
                .collaborators(Arrays.asList()) // Empty list
                .viewCount(0L)
                .saveCount(0L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

            // Transform with empty collections
            TravelPlanResponse response = TravelPlanResponse.builder()
                .id(travelPlan.getId())
                .userId(travelPlan.getUser().getId())
                .userName(travelPlan.getUser().getNickname())
                .title(travelPlan.getTitle())
                .destination(travelPlan.getDestination())
                .startDate(travelPlan.getStartDate())
                .endDate(travelPlan.getEndDate())
                .status(travelPlan.getStatus().name())
                .isPublic(travelPlan.isPublic())
                .tags(travelPlan.getTags()) // Empty list
                .viewCount(travelPlan.getViewCount())
                .saveCount(travelPlan.getSaveCount())
                .itineraryItems(travelPlan.getItineraryItems().stream()
                    .map(item -> ItineraryItemResponse.builder()
                        .id(item.getId())
                        .build())
                    .toList()) // Empty list
                .collaboratorIds(travelPlan.getCollaborators().stream()
                    .map(User::getId)
                    .toList()) // Empty list
                .createdAt(travelPlan.getCreatedAt())
                .updatedAt(travelPlan.getUpdatedAt())
                .build();

            // Verify empty collections
            assertNotNull(response.getTags());
            assertTrue(response.getTags().isEmpty());
            assertNotNull(response.getItineraryItems());
            assertTrue(response.getItineraryItems().isEmpty());
            assertNotNull(response.getCollaboratorIds());
            assertTrue(response.getCollaboratorIds().isEmpty());

            DTOValidationTestUtils.testSerialization(response, TravelPlanResponse.class);
        }

        @Test
        @DisplayName("Transformation with validation violations should be detected")
        void transformationWithValidationViolations() {
            // Create invalid request (should fail validation)
            CreateTravelPlanRequest invalidRequest = CreateTravelPlanRequest.builder()
                .title("") // Invalid - empty title
                .description("A".repeat(2001)) // Invalid - too long description
                .destination("") // Invalid - empty destination
                .startDate(LocalDate.now().minusDays(1)) // Invalid - past date
                .endDate(null) // Invalid - null end date
                .build();

            // Verify request fails validation
            DTOValidationTestUtils.assertViolationCount(invalidRequest, 5);

            // However, entity can still be created (business logic would prevent this)
            User user = User.builder().id("user-123").nickname("user").build();
            
            TravelPlan travelPlan = TravelPlan.builder()
                .id("tp-invalid")
                .user(user)
                .title(invalidRequest.getTitle().isEmpty() ? "Default Title" : invalidRequest.getTitle())
                .description(invalidRequest.getDescription().length() > 1000 ? 
                    invalidRequest.getDescription().substring(0, 1000) : invalidRequest.getDescription())
                .destination(invalidRequest.getDestination().isEmpty() ? "Default Destination" : invalidRequest.getDestination())
                .startDate(invalidRequest.getStartDate().isBefore(LocalDate.now()) ? 
                    LocalDate.now().plusDays(1) : invalidRequest.getStartDate())
                .endDate(invalidRequest.getEndDate() != null ? 
                    invalidRequest.getEndDate() : LocalDate.now().plusDays(7))
                .status(TravelPlanStatus.DRAFT)
                .isPublic(false)
                .viewCount(0L)
                .saveCount(0L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

            // Entity should have corrected values
            assertEquals("Default Title", travelPlan.getTitle());
            assertEquals(1000, travelPlan.getDescription().length());
            assertEquals("Default Destination", travelPlan.getDestination());
            assertTrue(travelPlan.getStartDate().isAfter(LocalDate.now()));
            assertNotNull(travelPlan.getEndDate());
        }
    }

    @Nested
    @DisplayName("Comprehensive Integration Tests")
    class ComprehensiveIntegrationTests {

        @Test
        @DisplayName("Full request-to-entity-to-response cycle should maintain data integrity")
        void fullTransformationCycle() {
            // 1. Start with a request
            CreateTravelPlanRequest request = CreateTravelPlanRequest.builder()
                .title("Full Cycle Test")
                .description("Testing full transformation cycle")
                .destination("Test Destination")
                .startDate(LocalDate.of(2024, 8, 1))
                .endDate(LocalDate.of(2024, 8, 7))
                .isPublic(true)
                .aiGenerated(false)
                .imageUrl("https://example.com/image.jpg")
                .tags(Arrays.asList("test", "integration"))
                .build();

            // Validate request
            DTOValidationTestUtils.assertValidDTO(request);

            // 2. Transform to entity
            User user = User.builder()
                .id("user-cycle")
                .nickname("cycleuser")
                .build();

            TravelPlan entity = TravelPlan.builder()
                .id("tp-cycle")
                .user(user)
                .title(request.getTitle())
                .description(request.getDescription())
                .destination(request.getDestination())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .isPublic(request.getIsPublic())
                .isAiGenerated(request.getAiGenerated())
                .coverImageUrl(request.getImageUrl())
                .tags(request.getTags())
                .status(TravelPlanStatus.DRAFT)
                .viewCount(0L)
                .saveCount(0L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

            // 3. Transform back to response
            TravelPlanResponse response = TravelPlanResponse.builder()
                .id(entity.getId())
                .userId(entity.getUser().getId())
                .userName(entity.getUser().getNickname())
                .title(entity.getTitle())
                .description(entity.getDescription())
                .destination(entity.getDestination())
                .startDate(entity.getStartDate())
                .endDate(entity.getEndDate())
                .status(entity.getStatus().name())
                .isPublic(entity.isPublic())
                .aiGenerated(entity.isAiGenerated())
                .imageUrl(entity.getCoverImageUrl())
                .tags(entity.getTags())
                .viewCount(entity.getViewCount())
                .saveCount(entity.getSaveCount())
                .itineraryItems(Arrays.asList())
                .collaboratorIds(Arrays.asList())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();

            // 4. Verify data integrity throughout cycle
            // Request -> Entity
            assertEquals(request.getTitle(), entity.getTitle());
            assertEquals(request.getDescription(), entity.getDescription());
            assertEquals(request.getDestination(), entity.getDestination());
            assertEquals(request.getStartDate(), entity.getStartDate());
            assertEquals(request.getEndDate(), entity.getEndDate());
            assertEquals(request.getIsPublic(), entity.isPublic());
            assertEquals(request.getAiGenerated(), entity.isAiGenerated());
            assertEquals(request.getImageUrl(), entity.getCoverImageUrl());
            assertEquals(request.getTags(), entity.getTags());

            // Entity -> Response
            assertEquals(entity.getTitle(), response.getTitle());
            assertEquals(entity.getDescription(), response.getDescription());
            assertEquals(entity.getDestination(), response.getDestination());
            assertEquals(entity.getStartDate(), response.getStartDate());
            assertEquals(entity.getEndDate(), response.getEndDate());
            assertEquals(entity.isPublic(), response.getIsPublic());
            assertEquals(entity.isAiGenerated(), response.getAiGenerated());
            assertEquals(entity.getCoverImageUrl(), response.getImageUrl());
            assertEquals(entity.getTags(), response.getTags());

            // Request -> Response (end-to-end)
            assertEquals(request.getTitle(), response.getTitle());
            assertEquals(request.getDescription(), response.getDescription());
            assertEquals(request.getDestination(), response.getDestination());
            assertEquals(request.getStartDate(), response.getStartDate());
            assertEquals(request.getEndDate(), response.getEndDate());
            assertEquals(request.getIsPublic(), response.getIsPublic());
            assertEquals(request.getAiGenerated(), response.getAiGenerated());
            assertEquals(request.getImageUrl(), response.getImageUrl());
            assertEquals(request.getTags(), response.getTags());

            // Validate final response
            DTOValidationTestUtils.assertValidDTO(response);
            DTOValidationTestUtils.testSerialization(response, TravelPlanResponse.class);
        }

        @Test
        @DisplayName("API Response wrapper transformations should work correctly")
        void apiResponseWrapperTransformations() {
            // Success response with user data
            User user = User.builder()
                .id("user-123")
                .email("test@example.com")
                .nickname("testuser")
                .bio("Test bio")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

            UserProfileResponse userResponse = UserProfileResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .name(user.getNickname())
                .bio(user.getBio())
                .followersCount(0)
                .followingCount(0)
                .travelPlansCount(0)
                .reviewsCount(0)
                .videosCount(0)
                .isFollowing(false)
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();

            ApiResponse<UserProfileResponse> successResponse = ApiResponse.success(userResponse);

            // Verify success response
            assertTrue(successResponse.isSuccess());
            assertEquals(userResponse, successResponse.getData());
            assertNotNull(successResponse.getMeta());
            assertNull(successResponse.getError());

            // Error response
            ApiResponse<Object> errorResponse = ApiResponse.error("USER_NOT_FOUND", "User not found");

            // Verify error response
            assertFalse(errorResponse.isSuccess());
            assertNull(errorResponse.getData());
            assertNull(errorResponse.getMeta());
            assertNotNull(errorResponse.getError());
            assertEquals("USER_NOT_FOUND", errorResponse.getError().getCode());
            assertEquals("User not found", errorResponse.getError().getMessage());

            // Test serialization of both
            DTOValidationTestUtils.testSerialization(successResponse, ApiResponse.class);
            DTOValidationTestUtils.testSerialization(errorResponse, ApiResponse.class);
        }
    }
}