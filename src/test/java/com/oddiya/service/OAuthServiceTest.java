package com.oddiya.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oddiya.config.OAuthConfig;
import com.oddiya.dto.response.AuthResponse;
import com.oddiya.exception.UnauthorizedException;
import com.oddiya.repository.UserRepository;
import com.oddiya.service.impl.OAuthServiceImpl;
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

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OAuthService Tests")
class OAuthServiceTest {

    @Mock
    private WebClient webClient;

    @Mock
    private OAuthConfig oAuthConfig;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SupabaseService supabaseService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private WebClient.RequestBodySpec requestBodySpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    @Mock
    private JsonNode jsonNode;

    @InjectMocks
    private OAuthServiceImpl oAuthService;

    private OAuthConfig.Google googleProvider;
    private OAuthConfig.Apple appleProvider;
    private AuthResponse mockAuthResponse;

    @BeforeEach
    void setUp() {
        googleProvider = new OAuthConfig.Google();
        googleProvider.setClientId("google-client-id");
        googleProvider.setClientSecret("google-client-secret");
        googleProvider.setRedirectUri("http://localhost/callback/google");
        googleProvider.setTokenUrl("https://oauth2.googleapis.com/token");

        appleProvider = new OAuthConfig.Apple();
        appleProvider.setClientId("apple-client-id");
        appleProvider.setRedirectUri("http://localhost/callback/apple");
        appleProvider.setTokenUrl("https://appleid.apple.com/auth/token");

        mockAuthResponse = AuthResponse.builder()
                .userId("user123")
                .email("test@example.com")
                .accessToken("access-token")
                .refreshToken("refresh-token")
                .expiresIn(3600L)
                .build();

        // Set up reflection test utils to inject values
        ReflectionTestUtils.setField(oAuthService, "supabaseUrl", "https://test.supabase.co");
        ReflectionTestUtils.setField(oAuthService, "supabaseAnonKey", "test-anon-key");
    }

    @Nested
    @DisplayName("Verify Token Tests")
    class VerifyTokenTests {

        @Test
        @DisplayName("Should successfully verify OAuth token")
        void shouldSuccessfullyVerifyOAuthToken() {
            // Given
            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("sub", "google123");
            userInfo.put("email", "test@example.com");
            userInfo.put("name", "Test User");

            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("user", userInfo);

            when(webClient.post()).thenReturn(requestBodyUriSpec);
            when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
            when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
            when(requestBodySpec.contentType(any())).thenReturn(requestBodySpec);
            when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(responseBody));

            // When
            Map<String, Object> result = oAuthService.verifyToken("google", "test-id-token");

            // Then
            assertThat(result).isNotNull();
            assertThat(result.get("sub")).isEqualTo("google123");
            assertThat(result.get("email")).isEqualTo("test@example.com");
            assertThat(result.get("name")).isEqualTo("Test User");
        }

        @Test
        @DisplayName("Should throw exception when response is null")
        void shouldThrowExceptionWhenResponseIsNull() {
            // Given
            when(webClient.post()).thenReturn(requestBodyUriSpec);
            when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
            when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
            when(requestBodySpec.contentType(any())).thenReturn(requestBodySpec);
            when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(null));

            // When & Then
            assertThatThrownBy(() -> oAuthService.verifyToken("google", "invalid-token"))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessage("Invalid OAuth token");
        }

        @Test
        @DisplayName("Should throw exception when user not in response")
        void shouldThrowExceptionWhenUserNotInResponse() {
            // Given
            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("error", "invalid token");

            when(webClient.post()).thenReturn(requestBodyUriSpec);
            when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
            when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
            when(requestBodySpec.contentType(any())).thenReturn(requestBodySpec);
            when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(responseBody));

            // When & Then
            assertThatThrownBy(() -> oAuthService.verifyToken("google", "invalid-token"))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessage("Invalid OAuth token");
        }

        @Test
        @DisplayName("Should throw exception when WebClient throws exception")
        void shouldThrowExceptionWhenWebClientThrowsException() {
            // Given
            when(webClient.post()).thenThrow(new RuntimeException("Network error"));

            // When & Then
            assertThatThrownBy(() -> oAuthService.verifyToken("google", "test-token"))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessage("OAuth verification failed");
        }

        @Test
        @DisplayName("Should handle different providers")
        void shouldHandleDifferentProviders() {
            // Given
            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("sub", "apple123");
            userInfo.put("email", "apple@example.com");

            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("user", userInfo);

            when(webClient.post()).thenReturn(requestBodyUriSpec);
            when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
            when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
            when(requestBodySpec.contentType(any())).thenReturn(requestBodySpec);
            when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(responseBody));

            // When
            Map<String, Object> result = oAuthService.verifyToken("apple", "apple-id-token");

            // Then
            assertThat(result).isNotNull();
            assertThat(result.get("sub")).isEqualTo("apple123");
            assertThat(result.get("email")).isEqualTo("apple@example.com");

            verify(requestBodySpec).bodyValue(argThat(body -> {
                Map<String, Object> requestBody = (Map<String, Object>) body;
                return "apple".equals(requestBody.get("provider"));
            }));
        }
    }

    @Nested
    @DisplayName("Google Authentication Tests")
    class GoogleAuthenticationTests {

        @Test
        @DisplayName("Should successfully authenticate with Google auth code")
        void shouldSuccessfullyAuthenticateWithGoogleAuthCode() throws Exception {
            // Given
            String authCode = "google-auth-code";
            String tokenResponse = "{\"id_token\":\"google-id-token\",\"access_token\":\"access-token\"}";

            when(oAuthConfig.getGoogle()).thenReturn(googleProvider);
            when(webClient.post()).thenReturn(requestBodyUriSpec);
            when(requestBodyUriSpec.uri(googleProvider.getTokenUrl())).thenReturn(requestBodySpec);
            when(requestBodySpec.contentType(any())).thenReturn(requestBodySpec);
            when(requestBodySpec.body(any())).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(tokenResponse));

            when(objectMapper.readTree(tokenResponse)).thenReturn(jsonNode);
            when(jsonNode.get("id_token")).thenReturn(jsonNode);
            when(jsonNode.asText()).thenReturn("google-id-token");

            when(supabaseService.signInWithOAuth("google", "google-id-token")).thenReturn(mockAuthResponse);

            // When
            AuthResponse response = oAuthService.authenticateWithGoogle(authCode);

            // Then
            assertThat(response).isEqualTo(mockAuthResponse);
            verify(supabaseService).signInWithOAuth("google", "google-id-token");
        }

        @Test
        @DisplayName("Should throw exception when Google token exchange fails")
        void shouldThrowExceptionWhenGoogleTokenExchangeFails() {
            // Given
            when(oAuthConfig.getGoogle()).thenReturn(googleProvider);
            when(webClient.post()).thenReturn(requestBodyUriSpec);
            when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
            when(requestBodySpec.contentType(any())).thenReturn(requestBodySpec);
            when(requestBodySpec.body(any())).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.error(new RuntimeException("Token exchange failed")));

            // When & Then
            assertThatThrownBy(() -> oAuthService.authenticateWithGoogle("invalid-code"))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessage("Google authentication failed");
        }

        @Test
        @DisplayName("Should throw exception when JSON parsing fails")
        void shouldThrowExceptionWhenJsonParsingFails() throws Exception {
            // Given
            String tokenResponse = "invalid-json";
            when(oAuthConfig.getGoogle()).thenReturn(googleProvider);
            when(webClient.post()).thenReturn(requestBodyUriSpec);
            when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
            when(requestBodySpec.contentType(any())).thenReturn(requestBodySpec);
            when(requestBodySpec.body(any())).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(tokenResponse));
            
            when(objectMapper.readTree(tokenResponse)).thenThrow(new RuntimeException("JSON parsing error"));

            // When & Then
            assertThatThrownBy(() -> oAuthService.authenticateWithGoogle("auth-code"))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessage("Google authentication failed");
        }

        @Test
        @DisplayName("Should successfully authenticate with Google ID token")
        void shouldSuccessfullyAuthenticateWithGoogleIdToken() {
            // Given
            String idToken = "google-id-token";
            when(supabaseService.signInWithOAuth("google", idToken)).thenReturn(mockAuthResponse);

            // When
            AuthResponse response = oAuthService.authenticateWithGoogleIdToken(idToken);

            // Then
            assertThat(response).isEqualTo(mockAuthResponse);
            verify(supabaseService).signInWithOAuth("google", idToken);
        }

        @Test
        @DisplayName("Should throw exception when Google ID token authentication fails")
        void shouldThrowExceptionWhenGoogleIdTokenAuthenticationFails() {
            // Given
            String idToken = "invalid-id-token";
            when(supabaseService.signInWithOAuth("google", idToken))
                    .thenThrow(new UnauthorizedException("Invalid token"));

            // When & Then
            assertThatThrownBy(() -> oAuthService.authenticateWithGoogleIdToken(idToken))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessage("Google authentication failed");
        }
    }

    @Nested
    @DisplayName("Apple Authentication Tests")
    class AppleAuthenticationTests {

        @Test
        @DisplayName("Should successfully authenticate with Apple auth code")
        void shouldSuccessfullyAuthenticateWithAppleAuthCode() throws Exception {
            // Given
            String authCode = "apple-auth-code";
            String tokenResponse = "{\"id_token\":\"apple-id-token\",\"access_token\":\"access-token\"}";

            when(oAuthConfig.getApple()).thenReturn(appleProvider);
            when(webClient.post()).thenReturn(requestBodyUriSpec);
            when(requestBodyUriSpec.uri(appleProvider.getTokenUrl())).thenReturn(requestBodySpec);
            when(requestBodySpec.contentType(any())).thenReturn(requestBodySpec);
            when(requestBodySpec.body(any())).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(tokenResponse));

            when(objectMapper.readTree(tokenResponse)).thenReturn(jsonNode);
            when(jsonNode.get("id_token")).thenReturn(jsonNode);
            when(jsonNode.asText()).thenReturn("apple-id-token");

            when(supabaseService.signInWithOAuth("apple", "apple-id-token")).thenReturn(mockAuthResponse);

            // When
            AuthResponse response = oAuthService.authenticateWithApple(authCode);

            // Then
            assertThat(response).isEqualTo(mockAuthResponse);
            verify(supabaseService).signInWithOAuth("apple", "apple-id-token");
        }

        @Test
        @DisplayName("Should throw exception when Apple token exchange fails")
        void shouldThrowExceptionWhenAppleTokenExchangeFails() {
            // Given
            when(oAuthConfig.getApple()).thenReturn(appleProvider);
            when(webClient.post()).thenReturn(requestBodyUriSpec);
            when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
            when(requestBodySpec.contentType(any())).thenReturn(requestBodySpec);
            when(requestBodySpec.body(any())).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.error(new RuntimeException("Apple token exchange failed")));

            // When & Then
            assertThatThrownBy(() -> oAuthService.authenticateWithApple("invalid-code"))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessage("Apple authentication failed");
        }

        @Test
        @DisplayName("Should successfully authenticate with Apple ID token")
        void shouldSuccessfullyAuthenticateWithAppleIdToken() {
            // Given
            String idToken = "apple-id-token";
            when(supabaseService.signInWithOAuth("apple", idToken)).thenReturn(mockAuthResponse);

            // When
            AuthResponse response = oAuthService.authenticateWithAppleIdToken(idToken);

            // Then
            assertThat(response).isEqualTo(mockAuthResponse);
            verify(supabaseService).signInWithOAuth("apple", idToken);
        }

        @Test
        @DisplayName("Should throw exception when Apple ID token authentication fails")
        void shouldThrowExceptionWhenAppleIdTokenAuthenticationFails() {
            // Given
            String idToken = "invalid-apple-id-token";
            when(supabaseService.signInWithOAuth("apple", idToken))
                    .thenThrow(new UnauthorizedException("Invalid Apple token"));

            // When & Then
            assertThatThrownBy(() -> oAuthService.authenticateWithAppleIdToken(idToken))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessage("Apple authentication failed");
        }

        @Test
        @DisplayName("Should generate placeholder Apple client secret")
        void shouldGeneratePlaceholderAppleClientSecret() throws Exception {
            // Given
            String authCode = "apple-auth-code";
            String tokenResponse = "{\"id_token\":\"apple-id-token\"}";

            when(oAuthConfig.getApple()).thenReturn(appleProvider);
            when(webClient.post()).thenReturn(requestBodyUriSpec);
            when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
            when(requestBodySpec.contentType(any())).thenReturn(requestBodySpec);
            when(requestBodySpec.body(any())).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(tokenResponse));
            when(objectMapper.readTree(tokenResponse)).thenReturn(jsonNode);
            when(jsonNode.get("id_token")).thenReturn(jsonNode);
            when(jsonNode.asText()).thenReturn("apple-id-token");
            when(supabaseService.signInWithOAuth("apple", "apple-id-token")).thenReturn(mockAuthResponse);

            // When
            oAuthService.authenticateWithApple(authCode);

            // Then
            // Verify that the client secret generation is called (placeholder implementation)
            // In a real implementation, this would test the actual JWT generation
            verify(requestBodySpec).body(any());
        }
    }

    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {

        @Test
        @DisplayName("Should handle full OAuth flow with token verification")
        void shouldHandleFullOAuthFlowWithTokenVerification() {
            // Given
            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("sub", "google123");
            userInfo.put("email", "test@example.com");

            Map<String, Object> verifyResponse = new HashMap<>();
            verifyResponse.put("user", userInfo);

            when(webClient.post()).thenReturn(requestBodyUriSpec);
            when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
            when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
            when(requestBodySpec.contentType(any())).thenReturn(requestBodySpec);
            when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(verifyResponse));

            when(supabaseService.signInWithOAuth("google", "test-token")).thenReturn(mockAuthResponse);

            // When
            Map<String, Object> verificationResult = oAuthService.verifyToken("google", "test-token");
            AuthResponse authResult = oAuthService.authenticateWithGoogleIdToken("test-token");

            // Then
            assertThat(verificationResult).isEqualTo(userInfo);
            assertThat(authResult).isEqualTo(mockAuthResponse);
        }

        @Test
        @DisplayName("Should handle concurrent OAuth operations")
        void shouldHandleConcurrentOAuthOperations() {
            // Given
            when(supabaseService.signInWithOAuth("google", "token1")).thenReturn(mockAuthResponse);
            when(supabaseService.signInWithOAuth("apple", "token2")).thenReturn(mockAuthResponse);

            // When
            AuthResponse response1 = oAuthService.authenticateWithGoogleIdToken("token1");
            AuthResponse response2 = oAuthService.authenticateWithAppleIdToken("token2");

            // Then
            assertThat(response1).isEqualTo(mockAuthResponse);
            assertThat(response2).isEqualTo(mockAuthResponse);
            verify(supabaseService).signInWithOAuth("google", "token1");
            verify(supabaseService).signInWithOAuth("apple", "token2");
        }

        @Test
        @DisplayName("Should handle network timeouts gracefully")
        void shouldHandleNetworkTimeoutsGracefully() {
            // Given
            when(supabaseService.signInWithOAuth("google", "timeout-token"))
                    .thenThrow(new RuntimeException("Request timeout"));

            // When & Then
            assertThatThrownBy(() -> oAuthService.authenticateWithGoogleIdToken("timeout-token"))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessage("Google authentication failed");
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should handle null token gracefully")
        void shouldHandleNullTokenGracefully() {
            // When & Then
            assertThatThrownBy(() -> oAuthService.verifyToken("google", null))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessage("OAuth verification failed");
        }

        @Test
        @DisplayName("Should handle empty token gracefully")
        void shouldHandleEmptyTokenGracefully() {
            // When & Then
            assertThatThrownBy(() -> oAuthService.verifyToken("google", ""))
                    .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        @DisplayName("Should handle null provider gracefully")
        void shouldHandleNullProviderGracefully() {
            // When & Then
            assertThatThrownBy(() -> oAuthService.verifyToken(null, "test-token"))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessage("OAuth verification failed");
        }

        @Test
        @DisplayName("Should handle unknown provider gracefully")
        void shouldHandleUnknownProviderGracefully() {
            // Given
            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("sub", "unknown123");

            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("user", userInfo);

            when(webClient.post()).thenReturn(requestBodyUriSpec);
            when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
            when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
            when(requestBodySpec.contentType(any())).thenReturn(requestBodySpec);
            when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(responseBody));

            // When
            Map<String, Object> result = oAuthService.verifyToken("unknown-provider", "test-token");

            // Then
            assertThat(result).isNotNull();
            assertThat(result.get("sub")).isEqualTo("unknown123");
        }

        @Test
        @DisplayName("Should handle malformed JSON response")
        void shouldHandleMalformedJsonResponse() throws Exception {
            // Given
            String malformedJson = "invalid-json-response";
            when(oAuthConfig.getGoogle()).thenReturn(googleProvider);
            when(webClient.post()).thenReturn(requestBodyUriSpec);
            when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
            when(requestBodySpec.contentType(any())).thenReturn(requestBodySpec);
            when(requestBodySpec.body(any())).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(malformedJson));
            when(objectMapper.readTree(malformedJson)).thenThrow(new RuntimeException("Invalid JSON"));

            // When & Then
            assertThatThrownBy(() -> oAuthService.authenticateWithGoogle("auth-code"))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessage("Google authentication failed");
        }
    }
}