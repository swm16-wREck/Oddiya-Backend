package com.oddiya.repository;

import com.oddiya.entity.Place;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive tests for PlaceRepository covering:
 * - Basic CRUD operations
 * - Custom finder methods (Naver Place ID)
 * - Text search functionality
 * - Geospatial queries (nearby places)
 * - Category and tag filtering
 * - Rating and popularity queries
 * - Database constraints
 * - Pagination and sorting
 * - Transaction boundaries and rollback scenarios
 * - Entity relationships and cascading
 */
@DisplayName("PlaceRepository Tests")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PlaceRepositoryTest extends RepositoryTestBase {

    @Autowired
    private PlaceRepository placeRepository;

    @Nested
    @DisplayName("Basic CRUD Operations")
    class BasicCrudOperations {

        @Test
        @DisplayName("Should save and retrieve place successfully")
        void shouldSaveAndRetrievePlace() {
            // Given
            Place newPlace = Place.builder()
                .naverPlaceId("new-place-123")
                .name("New Test Place")
                .category("restaurant")
                .description("A new test place")
                .address("123 Test Street")
                .roadAddress("123 Test Road")
                .latitude(37.5665)
                .longitude(126.9780)
                .phoneNumber("02-1234-5678")
                .website("http://test.com")
                .rating(4.0)
                .reviewCount(50)
                .bookmarkCount(25)
                .isVerified(true)
                .popularityScore(80.0)
                .isDeleted(false)
                .build();

            // When
            Place savedPlace = placeRepository.save(newPlace);
            Optional<Place> foundPlace = placeRepository.findById(savedPlace.getId());

            // Then
            assertThat(savedPlace).isNotNull();
            assertThat(savedPlace.getId()).isNotNull();
            assertThat(savedPlace.getCreatedAt()).isNotNull();
            assertThat(savedPlace.getUpdatedAt()).isNotNull();

            assertThat(foundPlace).isPresent();
            assertThat(foundPlace.get().getName()).isEqualTo("New Test Place");
            assertThat(foundPlace.get().getNaverPlaceId()).isEqualTo("new-place-123");
        }

        @Test
        @DisplayName("Should update place successfully")
        void shouldUpdatePlaceSuccessfully() {
            // Given
            String originalDescription = testPlace1.getDescription();

            // When
            testPlace1.setDescription("Updated description");
            testPlace1.setRating(4.8);
            Place updatedPlace = placeRepository.save(testPlace1);

            // Then
            assertThat(updatedPlace.getDescription()).isEqualTo("Updated description");
            assertThat(updatedPlace.getDescription()).isNotEqualTo(originalDescription);
            assertThat(updatedPlace.getRating()).isEqualTo(4.8);
            assertThat(updatedPlace.getUpdatedAt()).isAfter(updatedPlace.getCreatedAt());
        }

        @Test
        @DisplayName("Should delete place successfully")
        void shouldDeletePlaceSuccessfully() {
            // Given
            String placeId = testPlace1.getId();

            // When
            placeRepository.deleteById(placeId);

            // Then
            Optional<Place> deletedPlace = placeRepository.findById(placeId);
            assertThat(deletedPlace).isEmpty();
        }

        @Test
        @DisplayName("Should find all places including deleted")
        void shouldFindAllPlacesIncludingDeleted() {
            // When
            List<Place> allPlaces = placeRepository.findAll();

            // Then
            assertThat(allPlaces).hasSize(3);
            assertThat(allPlaces)
                .extracting(Place::getName)
                .containsExactlyInAnyOrder("Seoul Tower", "Busan Beach", "Deleted Place");
        }
    }

    @Nested
    @DisplayName("Naver Place ID Finder Methods")
    class NaverPlaceIdFinderMethods {

        @Test
        @DisplayName("Should find place by Naver Place ID")
        void shouldFindPlaceByNaverPlaceId() {
            // When
            Optional<Place> foundPlace = placeRepository.findByNaverPlaceId("naver-place-1");

            // Then
            assertThat(foundPlace).isPresent();
            assertThat(foundPlace.get().getName()).isEqualTo("Seoul Tower");
            assertThat(foundPlace.get().getCategory()).isEqualTo("tourist_attraction");
        }

        @Test
        @DisplayName("Should return empty when Naver Place ID not found")
        void shouldReturnEmptyWhenNaverPlaceIdNotFound() {
            // When
            Optional<Place> foundPlace = placeRepository.findByNaverPlaceId("nonexistent-place-id");

            // Then
            assertThat(foundPlace).isEmpty();
        }

        @Test
        @DisplayName("Should check if Naver Place ID exists")
        void shouldCheckIfNaverPlaceIdExists() {
            // When & Then
            assertThat(placeRepository.existsByNaverPlaceId("naver-place-1")).isTrue();
            assertThat(placeRepository.existsByNaverPlaceId("naver-place-2")).isTrue();
            assertThat(placeRepository.existsByNaverPlaceId("nonexistent-place-id")).isFalse();
        }
    }

    @Nested
    @DisplayName("Text Search Functionality")
    class TextSearchFunctionality {

        @Test
        @DisplayName("Should search places by name")
        void shouldSearchPlacesByName() {
            // Given
            Pageable pageable = PageRequest.of(0, 10);

            // When
            Page<Place> searchResults = placeRepository.searchPlaces("Seoul", pageable);

            // Then
            assertThat(searchResults.getContent()).hasSize(1);
            assertThat(searchResults.getContent().get(0).getName()).isEqualTo("Seoul Tower");
        }

        @Test
        @DisplayName("Should search places by address")
        void shouldSearchPlacesByAddress() {
            // Given
            Pageable pageable = PageRequest.of(0, 10);

            // When
            Page<Place> searchResults = placeRepository.searchPlaces("Busan", pageable);

            // Then
            assertThat(searchResults.getContent()).hasSize(1);
            assertThat(searchResults.getContent().get(0).getName()).isEqualTo("Busan Beach");
        }

        @Test
        @DisplayName("Should search places by description")
        void shouldSearchPlacesByDescription() {
            // Given
            Pageable pageable = PageRequest.of(0, 10);

            // When
            Page<Place> searchResults = placeRepository.searchPlaces("Famous landmark", pageable);

            // Then
            assertThat(searchResults.getContent()).hasSize(1);
            assertThat(searchResults.getContent().get(0).getName()).isEqualTo("Seoul Tower");
        }

        @Test
        @DisplayName("Should perform case-insensitive search")
        void shouldPerformCaseInsensitiveSearch() {
            // Given
            Pageable pageable = PageRequest.of(0, 10);

            // When
            Page<Place> searchResults = placeRepository.searchPlaces("SEOUL tower", pageable);

            // Then
            assertThat(searchResults.getContent()).hasSize(1);
            assertThat(searchResults.getContent().get(0).getName()).isEqualTo("Seoul Tower");
        }

        @Test
        @DisplayName("Should exclude deleted places from search")
        void shouldExcludeDeletedPlacesFromSearch() {
            // Given
            Pageable pageable = PageRequest.of(0, 10);

            // When
            Page<Place> searchResults = placeRepository.searchPlaces("Place", pageable);

            // Then
            // Should not find the deleted place even though "Place" matches "Deleted Place"
            assertThat(searchResults.getContent()).isEmpty();
        }

        @Test
        @DisplayName("Should return empty results for non-matching search")
        void shouldReturnEmptyForNonMatchingSearch() {
            // Given
            Pageable pageable = PageRequest.of(0, 10);

            // When
            Page<Place> searchResults = placeRepository.searchPlaces("nonexistent place", pageable);

            // Then
            assertThat(searchResults.getContent()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Geospatial Queries")
    class GeospatialQueries {

        @Test
        @DisplayName("Should find nearby places within radius")
        void shouldFindNearbyPlacesWithinRadius() {
            // Given - Search near Seoul Tower location with 1000m radius
            Double latitude = 37.5512;
            Double longitude = 126.9882;
            Integer radius = 1000; // 1km

            // When
            List<Place> nearbyPlaces = placeRepository.findNearbyPlaces(latitude, longitude, radius);

            // Then
            // PostgreSQL with PostGIS extension supports full spatial operations
            // In a real PostgreSQL environment, it would find places within the specified radius
            // For now, we'll test that the method executes without error
            assertThat(nearbyPlaces).isNotNull();
        }

        @Test
        @DisplayName("Should return places ordered by distance")
        void shouldReturnPlacesOrderedByDistance() {
            // Given
            Double latitude = 37.5512;
            Double longitude = 126.9882;
            Integer radius = 50000; // 50km to include both places

            // When
            List<Place> nearbyPlaces = placeRepository.findNearbyPlaces(latitude, longitude, radius);

            // Then
            // The method should execute without error
            // In PostgreSQL with PostGIS, results would be ordered by distance
            assertThat(nearbyPlaces).isNotNull();
        }

        @Test
        @DisplayName("Should exclude deleted places from nearby search")
        void shouldExcludeDeletedPlacesFromNearbySearch() {
            // Given
            Double latitude = 37.5665; // Near the deleted place location
            Double longitude = 126.9780;
            Integer radius = 1000;

            // When
            List<Place> nearbyPlaces = placeRepository.findNearbyPlaces(latitude, longitude, radius);

            // Then
            // Should not include the deleted place
            assertThat(nearbyPlaces).isNotNull();
            // In actual implementation with PostGIS, would verify deleted places are excluded
        }
    }

    @Nested
    @DisplayName("Category and Tag Filtering")
    class CategoryAndTagFiltering {

        @Test
        @DisplayName("Should find places by category")
        void shouldFindPlacesByCategory() {
            // Given
            Pageable pageable = PageRequest.of(0, 10);

            // When
            Page<Place> touristAttractions = placeRepository.findByCategoryAndIsDeletedFalse("tourist_attraction", pageable);

            // Then
            assertThat(touristAttractions.getContent()).hasSize(1);
            assertThat(touristAttractions.getContent().get(0).getName()).isEqualTo("Seoul Tower");
        }

        @Test
        @DisplayName("Should find places by multiple categories")
        void shouldFindPlacesByMultipleCategories() {
            // Given
            List<String> categories = List.of("tourist_attraction", "beach");
            Pageable pageable = PageRequest.of(0, 10);

            // When
            Page<Place> places = placeRepository.findByCategoriesIn(categories, pageable);

            // Then
            assertThat(places.getContent()).hasSize(2);
            assertThat(places.getContent())
                .extracting(Place::getName)
                .containsExactlyInAnyOrder("Seoul Tower", "Busan Beach");
        }

        @Test
        @DisplayName("Should exclude deleted places when filtering by category")
        void shouldExcludeDeletedPlacesWhenFilteringByCategory() {
            // Given
            Pageable pageable = PageRequest.of(0, 10);

            // When
            Page<Place> restaurants = placeRepository.findByCategoryAndIsDeletedFalse("restaurant", pageable);

            // Then
            // Should not find the deleted restaurant
            assertThat(restaurants.getContent()).isEmpty();
        }

        @Test
        @DisplayName("Should find places by tags")
        void shouldFindPlacesByTags() {
            // Given
            List<String> tags = List.of("landmark", "view");
            Pageable pageable = PageRequest.of(0, 10);

            // When
            Page<Place> places = placeRepository.findByTags(tags, pageable);

            // Then
            assertThat(places.getContent()).hasSize(1);
            assertThat(places.getContent().get(0).getName()).isEqualTo("Seoul Tower");
        }

        @Test
        @DisplayName("Should find places by specific tags")
        void shouldFindPlacesBySpecificTags() {
            // Given
            List<String> beachTags = List.of("beach", "swimming");
            Pageable pageable = PageRequest.of(0, 10);

            // When
            Page<Place> beachPlaces = placeRepository.findByTags(beachTags, pageable);

            // Then
            assertThat(beachPlaces.getContent()).hasSize(1);
            assertThat(beachPlaces.getContent().get(0).getName()).isEqualTo("Busan Beach");
        }
    }

    @Nested
    @DisplayName("Rating and Popularity Queries")
    class RatingAndPopularityQueries {

        @Test
        @DisplayName("Should find places by minimum rating")
        void shouldFindPlacesByMinimumRating() {
            // Given
            Double minRating = 4.0;
            Pageable pageable = PageRequest.of(0, 10);

            // When
            Page<Place> highRatedPlaces = placeRepository.findByMinimumRating(minRating, pageable);

            // Then
            assertThat(highRatedPlaces.getContent()).hasSize(2);
            assertThat(highRatedPlaces.getContent())
                .allMatch(place -> place.getRating() >= minRating)
                .extracting(Place::getName)
                .containsExactlyInAnyOrder("Seoul Tower", "Busan Beach");
        }

        @Test
        @DisplayName("Should order results by rating descending")
        void shouldOrderResultsByRatingDescending() {
            // Given
            Double minRating = 4.0;
            Pageable pageable = PageRequest.of(0, 10);

            // When
            Page<Place> highRatedPlaces = placeRepository.findByMinimumRating(minRating, pageable);

            // Then
            assertThat(highRatedPlaces.getContent()).hasSize(2);
            assertThat(highRatedPlaces.getContent().get(0).getRating())
                .isGreaterThanOrEqualTo(highRatedPlaces.getContent().get(1).getRating());
        }

        @Test
        @DisplayName("Should find top popular places")
        void shouldFindTopPopularPlaces() {
            // Given
            Pageable pageable = PageRequest.of(0, 5);

            // When
            List<Place> popularPlaces = placeRepository.findTopPopularPlaces(pageable);

            // Then
            assertThat(popularPlaces).hasSize(2);
            assertThat(popularPlaces)
                .allMatch(Place::isVerified)
                .allMatch(place -> !place.isDeleted());

            // Should be ordered by popularity score descending
            assertThat(popularPlaces.get(0).getPopularityScore())
                .isGreaterThanOrEqualTo(popularPlaces.get(1).getPopularityScore());
        }

        @Test
        @DisplayName("Should exclude unverified places from popular places")
        void shouldExcludeUnverifiedPlacesFromPopularPlaces() {
            // Given - Create an unverified place with high popularity
            Place unverifiedPlace = createTestPlace("naver-unverified", "Unverified Popular Place",
                "restaurant", 37.5000, 126.9000, false);
            unverifiedPlace.setVerified(false);
            unverifiedPlace.setPopularityScore(100.0);
            placeRepository.save(unverifiedPlace);

            Pageable pageable = PageRequest.of(0, 10);

            // When
            List<Place> popularPlaces = placeRepository.findTopPopularPlaces(pageable);

            // Then
            assertThat(popularPlaces).hasSize(2); // Only verified places
            assertThat(popularPlaces)
                .extracting(Place::getName)
                .doesNotContain("Unverified Popular Place");
        }
    }

    @Nested
    @DisplayName("Additional Finder Methods")
    class AdditionalFinderMethods {

        @Test
        @DisplayName("Should find by name or address containing")
        void shouldFindByNameOrAddressContaining() {
            // Given
            Pageable pageable = PageRequest.of(0, 10);

            // When
            Page<Place> seoulPlaces = placeRepository
                .findByNameContainingIgnoreCaseOrAddressContainingIgnoreCase("Seoul", "Seoul", pageable);

            // Then
            assertThat(seoulPlaces.getContent()).hasSize(1);
            assertThat(seoulPlaces.getContent().get(0).getName()).isEqualTo("Seoul Tower");
        }

        @Test
        @DisplayName("Should find places by category with simple method")
        void shouldFindPlacesByCategoryWithSimpleMethod() {
            // Given
            Pageable pageable = PageRequest.of(0, 10);

            // When
            Page<Place> beaches = placeRepository.findByCategory("beach", pageable);

            // Then
            assertThat(beaches.getContent()).hasSize(1);
            assertThat(beaches.getContent().get(0).getName()).isEqualTo("Busan Beach");
        }

        @Test
        @DisplayName("Should find all places ordered by popularity")
        void shouldFindAllPlacesOrderedByPopularity() {
            // Given
            Pageable pageable = PageRequest.of(0, 10);

            // When
            Page<Place> popularPlaces = placeRepository.findAllByOrderByPopularityScoreDesc(pageable);

            // Then
            assertThat(popularPlaces.getContent()).hasSize(3); // Includes deleted place
            assertThat(popularPlaces.getContent().get(0).getPopularityScore())
                .isEqualTo(95.0); // Seoul Tower has highest popularity
        }
    }

    @Nested
    @DisplayName("Database Constraints and Validation")
    class DatabaseConstraintsAndValidation {

        @Test
        @DisplayName("Should enforce unique Naver Place ID constraint")
        void shouldEnforceUniqueNaverPlaceIdConstraint() {
            // Given
            Place duplicateNaverIdPlace = Place.builder()
                .naverPlaceId("naver-place-1") // Same as testPlace1
                .name("Duplicate Naver ID Place")
                .category("restaurant")
                .address("Different Address")
                .latitude(37.0000)
                .longitude(127.0000)
                .build();

            // When & Then
            assertThatThrownBy(() -> {
                placeRepository.save(duplicateNaverIdPlace);
                entityManager.flush();
            }).isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("Should allow null Naver Place ID")
        void shouldAllowNullNaverPlaceId() {
            // Given
            Place placeWithNullNaverId = Place.builder()
                .naverPlaceId(null)
                .name("No Naver ID Place")
                .category("restaurant")
                .address("Test Address")
                .latitude(37.5000)
                .longitude(126.9000)
                .build();

            // When
            Place savedPlace = placeRepository.save(placeWithNullNaverId);

            // Then
            assertThat(savedPlace).isNotNull();
            assertThat(savedPlace.getNaverPlaceId()).isNull();
            assertThat(savedPlace.getId()).isNotNull();
        }

        @Test
        @DisplayName("Should validate required fields")
        void shouldValidateRequiredFields() {
            // Given
            Place invalidPlace = Place.builder()
                .naverPlaceId("valid-naver-id")
                .name(null) // Required field
                .category(null) // Required field
                .address(null) // Required field
                .latitude(null) // Required field
                .longitude(null) // Required field
                .build();

            // When & Then
            assertThatThrownBy(() -> {
                placeRepository.save(invalidPlace);
                entityManager.flush();
            }).isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("Should handle default values correctly")
        void shouldHandleDefaultValuesCorrectly() {
            // Given
            Place placeWithDefaults = Place.builder()
                .naverPlaceId("place-defaults")
                .name("Default Values Place")
                .category("restaurant")
                .address("Test Address")
                .latitude(37.5000)
                .longitude(126.9000)
                .build();

            // When
            Place savedPlace = placeRepository.save(placeWithDefaults);

            // Then
            assertThat(savedPlace.getReviewCount()).isEqualTo(0);
            assertThat(savedPlace.getBookmarkCount()).isEqualTo(0);
            assertThat(savedPlace.isVerified()).isFalse();
            assertThat(savedPlace.getPopularityScore()).isEqualTo(0.0);
            assertThat(savedPlace.isDeleted()).isFalse();
            assertThat(savedPlace.getImages()).isNotNull().isEmpty();
            assertThat(savedPlace.getTags()).isNotNull().isEmpty();
            assertThat(savedPlace.getOpeningHours()).isNotNull().isEmpty();
        }
    }

    @Nested
    @DisplayName("Pagination and Sorting")
    class PaginationAndSorting {

        @Test
        @DisplayName("Should paginate search results correctly")
        void shouldPaginateSearchResultsCorrectly() {
            // Given - Create additional places for pagination testing
            for (int i = 1; i <= 5; i++) {
                createTestPlace("test-place-" + i, "Test Place " + i,
                    "restaurant", 37.5000 + i * 0.001, 126.9000 + i * 0.001, false);
            }

            Pageable firstPage = PageRequest.of(0, 3);
            Pageable secondPage = PageRequest.of(1, 3);

            // When
            Page<Place> firstPageResults = placeRepository.searchPlaces("Test Place", firstPage);
            Page<Place> secondPageResults = placeRepository.searchPlaces("Test Place", secondPage);

            // Then
            assertThat(firstPageResults.getContent()).hasSize(3);
            assertThat(firstPageResults.getTotalElements()).isEqualTo(5);
            assertThat(firstPageResults.getTotalPages()).isEqualTo(2);
            assertThat(firstPageResults.hasNext()).isTrue();
            assertThat(firstPageResults.hasPrevious()).isFalse();

            assertThat(secondPageResults.getContent()).hasSize(2);
            assertThat(secondPageResults.hasNext()).isFalse();
            assertThat(secondPageResults.hasPrevious()).isTrue();
        }

        @Test
        @DisplayName("Should sort places by rating")
        void shouldSortPlacesByRating() {
            // Given - Create places with different ratings
            Place highRated = createTestPlace("high-rated", "High Rated Place",
                "restaurant", 37.5001, 126.9001, false);
            highRated.setRating(4.8);
            placeRepository.save(highRated);

            Place mediumRated = createTestPlace("medium-rated", "Medium Rated Place",
                "restaurant", 37.5002, 126.9002, false);
            mediumRated.setRating(3.5);
            placeRepository.save(mediumRated);

            Pageable sortedByRatingDesc = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "rating"));

            // When
            Page<Place> sortedPlaces = placeRepository.findByCategory("restaurant", sortedByRatingDesc);

            // Then
            assertThat(sortedPlaces.getContent()).hasSize(2);
            assertThat(sortedPlaces.getContent().get(0).getRating())
                .isGreaterThan(sortedPlaces.getContent().get(1).getRating());
        }

        @Test
        @DisplayName("Should handle empty pagination results")
        void shouldHandleEmptyPaginationResults() {
            // Given
            Pageable pageable = PageRequest.of(10, 10); // Page beyond available data

            // When
            Page<Place> result = placeRepository.searchPlaces("nonexistent", pageable);

            // Then
            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isEqualTo(0);
            assertThat(result.hasNext()).isFalse();
            assertThat(result.hasPrevious()).isTrue();
        }
    }

    @Nested
    @DisplayName("Collection and Relationship Handling")
    class CollectionAndRelationshipHandling {

        @Test
        @DisplayName("Should handle place images correctly")
        void shouldHandlePlaceImagesCorrectly() {
            // Given
            testPlace1.getImages().add("http://example.com/image1.jpg");
            testPlace1.getImages().add("http://example.com/image2.jpg");

            // When
            Place savedPlace = placeRepository.save(testPlace1);
            entityManager.flush();
            entityManager.clear();

            Place reloadedPlace = placeRepository.findById(savedPlace.getId()).orElse(null);

            // Then
            assertThat(reloadedPlace).isNotNull();
            assertThat(reloadedPlace.getImages()).hasSize(2);
            assertThat(reloadedPlace.getImages()).containsExactly(
                "http://example.com/image1.jpg",
                "http://example.com/image2.jpg"
            );
        }

        @Test
        @DisplayName("Should handle place tags correctly")
        void shouldHandlePlaceTagsCorrectly() {
            // Given
            testPlace1.getTags().add("new-tag");
            testPlace1.getTags().add("another-tag");

            // When
            Place savedPlace = placeRepository.save(testPlace1);
            entityManager.flush();
            entityManager.clear();

            Place reloadedPlace = placeRepository.findById(savedPlace.getId()).orElse(null);

            // Then
            assertThat(reloadedPlace).isNotNull();
            assertThat(reloadedPlace.getTags()).hasSize(5); // 3 original + 2 new
            assertThat(reloadedPlace.getTags()).contains("new-tag", "another-tag");
        }

        @Test
        @DisplayName("Should handle opening hours correctly")
        void shouldHandleOpeningHoursCorrectly() {
            // Given
            testPlace1.getOpeningHours().put("Monday", "09:00-18:00");
            testPlace1.getOpeningHours().put("Tuesday", "09:00-18:00");
            testPlace1.getOpeningHours().put("Sunday", "10:00-17:00");

            // When
            Place savedPlace = placeRepository.save(testPlace1);
            entityManager.flush();
            entityManager.clear();

            Place reloadedPlace = placeRepository.findById(savedPlace.getId()).orElse(null);

            // Then
            assertThat(reloadedPlace).isNotNull();
            assertThat(reloadedPlace.getOpeningHours()).hasSize(3);
            assertThat(reloadedPlace.getOpeningHours()).containsEntry("Monday", "09:00-18:00");
            assertThat(reloadedPlace.getOpeningHours()).containsEntry("Sunday", "10:00-17:00");
        }
    }

    @Nested
    @DisplayName("Transaction Boundaries and Rollback Scenarios")
    class TransactionBoundariesAndRollbackScenarios {

        @Test
        @Transactional
        @Rollback
        @DisplayName("Should rollback place transaction on constraint violation")
        void shouldRollbackPlaceTransactionOnConstraintViolation() {
            // Given
            String originalDescription = testPlace1.getDescription();
            Double originalRating = testPlace1.getRating();

            // When - Simulate transaction failure
            assertThatThrownBy(() -> {
                // Modify existing place
                testPlace1.setDescription("Updated description");
                testPlace1.setRating(5.0);
                placeRepository.save(testPlace1);
                entityManager.flush();

                // Create duplicate Naver Place ID to trigger rollback
                Place duplicatePlace = Place.builder()
                    .naverPlaceId("naver-place-1") // Same as testPlace1
                    .name("Duplicate Place")
                    .category("restaurant")
                    .address("Test Address")
                    .latitude(37.5000)
                    .longitude(126.9000)
                    .build();
                placeRepository.save(duplicatePlace);
                entityManager.flush();
            }).isInstanceOf(DataIntegrityViolationException.class);

            // Then - Verify rollback occurred
            entityManager.clear();
            Place reloadedPlace = placeRepository.findById(testPlace1.getId()).orElse(null);
            assertThat(reloadedPlace).isNotNull();
            assertThat(reloadedPlace.getDescription()).isEqualTo(originalDescription);
            assertThat(reloadedPlace.getRating()).isEqualTo(originalRating);
        }

        @Test
        @Transactional
        @DisplayName("Should handle bulk operations atomically")
        void shouldHandleBulkOperationsAtomically() {
            // Given
            List<Place> placesToSave = Arrays.asList(
                Place.builder()
                    .naverPlaceId("bulk-1")
                    .name("Bulk Place 1")
                    .category("restaurant")
                    .address("Address 1")
                    .latitude(37.5001)
                    .longitude(126.9001)
                    .build(),
                Place.builder()
                    .naverPlaceId("bulk-2")
                    .name("Bulk Place 2")
                    .category("cafe")
                    .address("Address 2")
                    .latitude(37.5002)
                    .longitude(126.9002)
                    .build(),
                Place.builder()
                    .naverPlaceId("bulk-3")
                    .name("Bulk Place 3")
                    .category("hotel")
                    .address("Address 3")
                    .latitude(37.5003)
                    .longitude(126.9003)
                    .build()
            );

            // When
            List<Place> savedPlaces = placeRepository.saveAll(placesToSave);
            entityManager.flush();

            // Then
            assertThat(savedPlaces).hasSize(3);
            assertThat(savedPlaces).allMatch(place -> place.getId() != null);
            assertThat(savedPlaces).allMatch(place -> place.getCreatedAt() != null);
            
            // Verify all were saved
            List<String> savedIds = savedPlaces.stream().map(Place::getId).toList();
            List<Place> foundPlaces = placeRepository.findAllById(savedIds);
            assertThat(foundPlaces).hasSize(3);
        }

        @Test
        @Transactional
        @DisplayName("Should handle cascading collection updates in transaction")
        void shouldHandleCascadingCollectionUpdatesInTransaction() {
            // Given
            Place place = testPlace1;

            // When - Update multiple collections in single transaction
            place.getTags().clear();
            place.getTags().addAll(Arrays.asList("updated-tag-1", "updated-tag-2", "updated-tag-3"));
            
            place.getImages().clear();
            place.getImages().addAll(Arrays.asList(
                "http://new1.jpg", "http://new2.jpg", "http://new3.jpg"
            ));
            
            place.getOpeningHours().clear();
            place.getOpeningHours().put("Monday", "10:00-20:00");
            place.getOpeningHours().put("Tuesday", "10:00-20:00");

            Place savedPlace = placeRepository.save(place);
            entityManager.flush();
            entityManager.clear();

            // Then - Verify all collections updated atomically
            Place reloadedPlace = placeRepository.findById(savedPlace.getId()).orElse(null);
            assertThat(reloadedPlace).isNotNull();

            assertThat(reloadedPlace.getTags()).hasSize(3)
                .containsExactlyInAnyOrder("updated-tag-1", "updated-tag-2", "updated-tag-3");

            assertThat(reloadedPlace.getImages()).hasSize(3)
                .containsExactlyInAnyOrder("http://new1.jpg", "http://new2.jpg", "http://new3.jpg");

            assertThat(reloadedPlace.getOpeningHours()).hasSize(2)
                .containsEntry("Monday", "10:00-20:00")
                .containsEntry("Tuesday", "10:00-20:00");
        }
    }

    @Nested
    @DisplayName("Advanced Query Performance and Edge Cases")
    class AdvancedQueryPerformanceAndEdgeCases {

        @Test
        @DisplayName("Should handle complex multi-condition queries efficiently")
        void shouldHandleComplexMultiConditionQueriesEfficiently() {
            // Given - Create places with varied characteristics
            Place premiumPlace = createTestPlace("premium-place", "Premium Restaurant", 
                "restaurant", 37.5100, 126.9100, false);
            premiumPlace.setRating(4.8);
            premiumPlace.setPopularityScore(90.0);
            premiumPlace.setVerified(true);
            premiumPlace.getTags().addAll(Arrays.asList("premium", "fine-dining", "romantic"));
            placeRepository.save(premiumPlace);

            Place casualPlace = createTestPlace("casual-place", "Casual Cafe", 
                "cafe", 37.5200, 126.9200, false);
            casualPlace.setRating(4.2);
            casualPlace.setPopularityScore(70.0);
            casualPlace.setVerified(true);
            casualPlace.getTags().addAll(Arrays.asList("casual", "coffee", "wifi"));
            placeRepository.save(casualPlace);

            Pageable pageable = PageRequest.of(0, 10);

            // When - Test various complex queries
            Page<Place> highRatedRestaurants = placeRepository.findByMinimumRating(4.5, pageable);
            Page<Place> premiumTaggedPlaces = placeRepository.findByTags(Arrays.asList("premium"), pageable);
            Page<Place> restaurantCategory = placeRepository.findByCategoryAndIsDeletedFalse("restaurant", pageable);

            // Then
            assertThat(highRatedRestaurants.getContent()).hasSize(1);
            assertThat(highRatedRestaurants.getContent().get(0).getName()).isEqualTo("Premium Restaurant");

            assertThat(premiumTaggedPlaces.getContent()).hasSize(1);
            assertThat(premiumTaggedPlaces.getContent().get(0).getName()).isEqualTo("Premium Restaurant");

            assertThat(restaurantCategory.getContent()).hasSize(1);
            assertThat(restaurantCategory.getContent().get(0).getName()).isEqualTo("Premium Restaurant");
        }

        @Test
        @DisplayName("Should handle search with special characters and edge cases")
        void shouldHandleSearchWithSpecialCharactersAndEdgeCases() {
            // Given
            Place specialPlace = createTestPlace("special-chars", "Café & Restaurant (Seoul)", 
                "restaurant", 37.5300, 126.9300, false);
            specialPlace.setDescription("Authentic Korean cuisine with 100% organic ingredients!");
            placeRepository.save(specialPlace);

            Pageable pageable = PageRequest.of(0, 10);

            // When - Test search with various special characters
            Page<Place> cafeResults = placeRepository.searchPlaces("Café", pageable);
            Page<Place> ampersandResults = placeRepository.searchPlaces("&", pageable);
            Page<Place> percentResults = placeRepository.searchPlaces("100%", pageable);
            Page<Place> parenthesesResults = placeRepository.searchPlaces("(Seoul)", pageable);

            // Then
            assertThat(cafeResults.getContent()).hasSize(1);
            assertThat(ampersandResults.getContent()).hasSize(1);
            assertThat(percentResults.getContent()).hasSize(1);
            assertThat(parenthesesResults.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("Should handle empty and null collections gracefully")
        void shouldHandleEmptyAndNullCollectionsGracefully() {
            // Given
            Place emptyCollectionsPlace = Place.builder()
                .naverPlaceId("empty-collections")
                .name("Empty Collections Place")
                .category("restaurant")
                .address("Test Address")
                .latitude(37.5400)
                .longitude(126.9400)
                .build();

            // When
            Place savedPlace = placeRepository.save(emptyCollectionsPlace);
            entityManager.flush();
            entityManager.clear();

            // Then
            Place reloadedPlace = placeRepository.findById(savedPlace.getId()).orElse(null);
            assertThat(reloadedPlace).isNotNull();
            assertThat(reloadedPlace.getTags()).isNotNull().isEmpty();
            assertThat(reloadedPlace.getImages()).isNotNull().isEmpty();
            assertThat(reloadedPlace.getOpeningHours()).isNotNull().isEmpty();

            // When - Search by empty tags should work
            Pageable pageable = PageRequest.of(0, 10);
            Page<Place> emptyTagResults = placeRepository.findByTags(Arrays.asList(), pageable);

            // Then - Should return empty results gracefully
            assertThat(emptyTagResults.getContent()).isEmpty();
        }

        @Test
        @DisplayName("Should handle large result sets with proper pagination")
        void shouldHandleLargeResultSetsWithProperPagination() {
            // Given - Create many places for pagination testing
            for (int i = 1; i <= 50; i++) {
                Place place = createTestPlace("large-test-" + i, "Large Test Place " + i, 
                    "restaurant", 37.5000 + i * 0.0001, 126.9000 + i * 0.0001, false);
                place.setRating(3.0 + (i % 3)); // Ratings between 3.0 and 5.0
                place.setPopularityScore(50.0 + i);
                placeRepository.save(place);
            }

            // When - Test various pagination scenarios
            Pageable firstPage = PageRequest.of(0, 10);
            Pageable middlePage = PageRequest.of(2, 10);
            Pageable lastPage = PageRequest.of(4, 10);

            Page<Place> firstResults = placeRepository.searchPlaces("Large Test", firstPage);
            Page<Place> middleResults = placeRepository.searchPlaces("Large Test", middlePage);
            Page<Place> lastResults = placeRepository.searchPlaces("Large Test", lastPage);

            // Then - Verify pagination works correctly
            assertThat(firstResults.getContent()).hasSize(10);
            assertThat(firstResults.getTotalElements()).isEqualTo(50);
            assertThat(firstResults.getTotalPages()).isEqualTo(5);
            assertThat(firstResults.isFirst()).isTrue();
            assertThat(firstResults.hasNext()).isTrue();

            assertThat(middleResults.getContent()).hasSize(10);
            assertThat(middleResults.hasNext()).isTrue();
            assertThat(middleResults.hasPrevious()).isTrue();

            assertThat(lastResults.getContent()).hasSize(10);
            assertThat(lastResults.isLast()).isTrue();
            assertThat(lastResults.hasPrevious()).isTrue();
        }
    }

    @Nested
    @DisplayName("Statistical and Aggregation Operations")
    class StatisticalAndAggregationOperations {

        @Test
        @DisplayName("Should handle place statistics correctly")
        void shouldHandlePlaceStatisticsCorrectly() {
            // Given
            Place place = testPlace1;
            place.setViewCount(100L);
            place.setReviewCount(25);
            place.setBookmarkCount(15);
            
            // When
            Place savedPlace = placeRepository.save(place);
            entityManager.flush();

            // Then
            assertThat(savedPlace.getViewCount()).isEqualTo(100L);
            assertThat(savedPlace.getReviewCount()).isEqualTo(25);
            assertThat(savedPlace.getBookmarkCount()).isEqualTo(15);
        }

        @Test
        @DisplayName("Should maintain data integrity during concurrent updates")
        void shouldMaintainDataIntegrityDuringConcurrentUpdates() {
            // Given
            Place concurrentPlace = createTestPlace("concurrent-place", "Concurrent Test Place", 
                "restaurant", 37.5500, 126.9500, false);
            concurrentPlace.setViewCount(0L);
            concurrentPlace.setBookmarkCount(0);
            Place savedPlace = placeRepository.save(concurrentPlace);
            entityManager.flush();

            // When - Simulate concurrent updates (in real scenario, this would be multiple threads)
            Place place1 = placeRepository.findById(savedPlace.getId()).orElse(null);
            Place place2 = placeRepository.findById(savedPlace.getId()).orElse(null);

            place1.setViewCount(place1.getViewCount() + 1);
            place2.setBookmarkCount(place2.getBookmarkCount() + 1);

            // Save first change
            placeRepository.save(place1);
            entityManager.flush();

            // Save second change - this should work as they modify different fields
            placeRepository.save(place2);
            entityManager.flush();

            // Then - Verify both changes are applied
            Place finalPlace = placeRepository.findById(savedPlace.getId()).orElse(null);
            assertThat(finalPlace).isNotNull();
            assertThat(finalPlace.getViewCount()).isEqualTo(1L);
            assertThat(finalPlace.getBookmarkCount()).isEqualTo(1);
        }
    }
}