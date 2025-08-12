package com.oddiya.repository;

import com.oddiya.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive tests for UserRepository covering:
 * - Basic CRUD operations
 * - Custom finder methods
 * - Complex query methods
 * - Relationship handling
 * - Database constraints
 * - Pagination and sorting
 */
@DisplayName("UserRepository Tests")
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
}