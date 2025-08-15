package com.oddiya.service;

import com.oddiya.dto.request.UpdateUserProfileRequest;
import com.oddiya.dto.response.PageResponse;
import com.oddiya.dto.response.UserProfileResponse;
import com.oddiya.entity.User;
import com.oddiya.exception.NotFoundException;
import com.oddiya.repository.UserRepository;
import com.oddiya.service.impl.UserServiceImpl;
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
@DisplayName("UserService Tests")
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserServiceImpl userService;

    private User testUser;
    private UpdateUserProfileRequest updateRequest;
    private Pageable pageable;
    private Map<String, String> testPreferences;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id("user123")
                .email("test@example.com")
                .username("testuser")
                .nickname("Test User")
                .bio("I love traveling!")
                .profileImageUrl("https://example.com/avatar.jpg")
                .provider("google")
                .providerId("google123")
                .isEmailVerified(true)
                .isPremium(false)
                .isActive(true)
                .lastLoginAt(LocalDateTime.now().minusHours(1))
                .createdAt(LocalDateTime.now().minusDays(30))
                .updatedAt(LocalDateTime.now().minusHours(1))
                .build();

        testPreferences = new HashMap<>();
        testPreferences.put("language", "en");
        testPreferences.put("timezone", "UTC");
        testPreferences.put("notifications", "enabled");
        testUser.setPreferences(testPreferences);

        updateRequest = UpdateUserProfileRequest.builder()
                .nickname("Updated User")
                .bio("Updated bio text")
                .profileImageUrl("https://example.com/new-avatar.jpg")
                .build();

        pageable = PageRequest.of(0, 10, Sort.by("nickname"));
    }

    @Nested
    @DisplayName("Get User Profile Tests")
    class GetUserProfileTests {

        @Test
        @DisplayName("Should successfully get user profile by ID")
        void shouldSuccessfullyGetUserProfileById() {
            // Given
            when(userRepository.findById("user123")).thenReturn(Optional.of(testUser));

            // When
            UserProfileResponse response = userService.getUserProfile("user123");

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo("user123");
            assertThat(response.getEmail()).isEqualTo("test@example.com");
            assertThat(response.getNickname()).isEqualTo("Test User");
            assertThat(response.getBio()).isEqualTo("I love traveling!");
            assertThat(response.getProfileImageUrl()).isEqualTo("https://example.com/avatar.jpg");
            assertThat(response.getPreferences()).isEqualTo(testPreferences);
            assertThat(response.getIsEmailVerified()).isTrue();
            assertThat(response.getIsPremium()).isFalse();
            assertThat(response.getIsActive()).isTrue();
            assertThat(response.getProvider()).isEqualTo("google");
            assertThat(response.getLastLoginAt()).isNotNull();
        }

        @Test
        @DisplayName("Should throw NotFoundException when user not found by ID")
        void shouldThrowNotFoundExceptionWhenUserNotFoundById() {
            // Given
            when(userRepository.findById("nonexistent")).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> userService.getUserProfile("nonexistent"))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("User not found with ID: nonexistent");
        }

        @Test
        @DisplayName("Should successfully get user profile by email")
        void shouldSuccessfullyGetUserProfileByEmail() {
            // Given
            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));

            // When
            UserProfileResponse response = userService.getUserProfileByEmail("test@example.com");

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo("user123");
            assertThat(response.getEmail()).isEqualTo("test@example.com");
        }

        @Test
        @DisplayName("Should throw NotFoundException when user not found by email")
        void shouldThrowNotFoundExceptionWhenUserNotFoundByEmail() {
            // Given
            when(userRepository.findByEmail("nonexistent@example.com")).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> userService.getUserProfileByEmail("nonexistent@example.com"))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("User not found with email: nonexistent@example.com");
        }

        @Test
        @DisplayName("Should handle user with null preferences")
        void shouldHandleUserWithNullPreferences() {
            // Given
            testUser.setPreferences(null);
            when(userRepository.findById("user123")).thenReturn(Optional.of(testUser));

            // When
            UserProfileResponse response = userService.getUserProfile("user123");

            // Then
            assertThat(response.getPreferences()).isNull();
        }

        @Test
        @DisplayName("Should handle user with null bio and profile image")
        void shouldHandleUserWithNullBioAndProfileImage() {
            // Given
            testUser.setBio(null);
            testUser.setProfileImageUrl(null);
            when(userRepository.findById("user123")).thenReturn(Optional.of(testUser));

            // When
            UserProfileResponse response = userService.getUserProfile("user123");

            // Then
            assertThat(response.getBio()).isNull();
            assertThat(response.getProfileImageUrl()).isNull();
        }
    }

    @Nested
    @DisplayName("Update User Profile Tests")
    class UpdateUserProfileTests {

        @Test
        @DisplayName("Should successfully update user profile with all fields")
        void shouldSuccessfullyUpdateUserProfileWithAllFields() {
            // Given
            when(userRepository.findById("user123")).thenReturn(Optional.of(testUser));
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            // When
            UserProfileResponse response = userService.updateUserProfile("user123", updateRequest);

            // Then
            assertThat(response).isNotNull();
            verify(userRepository).save(argThat(user -> {
                assertThat(user.getNickname()).isEqualTo("Updated User");
                assertThat(user.getBio()).isEqualTo("Updated bio text");
                assertThat(user.getProfileImageUrl()).isEqualTo("https://example.com/new-avatar.jpg");
                return true;
            }));
        }

        @Test
        @DisplayName("Should update only non-null fields")
        void shouldUpdateOnlyNonNullFields() {
            // Given
            UpdateUserProfileRequest partialRequest = UpdateUserProfileRequest.builder()
                    .nickname("Only Nickname Updated")
                    .build();

            when(userRepository.findById("user123")).thenReturn(Optional.of(testUser));
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            // When
            userService.updateUserProfile("user123", partialRequest);

            // Then
            verify(userRepository).save(argThat(user -> {
                assertThat(user.getNickname()).isEqualTo("Only Nickname Updated");
                assertThat(user.getBio()).isEqualTo("I love traveling!"); // Should remain unchanged
                assertThat(user.getProfileImageUrl()).isEqualTo("https://example.com/avatar.jpg"); // Should remain unchanged
                return true;
            }));
        }

        @Test
        @DisplayName("Should throw NotFoundException when user not found for update")
        void shouldThrowNotFoundExceptionWhenUserNotFoundForUpdate() {
            // Given
            when(userRepository.findById("nonexistent")).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> userService.updateUserProfile("nonexistent", updateRequest))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("User not found with ID: nonexistent");

            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("Should handle empty update request")
        void shouldHandleEmptyUpdateRequest() {
            // Given
            UpdateUserProfileRequest emptyRequest = UpdateUserProfileRequest.builder().build();
            when(userRepository.findById("user123")).thenReturn(Optional.of(testUser));
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            // When
            UserProfileResponse response = userService.updateUserProfile("user123", emptyRequest);

            // Then
            assertThat(response).isNotNull();
            verify(userRepository).save(argThat(user -> {
                // All fields should remain unchanged
                assertThat(user.getNickname()).isEqualTo("Test User");
                assertThat(user.getBio()).isEqualTo("I love traveling!");
                assertThat(user.getProfileImageUrl()).isEqualTo("https://example.com/avatar.jpg");
                return true;
            }));
        }

        @Test
        @DisplayName("Should handle very long nickname and bio")
        void shouldHandleVeryLongNicknameAndBio() {
            // Given
            String longNickname = "A".repeat(255);
            String longBio = "B".repeat(1000);
            UpdateUserProfileRequest longRequest = UpdateUserProfileRequest.builder()
                    .nickname(longNickname)
                    .bio(longBio)
                    .build();

            when(userRepository.findById("user123")).thenReturn(Optional.of(testUser));
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            // When
            userService.updateUserProfile("user123", longRequest);

            // Then
            verify(userRepository).save(argThat(user -> {
                assertThat(user.getNickname()).isEqualTo(longNickname);
                assertThat(user.getBio()).isEqualTo(longBio);
                return true;
            }));
        }
    }

    @Nested
    @DisplayName("Update User Preferences Tests")
    class UpdateUserPreferencesTests {

        @Test
        @DisplayName("Should successfully update user preferences")
        void shouldSuccessfullyUpdateUserPreferences() {
            // Given
            Map<String, String> newPreferences = new HashMap<>();
            newPreferences.put("language", "ko");
            newPreferences.put("theme", "dark");
            newPreferences.put("notifications", "disabled");

            when(userRepository.findById("user123")).thenReturn(Optional.of(testUser));
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            // When
            UserProfileResponse response = userService.updateUserPreferences("user123", newPreferences);

            // Then
            assertThat(response).isNotNull();
            verify(userRepository).save(argThat(user -> {
                assertThat(user.getPreferences()).isEqualTo(newPreferences);
                assertThat(user.getPreferences().get("language")).isEqualTo("ko");
                assertThat(user.getPreferences().get("theme")).isEqualTo("dark");
                return true;
            }));
        }

        @Test
        @DisplayName("Should handle empty preferences map")
        void shouldHandleEmptyPreferencesMap() {
            // Given
            Map<String, String> emptyPreferences = new HashMap<>();
            when(userRepository.findById("user123")).thenReturn(Optional.of(testUser));
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            // When
            userService.updateUserPreferences("user123", emptyPreferences);

            // Then
            verify(userRepository).save(argThat(user -> {
                assertThat(user.getPreferences()).isEmpty();
                return true;
            }));
        }

        @Test
        @DisplayName("Should handle null preferences map")
        void shouldHandleNullPreferencesMap() {
            // Given
            when(userRepository.findById("user123")).thenReturn(Optional.of(testUser));
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            // When
            userService.updateUserPreferences("user123", null);

            // Then
            verify(userRepository).save(argThat(user -> {
                assertThat(user.getPreferences()).isNull();
                return true;
            }));
        }

        @Test
        @DisplayName("Should throw NotFoundException when user not found for preferences update")
        void shouldThrowNotFoundExceptionWhenUserNotFoundForPreferencesUpdate() {
            // Given
            when(userRepository.findById("nonexistent")).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> userService.updateUserPreferences("nonexistent", testPreferences))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("User not found with ID: nonexistent");
        }
    }

    @Nested
    @DisplayName("Delete User Account Tests")
    class DeleteUserAccountTests {

        @Test
        @DisplayName("Should successfully delete user account")
        void shouldSuccessfullyDeleteUserAccount() {
            // Given
            when(userRepository.findById("user123")).thenReturn(Optional.of(testUser));

            // When
            userService.deleteUserAccount("user123");

            // Then
            verify(userRepository).delete(testUser);
        }

        @Test
        @DisplayName("Should throw NotFoundException when user not found for delete")
        void shouldThrowNotFoundExceptionWhenUserNotFoundForDelete() {
            // Given
            when(userRepository.findById("nonexistent")).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> userService.deleteUserAccount("nonexistent"))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("User not found with ID: nonexistent");

            verify(userRepository, never()).delete(any(User.class));
        }
    }

    @Nested
    @DisplayName("Search Users Tests")
    class SearchUsersTests {

        @Test
        @DisplayName("Should successfully search users by query")
        void shouldSuccessfullySearchUsersByQuery() {
            // Given
            List<User> users = Arrays.asList(testUser);
            Page<User> userPage = new PageImpl<>(users, pageable, 1);
            when(userRepository.searchUsers("test", pageable)).thenReturn(userPage);

            // When
            PageResponse<UserProfileResponse> response = userService.searchUsers("test", pageable);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getContent()).hasSize(1);
            assertThat(response.getTotalElements()).isEqualTo(1);
            assertThat(response.getContent().get(0).getNickname()).isEqualTo("Test User");
        }

        @Test
        @DisplayName("Should return empty result when no users match query")
        void shouldReturnEmptyResultWhenNoUsersMatchQuery() {
            // Given
            Page<User> emptyPage = new PageImpl<>(new ArrayList<>(), pageable, 0);
            when(userRepository.searchUsers("nonexistent", pageable)).thenReturn(emptyPage);

            // When
            PageResponse<UserProfileResponse> response = userService.searchUsers("nonexistent", pageable);

            // Then
            assertThat(response.getContent()).isEmpty();
            assertThat(response.getTotalElements()).isEqualTo(0);
            assertThat(response.isEmpty()).isTrue();
        }

        @Test
        @DisplayName("Should handle pagination correctly")
        void shouldHandlePaginationCorrectly() {
            // Given
            List<User> users = Arrays.asList(testUser);
            Page<User> userPage = new PageImpl<>(users, pageable, 10);
            when(userRepository.searchUsers("test", pageable)).thenReturn(userPage);

            // When
            PageResponse<UserProfileResponse> response = userService.searchUsers("test", pageable);

            // Then
            assertThat(response.getPageNumber()).isEqualTo(0);
            assertThat(response.getPageSize()).isEqualTo(10);
            assertThat(response.getTotalElements()).isEqualTo(10);
            assertThat(response.isFirst()).isTrue();
            assertThat(response.isLast()).isFalse();
        }
    }

    @Nested
    @DisplayName("User Existence Tests")
    class UserExistenceTests {

        @Test
        @DisplayName("Should return true when user exists by ID")
        void shouldReturnTrueWhenUserExistsById() {
            // Given
            when(userRepository.existsById("user123")).thenReturn(true);

            // When
            boolean exists = userService.userExists("user123");

            // Then
            assertThat(exists).isTrue();
        }

        @Test
        @DisplayName("Should return false when user does not exist by ID")
        void shouldReturnFalseWhenUserDoesNotExistById() {
            // Given
            when(userRepository.existsById("nonexistent")).thenReturn(false);

            // When
            boolean exists = userService.userExists("nonexistent");

            // Then
            assertThat(exists).isFalse();
        }

        @Test
        @DisplayName("Should return true when email exists")
        void shouldReturnTrueWhenEmailExists() {
            // Given
            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));

            // When
            boolean exists = userService.existsByEmail("test@example.com");

            // Then
            assertThat(exists).isTrue();
        }

        @Test
        @DisplayName("Should return false when email does not exist")
        void shouldReturnFalseWhenEmailDoesNotExist() {
            // Given
            when(userRepository.findByEmail("nonexistent@example.com")).thenReturn(Optional.empty());

            // When
            boolean exists = userService.existsByEmail("nonexistent@example.com");

            // Then
            assertThat(exists).isFalse();
        }
    }

    @Nested
    @DisplayName("User Activation Tests")
    class UserActivationTests {

        @Test
        @DisplayName("Should successfully activate user")
        void shouldSuccessfullyActivateUser() {
            // Given
            testUser.setActive(false);
            when(userRepository.findById("user123")).thenReturn(Optional.of(testUser));
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            // When
            userService.activateUser("user123");

            // Then
            verify(userRepository).save(argThat(user -> {
                assertThat(user.isActive()).isTrue();
                return true;
            }));
        }

        @Test
        @DisplayName("Should successfully deactivate user")
        void shouldSuccessfullyDeactivateUser() {
            // Given
            when(userRepository.findById("user123")).thenReturn(Optional.of(testUser));
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            // When
            userService.deactivateUser("user123");

            // Then
            verify(userRepository).save(argThat(user -> {
                assertThat(user.isActive()).isFalse();
                return true;
            }));
        }

        @Test
        @DisplayName("Should throw NotFoundException when user not found for activation")
        void shouldThrowNotFoundExceptionWhenUserNotFoundForActivation() {
            // Given
            when(userRepository.findById("nonexistent")).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> userService.activateUser("nonexistent"))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("User not found with ID: nonexistent");
        }

        @Test
        @DisplayName("Should throw NotFoundException when user not found for deactivation")
        void shouldThrowNotFoundExceptionWhenUserNotFoundForDeactivation() {
            // Given
            when(userRepository.findById("nonexistent")).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> userService.deactivateUser("nonexistent"))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("User not found with ID: nonexistent");
        }
    }

    @Nested
    @DisplayName("Last Login Tests")
    class LastLoginTests {

        @Test
        @DisplayName("Should successfully update last login timestamp")
        void shouldSuccessfullyUpdateLastLoginTimestamp() {
            // Given
            LocalDateTime beforeUpdate = LocalDateTime.now();
            when(userRepository.findById("user123")).thenReturn(Optional.of(testUser));
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            // When
            userService.updateLastLoginTime("user123");

            // Then
            verify(userRepository).save(argThat(user -> {
                assertThat(user.getLastLoginAt()).isAfter(beforeUpdate.minusSeconds(1));
                assertThat(user.getLastLoginAt()).isBefore(LocalDateTime.now().plusSeconds(1));
                return true;
            }));
        }

        @Test
        @DisplayName("Should throw NotFoundException when user not found for last login update")
        void shouldThrowNotFoundExceptionWhenUserNotFoundForLastLoginUpdate() {
            // Given
            when(userRepository.findById("nonexistent")).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> userService.updateLastLoginTime("nonexistent"))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("User not found with ID: nonexistent");
        }
    }

    @Nested
    @DisplayName("Parameterized Tests")
    class ParameterizedTests {

        @ParameterizedTest
        @ValueSource(strings = {"google", "facebook", "github", "microsoft", "apple"})
        @DisplayName("Should handle users from different OAuth providers")
        void shouldHandleUsersFromDifferentOAuthProviders(String provider) {
            // Given
            testUser.setProvider(provider);
            when(userRepository.findById("user123")).thenReturn(Optional.of(testUser));

            // When
            UserProfileResponse response = userService.getUserProfile("user123");

            // Then
            assertThat(response.getProvider()).isEqualTo(provider);
        }

        @ParameterizedTest
        @CsvSource({
            "John Doe, john@example.com",
            "Jane Smith, jane.smith@test.org",
            "Bob Johnson, bob.johnson@company.co.uk",
            "Alice Brown, alice.brown+travel@gmail.com"
        })
        @DisplayName("Should handle different name and email combinations")
        void shouldHandleDifferentNameAndEmailCombinations(String nickname, String email) {
            // Given
            testUser.setNickname(nickname);
            testUser.setEmail(email);
            when(userRepository.findByEmail(email)).thenReturn(Optional.of(testUser));

            // When
            UserProfileResponse response = userService.getUserProfileByEmail(email);

            // Then
            assertThat(response.getNickname()).isEqualTo(nickname);
            assertThat(response.getEmail()).isEqualTo(email);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"", " ", "   "})
        @DisplayName("Should handle invalid search queries gracefully")
        void shouldHandleInvalidSearchQueriesGracefully(String query) {
            // Given
            Page<User> emptyPage = new PageImpl<>(new ArrayList<>(), pageable, 0);
            when(userRepository.searchUsers(anyString(), eq(pageable))).thenReturn(emptyPage);

            // When
            PageResponse<UserProfileResponse> response = userService.searchUsers(query, pageable);

            // Then
            assertThat(response.getContent()).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        @DisplayName("Should handle different email verification states")
        void shouldHandleDifferentEmailVerificationStates(boolean isVerified) {
            // Given
            testUser.setEmailVerified(isVerified);
            when(userRepository.findById("user123")).thenReturn(Optional.of(testUser));

            // When
            UserProfileResponse response = userService.getUserProfile("user123");

            // Then
            assertThat(response.getIsEmailVerified()).isEqualTo(isVerified);
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        @DisplayName("Should handle different premium states")
        void shouldHandleDifferentPremiumStates(boolean isPremium) {
            // Given
            testUser.setPremium(isPremium);
            when(userRepository.findById("user123")).thenReturn(Optional.of(testUser));

            // When
            UserProfileResponse response = userService.getUserProfile("user123");

            // Then
            assertThat(response.getIsPremium()).isEqualTo(isPremium);
        }
    }

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle user with very long bio")
        void shouldHandleUserWithVeryLongBio() {
            // Given
            String longBio = "A".repeat(5000);
            testUser.setBio(longBio);
            when(userRepository.findById("user123")).thenReturn(Optional.of(testUser));

            // When
            UserProfileResponse response = userService.getUserProfile("user123");

            // Then
            assertThat(response.getBio()).hasSize(5000);
            assertThat(response.getBio()).isEqualTo(longBio);
        }

        @Test
        @DisplayName("Should handle user with many preferences")
        void shouldHandleUserWithManyPreferences() {
            // Given
            Map<String, String> manyPreferences = new HashMap<>();
            for (int i = 0; i < 100; i++) {
                manyPreferences.put("pref" + i, "value" + i);
            }
            testUser.setPreferences(manyPreferences);
            when(userRepository.findById("user123")).thenReturn(Optional.of(testUser));

            // When
            UserProfileResponse response = userService.getUserProfile("user123");

            // Then
            assertThat(response.getPreferences()).hasSize(100);
        }

        @Test
        @DisplayName("Should handle concurrent profile updates")
        void shouldHandleConcurrentProfileUpdates() {
            // Given
            when(userRepository.findById("user123")).thenReturn(Optional.of(testUser));
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            // When - Simulate concurrent updates
            for (int i = 0; i < 5; i++) {
                UpdateUserProfileRequest request = UpdateUserProfileRequest.builder()
                        .nickname("User " + i)
                        .build();
                assertThatCode(() -> userService.updateUserProfile("user123", request))
                        .doesNotThrowAnyException();
            }

            // Then
            verify(userRepository, times(5)).save(any(User.class));
        }

        @Test
        @DisplayName("Should handle user with null last login")
        void shouldHandleUserWithNullLastLogin() {
            // Given
            testUser.setLastLoginAt(null);
            when(userRepository.findById("user123")).thenReturn(Optional.of(testUser));

            // When
            UserProfileResponse response = userService.getUserProfile("user123");

            // Then
            assertThat(response.getLastLoginAt()).isNull();
        }

        @Test
        @DisplayName("Should handle special characters in search query")
        void shouldHandleSpecialCharactersInSearchQuery() {
            // Given
            String specialQuery = "@#$%^&*()";
            Page<User> emptyPage = new PageImpl<>(new ArrayList<>(), pageable, 0);
            when(userRepository.searchUsers(specialQuery, pageable)).thenReturn(emptyPage);

            // When
            PageResponse<UserProfileResponse> response = userService.searchUsers(specialQuery, pageable);

            // Then
            assertThat(response.getContent()).isEmpty();
            verify(userRepository).searchUsers(specialQuery, pageable);
        }
    }

    @Nested
    @DisplayName("Performance Tests")
    class PerformanceTests {

        @Test
        @DisplayName("Should minimize repository calls for single user operations")
        void shouldMinimizeRepositoryCallsForSingleUserOperations() {
            // Given
            when(userRepository.findById("user123")).thenReturn(Optional.of(testUser));

            // When
            userService.getUserProfile("user123");

            // Then
            verify(userRepository, times(1)).findById("user123");
            verify(userRepository, never()).save(any());
            verify(userRepository, never()).delete(any());
        }

        @Test
        @DisplayName("Should handle large search result pages efficiently")
        void shouldHandleLargeSearchResultPagesEfficiently() {
            // Given
            List<User> manyUsers = new ArrayList<>();
            for (int i = 0; i < 1000; i++) {
                manyUsers.add(User.builder()
                        .id("user" + i)
                        .email("user" + i + "@example.com")
                        .nickname("User " + i)
                        .build());
            }
            Pageable largePage = PageRequest.of(0, 1000);
            Page<User> page = new PageImpl<>(manyUsers, largePage, 1000);
            
            when(userRepository.searchUsers("user", largePage)).thenReturn(page);

            // When
            PageResponse<UserProfileResponse> response = userService.searchUsers("user", largePage);

            // Then
            assertThat(response.getContent()).hasSize(1000);
            verify(userRepository, times(1)).searchUsers("user", largePage);
        }
    }

    @Nested
    @DisplayName("Security Tests")
    class SecurityTests {

        @Test
        @DisplayName("Should not expose sensitive information in user profile")
        void shouldNotExposeSensitiveInformationInUserProfile() {
            // Given
            when(userRepository.findById("user123")).thenReturn(Optional.of(testUser));

            // When
            UserProfileResponse response = userService.getUserProfile("user123");

            // Then
            String responseString = response.toString();
            assertThat(responseString).doesNotContain("password");
            assertThat(responseString).doesNotContain("refreshToken");
            // providerId should not be exposed in the response
            assertThat(response.toString()).doesNotContain("google123");
        }

        @Test
        @DisplayName("Should handle malicious input in profile updates")
        void shouldHandleMaliciousInputInProfileUpdates() {
            // Given
            UpdateUserProfileRequest maliciousRequest = UpdateUserProfileRequest.builder()
                    .nickname("<script>alert('xss')</script>")
                    .bio("'; DROP TABLE users; --")
                    .profileImageUrl("javascript:alert('xss')")
                    .build();

            when(userRepository.findById("user123")).thenReturn(Optional.of(testUser));
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            // When
            UserProfileResponse response = userService.updateUserProfile("user123", maliciousRequest);

            // Then
            assertThat(response).isNotNull();
            // Note: In a real implementation, you might want to sanitize these inputs
            verify(userRepository).save(any(User.class));
        }
    }

    @Nested
    @DisplayName("Data Integrity Tests")
    class DataIntegrityTests {

        @Test
        @DisplayName("Should preserve user ID during updates")
        void shouldPreserveUserIdDuringUpdates() {
            // Given
            when(userRepository.findById("user123")).thenReturn(Optional.of(testUser));
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            // When
            userService.updateUserProfile("user123", updateRequest);

            // Then
            verify(userRepository).save(argThat(user -> {
                assertThat(user.getId()).isEqualTo("user123");
                return true;
            }));
        }

        @Test
        @DisplayName("Should preserve creation timestamp during updates")
        void shouldPreserveCreationTimestampDuringUpdates() {
            // Given
            LocalDateTime originalCreatedAt = testUser.getCreatedAt();
            when(userRepository.findById("user123")).thenReturn(Optional.of(testUser));
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            // When
            userService.updateUserProfile("user123", updateRequest);

            // Then
            verify(userRepository).save(argThat(user -> {
                assertThat(user.getCreatedAt()).isEqualTo(originalCreatedAt);
                return true;
            }));
        }

        @Test
        @DisplayName("Should preserve OAuth provider information during updates")
        void shouldPreserveOAuthProviderInformationDuringUpdates() {
            // Given
            when(userRepository.findById("user123")).thenReturn(Optional.of(testUser));
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            // When
            userService.updateUserProfile("user123", updateRequest);

            // Then
            verify(userRepository).save(argThat(user -> {
                assertThat(user.getProvider()).isEqualTo("google");
                assertThat(user.getProviderId()).isEqualTo("google123");
                return true;
            }));
        }
    }
}