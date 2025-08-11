package com.oddiya.service;

import com.oddiya.service.impl.JwtServiceImpl;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

    private JwtServiceImpl jwtService;

    private final String testSecret = "testSecretKeyThatIsAtLeast256BitsLongForHS256Algorithm12345678";
    private final Long testExpiration = 3600000L; // 1 hour
    private final Long testRefreshExpiration = 86400000L; // 24 hours

    @BeforeEach
    void setUp() {
        jwtService = new JwtServiceImpl(testSecret, testExpiration, testRefreshExpiration);
    }

    @Test
    @DisplayName("Should generate valid access token")
    void generateToken_ShouldReturnValidToken() {
        // Given
        String username = "testuser@example.com";

        // When
        String token = jwtService.generateToken(username);

        // Then
        assertThat(token).isNotNull();
        assertThat(token).isNotEmpty();
        assertThat(jwtService.validateToken(token)).isTrue();
        assertThat(jwtService.getUsernameFromToken(token)).isEqualTo(username);
    }

    @Test
    @DisplayName("Should generate valid refresh token")
    void generateRefreshToken_ShouldReturnValidToken() {
        // Given
        String username = "testuser@example.com";

        // When
        String refreshToken = jwtService.generateRefreshToken(username);

        // Then
        assertThat(refreshToken).isNotNull();
        assertThat(refreshToken).isNotEmpty();
        assertThat(jwtService.validateToken(refreshToken)).isTrue();
        assertThat(jwtService.getUsernameFromToken(refreshToken)).isEqualTo(username);
    }

    @Test
    @DisplayName("Should extract username from valid token")
    void getUsernameFromToken_ShouldReturnCorrectUsername() {
        // Given
        String username = "testuser@example.com";
        String token = jwtService.generateToken(username);

        // When
        String extractedUsername = jwtService.getUsernameFromToken(token);

        // Then
        assertThat(extractedUsername).isEqualTo(username);
    }

    @Test
    @DisplayName("Should validate valid token")
    void validateToken_WithValidToken_ShouldReturnTrue() {
        // Given
        String token = jwtService.generateToken("testuser@example.com");

        // When
        boolean isValid = jwtService.validateToken(token);

        // Then
        assertThat(isValid).isTrue();
    }

    @Test
    @DisplayName("Should invalidate malformed token")
    void validateToken_WithMalformedToken_ShouldReturnFalse() {
        // Given
        String malformedToken = "invalid.token.here";

        // When
        boolean isValid = jwtService.validateToken(malformedToken);

        // Then
        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("Should invalidate empty token")
    void validateToken_WithEmptyToken_ShouldReturnFalse() {
        // When
        boolean isValid = jwtService.validateToken("");

        // Then
        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("Should invalidate null token")
    void validateToken_WithNullToken_ShouldReturnFalse() {
        // When
        boolean isValid = jwtService.validateToken(null);

        // Then
        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("Should extract claims from valid token")
    void extractClaims_ShouldReturnValidClaims() {
        // Given
        String username = "testuser@example.com";
        String token = jwtService.generateToken(username);

        // When
        Claims claims = jwtService.extractAllClaims(token);

        // Then
        assertThat(claims).isNotNull();
        assertThat(claims.getSubject()).isEqualTo(username);
        assertThat(claims.getIssuedAt()).isNotNull();
        assertThat(claims.getExpiration()).isNotNull();
    }

    @Test
    @DisplayName("Should check if token is not expired")
    void isTokenExpired_WithValidToken_ShouldReturnFalse() {
        // Given
        String token = jwtService.generateToken("testuser@example.com");

        // When
        boolean isExpired = jwtService.isTokenExpired(token);

        // Then
        assertThat(isExpired).isFalse();
    }

    @Test
    @DisplayName("Should check if expired token is expired")
    void isTokenExpired_WithExpiredToken_ShouldReturnTrue() {
        // Given - Create a service with very short expiration
        JwtServiceImpl shortLivedService = new JwtServiceImpl(testSecret, 1L, testRefreshExpiration); // 1ms
        String token = shortLivedService.generateToken("testuser@example.com");
        
        // Wait for token to expire
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // When
        boolean isExpired = shortLivedService.isTokenExpired(token);

        // Then
        assertThat(isExpired).isTrue();
    }
}