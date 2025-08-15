package com.oddiya.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oddiya.config.TestSecurityConfig;
import com.oddiya.dto.request.LoginRequest;
import com.oddiya.dto.request.RefreshTokenRequest;
import com.oddiya.dto.response.AuthResponse;
import com.oddiya.exception.BadRequestException;
import com.oddiya.exception.UnauthorizedException;
import com.oddiya.security.WithMockJwtUser;
import com.oddiya.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import(TestSecurityConfig.class)
@DisplayName("AuthController Tests")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @Autowired
    private ObjectMapper objectMapper;

    @Nested
    @DisplayName("POST /api/v1/auth/login")
    class LoginTests {

        @Test
        @DisplayName("Should successfully login with valid credentials")
        void loginSuccess() throws Exception {
            // Given
            LoginRequest request = LoginRequest.builder()
                    .provider("google")
                    .idToken("valid-id-token")
                    .deviceId("device-123")
                    .deviceType("iOS")
                    .build();

            AuthResponse authResponse = AuthResponse.builder()
                    .accessToken("access-token")
                    .refreshToken("refresh-token")
                    .tokenType("Bearer")
                    .expiresIn(3600L)
                    .userId("user-123")
                    .build();

            given(authService.login(any(LoginRequest.class))).willReturn(authResponse);

            // When & Then
            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.data.accessToken", is("access-token")))
                    .andExpect(jsonPath("$.data.refreshToken", is("refresh-token")))
                    .andExpect(jsonPath("$.data.tokenType", is("Bearer")))
                    .andExpect(jsonPath("$.data.expiresIn", is(3600)))
                    .andExpect(jsonPath("$.data.userId", is("user-123")))
                    .andExpect(jsonPath("$.error", is(nullValue())));
        }

        @Test
        @DisplayName("Should return 400 when provider is missing")
        void loginFailsWithMissingProvider() throws Exception {
            // Given
            LoginRequest request = LoginRequest.builder()
                    .idToken("valid-id-token")
                    .build();

            // When & Then
            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andDo(print())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success", is(false)))
                    .andExpect(jsonPath("$.error.code", is("VALIDATION_ERROR")))
                    .andExpect(jsonPath("$.error.message", is("Validation failed")))
                    .andExpect(jsonPath("$.error.details.provider", containsString("Provider is required")));
        }

        @Test
        @DisplayName("Should return 400 when idToken is missing")
        void loginFailsWithMissingIdToken() throws Exception {
            // Given
            LoginRequest request = LoginRequest.builder()
                    .provider("google")
                    .build();

            // When & Then
            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andDo(print())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success", is(false)))
                    .andExpect(jsonPath("$.error.code", is("VALIDATION_ERROR")))
                    .andExpect(jsonPath("$.error.details.idToken", containsString("ID token is required")));
        }

        @Test
        @DisplayName("Should return 400 when request body is malformed")
        void loginFailsWithMalformedRequest() throws Exception {
            // When & Then
            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{invalid-json"))
                    .andDo(print())
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 401 when authentication fails")
        void loginFailsWithInvalidCredentials() throws Exception {
            // Given
            LoginRequest request = LoginRequest.builder()
                    .provider("google")
                    .idToken("invalid-token")
                    .build();

            given(authService.login(any(LoginRequest.class)))
                    .willThrow(new UnauthorizedException("Invalid credentials"));

            // When & Then
            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andDo(print())
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success", is(false)))
                    .andExpect(jsonPath("$.error.code", is("UNAUTHORIZED")))
                    .andExpect(jsonPath("$.error.message", is("Invalid credentials")));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/refresh")
    class RefreshTokenTests {

        @Test
        @DisplayName("Should successfully refresh token with valid refresh token")
        void refreshTokenSuccess() throws Exception {
            // Given
            RefreshTokenRequest request = RefreshTokenRequest.builder()
                    .refreshToken("valid-refresh-token")
                    .build();

            AuthResponse authResponse = AuthResponse.builder()
                    .accessToken("new-access-token")
                    .refreshToken("new-refresh-token")
                    .tokenType("Bearer")
                    .expiresIn(3600L)
                    .userId("user-123")
                    .build();

            given(authService.refreshToken(any(RefreshTokenRequest.class))).willReturn(authResponse);

            // When & Then
            mockMvc.perform(post("/api/v1/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.data.accessToken", is("new-access-token")))
                    .andExpect(jsonPath("$.data.refreshToken", is("new-refresh-token")))
                    .andExpect(jsonPath("$.data.tokenType", is("Bearer")));
        }

        @Test
        @DisplayName("Should return 401 when refresh token is invalid")
        void refreshTokenFailsWithInvalidToken() throws Exception {
            // Given
            RefreshTokenRequest request = RefreshTokenRequest.builder()
                    .refreshToken("invalid-refresh-token")
                    .build();

            given(authService.refreshToken(any(RefreshTokenRequest.class)))
                    .willThrow(new UnauthorizedException("Invalid refresh token"));

            // When & Then
            mockMvc.perform(post("/api/v1/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andDo(print())
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success", is(false)))
                    .andExpect(jsonPath("$.error.code", is("UNAUTHORIZED")))
                    .andExpect(jsonPath("$.error.message", is("Invalid refresh token")));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/logout")
    class LogoutTests {

        @Test
        @WithMockJwtUser("user-123")
        @DisplayName("Should successfully logout authenticated user")
        void logoutSuccess() throws Exception {
            // When & Then
            mockMvc.perform(post("/api/v1/auth/logout"))
                    .andDo(print())
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("Should return 401 when user is not authenticated")
        void logoutFailsWithoutAuthentication() throws Exception {
            // When & Then
            mockMvc.perform(post("/api/v1/auth/logout"))
                    .andDo(print())
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/auth/validate")
    class ValidateTokenTests {

        @Test
        @DisplayName("Should return true when token is valid")
        void validateTokenSuccess() throws Exception {
            // Given
            willDoNothing().given(authService).validateToken(anyString());

            // When & Then
            mockMvc.perform(get("/api/v1/auth/validate")
                    .header("Authorization", "Bearer valid-token"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.data", is(true)));
        }

        @Test
        @DisplayName("Should return false when token is invalid")
        void validateTokenFailsWithInvalidToken() throws Exception {
            // Given
            willThrow(new UnauthorizedException("Invalid token"))
                    .given(authService).validateToken(anyString());

            // When & Then
            mockMvc.perform(get("/api/v1/auth/validate")
                    .header("Authorization", "Bearer invalid-token"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.data", is(false)));
        }

        @Test
        @DisplayName("Should return 400 when Authorization header is missing")
        void validateTokenFailsWithoutAuthHeader() throws Exception {
            // When & Then
            mockMvc.perform(get("/api/v1/auth/validate"))
                    .andDo(print())
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("Content Type and Header Tests")
    class ContentTypeAndHeaderTests {

        @Test
        @DisplayName("Should accept only application/json content type")
        void shouldRejectNonJsonContentType() throws Exception {
            // Given
            LoginRequest request = LoginRequest.builder()
                    .provider("google")
                    .idToken("valid-token")
                    .build();

            // When & Then
            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.TEXT_PLAIN)
                    .content(objectMapper.writeValueAsString(request)))
                    .andDo(print())
                    .andExpect(status().isUnsupportedMediaType());
        }

        @Test
        @DisplayName("Should return application/json content type in responses")
        void shouldReturnJsonContentType() throws Exception {
            // Given
            LoginRequest request = LoginRequest.builder()
                    .provider("google")
                    .idToken("valid-token")
                    .build();

            AuthResponse authResponse = AuthResponse.builder()
                    .accessToken("access-token")
                    .refreshToken("refresh-token")
                    .tokenType("Bearer")
                    .expiresIn(3600L)
                    .userId("user-123")
                    .build();

            given(authService.login(any(LoginRequest.class))).willReturn(authResponse);

            // When & Then
            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON));
        }
    }
}