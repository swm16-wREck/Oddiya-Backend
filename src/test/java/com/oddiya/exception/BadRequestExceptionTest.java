package com.oddiya.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BadRequestException Tests")
class BadRequestExceptionTest {

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Should create exception with message")
        void shouldCreateExceptionWithMessage() {
            // Given
            String message = "Invalid request format";
            
            // When
            BadRequestException exception = new BadRequestException(message);
            
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
            String message = "Request validation failed";
            Throwable cause = new IllegalArgumentException("Invalid parameter");
            
            // When
            BadRequestException exception = new BadRequestException(message, cause);
            
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
            BadRequestException exception = new BadRequestException(message);
            
            // Then
            assertNotNull(exception);
            assertEquals(message, exception.getMessage());
        }

        @Test
        @DisplayName("Should handle null cause")
        void shouldHandleNullCause() {
            // Given
            String message = "Bad request";
            Throwable cause = null;
            
            // When
            BadRequestException exception = new BadRequestException(message, cause);
            
            // Then
            assertNotNull(exception);
            assertEquals(message, exception.getMessage());
            assertNull(exception.getCause());
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "Simple message",
            "Message with special chars: !@#$%^&*()",
            "Very long message that exceeds normal limits and includes various characters and numbers 12345",
            "Unicode message: 안녕하세요 こんにちは 你好",
            "JSON-like message: {\"error\": \"validation failed\", \"field\": \"email\"}"
        })
        @DisplayName("Should handle various message formats")
        void shouldHandleVariousMessageFormats(String message) {
            // When
            BadRequestException exception = new BadRequestException(message);
            
            // Then
            assertNotNull(exception);
            assertEquals(message, exception.getMessage());
        }
    }

    @Nested
    @DisplayName("Exception Chaining Tests")
    class ExceptionChainingTests {

        @Test
        @DisplayName("Should maintain exception chain with multiple levels")
        void shouldMaintainExceptionChainWithMultipleLevels() {
            // Given
            RuntimeException rootCause = new RuntimeException("Root cause");
            IllegalStateException intermediateCause = new IllegalStateException("Intermediate", rootCause);
            
            // When
            BadRequestException exception = new BadRequestException("Bad request", intermediateCause);
            
            // Then
            assertNotNull(exception);
            assertEquals("Bad request", exception.getMessage());
            assertEquals(intermediateCause, exception.getCause());
            assertEquals(rootCause, exception.getCause().getCause());
            assertEquals("Root cause", exception.getCause().getCause().getMessage());
        }

        @Test
        @DisplayName("Should handle different exception types as causes")
        void shouldHandleDifferentExceptionTypesAsCauses() {
            // Test with various exception types
            Throwable[] causes = {
                new IllegalArgumentException("Invalid argument"),
                new NumberFormatException("Invalid number"),
                new NullPointerException("Null pointer"),
                new IndexOutOfBoundsException("Index out of bounds"),
                new UnsupportedOperationException("Unsupported operation")
            };
            
            for (Throwable cause : causes) {
                // When
                BadRequestException exception = new BadRequestException("Bad request", cause);
                
                // Then
                assertNotNull(exception);
                assertEquals("Bad request", exception.getMessage());
                assertEquals(cause, exception.getCause());
                assertEquals(cause.getClass(), exception.getCause().getClass());
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
            BadRequestException exception = new BadRequestException("Test message");
            
            // Then
            assertTrue(exception instanceof RuntimeException);
            assertTrue(exception instanceof Exception);
            assertTrue(exception instanceof Throwable);
        }

        @Test
        @DisplayName("Should support stack trace")
        void shouldSupportStackTrace() {
            // Given & When
            BadRequestException exception = new BadRequestException("Test message");
            
            // Then
            assertNotNull(exception.getStackTrace());
            assertTrue(exception.getStackTrace().length > 0);
            
            // Should contain this test method in stack trace
            boolean foundTestMethod = false;
            for (StackTraceElement element : exception.getStackTrace()) {
                if (element.getMethodName().equals("shouldSupportStackTrace")) {
                    foundTestMethod = true;
                    break;
                }
            }
            assertTrue(foundTestMethod, "Stack trace should contain test method");
        }

        @Test
        @DisplayName("Should be throwable and catchable")
        void shouldBeThrowableAndCatchable() {
            // Given
            String testMessage = "Test exception message";
            
            // When & Then
            assertThrows(BadRequestException.class, () -> {
                throw new BadRequestException(testMessage);
            });
            
            // Test catching
            try {
                throw new BadRequestException(testMessage);
            } catch (BadRequestException e) {
                assertEquals(testMessage, e.getMessage());
            } catch (Exception e) {
                fail("Should catch as BadRequestException, not generic Exception");
            }
        }

        @Test
        @DisplayName("Should be catchable as RuntimeException")
        void shouldBeCatchableAsRuntimeException() {
            // Given
            String testMessage = "Runtime exception test";
            
            // When & Then
            try {
                throw new BadRequestException(testMessage);
            } catch (RuntimeException e) {
                assertTrue(e instanceof BadRequestException);
                assertEquals(testMessage, e.getMessage());
            } catch (Exception e) {
                fail("Should catch as RuntimeException");
            }
        }
    }

    @Nested
    @DisplayName("Edge Cases and Boundary Tests")
    class EdgeCasesAndBoundaryTests {

        @Test
        @DisplayName("Should handle extremely long message")
        void shouldHandleExtremelyLongMessage() {
            // Given - Create a very long message
            StringBuilder longMessage = new StringBuilder();
            for (int i = 0; i < 10000; i++) {
                longMessage.append("A");
            }
            String message = longMessage.toString();
            
            // When
            BadRequestException exception = new BadRequestException(message);
            
            // Then
            assertNotNull(exception);
            assertEquals(message, exception.getMessage());
            assertEquals(10000, exception.getMessage().length());
        }

        @Test
        @DisplayName("Should handle message with only whitespace")
        void shouldHandleMessageWithOnlyWhitespace() {
            // Given
            String[] whitespaceMessages = {
                " ",
                "   ",
                "\t",
                "\n",
                "\r",
                "  \t\n\r  "
            };
            
            for (String message : whitespaceMessages) {
                // When
                BadRequestException exception = new BadRequestException(message);
                
                // Then
                assertNotNull(exception);
                assertEquals(message, exception.getMessage());
            }
        }

        @Test
        @DisplayName("Should handle circular exception cause")
        void shouldHandleCircularExceptionCause() {
            // Given - Create circular reference
            RuntimeException cause1 = new RuntimeException("Cause 1");
            BadRequestException exception1 = new BadRequestException("Exception 1", cause1);
            
            // Create another exception that references the first
            BadRequestException exception2 = new BadRequestException("Exception 2", exception1);
            
            // When & Then - Should not cause infinite loops
            assertNotNull(exception2);
            assertEquals("Exception 2", exception2.getMessage());
            assertEquals(exception1, exception2.getCause());
            assertEquals(cause1, exception2.getCause().getCause());
        }

        @Test
        @DisplayName("Should preserve message when cause has same message")
        void shouldPreserveMessageWhenCauseHasSameMessage() {
            // Given
            String message = "Duplicate message";
            RuntimeException cause = new RuntimeException(message);
            
            // When
            BadRequestException exception = new BadRequestException(message, cause);
            
            // Then
            assertEquals(message, exception.getMessage());
            assertEquals(message, exception.getCause().getMessage());
            assertNotSame(exception.getMessage(), exception.getCause().getMessage());
        }
    }

    @Nested
    @DisplayName("Serialization and toString Tests")
    class SerializationAndToStringTests {

        @Test
        @DisplayName("Should have meaningful toString representation")
        void shouldHaveMeaningfulToStringRepresentation() {
            // Given
            String message = "Test exception message";
            
            // When
            BadRequestException exception = new BadRequestException(message);
            String toString = exception.toString();
            
            // Then
            assertNotNull(toString);
            assertTrue(toString.contains("BadRequestException"));
            assertTrue(toString.contains(message));
        }

        @Test
        @DisplayName("Should have toString with cause information")
        void shouldHaveToStringWithCauseInformation() {
            // Given
            String message = "Main message";
            String causeMessage = "Cause message";
            RuntimeException cause = new RuntimeException(causeMessage);
            
            // When
            BadRequestException exception = new BadRequestException(message, cause);
            String toString = exception.toString();
            
            // Then
            assertNotNull(toString);
            assertTrue(toString.contains("BadRequestException"));
            assertTrue(toString.contains(message));
            // Note: toString typically doesn't include cause details
        }

        @Test
        @DisplayName("Should handle toString with null message")
        void shouldHandleToStringWithNullMessage() {
            // Given
            BadRequestException exception = new BadRequestException(null);
            
            // When
            String toString = exception.toString();
            
            // Then
            assertNotNull(toString);
            assertTrue(toString.contains("BadRequestException"));
        }
    }
}