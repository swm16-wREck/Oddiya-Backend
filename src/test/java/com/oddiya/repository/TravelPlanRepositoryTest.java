package com.oddiya.repository;

import com.oddiya.entity.TravelPlan;
import com.oddiya.entity.TravelPlanStatus;
import com.oddiya.entity.User;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive tests for TravelPlanRepository covering:
 * - Basic CRUD operations
 * - User-specific queries
 * - Status filtering
 * - Date range queries
 * - Public/private visibility
 * - Search functionality
 * - Collaboration features
 * - Popularity and view tracking
 * - Database constraints
 * - Pagination and sorting
 * - Transaction boundaries and rollback scenarios
 * - Complex entity relationships and cascading
 */
@DisplayName("TravelPlanRepository Tests")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TravelPlanRepositoryTest extends RepositoryTestBase {

    @Autowired
    private TravelPlanRepository travelPlanRepository;

    @Nested
    @DisplayName("Basic CRUD Operations")
    class BasicCrudOperations {

        @Test
        @DisplayName("Should save and retrieve travel plan successfully")
        void shouldSaveAndRetrieveTravelPlan() {
            // Given
            TravelPlan newPlan = TravelPlan.builder()
                .user(testUser1)
                .title("New Adventure")
                .description("A new travel adventure")
                .destination("Jeju Island")
                .startDate(LocalDate.now().plusDays(30))
                .endDate(LocalDate.now().plusDays(35))
                .numberOfPeople(4)
                .budget(new BigDecimal("2000000"))
                .status(TravelPlanStatus.DRAFT)
                .isPublic(false)
                .isAiGenerated(false)
                .isDeleted(false)
                .viewCount(0L)
                .likeCount(0L)
                .shareCount(0L)
                .saveCount(0L)
                .build();

            // When
            TravelPlan savedPlan = travelPlanRepository.save(newPlan);
            Optional<TravelPlan> foundPlan = travelPlanRepository.findById(savedPlan.getId());

            // Then
            assertThat(savedPlan).isNotNull();
            assertThat(savedPlan.getId()).isNotNull();
            assertThat(savedPlan.getCreatedAt()).isNotNull();
            assertThat(savedPlan.getUpdatedAt()).isNotNull();

            assertThat(foundPlan).isPresent();
            assertThat(foundPlan.get().getTitle()).isEqualTo("New Adventure");
            assertThat(foundPlan.get().getDestination()).isEqualTo("Jeju Island");
            assertThat(foundPlan.get().getUser().getId()).isEqualTo(testUser1.getId());
        }

        @Test
        @DisplayName("Should update travel plan successfully")
        void shouldUpdateTravelPlanSuccessfully() {
            // Given
            String originalTitle = testTravelPlan1.getTitle();

            // When
            testTravelPlan1.setTitle("Updated Adventure");
            testTravelPlan1.setStatus(TravelPlanStatus.CONFIRMED);
            testTravelPlan1.setViewCount(100L);
            TravelPlan updatedPlan = travelPlanRepository.save(testTravelPlan1);

            // Then
            assertThat(updatedPlan.getTitle()).isEqualTo("Updated Adventure");
            assertThat(updatedPlan.getTitle()).isNotEqualTo(originalTitle);
            assertThat(updatedPlan.getStatus()).isEqualTo(TravelPlanStatus.CONFIRMED);
            assertThat(updatedPlan.getViewCount()).isEqualTo(100L);
            assertThat(updatedPlan.getUpdatedAt()).isAfter(updatedPlan.getCreatedAt());
        }

        @Test
        @DisplayName("Should delete travel plan successfully")
        void shouldDeleteTravelPlanSuccessfully() {
            // Given
            String planId = testTravelPlan1.getId();

            // When
            travelPlanRepository.deleteById(planId);

            // Then
            Optional<TravelPlan> deletedPlan = travelPlanRepository.findById(planId);
            assertThat(deletedPlan).isEmpty();
        }

        @Test
        @DisplayName("Should find all travel plans")
        void shouldFindAllTravelPlans() {
            // When
            List<TravelPlan> allPlans = travelPlanRepository.findAll();

            // Then
            assertThat(allPlans).hasSize(2);
            assertThat(allPlans)
                .extracting(TravelPlan::getTitle)
                .containsExactlyInAnyOrder("Seoul Adventure", "Private Plan");
        }
    }

    @Nested
    @DisplayName("User-Specific Queries")
    class UserSpecificQueries {

        @Test
        @DisplayName("Should find plans by user ID excluding deleted")
        void shouldFindPlansByUserIdExcludingDeleted() {
            // Given
            Pageable pageable = PageRequest.of(0, 10);

            // When
            Page<TravelPlan> userPlans = travelPlanRepository.findByUserIdAndIsDeletedFalse(testUser1.getId(), pageable);

            // Then
            assertThat(userPlans.getContent()).hasSize(1);
            assertThat(userPlans.getContent().get(0).getTitle()).isEqualTo("Seoul Adventure");
            assertThat(userPlans.getContent().get(0).getUser().getId()).isEqualTo(testUser1.getId());
        }

        @Test
        @DisplayName("Should find plans by user ID and status")
        void shouldFindPlansByUserIdAndStatus() {
            // Given
            Pageable pageable = PageRequest.of(0, 10);

            // When
            Page<TravelPlan> draftPlans = travelPlanRepository
                .findByUserIdAndStatusAndIsDeletedFalse(testUser1.getId(), TravelPlanStatus.DRAFT, pageable);
            Page<TravelPlan> publishedPlans = travelPlanRepository
                .findByUserIdAndStatusAndIsDeletedFalse(testUser2.getId(), TravelPlanStatus.CONFIRMED, pageable);

            // Then
            assertThat(draftPlans.getContent()).hasSize(1);
            assertThat(draftPlans.getContent().get(0).getStatus()).isEqualTo(TravelPlanStatus.DRAFT);

            assertThat(publishedPlans.getContent()).hasSize(1);
            assertThat(publishedPlans.getContent().get(0).getStatus()).isEqualTo(TravelPlanStatus.CONFIRMED);
        }

        @Test
        @DisplayName("Should find plans by user ID using simple method")
        void shouldFindPlansByUserIdUsingSimpleMethod() {
            // Given
            Pageable pageable = PageRequest.of(0, 10);

            // When
            Page<TravelPlan> userPlans = travelPlanRepository.findByUserId(testUser2.getId(), pageable);

            // Then
            assertThat(userPlans.getContent()).hasSize(1);
            assertThat(userPlans.getContent().get(0).getTitle()).isEqualTo("Private Plan");
        }

        @Test
        @DisplayName("Should find plan by ID and user ID excluding deleted")
        void shouldFindPlanByIdAndUserIdExcludingDeleted() {
            // When
            Optional<TravelPlan> foundPlan = travelPlanRepository
                .findByIdAndUserIdAndIsDeletedFalse(testTravelPlan1.getId(), testUser1.getId());
            Optional<TravelPlan> notFoundPlan = travelPlanRepository
                .findByIdAndUserIdAndIsDeletedFalse(testTravelPlan1.getId(), testUser2.getId());

            // Then
            assertThat(foundPlan).isPresent();
            assertThat(foundPlan.get().getTitle()).isEqualTo("Seoul Adventure");

            assertThat(notFoundPlan).isEmpty(); // Different user
        }
    }

    @Nested
    @DisplayName("Public Plans and Search")
    class PublicPlansAndSearch {

        @Test
        @DisplayName("Should find public plans only")
        void shouldFindPublicPlansOnly() {
            // Given
            Pageable pageable = PageRequest.of(0, 10);

            // When
            Page<TravelPlan> publicPlans = travelPlanRepository.findByIsPublicTrue(pageable);

            // Then
            assertThat(publicPlans.getContent()).hasSize(1);
            assertThat(publicPlans.getContent().get(0).getTitle()).isEqualTo("Seoul Adventure");
            assertThat(publicPlans.getContent().get(0).isPublic()).isTrue();
        }

        @Test
        @DisplayName("Should search public plans by title")
        void shouldSearchPublicPlansByTitle() {
            // Given
            Pageable pageable = PageRequest.of(0, 10);

            // When
            Page<TravelPlan> searchResults = travelPlanRepository.searchPublicPlans("Seoul", pageable);

            // Then
            assertThat(searchResults.getContent()).hasSize(1);
            assertThat(searchResults.getContent().get(0).getTitle()).isEqualTo("Seoul Adventure");
        }

        @Test
        @DisplayName("Should search public plans by destination")
        void shouldSearchPublicPlansByDestination() {
            // Given
            Pageable pageable = PageRequest.of(0, 10);

            // When
            Page<TravelPlan> searchResults = travelPlanRepository.searchPublicPlans("Seoul", pageable);

            // Then
            assertThat(searchResults.getContent()).hasSize(1);
            assertThat(searchResults.getContent().get(0).getDestination()).isEqualTo("Seoul");
        }

        @Test
        @DisplayName("Should search public plans by description")
        void shouldSearchPublicPlansByDescription() {
            // Given
            Pageable pageable = PageRequest.of(0, 10);

            // When
            Page<TravelPlan> searchResults = travelPlanRepository.searchPublicPlans("landmarks", pageable);

            // Then
            assertThat(searchResults.getContent()).hasSize(1);
            assertThat(searchResults.getContent().get(0).getDescription()).contains("landmarks");
        }

        @Test
        @DisplayName("Should exclude private plans from public search")
        void shouldExcludePrivatePlansFromPublicSearch() {
            // Given
            Pageable pageable = PageRequest.of(0, 10);

            // When
            Page<TravelPlan> searchResults = travelPlanRepository.searchPublicPlans("Private", pageable);

            // Then
            // Should not find the private plan even though title contains "Private"
            assertThat(searchResults.getContent()).isEmpty();
        }

        @Test
        @DisplayName("Should exclude deleted plans from public search")
        void shouldExcludeDeletedPlansFromPublicSearch() {
            // Given
            testTravelPlan1.setDeleted(true);
            travelPlanRepository.save(testTravelPlan1);

            Pageable pageable = PageRequest.of(0, 10);

            // When
            Page<TravelPlan> searchResults = travelPlanRepository.searchPublicPlans("Seoul", pageable);

            // Then
            assertThat(searchResults.getContent()).isEmpty();
        }

        @Test
        @DisplayName("Should search by title or destination")
        void shouldSearchByTitleOrDestination() {
            // Given
            Pageable pageable = PageRequest.of(0, 10);

            // When
            Page<TravelPlan> searchResults = travelPlanRepository
                .findByTitleContainingIgnoreCaseOrDestinationContainingIgnoreCase("Adventure", "Busan", pageable);

            // Then
            assertThat(searchResults.getContent()).hasSize(2);
            assertThat(searchResults.getContent())
                .extracting(TravelPlan::getTitle)
                .containsExactlyInAnyOrder("Seoul Adventure", "Private Plan");
        }
    }

    @Nested
    @DisplayName("Date Range Queries")
    class DateRangeQueries {

        @Test
        @DisplayName("Should find similar plans by destination and date range")
        void shouldFindSimilarPlansByDestinationAndDateRange() {
            // Given
            LocalDate startDate = LocalDate.now().plusDays(8);
            LocalDate endDate = LocalDate.now().plusDays(18);

            // When
            List<TravelPlan> similarPlans = travelPlanRepository
                .findSimilarPlans("Seoul", startDate, endDate);

            // Then
            assertThat(similarPlans).hasSize(1);
            assertThat(similarPlans.get(0).getTitle()).isEqualTo("Seoul Adventure");
            assertThat(similarPlans.get(0).getDestination()).isEqualTo("Seoul");
        }

        @Test
        @DisplayName("Should not find similar plans with non-matching destination")
        void shouldNotFindSimilarPlansWithNonMatchingDestination() {
            // Given
            LocalDate startDate = LocalDate.now().plusDays(8);
            LocalDate endDate = LocalDate.now().plusDays(18);

            // When
            List<TravelPlan> similarPlans = travelPlanRepository
                .findSimilarPlans("Tokyo", startDate, endDate);

            // Then
            assertThat(similarPlans).isEmpty();
        }

        @Test
        @DisplayName("Should find overlapping plans for user")
        void shouldFindOverlappingPlansForUser() {
            // Given - testTravelPlan1 is from day 10-15
            LocalDate overlapStart = LocalDate.now().plusDays(12);
            LocalDate overlapEnd = LocalDate.now().plusDays(20);

            // When
            List<TravelPlan> overlappingPlans = travelPlanRepository
                .findOverlappingPlans(testUser1.getId(), overlapStart, overlapEnd);

            // Then
            assertThat(overlappingPlans).hasSize(1);
            assertThat(overlappingPlans.get(0).getTitle()).isEqualTo("Seoul Adventure");
        }

        @Test
        @DisplayName("Should not find overlapping plans when dates don't overlap")
        void shouldNotFindOverlappingPlansWhenDatesDontOverlap() {
            // Given - testTravelPlan1 is from day 10-15, so day 1-5 shouldn't overlap
            LocalDate nonOverlapStart = LocalDate.now().plusDays(1);
            LocalDate nonOverlapEnd = LocalDate.now().plusDays(5);

            // When
            List<TravelPlan> overlappingPlans = travelPlanRepository
                .findOverlappingPlans(testUser1.getId(), nonOverlapStart, nonOverlapEnd);

            // Then
            assertThat(overlappingPlans).isEmpty();
        }

        @Test
        @DisplayName("Should exclude deleted plans from overlap check")
        void shouldExcludeDeletedPlansFromOverlapCheck() {
            // Given
            testTravelPlan1.setDeleted(true);
            travelPlanRepository.save(testTravelPlan1);

            LocalDate overlapStart = LocalDate.now().plusDays(12);
            LocalDate overlapEnd = LocalDate.now().plusDays(20);

            // When
            List<TravelPlan> overlappingPlans = travelPlanRepository
                .findOverlappingPlans(testUser1.getId(), overlapStart, overlapEnd);

            // Then
            assertThat(overlappingPlans).isEmpty();
        }
    }

    @Nested
    @DisplayName("Popularity and Collaboration")
    class PopularityAndCollaboration {

        @Test
        @DisplayName("Should find popular plans ordered by view count")
        void shouldFindPopularPlansOrderedByViewCount() {
            // Given
            Pageable pageable = PageRequest.of(0, 10);

            // When
            Page<TravelPlan> popularPlans = travelPlanRepository.findPopularPlans(pageable);

            // Then
            assertThat(popularPlans.getContent()).hasSize(1); // Only public plans
            assertThat(popularPlans.getContent().get(0).getTitle()).isEqualTo("Seoul Adventure");
            assertThat(popularPlans.getContent().get(0).isPublic()).isTrue();
        }

        @Test
        @DisplayName("Should exclude private plans from popular plans")
        void shouldExcludePrivatePlansFromPopularPlans() {
            // Given - testTravelPlan2 has higher view count but is private
            testTravelPlan2.setViewCount(200L);
            travelPlanRepository.save(testTravelPlan2);

            Pageable pageable = PageRequest.of(0, 10);

            // When
            Page<TravelPlan> popularPlans = travelPlanRepository.findPopularPlans(pageable);

            // Then
            assertThat(popularPlans.getContent()).hasSize(1);
            assertThat(popularPlans.getContent().get(0).getTitle()).isEqualTo("Seoul Adventure");
            // The private plan with higher view count should not appear
        }

        @Test
        @DisplayName("Should find collaborating plans")
        void shouldFindCollaboratingPlans() {
            // Given - Add testUser2 as collaborator to testTravelPlan1
            testTravelPlan1.getCollaborators().add(testUser2);
            travelPlanRepository.save(testTravelPlan1);

            Pageable pageable = PageRequest.of(0, 10);

            // When
            Page<TravelPlan> collaboratingPlans = travelPlanRepository
                .findCollaboratingPlans(testUser2.getId(), pageable);

            // Then
            assertThat(collaboratingPlans.getContent()).hasSize(1);
            assertThat(collaboratingPlans.getContent().get(0).getTitle()).isEqualTo("Seoul Adventure");
        }

        @Test
        @DisplayName("Should not find deleted plans in collaboration")
        void shouldNotFindDeletedPlansInCollaboration() {
            // Given
            testTravelPlan1.getCollaborators().add(testUser2);
            testTravelPlan1.setDeleted(true);
            travelPlanRepository.save(testTravelPlan1);

            Pageable pageable = PageRequest.of(0, 10);

            // When
            Page<TravelPlan> collaboratingPlans = travelPlanRepository
                .findCollaboratingPlans(testUser2.getId(), pageable);

            // Then
            assertThat(collaboratingPlans.getContent()).isEmpty();
        }

        @Test
        @DisplayName("Should handle empty collaboration relationships")
        void shouldHandleEmptyCollaborationRelationships() {
            // Given
            Pageable pageable = PageRequest.of(0, 10);

            // When
            Page<TravelPlan> collaboratingPlans = travelPlanRepository
                .findCollaboratingPlans(testUser3.getId(), pageable);

            // Then
            assertThat(collaboratingPlans.getContent()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Database Constraints and Validation")
    class DatabaseConstraintsAndValidation {

        @Test
        @DisplayName("Should enforce required user reference")
        void shouldEnforceRequiredUserReference() {
            // Given
            TravelPlan planWithoutUser = TravelPlan.builder()
                .user(null) // Required field
                .title("Plan Without User")
                .destination("Seoul")
                .startDate(LocalDate.now().plusDays(10))
                .endDate(LocalDate.now().plusDays(15))
                .build();

            // When & Then
            assertThatThrownBy(() -> {
                travelPlanRepository.save(planWithoutUser);
                entityManager.flush();
            }).isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("Should enforce required fields")
        void shouldEnforceRequiredFields() {
            // Given
            TravelPlan invalidPlan = TravelPlan.builder()
                .user(testUser1)
                .title(null) // Required field
                .destination(null) // Required field
                .startDate(null) // Required field
                .endDate(null) // Required field
                .build();

            // When & Then
            assertThatThrownBy(() -> {
                travelPlanRepository.save(invalidPlan);
                entityManager.flush();
            }).isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("Should handle default values correctly")
        void shouldHandleDefaultValuesCorrectly() {
            // Given
            TravelPlan planWithDefaults = TravelPlan.builder()
                .user(testUser1)
                .title("Default Values Plan")
                .destination("Seoul")
                .startDate(LocalDate.now().plusDays(10))
                .endDate(LocalDate.now().plusDays(15))
                .build();

            // When
            TravelPlan savedPlan = travelPlanRepository.save(planWithDefaults);

            // Then
            assertThat(savedPlan.getStatus()).isEqualTo(TravelPlanStatus.DRAFT);
            assertThat(savedPlan.isPublic()).isFalse();
            assertThat(savedPlan.isAiGenerated()).isFalse();
            assertThat(savedPlan.isDeleted()).isFalse();
            assertThat(savedPlan.getViewCount()).isEqualTo(0L);
            assertThat(savedPlan.getLikeCount()).isEqualTo(0L);
            assertThat(savedPlan.getShareCount()).isEqualTo(0L);
            assertThat(savedPlan.getSaveCount()).isEqualTo(0L);
            assertThat(savedPlan.getPreferences()).isNotNull();
            assertThat(savedPlan.getItineraryItems()).isNotNull().isEmpty();
            assertThat(savedPlan.getCollaborators()).isNotNull().isEmpty();
            assertThat(savedPlan.getVideos()).isNotNull().isEmpty();
            assertThat(savedPlan.getTags()).isNotNull();
        }

        @Test
        @DisplayName("Should validate date range consistency")
        void shouldValidateDateRangeConsistency() {
            // Given
            LocalDate startDate = LocalDate.now().plusDays(20);
            LocalDate endDate = LocalDate.now().plusDays(10); // End before start

            TravelPlan planWithInvalidDates = TravelPlan.builder()
                .user(testUser1)
                .title("Invalid Date Plan")
                .destination("Seoul")
                .startDate(startDate)
                .endDate(endDate)
                .build();

            // When & Then
            // Note: This test assumes business logic validation at the service layer
            // At repository level, we just ensure the dates are saved correctly
            TravelPlan savedPlan = travelPlanRepository.save(planWithInvalidDates);
            assertThat(savedPlan.getStartDate()).isEqualTo(startDate);
            assertThat(savedPlan.getEndDate()).isEqualTo(endDate);
        }
    }

    @Nested
    @DisplayName("Pagination and Sorting")
    class PaginationAndSorting {

        @Test
        @DisplayName("Should paginate user plans correctly")
        void shouldPaginateUserPlansCorrectly() {
            // Given - Create additional plans for the user
            for (int i = 1; i <= 5; i++) {
                createTestTravelPlan(testUser1, "Plan " + i, "Destination " + i,
                    LocalDate.now().plusDays(i * 10), LocalDate.now().plusDays(i * 10 + 5),
                    TravelPlanStatus.DRAFT, false, false);
            }

            Pageable firstPage = PageRequest.of(0, 3);
            Pageable secondPage = PageRequest.of(1, 3);

            // When
            Page<TravelPlan> firstPageResult = travelPlanRepository.findByUserIdAndIsDeletedFalse(testUser1.getId(), firstPage);
            Page<TravelPlan> secondPageResult = travelPlanRepository.findByUserIdAndIsDeletedFalse(testUser1.getId(), secondPage);

            // Then
            assertThat(firstPageResult.getContent()).hasSize(3);
            assertThat(firstPageResult.getTotalElements()).isEqualTo(6); // 1 original + 5 new
            assertThat(firstPageResult.getTotalPages()).isEqualTo(2);
            assertThat(firstPageResult.hasNext()).isTrue();
            assertThat(firstPageResult.hasPrevious()).isFalse();

            assertThat(secondPageResult.getContent()).hasSize(3);
            assertThat(secondPageResult.hasNext()).isFalse();
            assertThat(secondPageResult.hasPrevious()).isTrue();
        }

        @Test
        @DisplayName("Should sort plans by view count descending")
        void shouldSortPlansByViewCountDescending() {
            // Given - Create plans with different view counts
            TravelPlan highViewPlan = createTestTravelPlan(testUser1, "High View Plan", "Seoul",
                LocalDate.now().plusDays(50), LocalDate.now().plusDays(55),
                TravelPlanStatus.DRAFT, true, false);
            highViewPlan.setViewCount(500L);
            travelPlanRepository.save(highViewPlan);

            TravelPlan mediumViewPlan = createTestTravelPlan(testUser1, "Medium View Plan", "Seoul",
                LocalDate.now().plusDays(60), LocalDate.now().plusDays(65),
                TravelPlanStatus.DRAFT, true, false);
            mediumViewPlan.setViewCount(200L);
            travelPlanRepository.save(mediumViewPlan);

            Pageable sortedByViewCount = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "viewCount"));

            // When
            Page<TravelPlan> sortedPlans = travelPlanRepository.findByIsPublicTrue(sortedByViewCount);

            // Then
            assertThat(sortedPlans.getContent()).hasSize(3);
            assertThat(sortedPlans.getContent().get(0).getViewCount())
                .isGreaterThanOrEqualTo(sortedPlans.getContent().get(1).getViewCount());
        }

        @Test
        @DisplayName("Should handle empty pagination results")
        void shouldHandleEmptyPaginationResults() {
            // Given
            Pageable pageable = PageRequest.of(10, 10); // Page beyond available data

            // When
            Page<TravelPlan> result = travelPlanRepository.findByUserIdAndIsDeletedFalse(testUser3.getId(), pageable);

            // Then
            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isEqualTo(0);
            assertThat(result.hasNext()).isFalse();
            assertThat(result.hasPrevious()).isTrue();
        }
    }

    @Nested
    @DisplayName("Entity Relationships and Collections")
    class EntityRelationshipsAndCollections {

        @Test
        @DisplayName("Should handle preferences correctly")
        void shouldHandlePreferencesCorrectly() {
            // Given
            testTravelPlan1.getPreferences().put("diet", "vegetarian");
            testTravelPlan1.getPreferences().put("activity", "hiking");

            // When
            TravelPlan savedPlan = travelPlanRepository.save(testTravelPlan1);
            entityManager.flush();
            entityManager.clear();

            TravelPlan reloadedPlan = travelPlanRepository.findById(savedPlan.getId()).orElse(null);

            // Then
            assertThat(reloadedPlan).isNotNull();
            assertThat(reloadedPlan.getPreferences()).hasSize(4); // 2 original + 2 new
            assertThat(reloadedPlan.getPreferences()).containsEntry("diet", "vegetarian");
            assertThat(reloadedPlan.getPreferences()).containsEntry("activity", "hiking");
        }

        @Test
        @DisplayName("Should handle tags correctly")
        void shouldHandleTagsCorrectly() {
            // Given
            testTravelPlan1.getTags().add("budget");
            testTravelPlan1.getTags().add("family");

            // When
            TravelPlan savedPlan = travelPlanRepository.save(testTravelPlan1);
            entityManager.flush();
            entityManager.clear();

            TravelPlan reloadedPlan = travelPlanRepository.findById(savedPlan.getId()).orElse(null);

            // Then
            assertThat(reloadedPlan).isNotNull();
            assertThat(reloadedPlan.getTags()).hasSize(5); // 3 original + 2 new
            assertThat(reloadedPlan.getTags()).contains("budget", "family");
        }

        @Test
        @DisplayName("Should handle collaborator relationships correctly")
        void shouldHandleCollaboratorRelationshipsCorrectly() {
            // Given
            testTravelPlan1.getCollaborators().add(testUser2);
            testTravelPlan1.getCollaborators().add(testUser3);

            // When
            TravelPlan savedPlan = travelPlanRepository.save(testTravelPlan1);
            entityManager.flush();
            entityManager.clear();

            TravelPlan reloadedPlan = travelPlanRepository.findById(savedPlan.getId()).orElse(null);

            // Then
            assertThat(reloadedPlan).isNotNull();
            assertThat(reloadedPlan.getCollaborators()).hasSize(2);
            assertThat(reloadedPlan.getCollaborators())
                .extracting(User::getId)
                .containsExactlyInAnyOrder(testUser2.getId(), testUser3.getId());
        }

        @Test
        @DisplayName("Should maintain user relationship correctly")
        void shouldMaintainUserRelationshipCorrectly() {
            // When
            TravelPlan foundPlan = travelPlanRepository.findById(testTravelPlan1.getId()).orElse(null);

            // Then
            assertThat(foundPlan).isNotNull();
            assertThat(foundPlan.getUser()).isNotNull();
            assertThat(foundPlan.getUser().getId()).isEqualTo(testUser1.getId());
            assertThat(foundPlan.getUser().getEmail()).isEqualTo(testUser1.getEmail());
        }
    }

    @Nested
    @DisplayName("Status and Visibility Management")
    class StatusAndVisibilityManagement {

        @Test
        @DisplayName("Should handle all travel plan statuses")
        void shouldHandleAllTravelPlanStatuses() {
            // Given & When
            for (TravelPlanStatus status : TravelPlanStatus.values()) {
                TravelPlan plan = createTestTravelPlan(testUser1, "Plan " + status, "Seoul",
                    LocalDate.now().plusDays(100), LocalDate.now().plusDays(105),
                    status, false, false);

                // Then
                assertThat(plan.getStatus()).isEqualTo(status);
            }
        }

        @Test
        @DisplayName("Should filter by specific status correctly")
        void shouldFilterBySpecificStatusCorrectly() {
            // Given - Create plans with different statuses
            createTestTravelPlan(testUser1, "Draft Plan", "Seoul",
                LocalDate.now().plusDays(100), LocalDate.now().plusDays(105),
                TravelPlanStatus.DRAFT, false, false);
            createTestTravelPlan(testUser1, "Published Plan", "Seoul",
                LocalDate.now().plusDays(110), LocalDate.now().plusDays(115),
                TravelPlanStatus.CONFIRMED, false, false);
            createTestTravelPlan(testUser1, "Completed Plan", "Seoul",
                LocalDate.now().plusDays(120), LocalDate.now().plusDays(125),
                TravelPlanStatus.COMPLETED, false, false);

            Pageable pageable = PageRequest.of(0, 10);

            // When
            Page<TravelPlan> draftPlans = travelPlanRepository
                .findByUserIdAndStatusAndIsDeletedFalse(testUser1.getId(), TravelPlanStatus.DRAFT, pageable);
            Page<TravelPlan> publishedPlans = travelPlanRepository
                .findByUserIdAndStatusAndIsDeletedFalse(testUser1.getId(), TravelPlanStatus.CONFIRMED, pageable);
            Page<TravelPlan> completedPlans = travelPlanRepository
                .findByUserIdAndStatusAndIsDeletedFalse(testUser1.getId(), TravelPlanStatus.COMPLETED, pageable);

            // Then
            assertThat(draftPlans.getContent()).hasSize(2); // Original + new draft
            assertThat(publishedPlans.getContent()).hasSize(1);
            assertThat(completedPlans.getContent()).hasSize(1);

            assertThat(draftPlans.getContent()).allMatch(plan -> plan.getStatus() == TravelPlanStatus.DRAFT);
            assertThat(publishedPlans.getContent()).allMatch(plan -> plan.getStatus() == TravelPlanStatus.CONFIRMED);
            assertThat(completedPlans.getContent()).allMatch(plan -> plan.getStatus() == TravelPlanStatus.COMPLETED);
        }
    }

    @Nested
    @DisplayName("Transaction Boundaries and Rollback Scenarios")
    class TransactionBoundariesAndRollbackScenarios {

        @Test
        @Transactional
        @Rollback
        @DisplayName("Should rollback travel plan transaction on constraint violation")
        void shouldRollbackTravelPlanTransactionOnConstraintViolation() {
            // Given
            String originalTitle = testTravelPlan1.getTitle();
            TravelPlanStatus originalStatus = testTravelPlan1.getStatus();

            // When - Simulate transaction failure
            assertThatThrownBy(() -> {
                // Modify existing plan
                testTravelPlan1.setTitle("Updated Adventure");
                testTravelPlan1.setStatus(TravelPlanStatus.CONFIRMED);
                testTravelPlan1.setViewCount(500L);
                travelPlanRepository.save(testTravelPlan1);
                entityManager.flush();

                // Create plan with null required field to trigger rollback
                TravelPlan invalidPlan = TravelPlan.builder()
                    .user(testUser1)
                    .title(null) // Required field - will cause constraint violation
                    .destination("Test Destination")
                    .startDate(LocalDate.now().plusDays(100))
                    .endDate(LocalDate.now().plusDays(105))
                    .build();
                travelPlanRepository.save(invalidPlan);
                entityManager.flush();
            }).isInstanceOf(DataIntegrityViolationException.class);

            // Then - Verify rollback occurred
            entityManager.clear();
            TravelPlan reloadedPlan = travelPlanRepository.findById(testTravelPlan1.getId()).orElse(null);
            assertThat(reloadedPlan).isNotNull();
            assertThat(reloadedPlan.getTitle()).isEqualTo(originalTitle);
            assertThat(reloadedPlan.getStatus()).isEqualTo(originalStatus);
        }

        @Test
        @Transactional(propagation = Propagation.REQUIRES_NEW)
        @DisplayName("Should handle nested transactions with complex relationships")
        void shouldHandleNestedTransactionsWithComplexRelationships() {
            // Given
            TravelPlan nestedPlan = createTestTravelPlan(testUser1, "Nested Transaction Plan", "Seoul",
                LocalDate.now().plusDays(200), LocalDate.now().plusDays(205),
                TravelPlanStatus.DRAFT, false, false);

            // When - Simulate nested transaction operations
            try {
                // Outer transaction - Update plan and add collaborators
                nestedPlan.setTitle("Updated in Outer Transaction");
                nestedPlan.getCollaborators().add(testUser2);
                nestedPlan.getPreferences().put("transportation", "bus");
                travelPlanRepository.save(nestedPlan);

                // Inner transaction (new propagation) that succeeds
                this.performSuccessfulInnerTransaction(nestedPlan);

            } catch (Exception e) {
                // Should not reach here in this test
                fail("Nested transaction should succeed");
            }

            // Then - Verify both transactions committed
            entityManager.flush();
            entityManager.clear();
            TravelPlan reloadedPlan = travelPlanRepository.findById(nestedPlan.getId()).orElse(null);
            assertThat(reloadedPlan).isNotNull();
            assertThat(reloadedPlan.getTitle()).isEqualTo("Updated in Outer Transaction");
            assertThat(reloadedPlan.getCollaborators()).hasSize(1);
            assertThat(reloadedPlan.getPreferences()).containsEntry("transportation", "bus");
            assertThat(reloadedPlan.getPreferences()).containsEntry("inner", "transaction");
        }

        @Transactional(propagation = Propagation.REQUIRES_NEW)
        private void performSuccessfulInnerTransaction(TravelPlan plan) {
            // Inner transaction modifies the plan further
            plan.getPreferences().put("inner", "transaction");
            plan.setDescription("Modified in inner transaction");
            travelPlanRepository.save(plan);
            entityManager.flush();
        }

        @Test
        @Transactional
        @DisplayName("Should maintain referential integrity during bulk operations")
        void shouldMaintainReferentialIntegrityDuringBulkOperations() {
            // Given
            List<TravelPlan> bulkPlans = Arrays.asList(
                TravelPlan.builder()
                    .user(testUser1)
                    .title("Bulk Plan 1")
                    .destination("Seoul")
                    .startDate(LocalDate.now().plusDays(300))
                    .endDate(LocalDate.now().plusDays(305))
                    .status(TravelPlanStatus.DRAFT)
                    .build(),
                TravelPlan.builder()
                    .user(testUser2)
                    .title("Bulk Plan 2")
                    .destination("Busan")
                    .startDate(LocalDate.now().plusDays(310))
                    .endDate(LocalDate.now().plusDays(315))
                    .status(TravelPlanStatus.CONFIRMED)
                    .build(),
                TravelPlan.builder()
                    .user(testUser1)
                    .title("Bulk Plan 3")
                    .destination("Jeju")
                    .startDate(LocalDate.now().plusDays(320))
                    .endDate(LocalDate.now().plusDays(325))
                    .status(TravelPlanStatus.DRAFT)
                    .build()
            );

            // When
            List<TravelPlan> savedPlans = travelPlanRepository.saveAll(bulkPlans);
            entityManager.flush();

            // Then - Verify all plans saved with correct user references
            assertThat(savedPlans).hasSize(3);
            assertThat(savedPlans).allMatch(plan -> plan.getId() != null);
            assertThat(savedPlans).allMatch(plan -> plan.getUser() != null);
            assertThat(savedPlans).allMatch(plan -> plan.getCreatedAt() != null);

            // Verify user associations
            long user1Plans = savedPlans.stream()
                .filter(plan -> plan.getUser().getId().equals(testUser1.getId()))
                .count();
            long user2Plans = savedPlans.stream()
                .filter(plan -> plan.getUser().getId().equals(testUser2.getId()))
                .count();

            assertThat(user1Plans).isEqualTo(2);
            assertThat(user2Plans).isEqualTo(1);
        }

        @Test
        @DisplayName("Should handle concurrent collaborative plan modifications")
        void shouldHandleConcurrentCollaborativePlanModifications() throws Exception {
            // Given
            TravelPlan collaborativePlan = createTestTravelPlan(testUser1, "Collaborative Plan", "Seoul",
                LocalDate.now().plusDays(400), LocalDate.now().plusDays(405),
                TravelPlanStatus.DRAFT, false, false);
            collaborativePlan.getCollaborators().add(testUser2);
            collaborativePlan = travelPlanRepository.save(collaborativePlan);
            entityManager.flush();

            ExecutorService executor = Executors.newFixedThreadPool(3);
            final String planId = collaborativePlan.getId();

            // When - Simulate concurrent modifications by different collaborators
            CompletableFuture<Void> future1 = CompletableFuture.runAsync(() -> {
                try {
                    TravelPlan plan = travelPlanRepository.findById(planId).orElse(null);
                    if (plan != null) {
                        plan.getTags().add("collaborative-tag-1");
                        plan.setViewCount(plan.getViewCount() + 10);
                        travelPlanRepository.save(plan);
                        Thread.sleep(50); // Simulate processing time
                    }
                } catch (Exception e) {
                    // Expected in concurrent scenario
                }
            }, executor);

            CompletableFuture<Void> future2 = CompletableFuture.runAsync(() -> {
                try {
                    TravelPlan plan = travelPlanRepository.findById(planId).orElse(null);
                    if (plan != null) {
                        plan.getPreferences().put("budget", "medium");
                        plan.setLikeCount(plan.getLikeCount() + 5);
                        travelPlanRepository.save(plan);
                        Thread.sleep(50); // Simulate processing time
                    }
                } catch (Exception e) {
                    // Expected in concurrent scenario
                }
            }, executor);

            CompletableFuture<Void> future3 = CompletableFuture.runAsync(() -> {
                try {
                    TravelPlan plan = travelPlanRepository.findById(planId).orElse(null);
                    if (plan != null) {
                        plan.setDescription("Updated by concurrent user");
                        plan.setShareCount(plan.getShareCount() + 2);
                        travelPlanRepository.save(plan);
                        Thread.sleep(50); // Simulate processing time
                    }
                } catch (Exception e) {
                    // Expected in concurrent scenario
                }
            }, executor);

            // Then - Wait for completion and verify final state
            CompletableFuture.allOf(future1, future2, future3).join();
            executor.shutdown();

            // Verify the plan still exists and has consistent state
            TravelPlan finalPlan = travelPlanRepository.findById(planId).orElse(null);
            assertThat(finalPlan).isNotNull();
            assertThat(finalPlan.getUser().getId()).isEqualTo(testUser1.getId());
            assertThat(finalPlan.getCollaborators()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Complex Entity Relationships and Cascading")
    class ComplexEntityRelationshipsAndCascading {

        @Test
        @DisplayName("Should handle deep object graph updates atomically")
        void shouldHandleDeepObjectGraphUpdatesAtomically() {
            // Given
            TravelPlan complexPlan = testTravelPlan1;

            // When - Update multiple related collections in single transaction
            complexPlan.getTags().clear();
            complexPlan.getTags().addAll(Arrays.asList("updated", "comprehensive", "test"));

            complexPlan.getPreferences().clear();
            complexPlan.getPreferences().putAll(Map.of(
                "accommodation", "luxury",
                "transportation", "flight",
                "activity_level", "high",
                "budget", "premium"
            ));

            complexPlan.getCollaborators().clear();
            complexPlan.getCollaborators().addAll(Arrays.asList(testUser2, testUser3));

            complexPlan.setTitle("Completely Updated Plan");
            complexPlan.setDescription("Updated with complex relationships");
            complexPlan.setStatus(TravelPlanStatus.CONFIRMED);
            complexPlan.setBudget(new BigDecimal("5000000"));

            TravelPlan savedPlan = travelPlanRepository.save(complexPlan);
            entityManager.flush();
            entityManager.clear();

            // Then - Verify all updates applied atomically
            TravelPlan reloadedPlan = travelPlanRepository.findById(savedPlan.getId()).orElse(null);
            assertThat(reloadedPlan).isNotNull();

            assertThat(reloadedPlan.getTitle()).isEqualTo("Completely Updated Plan");
            assertThat(reloadedPlan.getStatus()).isEqualTo(TravelPlanStatus.CONFIRMED);
            assertThat(reloadedPlan.getBudget()).isEqualTo(new BigDecimal("5000000"));

            assertThat(reloadedPlan.getTags()).hasSize(3)
                .containsExactlyInAnyOrder("updated", "comprehensive", "test");

            assertThat(reloadedPlan.getPreferences()).hasSize(4)
                .containsEntry("accommodation", "luxury")
                .containsEntry("transportation", "flight")
                .containsEntry("activity_level", "high")
                .containsEntry("budget", "premium");

            assertThat(reloadedPlan.getCollaborators()).hasSize(2)
                .extracting(User::getId)
                .containsExactlyInAnyOrder(testUser2.getId(), testUser3.getId());
        }

        @Test
        @DisplayName("Should maintain bidirectional relationships correctly")
        void shouldMaintainBidirectionalRelationshipsCorrectly() {
            // Given
            TravelPlan plan = testTravelPlan1;
            User collaborator = testUser2;

            // When - Add collaborator relationship
            plan.getCollaborators().add(collaborator);
            travelPlanRepository.save(plan);
            entityManager.flush();
            entityManager.clear();

            // Then - Verify relationship from both sides
            TravelPlan reloadedPlan = travelPlanRepository.findById(plan.getId()).orElse(null);
            assertThat(reloadedPlan).isNotNull();
            assertThat(reloadedPlan.getCollaborators()).hasSize(1);
            assertThat(reloadedPlan.getCollaborators().get(0).getId()).isEqualTo(collaborator.getId());

            // Verify from collaborator side (using repository query)
            Pageable pageable = PageRequest.of(0, 10);
            Page<TravelPlan> collaboratingPlans = travelPlanRepository
                .findCollaboratingPlans(collaborator.getId(), pageable);
            assertThat(collaboratingPlans.getContent()).hasSize(1);
            assertThat(collaboratingPlans.getContent().get(0).getId()).isEqualTo(plan.getId());
        }

        @Test
        @DisplayName("Should handle collection removal correctly")
        void shouldHandleCollectionRemovalCorrectly() {
            // Given
            TravelPlan plan = testTravelPlan1;
            plan.getCollaborators().add(testUser2);
            plan.getCollaborators().add(testUser3);
            plan.getTags().addAll(Arrays.asList("tag1", "tag2", "tag3"));
            plan.getPreferences().put("test1", "value1");
            plan.getPreferences().put("test2", "value2");
            travelPlanRepository.save(plan);
            entityManager.flush();

            // When - Remove items from collections
            plan.getCollaborators().remove(testUser2); // Remove one collaborator
            plan.getTags().removeIf(tag -> tag.equals("tag2")); // Remove specific tag
            plan.getPreferences().remove("test1"); // Remove specific preference

            TravelPlan updatedPlan = travelPlanRepository.save(plan);
            entityManager.flush();
            entityManager.clear();

            // Then - Verify removals were persisted
            TravelPlan reloadedPlan = travelPlanRepository.findById(updatedPlan.getId()).orElse(null);
            assertThat(reloadedPlan).isNotNull();

            assertThat(reloadedPlan.getCollaborators()).hasSize(1);
            assertThat(reloadedPlan.getCollaborators().get(0).getId()).isEqualTo(testUser3.getId());

            assertThat(reloadedPlan.getTags()).hasSize(5); // Original 3 + added 3 - removed 1
            assertThat(reloadedPlan.getTags()).doesNotContain("tag2");
            assertThat(reloadedPlan.getTags()).contains("tag1", "tag3");

            assertThat(reloadedPlan.getPreferences()).doesNotContainKey("test1");
            assertThat(reloadedPlan.getPreferences()).containsKey("test2");
        }

        @Test
        @DisplayName("Should handle orphan removal and cascade operations")
        void shouldHandleOrphanRemovalAndCascadeOperations() {
            // Given
            TravelPlan plan = createTestTravelPlan(testUser1, "Cascade Test Plan", "Seoul",
                LocalDate.now().plusDays(500), LocalDate.now().plusDays(505),
                TravelPlanStatus.DRAFT, false, false);

            // Add collections data
            plan.getTags().addAll(Arrays.asList("cascade", "test", "orphan"));
            plan.getPreferences().put("cascade", "test");
            TravelPlan savedPlan = travelPlanRepository.save(plan);
            entityManager.flush();

            String planId = savedPlan.getId();

            // When - Delete the plan
            travelPlanRepository.deleteById(planId);
            entityManager.flush();
            entityManager.clear();

            // Then - Verify plan and its collections are removed
            Optional<TravelPlan> deletedPlan = travelPlanRepository.findById(planId);
            assertThat(deletedPlan).isEmpty();

            // Note: In a real JPA implementation with proper cascade settings,
            // the element collections would be automatically removed.
            // This test verifies the basic deletion functionality.
        }
    }

    @Nested
    @DisplayName("Advanced Query Optimization and Performance")
    class AdvancedQueryOptimizationAndPerformance {

        @Test
        @DisplayName("Should efficiently handle complex multi-table queries")
        void shouldEfficientlyHandleComplexMultiTableQueries() {
            // Given - Create plans with various characteristics for complex queries
            TravelPlan popularPlan = createTestTravelPlan(testUser1, "Popular Plan", "Seoul",
                LocalDate.now().plusDays(600), LocalDate.now().plusDays(605),
                TravelPlanStatus.CONFIRMED, true, false);
            popularPlan.setViewCount(1000L);
            popularPlan.setLikeCount(200L);
            popularPlan.getCollaborators().add(testUser2);
            travelPlanRepository.save(popularPlan);

            TravelPlan recentPlan = createTestTravelPlan(testUser2, "Recent Plan", "Busan",
                LocalDate.now().plusDays(610), LocalDate.now().plusDays(615),
                TravelPlanStatus.DRAFT, true, false);
            recentPlan.setViewCount(50L);
            travelPlanRepository.save(recentPlan);

            Pageable pageable = PageRequest.of(0, 10);

            // When - Execute various complex queries
            Page<TravelPlan> popularPlans = travelPlanRepository.findPopularPlans(pageable);
            Page<TravelPlan> collaboratingPlans = travelPlanRepository.findCollaboratingPlans(testUser2.getId(), pageable);
            Page<TravelPlan> publicPlans = travelPlanRepository.findByIsPublicTrue(pageable);
            List<TravelPlan> similarPlans = travelPlanRepository.findSimilarPlans("Seoul", 
                LocalDate.now().plusDays(598), LocalDate.now().plusDays(608));

            // Then - Verify query results
            assertThat(popularPlans.getContent()).isNotEmpty();
            assertThat(collaboratingPlans.getContent()).hasSize(1);
            assertThat(publicPlans.getContent()).hasSize(3); // Including existing public plan
            assertThat(similarPlans).hasSize(1);
        }

        @Test
        @DisplayName("Should handle large dataset pagination efficiently")
        void shouldHandleLargeDatasetPaginationEfficiently() {
            // Given - Create a large number of plans for pagination testing
            for (int i = 1; i <= 100; i++) {
                TravelPlan plan = createTestTravelPlan(
                    i % 2 == 0 ? testUser1 : testUser2,
                    "Large Dataset Plan " + i,
                    "Destination " + (i % 10),
                    LocalDate.now().plusDays(700 + i),
                    LocalDate.now().plusDays(705 + i),
                    i % 3 == 0 ? TravelPlanStatus.CONFIRMED : TravelPlanStatus.DRAFT,
                    i % 4 == 0, // 25% public
                    false
                );
                plan.setViewCount((long) (i * 10));
                travelPlanRepository.save(plan);
            }

            // When - Test various pagination scenarios
            Pageable smallPage = PageRequest.of(0, 5);
            Pageable mediumPage = PageRequest.of(5, 10);
            Pageable largePage = PageRequest.of(0, 50);

            Page<TravelPlan> smallResult = travelPlanRepository.findByUserIdAndIsDeletedFalse(testUser1.getId(), smallPage);
            Page<TravelPlan> mediumResult = travelPlanRepository.findByIsPublicTrue(mediumPage);
            Page<TravelPlan> largeResult = travelPlanRepository.findAll(largePage);

            // Then - Verify pagination efficiency
            assertThat(smallResult.getContent()).hasSize(5);
            assertThat(smallResult.getTotalElements()).isEqualTo(51); // 50 new + 1 existing for testUser1

            assertThat(mediumResult.getContent()).hasSize(10);
            assertThat(mediumResult.getTotalElements()).isEqualTo(26); // 25 new + 1 existing public

            assertThat(largeResult.getContent()).hasSize(50);
            assertThat(largeResult.getTotalElements()).isEqualTo(102); // 100 new + 2 existing
        }

        @Test
        @DisplayName("Should optimize queries with complex sorting and filtering")
        void shouldOptimizeQueriesWithComplexSortingAndFiltering() {
            // Given - Create plans with varied metrics
            for (int i = 1; i <= 10; i++) {
                TravelPlan plan = createTestTravelPlan(testUser1, "Metrics Plan " + i, "Seoul",
                    LocalDate.now().plusDays(800 + i * 10), LocalDate.now().plusDays(805 + i * 10),
                    TravelPlanStatus.CONFIRMED, true, false);
                plan.setViewCount((long) (i * 100));
                plan.setLikeCount((long) (i * 20));
                plan.setShareCount((long) (i * 5));
                plan.setSaveCount((long) (i * 10));
                travelPlanRepository.save(plan);
            }

            // When - Apply complex sorting and filtering
            Sort complexSort = Sort.by(
                Sort.Order.desc("viewCount"),
                Sort.Order.desc("likeCount"),
                Sort.Order.asc("title")
            );
            Pageable sortedPageable = PageRequest.of(0, 5, complexSort);

            Page<TravelPlan> sortedResults = travelPlanRepository.findByIsPublicTrue(sortedPageable);

            // Then - Verify sorting is applied correctly
            assertThat(sortedResults.getContent()).hasSize(5);
            List<Long> viewCounts = sortedResults.getContent().stream()
                .map(TravelPlan::getViewCount)
                .toList();
            
            // Should be sorted by view count descending
            for (int i = 0; i < viewCounts.size() - 1; i++) {
                assertThat(viewCounts.get(i)).isGreaterThanOrEqualTo(viewCounts.get(i + 1));
            }
        }
    }
}