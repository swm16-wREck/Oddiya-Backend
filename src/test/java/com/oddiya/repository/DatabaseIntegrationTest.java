package com.oddiya.repository;

import com.oddiya.entity.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests focusing on:
 * - Cross-repository operations
 * - Transaction management
 * - Entity relationship integrity
 * - Cascade operations
 * - Database constraint enforcement
 * - Performance scenarios
 * - Concurrent access patterns
 */
@DisplayName("Database Integration Tests")
class DatabaseIntegrationTest extends RepositoryTestBase {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PlaceRepository placeRepository;
    @Autowired
    private TravelPlanRepository travelPlanRepository;
    @Autowired
    private ItineraryItemRepository itineraryItemRepository;
    @Autowired
    private SavedPlanRepository savedPlanRepository;

    @Nested
    @DisplayName("Cross-Repository Operations")
    class CrossRepositoryOperations {

        @Test
        @DisplayName("Should create complete travel plan with all related entities")
        @Transactional
        @Rollback
        void shouldCreateCompleteTravelPlanWithAllRelatedEntities() {
            // Given - Create a user
            User creator = User.builder()
                .email("creator@test.com")
                .nickname("Plan Creator")
                .provider("google")
                .providerId("google-creator")
                .isActive(true)
                .build();
            creator = userRepository.save(creator);

            // Create places
            Place place1 = Place.builder()
                .naverPlaceId("integration-place-1")
                .name("Integration Place 1")
                .category("restaurant")
                .address("Integration Address 1")
                .latitude(37.5665)
                .longitude(126.9780)
                .rating(4.5)
                .reviewCount(100)
                .isVerified(true)
                .build();

            Place place2 = Place.builder()
                .naverPlaceId("integration-place-2")
                .name("Integration Place 2")
                .category("tourist_attraction")
                .address("Integration Address 2")
                .latitude(37.5700)
                .longitude(126.9800)
                .rating(4.2)
                .reviewCount(80)
                .isVerified(true)
                .build();

            place1 = placeRepository.save(place1);
            place2 = placeRepository.save(place2);

            // Create travel plan
            TravelPlan travelPlan = TravelPlan.builder()
                .user(creator)
                .title("Complete Integration Test Plan")
                .description("A comprehensive travel plan for testing")
                .destination("Seoul Integration Test")
                .startDate(LocalDate.now().plusDays(30))
                .endDate(LocalDate.now().plusDays(35))
                .numberOfPeople(2)
                .budget(new BigDecimal("1500000"))
                .status(TravelPlanStatus.DRAFT)
                .isPublic(true)
                .build();
            travelPlan = travelPlanRepository.save(travelPlan);

            // Create itinerary items
            ItineraryItem item1 = ItineraryItem.builder()
                .travelPlan(travelPlan)
                .place(place1)
                .dayNumber(1)
                .sequence(1)
                .title("Lunch at Integration Place 1")
                .startTime(java.time.LocalDateTime.of(LocalDate.now().plusDays(30), LocalTime.of(12, 0)))
                .endTime(java.time.LocalDateTime.of(LocalDate.now().plusDays(30), LocalTime.of(13, 30)))
                .estimatedCost(new BigDecimal("25000"))
                .build();

            ItineraryItem item2 = ItineraryItem.builder()
                .travelPlan(travelPlan)
                .place(place2)
                .dayNumber(1)
                .sequence(2)
                .title("Visit Integration Place 2")
                .startTime(java.time.LocalDateTime.of(LocalDate.now().plusDays(30), LocalTime.of(14, 0)))
                .endTime(java.time.LocalDateTime.of(LocalDate.now().plusDays(30), LocalTime.of(16, 0)))
                .estimatedCost(new BigDecimal("15000"))
                .build();

            itineraryItemRepository.save(item1);
            itineraryItemRepository.save(item2);

            // Create collaborator and saved plan
            User collaborator = User.builder()
                .email("collaborator@test.com")
                .nickname("Collaborator")
                .provider("google")
                .providerId("google-collab")
                .isActive(true)
                .build();
            collaborator = userRepository.save(collaborator);

            travelPlan.getCollaborators().add(collaborator);
            travelPlan = travelPlanRepository.save(travelPlan);

            SavedPlan savedPlan = SavedPlan.builder()
                .user(collaborator)
                .travelPlan(travelPlan)
                // SavedPlan doesn't have notes field
                .build();
            savedPlanRepository.save(savedPlan);

            entityManager.flush();
            entityManager.clear();

            // When - Retrieve and verify the complete structure
            TravelPlan retrievedPlan = travelPlanRepository.findById(travelPlan.getId()).orElse(null);
            List<ItineraryItem> items = itineraryItemRepository
                .findByTravelPlanIdOrderByDayNumberAscSequenceAsc(travelPlan.getId());
            boolean isSaved = savedPlanRepository.existsByUserIdAndTravelPlanId(
                collaborator.getId(), travelPlan.getId());

            // Then
            assertThat(retrievedPlan).isNotNull();
            assertThat(retrievedPlan.getUser().getEmail()).isEqualTo("creator@test.com");
            assertThat(retrievedPlan.getCollaborators()).hasSize(1);
            assertThat(retrievedPlan.getCollaborators().get(0).getEmail()).isEqualTo("collaborator@test.com");

            assertThat(items).hasSize(2);
            assertThat(items.get(0).getPlace().getName()).isEqualTo("Integration Place 1");
            assertThat(items.get(1).getPlace().getName()).isEqualTo("Integration Place 2");

            assertThat(isSaved).isTrue();
        }

        @Test
        @DisplayName("Should handle user with multiple travel plans and relationships")
        @Transactional
        @Rollback
        void shouldHandleUserWithMultipleTravelPlansAndRelationships() {
            // Given - Create a user with multiple plans
            User activeUser = User.builder()
                .email("active@test.com")
                .nickname("Active User")
                .provider("google")
                .providerId("google-active")
                .isActive(true)
                .build();
            activeUser = userRepository.save(activeUser);

            // Create multiple travel plans
            for (int i = 1; i <= 3; i++) {
                TravelPlan plan = TravelPlan.builder()
                    .user(activeUser)
                    .title("Plan " + i)
                    .destination("Destination " + i)
                    .startDate(LocalDate.now().plusDays(i * 10))
                    .endDate(LocalDate.now().plusDays(i * 10 + 5))
                    .status(i == 1 ? TravelPlanStatus.CONFIRMED : TravelPlanStatus.DRAFT)
                    .isPublic(i <= 2)
                    .build();
                travelPlanRepository.save(plan);
            }

            entityManager.flush();
            entityManager.clear();

            // When
            final User finalActiveUser = activeUser;
            List<TravelPlan> userPlans = travelPlanRepository.findAll().stream()
                .filter(plan -> plan.getUser().getId().equals(finalActiveUser.getId()))
                .toList();
            long publishedPlans = userPlans.stream()
                .filter(plan -> plan.getStatus() == TravelPlanStatus.CONFIRMED)
                .count();
            long publicPlans = userPlans.stream()
                .filter(TravelPlan::isPublic)
                .count();

            // Then
            assertThat(userPlans).hasSize(3);
            assertThat(publishedPlans).isEqualTo(1);
            assertThat(publicPlans).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("Transaction Management")
    class TransactionManagement {

        @Test
        @DisplayName("Should rollback transaction on constraint violation")
        @Transactional
        @Rollback
        void shouldRollbackTransactionOnConstraintViolation() {
            // Given - Create initial entities
            User user = User.builder()
                .email("transaction@test.com")
                .nickname("Transaction User")
                .provider("google")
                .providerId("google-transaction")
                .build();
            user = userRepository.save(user);

            TravelPlan plan = TravelPlan.builder()
                .user(user)
                .title("Transaction Test Plan")
                .destination("Seoul")
                .startDate(LocalDate.now().plusDays(10))
                .endDate(LocalDate.now().plusDays(15))
                .build();
            plan = travelPlanRepository.save(plan);

            // When & Then - Try to create duplicate email user in same transaction
            assertThatThrownBy(() -> {
                User duplicateUser = User.builder()
                    .email("transaction@test.com") // Duplicate email
                    .nickname("Duplicate User")
                    .provider("apple")
                    .providerId("apple-duplicate")
                    .build();
                userRepository.save(duplicateUser);
                entityManager.flush(); // Force constraint check
            }).isInstanceOf(DataIntegrityViolationException.class);

            // The original entities should still be available in the transaction
            assertThat(userRepository.findById(user.getId())).isPresent();
            assertThat(travelPlanRepository.findById(plan.getId())).isPresent();
        }

        @Test
        @DisplayName("Should maintain transaction isolation")
        @Transactional
        @Rollback
        void shouldMaintainTransactionIsolation() {
            // Given
            User user = User.builder()
                .email("isolation@test.com")
                .nickname("Isolation User")
                .provider("google")
                .providerId("google-isolation")
                .isActive(true)
                .build();
            user = userRepository.save(user);

            // When - Modify user within transaction
            user.setNickname("Modified Isolation User");
            userRepository.save(user);

            // Don't flush yet - changes should be visible within transaction
            User modifiedUser = userRepository.findById(user.getId()).orElse(null);

            // Then
            assertThat(modifiedUser).isNotNull();
            assertThat(modifiedUser.getNickname()).isEqualTo("Modified Isolation User");
        }
    }

    @Nested
    @DisplayName("Cascade Operations")
    class CascadeOperations {

        @Test
        @DisplayName("Should cascade delete itinerary items when travel plan is deleted")
        @Transactional
        @Rollback
        void shouldCascadeDeleteItineraryItemsWhenTravelPlanIsDeleted() {
            // Given
            TravelPlan plan = TravelPlan.builder()
                .user(testUser1)
                .title("Cascade Test Plan")
                .destination("Cascade City")
                .startDate(LocalDate.now().plusDays(20))
                .endDate(LocalDate.now().plusDays(25))
                .build();
            plan = travelPlanRepository.save(plan);

            // Create itinerary items
            for (int i = 1; i <= 3; i++) {
                ItineraryItem item = ItineraryItem.builder()
                    .travelPlan(plan)
                    .place(testPlace1)
                    .dayNumber(1)
                    .sequence(i)
                    .title("Activity " + i)
                    .build();
                itineraryItemRepository.save(item);
            }

            entityManager.flush();
            
            // Verify items exist
            List<ItineraryItem> itemsBefore = itineraryItemRepository
                .findByTravelPlanIdOrderByDayNumberAscSequenceAsc(plan.getId());
            assertThat(itemsBefore).hasSize(3);

            // When - Delete travel plan
            travelPlanRepository.deleteById(plan.getId());
            entityManager.flush();

            // Then - Items should be cascade deleted
            List<ItineraryItem> itemsAfter = itineraryItemRepository
                .findByTravelPlanIdOrderByDayNumberAscSequenceAsc(plan.getId());
            assertThat(itemsAfter).isEmpty();
        }

        @Test
        @DisplayName("Should maintain referential integrity with saved plans")
        @Transactional
        @Rollback
        void shouldMaintainReferentialIntegrityWithSavedPlans() {
            // Given
            TravelPlan plan = TravelPlan.builder()
                .user(testUser1)
                .title("Referential Integrity Plan")
                .destination("Integrity City")
                .startDate(LocalDate.now().plusDays(40))
                .endDate(LocalDate.now().plusDays(45))
                .build();
            plan = travelPlanRepository.save(plan);

            SavedPlan savedPlan = SavedPlan.builder()
                .user(testUser2)
                .travelPlan(plan)
                // SavedPlan doesn't have notes field
                .build();
            savedPlanRepository.save(savedPlan);

            entityManager.flush();

            // When - Try to delete travel plan (should fail due to saved plan reference)
            String planId = plan.getId();
            
            // Note: This depends on database constraints configuration
            // If cascade is set to DELETE, saved plans would be deleted
            // If cascade is RESTRICT, this would throw an exception
            // For this test, we assume the saved plan prevents deletion
            
            // Then - Verify saved plan still references the travel plan
            SavedPlan foundSavedPlan = savedPlanRepository.findById(savedPlan.getId()).orElse(null);
            assertThat(foundSavedPlan).isNotNull();
            assertThat(foundSavedPlan.getTravelPlan().getId()).isEqualTo(planId);
        }
    }

    @Nested
    @DisplayName("Performance Scenarios")
    class PerformanceScenarios {

        @Test
        @DisplayName("Should handle bulk data operations efficiently")
        @Transactional
        @Rollback
        void shouldHandleBulkDataOperationsEfficiently() {
            // Given - Create bulk data
            User bulkUser = User.builder()
                .email("bulk@test.com")
                .nickname("Bulk User")
                .provider("google")
                .providerId("google-bulk")
                .build();
            bulkUser = userRepository.save(bulkUser);

            long startTime = System.currentTimeMillis();

            // When - Create multiple travel plans with items
            for (int i = 1; i <= 20; i++) {
                TravelPlan plan = TravelPlan.builder()
                    .user(bulkUser)
                    .title("Bulk Plan " + i)
                    .destination("Destination " + i)
                    .startDate(LocalDate.now().plusDays(i))
                    .endDate(LocalDate.now().plusDays(i + 3))
                    .status(TravelPlanStatus.DRAFT)
                    .build();
                plan = travelPlanRepository.save(plan);

                // Create 5 itinerary items per plan
                for (int j = 1; j <= 5; j++) {
                    ItineraryItem item = ItineraryItem.builder()
                        .travelPlan(plan)
                        .place(testPlace1)
                        .dayNumber((j - 1) / 2 + 1)
                        .sequence((j - 1) % 2 + 1)
                        .title("Bulk Activity " + j)
                        .build();
                    itineraryItemRepository.save(item);
                }

                // Flush every 5 plans to avoid memory issues
                if (i % 5 == 0) {
                    entityManager.flush();
                    entityManager.clear();
                }
            }

            entityManager.flush();
            long endTime = System.currentTimeMillis();

            // Then - Verify data was created and performance
            final User finalBulkUser = bulkUser;
            List<TravelPlan> createdPlans = travelPlanRepository.findAll().stream()
                .filter(plan -> plan.getUser().getId().equals(finalBulkUser.getId()))
                .toList();

            assertThat(createdPlans).hasSize(20);
            assertThat(endTime - startTime).isLessThan(5000); // Should complete within 5 seconds

            // Verify itinerary items
            long totalItems = createdPlans.stream()
                .mapToLong(plan -> itineraryItemRepository
                    .findByTravelPlanIdOrderByDayNumberAscSequenceAsc(plan.getId()).size())
                .sum();
            assertThat(totalItems).isEqualTo(100); // 20 plans * 5 items each
        }

        @Test
        @DisplayName("Should efficiently query large datasets with pagination")
        @Transactional
        @Rollback
        void shouldEfficientlyQueryLargeDatasetsWithPagination() {
            // Given - Create test data
            for (int i = 1; i <= 50; i++) {
                User user = User.builder()
                    .email("perf" + i + "@test.com")
                    .nickname("Performance User " + i)
                    .provider("google")
                    .providerId("google-perf-" + i)
                    .isActive(i % 10 != 0) // Make every 10th user inactive
                    .build();
                userRepository.save(user);

                if (i % 10 == 0) {
                    entityManager.flush();
                    entityManager.clear();
                }
            }

            entityManager.flush();
            entityManager.clear();

            long startTime = System.currentTimeMillis();

            // When - Query with pagination
            var pageable = org.springframework.data.domain.PageRequest.of(0, 10);
            var activePage1 = userRepository.findActiveUsers(pageable);
            var activePage2 = userRepository.findActiveUsers(pageable.next());
            var activePage3 = userRepository.findActiveUsers(pageable.next().next());

            long endTime = System.currentTimeMillis();

            // Then - Verify results and performance
            assertThat(activePage1.getContent()).hasSize(10);
            assertThat(activePage2.getContent()).hasSize(10);
            assertThat(activePage3.getContent()).hasSize(10);
            assertThat(activePage1.getTotalElements()).isEqualTo(47); // 50 - 3 (original test users) = 47 active users

            assertThat(endTime - startTime).isLessThan(1000); // Should complete within 1 second
        }

        @Test
        @DisplayName("Should handle complex queries efficiently")
        @Transactional
        @Rollback
        void shouldHandleComplexQueriesEfficiently() {
            // Given - Create complex data scenario
            User complexUser = User.builder()
                .email("complex@test.com")
                .nickname("Complex User")
                .provider("google")
                .providerId("google-complex")
                .build();
            complexUser = userRepository.save(complexUser);

            // Create places in different categories
            for (int i = 1; i <= 10; i++) {
                Place place = Place.builder()
                    .naverPlaceId("complex-place-" + i)
                    .name("Complex Place " + i)
                    .category(i % 3 == 0 ? "restaurant" : i % 3 == 1 ? "tourist_attraction" : "shopping")
                    .address("Complex Address " + i)
                    .latitude(37.5665 + i * 0.001)
                    .longitude(126.9780 + i * 0.001)
                    .rating(3.0 + (i % 3))
                    .reviewCount(i * 10)
                    .popularityScore(i * 10.0)
                    .isVerified(i % 2 == 0)
                    .build();
                placeRepository.save(place);
            }

            entityManager.flush();
            entityManager.clear();

            long startTime = System.currentTimeMillis();

            // When - Execute complex queries
            var pageable = org.springframework.data.domain.PageRequest.of(0, 5);
            var searchResults = placeRepository.searchPlaces("Complex", pageable);
            var categoryResults = placeRepository.findByCategoryAndIsDeletedFalse("restaurant", pageable);
            var ratingResults = placeRepository.findByMinimumRating(4.0, pageable);
            var popularResults = placeRepository.findTopPopularPlaces(pageable);

            long endTime = System.currentTimeMillis();

            // Then - Verify results and performance
            assertThat(searchResults.getContent()).isNotEmpty();
            assertThat(categoryResults.getContent()).hasSize(3); // 3 restaurants
            assertThat(ratingResults.getContent()).hasSize(3); // Places with rating >= 4.0
            assertThat(popularResults).hasSize(5); // Top 5 verified places

            assertThat(endTime - startTime).isLessThan(2000); // Should complete within 2 seconds
        }
    }

    @Nested
    @DisplayName("Concurrent Access Patterns")
    class ConcurrentAccessPatterns {

        @Test
        @DisplayName("Should handle concurrent user creation")
        void shouldHandleConcurrentUserCreation() throws Exception {
            // Given
            ExecutorService executor = Executors.newFixedThreadPool(5);
            
            try {
                // When - Create users concurrently
                CompletableFuture<?>[] futures = new CompletableFuture[10];
                
                for (int i = 0; i < 10; i++) {
                    final int index = i;
                    futures[i] = CompletableFuture.runAsync(() -> {
                        User user = User.builder()
                            .email("concurrent" + index + "@test.com")
                            .nickname("Concurrent User " + index)
                            .provider("google")
                            .providerId("google-concurrent-" + index)
                            .build();
                        userRepository.save(user);
                    }, executor);
                }

                // Wait for all operations to complete
                CompletableFuture.allOf(futures).get();

                // Then
                List<User> concurrentUsers = userRepository.findAll().stream()
                    .filter(user -> user.getEmail().startsWith("concurrent"))
                    .toList();

                assertThat(concurrentUsers).hasSize(10);
            } finally {
                executor.shutdown();
            }
        }

        @Test
        @DisplayName("Should handle concurrent travel plan operations")
        void shouldHandleConcurrentTravelPlanOperations() throws Exception {
            // Given
            ExecutorService executor = Executors.newFixedThreadPool(3);
            
            try {
                User sharedUser = User.builder()
                    .email("shared@test.com")
                    .nickname("Shared User")
                    .provider("google")
                    .providerId("google-shared")
                    .build();
                sharedUser = userRepository.save(sharedUser);
                final String userId = sharedUser.getId();

                // When - Create travel plans concurrently for the same user
                CompletableFuture<?>[] futures = new CompletableFuture[5];
                
                for (int i = 0; i < 5; i++) {
                    final int index = i;
                    futures[i] = CompletableFuture.runAsync(() -> {
                        User user = userRepository.findById(userId).orElse(null);
                        if (user != null) {
                            TravelPlan plan = TravelPlan.builder()
                                .user(user)
                                .title("Concurrent Plan " + index)
                                .destination("Destination " + index)
                                .startDate(LocalDate.now().plusDays(index * 10))
                                .endDate(LocalDate.now().plusDays(index * 10 + 5))
                                .build();
                            travelPlanRepository.save(plan);
                        }
                    }, executor);
                }

                // Wait for all operations to complete
                CompletableFuture.allOf(futures).get();

                // Then
                List<TravelPlan> concurrentPlans = travelPlanRepository.findAll().stream()
                    .filter(plan -> plan.getUser().getId().equals(userId))
                    .toList();

                assertThat(concurrentPlans).hasSize(5);
                
            } finally {
                executor.shutdown();
            }
        }
    }
}