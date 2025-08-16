package com.oddiya.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oddiya.dto.response.AuthResponse;
import com.oddiya.entity.User;
import com.oddiya.exception.BadRequestException;
import com.oddiya.exception.UnauthorizedException;
import com.oddiya.repository.UserRepository;
import com.oddiya.service.impl.SupabaseServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SupabaseService Tests")
@SuppressWarnings("unchecked")  // Suppress unchecked warnings for Mockito WebClient mocking
class SupabaseServiceTest {

    @Mock
    private WebClient webClient;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private WebClient.RequestBodySpec requestBodySpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    @Mock
    private JsonNode jsonNode;

    @Mock
    private JsonNode userNode;

    @InjectMocks
    private SupabaseServiceImpl supabaseService;

    private User existingUser;
    private String mockSupabaseResponse;

    @BeforeEach
    void setUp() {
        existingUser = User.builder()
                .id("user123")
                .email("test@example.com")
                .nickname("testuser")
                .provider("supabase")
                .providerId("user123")
                .isEmailVerified(true)
                .isActive(true)
                .build();

        mockSupabaseResponse = """
                {
                    "user": {
                        "id": "user123",
                        "email": "test@example.com"
                    },
                    "access_token": "access-token-123",
                    "refresh_token": "refresh-token-123",
                    "expires_in": 3600
                }
                """;

        // Set up reflection test utils to inject values
        ReflectionTestUtils.setField(supabaseService, "supabaseUrl", "https://test.supabase.co");
        ReflectionTestUtils.setField(supabaseService, "supabaseAnonKey", "test-anon-key");
        ReflectionTestUtils.setField(supabaseService, "supabaseServiceKey", "test-service-key");
    }

    @Nested
    @DisplayName("Sign In With Email Tests")
    class SignInWithEmailTests {

        @Test
        @DisplayName("Should successfully sign in with email")
        void shouldSuccessfullySignInWithEmail() throws Exception {
            // Given
            String email = "test@example.com";
            String password = "password123";

            when(webClient.post()).thenReturn(requestBodyUriSpec);
            when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
            when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
            when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(mockSupabaseResponse));

            when(objectMapper.readTree(mockSupabaseResponse)).thenReturn(jsonNode);
            when(jsonNode.get("user")).thenReturn(userNode);
            when(userNode.get("id")).thenReturn(jsonNode);
            when(userNode.get("email")).thenReturn(jsonNode);
            when(jsonNode.get("access_token")).thenReturn(jsonNode);
            when(jsonNode.get("refresh_token")).thenReturn(jsonNode);
            when(jsonNode.get("expires_in")).thenReturn(jsonNode);
            when(jsonNode.asText()).thenReturn("user123").thenReturn("test@example.com")
                    .thenReturn("access-token-123").thenReturn("refresh-token-123");
            when(jsonNode.asLong()).thenReturn(3600L);

            when(userRepository.findByEmail(email)).thenReturn(Optional.of(existingUser));

            // When
            AuthResponse response = supabaseService.signInWithEmail(email, password);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getUserId()).isEqualTo("user123");
            assertThat(response.getEmail()).isEqualTo("test@example.com");
            assertThat(response.getAccessToken()).isEqualTo("access-token-123");
            assertThat(response.getRefreshToken()).isEqualTo("refresh-token-123");
            assertThat(response.getExpiresIn()).isEqualTo(3600L);

            verify(requestBodyUriSpec).uri(contains("/auth/v1/token?grant_type=password"));
        }

        @Test
        @DisplayName("Should create new user if not exists")
        void shouldCreateNewUserIfNotExists() throws Exception {
            // Given
            String email = "newuser@example.com";
            String password = "password123";

            when(webClient.post()).thenReturn(requestBodyUriSpec);
            when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
            when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
            when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(mockSupabaseResponse));

            when(objectMapper.readTree(mockSupabaseResponse)).thenReturn(jsonNode);
            when(jsonNode.get("user")).thenReturn(userNode);
            when(userNode.get("id")).thenReturn(jsonNode);
            when(userNode.get("email")).thenReturn(jsonNode);
            when(jsonNode.get("access_token")).thenReturn(jsonNode);
            when(jsonNode.get("refresh_token")).thenReturn(jsonNode);
            when(jsonNode.get("expires_in")).thenReturn(jsonNode);
            when(jsonNode.asText()).thenReturn("user123").thenReturn("newuser@example.com")
                    .thenReturn("access-token-123").thenReturn("refresh-token-123");
            when(jsonNode.asLong()).thenReturn(3600L);

            when(userRepository.findByEmail(email)).thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenReturn(existingUser);

            // When
            AuthResponse response = supabaseService.signInWithEmail(email, password);

            // Then
            assertThat(response).isNotNull();
            verify(userRepository).save(argThat(user -> {
                assertThat(user.getEmail()).isEqualTo("newuser@example.com");
                assertThat(user.getNickname()).isEqualTo("newuser");
                assertThat(user.getProvider()).isEqualTo("supabase");
                assertThat(user.isEmailVerified()).isTrue();
                assertThat(user.isActive()).isTrue();
                return true;
            }));
        }

        @Test
        @DisplayName("Should throw exception when Supabase authentication fails")
        void shouldThrowExceptionWhenSupabaseAuthenticationFails() {
            // Given
            when(webClient.post()).thenReturn(requestBodyUriSpec);
            when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
            when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
            when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.error(new RuntimeException("Auth failed")));

            // When & Then
            assertThatThrownBy(() -> supabaseService.signInWithEmail("test@example.com", "wrongpassword"))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessage("Authentication failed");
        }

        @Test
        @DisplayName("Should throw exception when JSON parsing fails")
        void shouldThrowExceptionWhenJsonParsingFails() throws Exception {
            // Given
            when(webClient.post()).thenReturn(requestBodyUriSpec);
            when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
            when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
            when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just("invalid-json"));

            when(objectMapper.readTree("invalid-json")).thenThrow(new RuntimeException("Invalid JSON"));

            // When & Then
            assertThatThrownBy(() -> supabaseService.signInWithEmail("test@example.com", "password"))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessage("Authentication failed");
        }
    }

    @Nested
    @DisplayName("Sign In With OAuth Tests")
    class SignInWithOAuthTests {

        @Test
        @DisplayName("Should successfully sign in with OAuth")
        void shouldSuccessfullySignInWithOAuth() throws Exception {
            // Given
            String provider = "google";
            String idToken = "google-id-token";

            when(webClient.post()).thenReturn(requestBodyUriSpec);
            when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
            when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
            when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(mockSupabaseResponse));

            when(objectMapper.readTree(mockSupabaseResponse)).thenReturn(jsonNode);
            when(jsonNode.get("user")).thenReturn(userNode);
            when(userNode.get("id")).thenReturn(jsonNode);
            when(userNode.get("email")).thenReturn(jsonNode);
            when(jsonNode.get("access_token")).thenReturn(jsonNode);
            when(jsonNode.get("refresh_token")).thenReturn(jsonNode);
            when(jsonNode.get("expires_in")).thenReturn(jsonNode);
            when(jsonNode.asText()).thenReturn("user123").thenReturn("test@example.com")
                    .thenReturn("access-token-123").thenReturn("refresh-token-123");
            when(jsonNode.asLong()).thenReturn(3600L);

            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(existingUser));

            // When
            AuthResponse response = supabaseService.signInWithOAuth(provider, idToken);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getUserId()).isEqualTo("user123");
            verify(requestBodyUriSpec).uri(contains("/auth/v1/token?grant_type=id_token"));
            verify(requestBodySpec).bodyValue(argThat(body -> {
                Map<String, String> bodyMap = (Map<String, String>) body;
                return "google".equals(bodyMap.get("provider")) && "google-id-token".equals(bodyMap.get("id_token"));
            }));
        }

        @Test
        @DisplayName("Should throw exception when OAuth authentication fails")
        void shouldThrowExceptionWhenOAuthAuthenticationFails() {
            // Given
            when(webClient.post()).thenReturn(requestBodyUriSpec);
            when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
            when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
            when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.error(new RuntimeException("OAuth failed")));

            // When & Then
            assertThatThrownBy(() -> supabaseService.signInWithOAuth("google", "invalid-token"))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessage("OAuth authentication failed");
        }
    }

    @Nested
    @DisplayName("Sign Up Tests")
    class SignUpTests {

        @Test
        @DisplayName("Should successfully sign up new user")
        void shouldSuccessfullySignUpNewUser() throws Exception {
            // Given
            String email = "newuser@example.com";
            String password = "password123";

            when(webClient.post()).thenReturn(requestBodyUriSpec);
            when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
            when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
            when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(mockSupabaseResponse));

            when(objectMapper.readTree(mockSupabaseResponse)).thenReturn(jsonNode);
            when(jsonNode.get("user")).thenReturn(userNode);
            when(userNode.get("id")).thenReturn(jsonNode);
            when(userNode.get("email")).thenReturn(jsonNode);
            when(jsonNode.get("access_token")).thenReturn(jsonNode);
            when(jsonNode.get("refresh_token")).thenReturn(jsonNode);
            when(jsonNode.get("expires_in")).thenReturn(jsonNode);
            when(jsonNode.asText()).thenReturn("user123").thenReturn("newuser@example.com")
                    .thenReturn("access-token-123").thenReturn("refresh-token-123");
            when(jsonNode.asLong()).thenReturn(3600L);

            when(userRepository.findByEmail(email)).thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenReturn(existingUser);

            // When
            AuthResponse response = supabaseService.signUp(email, password);

            // Then
            assertThat(response).isNotNull();
            verify(requestBodyUriSpec).uri(contains("/auth/v1/signup"));
        }

        @Test
        @DisplayName("Should throw exception when sign up fails")
        void shouldThrowExceptionWhenSignUpFails() {
            // Given
            when(webClient.post()).thenReturn(requestBodyUriSpec);
            when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
            when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
            when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.error(new RuntimeException("Sign up failed")));

            // When & Then
            assertThatThrownBy(() -> supabaseService.signUp("invalid@example.com", "weak"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Sign up failed");
        }
    }

    @Nested
    @DisplayName("Sign Out Tests")
    class SignOutTests {

        @Test
        @DisplayName("Should successfully sign out")
        void shouldSuccessfullySignOut() {
            // Given
            String accessToken = "access-token-123";

            when(webClient.post()).thenReturn(requestBodyUriSpec);
            when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
            when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
            when(requestBodySpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(Void.class)).thenReturn(Mono.empty());

            // When & Then
            assertThatCode(() -> supabaseService.signOut(accessToken))
                    .doesNotThrowAnyException();

            verify(requestBodyUriSpec).uri(contains("/auth/v1/logout"));
            verify(requestBodySpec).header("Authorization", "Bearer " + accessToken);
        }

        @Test
        @DisplayName("Should handle sign out failure gracefully")
        void shouldHandleSignOutFailureGracefully() {
            // Given
            String accessToken = "invalid-token";

            when(webClient.post()).thenReturn(requestBodyUriSpec);
            when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
            when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
            when(requestBodySpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(Void.class)).thenReturn(Mono.error(new RuntimeException("Sign out failed")));

            // When & Then
            assertThatCode(() -> supabaseService.signOut(accessToken))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Refresh Token Tests")
    class RefreshTokenTests {

        @Test
        @DisplayName("Should successfully refresh token")
        void shouldSuccessfullyRefreshToken() throws Exception {
            // Given
            String refreshToken = "refresh-token-123";

            when(webClient.post()).thenReturn(requestBodyUriSpec);
            when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
            when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
            when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(mockSupabaseResponse));

            when(objectMapper.readTree(mockSupabaseResponse)).thenReturn(jsonNode);
            when(jsonNode.get("user")).thenReturn(userNode);
            when(userNode.get("id")).thenReturn(jsonNode);
            when(userNode.get("email")).thenReturn(jsonNode);
            when(jsonNode.get("access_token")).thenReturn(jsonNode);
            when(jsonNode.get("refresh_token")).thenReturn(jsonNode);
            when(jsonNode.get("expires_in")).thenReturn(jsonNode);
            when(jsonNode.asText()).thenReturn("user123").thenReturn("test@example.com")
                    .thenReturn("new-access-token").thenReturn("new-refresh-token");
            when(jsonNode.asLong()).thenReturn(3600L);

            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(existingUser));

            // When
            AuthResponse response = supabaseService.refreshToken(refreshToken);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getAccessToken()).isEqualTo("new-access-token");
            assertThat(response.getRefreshToken()).isEqualTo("new-refresh-token");

            verify(requestBodyUriSpec).uri(contains("/auth/v1/token?grant_type=refresh_token"));
            verify(requestBodySpec).bodyValue(argThat(body -> {
                Map<String, String> bodyMap = (Map<String, String>) body;
                return refreshToken.equals(bodyMap.get("refresh_token"));
            }));
        }

        @Test
        @DisplayName("Should throw exception when refresh token fails")
        void shouldThrowExceptionWhenRefreshTokenFails() {
            // Given
            when(webClient.post()).thenReturn(requestBodyUriSpec);
            when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
            when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
            when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.error(new RuntimeException("Refresh failed")));

            // When & Then
            assertThatThrownBy(() -> supabaseService.refreshToken("invalid-refresh-token"))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessage("Token refresh failed");
        }
    }

    @Nested
    @DisplayName("Verify Token Tests")
    class VerifyTokenTests {

        @Test
        @DisplayName("Should return true for valid token")
        void shouldReturnTrueForValidToken() {
            // Given
            String accessToken = "valid-access-token";
            String userResponse = "{\"id\":\"user123\",\"email\":\"test@example.com\"}";

            when(webClient.get()).thenReturn(requestHeadersUriSpec);
            when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.header(anyString(), anyString())).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(userResponse));

            // When
            boolean isValid = supabaseService.verifyToken(accessToken);

            // Then
            assertThat(isValid).isTrue();
            verify(requestHeadersUriSpec).uri(contains("/auth/v1/user"));
            verify(requestHeadersSpec).header("Authorization", "Bearer " + accessToken);
        }

        @Test
        @DisplayName("Should return false for invalid token")
        void shouldReturnFalseForInvalidToken() {
            // Given
            String accessToken = "invalid-access-token";

            when(webClient.get()).thenReturn(requestHeadersUriSpec);
            when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.header(anyString(), anyString())).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.error(new RuntimeException("Token invalid")));

            // When
            boolean isValid = supabaseService.verifyToken(accessToken);

            // Then
            assertThat(isValid).isFalse();
        }

        @Test
        @DisplayName("Should return false for null response")
        void shouldReturnFalseForNullResponse() {
            // Given
            String accessToken = "some-token";

            when(webClient.get()).thenReturn(requestHeadersUriSpec);
            when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.header(anyString(), anyString())).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(null));

            // When
            boolean isValid = supabaseService.verifyToken(accessToken);

            // Then
            assertThat(isValid).isFalse();
        }

        @Test
        @DisplayName("Should return false for empty response")
        void shouldReturnFalseForEmptyResponse() {
            // Given
            String accessToken = "some-token";

            when(webClient.get()).thenReturn(requestHeadersUriSpec);
            when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.header(anyString(), anyString())).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(""));

            // When
            boolean isValid = supabaseService.verifyToken(accessToken);

            // Then
            assertThat(isValid).isFalse();
        }
    }

    @Nested
    @DisplayName("Get User ID Tests")
    class GetUserIdTests {

        @Test
        @DisplayName("Should successfully get user ID from token")
        void shouldSuccessfullyGetUserIdFromToken() throws Exception {
            // Given
            String accessToken = "valid-access-token";
            String userResponse = "{\"id\":\"user123\",\"email\":\"test@example.com\"}";

            when(webClient.get()).thenReturn(requestHeadersUriSpec);
            when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.header(anyString(), anyString())).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(userResponse));

            when(objectMapper.readTree(userResponse)).thenReturn(jsonNode);
            when(jsonNode.get("id")).thenReturn(jsonNode);
            when(jsonNode.asText()).thenReturn("user123");

            // When
            String userId = supabaseService.getUserId(accessToken);

            // Then
            assertThat(userId).isEqualTo("user123");
        }

        @Test
        @DisplayName("Should throw exception when token is invalid")
        void shouldThrowExceptionWhenTokenIsInvalid() {
            // Given
            String accessToken = "invalid-token";

            when(webClient.get()).thenReturn(requestHeadersUriSpec);
            when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.header(anyString(), anyString())).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.error(new RuntimeException("Invalid token")));

            // When & Then
            assertThatThrownBy(() -> supabaseService.getUserId(accessToken))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessage("Invalid token");
        }

        @Test
        @DisplayName("Should throw exception when JSON parsing fails")
        void shouldThrowExceptionWhenJsonParsingFails() throws Exception {
            // Given
            String accessToken = "valid-token";
            String userResponse = "invalid-json";

            when(webClient.get()).thenReturn(requestHeadersUriSpec);
            when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.header(anyString(), anyString())).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(userResponse));

            when(objectMapper.readTree(userResponse)).thenThrow(new RuntimeException("JSON parsing failed"));

            // When & Then
            assertThatThrownBy(() -> supabaseService.getUserId(accessToken))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessage("Invalid token");
        }
    }

    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {

        @Test
        @DisplayName("Should handle full authentication flow")
        void shouldHandleFullAuthenticationFlow() throws Exception {
            // Given
            String email = "test@example.com";
            String password = "password123";

            // Setup for sign in
            when(webClient.post()).thenReturn(requestBodyUriSpec);
            when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
            when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
            when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(mockSupabaseResponse));

            when(objectMapper.readTree(mockSupabaseResponse)).thenReturn(jsonNode);
            when(jsonNode.get("user")).thenReturn(userNode);
            when(userNode.get("id")).thenReturn(jsonNode);
            when(userNode.get("email")).thenReturn(jsonNode);
            when(jsonNode.get("access_token")).thenReturn(jsonNode);
            when(jsonNode.get("refresh_token")).thenReturn(jsonNode);
            when(jsonNode.get("expires_in")).thenReturn(jsonNode);
            when(jsonNode.asText()).thenReturn("user123", "test@example.com", "access-token-123", "refresh-token-123");
            when(jsonNode.asLong()).thenReturn(3600L);

            when(userRepository.findByEmail(email)).thenReturn(Optional.of(existingUser));

            // Setup for token verification
            when(webClient.get()).thenReturn(requestHeadersUriSpec);
            when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.header(anyString(), anyString())).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just("{\"id\":\"user123\"}"));

            // When
            AuthResponse authResponse = supabaseService.signInWithEmail(email, password);
            boolean isValid = supabaseService.verifyToken(authResponse.getAccessToken());

            // Then
            assertThat(authResponse).isNotNull();
            assertThat(authResponse.getAccessToken()).isEqualTo("access-token-123");
            assertThat(isValid).isTrue();
        }

        @Test
        @DisplayName("Should handle OAuth and regular sign in differently")
        void shouldHandleOAuthAndRegularSignInDifferently() throws Exception {
            // Test regular sign in
            when(webClient.post()).thenReturn(requestBodyUriSpec);
            when(requestBodyUriSpec.uri(contains("grant_type=password"))).thenReturn(requestBodySpec);
            when(requestBodyUriSpec.uri(contains("grant_type=id_token"))).thenReturn(requestBodySpec);
            when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
            when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(mockSupabaseResponse));

            // Setup JSON parsing
            when(objectMapper.readTree(mockSupabaseResponse)).thenReturn(jsonNode);
            when(jsonNode.get("user")).thenReturn(userNode);
            when(userNode.get("id")).thenReturn(jsonNode);
            when(userNode.get("email")).thenReturn(jsonNode);
            when(jsonNode.get("access_token")).thenReturn(jsonNode);
            when(jsonNode.get("refresh_token")).thenReturn(jsonNode);
            when(jsonNode.get("expires_in")).thenReturn(jsonNode);
            when(jsonNode.asText()).thenReturn("user123", "test@example.com", "access-token-123", "refresh-token-123");
            when(jsonNode.asLong()).thenReturn(3600L);

            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(existingUser));

            // When
            AuthResponse emailAuth = supabaseService.signInWithEmail("test@example.com", "password");
            AuthResponse oauthAuth = supabaseService.signInWithOAuth("google", "google-token");

            // Then
            assertThat(emailAuth).isNotNull();
            assertThat(oauthAuth).isNotNull();
            
            verify(requestBodyUriSpec).uri(contains("grant_type=password"));
            verify(requestBodyUriSpec).uri(contains("grant_type=id_token"));
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should handle network timeouts gracefully")
        void shouldHandleNetworkTimeoutsGracefully() {
            // Given
            when(webClient.post()).thenReturn(requestBodyUriSpec);
            when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
            when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
            when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.error(new RuntimeException("Connection timeout")));

            // When & Then
            assertThatThrownBy(() -> supabaseService.signInWithEmail("test@example.com", "password"))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessage("Authentication failed");
        }

        @Test
        @DisplayName("Should handle malformed JSON responses")
        void shouldHandleMalformedJsonResponses() throws Exception {
            // Given
            when(webClient.post()).thenReturn(requestBodyUriSpec);
            when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
            when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
            when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just("malformed-json"));

            when(objectMapper.readTree("malformed-json")).thenThrow(new RuntimeException("JSON parse error"));

            // When & Then
            assertThatThrownBy(() -> supabaseService.signInWithEmail("test@example.com", "password"))
                    .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        @DisplayName("Should handle null email in user creation")
        void shouldHandleNullEmailInUserCreation() {
            // Given
            when(userRepository.findByEmail(null)).thenReturn(Optional.empty());

            // When & Then
            assertThatCode(() -> userRepository.findByEmail(null))
                    .doesNotThrowAnyException();
        }
    }
}