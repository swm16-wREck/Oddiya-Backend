package com.oddiya.repository;

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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive tests for UserRepository covering:
 * - Basic CRUD operations
 * - Custom finder methods
 * - Complex query methods
 * - Relationship handling
 * - Database constraints
 * - Pagination and sorting
 * - Transaction boundaries and rollback scenarios
 * - Concurrent access and locking
 * - Performance and batch operations
 */
@DisplayName("UserRepository Tests")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryTest extends RepositoryTestBase {

    @Autowired
    private UserRepository userRepository;

    @Nested
    @DisplayName("Basic CRUD Operations")
    class BasicCrudOperations {

        @Test
        @DisplayName("Should save and retrieve user successfully")
        void shouldSaveAndRetrieveUser() {
            // Given
            User newUser = User.builder()
                .email("new@test.com")
                .nickname("NewUser")
                .provider("google")
                .providerId("google-new")
                .isActive(true)
                .isDeleted(false)
                .build();

            // When
            User savedUser = userRepository.save(newUser);
            Optional<User> foundUser = userRepository.findById(savedUser.getId());

            // Then
            assertThat(savedUser).isNotNull();
            assertThat(savedUser.getId()).isNotNull();
            assertThat(savedUser.getCreatedAt()).isNotNull();
            assertThat(savedUser.getUpdatedAt()).isNotNull();
            assertThat(savedUser.getVersion()).isEqualTo(0L);

            assertThat(foundUser).isPresent();
            assertThat(foundUser.get().getEmail()).isEqualTo("new@test.com");
            assertThat(foundUser.get().getNickname()).isEqualTo("NewUser");
        }

        @Test
        @DisplayName("Should update user successfully")
        void shouldUpdateUserSuccessfully() {
            // Given
            String originalBio = testUser1.getBio();

            // When
            testUser1.setBio("Updated bio");
            User updatedUser = userRepository.save(testUser1);

            // Then
            assertThat(updatedUser.getBio()).isEqualTo("Updated bio");
            assertThat(updatedUser.getBio()).isNotEqualTo(originalBio);
            assertThat(updatedUser.getUpdatedAt()).isAfter(updatedUser.getCreatedAt());
            assertThat(updatedUser.getVersion()).isEqualTo(1L);
        }

        @Test
        @DisplayName("Should delete user successfully")
        void shouldDeleteUserSuccessfully() {
            // Given
            String userId = testUser1.getId();

            // When
            userRepository.deleteById(userId);

            // Then
            Optional<User> deletedUser = userRepository.findById(userId);
            assertThat(deletedUser).isEmpty();
        }

        @Test
        @DisplayName("Should find all users")
        void shouldFindAllUsers() {
            // When
            List<User> allUsers = userRepository.findAll();

            // Then
            assertThat(allUsers).hasSize(3);
            assertThat(allUsers)
                .extracting(User::getEmail)
                .containsExactlyInAnyOrder(
                    "test1@oddiya.com",
                    "test2@oddiya.com",
                    "test3@oddiya.com"
                );
        }
    }

    @Nested
    @DisplayName("Custom Finder Methods")
    class CustomFinderMethods {

        @Test
        @DisplayName("Should find user by email")
        void shouldFindUserByEmail() {
            // When
            Optional<User> foundUser = userRepository.findByEmail("test1@oddiya.com");

            // Then
            assertThat(foundUser).isPresent();
            assertThat(foundUser.get().getNickname()).isEqualTo("Test User 1");
            assertThat(foundUser.get().getProvider()).isEqualTo("google");
        }

        @Test
        @DisplayName("Should return empty when email not found")
        void shouldReturnEmptyWhenEmailNotFound() {
            // When
            Optional<User> foundUser = userRepository.findByEmail("nonexistent@test.com");

            // Then
            assertThat(foundUser).isEmpty();
        }

        @Test
        @DisplayName("Should find user by provider and providerId")
        void shouldFindUserByProviderAndProviderId() {
            // When
            Optional<User> foundUser = userRepository.findByProviderAndProviderId("apple", "apple-456");

            // Then
            assertThat(foundUser).isPresent();
            assertThat(foundUser.get().getEmail()).isEqualTo("test2@oddiya.com");
            assertThat(foundUser.get().getNickname()).isEqualTo("Test User 2");
        }

        @Test
        @DisplayName("Should return empty when provider/providerId combination not found")
        void shouldReturnEmptyWhenProviderProviderIdNotFound() {
            // When
            Optional<User> foundUser = userRepository.findByProviderAndProviderId("google", "nonexistent-id");

            // Then
            assertThat(foundUser).isEmpty();
        }

        @Test
        @DisplayName("Should check if email exists")
        void shouldCheckIfEmailExists() {
            // When & Then
            assertThat(userRepository.existsByEmail("test1@oddiya.com")).isTrue();
            assertThat(userRepository.existsByEmail("nonexistent@test.com")).isFalse();
        }

        @Test
        @DisplayName("Should check if nickname exists")
        void shouldCheckIfNicknameExists() {
            // When & Then
            assertThat(userRepository.existsByNickname("Test User 1")).isTrue();
            assertThat(userRepository.existsByNickname("Nonexistent User")).isFalse();
        }
    }

    @Nested
    @DisplayName("Custom Query Methods")
    class CustomQueryMethods {

        @Test
        @DisplayName("Should find active users only")
        void shouldFindActiveUsersOnly() {
            // Given
            Pageable pageable = PageRequest.of(0, 10);

            // When
            Page<User> activeUsers = userRepository.findActiveUsers(pageable);

            // Then
            assertThat(activeUsers.getContent()).hasSize(2);
            assertThat(activeUsers.getContent())
                .extracting(User::getNickname)
                .containsExactlyInAnyOrder("Test User 1", "Test User 2");
            assertThat(activeUsers.getContent())
                .allMatch(User::isActive)
                .allMatch(user -> !user.isDeleted());
        }

        @Test
        @DisplayName("Should search users by nickname")
        void shouldSearchUsersByNickname() {
            // Given
            Pageable pageable = PageRequest.of(0, 10);

            // When
            Page<User> searchResults = userRepository.searchUsers("User 1", pageable);

            // Then
            assertThat(searchResults.getContent()).hasSize(1);
            assertThat(searchResults.getContent().get(0).getNickname()).isEqualTo("Test User 1");
        }

        @Test
        @DisplayName("Should search users by bio content")
        void shouldSearchUsersByBio() {
            // Given
            Pageable pageable = PageRequest.of(0, 10);

            // When
            Page<User> searchResults = userRepository.searchUsers("bio for user 2", pageable);

            // Then
            assertThat(searchResults.getContent()).hasSize(1);
            assertThat(searchResults.getContent().get(0).getNickname()).isEqualTo("Test User 2");
        }

        @Test
        @DisplayName("Should return empty results for non-matching search")
        void shouldReturnEmptyForNonMatchingSearch() {
            // Given
            Pageable pageable = PageRequest.of(0, 10);

            // When
            Page<User> searchResults = userRepository.searchUsers("nonexistent search term", pageable);

            // Then
            assertThat(searchResults.getContent()).isEmpty();
        }

        @Test
        @DisplayName("Should exclude inactive and deleted users from search")
        void shouldExcludeInactiveAndDeletedUsersFromSearch() {
            // Given
            Pageable pageable = PageRequest.of(0, 10);

            // When
            Page<User> searchResults = userRepository.searchUsers("Test", pageable);

            // Then
            assertThat(searchResults.getContent()).hasSize(2);
            assertThat(searchResults.getContent())
                .extracting(User::getNickname)
                .containsExactlyInAnyOrder("Test User 1", "Test User 2")
                .doesNotContain("Test User 3"); // inactive user should be excluded
        }
    }

    @Nested
    @DisplayName("Follower Relationship Queries")
    class FollowerRelationshipQueries {

        @Test
        @DisplayName("Should handle empty follower relationships")
        void shouldHandleEmptyFollowerRelationships() {
            // Given
            Pageable pageable = PageRequest.of(0, 10);

            // When
            Page<User> following = userRepository.findFollowing(testUser1.getId(), pageable);
            Page<User> followers = userRepository.findFollowers(testUser1.getId(), pageable);
            Long followingCount = userRepository.countFollowing(testUser1.getId());
            Long followerCount = userRepository.countFollowers(testUser1.getId());

            // Then
            assertThat(following.getContent()).isEmpty();
            assertThat(followers.getContent()).isEmpty();
            assertThat(followingCount).isEqualTo(0L);
            assertThat(followerCount).isEqualTo(0L);
        }

        @Test
        @DisplayName("Should manage follower relationships correctly")
        void shouldManageFollowerRelationshipsCorrectly() {
            // Given - Set up follower relationship: testUser2 follows testUser1
            testUser1.getFollowers().add(testUser2);
            testUser2.getFollowing().add(testUser1);
            userRepository.save(testUser1);
            userRepository.save(testUser2);
            entityManager.flush();
            entityManager.clear();

            Pageable pageable = PageRequest.of(0, 10);

            // When
            Page<User> user1Followers = userRepository.findFollowers(testUser1.getId(), pageable);
            Page<User> user2Following = userRepository.findFollowing(testUser2.getId(), pageable);
            Long user1FollowerCount = userRepository.countFollowers(testUser1.getId());
            Long user2FollowingCount = userRepository.countFollowing(testUser2.getId());

            // Then
            assertThat(user1Followers.getContent()).hasSize(1);
            assertThat(user1Followers.getContent().get(0).getId()).isEqualTo(testUser2.getId());

            assertThat(user2Following.getContent()).hasSize(1);
            assertThat(user2Following.getContent().get(0).getId()).isEqualTo(testUser1.getId());

            assertThat(user1FollowerCount).isEqualTo(1L);
            assertThat(user2FollowingCount).isEqualTo(1L);
        }

        @Test
        @DisplayName("Should handle multiple follower relationships")
        void shouldHandleMultipleFollowerRelationships() {
            // Given - Set up multiple relationships
            testUser1.getFollowers().add(testUser2);
            testUser1.getFollowers().add(testUser3);
            testUser2.getFollowing().add(testUser1);
            testUser3.getFollowing().add(testUser1);

            userRepository.save(testUser1);
            userRepository.save(testUser2);
            userRepository.save(testUser3);
            entityManager.flush();
            entityManager.clear();

            Pageable pageable = PageRequest.of(0, 10);

            // When
            Page<User> user1Followers = userRepository.findFollowers(testUser1.getId(), pageable);
            Long user1FollowerCount = userRepository.countFollowers(testUser1.getId());

            // Then
            assertThat(user1Followers.getContent()).hasSize(2);
            assertThat(user1Followers.getContent())
                .extracting(User::getId)
                .containsExactlyInAnyOrder(testUser2.getId(), testUser3.getId());
            assertThat(user1FollowerCount).isEqualTo(2L);
        }
    }

    @Nested
    @DisplayName("Database Constraints and Validation")
    class DatabaseConstraintsAndValidation {

        @Test
        @DisplayName("Should enforce unique email constraint")
        void shouldEnforceUniqueEmailConstraint() {
            // Given
            User duplicateEmailUser = User.builder()
                .email("test1@oddiya.com") // Same email as testUser1
                .nickname("Different Nickname")
                .provider("apple")
                .providerId("apple-different")
                .build();

            // When & Then
            assertThatThrownBy(() -> {
                userRepository.save(duplicateEmailUser);
                entityManager.flush();
            }).isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("Should enforce unique username constraint")
        void shouldEnforceUniqueUsernameConstraint() {
            // Given
            User duplicateUsernameUser = User.builder()
                .email("different@test.com")
                .username("testuser1") // Same username as testUser1
                .nickname("Different Nickname")
                .provider("apple")
                .providerId("apple-different")
                .build();

            // When & Then
            assertThatThrownBy(() -> {
                userRepository.save(duplicateUsernameUser);
                entityManager.flush();
            }).isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("Should allow null username")
        void shouldAllowNullUsername() {
            // Given
            User userWithNullUsername = User.builder()
                .email("null-username@test.com")
                .username(null)
                .nickname("Null Username User")
                .provider("google")
                .providerId("google-null-username")
                .build();

            // When
            User savedUser = userRepository.save(userWithNullUsername);

            // Then
            assertThat(savedUser).isNotNull();
            assertThat(savedUser.getUsername()).isNull();
            assertThat(savedUser.getId()).isNotNull();
        }

        @Test
        @DisplayName("Should validate required fields")
        void shouldValidateRequiredFields() {
            // Given
            User invalidUser = User.builder()
                .email(null) // Required field
                .nickname(null) // Required field
                .provider(null) // Required field
                .providerId(null) // Required field
                .build();

            // When & Then
            assertThatThrownBy(() -> {
                userRepository.save(invalidUser);
                entityManager.flush();
            }).isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Nested
    @DisplayName("Pagination and Sorting")
    class PaginationAndSorting {

        @Test
        @DisplayName("Should paginate active users correctly")
        void shouldPaginateActiveUsersCorrectly() {
            // Given - Create additional test users
            for (int i = 4; i <= 10; i++) {
                createTestUser("test" + i + "@test.com", "Test User " + i, 
                             "google", "google-" + i, true);
            }

            Pageable firstPage = PageRequest.of(0, 3);
            Pageable secondPage = PageRequest.of(1, 3);

            // When
            Page<User> firstPageResult = userRepository.findActiveUsers(firstPage);
            Page<User> secondPageResult = userRepository.findActiveUsers(secondPage);

            // Then
            assertThat(firstPageResult.getContent()).hasSize(3);
            assertThat(firstPageResult.getTotalElements()).isEqualTo(9); // 2 original + 7 new active users
            assertThat(firstPageResult.getTotalPages()).isEqualTo(3);
            assertThat(firstPageResult.hasNext()).isTrue();
            assertThat(firstPageResult.hasPrevious()).isFalse();

            assertThat(secondPageResult.getContent()).hasSize(3);
            assertThat(secondPageResult.hasNext()).isTrue();
            assertThat(secondPageResult.hasPrevious()).isTrue();
        }

        @Test
        @DisplayName("Should handle empty pagination results")
        void shouldHandleEmptyPaginationResults() {
            // Given
            Pageable pageable = PageRequest.of(10, 10); // Page beyond available data

            // When
            Page<User> result = userRepository.findActiveUsers(pageable);

            // Then
            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isEqualTo(2);
            assertThat(result.hasNext()).isFalse();
            assertThat(result.hasPrevious()).isTrue();
        }

        @Test
        @DisplayName("Should paginate search results correctly")
        void shouldPaginateSearchResultsCorrectly() {
            // Given - Create additional users matching search criteria
            for (int i = 4; i <= 6; i++) {
                User user = createTestUser("search" + i + "@test.com", "Search User " + i, 
                                        "google", "google-search-" + i, true);
                user.setBio("This is a searchable bio " + i);
                userRepository.save(user);
            }

            Pageable pageable = PageRequest.of(0, 2);

            // When
            Page<User> searchResults = userRepository.searchUsers("searchable", pageable);

            // Then
            assertThat(searchResults.getContent()).hasSize(2);
            assertThat(searchResults.getTotalElements()).isEqualTo(3);
            assertThat(searchResults.hasNext()).isTrue();
        }
    }

    @Nested
    @DisplayName("Entity Relationships and Cascade Operations")
    class EntityRelationshipsAndCascadeOperations {

        @Test
        @DisplayName("Should handle user preferences correctly")
        void shouldHandleUserPreferencesCorrectly() {
            // Given
            testUser1.getPreferences().put("newKey", "newValue");
            testUser1.getTravelPreferences().put("accommodation", "hostel");

            // When
            User savedUser = userRepository.save(testUser1);
            entityManager.flush();
            entityManager.clear();

            User reloadedUser = userRepository.findById(savedUser.getId()).orElse(null);

            // Then
            assertThat(reloadedUser).isNotNull();
            assertThat(reloadedUser.getPreferences()).containsEntry("newKey", "newValue");
            assertThat(reloadedUser.getTravelPreferences()).containsEntry("accommodation", "hostel");
        }

        @Test
        @DisplayName("Should initialize collections properly")
        void shouldInitializeCollectionsProperly() {
            // Given
            User newUser = User.builder()
                .email("collections@test.com")
                .nickname("Collections User")
                .provider("google")
                .providerId("google-collections")
                .build();

            // When
            User savedUser = userRepository.save(newUser);

            // Then
            assertThat(savedUser.getPreferences()).isNotNull();
            assertThat(savedUser.getTravelPreferences()).isNotNull();
            assertThat(savedUser.getTravelPlans()).isNotNull();
            assertThat(savedUser.getReviews()).isNotNull();
            assertThat(savedUser.getVideos()).isNotNull();
            assertThat(savedUser.getFollowers()).isNotNull();
            assertThat(savedUser.getFollowing()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Audit and Timestamp Functionality")
    class AuditAndTimestampFunctionality {

        @Test
        @DisplayName("Should automatically set created and updated timestamps")
        void shouldAutomaticallySetCreatedAndUpdatedTimestamps() {
            // Given
            User newUser = User.builder()
                .email("timestamp@test.com")
                .nickname("Timestamp User")
                .provider("google")
                .providerId("google-timestamp")
                .build();

            // When
            User savedUser = userRepository.save(newUser);

            // Then
            assertThat(savedUser.getCreatedAt()).isNotNull();
            assertThat(savedUser.getUpdatedAt()).isNotNull();
            assertThat(savedUser.getCreatedAt()).isEqualTo(savedUser.getUpdatedAt());
            assertThat(savedUser.getVersion()).isEqualTo(0L);
        }

        @Test
        @DisplayName("Should update timestamp on entity modification")
        void shouldUpdateTimestampOnEntityModification() throws InterruptedException {
            // Given
            User user = testUser1;
            var originalUpdatedAt = user.getUpdatedAt();

            // Add small delay to ensure timestamp difference
            Thread.sleep(10);

            // When
            user.setBio("Modified bio");
            User updatedUser = userRepository.save(user);
            entityManager.flush();

            // Then
            assertThat(updatedUser.getUpdatedAt()).isAfter(originalUpdatedAt);
            assertThat(updatedUser.getVersion()).isEqualTo(1L);
        }

        @Test
        @DisplayName("Should handle optimistic locking with version field")
        void shouldHandleOptimisticLockingWithVersionField() {
            // Given
            User user1 = userRepository.findById(testUser1.getId()).orElse(null);
            User user2 = userRepository.findById(testUser1.getId()).orElse(null);

            assertThat(user1).isNotNull();
            assertThat(user2).isNotNull();
            assertThat(user1.getVersion()).isEqualTo(user2.getVersion());

            // When - Modify and save first instance
            user1.setBio("Modified by user1");
            userRepository.save(user1);
            entityManager.flush();

            // Then - Second instance should have stale version
            user2.setBio("Modified by user2");
            assertThatThrownBy(() -> {
                userRepository.save(user2);
                entityManager.flush();
            }).isInstanceOf(org.springframework.orm.ObjectOptimisticLockingFailureException.class);
        }
    }

    @Nested
    @DisplayName("Transaction Boundaries and Rollback Scenarios")
    class TransactionBoundariesAndRollbackScenarios {

        @Test
        @Transactional
        @Rollback
        @DisplayName("Should rollback transaction on exception")
        void shouldRollbackTransactionOnException() {
            // Given
            String originalEmail = testUser1.getEmail();
            String originalBio = testUser1.getBio();

            // When - Simulate transaction failure
            assertThatThrownBy(() -> {
                testUser1.setEmail("updated@test.com");
                testUser1.setBio("Updated bio");
                userRepository.save(testUser1);
                entityManager.flush();
                
                // Force constraint violation to trigger rollback
                User duplicateUser = User.builder()
                    .email("updated@test.com")
                    .nickname("Duplicate User")
                    .provider("google")
                    .providerId("google-duplicate")
                    .build();
                userRepository.save(duplicateUser);
                entityManager.flush();
            }).isInstanceOf(DataIntegrityViolationException.class);

            // Then - Verify rollback occurred
            entityManager.clear();
            User reloadedUser = userRepository.findById(testUser1.getId()).orElse(null);
            assertThat(reloadedUser).isNotNull();
            assertThat(reloadedUser.getEmail()).isEqualTo(originalEmail);
            assertThat(reloadedUser.getBio()).isEqualTo(originalBio);
        }

        @Test
        @Transactional(propagation = Propagation.REQUIRES_NEW)
        @DisplayName("Should handle nested transactions correctly")
        void shouldHandleNestedTransactionsCorrectly() {
            // Given
            User outerUser = createTestUser("outer@test.com", "Outer User", "google", "google-outer", true);
            
            // When - Simulate nested transaction
            try {
                // Outer transaction modifies user
                outerUser.setBio("Modified in outer transaction");
                userRepository.save(outerUser);
                
                // Inner transaction (new propagation) fails
                this.performInnerTransactionThatFails();
                
            } catch (DataIntegrityViolationException e) {
                // Expected exception from inner transaction
            }

            // Then - Verify outer transaction state
            entityManager.flush();
            entityManager.clear();
            User reloadedUser = userRepository.findById(outerUser.getId()).orElse(null);
            assertThat(reloadedUser).isNotNull();
            assertThat(reloadedUser.getBio()).isEqualTo("Modified in outer transaction");
        }

        @Transactional(propagation = Propagation.REQUIRES_NEW)
        private void performInnerTransactionThatFails() {
            // This will fail due to constraint violation
            User invalidUser = User.builder()
                .email("outer@test.com") // Duplicate email
                .nickname("Invalid User")
                .provider("google")
                .providerId("google-invalid")
                .build();
            userRepository.save(invalidUser);
            entityManager.flush();
        }

        @Test
        @DisplayName("Should maintain data consistency during concurrent modifications")
        void shouldMaintainDataConsistencyDuringConcurrentModifications() throws Exception {
            // Given
            User concurrentUser = createTestUser("concurrent@test.com", "Concurrent User", 
                                               "google", "google-concurrent", true);
            ExecutorService executor = Executors.newFixedThreadPool(3);

            // When - Perform concurrent modifications
            CompletableFuture<Void> future1 = CompletableFuture.runAsync(() -> {
                try {
                    User user = userRepository.findById(concurrentUser.getId()).orElse(null);
                    user.setBio("Updated by thread 1");
                    userRepository.save(user);
                    Thread.sleep(100); // Simulate processing time
                } catch (Exception e) {
                    // Expected in concurrent scenario
                }
            }, executor);

            CompletableFuture<Void> future2 = CompletableFuture.runAsync(() -> {
                try {
                    User user = userRepository.findById(concurrentUser.getId()).orElse(null);
                    user.setNickname("Updated by thread 2");
                    userRepository.save(user);
                    Thread.sleep(100); // Simulate processing time
                } catch (Exception e) {
                    // Expected in concurrent scenario
                }
            }, executor);

            CompletableFuture<Void> future3 = CompletableFuture.runAsync(() -> {
                try {
                    User user = userRepository.findById(concurrentUser.getId()).orElse(null);
                    user.getPreferences().put("concurrent", "thread3");
                    userRepository.save(user);
                    Thread.sleep(100); // Simulate processing time
                } catch (Exception e) {
                    // Expected in concurrent scenario
                }
            }, executor);

            // Then - Wait for completion and verify final state
            CompletableFuture.allOf(future1, future2, future3).join();
            executor.shutdown();

            // Verify that the entity still exists and has consistent state
            User finalUser = userRepository.findById(concurrentUser.getId()).orElse(null);
            assertThat(finalUser).isNotNull();
            assertThat(finalUser.getEmail()).isEqualTo("concurrent@test.com");
        }
    }

    @Nested
    @DisplayName("Advanced Sorting and Complex Queries")
    class AdvancedSortingAndComplexQueries {

        @Test
        @DisplayName("Should support complex sorting combinations")
        void shouldSupportComplexSortingCombinations() {
            // Given - Create users with different characteristics
            User user1 = createTestUser("sort1@test.com", "Alpha User", "google", "sort-1", true);
            User user2 = createTestUser("sort2@test.com", "Beta User", "apple", "sort-2", true);
            User user3 = createTestUser("sort3@test.com", "Alpha User", "apple", "sort-3", true);
            
            // When - Sort by nickname ASC, then provider DESC
            Sort complexSort = Sort.by(
                Sort.Order.asc("nickname"),
                Sort.Order.desc("provider")
            );
            Pageable pageable = PageRequest.of(0, 10, complexSort);
            Page<User> sortedResults = userRepository.findActiveUsers(pageable);

            // Then - Verify sorting order
            List<User> users = sortedResults.getContent();
            assertThat(users.size()).isGreaterThanOrEqualTo(3);
            
            // Find our test users in the results
            List<User> testUsers = users.stream()
                .filter(u -> u.getEmail().startsWith("sort"))
                .toList();
            
            assertThat(testUsers).hasSize(3);
            // First should be "Alpha User" with "google" provider (nickname ASC, provider DESC)
            assertThat(testUsers.get(0).getNickname()).isEqualTo("Alpha User");
            assertThat(testUsers.get(0).getProvider()).isEqualTo("google");
        }

        @Test
        @DisplayName("Should handle search with special characters and SQL injection attempts")
        void shouldHandleSearchWithSpecialCharactersAndSQLInjectionAttempts() {
            // Given
            User specialUser = createTestUser("special@test.com", "User's \"Special\" Name", 
                                           "google", "special-chars", true);
            specialUser.setBio("Bio with 'quotes' and %wildcards% and --comments");
            userRepository.save(specialUser);

            Pageable pageable = PageRequest.of(0, 10);

            // When - Search with special characters
            Page<User> results1 = userRepository.searchUsers("User's", pageable);
            Page<User> results2 = userRepository.searchUsers("quotes", pageable);
            Page<User> results3 = userRepository.searchUsers("%wildcards%", pageable);

            // Then - Should find user safely without SQL injection
            assertThat(results1.getContent()).hasSize(1);
            assertThat(results1.getContent().get(0).getNickname()).contains("User's");

            assertThat(results2.getContent()).hasSize(1);
            assertThat(results2.getContent().get(0).getBio()).contains("quotes");

            assertThat(results3.getContent()).hasSize(1);
            assertThat(results3.getContent().get(0).getBio()).contains("wildcards");

            // When - Attempt SQL injection
            Page<User> injectionAttempt = userRepository.searchUsers("'; DROP TABLE users; --", pageable);

            // Then - Should not cause issues (returns empty or safe results)
            assertThat(injectionAttempt.getContent()).isEmpty();
            
            // Verify users table still exists and is functional
            assertThat(userRepository.count()).isGreaterThan(0);
        }

        @Test
        @DisplayName("Should perform efficient batch operations")
        void shouldPerformEfficientBatchOperations() {
            // Given - Create multiple users for batch processing
            List<User> batchUsers = List.of(
                User.builder().email("batch1@test.com").nickname("Batch User 1").provider("google").providerId("batch-1").build(),
                User.builder().email("batch2@test.com").nickname("Batch User 2").provider("google").providerId("batch-2").build(),
                User.builder().email("batch3@test.com").nickname("Batch User 3").provider("google").providerId("batch-3").build(),
                User.builder().email("batch4@test.com").nickname("Batch User 4").provider("google").providerId("batch-4").build(),
                User.builder().email("batch5@test.com").nickname("Batch User 5").provider("google").providerId("batch-5").build()
            );

            // When - Save all users in batch
            List<User> savedUsers = userRepository.saveAll(batchUsers);
            entityManager.flush();

            // Then - Verify batch save
            assertThat(savedUsers).hasSize(5);
            assertThat(savedUsers).allMatch(user -> user.getId() != null);
            assertThat(savedUsers).allMatch(user -> user.getCreatedAt() != null);

            // When - Find all saved users
            List<String> userIds = savedUsers.stream().map(User::getId).toList();
            List<User> foundUsers = userRepository.findAllById(userIds);

            // Then - Verify batch retrieval
            assertThat(foundUsers).hasSize(5);
            assertThat(foundUsers)
                .extracting(User::getEmail)
                .containsExactlyInAnyOrder("batch1@test.com", "batch2@test.com", "batch3@test.com", 
                                         "batch4@test.com", "batch5@test.com");

            // When - Delete all batch users
            userRepository.deleteAllById(userIds);

            // Then - Verify batch deletion
            List<User> deletedUsers = userRepository.findAllById(userIds);
            assertThat(deletedUsers).isEmpty();
        }
    }

    @Nested
    @DisplayName("Element Collection and Map Handling")
    class ElementCollectionAndMapHandling {

        @Test
        @DisplayName("Should handle complex preference updates atomically")
        void shouldHandleComplexPreferenceUpdatesAtomically() {
            // Given
            User user = testUser1;
            Map<String, String> originalPrefs = Map.copyOf(user.getPreferences());
            Map<String, String> originalTravelPrefs = Map.copyOf(user.getTravelPreferences());

            // When - Update multiple preferences
            user.getPreferences().clear();
            user.getPreferences().putAll(Map.of(
                "theme", "dark",
                "language", "en",
                "notifications", "enabled",
                "currency", "USD"
            ));

            user.getTravelPreferences().clear();
            user.getTravelPreferences().putAll(Map.of(
                "budget", "high",
                "accommodation", "hotel",
                "transportation", "flight",
                "activity_level", "moderate"
            ));

            User savedUser = userRepository.save(user);
            entityManager.flush();
            entityManager.clear();

            // Then - Verify atomic update
            User reloadedUser = userRepository.findById(savedUser.getId()).orElse(null);
            assertThat(reloadedUser).isNotNull();
            
            assertThat(reloadedUser.getPreferences()).hasSize(4);
            assertThat(reloadedUser.getPreferences())
                .containsEntry("theme", "dark")
                .containsEntry("language", "en")
                .containsEntry("notifications", "enabled")
                .containsEntry("currency", "USD");

            assertThat(reloadedUser.getTravelPreferences()).hasSize(4);
            assertThat(reloadedUser.getTravelPreferences())
                .containsEntry("budget", "high")
                .containsEntry("accommodation", "hotel")
                .containsEntry("transportation", "flight")
                .containsEntry("activity_level", "moderate");
        }

        @Test
        @DisplayName("Should handle empty and null preference collections")
        void shouldHandleEmptyAndNullPreferenceCollections() {
            // Given
            User user = User.builder()
                .email("empty-prefs@test.com")
                .nickname("Empty Prefs User")
                .provider("google")
                .providerId("empty-prefs")
                .build();

            // When - Save user with default empty collections
            User savedUser = userRepository.save(user);
            entityManager.flush();
            entityManager.clear();

            // Then - Verify empty collections are handled properly
            User reloadedUser = userRepository.findById(savedUser.getId()).orElse(null);
            assertThat(reloadedUser).isNotNull();
            assertThat(reloadedUser.getPreferences()).isNotNull().isEmpty();
            assertThat(reloadedUser.getTravelPreferences()).isNotNull().isEmpty();

            // When - Add preferences after initial save
            reloadedUser.getPreferences().put("added", "later");
            reloadedUser.getTravelPreferences().put("travel", "preference");
            User updatedUser = userRepository.save(reloadedUser);
            entityManager.flush();

            // Then - Verify preferences were added
            assertThat(updatedUser.getPreferences()).hasSize(1).containsEntry("added", "later");
            assertThat(updatedUser.getTravelPreferences()).hasSize(1).containsEntry("travel", "preference");
        }
    }
}