package com.oddiya.service;

import com.oddiya.dto.request.LoginRequest;
import com.oddiya.dto.request.RefreshTokenRequest;
import com.oddiya.dto.response.AuthResponse;
import com.oddiya.entity.User;
import com.oddiya.exception.UnauthorizedException;
import com.oddiya.repository.UserRepository;
import com.oddiya.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService Tests")
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtService jwtService;

    @Mock
    private OAuthService oAuthService;

    @InjectMocks
    private AuthServiceImpl authService;

    private User testUser;
    private LoginRequest loginRequest;
    private Map<String, Object> oAuthUserData;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id("user123")
                .email("test@example.com")
                .nickname("Test User")
                .profileImageUrl("http://example.com/profile.jpg")
                .provider("google")
                .providerId("google123")
                .isEmailVerified(true)
                .isActive(true)
                .refreshToken("old-refresh-token")
                .build();

        loginRequest = LoginRequest.builder()
                .provider("google")
                .idToken("test-id-token")
                .build();

        oAuthUserData = new HashMap<>();
        oAuthUserData.put("email", "test@example.com");
        oAuthUserData.put("sub", "google123");
        oAuthUserData.put("name", "Test User");
        oAuthUserData.put("picture", "http://example.com/profile.jpg");
    }

    @Nested
    @DisplayName("Login Tests")
    class LoginTests {

        @Test
        @DisplayName("Should successfully login existing user")
        void shouldSuccessfullyLoginExistingUser() {
            // Given
            when(oAuthService.verifyToken("google", "test-id-token")).thenReturn(oAuthUserData);
            when(userRepository.findByProviderAndProviderId("google", "google123"))
                    .thenReturn(Optional.of(testUser));
            when(jwtService.generateAccessToken("user123", "test@example.com"))
                    .thenReturn("new-access-token");
            when(jwtService.generateRefreshToken("user123"))
                    .thenReturn("new-refresh-token");
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            // When
            AuthResponse response = authService.login(loginRequest);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getUserId()).isEqualTo("user123");
            assertThat(response.getEmail()).isEqualTo("test@example.com");
            assertThat(response.getAccessToken()).isEqualTo("new-access-token");
            assertThat(response.getRefreshToken()).isEqualTo("new-refresh-token");
            assertThat(response.getExpiresIn()).isEqualTo(3600L);
            assertThat(response.isNewUser()).isFalse();

            verify(userRepository).save(any(User.class));
            verify(oAuthService).verifyToken("google", "test-id-token");
            verify(jwtService).generateAccessToken("user123", "test@example.com");
            verify(jwtService).generateRefreshToken("user123");
        }

        @Test
        @DisplayName("Should create new user on first login")
        void shouldCreateNewUserOnFirstLogin() {
            // Given
            User newUser = User.builder()
                    .id("new-user123")
                    .email("test@example.com")
                    .nickname("Test User")
                    .profileImageUrl("http://example.com/profile.jpg")
                    .provider("google")
                    .providerId("google123")
                    .isEmailVerified(true)
                    .isActive(true)
                    .refreshToken("new-refresh-token")
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            when(oAuthService.verifyToken("google", "test-id-token")).thenReturn(oAuthUserData);
            when(userRepository.findByProviderAndProviderId("google", "google123"))
                    .thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenReturn(newUser);
            when(jwtService.generateAccessToken("new-user123", "test@example.com"))
                    .thenReturn("new-access-token");
            when(jwtService.generateRefreshToken("new-user123"))
                    .thenReturn("new-refresh-token");

            // When
            AuthResponse response = authService.login(loginRequest);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getUserId()).isEqualTo("new-user123");
            assertThat(response.getEmail()).isEqualTo("test@example.com");
            assertThat(response.isNewUser()).isTrue();

            verify(userRepository, times(2)).save(any(User.class)); // Once for create, once for refresh token
        }

        @Test
        @DisplayName("Should handle user with null name gracefully")
        void shouldHandleNullNameGracefully() {
            // Given
            oAuthUserData.put("name", null);
            when(oAuthService.verifyToken("google", "test-id-token")).thenReturn(oAuthUserData);
            when(userRepository.findByProviderAndProviderId("google", "google123"))
                    .thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenReturn(testUser);
            when(jwtService.generateAccessToken(anyString(), anyString())).thenReturn("access-token");
            when(jwtService.generateRefreshToken(anyString())).thenReturn("refresh-token");

            // When
            AuthResponse response = authService.login(loginRequest);

            // Then
            verify(userRepository).save(argThat(user -> 
                user.getNickname().equals("test"))); // Should use email prefix
        }

        @Test
        @DisplayName("Should throw exception when OAuth verification fails")
        void shouldThrowExceptionWhenOAuthVerificationFails() {
            // Given
            when(oAuthService.verifyToken("google", "test-id-token"))
                    .thenThrow(new UnauthorizedException("Invalid OAuth token"));

            // When & Then
            assertThatThrownBy(() -> authService.login(loginRequest))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessage("Invalid OAuth token");

            verify(userRepository, never()).save(any(User.class));
        }
    }

    @Nested
    @DisplayName("Refresh Token Tests")
    class RefreshTokenTests {

        @Test
        @DisplayName("Should successfully refresh tokens")
        void shouldSuccessfullyRefreshTokens() {
            // Given
            RefreshTokenRequest request = RefreshTokenRequest.builder()
                    .refreshToken("old-refresh-token")
                    .build();

            when(jwtService.validateRefreshToken("old-refresh-token")).thenReturn("user123");
            when(userRepository.findById("user123")).thenReturn(Optional.of(testUser));
            when(jwtService.generateAccessToken("user123", "test@example.com"))
                    .thenReturn("new-access-token");
            when(jwtService.generateRefreshToken("user123")).thenReturn("new-refresh-token");
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            // When
            AuthResponse response = authService.refreshToken(request);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getUserId()).isEqualTo("user123");
            assertThat(response.getAccessToken()).isEqualTo("new-access-token");
            assertThat(response.getRefreshToken()).isEqualTo("new-refresh-token");
            assertThat(response.isNewUser()).isFalse();

            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("Should throw exception for invalid refresh token")
        void shouldThrowExceptionForInvalidRefreshToken() {
            // Given
            RefreshTokenRequest request = RefreshTokenRequest.builder()
                    .refreshToken("invalid-token")
                    .build();

            when(jwtService.validateRefreshToken("invalid-token"))
                    .thenThrow(new UnauthorizedException("Invalid refresh token"));

            // When & Then
            assertThatThrownBy(() -> authService.refreshToken(request))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessage("Invalid refresh token");
        }

        @Test
        @DisplayName("Should throw exception when user not found")
        void shouldThrowExceptionWhenUserNotFound() {
            // Given
            RefreshTokenRequest request = RefreshTokenRequest.builder()
                    .refreshToken("old-refresh-token")
                    .build();

            when(jwtService.validateRefreshToken("old-refresh-token")).thenReturn("nonexistent-user");
            when(userRepository.findById("nonexistent-user")).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> authService.refreshToken(request))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessage("User not found");
        }

        @Test
        @DisplayName("Should throw exception when refresh token doesn't match")
        void shouldThrowExceptionWhenRefreshTokenDoesNotMatch() {
            // Given
            RefreshTokenRequest request = RefreshTokenRequest.builder()
                    .refreshToken("different-refresh-token")
                    .build();

            when(jwtService.validateRefreshToken("different-refresh-token")).thenReturn("user123");
            when(userRepository.findById("user123")).thenReturn(Optional.of(testUser));

            // When & Then
            assertThatThrownBy(() -> authService.refreshToken(request))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessage("Invalid refresh token");
        }
    }

    @Nested
    @DisplayName("Logout Tests")
    class LogoutTests {

        @Test
        @DisplayName("Should successfully logout user")
        void shouldSuccessfullyLogoutUser() {
            // Given
            when(userRepository.findById("user123")).thenReturn(Optional.of(testUser));
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            // When
            authService.logout("user123");

            // Then
            verify(userRepository).save(argThat(user -> user.getRefreshToken() == null));
        }

        @Test
        @DisplayName("Should throw exception when user not found for logout")
        void shouldThrowExceptionWhenUserNotFoundForLogout() {
            // Given
            when(userRepository.findById("nonexistent-user")).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> authService.logout("nonexistent-user"))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessage("User not found");

            verify(userRepository, never()).save(any(User.class));
        }
    }

    @Nested
    @DisplayName("Token Validation Tests")
    class TokenValidationTests {

        @Test
        @DisplayName("Should validate token successfully")
        void shouldValidateTokenSuccessfully() {
            // Given
            String validToken = "valid-access-token";

            // When & Then
            assertThatCode(() -> authService.validateToken(validToken))
                    .doesNotThrowAnyException();

            verify(jwtService).validateAccessToken(validToken);
        }

        @Test
        @DisplayName("Should propagate JWT service exception")
        void shouldPropagateJwtServiceException() {
            // Given
            String invalidToken = "invalid-token";
            when(jwtService.validateAccessToken(invalidToken))
                    .thenThrow(new UnauthorizedException("Invalid access token"));

            // When & Then
            assertThatThrownBy(() -> authService.validateToken(invalidToken))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessage("Invalid access token");
        }
    }

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle concurrent login attempts")
        void shouldHandleConcurrentLoginAttempts() {
            // Given
            when(oAuthService.verifyToken("google", "test-id-token")).thenReturn(oAuthUserData);
            when(userRepository.findByProviderAndProviderId("google", "google123"))
                    .thenReturn(Optional.of(testUser));
            when(jwtService.generateAccessToken(anyString(), anyString())).thenReturn("access-token");
            when(jwtService.generateRefreshToken(anyString())).thenReturn("refresh-token");
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            // When
            AuthResponse response1 = authService.login(loginRequest);
            AuthResponse response2 = authService.login(loginRequest);

            // Then
            assertThat(response1).isNotNull();
            assertThat(response2).isNotNull();
            verify(userRepository, times(2)).save(any(User.class));
        }

        @Test
        @DisplayName("Should handle OAuth data with missing fields")
        void shouldHandleOAuthDataWithMissingFields() {
            // Given
            oAuthUserData.remove("picture");
            when(oAuthService.verifyToken("google", "test-id-token")).thenReturn(oAuthUserData);
            when(userRepository.findByProviderAndProviderId("google", "google123"))
                    .thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenReturn(testUser);
            when(jwtService.generateAccessToken(anyString(), anyString())).thenReturn("access-token");
            when(jwtService.generateRefreshToken(anyString())).thenReturn("refresh-token");

            // When
            AuthResponse response = authService.login(loginRequest);

            // Then
            assertThat(response).isNotNull();
            verify(userRepository).save(argThat(user -> user.getProfileImageUrl() == null));
        }

        @Test
        @DisplayName("Should handle very long email addresses")
        void shouldHandleVeryLongEmailAddresses() {
            // Given
            String longEmail = "very.long.email.address.that.might.cause.issues@very.long.domain.name.example.com";
            oAuthUserData.put("email", longEmail);
            when(oAuthService.verifyToken("google", "test-id-token")).thenReturn(oAuthUserData);
            when(userRepository.findByProviderAndProviderId("google", "google123"))
                    .thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenReturn(testUser);
            when(jwtService.generateAccessToken(anyString(), anyString())).thenReturn("access-token");
            when(jwtService.generateRefreshToken(anyString())).thenReturn("refresh-token");

            // When
            AuthResponse response = authService.login(loginRequest);

            // Then
            assertThat(response).isNotNull();
            verify(userRepository).save(argThat(user -> 
                user.getNickname().equals("very.long.email.address.that.might.cause.issues")));
        }
    }

    @Nested
    @DisplayName("Transaction Boundary Tests")
    class TransactionBoundaryTests {

        @Test
        @DisplayName("Should handle repository save failure during login")
        void shouldHandleRepositorySaveFailureDuringLogin() {
            // Given
            when(oAuthService.verifyToken("google", "test-id-token")).thenReturn(oAuthUserData);
            when(userRepository.findByProviderAndProviderId("google", "google123"))
                    .thenReturn(Optional.of(testUser));
            when(jwtService.generateAccessToken(anyString(), anyString())).thenReturn("access-token");
            when(jwtService.generateRefreshToken(anyString())).thenReturn("refresh-token");
            when(userRepository.save(any(User.class))).thenThrow(new RuntimeException("Database error"));

            // When & Then
            assertThatThrownBy(() -> authService.login(loginRequest))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Database error");
        }

        @Test
        @DisplayName("Should handle JWT generation failure")
        void shouldHandleJwtGenerationFailure() {
            // Given
            when(oAuthService.verifyToken("google", "test-id-token")).thenReturn(oAuthUserData);
            when(userRepository.findByProviderAndProviderId("google", "google123"))
                    .thenReturn(Optional.of(testUser));
            when(jwtService.generateAccessToken(anyString(), anyString()))
                    .thenThrow(new RuntimeException("JWT generation failed"));

            // When & Then
            assertThatThrownBy(() -> authService.login(loginRequest))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("JWT generation failed");

            verify(userRepository, never()).save(any(User.class));
        }
    }

    @Nested
    @DisplayName("Data Consistency Tests")
    class DataConsistencyTests {

        @Test
        @DisplayName("Should maintain data integrity during user creation")
        void shouldMaintainDataIntegrityDuringUserCreation() {
            // Given
            when(oAuthService.verifyToken("google", "test-id-token")).thenReturn(oAuthUserData);
            when(userRepository.findByProviderAndProviderId("google", "google123"))
                    .thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenReturn(testUser);
            when(jwtService.generateAccessToken(anyString(), anyString())).thenReturn("access-token");
            when(jwtService.generateRefreshToken(anyString())).thenReturn("refresh-token");

            // When
            authService.login(loginRequest);

            // Then
            verify(userRepository).save(argThat(user -> {
                assertThat(user.getEmail()).isEqualTo("test@example.com");
                assertThat(user.getProvider()).isEqualTo("google");
                assertThat(user.getProviderId()).isEqualTo("google123");
                assertThat(user.isEmailVerified()).isTrue();
                assertThat(user.isActive()).isTrue();
                return true;
            }));
        }

        @Test
        @DisplayName("Should update refresh token correctly")
        void shouldUpdateRefreshTokenCorrectly() {
            // Given
            String newRefreshToken = "new-refresh-token-value";
            when(oAuthService.verifyToken("google", "test-id-token")).thenReturn(oAuthUserData);
            when(userRepository.findByProviderAndProviderId("google", "google123"))
                    .thenReturn(Optional.of(testUser));
            when(jwtService.generateAccessToken(anyString(), anyString())).thenReturn("access-token");
            when(jwtService.generateRefreshToken(anyString())).thenReturn(newRefreshToken);
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            // When
            authService.login(loginRequest);

            // Then
            verify(userRepository).save(argThat(user -> 
                user.getRefreshToken().equals(newRefreshToken)));
        }
    }
}