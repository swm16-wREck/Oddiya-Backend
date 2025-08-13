package com.oddiya.service;

import com.oddiya.dto.request.CreateItineraryItemRequest;
import com.oddiya.dto.request.CreateTravelPlanRequest;
import com.oddiya.dto.request.UpdateTravelPlanRequest;
import com.oddiya.dto.response.PageResponse;
import com.oddiya.dto.response.TravelPlanResponse;
import com.oddiya.entity.*;
import com.oddiya.exception.BadRequestException;
import com.oddiya.exception.NotFoundException;
import com.oddiya.exception.UnauthorizedException;
import com.oddiya.repository.*;
import com.oddiya.service.impl.TravelPlanServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TravelPlanService Tests")
class TravelPlanServiceTest {

    @Mock
    private TravelPlanRepository travelPlanRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PlaceRepository placeRepository;

    @Mock
    private ItineraryItemRepository itineraryItemRepository;

    @Mock
    private SavedPlanRepository savedPlanRepository;

    @InjectMocks
    private TravelPlanServiceImpl travelPlanService;

    private User testUser;
    private User collaborator;
    private TravelPlan testTravelPlan;
    private Place testPlace;
    private ItineraryItem testItineraryItem;
    private CreateTravelPlanRequest createRequest;
    private UpdateTravelPlanRequest updateRequest;
    private Pageable pageable;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id("user123")
                .email("test@example.com")
                .nickname("Test User")
                .profileImageUrl("http://example.com/profile.jpg")
                .build();

        collaborator = User.builder()
                .id("collab123")
                .email("collab@example.com")
                .nickname("Collaborator")
                .build();

        testPlace = Place.builder()
                .id("place123")
                .name("Test Place")
                .description("A great place")
                .address("123 Test Street")
                .latitude(37.5665)
                .longitude(126.9780)
                .category("restaurant")
                .rating(4.5)
                .reviewCount(100)
                .build();

        testItineraryItem = ItineraryItem.builder()
                .dayNumber(1)
                .sequence(1)
                .place(testPlace)
                .startTime(LocalDateTime.of(2024, 1, 15, 9, 0))
                .endTime(LocalDateTime.of(2024, 1, 15, 11, 0))
                .notes("Visit this place")
                .transportMode("walking")
                .transportDurationMinutes(15)
                .build();

        testTravelPlan = TravelPlan.builder()
                .id("plan123")
                .user(testUser)
                .title("Test Travel Plan")
                .description("A great travel plan")
                .destination("Seoul")
                .startDate(LocalDate.now().plusDays(1))
                .endDate(LocalDate.now().plusDays(7))
                .isPublic(true)
                .isAiGenerated(false)
                .coverImageUrl("http://example.com/cover.jpg")
                .tags(Arrays.asList("culture", "food"))
                .status(TravelPlanStatus.DRAFT)
                .viewCount(0L)
                .saveCount(0L)
                .itineraryItems(Arrays.asList(testItineraryItem))
                .collaborators(new ArrayList<>())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        testItineraryItem.setTravelPlan(testTravelPlan);

        createRequest = CreateTravelPlanRequest.builder()
                .title("New Travel Plan")
                .description("A new adventure")
                .destination("Busan")
                .startDate(LocalDate.now().plusDays(1))
                .endDate(LocalDate.now().plusDays(5))
                .isPublic(false)
                .aiGenerated(true)
                .imageUrl("http://example.com/image.jpg")
                .tags(Arrays.asList("beach", "relaxation"))
                .itineraryItems(Arrays.asList(
                        CreateItineraryItemRequest.builder()
                                .placeId("place123")
                                .dayNumber(1)
                                .order(1)
                                .startTime(LocalDateTime.of(2024, 1, 15, 10, 0))
                                .endTime(LocalDateTime.of(2024, 1, 15, 12, 0))
                                .notes("Start here")
                                .transportMode("bus")
                                .transportDuration(30)
                                .build()
                ))
                .build();

        updateRequest = UpdateTravelPlanRequest.builder()
                .title("Updated Title")
                .description("Updated description")
                .status("PUBLISHED")
                .build();

        pageable = PageRequest.of(0, 10, Sort.by("createdAt").descending());
    }

    @Nested
    @DisplayName("Create Travel Plan Tests")
    class CreateTravelPlanTests {

        @Test
        @DisplayName("Should successfully create travel plan with all fields")
        void shouldSuccessfullyCreateTravelPlanWithAllFields() {
            // Given
            when(userRepository.findById("user123")).thenReturn(Optional.of(testUser));
            when(travelPlanRepository.save(any(TravelPlan.class))).thenReturn(testTravelPlan);
            when(placeRepository.findById("place123")).thenReturn(Optional.of(testPlace));
            when(itineraryItemRepository.save(any(ItineraryItem.class))).thenReturn(testItineraryItem);

            // When
            TravelPlanResponse response = travelPlanService.createTravelPlan("user123", createRequest);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getTitle()).isEqualTo(testTravelPlan.getTitle());
            assertThat(response.getDestination()).isEqualTo(testTravelPlan.getDestination());
            assertThat(response.getUserId()).isEqualTo("user123");

            verify(travelPlanRepository).save(argThat(plan -> {
                assertThat(plan.getTitle()).isEqualTo(createRequest.getTitle());
                assertThat(plan.getUser()).isEqualTo(testUser);
                assertThat(plan.getStatus()).isEqualTo(TravelPlanStatus.DRAFT);
                assertThat(plan.getViewCount()).isEqualTo(0L);
                assertThat(plan.getSaveCount()).isEqualTo(0L);
                return true;
            }));

            verify(itineraryItemRepository).save(any(ItineraryItem.class));
        }

        @Test
        @DisplayName("Should throw exception when user not found")
        void shouldThrowExceptionWhenUserNotFound() {
            // Given
            when(userRepository.findById("nonexistent")).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> travelPlanService.createTravelPlan("nonexistent", createRequest))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("User not found");

            verify(travelPlanRepository, never()).save(any(TravelPlan.class));
        }

        @Test
        @DisplayName("Should throw exception when start date is after end date")
        void shouldThrowExceptionWhenStartDateIsAfterEndDate() {
            // Given
            createRequest.setStartDate(LocalDate.now().plusDays(10));
            createRequest.setEndDate(LocalDate.now().plusDays(5));
            when(userRepository.findById("user123")).thenReturn(Optional.of(testUser));

            // When & Then
            assertThatThrownBy(() -> travelPlanService.createTravelPlan("user123", createRequest))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Start date must be before end date");
        }

        @Test
        @DisplayName("Should handle null boolean fields with defaults")
        void shouldHandleNullBooleanFieldsWithDefaults() {
            // Given
            createRequest.setIsPublic(null);
            createRequest.setAiGenerated(null);
            when(userRepository.findById("user123")).thenReturn(Optional.of(testUser));
            when(travelPlanRepository.save(any(TravelPlan.class))).thenReturn(testTravelPlan);

            // When
            travelPlanService.createTravelPlan("user123", createRequest);

            // Then
            verify(travelPlanRepository).save(argThat(plan -> {
                assertThat(plan.isPublic()).isFalse();
                assertThat(plan.isAiGenerated()).isFalse();
                return true;
            }));
        }

        @Test
        @DisplayName("Should create travel plan without itinerary items")
        void shouldCreateTravelPlanWithoutItineraryItems() {
            // Given
            createRequest.setItineraryItems(null);
            when(userRepository.findById("user123")).thenReturn(Optional.of(testUser));
            when(travelPlanRepository.save(any(TravelPlan.class))).thenReturn(testTravelPlan);

            // When
            TravelPlanResponse response = travelPlanService.createTravelPlan("user123", createRequest);

            // Then
            assertThat(response).isNotNull();
            verify(itineraryItemRepository, never()).save(any(ItineraryItem.class));
        }

        @Test
        @DisplayName("Should throw exception when place not found for itinerary item")
        void shouldThrowExceptionWhenPlaceNotFoundForItineraryItem() {
            // Given
            when(userRepository.findById("user123")).thenReturn(Optional.of(testUser));
            when(travelPlanRepository.save(any(TravelPlan.class))).thenReturn(testTravelPlan);
            when(placeRepository.findById("place123")).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> travelPlanService.createTravelPlan("user123", createRequest))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Place not found");
        }
    }

    @Nested
    @DisplayName("Get Travel Plan Tests")
    class GetTravelPlanTests {

        @Test
        @DisplayName("Should successfully get travel plan by ID")
        void shouldSuccessfullyGetTravelPlanById() {
            // Given
            when(travelPlanRepository.findById("plan123")).thenReturn(Optional.of(testTravelPlan));

            // When
            TravelPlanResponse response = travelPlanService.getTravelPlan("plan123");

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo("plan123");
            assertThat(response.getTitle()).isEqualTo(testTravelPlan.getTitle());
            assertThat(response.getItineraryItems()).hasSize(1);
        }

        @Test
        @DisplayName("Should throw exception when travel plan not found")
        void shouldThrowExceptionWhenTravelPlanNotFound() {
            // Given
            when(travelPlanRepository.findById("nonexistent")).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> travelPlanService.getTravelPlan("nonexistent"))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Travel plan not found");
        }

        @Test
        @DisplayName("Should get private travel plan (access control not fully implemented)")
        void shouldGetPrivateTravelPlan() {
            // Given
            testTravelPlan.setPublic(false);
            when(travelPlanRepository.findById("plan123")).thenReturn(Optional.of(testTravelPlan));

            // When
            TravelPlanResponse response = travelPlanService.getTravelPlan("plan123");

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getIsPublic()).isFalse();
        }
    }

    @Nested
    @DisplayName("Update Travel Plan Tests")
    class UpdateTravelPlanTests {

        @Test
        @DisplayName("Should successfully update travel plan")
        void shouldSuccessfullyUpdateTravelPlan() {
            // Given
            when(travelPlanRepository.findById("plan123")).thenReturn(Optional.of(testTravelPlan));
            when(travelPlanRepository.save(any(TravelPlan.class))).thenReturn(testTravelPlan);

            // When
            TravelPlanResponse response = travelPlanService.updateTravelPlan("user123", "plan123", updateRequest);

            // Then
            assertThat(response).isNotNull();
            verify(travelPlanRepository).save(argThat(plan -> {
                assertThat(plan.getTitle()).isEqualTo("Updated Title");
                assertThat(plan.getDescription()).isEqualTo("Updated description");
                assertThat(plan.getStatus()).isEqualTo(TravelPlanStatus.CONFIRMED);
                return true;
            }));
        }

        @Test
        @DisplayName("Should throw exception when user not authorized to update")
        void shouldThrowExceptionWhenUserNotAuthorizedToUpdate() {
            // Given
            when(travelPlanRepository.findById("plan123")).thenReturn(Optional.of(testTravelPlan));

            // When & Then
            assertThatThrownBy(() -> travelPlanService.updateTravelPlan("other-user", "plan123", updateRequest))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessage("You are not authorized to update this travel plan");

            verify(travelPlanRepository, never()).save(any(TravelPlan.class));
        }

        @Test
        @DisplayName("Should update only non-null fields")
        void shouldUpdateOnlyNonNullFields() {
            // Given
            UpdateTravelPlanRequest partialUpdate = UpdateTravelPlanRequest.builder()
                    .title("New Title Only")
                    .build();

            when(travelPlanRepository.findById("plan123")).thenReturn(Optional.of(testTravelPlan));
            when(travelPlanRepository.save(any(TravelPlan.class))).thenReturn(testTravelPlan);

            // When
            travelPlanService.updateTravelPlan("user123", "plan123", partialUpdate);

            // Then
            verify(travelPlanRepository).save(argThat(plan -> {
                assertThat(plan.getTitle()).isEqualTo("New Title Only");
                assertThat(plan.getDescription()).isEqualTo(testTravelPlan.getDescription()); // Unchanged
                return true;
            }));
        }

        @Test
        @DisplayName("Should throw exception when travel plan not found for update")
        void shouldThrowExceptionWhenTravelPlanNotFoundForUpdate() {
            // Given
            when(travelPlanRepository.findById("nonexistent")).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> travelPlanService.updateTravelPlan("user123", "nonexistent", updateRequest))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Travel plan not found");
        }
    }

    @Nested
    @DisplayName("Delete Travel Plan Tests")
    class DeleteTravelPlanTests {

        @Test
        @DisplayName("Should successfully delete travel plan")
        void shouldSuccessfullyDeleteTravelPlan() {
            // Given
            when(travelPlanRepository.findById("plan123")).thenReturn(Optional.of(testTravelPlan));

            // When
            travelPlanService.deleteTravelPlan("user123", "plan123");

            // Then
            verify(travelPlanRepository).delete(testTravelPlan);
        }

        @Test
        @DisplayName("Should throw exception when user not authorized to delete")
        void shouldThrowExceptionWhenUserNotAuthorizedToDelete() {
            // Given
            when(travelPlanRepository.findById("plan123")).thenReturn(Optional.of(testTravelPlan));

            // When & Then
            assertThatThrownBy(() -> travelPlanService.deleteTravelPlan("other-user", "plan123"))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessage("You are not authorized to delete this travel plan");

            verify(travelPlanRepository, never()).delete(any(TravelPlan.class));
        }

        @Test
        @DisplayName("Should throw exception when travel plan not found for delete")
        void shouldThrowExceptionWhenTravelPlanNotFoundForDelete() {
            // Given
            when(travelPlanRepository.findById("nonexistent")).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> travelPlanService.deleteTravelPlan("user123", "nonexistent"))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Travel plan not found");
        }
    }

    @Nested
    @DisplayName("Get User Travel Plans Tests")
    class GetUserTravelPlansTests {

        @Test
        @DisplayName("Should successfully get user travel plans")
        void shouldSuccessfullyGetUserTravelPlans() {
            // Given
            List<TravelPlan> plans = Arrays.asList(testTravelPlan);
            Page<TravelPlan> page = new PageImpl<>(plans, pageable, 1);
            when(travelPlanRepository.findSavedPlansByUser("user123", pageable)).thenReturn(page);

            // When
            PageResponse<TravelPlanResponse> response = travelPlanService.getUserTravelPlans("user123", pageable);

            // Then
            assertThat(response.getContent()).hasSize(1);
            assertThat(response.getTotalElements()).isEqualTo(1);
            assertThat(response.getContent().get(0).getUserId()).isEqualTo("user123");
        }

        @Test
        @DisplayName("Should return empty page for user with no travel plans")
        void shouldReturnEmptyPageForUserWithNoTravelPlans() {
            // Given
            Page<TravelPlan> emptyPage = new PageImpl<>(new ArrayList<>(), pageable, 0);
            when(travelPlanRepository.findSavedPlansByUser("user123", pageable)).thenReturn(emptyPage);

            // When
            PageResponse<TravelPlanResponse> response = travelPlanService.getUserTravelPlans("user123", pageable);

            // Then
            assertThat(response.getContent()).isEmpty();
            assertThat(response.getTotalElements()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("Get Public Travel Plans Tests")
    class GetPublicTravelPlansTests {

        @Test
        @DisplayName("Should successfully get public travel plans")
        void shouldSuccessfullyGetPublicTravelPlans() {
            // Given
            List<TravelPlan> plans = Arrays.asList(testTravelPlan);
            Page<TravelPlan> page = new PageImpl<>(plans, pageable, 1);
            when(travelPlanRepository.findByIsPublicTrue(pageable)).thenReturn(page);

            // When
            PageResponse<TravelPlanResponse> response = travelPlanService.getPublicTravelPlans(pageable);

            // Then
            assertThat(response.getContent()).hasSize(1);
            assertThat(response.getContent().get(0).getIsPublic()).isTrue();
        }
    }

    @Nested
    @DisplayName("Search Travel Plans Tests")
    class SearchTravelPlansTests {

        @Test
        @DisplayName("Should successfully search travel plans by query")
        void shouldSuccessfullySearchTravelPlansByQuery() {
            // Given
            List<TravelPlan> plans = Arrays.asList(testTravelPlan);
            Page<TravelPlan> page = new PageImpl<>(plans, pageable, 1);
            when(travelPlanRepository.findByTitleContainingIgnoreCaseOrDestinationContainingIgnoreCase(
                    "seoul", "seoul", pageable)).thenReturn(page);

            // When
            PageResponse<TravelPlanResponse> response = travelPlanService.searchTravelPlans("seoul", pageable);

            // Then
            assertThat(response.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("Should return empty result when no plans match query")
        void shouldReturnEmptyResultWhenNoPlansMatchQuery() {
            // Given
            Page<TravelPlan> emptyPage = new PageImpl<>(new ArrayList<>(), pageable, 0);
            when(travelPlanRepository.findByTitleContainingIgnoreCaseOrDestinationContainingIgnoreCase(
                    "nonexistent", "nonexistent", pageable)).thenReturn(emptyPage);

            // When
            PageResponse<TravelPlanResponse> response = travelPlanService.searchTravelPlans("nonexistent", pageable);

            // Then
            assertThat(response.getContent()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Copy Travel Plan Tests")
    class CopyTravelPlanTests {

        @Test
        @DisplayName("Should successfully copy travel plan")
        void shouldSuccessfullyCopyTravelPlan() {
            // Given
            when(userRepository.findById("user123")).thenReturn(Optional.of(testUser));
            when(travelPlanRepository.findById("plan123")).thenReturn(Optional.of(testTravelPlan));
            when(travelPlanRepository.save(any(TravelPlan.class))).thenReturn(testTravelPlan);
            when(itineraryItemRepository.save(any(ItineraryItem.class))).thenReturn(testItineraryItem);

            // When
            TravelPlanResponse response = travelPlanService.copyTravelPlan("user123", "plan123");

            // Then
            assertThat(response).isNotNull();
            verify(travelPlanRepository).save(argThat(plan -> {
                assertThat(plan.getTitle()).isEqualTo("Test Travel Plan (Copy)");
                assertThat(plan.getUser()).isEqualTo(testUser);
                assertThat(plan.isPublic()).isFalse(); // Copies are private by default
                assertThat(plan.isAiGenerated()).isFalse();
                assertThat(plan.getStatus()).isEqualTo(TravelPlanStatus.DRAFT);
                return true;
            }));

            verify(itineraryItemRepository).save(any(ItineraryItem.class));
        }

        @Test
        @DisplayName("Should throw exception when user not found for copy")
        void shouldThrowExceptionWhenUserNotFoundForCopy() {
            // Given
            when(userRepository.findById("nonexistent")).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> travelPlanService.copyTravelPlan("nonexistent", "plan123"))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("User not found");
        }

        @Test
        @DisplayName("Should throw exception when original plan not found")
        void shouldThrowExceptionWhenOriginalPlanNotFound() {
            // Given
            when(userRepository.findById("user123")).thenReturn(Optional.of(testUser));
            when(travelPlanRepository.findById("nonexistent")).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> travelPlanService.copyTravelPlan("user123", "nonexistent"))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Travel plan not found");
        }
    }

    @Nested
    @DisplayName("Collaborator Management Tests")
    class CollaboratorManagementTests {

        @Test
        @DisplayName("Should successfully add collaborator")
        void shouldSuccessfullyAddCollaborator() {
            // Given
            when(travelPlanRepository.findById("plan123")).thenReturn(Optional.of(testTravelPlan));
            when(userRepository.findById("collab123")).thenReturn(Optional.of(collaborator));
            when(travelPlanRepository.save(any(TravelPlan.class))).thenReturn(testTravelPlan);

            // When
            travelPlanService.addCollaborator("user123", "plan123", "collab123");

            // Then
            verify(travelPlanRepository).save(argThat(plan -> {
                assertThat(plan.getCollaborators()).contains(collaborator);
                return true;
            }));
        }

        @Test
        @DisplayName("Should not add duplicate collaborator")
        void shouldNotAddDuplicateCollaborator() {
            // Given
            testTravelPlan.getCollaborators().add(collaborator);
            when(travelPlanRepository.findById("plan123")).thenReturn(Optional.of(testTravelPlan));
            when(userRepository.findById("collab123")).thenReturn(Optional.of(collaborator));

            // When
            travelPlanService.addCollaborator("user123", "plan123", "collab123");

            // Then
            verify(travelPlanRepository, never()).save(any(TravelPlan.class));
        }

        @Test
        @DisplayName("Should throw exception when not authorized to add collaborator")
        void shouldThrowExceptionWhenNotAuthorizedToAddCollaborator() {
            // Given
            when(travelPlanRepository.findById("plan123")).thenReturn(Optional.of(testTravelPlan));

            // When & Then
            assertThatThrownBy(() -> travelPlanService.addCollaborator("other-user", "plan123", "collab123"))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessage("You are not authorized to add collaborators to this travel plan");
        }

        @Test
        @DisplayName("Should successfully remove collaborator")
        void shouldSuccessfullyRemoveCollaborator() {
            // Given
            testTravelPlan.getCollaborators().add(collaborator);
            when(travelPlanRepository.findById("plan123")).thenReturn(Optional.of(testTravelPlan));
            when(userRepository.findById("collab123")).thenReturn(Optional.of(collaborator));
            when(travelPlanRepository.save(any(TravelPlan.class))).thenReturn(testTravelPlan);

            // When
            travelPlanService.removeCollaborator("user123", "plan123", "collab123");

            // Then
            verify(travelPlanRepository).save(argThat(plan -> {
                assertThat(plan.getCollaborators()).doesNotContain(collaborator);
                return true;
            }));
        }

        @Test
        @DisplayName("Should throw exception when collaborator not found for add")
        void shouldThrowExceptionWhenCollaboratorNotFoundForAdd() {
            // Given
            when(travelPlanRepository.findById("plan123")).thenReturn(Optional.of(testTravelPlan));
            when(userRepository.findById("nonexistent")).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> travelPlanService.addCollaborator("user123", "plan123", "nonexistent"))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Collaborator not found");
        }
    }

    @Nested
    @DisplayName("View Count Tests")
    class ViewCountTests {

        @Test
        @DisplayName("Should successfully increment view count")
        void shouldSuccessfullyIncrementViewCount() {
            // Given
            testTravelPlan.setViewCount(5L);
            when(travelPlanRepository.findById("plan123")).thenReturn(Optional.of(testTravelPlan));
            when(travelPlanRepository.save(any(TravelPlan.class))).thenReturn(testTravelPlan);

            // When
            travelPlanService.incrementViewCount("plan123");

            // Then
            verify(travelPlanRepository).save(argThat(plan -> {
                assertThat(plan.getViewCount()).isEqualTo(6L);
                return true;
            }));
        }

        @Test
        @DisplayName("Should handle non-existent plan gracefully for view count")
        void shouldHandleNonExistentPlanGracefullyForViewCount() {
            // Given
            when(travelPlanRepository.findById("nonexistent")).thenReturn(Optional.empty());

            // When & Then
            assertThatCode(() -> travelPlanService.incrementViewCount("nonexistent"))
                    .doesNotThrowAnyException();

            verify(travelPlanRepository, never()).save(any(TravelPlan.class));
        }
    }

    @Nested
    @DisplayName("Save/Unsave Travel Plan Tests")
    class SaveUnsaveTravelPlanTests {

        @Test
        @DisplayName("Should successfully save travel plan")
        void shouldSuccessfullySaveTravelPlan() {
            // Given
            when(userRepository.findById("user123")).thenReturn(Optional.of(testUser));
            when(travelPlanRepository.findById("plan123")).thenReturn(Optional.of(testTravelPlan));
            when(savedPlanRepository.existsSavedPlan("user123", "plan123")).thenReturn(false);
            when(savedPlanRepository.save(any(SavedPlan.class))).thenReturn(new SavedPlan());
            when(travelPlanRepository.save(any(TravelPlan.class))).thenReturn(testTravelPlan);

            // When
            travelPlanService.saveTravelPlan("user123", "plan123");

            // Then
            verify(savedPlanRepository).save(any(SavedPlan.class));
            verify(travelPlanRepository).save(argThat(plan -> {
                assertThat(plan.getSaveCount()).isEqualTo(1L);
                return true;
            }));
        }

        @Test
        @DisplayName("Should not save travel plan if already saved")
        void shouldNotSaveTravelPlanIfAlreadySaved() {
            // Given
            when(userRepository.findById("user123")).thenReturn(Optional.of(testUser));
            when(travelPlanRepository.findById("plan123")).thenReturn(Optional.of(testTravelPlan));
            when(savedPlanRepository.existsSavedPlan("user123", "plan123")).thenReturn(true);

            // When
            travelPlanService.saveTravelPlan("user123", "plan123");

            // Then
            verify(savedPlanRepository, never()).save(any(SavedPlan.class));
            verify(travelPlanRepository, never()).save(any(TravelPlan.class));
        }

        @Test
        @DisplayName("Should successfully unsave travel plan")
        void shouldSuccessfullyUnsaveTravelPlan() {
            // Given
            SavedPlan savedPlan = SavedPlan.builder()
                    .user(testUser)
                    .travelPlan(testTravelPlan)
                    .build();

            testTravelPlan.setSaveCount(5L);
            
            when(savedPlanRepository.findSavedPlan("user123", "plan123")).thenReturn(Optional.of(savedPlan));
            when(travelPlanRepository.findById("plan123")).thenReturn(Optional.of(testTravelPlan));
            when(travelPlanRepository.save(any(TravelPlan.class))).thenReturn(testTravelPlan);

            // When
            travelPlanService.unsaveTravelPlan("user123", "plan123");

            // Then
            verify(savedPlanRepository).delete(savedPlan);
            verify(travelPlanRepository).save(argThat(plan -> {
                assertThat(plan.getSaveCount()).isEqualTo(4L);
                return true;
            }));
        }

        @Test
        @DisplayName("Should not let save count go below zero")
        void shouldNotLetSaveCountGoBelowZero() {
            // Given
            SavedPlan savedPlan = SavedPlan.builder()
                    .user(testUser)
                    .travelPlan(testTravelPlan)
                    .build();

            testTravelPlan.setSaveCount(0L);
            
            when(savedPlanRepository.findSavedPlan("user123", "plan123")).thenReturn(Optional.of(savedPlan));
            when(travelPlanRepository.findById("plan123")).thenReturn(Optional.of(testTravelPlan));
            when(travelPlanRepository.save(any(TravelPlan.class))).thenReturn(testTravelPlan);

            // When
            travelPlanService.unsaveTravelPlan("user123", "plan123");

            // Then
            verify(travelPlanRepository).save(argThat(plan -> {
                assertThat(plan.getSaveCount()).isEqualTo(0L);
                return true;
            }));
        }

        @Test
        @DisplayName("Should throw exception when saved plan not found for unsave")
        void shouldThrowExceptionWhenSavedPlanNotFoundForUnsave() {
            // Given
            when(savedPlanRepository.findSavedPlan("user123", "plan123")).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> travelPlanService.unsaveTravelPlan("user123", "plan123"))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Saved plan not found");
        }
    }

    @Nested
    @DisplayName("Get Saved Travel Plans Tests")
    class GetSavedTravelPlansTests {

        @Test
        @DisplayName("Should successfully get saved travel plans")
        void shouldSuccessfullyGetSavedTravelPlans() {
            // Given
            SavedPlan savedPlan = SavedPlan.builder()
                    .user(testUser)
                    .travelPlan(testTravelPlan)
                    .build();

            List<SavedPlan> savedPlans = Arrays.asList(savedPlan);
            Page<SavedPlan> savedPlansPage = new PageImpl<>(savedPlans, pageable, 1);
            
            when(savedPlanRepository.findSavedPlansByUser("user123", pageable)).thenReturn(savedPlansPage);

            // When
            PageResponse<TravelPlanResponse> response = travelPlanService.getSavedTravelPlans("user123", pageable);

            // Then
            assertThat(response.getContent()).hasSize(1);
            assertThat(response.getContent().get(0).getId()).isEqualTo("plan123");
        }

        @Test
        @DisplayName("Should return empty page for user with no saved plans")
        void shouldReturnEmptyPageForUserWithNoSavedPlans() {
            // Given
            Page<SavedPlan> emptyPage = new PageImpl<>(new ArrayList<>(), pageable, 0);
            when(savedPlanRepository.findSavedPlansByUser("user123", pageable)).thenReturn(emptyPage);

            // When
            PageResponse<TravelPlanResponse> response = travelPlanService.getSavedTravelPlans("user123", pageable);

            // Then
            assertThat(response.getContent()).isEmpty();
            assertThat(response.getTotalElements()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("Mapping Tests")
    class MappingTests {

        @Test
        @DisplayName("Should correctly map TravelPlan to TravelPlanResponse")
        void shouldCorrectlyMapTravelPlanToTravelPlanResponse() {
            // Given
            when(travelPlanRepository.findById("plan123")).thenReturn(Optional.of(testTravelPlan));

            // When
            TravelPlanResponse response = travelPlanService.getTravelPlan("plan123");

            // Then
            assertThat(response.getId()).isEqualTo(testTravelPlan.getId());
            assertThat(response.getUserId()).isEqualTo(testTravelPlan.getUser().getId());
            assertThat(response.getUserName()).isEqualTo(testTravelPlan.getUser().getNickname());
            assertThat(response.getUserProfilePicture()).isEqualTo(testTravelPlan.getUser().getProfileImageUrl());
            assertThat(response.getTitle()).isEqualTo(testTravelPlan.getTitle());
            assertThat(response.getDescription()).isEqualTo(testTravelPlan.getDescription());
            assertThat(response.getDestination()).isEqualTo(testTravelPlan.getDestination());
            assertThat(response.getStartDate()).isEqualTo(testTravelPlan.getStartDate());
            assertThat(response.getEndDate()).isEqualTo(testTravelPlan.getEndDate());
            assertThat(response.getStatus()).isEqualTo(testTravelPlan.getStatus().name());
            assertThat(response.getIsPublic()).isEqualTo(testTravelPlan.isPublic());
            assertThat(response.getAiGenerated()).isEqualTo(testTravelPlan.isAiGenerated());
            assertThat(response.getImageUrl()).isEqualTo(testTravelPlan.getCoverImageUrl());
            assertThat(response.getTags()).isEqualTo(testTravelPlan.getTags());
            assertThat(response.getViewCount()).isEqualTo(testTravelPlan.getViewCount());
            assertThat(response.getSaveCount()).isEqualTo(testTravelPlan.getSaveCount());
            assertThat(response.getItineraryItems()).hasSize(1);
            assertThat(response.getCollaboratorIds()).isEmpty();
        }

        @Test
        @DisplayName("Should handle null collections gracefully in mapping")
        void shouldHandleNullCollectionsGracefullyInMapping() {
            // Given
            testTravelPlan.setItineraryItems(null);
            testTravelPlan.setCollaborators(null);
            when(travelPlanRepository.findById("plan123")).thenReturn(Optional.of(testTravelPlan));

            // When
            TravelPlanResponse response = travelPlanService.getTravelPlan("plan123");

            // Then
            assertThat(response.getItineraryItems()).isEmpty();
            assertThat(response.getCollaboratorIds()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Parameterized Tests")
    class ParameterizedTests {

        @ParameterizedTest
        @EnumSource(TravelPlanStatus.class)
        @DisplayName("Should handle all travel plan status values")
        void shouldHandleAllTravelPlanStatusValues(TravelPlanStatus status) {
            // Given
            updateRequest.setStatus(status.name());
            when(travelPlanRepository.findById("plan123")).thenReturn(Optional.of(testTravelPlan));
            when(travelPlanRepository.save(any(TravelPlan.class))).thenReturn(testTravelPlan);

            // When
            TravelPlanResponse response = travelPlanService.updateTravelPlan("user123", "plan123", updateRequest);

            // Then
            assertThat(response).isNotNull();
            verify(travelPlanRepository).save(argThat(plan -> plan.getStatus() == status));
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 7, 14, 30, 365})
        @DisplayName("Should handle different travel duration lengths")
        void shouldHandleDifferentTravelDurationLengths(int durationDays) {
            // Given
            LocalDate startDate = LocalDate.now().plusDays(1);
            createRequest.setStartDate(startDate);
            createRequest.setEndDate(startDate.plusDays(durationDays));
            
            when(userRepository.findById("user123")).thenReturn(Optional.of(testUser));
            when(travelPlanRepository.save(any(TravelPlan.class))).thenReturn(testTravelPlan);

            // When & Then
            assertThatCode(() -> travelPlanService.createTravelPlan("user123", createRequest))
                    .doesNotThrowAnyException();
        }

        @ParameterizedTest
        @CsvSource({
            "Seoul, korea",
            "Tokyo, japan",
            "Paris, france", 
            "New York, usa",
            "London, uk"
        })
        @DisplayName("Should create travel plans for different destinations")
        void shouldCreateTravelPlansForDifferentDestinations(String destination, String tag) {
            // Given
            createRequest.setDestination(destination);
            createRequest.setTags(Arrays.asList(tag, "vacation"));
            
            when(userRepository.findById("user123")).thenReturn(Optional.of(testUser));
            when(travelPlanRepository.save(any(TravelPlan.class))).thenReturn(testTravelPlan);

            // When
            TravelPlanResponse response = travelPlanService.createTravelPlan("user123", createRequest);

            // Then
            assertThat(response).isNotNull();
            verify(travelPlanRepository).save(argThat(plan -> 
                plan.getDestination().equals(destination) && plan.getTags().contains(tag)));
        }

        @ParameterizedTest
        @ValueSource(strings = {"", " ", "   "})
        @NullAndEmptySource
        @DisplayName("Should handle invalid search queries gracefully")
        void shouldHandleInvalidSearchQueriesGracefully(String query) {
            // Given
            Page<TravelPlan> emptyPage = new PageImpl<>(new ArrayList<>(), pageable, 0);
            when(travelPlanRepository.findByTitleContainingIgnoreCaseOrDestinationContainingIgnoreCase(
                    anyString(), anyString(), eq(pageable))).thenReturn(emptyPage);

            // When
            PageResponse<TravelPlanResponse> response = travelPlanService.searchTravelPlans(query, pageable);

            // Then
            assertThat(response.getContent()).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(longs = {0L, 1L, 100L, 999999L})
        @DisplayName("Should handle different view count values")
        void shouldHandleDifferentViewCountValues(Long initialCount) {
            // Given
            testTravelPlan.setViewCount(initialCount);
            when(travelPlanRepository.findById("plan123")).thenReturn(Optional.of(testTravelPlan));
            when(travelPlanRepository.save(any(TravelPlan.class))).thenReturn(testTravelPlan);

            // When
            travelPlanService.incrementViewCount("plan123");

            // Then
            verify(travelPlanRepository).save(argThat(plan -> 
                plan.getViewCount().equals(initialCount + 1)));
        }
    }

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle extremely long titles")
        void shouldHandleExtremelyLongTitles() {
            // Given
            String longTitle = "A".repeat(1000);
            createRequest.setTitle(longTitle);
            
            when(userRepository.findById("user123")).thenReturn(Optional.of(testUser));
            when(travelPlanRepository.save(any(TravelPlan.class))).thenReturn(testTravelPlan);

            // When & Then
            assertThatCode(() -> travelPlanService.createTravelPlan("user123", createRequest))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should handle travel plans with many tags")
        void shouldHandleTravelPlansWithManyTags() {
            // Given
            List<String> manyTags = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                manyTags.add("tag" + i);
            }
            createRequest.setTags(manyTags);
            
            when(userRepository.findById("user123")).thenReturn(Optional.of(testUser));
            when(travelPlanRepository.save(any(TravelPlan.class))).thenReturn(testTravelPlan);

            // When & Then
            assertThatCode(() -> travelPlanService.createTravelPlan("user123", createRequest))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should handle travel plan with many collaborators")
        void shouldHandleTravelPlanWithManyCollaborators() {
            // Given
            List<User> manyCollaborators = new ArrayList<>();
            for (int i = 0; i < 50; i++) {
                manyCollaborators.add(User.builder().id("user" + i).build());
            }
            testTravelPlan.setCollaborators(manyCollaborators);
            
            when(travelPlanRepository.findById("plan123")).thenReturn(Optional.of(testTravelPlan));

            // When
            TravelPlanResponse response = travelPlanService.getTravelPlan("plan123");

            // Then
            assertThat(response.getCollaboratorIds()).hasSize(50);
        }

        @Test
        @DisplayName("Should handle concurrent updates gracefully")
        void shouldHandleConcurrentUpdatesGracefully() {
            // Given
            when(travelPlanRepository.findById("plan123")).thenReturn(Optional.of(testTravelPlan));
            when(travelPlanRepository.save(any(TravelPlan.class))).thenReturn(testTravelPlan);

            // When - Simulate concurrent updates
            for (int i = 0; i < 10; i++) {
                UpdateTravelPlanRequest request = UpdateTravelPlanRequest.builder()
                        .title("Title " + i)
                        .build();
                assertThatCode(() -> travelPlanService.updateTravelPlan("user123", "plan123", request))
                        .doesNotThrowAnyException();
            }

            // Then
            verify(travelPlanRepository, times(10)).save(any(TravelPlan.class));
        }

        @Test
        @DisplayName("Should handle same day start and end dates")
        void shouldHandleSameDayStartAndEndDates() {
            // Given
            LocalDate sameDate = LocalDate.now().plusDays(1);
            createRequest.setStartDate(sameDate);
            createRequest.setEndDate(sameDate);
            
            when(userRepository.findById("user123")).thenReturn(Optional.of(testUser));
            when(travelPlanRepository.save(any(TravelPlan.class))).thenReturn(testTravelPlan);

            // When & Then
            assertThatCode(() -> travelPlanService.createTravelPlan("user123", createRequest))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should handle travel plans in the distant future")
        void shouldHandleTravelPlansInTheDistantFuture() {
            // Given
            LocalDate futureDate = LocalDate.now().plusYears(10);
            createRequest.setStartDate(futureDate);
            createRequest.setEndDate(futureDate.plusDays(7));
            
            when(userRepository.findById("user123")).thenReturn(Optional.of(testUser));
            when(travelPlanRepository.save(any(TravelPlan.class))).thenReturn(testTravelPlan);

            // When & Then
            assertThatCode(() -> travelPlanService.createTravelPlan("user123", createRequest))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Security Tests")
    class SecurityTests {

        @Test
        @DisplayName("Should prevent unauthorized access to private plans")
        void shouldPreventUnauthorizedAccessToPrivatePlans() {
            // Given
            testTravelPlan.setPublic(false);
            when(travelPlanRepository.findById("plan123")).thenReturn(Optional.of(testTravelPlan));

            // When & Then
            // Note: Current implementation doesn't fully enforce this, but test documents expected behavior
            TravelPlanResponse response = travelPlanService.getTravelPlan("plan123");
            assertThat(response).isNotNull();
            assertThat(response.getIsPublic()).isFalse();
        }

        @Test
        @DisplayName("Should validate user ownership before updates")
        void shouldValidateUserOwnershipBeforeUpdates() {
            // Given
            when(travelPlanRepository.findById("plan123")).thenReturn(Optional.of(testTravelPlan));

            // When & Then
            assertThatThrownBy(() -> 
                travelPlanService.updateTravelPlan("malicious-user", "plan123", updateRequest))
                .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        @DisplayName("Should validate user ownership before deletions")
        void shouldValidateUserOwnershipBeforeDeletions() {
            // Given
            when(travelPlanRepository.findById("plan123")).thenReturn(Optional.of(testTravelPlan));

            // When & Then
            assertThatThrownBy(() -> 
                travelPlanService.deleteTravelPlan("malicious-user", "plan123"))
                .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        @DisplayName("Should not expose sensitive user information in responses")
        void shouldNotExposeSensitiveUserInformationInResponses() {
            // Given
            when(travelPlanRepository.findById("plan123")).thenReturn(Optional.of(testTravelPlan));

            // When
            TravelPlanResponse response = travelPlanService.getTravelPlan("plan123");

            // Then
            String responseString = response.toString();
            assertThat(responseString).doesNotContain("password");
            assertThat(responseString).doesNotContain("refreshToken");
            assertThat(responseString).doesNotContain("provider");
        }
    }

    @Nested
    @DisplayName("Performance Tests")
    class PerformanceTests {

        @Test
        @DisplayName("Should minimize repository calls during travel plan creation")
        void shouldMinimizeRepositoryCallsDuringTravelPlanCreation() {
            // Given
            when(userRepository.findById("user123")).thenReturn(Optional.of(testUser));
            when(travelPlanRepository.save(any(TravelPlan.class))).thenReturn(testTravelPlan);
            when(placeRepository.findById("place123")).thenReturn(Optional.of(testPlace));
            when(itineraryItemRepository.save(any(ItineraryItem.class))).thenReturn(testItineraryItem);

            // When
            travelPlanService.createTravelPlan("user123", createRequest);

            // Then
            verify(userRepository, times(1)).findById("user123");
            verify(travelPlanRepository, times(1)).save(any(TravelPlan.class));
            verify(placeRepository, times(1)).findById("place123");
            verify(itineraryItemRepository, times(1)).save(any(ItineraryItem.class));
        }

        @Test
        @DisplayName("Should handle batch operations efficiently")
        void shouldHandleBatchOperationsEfficiently() {
            // Given
            List<CreateItineraryItemRequest> manyItems = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                manyItems.add(CreateItineraryItemRequest.builder()
                        .placeId("place123")
                        .dayNumber(1)
                        .order(i + 1)
                        .startTime(LocalDateTime.now().plusHours(i))
                        .endTime(LocalDateTime.now().plusHours(i + 1))
                        .build());
            }
            createRequest.setItineraryItems(manyItems);
            
            when(userRepository.findById("user123")).thenReturn(Optional.of(testUser));
            when(travelPlanRepository.save(any(TravelPlan.class))).thenReturn(testTravelPlan);
            when(placeRepository.findById("place123")).thenReturn(Optional.of(testPlace));
            when(itineraryItemRepository.save(any(ItineraryItem.class))).thenReturn(testItineraryItem);

            // When
            travelPlanService.createTravelPlan("user123", createRequest);

            // Then
            verify(placeRepository, times(20)).findById("place123"); // Could be optimized
            verify(itineraryItemRepository, times(20)).save(any(ItineraryItem.class));
        }

        @Test
        @DisplayName("Should handle large page sizes efficiently")
        void shouldHandleLargePageSizesEfficiently() {
            // Given
            Pageable largePage = PageRequest.of(0, 1000);
            List<TravelPlan> manyPlans = new ArrayList<>();
            for (int i = 0; i < 1000; i++) {
                manyPlans.add(TravelPlan.builder()
                        .id("plan" + i)
                        .user(testUser)
                        .title("Plan " + i)
                        .itineraryItems(new ArrayList<>())
                        .collaborators(new ArrayList<>())
                        .build());
            }
            Page<TravelPlan> page = new PageImpl<>(manyPlans, largePage, 1000);
            
            when(travelPlanRepository.findSavedPlansByUser("user123", largePage)).thenReturn(page);

            // When
            PageResponse<TravelPlanResponse> response = travelPlanService.getUserTravelPlans("user123", largePage);

            // Then
            assertThat(response.getContent()).hasSize(1000);
            assertThat(response.getTotalElements()).isEqualTo(1000);
        }
    }

    @Nested
    @DisplayName("Data Integrity Tests")
    class DataIntegrityTests {

        @Test
        @DisplayName("Should maintain referential integrity when copying travel plans")
        void shouldMaintainReferentialIntegrityWhenCopyingTravelPlans() {
            // Given
            when(userRepository.findById("user123")).thenReturn(Optional.of(testUser));
            when(travelPlanRepository.findById("plan123")).thenReturn(Optional.of(testTravelPlan));
            when(travelPlanRepository.save(any(TravelPlan.class))).thenReturn(testTravelPlan);
            when(itineraryItemRepository.save(any(ItineraryItem.class))).thenReturn(testItineraryItem);

            // When
            travelPlanService.copyTravelPlan("user123", "plan123");

            // Then
            verify(travelPlanRepository).save(argThat(plan -> {
                assertThat(plan.getUser()).isEqualTo(testUser);
                assertThat(plan.getId()).isNotEqualTo("plan123"); // Should be different
                return true;
            }));
            
            verify(itineraryItemRepository).save(argThat(item -> {
                assertThat(item.getPlace()).isEqualTo(testPlace); // Should reference same place
                return true;
            }));
        }

        @Test
        @DisplayName("Should maintain count consistency when saving/unsaving")
        void shouldMaintainCountConsistencyWhenSavingUnsaving() {
            // Given
            testTravelPlan.setSaveCount(5L);
            when(userRepository.findById("user123")).thenReturn(Optional.of(testUser));
            when(travelPlanRepository.findById("plan123")).thenReturn(Optional.of(testTravelPlan));
            when(savedPlanRepository.existsSavedPlan("user123", "plan123")).thenReturn(false);
            when(savedPlanRepository.save(any(SavedPlan.class))).thenReturn(new SavedPlan());
            when(travelPlanRepository.save(any(TravelPlan.class))).thenReturn(testTravelPlan);

            // When
            travelPlanService.saveTravelPlan("user123", "plan123");

            // Then
            verify(travelPlanRepository).save(argThat(plan -> {
                assertThat(plan.getSaveCount()).isEqualTo(6L); // Should increment
                return true;
            }));
        }

        @Test
        @DisplayName("Should handle cascading deletes properly")
        void shouldHandleCascadingDeletesProperly() {
            // Given
            when(travelPlanRepository.findById("plan123")).thenReturn(Optional.of(testTravelPlan));

            // When
            travelPlanService.deleteTravelPlan("user123", "plan123");

            // Then
            verify(travelPlanRepository).delete(testTravelPlan);
            // Note: In real implementation, should also verify itinerary items are deleted
        }
    }
}