package com.oddiya.service;

import com.oddiya.exception.UnauthorizedException;
import com.oddiya.service.impl.JwtServiceImpl;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtService Tests")
class JwtServiceTest {

    private JwtServiceImpl jwtService;
    private final String testSecret = "test-secret-key-for-jwt-token-generation-must-be-at-least-256-bits-long";
    private final long accessTokenExpiration = 3600000L; // 1 hour
    private final long refreshTokenExpiration = 604800000L; // 1 week

    @BeforeEach
    void setUp() {
        jwtService = new JwtServiceImpl(testSecret, accessTokenExpiration, refreshTokenExpiration);
    }

    @Nested
    @DisplayName("Access Token Tests")
    class AccessTokenTests {

        @Test
        @DisplayName("Should generate valid access token")
        void shouldGenerateValidAccessToken() {
            // Given
            String userId = "user123";
            String email = "test@example.com";

            // When
            String token = jwtService.generateAccessToken(userId, email);

            // Then
            assertThat(token).isNotNull().isNotEmpty();
            
            // Verify token structure (JWT has 3 parts separated by dots)
            String[] tokenParts = token.split("\\.");
            assertThat(tokenParts).hasSize(3);
        }

        @Test
        @DisplayName("Should validate valid access token and return user ID")
        void shouldValidateValidAccessTokenAndReturnUserId() {
            // Given
            String userId = "user123";
            String email = "test@example.com";
            String token = jwtService.generateAccessToken(userId, email);

            // When
            String extractedUserId = jwtService.validateAccessToken(token);

            // Then
            assertThat(extractedUserId).isEqualTo(userId);
        }

        @Test
        @DisplayName("Should throw exception for invalid access token")
        void shouldThrowExceptionForInvalidAccessToken() {
            // Given
            String invalidToken = "invalid.token.format";

            // When & Then
            assertThatThrownBy(() -> jwtService.validateAccessToken(invalidToken))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessage("Invalid access token");
        }

        @Test
        @DisplayName("Should throw exception when refresh token is used for access validation")
        void shouldThrowExceptionWhenRefreshTokenIsUsedForAccessValidation() {
            // Given
            String refreshToken = jwtService.generateRefreshToken("user123");

            // When & Then
            assertThatThrownBy(() -> jwtService.validateAccessToken(refreshToken))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessage("Invalid token type");
        }

        @Test
        @DisplayName("Should include email in access token claims")
        void shouldIncludeEmailInAccessTokenClaims() {
            // Given
            String userId = "user123";
            String email = "test@example.com";
            String token = jwtService.generateAccessToken(userId, email);

            // When
            Claims claims = jwtService.extractAllClaims(token);

            // Then
            assertThat(claims.get("email")).isEqualTo(email);
            assertThat(claims.get("type")).isEqualTo("access");
            assertThat(claims.getSubject()).isEqualTo(userId);
        }

        @Test
        @DisplayName("Should have correct expiration time")
        void shouldHaveCorrectExpirationTime() {
            // Given
            String userId = "user123";
            String email = "test@example.com";
            long beforeGeneration = System.currentTimeMillis();

            // When
            String token = jwtService.generateAccessToken(userId, email);
            Claims claims = jwtService.extractAllClaims(token);

            // Then
            long expectedExpiration = beforeGeneration + accessTokenExpiration;
            long actualExpiration = claims.getExpiration().getTime();
            
            // Allow for small timing differences (within 1 second)
            assertThat(actualExpiration).isBetween(expectedExpiration - 1000, expectedExpiration + 1000);
        }
    }

    @Nested
    @DisplayName("Refresh Token Tests")
    class RefreshTokenTests {

        @Test
        @DisplayName("Should generate valid refresh token")
        void shouldGenerateValidRefreshToken() {
            // Given
            String userId = "user123";

            // When
            String token = jwtService.generateRefreshToken(userId);

            // Then
            assertThat(token).isNotNull().isNotEmpty();
            
            String[] tokenParts = token.split("\\.");
            assertThat(tokenParts).hasSize(3);
        }

        @Test
        @DisplayName("Should validate valid refresh token and return user ID")
        void shouldValidateValidRefreshTokenAndReturnUserId() {
            // Given
            String userId = "user123";
            String token = jwtService.generateRefreshToken(userId);

            // When
            String extractedUserId = jwtService.validateRefreshToken(token);

            // Then
            assertThat(extractedUserId).isEqualTo(userId);
        }

        @Test
        @DisplayName("Should throw exception for invalid refresh token")
        void shouldThrowExceptionForInvalidRefreshToken() {
            // Given
            String invalidToken = "invalid.refresh.token";

            // When & Then
            assertThatThrownBy(() -> jwtService.validateRefreshToken(invalidToken))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessage("Invalid refresh token");
        }

        @Test
        @DisplayName("Should throw exception when access token is used for refresh validation")
        void shouldThrowExceptionWhenAccessTokenIsUsedForRefreshValidation() {
            // Given
            String accessToken = jwtService.generateAccessToken("user123", "test@example.com");

            // When & Then
            assertThatThrownBy(() -> jwtService.validateRefreshToken(accessToken))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessage("Invalid token type");
        }

        @Test
        @DisplayName("Should have correct type in refresh token claims")
        void shouldHaveCorrectTypeInRefreshTokenClaims() {
            // Given
            String userId = "user123";
            String token = jwtService.generateRefreshToken(userId);

            // When
            Claims claims = jwtService.extractAllClaims(token);

            // Then
            assertThat(claims.get("type")).isEqualTo("refresh");
            assertThat(claims.getSubject()).isEqualTo(userId);
            assertThat(claims.get("email")).isNull(); // Refresh tokens don't contain email
        }

        @Test
        @DisplayName("Should have longer expiration time than access token")
        void shouldHaveLongerExpirationTimeThanAccessToken() {
            // Given
            String userId = "user123";
            long beforeGeneration = System.currentTimeMillis();

            // When
            String refreshToken = jwtService.generateRefreshToken(userId);
            Claims refreshClaims = jwtService.extractAllClaims(refreshToken);

            // Then
            long expectedExpiration = beforeGeneration + refreshTokenExpiration;
            long actualExpiration = refreshClaims.getExpiration().getTime();
            
            assertThat(actualExpiration).isBetween(expectedExpiration - 1000, expectedExpiration + 1000);
            assertThat(refreshTokenExpiration).isGreaterThan(accessTokenExpiration);
        }
    }

    @Nested
    @DisplayName("Token Extraction Tests")
    class TokenExtractionTests {

        @Test
        @DisplayName("Should extract user ID from valid token")
        void shouldExtractUserIdFromValidToken() {
            // Given
            String userId = "user123";
            String token = jwtService.generateAccessToken(userId, "test@example.com");

            // When
            String extractedUserId = jwtService.extractUserId(token);

            // Then
            assertThat(extractedUserId).isEqualTo(userId);
        }

        @Test
        @DisplayName("Should throw exception when extracting user ID from invalid token")
        void shouldThrowExceptionWhenExtractingUserIdFromInvalidToken() {
            // Given
            String invalidToken = "invalid.token.format";

            // When & Then
            assertThatThrownBy(() -> jwtService.extractUserId(invalidToken))
                    .isInstanceOf(JwtException.class);
        }
    }

    @Nested
    @DisplayName("Legacy Testing Methods Tests")
    class LegacyTestingMethodsTests {

        @Test
        @DisplayName("Should generate token using legacy method")
        void shouldGenerateTokenUsingLegacyMethod() {
            // Given
            String username = "testuser";

            // When
            String token = jwtService.generateToken(username);

            // Then
            assertThat(token).isNotNull().isNotEmpty();
            assertThat(jwtService.getUsernameFromToken(token)).isEqualTo(username);
        }

        @Test
        @DisplayName("Should validate token using legacy method")
        void shouldValidateTokenUsingLegacyMethod() {
            // Given
            String username = "testuser";
            String token = jwtService.generateToken(username);

            // When
            boolean isValid = jwtService.validateToken(token);

            // Then
            assertThat(isValid).isTrue();
        }

        @Test
        @DisplayName("Should return false for invalid token using legacy validation")
        void shouldReturnFalseForInvalidTokenUsingLegacyValidation() {
            // Given
            String invalidToken = "invalid.token.format";

            // When
            boolean isValid = jwtService.validateToken(invalidToken);

            // Then
            assertThat(isValid).isFalse();
        }

        @Test
        @DisplayName("Should get username from token using legacy method")
        void shouldGetUsernameFromTokenUsingLegacyMethod() {
            // Given
            String username = "testuser";
            String token = jwtService.generateToken(username);

            // When
            String extractedUsername = jwtService.getUsernameFromToken(token);

            // Then
            assertThat(extractedUsername).isEqualTo(username);
        }

        @Test
        @DisplayName("Should return null when getting username from invalid token")
        void shouldReturnNullWhenGettingUsernameFromInvalidToken() {
            // Given
            String invalidToken = "invalid.token.format";

            // When
            String extractedUsername = jwtService.getUsernameFromToken(invalidToken);

            // Then
            assertThat(extractedUsername).isNull();
        }

        @Test
        @DisplayName("Should detect expired token")
        void shouldDetectExpiredToken() {
            // Given
            JwtServiceImpl shortExpirationService = new JwtServiceImpl(testSecret, 1L, refreshTokenExpiration);
            String token = shortExpirationService.generateToken("testuser");
            
            // Wait for token to expire
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // When
            boolean isExpired = shortExpirationService.isTokenExpired(token);

            // Then
            assertThat(isExpired).isTrue();
        }

        @Test
        @DisplayName("Should return true for expired check on invalid token")
        void shouldReturnTrueForExpiredCheckOnInvalidToken() {
            // Given
            String invalidToken = "invalid.token.format";

            // When
            boolean isExpired = jwtService.isTokenExpired(invalidToken);

            // Then
            assertThat(isExpired).isTrue();
        }
    }

    @Nested
    @DisplayName("Security Tests")
    class SecurityTests {

        @Test
        @DisplayName("Should not accept tokens signed with different secret")
        void shouldNotAcceptTokensSignedWithDifferentSecret() {
            // Given
            String differentSecret = "different-secret-key-for-jwt-token-generation-must-be-at-least-256-bits";
            JwtServiceImpl differentService = new JwtServiceImpl(differentSecret, accessTokenExpiration, refreshTokenExpiration);
            String token = differentService.generateAccessToken("user123", "test@example.com");

            // When & Then
            assertThatThrownBy(() -> jwtService.validateAccessToken(token))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessage("Invalid access token");
        }

        @Test
        @DisplayName("Should handle malformed tokens")
        void shouldHandleMalformedTokens() {
            // Given
            String malformedToken = "this.is.malformed";

            // When & Then
            assertThatThrownBy(() -> jwtService.validateAccessToken(malformedToken))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessage("Invalid access token");
        }

        @Test
        @DisplayName("Should handle empty token")
        void shouldHandleEmptyToken() {
            // Given
            String emptyToken = "";

            // When & Then
            assertThatThrownBy(() -> jwtService.validateAccessToken(emptyToken))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessage("Invalid access token");
        }

        @Test
        @DisplayName("Should handle null token")
        void shouldHandleNullToken() {
            // Given
            String nullToken = null;

            // When & Then
            assertThatThrownBy(() -> jwtService.validateAccessToken(nullToken))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessage("Invalid access token");
        }
    }

    @Nested
    @DisplayName("Token Structure Tests")
    class TokenStructureTests {

        @Test
        @DisplayName("Should generate different tokens for different users")
        void shouldGenerateDifferentTokensForDifferentUsers() {
            // Given
            String user1 = "user1";
            String user2 = "user2";
            String email = "test@example.com";

            // When
            String token1 = jwtService.generateAccessToken(user1, email);
            String token2 = jwtService.generateAccessToken(user2, email);

            // Then
            assertThat(token1).isNotEqualTo(token2);
        }

        @Test
        @DisplayName("Should generate different tokens for same user at different times")
        void shouldGenerateDifferentTokensForSameUserAtDifferentTimes() {
            // Given
            String userId = "user123";
            String email = "test@example.com";

            // When
            String token1 = jwtService.generateAccessToken(userId, email);
            
            try {
                Thread.sleep(10); // Small delay to ensure different timestamps
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            String token2 = jwtService.generateAccessToken(userId, email);

            // Then
            assertThat(token1).isNotEqualTo(token2);
        }

        @Test
        @DisplayName("Should have consistent token format")
        void shouldHaveConsistentTokenFormat() {
            // Given
            String userId = "user123";
            String email = "test@example.com";

            // When
            String token = jwtService.generateAccessToken(userId, email);

            // Then
            assertThat(token).matches("^[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+$");
        }
    }

    @Nested
    @DisplayName("Claims Validation Tests")
    class ClaimsValidationTests {

        @Test
        @DisplayName("Should have issued at claim")
        void shouldHaveIssuedAtClaim() {
            // Given
            String userId = "user123";
            String email = "test@example.com";
            long beforeGeneration = System.currentTimeMillis();

            // When
            String token = jwtService.generateAccessToken(userId, email);
            Claims claims = jwtService.extractAllClaims(token);

            // Then
            assertThat(claims.getIssuedAt()).isNotNull();
            assertThat(claims.getIssuedAt().getTime()).isBetween(beforeGeneration - 1000, System.currentTimeMillis());
        }

        @Test
        @DisplayName("Should validate expiration is after issued at")
        void shouldValidateExpirationIsAfterIssuedAt() {
            // Given
            String userId = "user123";
            String email = "test@example.com";

            // When
            String token = jwtService.generateAccessToken(userId, email);
            Claims claims = jwtService.extractAllClaims(token);

            // Then
            assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
            
            long timeDifference = claims.getExpiration().getTime() - claims.getIssuedAt().getTime();
            assertThat(timeDifference).isBetween(accessTokenExpiration - 1000, accessTokenExpiration + 1000);
        }

        @Test
        @DisplayName("Should handle special characters in email")
        void shouldHandleSpecialCharactersInEmail() {
            // Given
            String userId = "user123";
            String emailWithSpecialChars = "test+special.chars@example-domain.com";

            // When
            String token = jwtService.generateAccessToken(userId, emailWithSpecialChars);
            Claims claims = jwtService.extractAllClaims(token);

            // Then
            assertThat(claims.get("email")).isEqualTo(emailWithSpecialChars);
            assertThat(jwtService.validateAccessToken(token)).isEqualTo(userId);
        }

        @Test
        @DisplayName("Should handle Unicode characters in user ID")
        void shouldHandleUnicodeCharactersInUserId() {
            // Given
            String userIdWithUnicode = "사용자123";
            String email = "test@example.com";

            // When
            String token = jwtService.generateAccessToken(userIdWithUnicode, email);

            // Then
            assertThat(jwtService.validateAccessToken(token)).isEqualTo(userIdWithUnicode);
            assertThat(jwtService.extractUserId(token)).isEqualTo(userIdWithUnicode);
        }
    }
}