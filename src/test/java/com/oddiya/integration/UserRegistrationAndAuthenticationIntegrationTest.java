package com.oddiya.integration;

import com.oddiya.dto.request.LoginRequest;
import com.oddiya.dto.request.RefreshTokenRequest;
import com.oddiya.dto.request.SignUpRequest;
import com.oddiya.dto.response.ApiResponse;
import com.oddiya.dto.response.AuthResponse;
import com.oddiya.entity.User;
import org.junit.jupiter.api.*;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration test for complete user registration and authentication workflows.
 * Tests the entire flow from user registration through login, token refresh, and logout.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class UserRegistrationAndAuthenticationIntegrationTest extends OddiyaIntegrationTestBase {

    private String registeredUserEmail;
    private String accessToken;
    private String refreshToken;

    @Test
    @Order(1)
    @DisplayName("Should complete OAuth login workflow successfully")
    void shouldCompleteOAuthLoginWorkflowSuccessfully() {
        // Given: OAuth login request
        LoginRequest loginRequest = createMockLoginRequest();
        
        HttpEntity<LoginRequest> request = new HttpEntity<>(loginRequest, createHeaders());

        // When: Making OAuth login request
        ResponseEntity<ApiResponse<AuthResponse>> response = restTemplate.exchange(
            baseUrl + "/auth/login",
            HttpMethod.POST,
            request,
            new ParameterizedTypeReference<ApiResponse<AuthResponse>>() {}
        );

        // Then: Should return successful authentication response
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        
        ApiResponse<AuthResponse> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.isSuccess()).isTrue();
        assertThat(body.getMessage()).isEqualTo("Success");
        
        AuthResponse authResponse = body.getData();
        assertThat(authResponse).isNotNull();
        assertThat(authResponse.getAccessToken()).isNotBlank();
        assertThat(authResponse.getRefreshToken()).isNotBlank();
        assertThat(authResponse.getUserId()).isNotBlank();
        assertThat(authResponse.getEmail()).isNotBlank();
        assertThat(authResponse.getNickname()).isNotBlank();

        // Store tokens for subsequent tests
        accessToken = authResponse.getAccessToken();
        refreshToken = authResponse.getRefreshToken();
        registeredUserEmail = authResponse.getEmail();

        // Verify user is saved in database
        List<User> users = userRepository.findAll();
        User savedUser = users.stream()
            .filter(u -> u.getEmail().equals(authResponse.getEmail()))
            .findFirst()
            .orElse(null);
        
        assertThat(savedUser).isNotNull();
        assertThat(savedUser.getProvider()).isEqualTo("google");
        assertThat(savedUser.isActive()).isTrue();
        assertThat(savedUser.getRefreshToken()).isNotBlank();
    }

    @Test
    @Order(2)
    @DisplayName("Should handle duplicate OAuth login gracefully")
    void shouldHandleDuplicateOAuthLoginGracefully() {
        // Given: First OAuth login
        LoginRequest loginRequest = createMockLoginRequest();
        HttpEntity<LoginRequest> request = new HttpEntity<>(loginRequest, createHeaders());
        
        restTemplate.exchange(
            baseUrl + "/auth/login",
            HttpMethod.POST,
            request,
            new ParameterizedTypeReference<ApiResponse<AuthResponse>>() {}
        );

        // When: Second OAuth login with same credentials
        ResponseEntity<ApiResponse<AuthResponse>> secondResponse = restTemplate.exchange(
            baseUrl + "/auth/login",
            HttpMethod.POST,
            request,
            new ParameterizedTypeReference<ApiResponse<AuthResponse>>() {}
        );

        // Then: Should return successful response (not create duplicate)
        assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        
        ApiResponse<AuthResponse> body = secondResponse.getBody();
        assertThat(body).isNotNull();
        assertThat(body.isSuccess()).isTrue();
        
        // Verify only one user exists in database
        List<User> users = userRepository.findAll();
        long userCount = users.stream()
            .filter(u -> u.getProvider().equals("google"))
            .count();
        assertThat(userCount).isEqualTo(1);
    }

    @Test
    @Order(3)
    @DisplayName("Should refresh access token successfully")
    void shouldRefreshAccessTokenSuccessfully() throws InterruptedException {
        // Given: Valid refresh token from login
        LoginRequest loginRequest = createMockLoginRequest();
        HttpEntity<LoginRequest> loginRequestEntity = new HttpEntity<>(loginRequest, createHeaders());
        
        ResponseEntity<ApiResponse<AuthResponse>> loginResponse = restTemplate.exchange(
            baseUrl + "/auth/login",
            HttpMethod.POST,
            loginRequestEntity,
            new ParameterizedTypeReference<ApiResponse<AuthResponse>>() {}
        );

        String originalRefreshToken = loginResponse.getBody().getData().getRefreshToken();
        
        // Small delay to ensure different token generation
        Thread.sleep(100);

        RefreshTokenRequest refreshRequest = RefreshTokenRequest.builder()
            .refreshToken(originalRefreshToken)
            .build();
        
        HttpEntity<RefreshTokenRequest> refreshRequestEntity = new HttpEntity<>(refreshRequest, createHeaders());

        // When: Refreshing token
        ResponseEntity<ApiResponse<AuthResponse>> refreshResponse = restTemplate.exchange(
            baseUrl + "/auth/refresh",
            HttpMethod.POST,
            refreshRequestEntity,
            new ParameterizedTypeReference<ApiResponse<AuthResponse>>() {}
        );

        // Then: Should return new tokens
        assertThat(refreshResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        
        ApiResponse<AuthResponse> body = refreshResponse.getBody();
        assertThat(body).isNotNull();
        assertThat(body.isSuccess()).isTrue();
        
        AuthResponse authResponse = body.getData();
        assertThat(authResponse).isNotNull();
        assertThat(authResponse.getAccessToken()).isNotBlank();
        assertThat(authResponse.getRefreshToken()).isNotBlank();
        assertThat(authResponse.getUserId()).isNotBlank();
        
        // Verify tokens are different (new ones generated)
        String newAccessToken = authResponse.getAccessToken();
        String newRefreshToken = authResponse.getRefreshToken();
        
        assertThat(newAccessToken).isNotEqualTo(loginResponse.getBody().getData().getAccessToken());
        assertThat(newRefreshToken).isNotEqualTo(originalRefreshToken);
    }

    @Test
    @Order(4)
    @DisplayName("Should validate token successfully")
    void shouldValidateTokenSuccessfully() {
        // Given: Valid access token
        LoginRequest loginRequest = createMockLoginRequest();
        HttpEntity<LoginRequest> loginRequestEntity = new HttpEntity<>(loginRequest, createHeaders());
        
        ResponseEntity<ApiResponse<AuthResponse>> loginResponse = restTemplate.exchange(
            baseUrl + "/auth/login",
            HttpMethod.POST,
            loginRequestEntity,
            new ParameterizedTypeReference<ApiResponse<AuthResponse>>() {}
        );

        String validToken = loginResponse.getBody().getData().getAccessToken();
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + validToken);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        // When: Validating token
        ResponseEntity<ApiResponse<Boolean>> response = restTemplate.exchange(
            baseUrl + "/auth/validate",
            HttpMethod.GET,
            request,
            new ParameterizedTypeReference<ApiResponse<Boolean>>() {}
        );

        // Then: Should return true for valid token
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        
        ApiResponse<Boolean> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.isSuccess()).isTrue();
        assertThat(body.getData()).isTrue();
    }

    @Test
    @Order(5)
    @DisplayName("Should reject invalid token")
    void shouldRejectInvalidToken() {
        // Given: Invalid token
        String invalidToken = "invalid.jwt.token";
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + invalidToken);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        // When: Validating invalid token
        ResponseEntity<ApiResponse<Boolean>> response = restTemplate.exchange(
            baseUrl + "/auth/validate",
            HttpMethod.GET,
            request,
            new ParameterizedTypeReference<ApiResponse<Boolean>>() {}
        );

        // Then: Should return false or error
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        
        ApiResponse<Boolean> body = response.getBody();
        assertThat(body).isNotNull();
        // Either returns false or throws an exception resulting in error response
        if (body.isSuccess()) {
            assertThat(body.getData()).isFalse();
        } else {
            assertThat(body.isSuccess()).isFalse();
        }
    }

    @Test
    @Order(6)
    @DisplayName("Should logout successfully and invalidate refresh token")
    void shouldLogoutSuccessfullyAndInvalidateRefreshToken() {
        // Given: Logged in user with valid tokens
        LoginRequest loginRequest = createMockLoginRequest();
        HttpEntity<LoginRequest> loginRequestEntity = new HttpEntity<>(loginRequest, createHeaders());
        
        ResponseEntity<ApiResponse<AuthResponse>> loginResponse = restTemplate.exchange(
            baseUrl + "/auth/login",
            HttpMethod.POST,
            loginRequestEntity,
            new ParameterizedTypeReference<ApiResponse<AuthResponse>>() {}
        );

        String accessToken = loginResponse.getBody().getData().getAccessToken();
        String refreshTokenToInvalidate = loginResponse.getBody().getData().getRefreshToken();
        String userId = loginResponse.getBody().getData().getUserId();
        
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        HttpEntity<Void> logoutRequest = new HttpEntity<>(headers);

        // When: Logging out
        ResponseEntity<Void> logoutResponse = restTemplate.exchange(
            baseUrl + "/auth/logout",
            HttpMethod.POST,
            logoutRequest,
            Void.class
        );

        // Then: Should return success status
        assertThat(logoutResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Verify refresh token is invalidated by trying to use it
        RefreshTokenRequest refreshRequest = RefreshTokenRequest.builder()
            .refreshToken(refreshTokenToInvalidate)
            .build();
        
        HttpEntity<RefreshTokenRequest> refreshRequestEntity = new HttpEntity<>(refreshRequest, createHeaders());

        ResponseEntity<ApiResponse<AuthResponse>> refreshResponse = restTemplate.exchange(
            baseUrl + "/auth/refresh",
            HttpMethod.POST,
            refreshRequestEntity,
            new ParameterizedTypeReference<ApiResponse<AuthResponse>>() {}
        );

        // Should fail since token was invalidated during logout
        assertThat(refreshResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @Order(7)
    @DisplayName("Should handle authentication workflow with missing provider gracefully")
    void shouldHandleAuthenticationWithMissingProviderGracefully() {
        // Given: Login request with missing provider
        LoginRequest invalidLoginRequest = LoginRequest.builder()
            .provider("") // Empty provider
            .providerId("some-provider-id")
            .email("test@example.com")
            .nickname("Test User")
            .build();
        
        HttpEntity<LoginRequest> request = new HttpEntity<>(invalidLoginRequest, createHeaders());

        // When: Making login request with invalid data
        ResponseEntity<ApiResponse<AuthResponse>> response = restTemplate.exchange(
            baseUrl + "/auth/login",
            HttpMethod.POST,
            request,
            new ParameterizedTypeReference<ApiResponse<AuthResponse>>() {}
        );

        // Then: Should return error response
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @Order(8)
    @DisplayName("Should handle authentication workflow transaction consistency")
    void shouldHandleAuthenticationWorkflowTransactionConsistency() {
        // Given: Multiple concurrent login attempts
        LoginRequest loginRequest = createMockLoginRequest();
        HttpEntity<LoginRequest> request = new HttpEntity<>(loginRequest, createHeaders());

        // When: Making multiple concurrent login requests
        ResponseEntity<ApiResponse<AuthResponse>> response1 = restTemplate.exchange(
            baseUrl + "/auth/login",
            HttpMethod.POST,
            request,
            new ParameterizedTypeReference<ApiResponse<AuthResponse>>() {}
        );

        ResponseEntity<ApiResponse<AuthResponse>> response2 = restTemplate.exchange(
            baseUrl + "/auth/login",
            HttpMethod.POST,
            request,
            new ParameterizedTypeReference<ApiResponse<AuthResponse>>() {}
        );

        // Then: Both should succeed and return consistent user data
        assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.OK);
        
        String userId1 = response1.getBody().getData().getUserId();
        String userId2 = response2.getBody().getData().getUserId();
        
        assertThat(userId1).isEqualTo(userId2);

        // Verify only one user record exists
        List<User> users = userRepository.findAll();
        long userCount = users.stream()
            .filter(u -> u.getProvider().equals("google"))
            .count();
        assertThat(userCount).isEqualTo(1);
    }
}