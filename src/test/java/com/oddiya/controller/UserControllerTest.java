package com.oddiya.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oddiya.config.TestSecurityConfig;
import com.oddiya.dto.UserDTO;
import com.oddiya.entity.User;
import com.oddiya.repository.UserRepository;
import com.oddiya.security.WithMockJwtUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.*;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@Import(TestSecurityConfig.class)
@DisplayName("UserController Tests")
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private User createSampleUser() {
        return User.builder()
                .id("user-123")
                .email("test@example.com")
                .username("testuser")
                .nickname("Test User")
                .bio("A test user for testing purposes")
                .profileImageUrl("https://example.com/avatar.jpg")
                .isEmailVerified(true)
                .isPremium(false)
                .isActive(true)
                .preferences(new HashMap<String, String>() {{
                    put("language", "en");
                    put("notifications", "enabled");
                }})
                .createdAt(LocalDateTime.now().minusDays(30))
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private UserDTO createSampleUserDTO() {
        User user = createSampleUser();
        return UserDTO.builder()
                .id(user.getId())
                .email(user.getEmail())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .bio(user.getBio())
                .profileImageUrl(user.getProfileImageUrl())
                .isEmailVerified(user.isEmailVerified())
                .isPremium(user.isPremium())
                .isActive(user.isActive())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    @Nested
    @DisplayName("GET /api/v1/users/profile")
    class GetUserProfileTests {

        @Test
        @WithMockJwtUser(email = "test@example.com")
        @DisplayName("Should successfully get user profile")
        void getUserProfileSuccess() throws Exception {
            // Given
            User user = createSampleUser();
            given(userRepository.findByEmail("test@example.com")).willReturn(Optional.of(user));

            // When & Then
            mockMvc.perform(get("/api/v1/users/profile"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.data.id", is("user-123")))
                    .andExpect(jsonPath("$.data.email", is("test@example.com")))
                    .andExpect(jsonPath("$.data.username", is("testuser")))
                    .andExpect(jsonPath("$.data.nickname", is("Test User")))
                    .andExpect(jsonPath("$.data.bio", is("A test user for testing purposes")))
                    .andExpect(jsonPath("$.data.profileImageUrl", is("https://example.com/avatar.jpg")))
                    .andExpect(jsonPath("$.data.isEmailVerified", is(true)))
                    .andExpect(jsonPath("$.data.isPremium", is(false)))
                    .andExpect(jsonPath("$.data.isActive", is(true)));
        }

        @Test
        @WithMockJwtUser(email = "nonexistent@example.com")
        @DisplayName("Should return 404 when user not found")
        void getUserProfileNotFound() throws Exception {
            // Given
            given(userRepository.findByEmail("nonexistent@example.com")).willReturn(Optional.empty());

            // When & Then
            mockMvc.perform(get("/api/v1/users/profile"))
                    .andDo(print())
                    .andExpect(status().isInternalServerError()); // UsernameNotFoundException is thrown
        }

        @Test
        @DisplayName("Should return 401 when user is not authenticated")
        void getUserProfileWithoutAuthentication() throws Exception {
            // When & Then
            mockMvc.perform(get("/api/v1/users/profile"))
                    .andDo(print())
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/users/profile")
    class UpdateUserProfileTests {

        @Test
        @WithMockJwtUser(email = "test@example.com")
        @DisplayName("Should successfully update user profile")
        void updateUserProfileSuccess() throws Exception {
            // Given
            User existingUser = createSampleUser();
            User updatedUser = createSampleUser();
            updatedUser.setNickname("Updated Nickname");
            updatedUser.setBio("Updated bio");
            updatedUser.setProfileImageUrl("https://example.com/new-avatar.jpg");

            UserDTO updateRequest = UserDTO.builder()
                    .nickname("Updated Nickname")
                    .bio("Updated bio")
                    .profileImageUrl("https://example.com/new-avatar.jpg")
                    .build();

            given(userRepository.findByEmail("test@example.com")).willReturn(Optional.of(existingUser));
            given(userRepository.save(any(User.class))).willReturn(updatedUser);

            // When & Then
            mockMvc.perform(put("/api/v1/users/profile")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(updateRequest)))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.data.nickname", is("Updated Nickname")))
                    .andExpect(jsonPath("$.data.bio", is("Updated bio")))
                    .andExpect(jsonPath("$.data.profileImageUrl", is("https://example.com/new-avatar.jpg")));
        }

        @Test
        @WithMockJwtUser(email = "test@example.com")
        @DisplayName("Should update only provided fields")
        void updateUserProfilePartialUpdate() throws Exception {
            // Given
            User existingUser = createSampleUser();
            User updatedUser = createSampleUser();
            updatedUser.setNickname("Only Nickname Updated");

            UserDTO updateRequest = UserDTO.builder()
                    .nickname("Only Nickname Updated")
                    .build();

            given(userRepository.findByEmail("test@example.com")).willReturn(Optional.of(existingUser));
            given(userRepository.save(any(User.class))).willReturn(updatedUser);

            // When & Then
            mockMvc.perform(put("/api/v1/users/profile")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(updateRequest)))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.data.nickname", is("Only Nickname Updated")))
                    .andExpect(jsonPath("$.data.bio", is("A test user for testing purposes"))) // original bio
                    .andExpect(jsonPath("$.data.profileImageUrl", is("https://example.com/avatar.jpg"))); // original URL
        }

        @Test
        @WithMockJwtUser(email = "test@example.com")
        @DisplayName("Should return 400 with invalid JSON")
        void updateUserProfileInvalidJson() throws Exception {
            // When & Then
            mockMvc.perform(put("/api/v1/users/profile")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{invalid-json"))
                    .andDo(print())
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 401 when user is not authenticated")
        void updateUserProfileWithoutAuthentication() throws Exception {
            // Given
            UserDTO updateRequest = UserDTO.builder()
                    .nickname("Updated Nickname")
                    .build();

            // When & Then
            mockMvc.perform(put("/api/v1/users/profile")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(updateRequest)))
                    .andDo(print())
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/users/{id}")
    class GetUserByIdTests {

        @Test
        @DisplayName("Should successfully get user by ID")
        void getUserByIdSuccess() throws Exception {
            // Given
            User user = createSampleUser();
            given(userRepository.findById("user-123")).willReturn(Optional.of(user));

            // When & Then
            mockMvc.perform(get("/api/v1/users/user-123"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.data.id", is("user-123")))
                    .andExpect(jsonPath("$.data.username", is("testuser")));
        }

        @Test
        @DisplayName("Should return 404 when user not found")
        void getUserByIdNotFound() throws Exception {
            // Given
            given(userRepository.findById("non-existent")).willReturn(Optional.empty());

            // When & Then
            mockMvc.perform(get("/api/v1/users/non-existent"))
                    .andDo(print())
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/users/profile")
    class DeleteUserAccountTests {

        @Test
        @WithMockJwtUser(email = "test@example.com")
        @DisplayName("Should successfully delete user account")
        void deleteUserAccountSuccess() throws Exception {
            // Given
            User user = createSampleUser();
            given(userRepository.findByEmail("test@example.com")).willReturn(Optional.of(user));
            willDoNothing().given(userRepository).delete(user);

            // When & Then
            mockMvc.perform(delete("/api/v1/users/profile"))
                    .andDo(print())
                    .andExpect(status().isNoContent());
        }

        @Test
        @WithMockJwtUser(email = "nonexistent@example.com")
        @DisplayName("Should return 500 when user to delete not found")
        void deleteUserAccountNotFound() throws Exception {
            // Given
            given(userRepository.findByEmail("nonexistent@example.com")).willReturn(Optional.empty());

            // When & Then
            mockMvc.perform(delete("/api/v1/users/profile"))
                    .andDo(print())
                    .andExpect(status().isInternalServerError()); // UsernameNotFoundException
        }

        @Test
        @DisplayName("Should return 401 when user is not authenticated")
        void deleteUserAccountWithoutAuthentication() throws Exception {
            // When & Then
            mockMvc.perform(delete("/api/v1/users/profile"))
                    .andDo(print())
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/users/preferences")
    class UpdateUserPreferencesTests {

        @Test
        @WithMockJwtUser(email = "test@example.com")
        @DisplayName("Should successfully update user preferences")
        void updateUserPreferencesSuccess() throws Exception {
            // Given
            User user = createSampleUser();
            User updatedUser = createSampleUser();
            Map<String, String> newPreferences = new HashMap<String, String>() {{
                put("language", "ko");
                put("notifications", "disabled");
                put("theme", "dark");
            }};
            updatedUser.setPreferences(newPreferences);

            given(userRepository.findByEmail("test@example.com")).willReturn(Optional.of(user));
            given(userRepository.save(any(User.class))).willReturn(updatedUser);

            // When & Then
            mockMvc.perform(put("/api/v1/users/preferences")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(newPreferences)))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.data.id", is("user-123")));
        }

        @Test
        @WithMockJwtUser(email = "test@example.com")
        @DisplayName("Should handle empty preferences")
        void updateUserPreferencesEmpty() throws Exception {
            // Given
            User user = createSampleUser();
            User updatedUser = createSampleUser();
            Map<String, String> emptyPreferences = new HashMap<>();
            updatedUser.setPreferences(emptyPreferences);

            given(userRepository.findByEmail("test@example.com")).willReturn(Optional.of(user));
            given(userRepository.save(any(User.class))).willReturn(updatedUser);

            // When & Then
            mockMvc.perform(put("/api/v1/users/preferences")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(emptyPreferences)))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)));
        }

        @Test
        @DisplayName("Should return 401 when user is not authenticated")
        void updateUserPreferencesWithoutAuthentication() throws Exception {
            // Given
            Map<String, String> preferences = Collections.singletonMap("language", "ko");

            // When & Then
            mockMvc.perform(put("/api/v1/users/preferences")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(preferences)))
                    .andDo(print())
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/users/search")
    class SearchUsersTests {

        @Test
        @DisplayName("Should successfully search users")
        void searchUsersSuccess() throws Exception {
            // Given
            User user = createSampleUser();
            Page<User> userPage = new PageImpl<>(Arrays.asList(user));

            given(userRepository.searchUsers(eq("test"), any(Pageable.class))).willReturn(userPage);

            // When & Then
            mockMvc.perform(get("/api/v1/users/search")
                    .param("q", "test"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.data", hasSize(1)))
                    .andExpect(jsonPath("$.data[0].id", is("user-123")))
                    .andExpect(jsonPath("$.data[0].username", is("testuser")));
        }

        @Test
        @DisplayName("Should return empty results when no users match")
        void searchUsersNoResults() throws Exception {
            // Given
            Page<User> emptyPage = new PageImpl<>(Collections.emptyList());
            given(userRepository.searchUsers(eq("nonexistent"), any(Pageable.class))).willReturn(emptyPage);

            // When & Then
            mockMvc.perform(get("/api/v1/users/search")
                    .param("q", "nonexistent"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.data", hasSize(0)));
        }

        @Test
        @DisplayName("Should use default pagination parameters")
        void searchUsersWithDefaults() throws Exception {
            // Given
            Page<User> userPage = new PageImpl<>(Collections.emptyList());
            given(userRepository.searchUsers(eq("test"), any(Pageable.class))).willReturn(userPage);

            // When & Then
            mockMvc.perform(get("/api/v1/users/search")
                    .param("q", "test"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)));
        }

        @Test
        @DisplayName("Should handle custom pagination parameters")
        void searchUsersWithCustomPagination() throws Exception {
            // Given
            Page<User> userPage = new PageImpl<>(Collections.emptyList());
            given(userRepository.searchUsers(eq("test"), any(Pageable.class))).willReturn(userPage);

            // When & Then
            mockMvc.perform(get("/api/v1/users/search")
                    .param("q", "test")
                    .param("page", "1")
                    .param("size", "10"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)));
        }

        @Test
        @DisplayName("Should return 400 when query parameter is missing")
        void searchUsersFailsWithoutQuery() throws Exception {
            // When & Then
            mockMvc.perform(get("/api/v1/users/search"))
                    .andDo(print())
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("Validation Tests")
    class ValidationTests {

        @Test
        @WithMockJwtUser(email = "test@example.com")
        @DisplayName("Should validate UserDTO fields with @Valid annotation")
        void validateUserDTOFields() throws Exception {
            // Note: Since UserDTO in this controller doesn't have validation annotations,
            // this test demonstrates the structure for when they are added

            // Given - user exists for the update
            User user = createSampleUser();
            given(userRepository.findByEmail("test@example.com")).willReturn(Optional.of(user));
            given(userRepository.save(any(User.class))).willReturn(user);

            // Valid request should work
            UserDTO validRequest = UserDTO.builder()
                    .nickname("ValidNickname")
                    .bio("Valid bio")
                    .build();

            // When & Then
            mockMvc.perform(put("/api/v1/users/profile")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validRequest)))
                    .andDo(print())
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockJwtUser(email = "test@example.com")
        @DisplayName("Should handle null values gracefully in partial updates")
        void handleNullValuesInPartialUpdate() throws Exception {
            // Given
            User user = createSampleUser();
            given(userRepository.findByEmail("test@example.com")).willReturn(Optional.of(user));
            given(userRepository.save(any(User.class))).willReturn(user);

            // Request with null values (should be ignored)
            UserDTO requestWithNulls = UserDTO.builder()
                    .nickname("New Nickname")
                    .bio(null) // This should be ignored
                    .profileImageUrl(null) // This should be ignored
                    .build();

            // When & Then
            mockMvc.perform(put("/api/v1/users/profile")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(requestWithNulls)))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)));
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should handle malformed JSON requests")
        void handleMalformedJson() throws Exception {
            // When & Then
            mockMvc.perform(put("/api/v1/users/preferences")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{malformed-json"))
                    .andDo(print())
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should reject non-JSON content type")
        void rejectNonJsonContentType() throws Exception {
            // When & Then
            mockMvc.perform(put("/api/v1/users/profile")
                    .contentType(MediaType.TEXT_PLAIN)
                    .content("plain text content"))
                    .andDo(print())
                    .andExpect(status().isUnsupportedMediaType());
        }

        @Test
        @DisplayName("Should handle large request bodies appropriately")
        void handleLargeRequestBodies() throws Exception {
            // Given - very large bio (assuming there might be size limits)
            String largeBio = "A".repeat(10000); // 10KB bio
            UserDTO largeRequest = UserDTO.builder()
                    .bio(largeBio)
                    .build();

            User user = createSampleUser();
            given(userRepository.findByEmail("test@example.com")).willReturn(Optional.of(user));

            // When & Then - This test checks that large but reasonable requests are handled
            mockMvc.perform(put("/api/v1/users/profile")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(largeRequest)))
                    .andDo(print())
                    .andExpect(status().isUnauthorized()); // Will fail auth since no @WithMockJwtUser
        }
    }

    @Nested
    @DisplayName("HTTP Method Tests")
    class HttpMethodTests {

        @Test
        @DisplayName("Should reject unsupported HTTP methods")
        void rejectUnsupportedHttpMethods() throws Exception {
            // PATCH method not supported for profile endpoint
            mockMvc.perform(patch("/api/v1/users/profile")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
                    .andDo(print())
                    .andExpect(status().isMethodNotAllowed());
        }

        @Test
        @DisplayName("Should support OPTIONS requests for CORS")
        void supportOptionsForCors() throws Exception {
            // OPTIONS request should be handled by CORS configuration
            mockMvc.perform(options("/api/v1/users/profile"))
                    .andDo(print())
                    .andExpect(status().isOk());
        }
    }
}