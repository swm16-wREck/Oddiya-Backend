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
}