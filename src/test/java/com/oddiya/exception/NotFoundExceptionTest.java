package com.oddiya.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("NotFoundException Tests")
class NotFoundExceptionTest {

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Should create exception with message")
        void shouldCreateExceptionWithMessage() {
            // Given
            String message = "Resource not found";
            
            // When
            NotFoundException exception = new NotFoundException(message);
            
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
            String message = "User not found";
            Throwable cause = new IllegalStateException("Database connection lost");
            
            // When
            NotFoundException exception = new NotFoundException(message, cause);
            
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
            NotFoundException exception = new NotFoundException(message);
            
            // Then
            assertNotNull(exception);
            assertEquals(message, exception.getMessage());
        }

        @Test
        @DisplayName("Should handle null cause")
        void shouldHandleNullCause() {
            // Given
            String message = "Resource not found";
            Throwable cause = null;
            
            // When
            NotFoundException exception = new NotFoundException(message, cause);
            
            // Then
            assertNotNull(exception);
            assertEquals(message, exception.getMessage());
            assertNull(exception.getCause());
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "User not found",
            "Travel plan not found",
            "Place with ID 123 not found",
            "Review not found for user 456",
            "Itinerary item not found in plan 789",
            "Video not found with status ACTIVE",
            "Saved plan not found for user and travel plan"
        })
        @DisplayName("Should handle various not found scenarios")
        void shouldHandleVariousNotFoundScenarios(String message) {
            // When
            NotFoundException exception = new NotFoundException(message);
            
            // Then
            assertNotNull(exception);
            assertEquals(message, exception.getMessage());
        }
    }

    @Nested
    @DisplayName("Exception Chaining Tests")
    class ExceptionChainingTests {

        @Test
        @DisplayName("Should maintain exception chain with database-related causes")
        void shouldMaintainExceptionChainWithDatabaseRelatedCauses() {
            // Given - Simulate database-related exception chain
            RuntimeException sqlException = new RuntimeException("Connection timeout");
            IllegalStateException repositoryException = new IllegalStateException("Repository access failed", sqlException);
            
            // When
            NotFoundException exception = new NotFoundException("Entity not found", repositoryException);
            
            // Then
            assertNotNull(exception);
            assertEquals("Entity not found", exception.getMessage());
            assertEquals(repositoryException, exception.getCause());
            assertEquals(sqlException, exception.getCause().getCause());
            assertEquals("Connection timeout", exception.getCause().getCause().getMessage());
        }

        @Test
        @DisplayName("Should handle service layer exception chaining")
        void shouldHandleServiceLayerExceptionChaining() {
            // Given - Simulate service layer exception chain
            Throwable[] serviceCauses = {
                new IllegalArgumentException("Invalid user ID"),
                new SecurityException("Access denied"),
                new NullPointerException("Required parameter is null"),
                new IllegalStateException("Service unavailable")
            };
            
            for (Throwable cause : serviceCauses) {
                // When
                NotFoundException exception = new NotFoundException("Service operation failed", cause);
                
                // Then
                assertNotNull(exception);
                assertEquals("Service operation failed", exception.getMessage());
                assertEquals(cause, exception.getCause());
                assertEquals(cause.getClass(), exception.getCause().getClass());
            }
        }

        @Test
        @DisplayName("Should support nested NotFoundException chain")
        void shouldSupportNestedNotFoundExceptionChain() {
            // Given - Chain of NotFoundException instances
            NotFoundException innerException = new NotFoundException("Inner resource not found");
            NotFoundException middleException = new NotFoundException("Middle resource not found", innerException);
            
            // When
            NotFoundException outerException = new NotFoundException("Outer resource not found", middleException);
            
            // Then
            assertNotNull(outerException);
            assertEquals("Outer resource not found", outerException.getMessage());
            assertEquals(middleException, outerException.getCause());
            assertEquals(innerException, outerException.getCause().getCause());
            assertEquals("Inner resource not found", outerException.getCause().getCause().getMessage());
        }
    }

    @Nested
    @DisplayName("Runtime Exception Behavior Tests")
    class RuntimeExceptionBehaviorTests {

        @Test
        @DisplayName("Should be instance of RuntimeException")
        void shouldBeInstanceOfRuntimeException() {
            // Given
            NotFoundException exception = new NotFoundException("Test message");
            
            // Then
            assertTrue(exception instanceof RuntimeException);
            assertTrue(exception instanceof Exception);
            assertTrue(exception instanceof Throwable);
        }

        @Test
        @DisplayName("Should support stack trace operations")
        void shouldSupportStackTraceOperations() {
            // Given & When
            NotFoundException exception = new NotFoundException("Stack trace test");
            
            // Then
            assertNotNull(exception.getStackTrace());
            assertTrue(exception.getStackTrace().length > 0);
            
            // Verify this method appears in stack trace
            boolean foundTestMethod = false;
            for (StackTraceElement element : exception.getStackTrace()) {
                if (element.getMethodName().equals("shouldSupportStackTraceOperations")) {
                    foundTestMethod = true;
                    assertEquals("NotFoundExceptionTest", element.getClassName().substring(
                        element.getClassName().lastIndexOf('.') + 1));
                    break;
                }
            }
            assertTrue(foundTestMethod, "Stack trace should contain test method");
        }

        @Test
        @DisplayName("Should be throwable in different contexts")
        void shouldBeThrowableInDifferentContexts() {
            // Test throwing and catching
            assertThrows(NotFoundException.class, () -> {
                throw new NotFoundException("Direct throw test");
            });
            
            // Test method that throws
            assertThrows(NotFoundException.class, this::methodThatThrows);
            
            // Test catching specific vs general
            try {
                throw new NotFoundException("Catch test");
            } catch (NotFoundException e) {
                assertEquals("Catch test", e.getMessage());
            } catch (RuntimeException e) {
                fail("Should catch as NotFoundException, not generic RuntimeException");
            }
        }

        private void methodThatThrows() {
            throw new NotFoundException("Method throw test");
        }

        @Test
        @DisplayName("Should support exception suppression")
        void shouldSupportExceptionSuppression() {
            // Given
            NotFoundException mainException = new NotFoundException("Main exception");
            RuntimeException suppressedException = new RuntimeException("Suppressed exception");
            
            // When
            mainException.addSuppressed(suppressedException);
            
            // Then
            Throwable[] suppressed = mainException.getSuppressed();
            assertNotNull(suppressed);
            assertEquals(1, suppressed.length);
            assertEquals(suppressedException, suppressed[0]);
        }
    }

    @Nested
    @DisplayName("Use Case Specific Tests")
    class UseCaseSpecificTests {

        @Test
        @DisplayName("Should handle user not found scenarios")
        void shouldHandleUserNotFoundScenarios() {
            // Given
            String[] userMessages = {
                "User not found",
                "User with ID 123 not found",
                "User with email john@example.com not found",
                "User profile not found"
            };
            
            for (String message : userMessages) {
                // When
                NotFoundException exception = new NotFoundException(message);
                
                // Then
                assertNotNull(exception);
                assertTrue(message.toLowerCase().contains("user"));
                assertEquals(message, exception.getMessage());
            }
        }

        @Test
        @DisplayName("Should handle travel plan not found scenarios")
        void shouldHandleTravelPlanNotFoundScenarios() {
            // Given
            Long planId = 456L;
            Long userId = 123L;
            
            String[] planMessages = {
                "Travel plan not found",
                "Travel plan with ID " + planId + " not found",
                "Travel plan not found for user " + userId,
                "Active travel plan not found",
                "Published travel plan not found"
            };
            
            for (String message : planMessages) {
                // When
                NotFoundException exception = new NotFoundException(message);
                
                // Then
                assertNotNull(exception);
                assertTrue(message.toLowerCase().contains("travel plan") || 
                          message.toLowerCase().contains("plan"));
                assertEquals(message, exception.getMessage());
            }
        }

        @Test
        @DisplayName("Should handle place not found scenarios")
        void shouldHandlePlaceNotFoundScenarios() {
            // Given
            String[] placeMessages = {
                "Place not found",
                "Place with coordinates not found",
                "Tourist attraction not found",
                "Restaurant not found in area"
            };
            
            for (String message : placeMessages) {
                // When
                NotFoundException exception = new NotFoundException(message);
                
                // Then
                assertNotNull(exception);
                assertEquals(message, exception.getMessage());
            }
        }

        @Test
        @DisplayName("Should handle review and rating not found scenarios")
        void shouldHandleReviewAndRatingNotFoundScenarios() {
            // Given
            String[] reviewMessages = {
                "Review not found",
                "Review not found for place and user",
                "Rating not found",
                "User review not found for this place"
            };
            
            for (String message : reviewMessages) {
                // When
                NotFoundException exception = new NotFoundException(message);
                
                // Then
                assertNotNull(exception);
                assertTrue(message.toLowerCase().contains("review") || 
                          message.toLowerCase().contains("rating"));
                assertEquals(message, exception.getMessage());
            }
        }
    }

    @Nested
    @DisplayName("Edge Cases and Boundary Tests")
    class EdgeCasesAndBoundaryTests {

        @Test
        @DisplayName("Should handle concurrent exception creation")
        void shouldHandleConcurrentExceptionCreation() throws InterruptedException {
            // Given
            int threadCount = 100;
            Thread[] threads = new Thread[threadCount];
            NotFoundException[] exceptions = new NotFoundException[threadCount];
            
            // When - Create exceptions concurrently
            for (int i = 0; i < threadCount; i++) {
                final int index = i;
                threads[i] = new Thread(() -> {
                    exceptions[index] = new NotFoundException("Concurrent exception " + index);
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
                assertEquals("Concurrent exception " + i, exceptions[i].getMessage());
            }
        }

        @Test
        @DisplayName("Should handle exception with special characters")
        void shouldHandleExceptionWithSpecialCharacters() {
            // Given
            String[] specialMessages = {
                "Resource not found: 特殊文字",
                "Не найдено: русский текст",
                "Ressource introuvable: français",
                "リソースが見つかりません",
                "منبع پیدا نشد",
                "Resource with ID [123] not found {error: 404}",
                "Path '/api/v1/users/invalid' not found",
                "Query 'SELECT * FROM users WHERE id = ?' returned no results"
            };
            
            for (String message : specialMessages) {
                // When
                NotFoundException exception = new NotFoundException(message);
                
                // Then
                assertNotNull(exception);
                assertEquals(message, exception.getMessage());
            }
        }

        @Test
        @DisplayName("Should handle very long exception messages")
        void shouldHandleVeryLongExceptionMessages() {
            // Given - Create progressively longer messages
            int[] lengths = {100, 1000, 10000};
            
            for (int length : lengths) {
                StringBuilder longMessage = new StringBuilder("Resource not found: ");
                for (int i = 0; i < length; i++) {
                    longMessage.append("x");
                }
                
                // When
                NotFoundException exception = new NotFoundException(longMessage.toString());
                
                // Then
                assertNotNull(exception);
                assertEquals(longMessage.toString(), exception.getMessage());
                assertTrue(exception.getMessage().length() > length);
            }
        }

        @Test
        @DisplayName("Should maintain message integrity with cause chain")
        void shouldMaintainMessageIntegrityWithCauseChain() {
            // Given
            String originalMessage = "Original not found message";
            String causeMessage = "Database connection failed";
            String nestedCauseMessage = "Network timeout";
            
            RuntimeException nestedCause = new RuntimeException(nestedCauseMessage);
            IllegalStateException cause = new IllegalStateException(causeMessage, nestedCause);
            
            // When
            NotFoundException exception = new NotFoundException(originalMessage, cause);
            
            // Then
            assertEquals(originalMessage, exception.getMessage());
            assertEquals(causeMessage, exception.getCause().getMessage());
            assertEquals(nestedCauseMessage, exception.getCause().getCause().getMessage());
            
            // Verify messages are independent
            assertNotEquals(exception.getMessage(), exception.getCause().getMessage());
            assertNotEquals(exception.getCause().getMessage(), 
                           exception.getCause().getCause().getMessage());
        }

        @Test
        @DisplayName("Should handle toString with complex cause chain")
        void shouldHandleToStringWithComplexCauseChain() {
            // Given
            RuntimeException deepCause = new RuntimeException("Deep cause");
            IllegalArgumentException middleCause = new IllegalArgumentException("Middle cause", deepCause);
            
            // When
            NotFoundException exception = new NotFoundException("Top level message", middleCause);
            String toString = exception.toString();
            
            // Then
            assertNotNull(toString);
            assertTrue(toString.contains("NotFoundException"));
            assertTrue(toString.contains("Top level message"));
            assertFalse(toString.isEmpty());
        }
    }
}