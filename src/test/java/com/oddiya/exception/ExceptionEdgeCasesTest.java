package com.oddiya.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringWriter;
import java.io.PrintWriter;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Exception Edge Cases and Advanced Scenarios Tests")
class ExceptionEdgeCasesTest {

    @Nested
    @DisplayName("Null Message and Cause Handling")
    class NullMessageAndCauseHandling {

        @Test
        @DisplayName("Should handle all exceptions with null messages consistently")
        void shouldHandleAllExceptionsWithNullMessagesConsistently() {
            // Given & When
            BadRequestException badRequestException = new BadRequestException(null);
            NotFoundException notFoundException = new NotFoundException(null);
            ResourceNotFoundException resourceNotFoundException = new ResourceNotFoundException((String) null);
            UnauthorizedException unauthorizedException = new UnauthorizedException(null);
            
            // Then
            assertNull(badRequestException.getMessage());
            assertNull(notFoundException.getMessage());
            assertNull(resourceNotFoundException.getMessage());
            assertNull(unauthorizedException.getMessage());
            
            // All should still be valid exception instances
            assertNotNull(badRequestException);
            assertNotNull(notFoundException);
            assertNotNull(resourceNotFoundException);
            assertNotNull(unauthorizedException);
        }

        @Test
        @DisplayName("Should handle all exceptions with null causes consistently")
        void shouldHandleAllExceptionsWithNullCausesConsistently() {
            // Given & When
            BadRequestException badRequestException = new BadRequestException("Message", null);
            NotFoundException notFoundException = new NotFoundException("Message", null);
            UnauthorizedException unauthorizedException = new UnauthorizedException("Message", null);
            
            // Then
            assertEquals("Message", badRequestException.getMessage());
            assertEquals("Message", notFoundException.getMessage());
            assertEquals("Message", unauthorizedException.getMessage());
            
            assertNull(badRequestException.getCause());
            assertNull(notFoundException.getCause());
            assertNull(unauthorizedException.getCause());
        }

        @Test
        @DisplayName("Should handle ResourceNotFoundException with null formatted parameters")
        void shouldHandleResourceNotFoundExceptionWithNullFormattedParameters() {
            // Given & When
            ResourceNotFoundException allNull = new ResourceNotFoundException(null, null, null);
            ResourceNotFoundException resourceNull = new ResourceNotFoundException(null, "field", "value");
            ResourceNotFoundException fieldNull = new ResourceNotFoundException("Resource", null, "value");
            ResourceNotFoundException valueNull = new ResourceNotFoundException("Resource", "field", null);
            
            // Then
            assertEquals("null not found with null: 'null'", allNull.getMessage());
            assertEquals("null not found with field: 'value'", resourceNull.getMessage());
            assertEquals("Resource not found with null: 'value'", fieldNull.getMessage());
            assertEquals("Resource not found with field: 'null'", valueNull.getMessage());
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"", " ", "\t", "\n", "\r\n"})
        @DisplayName("Should handle various null and whitespace scenarios")
        void shouldHandleVariousNullAndWhitespaceScenarios(String input) {
            // When
            BadRequestException exception = new BadRequestException(input);
            
            // Then
            assertEquals(input, exception.getMessage());
            assertNotNull(exception);
        }
    }

    @Nested
    @DisplayName("Exception Chaining and Cause Analysis")
    class ExceptionChainingAndCauseAnalysis {

        @Test
        @DisplayName("Should handle very deep exception chains")
        void shouldHandleVeryDeepExceptionChains() {
            // Given - Create a deep exception chain
            Throwable rootCause = new RuntimeException("Level 0: Root cause");
            Throwable currentCause = rootCause;
            
            // Create a chain of 100 exceptions
            for (int i = 1; i <= 100; i++) {
                currentCause = new RuntimeException("Level " + i + ": Intermediate cause", currentCause);
            }
            
            // When - Create our exception with the deep chain
            BadRequestException exception = new BadRequestException("Top level exception", currentCause);
            
            // Then - Verify the chain is maintained
            assertNotNull(exception.getCause());
            assertEquals("Level 100: Intermediate cause", exception.getCause().getMessage());
            
            // Navigate to the root cause
            Throwable cause = exception;
            int depth = 0;
            while (cause.getCause() != null) {
                cause = cause.getCause();
                depth++;
            }
            
            assertEquals(101, depth); // 100 intermediate + 1 root
            assertEquals("Level 0: Root cause", cause.getMessage());
        }

        @Test
        @DisplayName("Should handle circular exception references safely")
        void shouldHandleCircularExceptionReferencesSafely() {
            // Given - Create circular reference (although not recommended in practice)
            RuntimeException exception1 = new RuntimeException("Exception 1");
            RuntimeException exception2 = new RuntimeException("Exception 2", exception1);
            RuntimeException exception3 = new RuntimeException("Exception 3", exception2);
            
            // Create the circular reference by setting exception1's cause to exception3
            try {
                exception1.initCause(exception3);
            } catch (IllegalStateException e) {
                // Expected - cannot create circular reference
                assertTrue(e.getMessage().contains("circular reference"));
            }
            
            // When - Create our exception with linear chain
            UnauthorizedException exception = new UnauthorizedException("Top level", exception3);
            
            // Then - Should handle normally
            assertNotNull(exception);
            assertEquals("Top level", exception.getMessage());
            assertEquals(exception3, exception.getCause());
        }

        @Test
        @DisplayName("Should preserve cause information through exception transformation")
        void shouldPreserveCauseInformationThroughExceptionTransformation() {
            // Given - Original exception chain
            NumberFormatException parseException = new NumberFormatException("Invalid number format: abc");
            IllegalArgumentException validationException = new IllegalArgumentException("Parameter validation failed", parseException);
            
            // When - Transform to our domain exceptions
            BadRequestException domainException = new BadRequestException("Request processing failed", validationException);
            
            // Then - Verify cause chain is preserved
            assertEquals("Request processing failed", domainException.getMessage());
            assertEquals(validationException, domainException.getCause());
            assertEquals(parseException, domainException.getCause().getCause());
            assertEquals("Invalid number format: abc", domainException.getCause().getCause().getMessage());
            
            // Verify cause types are preserved
            assertTrue(domainException.getCause() instanceof IllegalArgumentException);
            assertTrue(domainException.getCause().getCause() instanceof NumberFormatException);
        }

        @Test
        @DisplayName("Should handle mixed exception type chains")
        void shouldHandleMixedExceptionTypeChains() {
            // Given - Chain with different exception types
            SecurityException securityException = new SecurityException("Security violation");
            IllegalStateException stateException = new IllegalStateException("Invalid state", securityException);
            NullPointerException nullException = new NullPointerException("Null pointer");
            nullException.initCause(stateException);
            
            // When - Create our domain exception
            UnauthorizedException authException = new UnauthorizedException("Authentication failed", nullException);
            
            // Then - Verify mixed type chain
            assertTrue(authException instanceof RuntimeException);
            assertTrue(authException.getCause() instanceof NullPointerException);
            assertTrue(authException.getCause().getCause() instanceof IllegalStateException);
            assertTrue(authException.getCause().getCause().getCause() instanceof SecurityException);
            
            // Verify messages are preserved
            assertEquals("Authentication failed", authException.getMessage());
            assertNull(authException.getCause().getMessage()); // NPE typically has null message
            assertEquals("Invalid state", authException.getCause().getCause().getMessage());
            assertEquals("Security violation", authException.getCause().getCause().getCause().getMessage());
        }
    }

    @Nested
    @DisplayName("Concurrent Exception Handling")
    class ConcurrentExceptionHandling {

        @Test
        @DisplayName("Should handle concurrent exception creation safely")
        void shouldHandleConcurrentExceptionCreationSafely() throws InterruptedException {
            // Given
            int threadCount = 1000;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch completionLatch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            
            ExecutorService executor = Executors.newFixedThreadPool(50);
            
            // When - Create exceptions concurrently
            for (int i = 0; i < threadCount; i++) {
                final int index = i;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        
                        // Create different types of exceptions
                        switch (index % 4) {
                            case 0:
                                new BadRequestException("Concurrent bad request " + index);
                                break;
                            case 1:
                                new NotFoundException("Concurrent not found " + index);
                                break;
                            case 2:
                                new ResourceNotFoundException("Resource" + index, "id", String.valueOf(index));
                                break;
                            case 3:
                                new UnauthorizedException("Concurrent unauthorized " + index);
                                break;
                        }
                        
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        // Should not happen in normal exception creation
                        e.printStackTrace();
                    } finally {
                        completionLatch.countDown();
                    }
                });
            }
            
            // Start all threads simultaneously
            startLatch.countDown();
            
            // Wait for completion
            assertTrue(completionLatch.await(30, TimeUnit.SECONDS));
            executor.shutdown();
            
            // Then - All exceptions should be created successfully
            assertEquals(threadCount, successCount.get());
        }

        @Test
        @DisplayName("Should handle concurrent exception chain creation")
        void shouldHandleConcurrentExceptionChainCreation() throws InterruptedException {
            // Given
            int threadCount = 100;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch completionLatch = new CountDownLatch(threadCount);
            Exception[] exceptions = new Exception[threadCount];
            
            ExecutorService executor = Executors.newFixedThreadPool(20);
            
            // When - Create exception chains concurrently
            for (int i = 0; i < threadCount; i++) {
                final int index = i;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        
                        // Create exception chain
                        RuntimeException rootCause = new RuntimeException("Root cause " + index);
                        IllegalStateException intermediateCause = new IllegalStateException("Intermediate " + index, rootCause);
                        exceptions[index] = new BadRequestException("Top level " + index, intermediateCause);
                        
                    } catch (Exception e) {
                        exceptions[index] = e; // Capture any unexpected exceptions
                    } finally {
                        completionLatch.countDown();
                    }
                });
            }
            
            startLatch.countDown();
            assertTrue(completionLatch.await(30, TimeUnit.SECONDS));
            executor.shutdown();
            
            // Then - Verify all chains are created correctly
            for (int i = 0; i < threadCount; i++) {
                assertNotNull(exceptions[i]);
                assertTrue(exceptions[i] instanceof BadRequestException);
                assertEquals("Top level " + i, exceptions[i].getMessage());
                assertNotNull(exceptions[i].getCause());
                assertEquals("Intermediate " + i, exceptions[i].getCause().getMessage());
                assertEquals("Root cause " + i, exceptions[i].getCause().getCause().getMessage());
            }
        }
    }

    @Nested
    @DisplayName("Stack Trace and Debugging Information")
    class StackTraceAndDebuggingInformation {

        @Test
        @DisplayName("Should preserve complete stack trace information")
        void shouldPreserveCompleteStackTraceInformation() {
            // When
            BadRequestException exception = createExceptionInNestedMethod();
            
            // Then
            StackTraceElement[] stackTrace = exception.getStackTrace();
            assertNotNull(stackTrace);
            assertTrue(stackTrace.length > 0);
            
            // Should contain the nested method in stack trace
            boolean foundNestedMethod = false;
            boolean foundTestMethod = false;
            
            for (StackTraceElement element : stackTrace) {
                if (element.getMethodName().equals("createExceptionInNestedMethod")) {
                    foundNestedMethod = true;
                }
                if (element.getMethodName().equals("shouldPreserveCompleteStackTraceInformation")) {
                    foundTestMethod = true;
                }
            }
            
            assertTrue(foundNestedMethod, "Stack trace should contain nested method");
            assertTrue(foundTestMethod, "Stack trace should contain test method");
        }

        private BadRequestException createExceptionInNestedMethod() {
            return createExceptionInDeeperMethod();
        }

        private BadRequestException createExceptionInDeeperMethod() {
            return new BadRequestException("Exception created in nested method");
        }

        @Test
        @DisplayName("Should handle stack trace modification and printing")
        void shouldHandleStackTraceModificationAndPrinting() {
            // Given
            ResourceNotFoundException exception = new ResourceNotFoundException("Test", "id", "123");
            StackTraceElement[] originalTrace = exception.getStackTrace();
            
            // When - Modify stack trace
            StackTraceElement customElement = new StackTraceElement(
                "com.custom.Class", "customMethod", "CustomFile.java", 42);
            StackTraceElement[] modifiedTrace = new StackTraceElement[originalTrace.length + 1];
            modifiedTrace[0] = customElement;
            System.arraycopy(originalTrace, 0, modifiedTrace, 1, originalTrace.length);
            
            exception.setStackTrace(modifiedTrace);
            
            // Then - Verify modification
            StackTraceElement[] newTrace = exception.getStackTrace();
            assertEquals(modifiedTrace.length, newTrace.length);
            assertEquals("com.custom.Class", newTrace[0].getClassName());
            assertEquals("customMethod", newTrace[0].getMethodName());
            assertEquals(42, newTrace[0].getLineNumber());
        }

        @Test
        @DisplayName("Should handle stack trace printing to different outputs")
        void shouldHandleStackTracePrintingToDifferentOutputs() {
            // Given
            UnauthorizedException exception = new UnauthorizedException("Test exception");
            
            // When - Print to ByteArrayOutputStream
            ByteArrayOutputStream byteOutput = new ByteArrayOutputStream();
            PrintStream printStream = new PrintStream(byteOutput);
            exception.printStackTrace(printStream);
            
            // Then - Verify output contains expected information
            String output = byteOutput.toString();
            assertFalse(output.isEmpty());
            assertTrue(output.contains("UnauthorizedException"));
            assertTrue(output.contains("Test exception"));
            assertTrue(output.contains("at "));
            
            // When - Print to StringWriter
            StringWriter stringWriter = new StringWriter();
            PrintWriter printWriter = new PrintWriter(stringWriter);
            exception.printStackTrace(printWriter);
            
            // Then - Verify string output
            String stringOutput = stringWriter.toString();
            assertFalse(stringOutput.isEmpty());
            assertTrue(stringOutput.contains("UnauthorizedException"));
            assertTrue(stringOutput.contains("Test exception"));
        }

        @Test
        @DisplayName("Should handle suppressed exception information")
        void shouldHandleSuppressedExceptionInformation() {
            // Given
            NotFoundException mainException = new NotFoundException("Main exception");
            RuntimeException suppressed1 = new RuntimeException("First suppressed");
            IllegalStateException suppressed2 = new IllegalStateException("Second suppressed");
            
            // When - Add suppressed exceptions
            mainException.addSuppressed(suppressed1);
            mainException.addSuppressed(suppressed2);
            
            // Then - Verify suppressed exceptions
            Throwable[] suppressedExceptions = mainException.getSuppressed();
            assertNotNull(suppressedExceptions);
            assertEquals(2, suppressedExceptions.length);
            assertEquals(suppressed1, suppressedExceptions[0]);
            assertEquals(suppressed2, suppressedExceptions[1]);
            
            // Verify stack trace includes suppressed information
            StringWriter stringWriter = new StringWriter();
            PrintWriter printWriter = new PrintWriter(stringWriter);
            mainException.printStackTrace(printWriter);
            
            String output = stringWriter.toString();
            assertTrue(output.contains("Suppressed:"));
            assertTrue(output.contains("First suppressed"));
            assertTrue(output.contains("Second suppressed"));
        }
    }

    @Nested
    @DisplayName("Exception Serialization and Cloning")
    class ExceptionSerializationAndCloning {

        @Test
        @DisplayName("Should handle toString representation consistently")
        void shouldHandleToStringRepresentationConsistently() {
            // Given
            String message = "Test exception message";
            BadRequestException exception = new BadRequestException(message);
            
            // When
            String toString = exception.toString();
            
            // Then
            assertNotNull(toString);
            assertFalse(toString.isEmpty());
            assertTrue(toString.contains("BadRequestException"));
            assertTrue(toString.contains(message));
            
            // Verify format consistency
            assertTrue(toString.startsWith("com.oddiya.exception.BadRequestException"));
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
            // Should handle null message gracefully
        }

        @Test
        @DisplayName("Should handle toString with cause information")
        void shouldHandleToStringWithCauseInformation() {
            // Given
            RuntimeException cause = new RuntimeException("Cause message");
            UnauthorizedException exception = new UnauthorizedException("Main message", cause);
            
            // When
            String toString = exception.toString();
            String causeToString = exception.getCause().toString();
            
            // Then
            assertNotNull(toString);
            assertNotNull(causeToString);
            assertTrue(toString.contains("UnauthorizedException"));
            assertTrue(toString.contains("Main message"));
            assertTrue(causeToString.contains("RuntimeException"));
            assertTrue(causeToString.contains("Cause message"));
        }

        @Test
        @DisplayName("Should handle equals and hashCode behavior")
        void shouldHandleEqualsAndHashCodeBehavior() {
            // Given
            String message = "Test message";
            BadRequestException exception1 = new BadRequestException(message);
            BadRequestException exception2 = new BadRequestException(message);
            BadRequestException exception3 = new BadRequestException("Different message");
            
            // Then - Exceptions are not equal even with same message (object identity)
            assertNotEquals(exception1, exception2);
            assertNotEquals(exception1, exception3);
            assertNotEquals(exception2, exception3);
            
            // But they are equal to themselves
            assertEquals(exception1, exception1);
            assertEquals(exception2, exception2);
            assertEquals(exception3, exception3);
            
            // Hash codes may or may not be equal (implementation dependent)
            // Just verify they don't throw exceptions
            assertDoesNotThrow(() -> {
                int hash1 = exception1.hashCode();
                int hash2 = exception2.hashCode();
                int hash3 = exception3.hashCode();
            });
        }

        @Test
        @DisplayName("Should handle exception message immutability")
        void shouldHandleExceptionMessageImmutability() {
            // Given
            StringBuilder mutableMessage = new StringBuilder("Original message");
            String originalString = mutableMessage.toString();
            
            // When - Create exception with string from mutable source
            ResourceNotFoundException exception = new ResourceNotFoundException(originalString);
            
            // Modify the original source
            mutableMessage.append(" - Modified");
            
            // Then - Exception message should remain unchanged
            assertEquals("Original message", exception.getMessage());
            assertNotEquals(mutableMessage.toString(), exception.getMessage());
        }
    }

    @Nested
    @DisplayName("Performance and Memory Tests")
    class PerformanceAndMemoryTests {

        @Test
        @DisplayName("Should handle large number of exception creations efficiently")
        void shouldHandleLargeNumberOfExceptionCreationsEfficiently() {
            // Given
            int exceptionCount = 10000;
            long startTime = System.currentTimeMillis();
            
            // When - Create many exceptions
            Exception[] exceptions = new Exception[exceptionCount];
            for (int i = 0; i < exceptionCount; i++) {
                switch (i % 4) {
                    case 0:
                        exceptions[i] = new BadRequestException("Exception " + i);
                        break;
                    case 1:
                        exceptions[i] = new NotFoundException("Exception " + i);
                        break;
                    case 2:
                        exceptions[i] = new ResourceNotFoundException("Resource" + i, "id", String.valueOf(i));
                        break;
                    case 3:
                        exceptions[i] = new UnauthorizedException("Exception " + i);
                        break;
                }
            }
            
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            
            // Then - Should complete in reasonable time (less than 5 seconds)
            assertTrue(duration < 5000, "Creating " + exceptionCount + " exceptions took " + duration + "ms");
            
            // Verify all exceptions were created
            for (int i = 0; i < exceptionCount; i++) {
                assertNotNull(exceptions[i]);
                assertTrue(exceptions[i].getMessage().contains(String.valueOf(i)));
            }
        }

        @Test
        @DisplayName("Should handle exceptions with very large messages efficiently")
        void shouldHandleExceptionsWithVeryLargeMessagesEfficiently() {
            // Given - Create very large message
            StringBuilder largeMessage = new StringBuilder();
            for (int i = 0; i < 100000; i++) {
                largeMessage.append("Large message content ").append(i).append(". ");
            }
            
            long startTime = System.currentTimeMillis();
            
            // When - Create exception with large message
            BadRequestException exception = new BadRequestException(largeMessage.toString());
            
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            
            // Then - Should handle efficiently
            assertTrue(duration < 1000, "Creating exception with large message took " + duration + "ms");
            assertEquals(largeMessage.toString(), exception.getMessage());
            assertTrue(exception.getMessage().length() > 1000000);
        }

        @Test
        @DisplayName("Should handle deep stack traces efficiently")
        void shouldHandleDeepStackTracesEfficiently() {
            // Given & When - Create exception through deep call stack
            long startTime = System.currentTimeMillis();
            
            Exception exception = createDeepStackTraceException(100);
            
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            
            // Then - Should handle deep traces efficiently
            assertTrue(duration < 1000, "Creating deep stack trace took " + duration + "ms");
            assertNotNull(exception);
            assertTrue(exception.getStackTrace().length > 100);
        }

        private Exception createDeepStackTraceException(int depth) {
            if (depth <= 0) {
                return new BadRequestException("Deep stack trace exception");
            }
            return createDeepStackTraceException(depth - 1);
        }
    }
}