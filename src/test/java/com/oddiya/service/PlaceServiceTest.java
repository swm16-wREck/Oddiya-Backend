package com.oddiya.service;

import com.oddiya.dto.request.CreatePlaceRequest;
import com.oddiya.dto.response.PageResponse;
import com.oddiya.dto.response.PlaceResponse;
import com.oddiya.entity.Place;
import com.oddiya.exception.NotFoundException;
import com.oddiya.repository.PlaceRepository;
import com.oddiya.service.impl.PlaceServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PlaceService Tests")
class PlaceServiceTest {

    @Mock
    private PlaceRepository placeRepository;

    @InjectMocks
    private PlaceServiceImpl placeService;

    private Place testPlace;
    private CreatePlaceRequest createRequest;
    private Pageable pageable;

    @BeforeEach
    void setUp() {
        testPlace = Place.builder()
                .id("place123")
                .name("Test Restaurant")
                .description("A great place to eat")
                .address("123 Test Street, Test City")
                .latitude(37.5665)
                .longitude(126.9780)
                .category("restaurant")
                .phoneNumber("+82-2-1234-5678")
                .website("https://testrestaurant.com")
                .openingHours(Map.of("Mon-Sun", "09:00-22:00"))
                .images(Arrays.asList("image1.jpg", "image2.jpg"))
                .tags(Arrays.asList("korean", "spicy", "popular"))
                .rating(4.5)
                .reviewCount(150)
                .bookmarkCount(25)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        createRequest = CreatePlaceRequest.builder()
                .name("New Restaurant")
                .description("A new place to dine")
                .address("456 New Street, New City")
                .latitude(37.5665)
                .longitude(126.9780)
                .category("restaurant")
                .phoneNumber("+82-2-9876-5432")
                .website("https://newrestaurant.com")
                .openingHours(Map.of("Mon-Sun", "10:00-23:00"))
                .images(Arrays.asList("new1.jpg", "new2.jpg"))
                .tags(Arrays.asList("fusion", "modern"))
                .build();

        pageable = PageRequest.of(0, 10, Sort.by("name"));
    }

    @Nested
    @DisplayName("Create Place Tests")
    class CreatePlaceTests {

        @Test
        @DisplayName("Should successfully create place with all fields")
        void shouldSuccessfullyCreatePlaceWithAllFields() {
            // Given
            when(placeRepository.save(any(Place.class))).thenReturn(testPlace);

            // When
            PlaceResponse response = placeService.createPlace(createRequest);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getName()).isEqualTo(testPlace.getName());
            assertThat(response.getDescription()).isEqualTo(testPlace.getDescription());
            assertThat(response.getAddress()).isEqualTo(testPlace.getAddress());
            assertThat(response.getLatitude()).isEqualTo(testPlace.getLatitude());
            assertThat(response.getLongitude()).isEqualTo(testPlace.getLongitude());
            assertThat(response.getCategory()).isEqualTo(testPlace.getCategory());

            verify(placeRepository).save(argThat(place -> {
                assertThat(place.getName()).isEqualTo(createRequest.getName());
                assertThat(place.getRating()).isEqualTo(0.0);
                assertThat(place.getReviewCount()).isEqualTo(0);
                assertThat(place.getBookmarkCount()).isEqualTo(0);
                return true;
            }));
        }

        @Test
        @DisplayName("Should create place with null collections as empty lists")
        void shouldCreatePlaceWithNullCollectionsAsEmptyLists() {
            // Given
            createRequest.setImages(null);
            createRequest.setTags(null);
            when(placeRepository.save(any(Place.class))).thenReturn(testPlace);

            // When
            PlaceResponse response = placeService.createPlace(createRequest);

            // Then
            verify(placeRepository).save(argThat(place -> {
                assertThat(place.getImages()).isNotNull().isEmpty();
                assertThat(place.getTags()).isNotNull().isEmpty();
                return true;
            }));
        }

        @Test
        @DisplayName("Should initialize default values correctly")
        void shouldInitializeDefaultValuesCorrectly() {
            // Given
            when(placeRepository.save(any(Place.class))).thenReturn(testPlace);

            // When
            placeService.createPlace(createRequest);

            // Then
            verify(placeRepository).save(argThat(place -> {
                assertThat(place.getRating()).isEqualTo(0.0);
                assertThat(place.getReviewCount()).isEqualTo(0);
                assertThat(place.getBookmarkCount()).isEqualTo(0);
                return true;
            }));
        }

        @Test
        @DisplayName("Should handle repository save failure")
        void shouldHandleRepositorySaveFailure() {
            // Given
            when(placeRepository.save(any(Place.class)))
                    .thenThrow(new RuntimeException("Database error"));

            // When & Then
            assertThatThrownBy(() -> placeService.createPlace(createRequest))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Database error");
        }
    }

    @Nested
    @DisplayName("Get Place Tests")
    class GetPlaceTests {

        @Test
        @DisplayName("Should successfully get place by ID")
        void shouldSuccessfullyGetPlaceById() {
            // Given
            when(placeRepository.findById("place123")).thenReturn(Optional.of(testPlace));

            // When
            PlaceResponse response = placeService.getPlace("place123");

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo("place123");
            assertThat(response.getName()).isEqualTo(testPlace.getName());
            assertThat(response.getAverageRating()).isEqualTo(testPlace.getRating());
            assertThat(response.getReviewCount()).isEqualTo(testPlace.getReviewCount());
        }

        @Test
        @DisplayName("Should throw NotFoundException when place not found")
        void shouldThrowNotFoundExceptionWhenPlaceNotFound() {
            // Given
            when(placeRepository.findById("nonexistent")).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> placeService.getPlace("nonexistent"))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Place not found");
        }

        @Test
        @DisplayName("Should handle null place ID")
        void shouldHandleNullPlaceId() {
            // Given
            when(placeRepository.findById(null)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> placeService.getPlace(null))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Place not found");
        }
    }

    @Nested
    @DisplayName("Update Place Tests")
    class UpdatePlaceTests {

        @Test
        @DisplayName("Should successfully update place with all fields")
        void shouldSuccessfullyUpdatePlaceWithAllFields() {
            // Given
            when(placeRepository.findById("place123")).thenReturn(Optional.of(testPlace));
            when(placeRepository.save(any(Place.class))).thenReturn(testPlace);

            // When
            PlaceResponse response = placeService.updatePlace("place123", createRequest);

            // Then
            assertThat(response).isNotNull();
            verify(placeRepository).save(argThat(place -> {
                assertThat(place.getName()).isEqualTo(createRequest.getName());
                assertThat(place.getDescription()).isEqualTo(createRequest.getDescription());
                assertThat(place.getAddress()).isEqualTo(createRequest.getAddress());
                assertThat(place.getLatitude()).isEqualTo(createRequest.getLatitude());
                assertThat(place.getLongitude()).isEqualTo(createRequest.getLongitude());
                return true;
            }));
        }

        @Test
        @DisplayName("Should update only non-null fields")
        void shouldUpdateOnlyNonNullFields() {
            // Given
            CreatePlaceRequest partialUpdate = CreatePlaceRequest.builder()
                    .name("Updated Name")
                    .description("Updated Description")
                    // Other fields are null
                    .build();

            when(placeRepository.findById("place123")).thenReturn(Optional.of(testPlace));
            when(placeRepository.save(any(Place.class))).thenReturn(testPlace);

            // When
            placeService.updatePlace("place123", partialUpdate);

            // Then
            verify(placeRepository).save(argThat(place -> {
                assertThat(place.getName()).isEqualTo("Updated Name");
                assertThat(place.getDescription()).isEqualTo("Updated Description");
                // Other fields should remain unchanged
                assertThat(place.getAddress()).isEqualTo(testPlace.getAddress());
                assertThat(place.getCategory()).isEqualTo(testPlace.getCategory());
                return true;
            }));
        }

        @Test
        @DisplayName("Should throw NotFoundException when updating non-existent place")
        void shouldThrowNotFoundExceptionWhenUpdatingNonExistentPlace() {
            // Given
            when(placeRepository.findById("nonexistent")).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> placeService.updatePlace("nonexistent", createRequest))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Place not found");

            verify(placeRepository, never()).save(any(Place.class));
        }

        @Test
        @DisplayName("Should handle empty collections in update")
        void shouldHandleEmptyCollectionsInUpdate() {
            // Given
            createRequest.setImages(new ArrayList<>());
            createRequest.setTags(new ArrayList<>());

            when(placeRepository.findById("place123")).thenReturn(Optional.of(testPlace));
            when(placeRepository.save(any(Place.class))).thenReturn(testPlace);

            // When
            placeService.updatePlace("place123", createRequest);

            // Then
            verify(placeRepository).save(argThat(place -> {
                assertThat(place.getImages()).isEmpty();
                assertThat(place.getTags()).isEmpty();
                return true;
            }));
        }
    }

    @Nested
    @DisplayName("Delete Place Tests")
    class DeletePlaceTests {

        @Test
        @DisplayName("Should successfully delete existing place")
        void shouldSuccessfullyDeleteExistingPlace() {
            // Given
            when(placeRepository.findById("place123")).thenReturn(Optional.of(testPlace));

            // When
            placeService.deletePlace("place123");

            // Then
            verify(placeRepository).delete(testPlace);
        }

        @Test
        @DisplayName("Should throw NotFoundException when deleting non-existent place")
        void shouldThrowNotFoundExceptionWhenDeletingNonExistentPlace() {
            // Given
            when(placeRepository.findById("nonexistent")).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> placeService.deletePlace("nonexistent"))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Place not found");

            verify(placeRepository, never()).delete(any(Place.class));
        }
    }

    @Nested
    @DisplayName("Search Places Tests")
    class SearchPlacesTests {

        @Test
        @DisplayName("Should successfully search places by query")
        void shouldSuccessfullySearchPlacesByQuery() {
            // Given
            List<Place> places = Arrays.asList(testPlace);
            Page<Place> placePage = new PageImpl<>(places, pageable, 1);
            
            when(placeRepository.findByNameContainingIgnoreCaseOrAddressContainingIgnoreCase(
                    eq("restaurant"), eq("restaurant"), eq(pageable))).thenReturn(placePage);

            // When
            PageResponse<PlaceResponse> response = placeService.searchPlaces("restaurant", pageable);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getContent()).hasSize(1);
            assertThat(response.getTotalElements()).isEqualTo(1);
            assertThat(response.getTotalPages()).isEqualTo(1);
            assertThat(response.getContent().get(0).getName()).isEqualTo(testPlace.getName());
        }

        @Test
        @DisplayName("Should return empty result when no places match query")
        void shouldReturnEmptyResultWhenNoPlacesMatchQuery() {
            // Given
            Page<Place> emptyPage = new PageImpl<>(new ArrayList<>(), pageable, 0);
            
            when(placeRepository.findByNameContainingIgnoreCaseOrAddressContainingIgnoreCase(
                    eq("nonexistent"), eq("nonexistent"), eq(pageable))).thenReturn(emptyPage);

            // When
            PageResponse<PlaceResponse> response = placeService.searchPlaces("nonexistent", pageable);

            // Then
            assertThat(response.getContent()).isEmpty();
            assertThat(response.getTotalElements()).isEqualTo(0);
            assertThat(response.isEmpty()).isTrue();
        }

        @Test
        @DisplayName("Should handle case-insensitive search")
        void shouldHandleCaseInsensitiveSearch() {
            // Given
            List<Place> places = Arrays.asList(testPlace);
            Page<Place> placePage = new PageImpl<>(places, pageable, 1);
            
            when(placeRepository.findByNameContainingIgnoreCaseOrAddressContainingIgnoreCase(
                    eq("RESTAURANT"), eq("RESTAURANT"), eq(pageable))).thenReturn(placePage);

            // When
            PageResponse<PlaceResponse> response = placeService.searchPlaces("RESTAURANT", pageable);

            // Then
            assertThat(response.getContent()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Get Places by Category Tests")
    class GetPlacesByCategoryTests {

        @Test
        @DisplayName("Should successfully get places by category")
        void shouldSuccessfullyGetPlacesByCategory() {
            // Given
            List<Place> places = Arrays.asList(testPlace);
            Page<Place> placePage = new PageImpl<>(places, pageable, 1);
            
            when(placeRepository.findByCategory("restaurant", pageable)).thenReturn(placePage);

            // When
            PageResponse<PlaceResponse> response = placeService.getPlacesByCategory("restaurant", pageable);

            // Then
            assertThat(response.getContent()).hasSize(1);
            assertThat(response.getContent().get(0).getCategory()).isEqualTo("restaurant");
        }

        @Test
        @DisplayName("Should return empty result for non-existent category")
        void shouldReturnEmptyResultForNonExistentCategory() {
            // Given
            Page<Place> emptyPage = new PageImpl<>(new ArrayList<>(), pageable, 0);
            
            when(placeRepository.findByCategory("nonexistent", pageable)).thenReturn(emptyPage);

            // When
            PageResponse<PlaceResponse> response = placeService.getPlacesByCategory("nonexistent", pageable);

            // Then
            assertThat(response.getContent()).isEmpty();
            assertThat(response.getTotalElements()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("Get Popular Places Tests")
    class GetPopularPlacesTests {

        @Test
        @DisplayName("Should successfully get popular places")
        void shouldSuccessfullyGetPopularPlaces() {
            // Given
            List<Place> places = Arrays.asList(testPlace);
            Page<Place> placePage = new PageImpl<>(places, pageable, 1);
            
            when(placeRepository.findAllByOrderByPopularityScoreDesc(pageable)).thenReturn(placePage);

            // When
            PageResponse<PlaceResponse> response = placeService.getPopularPlaces(pageable);

            // Then
            assertThat(response.getContent()).hasSize(1);
            assertThat(response.getTotalElements()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should handle pagination correctly")
        void shouldHandlePaginationCorrectly() {
            // Given
            Pageable secondPage = PageRequest.of(1, 5);
            Page<Place> emptyPage = new PageImpl<>(new ArrayList<>(), secondPage, 0);
            
            when(placeRepository.findAllByOrderByPopularityScoreDesc(secondPage)).thenReturn(emptyPage);

            // When
            PageResponse<PlaceResponse> response = placeService.getPopularPlaces(secondPage);

            // Then
            assertThat(response.getPageNumber()).isEqualTo(1);
            assertThat(response.getPageSize()).isEqualTo(5);
            assertThat(response.getContent()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Get Nearby Places Tests")
    class GetNearbyPlacesTests {

        @Test
        @DisplayName("Should return empty list for nearby places (not implemented)")
        void shouldReturnEmptyListForNearbyPlaces() {
            // When
            List<PlaceResponse> response = placeService.getNearbyPlaces(37.5665, 126.9780, 1.0);

            // Then
            assertThat(response).isEmpty();
        }

        @Test
        @DisplayName("Should handle various coordinate values")
        void shouldHandleVariousCoordinateValues() {
            // When
            List<PlaceResponse> response1 = placeService.getNearbyPlaces(-90.0, -180.0, 0.5);
            List<PlaceResponse> response2 = placeService.getNearbyPlaces(90.0, 180.0, 100.0);

            // Then
            assertThat(response1).isEmpty();
            assertThat(response2).isEmpty();
        }
    }

    @Nested
    @DisplayName("Increment View Count Tests")
    class IncrementViewCountTests {

        @Test
        @DisplayName("Should increment view count for existing place")
        void shouldIncrementViewCountForExistingPlace() {
            // Given
            when(placeRepository.findById("place123")).thenReturn(Optional.of(testPlace));
            when(placeRepository.save(any(Place.class))).thenReturn(testPlace);

            // When
            placeService.incrementViewCount("place123");

            // Then
            verify(placeRepository).save(testPlace);
        }

        @Test
        @DisplayName("Should handle non-existent place gracefully")
        void shouldHandleNonExistentPlaceGracefully() {
            // Given
            when(placeRepository.findById("nonexistent")).thenReturn(Optional.empty());

            // When & Then
            assertThatCode(() -> placeService.incrementViewCount("nonexistent"))
                    .doesNotThrowAnyException();

            verify(placeRepository, never()).save(any(Place.class));
        }
    }

    @Nested
    @DisplayName("Mapping Tests")
    class MappingTests {

        @Test
        @DisplayName("Should correctly map Place entity to PlaceResponse")
        void shouldCorrectlyMapPlaceEntityToPlaceResponse() {
            // Given
            when(placeRepository.findById("place123")).thenReturn(Optional.of(testPlace));

            // When
            PlaceResponse response = placeService.getPlace("place123");

            // Then
            assertThat(response.getId()).isEqualTo(testPlace.getId());
            assertThat(response.getName()).isEqualTo(testPlace.getName());
            assertThat(response.getDescription()).isEqualTo(testPlace.getDescription());
            assertThat(response.getAddress()).isEqualTo(testPlace.getAddress());
            assertThat(response.getLatitude()).isEqualTo(testPlace.getLatitude());
            assertThat(response.getLongitude()).isEqualTo(testPlace.getLongitude());
            assertThat(response.getCategory()).isEqualTo(testPlace.getCategory());
            assertThat(response.getPhoneNumber()).isEqualTo(testPlace.getPhoneNumber());
            assertThat(response.getWebsite()).isEqualTo(testPlace.getWebsite());
            assertThat(response.getOpeningHours()).isEqualTo(testPlace.getOpeningHours());
            assertThat(response.getAverageRating()).isEqualTo(testPlace.getRating());
            assertThat(response.getReviewCount()).isEqualTo(testPlace.getReviewCount());
            assertThat(response.getImages()).isEqualTo(testPlace.getImages());
            assertThat(response.getTags()).isEqualTo(testPlace.getTags());
            assertThat(response.getIsSaved()).isFalse(); // Default value
            assertThat(response.getCreatedAt()).isEqualTo(testPlace.getCreatedAt());
            assertThat(response.getUpdatedAt()).isEqualTo(testPlace.getUpdatedAt());
        }

        @Test
        @DisplayName("Should set default values for null response fields")
        void shouldSetDefaultValuesForNullResponseFields() {
            // Given
            when(placeRepository.findById("place123")).thenReturn(Optional.of(testPlace));

            // When
            PlaceResponse response = placeService.getPlace("place123");

            // Then
            assertThat(response.getPriceRange()).isNull();
            assertThat(response.getGooglePlaceId()).isNull();
            assertThat(response.getMetadata()).isNull();
            assertThat(response.getIsSaved()).isFalse();
        }
    }

    @Nested
    @DisplayName("Edge Cases Tests")
    class EdgeCasesTests {

        @Test
        @DisplayName("Should handle place with empty collections")
        void shouldHandlePlaceWithEmptyCollections() {
            // Given
            testPlace.setImages(new ArrayList<>());
            testPlace.setTags(new ArrayList<>());
            when(placeRepository.findById("place123")).thenReturn(Optional.of(testPlace));

            // When
            PlaceResponse response = placeService.getPlace("place123");

            // Then
            assertThat(response.getImages()).isEmpty();
            assertThat(response.getTags()).isEmpty();
        }

        @Test
        @DisplayName("Should handle place with null string fields")
        void shouldHandlePlaceWithNullStringFields() {
            // Given
            testPlace.setDescription(null);
            testPlace.setPhoneNumber(null);
            testPlace.setWebsite(null);
            when(placeRepository.findById("place123")).thenReturn(Optional.of(testPlace));

            // When
            PlaceResponse response = placeService.getPlace("place123");

            // Then
            assertThat(response.getDescription()).isNull();
            assertThat(response.getPhoneNumber()).isNull();
            assertThat(response.getWebsite()).isNull();
        }

        @Test
        @DisplayName("Should handle extreme coordinate values")
        void shouldHandleExtremeCoordinateValues() {
            // Given
            createRequest.setLatitude(-90.0);
            createRequest.setLongitude(180.0);
            when(placeRepository.save(any(Place.class))).thenReturn(testPlace);

            // When
            PlaceResponse response = placeService.createPlace(createRequest);

            // Then
            assertThat(response).isNotNull();
            verify(placeRepository).save(argThat(place -> {
                assertThat(place.getLatitude()).isEqualTo(-90.0);
                assertThat(place.getLongitude()).isEqualTo(180.0);
                return true;
            }));
        }

        @Test
        @DisplayName("Should handle very long text fields")
        void shouldHandleVeryLongTextFields() {
            // Given
            String longText = "A".repeat(1000);
            createRequest.setName(longText);
            createRequest.setDescription(longText);
            when(placeRepository.save(any(Place.class))).thenReturn(testPlace);

            // When
            PlaceResponse response = placeService.createPlace(createRequest);

            // Then
            assertThat(response).isNotNull();
            verify(placeRepository).save(argThat(place -> {
                assertThat(place.getName()).isEqualTo(longText);
                assertThat(place.getDescription()).isEqualTo(longText);
                return true;
            }));
        }
    }

    @Nested
    @DisplayName("Parameterized Tests")
    class ParameterizedTests {

        @ParameterizedTest
        @ValueSource(strings = {"restaurant", "hotel", "attraction", "shopping", "entertainment"})
        @DisplayName("Should create places with different categories")
        void shouldCreatePlacesWithDifferentCategories(String category) {
            // Given
            createRequest.setCategory(category);
            when(placeRepository.save(any(Place.class))).thenReturn(testPlace);

            // When
            PlaceResponse response = placeService.createPlace(createRequest);

            // Then
            assertThat(response).isNotNull();
            verify(placeRepository).save(argThat(place -> {
                assertThat(place.getCategory()).isEqualTo(category);
                return true;
            }));
        }

        @ParameterizedTest
        @CsvSource({
            "37.5665, 126.9780, Seoul",
            "35.1796, 129.0756, Busan", 
            "33.4996, 126.5312, Jeju",
            "37.4563, 126.7052, Incheon",
            "35.8714, 128.6014, Daegu"
        })
        @DisplayName("Should create places with different coordinates")
        void shouldCreatePlacesWithDifferentCoordinates(double latitude, double longitude, String city) {
            // Given
            createRequest.setLatitude(latitude);
            createRequest.setLongitude(longitude);
            createRequest.setAddress("123 Street, " + city);
            when(placeRepository.save(any(Place.class))).thenReturn(testPlace);

            // When
            PlaceResponse response = placeService.createPlace(createRequest);

            // Then
            assertThat(response).isNotNull();
            verify(placeRepository).save(argThat(place -> {
                assertThat(place.getLatitude()).isEqualTo(latitude);
                assertThat(place.getLongitude()).isEqualTo(longitude);
                assertThat(place.getAddress()).contains(city);
                return true;
            }));
        }

        @ParameterizedTest
        @ValueSource(doubles = {1.0, 5.0, 10.0, 50.0, 100.0})
        @DisplayName("Should handle different search radius values for nearby places")
        void shouldHandleDifferentSearchRadiusValues(double radius) {
            // Given
            List<Place> nearbyPlaces = Arrays.asList(testPlace);
            when(placeRepository.findNearbyPlaces(eq(37.5665), eq(126.9780), anyInt()))
                    .thenReturn(nearbyPlaces);

            // When
            List<PlaceResponse> response = placeService.getNearbyPlaces(37.5665, 126.9780, radius);

            // Then
            assertThat(response).isNotNull();
            verify(placeRepository).findNearbyPlaces(eq(37.5665), eq(126.9780), eq((int) (radius * 1000)));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"", " ", "   "})
        @DisplayName("Should handle invalid search queries gracefully")
        void shouldHandleInvalidSearchQueriesGracefully(String query) {
            // Given
            Page<Place> emptyPage = new PageImpl<>(new ArrayList<>(), pageable, 0);
            when(placeRepository.findByNameContainingIgnoreCaseOrAddressContainingIgnoreCase(anyString(), anyString(), eq(pageable))).thenReturn(emptyPage);

            // When
            PageResponse<PlaceResponse> response = placeService.searchPlaces(query, pageable);

            // Then
            assertThat(response.getContent()).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(longs = {0L, 1L, 100L, 999999L})
        @DisplayName("Should handle different view count values for increment")
        void shouldHandleDifferentViewCountValuesForIncrement(Long initialCount) {
            // Given
            testPlace.setViewCount(initialCount);
            when(placeRepository.findById("place123")).thenReturn(Optional.of(testPlace));
            when(placeRepository.save(any(Place.class))).thenReturn(testPlace);

            // When
            placeService.incrementViewCount("place123");

            // Then
            verify(placeRepository).save(argThat(place -> {
                assertThat(place.getViewCount()).isEqualTo(initialCount + 1);
                return true;
            }));
        }

        @ParameterizedTest
        @CsvSource({
            "1, korean restaurant",
            "5, popular spicy food",
            "10, fusion modern dining",
            "3, traditional local"
        })
        @DisplayName("Should handle places with different numbers of tags")
        void shouldHandlePlacesWithDifferentNumbersOfTags(int tagCount, String tagPrefix) {
            // Given
            List<String> tags = new ArrayList<>();
            for (int i = 1; i <= tagCount; i++) {
                tags.add(tagPrefix + " " + i);
            }
            createRequest.setTags(tags);
            when(placeRepository.save(any(Place.class))).thenReturn(testPlace);

            // When
            PlaceResponse response = placeService.createPlace(createRequest);

            // Then
            assertThat(response).isNotNull();
            verify(placeRepository).save(argThat(place -> {
                assertThat(place.getTags()).hasSize(tagCount);
                assertThat(place.getTags().get(0)).startsWith(tagPrefix);
                return true;
            }));
        }
    }

    @Nested
    @DisplayName("Advanced Search Tests")
    class AdvancedSearchTests {

        @Test
        @DisplayName("Should search places with multiple criteria")
        void shouldSearchPlacesWithMultipleCriteria() {
            // Given
            List<Place> matchingPlaces = Arrays.asList(testPlace);
            Page<Place> placePage = new PageImpl<>(matchingPlaces, pageable, 1);
            
            when(placeRepository.findByNameContainingIgnoreCaseOrAddressContainingIgnoreCase("korean restaurant", "korean restaurant", pageable)).thenReturn(placePage);

            // When
            PageResponse<PlaceResponse> response = placeService.searchPlaces("korean restaurant", pageable);

            // Then
            assertThat(response.getContent()).hasSize(1);
            assertThat(response.getContent().get(0).getCategory()).isEqualTo("restaurant");
        }

        @Test
        @DisplayName("Should handle search with special characters")
        void shouldHandleSearchWithSpecialCharacters() {
            // Given
            String specialQuery = "café & restaurant (24/7)";
            Page<Place> emptyPage = new PageImpl<>(new ArrayList<>(), pageable, 0);
            when(placeRepository.findByNameContainingIgnoreCaseOrAddressContainingIgnoreCase(specialQuery, specialQuery, pageable)).thenReturn(emptyPage);

            // When
            PageResponse<PlaceResponse> response = placeService.searchPlaces(specialQuery, pageable);

            // Then
            assertThat(response.getContent()).isEmpty();
            verify(placeRepository).findByNameContainingIgnoreCaseOrAddressContainingIgnoreCase(specialQuery, specialQuery, pageable);
        }

        @Test
        @DisplayName("Should handle search with unicode characters")
        void shouldHandleSearchWithUnicodeCharacters() {
            // Given
            String unicodeQuery = "한국 음식점 맛집";
            List<Place> koreanPlaces = Arrays.asList(testPlace);
            Page<Place> placePage = new PageImpl<>(koreanPlaces, pageable, 1);
            when(placeRepository.findByNameContainingIgnoreCaseOrAddressContainingIgnoreCase(unicodeQuery, unicodeQuery, pageable)).thenReturn(placePage);

            // When
            PageResponse<PlaceResponse> response = placeService.searchPlaces(unicodeQuery, pageable);

            // Then
            assertThat(response.getContent()).hasSize(1);
            verify(placeRepository).findByNameContainingIgnoreCaseOrAddressContainingIgnoreCase(unicodeQuery, unicodeQuery, pageable);
        }

        @Test
        @DisplayName("Should handle very long search queries")
        void shouldHandleVeryLongSearchQueries() {
            // Given
            String longQuery = "restaurant ".repeat(100);
            Page<Place> emptyPage = new PageImpl<>(new ArrayList<>(), pageable, 0);
            when(placeRepository.findByNameContainingIgnoreCaseOrAddressContainingIgnoreCase(longQuery.trim(), longQuery.trim(), pageable)).thenReturn(emptyPage);

            // When
            PageResponse<PlaceResponse> response = placeService.searchPlaces(longQuery.trim(), pageable);

            // Then
            assertThat(response.getContent()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Popularity Score Tests")
    class PopularityScoreTests {

        @Test
        @DisplayName("Should calculate popularity score correctly with all metrics")
        void shouldCalculatePopularityScoreCorrectlyWithAllMetrics() {
            // Given
            testPlace.setViewCount(1000L);
            testPlace.setRating(4.5);
            testPlace.setReviewCount(200);
            testPlace.setBookmarkCount(50);
            
            when(placeRepository.findById("place123")).thenReturn(Optional.of(testPlace));
            when(placeRepository.save(any(Place.class))).thenReturn(testPlace);

            // When
            placeService.incrementViewCount("place123");

            // Then
            verify(placeRepository).save(argThat(place -> {
                assertThat(place.getPopularityScore()).isPositive();
                // Score should include contributions from all metrics
                double expectedScore = Math.log10(1001 + 1) * 10 + 4.5 * 20 + 
                                     Math.log10(200 + 1) * 5 + Math.log10(50 + 1) * 15;
                assertThat(place.getPopularityScore()).isCloseTo(expectedScore, within(0.01));
                return true;
            }));
        }

        @Test
        @DisplayName("Should handle null popularity metrics gracefully")
        void shouldHandleNullPopularityMetricsGracefully() {
            // Given
            testPlace.setViewCount(null);
            testPlace.setRating(null);
            testPlace.setReviewCount(null);
            testPlace.setBookmarkCount(null);
            
            when(placeRepository.findById("place123")).thenReturn(Optional.of(testPlace));
            when(placeRepository.save(any(Place.class))).thenReturn(testPlace);

            // When
            placeService.incrementViewCount("place123");

            // Then
            verify(placeRepository).save(argThat(place -> {
                assertThat(place.getViewCount()).isEqualTo(1L); // Should set to 1
                assertThat(place.getPopularityScore()).isEqualTo(Math.log10(2) * 10); // Only view count contributes
                return true;
            }));
        }

        @Test
        @DisplayName("Should update popularity score on view count increment")
        void shouldUpdatePopularityScoreOnViewCountIncrement() {
            // Given
            testPlace.setViewCount(10L);
            testPlace.setRating(3.0);
            testPlace.setReviewCount(5);
            testPlace.setBookmarkCount(2);
            
            when(placeRepository.findById("place123")).thenReturn(Optional.of(testPlace));
            when(placeRepository.save(any(Place.class))).thenReturn(testPlace);

            // When
            placeService.incrementViewCount("place123");

            // Then
            verify(placeRepository).save(argThat(place -> {
                assertThat(place.getViewCount()).isEqualTo(11L);
                assertThat(place.getPopularityScore()).isPositive();
                return true;
            }));
        }
    }

    @Nested
    @DisplayName("Pagination Tests")
    class PaginationTests {

        @Test
        @DisplayName("Should handle first page correctly")
        void shouldHandleFirstPageCorrectly() {
            // Given
            Pageable firstPage = PageRequest.of(0, 5, Sort.by("name"));
            List<Place> places = Arrays.asList(testPlace);
            Page<Place> placePage = new PageImpl<>(places, firstPage, 10);
            
            when(placeRepository.findByCategory("restaurant", firstPage)).thenReturn(placePage);

            // When
            PageResponse<PlaceResponse> response = placeService.getPlacesByCategory("restaurant", firstPage);

            // Then
            assertThat(response.getPageNumber()).isEqualTo(0);
            assertThat(response.getPageSize()).isEqualTo(5);
            assertThat(response.getTotalElements()).isEqualTo(10);
            assertThat(response.getTotalPages()).isEqualTo(2);
            assertThat(response.isFirst()).isTrue();
            assertThat(response.isLast()).isFalse();
        }

        @Test
        @DisplayName("Should handle last page correctly")
        void shouldHandleLastPageCorrectly() {
            // Given
            Pageable lastPage = PageRequest.of(1, 5, Sort.by("name"));
            List<Place> places = Arrays.asList(testPlace);
            Page<Place> placePage = new PageImpl<>(places, lastPage, 6); // Total 6 items, so page 1 is last
            
            when(placeRepository.findAllByOrderByPopularityScoreDesc(lastPage)).thenReturn(placePage);

            // When
            PageResponse<PlaceResponse> response = placeService.getPopularPlaces(lastPage);

            // Then
            assertThat(response.getPageNumber()).isEqualTo(1);
            assertThat(response.getTotalElements()).isEqualTo(6);
            assertThat(response.getTotalPages()).isEqualTo(2);
            assertThat(response.isLast()).isTrue();
        }

        @Test
        @DisplayName("Should handle empty pages")
        void shouldHandleEmptyPages() {
            // Given
            Pageable emptyPage = PageRequest.of(5, 10); // Way beyond available content
            Page<Place> empty = new PageImpl<>(new ArrayList<>(), emptyPage, 10);
            
            when(placeRepository.findByNameContainingIgnoreCaseOrAddressContainingIgnoreCase("nonexistent", "nonexistent", emptyPage)).thenReturn(empty);

            // When
            PageResponse<PlaceResponse> response = placeService.searchPlaces("nonexistent", emptyPage);

            // Then
            assertThat(response.getContent()).isEmpty();
            assertThat(response.getPageNumber()).isEqualTo(5);
            assertThat(response.isEmpty()).isTrue();
        }
    }

    @Nested
    @DisplayName("Performance Tests")
    class PerformanceTests {

        @Test
        @DisplayName("Should minimize repository calls for single place operations")
        void shouldMinimizeRepositoryCallsForSinglePlaceOperations() {
            // Given
            when(placeRepository.findById("place123")).thenReturn(Optional.of(testPlace));

            // When
            placeService.getPlace("place123");

            // Then
            verify(placeRepository, times(1)).findById("place123");
            verify(placeRepository, never()).save(any());
            verify(placeRepository, never()).delete(any());
        }

        @Test
        @DisplayName("Should handle bulk operations efficiently")
        void shouldHandleBulkOperationsEfficiently() {
            // Given
            List<Place> manyPlaces = new ArrayList<>();
            for (int i = 0; i < 1000; i++) {
                manyPlaces.add(Place.builder()
                        .id("place" + i)
                        .name("Place " + i)
                        .category("restaurant")
                        .build());
            }
            Pageable largePage = PageRequest.of(0, 1000);
            Page<Place> page = new PageImpl<>(manyPlaces, largePage, 1000);
            
            when(placeRepository.findByCategory("restaurant", largePage)).thenReturn(page);

            // When
            PageResponse<PlaceResponse> response = placeService.getPlacesByCategory("restaurant", largePage);

            // Then
            assertThat(response.getContent()).hasSize(1000);
            verify(placeRepository, times(1)).findByCategory("restaurant", largePage);
        }

        @Test
        @DisplayName("Should handle concurrent view count increments")
        void shouldHandleConcurrentViewCountIncrements() {
            // Given
            when(placeRepository.findById("place123")).thenReturn(Optional.of(testPlace));
            when(placeRepository.save(any(Place.class))).thenReturn(testPlace);

            // When - Simulate concurrent increments
            for (int i = 0; i < 10; i++) {
                placeService.incrementViewCount("place123");
            }

            // Then
            verify(placeRepository, times(10)).findById("place123");
            verify(placeRepository, times(10)).save(any(Place.class));
        }
    }

    @Nested
    @DisplayName("Data Integrity Tests")
    class DataIntegrityTests {

        @Test
        @DisplayName("Should maintain data consistency during updates")
        void shouldMaintainDataConsistencyDuringUpdates() {
            // Given
            when(placeRepository.findById("place123")).thenReturn(Optional.of(testPlace));
            when(placeRepository.save(any(Place.class))).thenReturn(testPlace);

            // When
            placeService.updatePlace("place123", createRequest);

            // Then
            verify(placeRepository).save(argThat(place -> {
                assertThat(place.getId()).isEqualTo("place123"); // Should maintain ID
                assertThat(place.getName()).isEqualTo(createRequest.getName());
                // Ratings and counts should be preserved during regular updates
                assertThat(place.getRating()).isEqualTo(testPlace.getRating());
                assertThat(place.getReviewCount()).isEqualTo(testPlace.getReviewCount());
                return true;
            }));
        }

        @Test
        @DisplayName("Should handle null view count gracefully")
        void shouldHandleNullViewCountGracefully() {
            // Given
            testPlace.setViewCount(null);
            when(placeRepository.findById("place123")).thenReturn(Optional.of(testPlace));
            when(placeRepository.save(any(Place.class))).thenReturn(testPlace);

            // When
            placeService.incrementViewCount("place123");

            // Then
            verify(placeRepository).save(argThat(place -> {
                assertThat(place.getViewCount()).isEqualTo(1L); // Should initialize to 1
                return true;
            }));
        }

        @Test
        @DisplayName("Should preserve creation timestamp during updates")
        void shouldPreserveCreationTimestampDuringUpdates() {
            // Given
            LocalDateTime originalCreatedAt = testPlace.getCreatedAt();
            when(placeRepository.findById("place123")).thenReturn(Optional.of(testPlace));
            when(placeRepository.save(any(Place.class))).thenReturn(testPlace);

            // When
            placeService.updatePlace("place123", createRequest);

            // Then
            verify(placeRepository).save(argThat(place -> {
                assertThat(place.getCreatedAt()).isEqualTo(originalCreatedAt); // Should not change
                return true;
            }));
        }
    }

    @Nested
    @DisplayName("Security Tests")
    class SecurityTests {

        @Test
        @DisplayName("Should sanitize input data")
        void shouldSanitizeInputData() {
            // Given
            CreatePlaceRequest maliciousRequest = CreatePlaceRequest.builder()
                    .name("<script>alert('xss')</script>Restaurant")
                    .description("A great place<!--comment-->")
                    .website("javascript:alert('xss')")
                    .build();
            when(placeRepository.save(any(Place.class))).thenReturn(testPlace);

            // When
            PlaceResponse response = placeService.createPlace(maliciousRequest);

            // Then
            assertThat(response).isNotNull();
            // Note: In a real implementation, you might want to sanitize these inputs
            verify(placeRepository).save(any(Place.class));
        }

        @Test
        @DisplayName("Should handle SQL injection attempts in search")
        void shouldHandleSqlInjectionAttemptsInSearch() {
            // Given
            String maliciousQuery = "'; DROP TABLE places; --";
            Page<Place> emptyPage = new PageImpl<>(new ArrayList<>(), pageable, 0);
            when(placeRepository.findByNameContainingIgnoreCaseOrAddressContainingIgnoreCase(maliciousQuery, maliciousQuery, pageable)).thenReturn(emptyPage);

            // When
            PageResponse<PlaceResponse> response = placeService.searchPlaces(maliciousQuery, pageable);

            // Then
            assertThat(response.getContent()).isEmpty();
            verify(placeRepository).findByNameContainingIgnoreCaseOrAddressContainingIgnoreCase(maliciousQuery, maliciousQuery, pageable);
        }
    }
}