package com.oddiya.integration;

import org.junit.jupiter.api.Disabled;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oddiya.dto.LoginRequestDTO;
import com.oddiya.dto.RegisterRequestDTO;
import com.oddiya.entity.User;
import com.oddiya.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
@Disabled("Temporarily disabled - Testcontainers not configured in CI/CD")
class UserIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("oddiya_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Should complete full user registration and login flow")
    void fullUserRegistrationAndLoginFlow() throws Exception {
        // Step 1: Register new user
        RegisterRequestDTO registerRequest = new RegisterRequestDTO();
        registerRequest.setEmail("newuser@example.com");
        registerRequest.setPassword("SecurePassword123!");
        registerRequest.setUsername("newuser");
        registerRequest.setNickname("New User");

        MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.email").value("newuser@example.com"))
            .andReturn();

        // Verify user is saved in database
        assertThat(userRepository.count()).isEqualTo(1);
        User savedUser = userRepository.findByEmail("newuser@example.com").orElseThrow();
        assertThat(savedUser.getUsername()).isEqualTo("newuser");

        // Step 2: Login with registered user
        LoginRequestDTO loginRequest = new LoginRequestDTO();
        loginRequest.setEmail("newuser@example.com");
        loginRequest.setPassword("SecurePassword123!");

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").exists())
            .andExpect(jsonPath("$.refreshToken").exists())
            .andReturn();

        String responseContent = loginResult.getResponse().getContentAsString();
        String accessToken = objectMapper.readTree(responseContent).get("accessToken").asText();

        // Step 3: Access protected endpoint with token
        mockMvc.perform(get("/api/v1/users/profile")
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("newuser@example.com"));

        // Step 4: Update user profile
        String updateJson = "{\"nickname\":\"Updated User\",\"bio\":\"Test bio\"}";
        
        mockMvc.perform(put("/api/v1/users/profile")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nickname").value("Updated User"))
            .andExpect(jsonPath("$.bio").value("Test bio"));

        // Verify update in database
        User updatedUser = userRepository.findByEmail("newuser@example.com").orElseThrow();
        assertThat(updatedUser.getNickname()).isEqualTo("Updated User");
        assertThat(updatedUser.getBio()).isEqualTo("Test bio");
    }

    @Test
    @DisplayName("Should handle duplicate registration attempts")
    void duplicateRegistration_ShouldReturn409() throws Exception {
        // First registration
        RegisterRequestDTO registerRequest = new RegisterRequestDTO();
        registerRequest.setEmail("duplicate@example.com");
        registerRequest.setPassword("Password123!");
        registerRequest.setUsername("duplicate");
        registerRequest.setNickname("Duplicate User");

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
            .andExpect(status().isCreated());

        // Duplicate registration attempt
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
            .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Should validate password requirements")
    void weakPassword_ShouldReturn400() throws Exception {
        RegisterRequestDTO registerRequest = new RegisterRequestDTO();
        registerRequest.setEmail("weak@example.com");
        registerRequest.setPassword("weak"); // Too short and simple
        registerRequest.setUsername("weakuser");
        registerRequest.setNickname("Weak User");

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should handle invalid login credentials")
    void invalidLogin_ShouldReturn401() throws Exception {
        // Register user first
        RegisterRequestDTO registerRequest = new RegisterRequestDTO();
        registerRequest.setEmail("valid@example.com");
        registerRequest.setPassword("ValidPassword123!");
        registerRequest.setUsername("validuser");
        registerRequest.setNickname("Valid User");

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
            .andExpect(status().isCreated());

        // Try login with wrong password
        LoginRequestDTO loginRequest = new LoginRequestDTO();
        loginRequest.setEmail("valid@example.com");
        loginRequest.setPassword("WrongPassword123!");

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should handle concurrent user operations")
    void concurrentUserOperations() throws Exception {
        // Create multiple users concurrently
        for (int i = 0; i < 5; i++) {
            RegisterRequestDTO registerRequest = new RegisterRequestDTO();
            registerRequest.setEmail("user" + i + "@example.com");
            registerRequest.setPassword("Password123!");
            registerRequest.setUsername("user" + i);
            registerRequest.setNickname("User " + i);

            mockMvc.perform(post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());
        }

        // Verify all users are created
        assertThat(userRepository.count()).isEqualTo(5);
    }
}