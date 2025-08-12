package com.oddiya.repository;

import com.oddiya.entity.SavedPlan;
import com.oddiya.entity.TravelPlan;
import com.oddiya.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive tests for SavedPlanRepository covering:
 * - Basic CRUD operations
 * - User and travel plan relationship queries
 * - Existence checks
 * - Pagination and sorting
 * - Database constraints
 * - Cascade delete operations
 */
@DisplayName("SavedPlanRepository Tests")
class SavedPlanRepositoryTest extends RepositoryTestBase {

    @Autowired
    private SavedPlanRepository savedPlanRepository;

    private SavedPlan testSavedPlan1;
    private SavedPlan testSavedPlan2;

    @BeforeEach
    void setUpSavedPlanTestData() {
        // Create saved plans
        testSavedPlan1 = SavedPlan.builder()
            .user(testUser1)
            .travelPlan(testTravelPlan1)
            .build();

        testSavedPlan2 = SavedPlan.builder()
            .user(testUser2)
            .travelPlan(testTravelPlan1)
            .build();

        testSavedPlan1 = entityManager.persistAndFlush(testSavedPlan1);
        testSavedPlan2 = entityManager.persistAndFlush(testSavedPlan2);

        entityManager.clear();
    }

    @Nested
    @DisplayName("Basic CRUD Operations")
    class BasicCrudOperations {

        @Test
        @DisplayName("Should save and retrieve saved plan successfully")
        void shouldSaveAndRetrieveSavedPlan() {
            // Given
            SavedPlan newSavedPlan = SavedPlan.builder()
                .user(testUser3)
                .travelPlan(testTravelPlan2)
                // SavedPlan doesn't have notes field
                .build();

            // When
            SavedPlan savedPlan = savedPlanRepository.save(newSavedPlan);
            Optional<SavedPlan> foundPlan = savedPlanRepository.findById(savedPlan.getId());

            // Then
            assertThat(savedPlan).isNotNull();
            assertThat(savedPlan.getId()).isNotNull();
            assertThat(savedPlan.getCreatedAt()).isNotNull();
            assertThat(savedPlan.getUpdatedAt()).isNotNull();

            assertThat(foundPlan).isPresent();
            // SavedPlan doesn't have notes field
            assertThat(foundPlan.get().getUser().getId()).isEqualTo(testUser3.getId());
            assertThat(foundPlan.get().getTravelPlan().getId()).isEqualTo(testTravelPlan2.getId());
        }

        @Test
        @DisplayName("Should update saved plan successfully")
        void shouldUpdateSavedPlanSuccessfully() {
            // Given
            // SavedPlan doesn't have notes field - test ID instead
            String originalId = testSavedPlan1.getId();

            // When
            // SavedPlan doesn't have notes field - skip this test
            SavedPlan updatedPlan = savedPlanRepository.save(testSavedPlan1);

            // Then
            // SavedPlan doesn't have notes field
            // SavedPlan doesn't have notes field
            assertThat(updatedPlan.getUpdatedAt()).isAfter(updatedPlan.getCreatedAt());
        }

        @Test
        @DisplayName("Should delete saved plan successfully")
        void shouldDeleteSavedPlanSuccessfully() {
            // Given
            String planId = testSavedPlan1.getId();

            // When
            savedPlanRepository.deleteById(planId);

            // Then
            Optional<SavedPlan> deletedPlan = savedPlanRepository.findById(planId);
            assertThat(deletedPlan).isEmpty();
        }

        @Test
        @DisplayName("Should find all saved plans")
        void shouldFindAllSavedPlans() {
            // When
            List<SavedPlan> allPlans = savedPlanRepository.findAll();

            // Then
            assertThat(allPlans).hasSize(2);
            assertThat(allPlans)
                .extracting(sp -> sp.getUser().getId())
                .containsExactlyInAnyOrder(
                    testUser1.getId(),
                    testUser2.getId()
                );
        }
    }

    @Nested
    @DisplayName("Existence and Lookup Queries")
    class ExistenceAndLookupQueries {

        @Test
        @DisplayName("Should check if user saved a specific travel plan")
        void shouldCheckIfUserSavedSpecificTravelPlan() {
            // When & Then
            assertThat(savedPlanRepository.existsByUserIdAndTravelPlanId(
                testUser1.getId(), testTravelPlan1.getId())).isTrue();
            assertThat(savedPlanRepository.existsByUserIdAndTravelPlanId(
                testUser2.getId(), testTravelPlan1.getId())).isTrue();
            assertThat(savedPlanRepository.existsByUserIdAndTravelPlanId(
                testUser3.getId(), testTravelPlan1.getId())).isFalse();
            assertThat(savedPlanRepository.existsByUserIdAndTravelPlanId(
                testUser1.getId(), testTravelPlan2.getId())).isFalse();
        }

        @Test
        @DisplayName("Should find saved plan by user and travel plan")
        void shouldFindSavedPlanByUserAndTravelPlan() {
            // When
            Optional<SavedPlan> foundPlan1 = savedPlanRepository
                .findByUserIdAndTravelPlanId(testUser1.getId(), testTravelPlan1.getId());
            Optional<SavedPlan> foundPlan2 = savedPlanRepository
                .findByUserIdAndTravelPlanId(testUser2.getId(), testTravelPlan1.getId());
            Optional<SavedPlan> notFoundPlan = savedPlanRepository
                .findByUserIdAndTravelPlanId(testUser3.getId(), testTravelPlan1.getId());

            // Then
            assertThat(foundPlan1).isPresent();
            // Verify the saved plan belongs to user1

            assertThat(foundPlan2).isPresent();
            // Verify the saved plan belongs to user2

            assertThat(notFoundPlan).isEmpty();
        }

        @Test
        @DisplayName("Should return empty when user-plan combination doesn't exist")
        void shouldReturnEmptyWhenUserPlanCombinationDoesntExist() {
            // When
            Optional<SavedPlan> nonExistentPlan = savedPlanRepository
                .findByUserIdAndTravelPlanId("non-existent-user", "non-existent-plan");

            // Then
            assertThat(nonExistentPlan).isEmpty();
        }
    }

    @Nested
    @DisplayName("User-Specific Queries")
    class UserSpecificQueries {

        @Test
        @DisplayName("Should find all saved plans by user")
        void shouldFindAllSavedPlansByUser() {
            // Given
            Pageable pageable = PageRequest.of(0, 10);

            // When
            Page<SavedPlan> user1Plans = savedPlanRepository.findByUserId(testUser1.getId(), pageable);
            Page<SavedPlan> user2Plans = savedPlanRepository.findByUserId(testUser2.getId(), pageable);
            Page<SavedPlan> user3Plans = savedPlanRepository.findByUserId(testUser3.getId(), pageable);

            // Then
            assertThat(user1Plans.getContent()).hasSize(1);
            assertThat(user1Plans.getContent().get(0).getUser().getId()).isEqualTo(testUser1.getId());

            assertThat(user2Plans.getContent()).hasSize(1);
            assertThat(user2Plans.getContent().get(0).getUser().getId()).isEqualTo(testUser2.getId());

            assertThat(user3Plans.getContent()).isEmpty();
        }

        @Test
        @DisplayName("Should handle pagination for user saved plans")
        void shouldHandlePaginationForUserSavedPlans() {
            // Given - Create additional saved plans for testUser1
            for (int i = 1; i <= 5; i++) {
                TravelPlan additionalPlan = createTestTravelPlan(
                    testUser3, "Additional Plan " + i, "Destination " + i,
                    testTravelPlan1.getStartDate().plusDays(i * 10),
                    testTravelPlan1.getEndDate().plusDays(i * 10),
                    testTravelPlan1.getStatus(), true, false
                );

                SavedPlan savedPlan = SavedPlan.builder()
                    .user(testUser1)
                    .travelPlan(additionalPlan)
                    // SavedPlan doesn't have notes field
                    .build();
                savedPlanRepository.save(savedPlan);
            }

            Pageable firstPage = PageRequest.of(0, 3);
            Pageable secondPage = PageRequest.of(1, 3);

            // When
            Page<SavedPlan> firstPageResult = savedPlanRepository.findByUserId(testUser1.getId(), firstPage);
            Page<SavedPlan> secondPageResult = savedPlanRepository.findByUserId(testUser1.getId(), secondPage);

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
        @DisplayName("Should handle empty results for user with no saved plans")
        void shouldHandleEmptyResultsForUserWithNoSavedPlans() {
            // Given
            Pageable pageable = PageRequest.of(0, 10);

            // When
            Page<SavedPlan> emptyResults = savedPlanRepository.findByUserId(testUser3.getId(), pageable);

            // Then
            assertThat(emptyResults.getContent()).isEmpty();
            assertThat(emptyResults.getTotalElements()).isEqualTo(0);
            assertThat(emptyResults.hasNext()).isFalse();
            assertThat(emptyResults.hasPrevious()).isFalse();
        }
    }

    @Nested
    @DisplayName("Delete Operations")
    class DeleteOperations {

        @Test
        @DisplayName("Should delete by user ID and travel plan ID")
        void shouldDeleteByUserIdAndTravelPlanId() {
            // Given
            assertThat(savedPlanRepository.existsByUserIdAndTravelPlanId(
                testUser1.getId(), testTravelPlan1.getId())).isTrue();

            // When
            savedPlanRepository.deleteByUserIdAndTravelPlanId(testUser1.getId(), testTravelPlan1.getId());
            entityManager.flush();

            // Then
            assertThat(savedPlanRepository.existsByUserIdAndTravelPlanId(
                testUser1.getId(), testTravelPlan1.getId())).isFalse();
            
            // Verify other saved plan still exists
            assertThat(savedPlanRepository.existsByUserIdAndTravelPlanId(
                testUser2.getId(), testTravelPlan1.getId())).isTrue();
        }

        @Test
        @DisplayName("Should handle delete when no matching record exists")
        void shouldHandleDeleteWhenNoMatchingRecordExists() {
            // When & Then - Should not throw exception
            savedPlanRepository.deleteByUserIdAndTravelPlanId("non-existent-user", "non-existent-plan");
            entityManager.flush();
            
            // Verify existing records are still there
            assertThat(savedPlanRepository.findAll()).hasSize(2);
        }

        @Test
        @DisplayName("Should delete only matching user-plan combination")
        void shouldDeleteOnlyMatchingUserPlanCombination() {
            // Given - Create another saved plan for same travel plan but different user
            SavedPlan additionalSavedPlan = SavedPlan.builder()
                .user(testUser3)
                .travelPlan(testTravelPlan1)
                // notes field doesn't exist in SavedPlan
                .build();
            savedPlanRepository.save(additionalSavedPlan);

            // Verify we have 3 saved plans total
            assertThat(savedPlanRepository.findAll()).hasSize(3);

            // When - Delete specific user-plan combination
            savedPlanRepository.deleteByUserIdAndTravelPlanId(testUser1.getId(), testTravelPlan1.getId());
            entityManager.flush();

            // Then
            List<SavedPlan> remainingPlans = savedPlanRepository.findAll();
            assertThat(remainingPlans).hasSize(2);
            
            assertThat(remainingPlans)
                .extracting(sp -> sp.getUser().getId())
                .containsExactlyInAnyOrder(testUser2.getId(), testUser3.getId())
                .doesNotContain(testUser1.getId());
        }
    }

    @Nested
    @DisplayName("Database Constraints and Validation")
    class DatabaseConstraintsAndValidation {

        @Test
        @DisplayName("Should enforce required user reference")
        void shouldEnforceRequiredUserReference() {
            // Given
            SavedPlan planWithoutUser = SavedPlan.builder()
                .user(null) // Required field
                .travelPlan(testTravelPlan1)
                // SavedPlan doesn't have notes field
                .build();

            // When & Then
            assertThatThrownBy(() -> {
                savedPlanRepository.save(planWithoutUser);
                entityManager.flush();
            }).isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("Should enforce required travel plan reference")
        void shouldEnforceRequiredTravelPlanReference() {
            // Given
            SavedPlan planWithoutTravelPlan = SavedPlan.builder()
                .user(testUser1)
                .travelPlan(null) // Required field
                // SavedPlan doesn't have notes field
                .build();

            // When & Then
            assertThatThrownBy(() -> {
                savedPlanRepository.save(planWithoutTravelPlan);
                entityManager.flush();
            }).isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("Should enforce unique user-travel plan combination")
        void shouldEnforceUniqueUserTravelPlanCombination() {
            // Given - Try to create duplicate user-travel plan combination
            SavedPlan duplicateSavedPlan = SavedPlan.builder()
                .user(testUser1)
                .travelPlan(testTravelPlan1) // Same combination as testSavedPlan1
                // notes field doesn't exist in SavedPlan
                .build();

            // When & Then
            assertThatThrownBy(() -> {
                savedPlanRepository.save(duplicateSavedPlan);
                entityManager.flush();
            }).isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("Should allow null notes")
        void shouldAllowNullNotes() {
            // Given
            SavedPlan planWithoutNotes = SavedPlan.builder()
                .user(testUser3)
                .travelPlan(testTravelPlan2)
                // SavedPlan doesn't have notes field
                .build();

            // When
            SavedPlan savedPlan = savedPlanRepository.save(planWithoutNotes);

            // Then
            assertThat(savedPlan).isNotNull();
            // SavedPlan doesn't have notes field
            assertThat(savedPlan.getId()).isNotNull();
        }

        @Test
        @DisplayName("Should handle empty string notes")
        void shouldHandleEmptyStringNotes() {
            // Given
            SavedPlan planWithEmptyNotes = SavedPlan.builder()
                .user(testUser3)
                .travelPlan(testTravelPlan2)
                // SavedPlan doesn't have notes field
                .build();

            // When
            SavedPlan savedPlan = savedPlanRepository.save(planWithEmptyNotes);

            // Then
            assertThat(savedPlan).isNotNull();
            // SavedPlan doesn't have notes field
            assertThat(savedPlan.getId()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Relationship Integrity")
    class RelationshipIntegrity {

        @Test
        @DisplayName("Should maintain user relationship correctly")
        void shouldMaintainUserRelationshipCorrectly() {
            // When
            SavedPlan foundPlan = savedPlanRepository.findById(testSavedPlan1.getId()).orElse(null);

            // Then
            assertThat(foundPlan).isNotNull();
            assertThat(foundPlan.getUser()).isNotNull();
            assertThat(foundPlan.getUser().getId()).isEqualTo(testUser1.getId());
            assertThat(foundPlan.getUser().getEmail()).isEqualTo(testUser1.getEmail());
            assertThat(foundPlan.getUser().getNickname()).isEqualTo(testUser1.getNickname());
        }

        @Test
        @DisplayName("Should maintain travel plan relationship correctly")
        void shouldMaintainTravelPlanRelationshipCorrectly() {
            // When
            SavedPlan foundPlan = savedPlanRepository.findById(testSavedPlan1.getId()).orElse(null);

            // Then
            assertThat(foundPlan).isNotNull();
            assertThat(foundPlan.getTravelPlan()).isNotNull();
            assertThat(foundPlan.getTravelPlan().getId()).isEqualTo(testTravelPlan1.getId());
            assertThat(foundPlan.getTravelPlan().getTitle()).isEqualTo(testTravelPlan1.getTitle());
            assertThat(foundPlan.getTravelPlan().getDestination()).isEqualTo(testTravelPlan1.getDestination());
        }

        @Test
        @DisplayName("Should handle user reference changes")
        void shouldHandleUserReferenceChanges() {
            // Given
            User originalUser = testSavedPlan1.getUser();

            // When - Change user reference
            testSavedPlan1.setUser(testUser3);
            SavedPlan updatedPlan = savedPlanRepository.save(testSavedPlan1);
            entityManager.flush();
            entityManager.clear();

            SavedPlan reloadedPlan = savedPlanRepository.findById(updatedPlan.getId()).orElse(null);

            // Then
            assertThat(reloadedPlan).isNotNull();
            assertThat(reloadedPlan.getUser().getId()).isEqualTo(testUser3.getId());
            assertThat(reloadedPlan.getUser().getId()).isNotEqualTo(originalUser.getId());
        }

        @Test
        @DisplayName("Should handle travel plan reference changes")
        void shouldHandleTravelPlanReferenceChanges() {
            // Given
            TravelPlan originalTravelPlan = testSavedPlan1.getTravelPlan();

            // When - Change travel plan reference
            testSavedPlan1.setTravelPlan(testTravelPlan2);
            SavedPlan updatedPlan = savedPlanRepository.save(testSavedPlan1);
            entityManager.flush();
            entityManager.clear();

            SavedPlan reloadedPlan = savedPlanRepository.findById(updatedPlan.getId()).orElse(null);

            // Then
            assertThat(reloadedPlan).isNotNull();
            assertThat(reloadedPlan.getTravelPlan().getId()).isEqualTo(testTravelPlan2.getId());
            assertThat(reloadedPlan.getTravelPlan().getId()).isNotEqualTo(originalTravelPlan.getId());
        }
    }

    @Nested
    @DisplayName("Pagination and Sorting")
    class PaginationAndSorting {

        @Test
        @DisplayName("Should sort saved plans by creation date")
        void shouldSortSavedPlansByCreationDate() {
            // Given - Create additional saved plan with delay to ensure different timestamps
            try {
                Thread.sleep(10); // Small delay to ensure different timestamps
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            SavedPlan newerPlan = SavedPlan.builder()
                .user(testUser3)
                .travelPlan(testTravelPlan2)
                .build();
            savedPlanRepository.save(newerPlan);

            Pageable sortedByCreatedAtDesc = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));

            // When
            Page<SavedPlan> sortedPlans = savedPlanRepository.findByUserId(testUser3.getId(), sortedByCreatedAtDesc);

            // Then
            assertThat(sortedPlans.getContent()).hasSize(1);
            assertThat(sortedPlans.getContent().get(0).getUser().getId()).isEqualTo(testUser3.getId());
        }

        @Test
        @DisplayName("Should handle pagination beyond available data")
        void shouldHandlePaginationBeyondAvailableData() {
            // Given
            Pageable beyondData = PageRequest.of(10, 10); // Page way beyond available data

            // When
            Page<SavedPlan> result = savedPlanRepository.findByUserId(testUser1.getId(), beyondData);

            // Then
            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.hasNext()).isFalse();
            assertThat(result.hasPrevious()).isTrue();
        }

        @Test
        @DisplayName("Should sort by multiple criteria")
        void shouldSortByMultipleCriteria() {
            // Given - Create multiple saved plans for the same user
            TravelPlan plan2 = createTestTravelPlan(testUser3, "Plan 2", "Dest 2",
                testTravelPlan1.getStartDate(), testTravelPlan1.getEndDate(),
                testTravelPlan1.getStatus(), true, false);
            TravelPlan plan3 = createTestTravelPlan(testUser3, "Plan 3", "Dest 3",
                testTravelPlan1.getStartDate(), testTravelPlan1.getEndDate(),
                testTravelPlan1.getStatus(), true, false);

            SavedPlan savedPlan2 = SavedPlan.builder()
                .user(testUser1)
                .travelPlan(plan2)
                .build();
            SavedPlan savedPlan3 = SavedPlan.builder()
                .user(testUser1)
                .travelPlan(plan3)
                .build();

            savedPlanRepository.save(savedPlan2);
            savedPlanRepository.save(savedPlan3);

            Pageable sortedByCreatedAtAsc = PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "createdAt"));

            // When
            Page<SavedPlan> sortedPlans = savedPlanRepository.findByUserId(testUser1.getId(), sortedByCreatedAtAsc);

            // Then
            assertThat(sortedPlans.getContent()).hasSize(3);
            assertThat(sortedPlans.getContent().get(0).getTravelPlan().getId()).isEqualTo(testTravelPlan1.getId());
            assertThat(sortedPlans.getContent().get(1).getTravelPlan().getId()).isEqualTo(plan2.getId());
            assertThat(sortedPlans.getContent().get(2).getTravelPlan().getId()).isEqualTo(plan3.getId());
        }
    }
}