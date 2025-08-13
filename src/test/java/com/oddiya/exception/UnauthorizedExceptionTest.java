package com.oddiya.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UnauthorizedException Tests")
class UnauthorizedExceptionTest {

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Should create exception with message")
        void shouldCreateExceptionWithMessage() {
            // Given
            String message = "Access denied - insufficient permissions";
            
            // When
            UnauthorizedException exception = new UnauthorizedException(message);
            
            // Then
            assertNotNull(exception);
            assertEquals(message, exception.getMessage());
            assertNull(exception.getCause());
            assertTrue(exception instanceof RuntimeException);
        }

        @Test
        @DisplayName("Should create exception with message and cause")
        void shouldCreateExceptionWithMessageAndCause() {
            // Given
            String message = "Authentication failed";
            Throwable cause = new SecurityException("Invalid credentials");
            
            // When
            UnauthorizedException exception = new UnauthorizedException(message, cause);
            
            // Then
            assertNotNull(exception);
            assertEquals(message, exception.getMessage());
            assertEquals(cause, exception.getCause());
            assertSame(cause, exception.getCause());
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("Should handle null and empty messages")
        void shouldHandleNullAndEmptyMessages(String message) {
            // When
            UnauthorizedException exception = new UnauthorizedException(message);
            
            // Then
            assertNotNull(exception);
            assertEquals(message, exception.getMessage());
        }

        @Test
        @DisplayName("Should handle null cause")
        void shouldHandleNullCause() {
            // Given
            String message = "Unauthorized access";
            Throwable cause = null;
            
            // When
            UnauthorizedException exception = new UnauthorizedException(message, cause);
            
            // Then
            assertNotNull(exception);
            assertEquals(message, exception.getMessage());
            assertNull(exception.getCause());
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "Access denied",
            "Invalid JWT token",
            "Token has expired",
            "Insufficient permissions for this operation",
            "User not authenticated",
            "Invalid API key",
            "Session has expired",
            "OAuth token is invalid or expired"
        })
        @DisplayName("Should handle various authentication and authorization scenarios")
        void shouldHandleVariousAuthenticationAndAuthorizationScenarios(String message) {
            // When
            UnauthorizedException exception = new UnauthorizedException(message);
            
            // Then
            assertNotNull(exception);
            assertEquals(message, exception.getMessage());
        }
    }

    @Nested
    @DisplayName("Security-Related Exception Chaining Tests")
    class SecurityRelatedExceptionChainingTests {

        @Test
        @DisplayName("Should maintain exception chain with security-related causes")
        void shouldMaintainExceptionChainWithSecurityRelatedCauses() {
            // Given - Simulate security-related exception chain
            RuntimeException tokenException = new RuntimeException("JWT signature verification failed");
            SecurityException securityException = new SecurityException("Token validation failed", tokenException);
            
            // When
            UnauthorizedException exception = new UnauthorizedException("Authentication failed", securityException);
            
            // Then
            assertNotNull(exception);
            assertEquals("Authentication failed", exception.getMessage());
            assertEquals(securityException, exception.getCause());
            assertEquals(tokenException, exception.getCause().getCause());
            assertEquals("JWT signature verification failed", exception.getCause().getCause().getMessage());
        }

        @Test
        @DisplayName("Should handle authentication service exception chaining")
        void shouldHandleAuthenticationServiceExceptionChaining() {
            // Given - Common authentication-related exception types
            Throwable[] authCauses = {
                new SecurityException("Invalid credentials"),
                new IllegalArgumentException("Malformed token"),
                new IllegalStateException("Authentication service unavailable"),
                new NullPointerException("Required authentication parameter is null"),
                new RuntimeException("External OAuth service failed")
            };
            
            for (Throwable cause : authCauses) {
                // When
                UnauthorizedException exception = new UnauthorizedException("Authentication operation failed", cause);
                
                // Then
                assertNotNull(exception);
                assertEquals("Authentication operation failed", exception.getMessage());
                assertEquals(cause, exception.getCause());
                assertEquals(cause.getClass(), exception.getCause().getClass());
            }
        }

        @Test
        @DisplayName("Should support nested authentication exception chain")
        void shouldSupportNestedAuthenticationExceptionChain() {
            // Given - Chain of authentication-related exceptions
            UnauthorizedException innerException = new UnauthorizedException("Token expired");
            UnauthorizedException middleException = new UnauthorizedException("Session invalid", innerException);
            
            // When
            UnauthorizedException outerException = new UnauthorizedException("Access denied", middleException);
            
            // Then
            assertNotNull(outerException);
            assertEquals("Access denied", outerException.getMessage());
            assertEquals(middleException, outerException.getCause());
            assertEquals(innerException, outerException.getCause().getCause());
            assertEquals("Token expired", outerException.getCause().getCause().getMessage());
        }

        @Test
        @DisplayName("Should handle JWT-related exception chaining")
        void shouldHandleJWTRelatedExceptionChaining() {
            // Given - JWT-specific exception scenarios
            RuntimeException signatureException = new RuntimeException("Invalid signature");
            IllegalArgumentException malformedJwtException = new IllegalArgumentException("Malformed JWT token", signatureException);
            
            // When
            UnauthorizedException exception = new UnauthorizedException("JWT authentication failed", malformedJwtException);
            
            // Then
            assertNotNull(exception);
            assertEquals("JWT authentication failed", exception.getMessage());
            assertEquals(malformedJwtException, exception.getCause());
            assertEquals(signatureException, exception.getCause().getCause());
            assertTrue(exception.getMessage().contains("JWT"));
        }
    }

    @Nested
    @DisplayName("Authentication Scenario Tests")
    class AuthenticationScenarioTests {

        @Test
        @DisplayName("Should handle JWT token related scenarios")
        void shouldHandleJWTTokenRelatedScenarios() {
            // Given
            String[] jwtMessages = {
                "JWT token is missing",
                "JWT token has expired",
                "JWT signature verification failed",
                "JWT token is malformed",
                "JWT token is blacklisted",
                "JWT issuer is not trusted",
                "JWT audience validation failed"
            };
            
            for (String message : jwtMessages) {
                // When
                UnauthorizedException exception = new UnauthorizedException(message);
                
                // Then
                assertNotNull(exception);
                assertTrue(message.toUpperCase().contains("JWT") || message.toLowerCase().contains("token"));
                assertEquals(message, exception.getMessage());
            }
        }

        @Test
        @DisplayName("Should handle OAuth authentication scenarios")
        void shouldHandleOAuthAuthenticationScenarios() {
            // Given
            String[] oauthMessages = {
                "OAuth token is invalid",
                "OAuth token has expired",
                "OAuth scope is insufficient",
                "OAuth client is not authorized",
                "OAuth redirect URI mismatch",
                "OAuth state parameter is invalid",
                "OAuth authorization code is invalid"
            };
            
            for (String message : oauthMessages) {
                // When
                UnauthorizedException exception = new UnauthorizedException(message);
                
                // Then
                assertNotNull(exception);
                assertTrue(message.toUpperCase().contains("OAUTH"));
                assertEquals(message, exception.getMessage());
            }
        }

        @Test
        @DisplayName("Should handle API key authentication scenarios")
        void shouldHandleAPIKeyAuthenticationScenarios() {
            // Given
            String[] apiKeyMessages = {
                "API key is missing",
                "API key is invalid",
                "API key has expired",
                "API key is suspended",
                "API key rate limit exceeded",
                "API key does not have required permissions"
            };
            
            for (String message : apiKeyMessages) {
                // When
                UnauthorizedException exception = new UnauthorizedException(message);
                
                // Then
                assertNotNull(exception);
                assertTrue(message.toUpperCase().contains("API KEY") || message.contains("API"));
                assertEquals(message, exception.getMessage());
            }
        }

        @Test
        @DisplayName("Should handle session-based authentication scenarios")
        void shouldHandleSessionBasedAuthenticationScenarios() {
            // Given
            String[] sessionMessages = {
                "Session has expired",
                "Session is invalid",
                "No active session found",
                "Session token mismatch",
                "Session has been terminated",
                "Maximum session time exceeded"
            };
            
            for (String message : sessionMessages) {
                // When
                UnauthorizedException exception = new UnauthorizedException(message);
                
                // Then
                assertNotNull(exception);
                assertTrue(message.toLowerCase().contains("session"));
                assertEquals(message, exception.getMessage());
            }
        }

        @Test
        @DisplayName("Should handle permission and role-based scenarios")
        void shouldHandlePermissionAndRoleBasedScenarios() {
            // Given
            String[] permissionMessages = {
                "Insufficient permissions",
                "User role does not allow this operation",
                "Admin privileges required",
                "Access denied for this resource",
                "Operation requires elevated permissions",
                "User is not authorized for this action"
            };
            
            for (String message : permissionMessages) {
                // When
                UnauthorizedException exception = new UnauthorizedException(message);
                
                // Then
                assertNotNull(exception);
                assertTrue(message.toLowerCase().contains("permission") || 
                          message.toLowerCase().contains("role") || 
                          message.toLowerCase().contains("access") ||
                          message.toLowerCase().contains("authorized"));
                assertEquals(message, exception.getMessage());
            }
        }
    }

    @Nested
    @DisplayName("Runtime Exception Behavior Tests")
    class RuntimeExceptionBehaviorTests {

        @Test
        @DisplayName("Should be instance of RuntimeException")
        void shouldBeInstanceOfRuntimeException() {
            // Given
            UnauthorizedException exception = new UnauthorizedException("Test message");
            
            // Then
            assertTrue(exception instanceof RuntimeException);
            assertTrue(exception instanceof Exception);
            assertTrue(exception instanceof Throwable);
        }

        @Test
        @DisplayName("Should support stack trace operations")
        void shouldSupportStackTraceOperations() {
            // Given & When
            UnauthorizedException exception = new UnauthorizedException("Stack trace test");
            
            // Then
            assertNotNull(exception.getStackTrace());
            assertTrue(exception.getStackTrace().length > 0);
            
            // Verify this method appears in stack trace
            boolean foundTestMethod = false;
            for (StackTraceElement element : exception.getStackTrace()) {
                if (element.getMethodName().equals("shouldSupportStackTraceOperations")) {
                    foundTestMethod = true;
                    assertEquals("UnauthorizedExceptionTest", element.getClassName().substring(
                        element.getClassName().lastIndexOf('.') + 1));
                    break;
                }
            }
            assertTrue(foundTestMethod, "Stack trace should contain test method");
        }

        @Test
        @DisplayName("Should be throwable in authentication contexts")
        void shouldBeThrowableInAuthenticationContexts() {
            // Test direct throwing
            assertThrows(UnauthorizedException.class, () -> {
                throw new UnauthorizedException("Direct auth failure");
            });
            
            // Test method that throws in auth context
            assertThrows(UnauthorizedException.class, this::authenticateUser);
            
            // Test catching specific vs general
            try {
                throw new UnauthorizedException("Specific auth test");
            } catch (UnauthorizedException e) {
                assertEquals("Specific auth test", e.getMessage());
            } catch (RuntimeException e) {
                fail("Should catch as UnauthorizedException, not generic RuntimeException");
            }
        }

        private void authenticateUser() {
            throw new UnauthorizedException("User authentication failed");
        }

        @Test
        @DisplayName("Should support suppressed exception handling")
        void shouldSupportSuppressedExceptionHandling() {
            // Given
            UnauthorizedException mainException = new UnauthorizedException("Main auth failure");
            RuntimeException suppressedException1 = new RuntimeException("Cleanup failed");
            SecurityException suppressedException2 = new SecurityException("Audit log failed");
            
            // When
            mainException.addSuppressed(suppressedException1);
            mainException.addSuppressed(suppressedException2);
            
            // Then
            Throwable[] suppressed = mainException.getSuppressed();
            assertNotNull(suppressed);
            assertEquals(2, suppressed.length);
            assertEquals(suppressedException1, suppressed[0]);
            assertEquals(suppressedException2, suppressed[1]);
        }

        @Test
        @DisplayName("Should handle try-with-resources scenarios")
        void shouldHandleTryWithResourcesScenarios() {
            // Given - Simulate authentication resource that fails
            class MockAuthResource implements AutoCloseable {
                public void authenticate() throws UnauthorizedException {
                    throw new UnauthorizedException("Authentication resource failed");
                }
                
                @Override
                public void close() throws Exception {
                    throw new RuntimeException("Resource cleanup failed");
                }
            }
            
            // When & Then
            UnauthorizedException authException = assertThrows(UnauthorizedException.class, () -> {
                try (MockAuthResource resource = new MockAuthResource()) {
                    resource.authenticate();
                }
            });
            
            // Should have suppressed exception from close()
            assertEquals("Authentication resource failed", authException.getMessage());
            assertTrue(authException.getSuppressed().length > 0);
            assertEquals("Resource cleanup failed", authException.getSuppressed()[0].getMessage());
        }
    }

    @Nested
    @DisplayName("Security Context Tests")
    class SecurityContextTests {

        @Test
        @DisplayName("Should handle user context scenarios")
        void shouldHandleUserContextScenarios() {
            // Given - User context scenarios
            Long[] userIds = {123L, 456L, 789L};
            String[] userEmails = {"user1@test.com", "user2@test.com", "admin@test.com"};
            
            for (int i = 0; i < userIds.length; i++) {
                // When
                String message = String.format("User %d (%s) is not authorized", userIds[i], userEmails[i]);
                UnauthorizedException exception = new UnauthorizedException(message);
                
                // Then
                assertNotNull(exception);
                assertTrue(exception.getMessage().contains(userIds[i].toString()));
                assertTrue(exception.getMessage().contains(userEmails[i]));
                assertTrue(exception.getMessage().contains("not authorized"));
            }
        }

        @Test
        @DisplayName("Should handle resource access scenarios")
        void shouldHandleResourceAccessScenarios() {
            // Given - Resource access scenarios
            String[] resources = {
                "/api/v1/admin/users",
                "/api/v1/travel-plans/123",
                "/api/v1/users/456/profile",
                "/api/v1/places/789/reviews"
            };
            
            for (String resource : resources) {
                // When
                String message = String.format("Access denied to resource: %s", resource);
                UnauthorizedException exception = new UnauthorizedException(message);
                
                // Then
                assertNotNull(exception);
                assertTrue(exception.getMessage().contains(resource));
                assertTrue(exception.getMessage().contains("Access denied"));
            }
        }

        @Test
        @DisplayName("Should handle operation-specific authorization scenarios")
        void shouldHandleOperationSpecificAuthorizationScenarios() {
            // Given - Operation-specific scenarios
            String[] operations = {"READ", "WRITE", "DELETE", "UPDATE", "ADMIN"};
            String[] resources = {"TravelPlan", "User", "Place", "Review", "Video"};
            
            for (String operation : operations) {
                for (String resource : resources) {
                    // When
                    String message = String.format("User is not authorized to %s %s", operation, resource);
                    UnauthorizedException exception = new UnauthorizedException(message);
                    
                    // Then
                    assertNotNull(exception);
                    assertTrue(exception.getMessage().contains(operation));
                    assertTrue(exception.getMessage().contains(resource));
                    assertTrue(exception.getMessage().contains("not authorized"));
                }
            }
        }
    }

    @Nested
    @DisplayName("Edge Cases and Boundary Tests")
    class EdgeCasesAndBoundaryTests {

        @Test
        @DisplayName("Should handle concurrent authentication failures")
        void shouldHandleConcurrentAuthenticationFailures() throws InterruptedException {
            // Given
            int threadCount = 50;
            Thread[] threads = new Thread[threadCount];
            UnauthorizedException[] exceptions = new UnauthorizedException[threadCount];
            
            // When - Create exceptions concurrently
            for (int i = 0; i < threadCount; i++) {
                final int index = i;
                threads[i] = new Thread(() -> {
                    exceptions[index] = new UnauthorizedException("Concurrent auth failure " + index);
                });
                threads[i].start();
            }
            
            // Wait for all threads to complete
            for (Thread thread : threads) {
                thread.join();
            }
            
            // Then
            for (int i = 0; i < threadCount; i++) {
                assertNotNull(exceptions[i]);
                assertEquals("Concurrent auth failure " + i, exceptions[i].getMessage());
            }
        }

        @Test
        @DisplayName("Should handle authentication messages with sensitive data masking")
        void shouldHandleAuthenticationMessagesWithSensitiveDataMasking() {
            // Given - Simulate messages that might contain sensitive data
            String[] sensitiveMessages = {
                "Invalid password for user: [REDACTED]",
                "API key validation failed: ***...***",
                "Token signature mismatch: [MASKED]",
                "OAuth client secret invalid: [HIDDEN]"
            };
            
            for (String message : sensitiveMessages) {
                // When
                UnauthorizedException exception = new UnauthorizedException(message);
                
                // Then
                assertNotNull(exception);
                assertEquals(message, exception.getMessage());
                // Verify sensitive data is masked
                assertFalse(exception.getMessage().contains("password123"));
                assertFalse(exception.getMessage().contains("secret_key"));
                assertTrue(exception.getMessage().contains("[") || exception.getMessage().contains("*"));
            }
        }

        @Test
        @DisplayName("Should handle extremely long authentication error messages")
        void shouldHandleExtremelyLongAuthenticationErrorMessages() {
            // Given - Create a very long authentication error message
            StringBuilder longMessage = new StringBuilder("Authentication failed: ");
            for (int i = 0; i < 5000; i++) {
                longMessage.append("Invalid token ");
            }
            String message = longMessage.toString();
            
            // When
            UnauthorizedException exception = new UnauthorizedException(message);
            
            // Then
            assertNotNull(exception);
            assertEquals(message, exception.getMessage());
            assertTrue(exception.getMessage().length() > 50000);
            assertTrue(exception.getMessage().startsWith("Authentication failed:"));
        }

        @Test
        @DisplayName("Should handle multilingual authentication error messages")
        void shouldHandleMultilingualAuthenticationErrorMessages() {
            // Given - Authentication errors in different languages
            String[] multilingualMessages = {
                "인증 실패: 유효하지 않은 토큰", // Korean
                "認証に失敗しました：無効なトークン", // Japanese
                "Аутентификация не удалась: недействительный токен", // Russian
                "Falló la autenticación: token inválido", // Spanish
                "Échec de l'authentification : jeton invalide", // French
                "Autenticazione fallita: token non valido" // Italian
            };
            
            for (String message : multilingualMessages) {
                // When
                UnauthorizedException exception = new UnauthorizedException(message);
                
                // Then
                assertNotNull(exception);
                assertEquals(message, exception.getMessage());
            }
        }

        @Test
        @DisplayName("Should handle toString with authentication context")
        void shouldHandleToStringWithAuthenticationContext() {
            // Given
            UnauthorizedException exception = new UnauthorizedException("JWT token expired for user: admin@example.com");
            
            // When
            String toString = exception.toString();
            
            // Then
            assertNotNull(toString);
            assertTrue(toString.contains("UnauthorizedException"));
            assertTrue(toString.contains("JWT token expired"));
            assertFalse(toString.isEmpty());
        }

        @Test
        @DisplayName("Should maintain consistency across different authentication methods")
        void shouldMaintainConsistencyAcrossDifferentAuthenticationMethods() {
            // Given - Different authentication methods
            String[] authMethods = {"JWT", "OAuth", "API_KEY", "SESSION", "BASIC_AUTH"};
            
            for (String method : authMethods) {
                // When
                UnauthorizedException exception1 = new UnauthorizedException(method + " authentication failed");
                UnauthorizedException exception2 = new UnauthorizedException(method + " authentication failed");
                
                // Then
                assertEquals(exception1.getMessage(), exception2.getMessage());
                assertEquals(exception1.getClass(), exception2.getClass());
                assertNotSame(exception1, exception2); // Different instances
            }
        }
    }
}