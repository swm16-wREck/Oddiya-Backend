package com.oddiya.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oddiya.config.JwtAuthenticationFilter;
import com.oddiya.dto.UserDTO;
import com.oddiya.entity.User;
import com.oddiya.repository.UserRepository;
import com.oddiya.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private JwtService jwtService;

    private User testUser;
    private UserDTO testUserDTO;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId("test-user-id");
        testUser.setEmail("test@example.com");
        testUser.setUsername("testuser");
        testUser.setNickname("Test User");
        testUser.setBio("Test bio");
        testUser.setCreatedAt(LocalDateTime.now());
        testUser.setUpdatedAt(LocalDateTime.now());

        testUserDTO = new UserDTO();
        testUserDTO.setId("test-user-id");
        testUserDTO.setEmail("test@example.com");
        testUserDTO.setUsername("testuser");
        testUserDTO.setNickname("Test User");
    }

    @Test
    @DisplayName("Should get user profile")
    @WithMockUser(username = "test@example.com")
    void getUserProfile_ShouldReturnUserDetails() throws Exception {
        // Given
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(testUser));

        // When & Then
        mockMvc.perform(get("/api/v1/users/profile"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("test@example.com"))
            .andExpect(jsonPath("$.username").value("testuser"))
            .andExpect(jsonPath("$.nickname").value("Test User"));
    }

    @Test
    @DisplayName("Should return 401 when not authenticated")
    void getUserProfile_WithoutAuthentication_ShouldReturn401() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/users/profile"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should update user profile")
    @WithMockUser(username = "test@example.com")
    void updateUserProfile_ShouldUpdateAndReturnUser() throws Exception {
        // Given
        UserDTO updateDTO = new UserDTO();
        updateDTO.setNickname("Updated User");
        updateDTO.setBio("New bio");
        
        User updatedUser = new User();
        updatedUser.setId("test-user-id");
        updatedUser.setEmail("test@example.com");
        updatedUser.setUsername("testuser");
        updatedUser.setNickname("Updated User");
        updatedUser.setBio("New bio");
        
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(updatedUser);

        // When & Then
        mockMvc.perform(put("/api/v1/users/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDTO)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nickname").value("Updated User"))
            .andExpect(jsonPath("$.bio").value("New bio"));
    }

    @Test
    @DisplayName("Should get user by ID")
    @WithMockUser
    void getUserById_WhenUserExists_ShouldReturnUser() throws Exception {
        // Given
        when(userRepository.findById("1")).thenReturn(Optional.of(testUser));

        // When & Then
        mockMvc.perform(get("/api/v1/users/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("test-user-id"))
            .andExpect(jsonPath("$.email").value("test@example.com"));
    }

    @Test
    @DisplayName("Should return 404 when user not found")
    @WithMockUser
    void getUserById_WhenUserDoesNotExist_ShouldReturn404() throws Exception {
        // Given
        when(userRepository.findById("999")).thenReturn(Optional.empty());

        // When & Then
        mockMvc.perform(get("/api/v1/users/999"))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should delete user account")
    @WithMockUser(username = "test@example.com")
    void deleteUserAccount_ShouldDeleteUser() throws Exception {
        // Given
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(testUser));

        // When & Then
        mockMvc.perform(delete("/api/v1/users/profile"))
            .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Should update user preferences")
    @WithMockUser(username = "test@example.com")
    void updateUserPreferences_ShouldUpdatePreferences() throws Exception {
        // Given
        String preferences = "{\"theme\":\"dark\",\"language\":\"ko\"}";
        
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // When & Then
        mockMvc.perform(put("/api/v1/users/preferences")
                .contentType(MediaType.APPLICATION_JSON)
                .content(preferences))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should search users by username")
    @WithMockUser
    void searchUsers_ShouldReturnMatchingUsers() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/users/search")
                .param("q", "test"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("Should handle validation errors")
    @WithMockUser(username = "test@example.com")
    void updateUserProfile_WithInvalidData_ShouldReturn400() throws Exception {
        // Given
        UserDTO invalidDTO = new UserDTO();
        invalidDTO.setEmail("invalid-email"); // Invalid email format

        // When & Then
        mockMvc.perform(put("/api/v1/users/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidDTO)))
            .andExpect(status().isBadRequest());
    }
}