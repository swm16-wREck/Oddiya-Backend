package com.oddiya.exception;

import com.oddiya.dto.response.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.core.MethodParameter;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@DisplayName("GlobalExceptionHandler Tests")
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler globalExceptionHandler;

    @Mock
    private MethodParameter methodParameter;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        globalExceptionHandler = new GlobalExceptionHandler();
    }

    @Nested
    @DisplayName("ResourceNotFoundException Handler Tests")
    class ResourceNotFoundExceptionHandlerTests {

        @Test
        @DisplayName("Should handle ResourceNotFoundException and return 404 with correct response")
        void shouldHandleResourceNotFoundExceptionAndReturn404() {
            // Given
            String errorMessage = "User not found with id: '123'";
            ResourceNotFoundException exception = new ResourceNotFoundException(errorMessage);
            
            // When
            ResponseEntity<ApiResponse<Void>> response = globalExceptionHandler
                .handleResourceNotFoundException(exception);
            
            // Then
            assertNotNull(response);
            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
            
            ApiResponse<Void> responseBody = response.getBody();
            assertNotNull(responseBody);
            assertFalse(responseBody.isSuccess());
            assertNull(responseBody.getData());
            
            ApiResponse.ErrorDetail error = responseBody.getError();
            assertNotNull(error);
            assertEquals("RESOURCE_NOT_FOUND", error.getCode());
            assertEquals(errorMessage, error.getMessage());
            assertNotNull(error.getTimestamp());
            assertTrue(error.getTimestamp().isBefore(LocalDateTime.now().plusSeconds(1)));
        }

        @Test
        @DisplayName("Should handle ResourceNotFoundException with formatted message")
        void shouldHandleResourceNotFoundExceptionWithFormattedMessage() {
            // Given
            ResourceNotFoundException exception = new ResourceNotFoundException("TravelPlan", "id", "456");
            String expectedMessage = "TravelPlan not found with id: '456'";
            
            // When
            ResponseEntity<ApiResponse<Void>> response = globalExceptionHandler
                .handleResourceNotFoundException(exception);
            
            // Then
            assertNotNull(response);
            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
            
            ApiResponse<Void> responseBody = response.getBody();
            assertNotNull(responseBody);
            assertEquals("RESOURCE_NOT_FOUND", responseBody.getError().getCode());
            assertEquals(expectedMessage, responseBody.getError().getMessage());
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "User not found",
            "Travel plan does not exist",
            "Place with specified coordinates not found",
            "Review not found for this user and place"
        })
        @DisplayName("Should handle various ResourceNotFoundException messages")
        void shouldHandleVariousResourceNotFoundExceptionMessages(String message) {
            // Given
            ResourceNotFoundException exception = new ResourceNotFoundException(message);
            
            // When
            ResponseEntity<ApiResponse<Void>> response = globalExceptionHandler
                .handleResourceNotFoundException(exception);
            
            // Then
            assertNotNull(response);
            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
            assertEquals(message, response.getBody().getError().getMessage());
        }

        @Test
        @DisplayName("Should handle ResourceNotFoundException with null message")
        void shouldHandleResourceNotFoundExceptionWithNullMessage() {
            // Given
            ResourceNotFoundException exception = new ResourceNotFoundException((String) null);
            
            // When
            ResponseEntity<ApiResponse<Void>> response = globalExceptionHandler
                .handleResourceNotFoundException(exception);
            
            // Then
            assertNotNull(response);
            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
            assertNull(response.getBody().getError().getMessage());
        }

        @Test
        @DisplayName("Should handle ResourceNotFoundException with cause")
        void shouldHandleResourceNotFoundExceptionWithCause() {
            // Given
            RuntimeException cause = new RuntimeException("Database connection failed");
            ResourceNotFoundException exception = new ResourceNotFoundException("Resource not found");
            exception.initCause(cause);
            
            // When
            ResponseEntity<ApiResponse<Void>> response = globalExceptionHandler
                .handleResourceNotFoundException(exception);
            
            // Then
            assertNotNull(response);
            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
            assertEquals("Resource not found", response.getBody().getError().getMessage());
            // Note: Cause details are not exposed in the response for security
        }
    }

    @Nested
    @DisplayName("UnauthorizedException Handler Tests")
    class UnauthorizedExceptionHandlerTests {

        @Test
        @DisplayName("Should handle UnauthorizedException and return 401 with correct response")
        void shouldHandleUnauthorizedExceptionAndReturn401() {
            // Given
            String errorMessage = "Invalid JWT token";
            UnauthorizedException exception = new UnauthorizedException(errorMessage);
            
            // When
            ResponseEntity<ApiResponse<Void>> response = globalExceptionHandler
                .handleUnauthorizedException(exception);
            
            // Then
            assertNotNull(response);
            assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
            
            ApiResponse<Void> responseBody = response.getBody();
            assertNotNull(responseBody);
            assertFalse(responseBody.isSuccess());
            assertNull(responseBody.getData());
            
            ApiResponse.ErrorDetail error = responseBody.getError();
            assertNotNull(error);
            assertEquals("UNAUTHORIZED", error.getCode());
            assertEquals(errorMessage, error.getMessage());
            assertNotNull(error.getTimestamp());
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "Access denied",
            "Token has expired",
            "Invalid credentials",
            "Insufficient permissions",
            "Authentication required"
        })
        @DisplayName("Should handle various UnauthorizedException messages")
        void shouldHandleVariousUnauthorizedExceptionMessages(String message) {
            // Given
            UnauthorizedException exception = new UnauthorizedException(message);
            
            // When
            ResponseEntity<ApiResponse<Void>> response = globalExceptionHandler
                .handleUnauthorizedException(exception);
            
            // Then
            assertNotNull(response);
            assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
            assertEquals("UNAUTHORIZED", response.getBody().getError().getCode());
            assertEquals(message, response.getBody().getError().getMessage());
        }

        @Test
        @DisplayName("Should handle UnauthorizedException with security context")
        void shouldHandleUnauthorizedExceptionWithSecurityContext() {
            // Given
            String securityMessage = "User 'admin@test.com' does not have permission to access resource '/api/admin'";
            UnauthorizedException exception = new UnauthorizedException(securityMessage);
            
            // When
            ResponseEntity<ApiResponse<Void>> response = globalExceptionHandler
                .handleUnauthorizedException(exception);
            
            // Then
            assertNotNull(response);
            assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
            assertEquals(securityMessage, response.getBody().getError().getMessage());
        }

        @Test
        @DisplayName("Should handle UnauthorizedException with null message")
        void shouldHandleUnauthorizedExceptionWithNullMessage() {
            // Given
            UnauthorizedException exception = new UnauthorizedException(null);
            
            // When
            ResponseEntity<ApiResponse<Void>> response = globalExceptionHandler
                .handleUnauthorizedException(exception);
            
            // Then
            assertNotNull(response);
            assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
            assertNull(response.getBody().getError().getMessage());
        }

        @Test
        @DisplayName("Should handle UnauthorizedException with cause chain")
        void shouldHandleUnauthorizedExceptionWithCauseChain() {
            // Given
            SecurityException rootCause = new SecurityException("Token signature invalid");
            IllegalStateException cause = new IllegalStateException("Authentication service unavailable", rootCause);
            UnauthorizedException exception = new UnauthorizedException("Authentication failed", cause);
            
            // When
            ResponseEntity<ApiResponse<Void>> response = globalExceptionHandler
                .handleUnauthorizedException(exception);
            
            // Then
            assertNotNull(response);
            assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
            assertEquals("Authentication failed", response.getBody().getError().getMessage());
            // Verify cause details are not exposed
            assertNotNull(exception.getCause());
        }
    }

    @Nested
    @DisplayName("BadRequestException Handler Tests")
    class BadRequestExceptionHandlerTests {

        @Test
        @DisplayName("Should handle BadRequestException and return 400 with correct response")
        void shouldHandleBadRequestExceptionAndReturn400() {
            // Given
            String errorMessage = "Invalid request format";
            BadRequestException exception = new BadRequestException(errorMessage);
            
            // When
            ResponseEntity<ApiResponse<Void>> response = globalExceptionHandler
                .handleBadRequestException(exception);
            
            // Then
            assertNotNull(response);
            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            
            ApiResponse<Void> responseBody = response.getBody();
            assertNotNull(responseBody);
            assertFalse(responseBody.isSuccess());
            assertNull(responseBody.getData());
            
            ApiResponse.ErrorDetail error = responseBody.getError();
            assertNotNull(error);
            assertEquals("BAD_REQUEST", error.getCode());
            assertEquals(errorMessage, error.getMessage());
            assertNotNull(error.getTimestamp());
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "Invalid JSON format",
            "Required field is missing",
            "Invalid date format",
            "Parameter value out of range",
            "Malformed request body"
        })
        @DisplayName("Should handle various BadRequestException messages")
        void shouldHandleVariousBadRequestExceptionMessages(String message) {
            // Given
            BadRequestException exception = new BadRequestException(message);
            
            // When
            ResponseEntity<ApiResponse<Void>> response = globalExceptionHandler
                .handleBadRequestException(exception);
            
            // Then
            assertNotNull(response);
            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertEquals("BAD_REQUEST", response.getBody().getError().getCode());
            assertEquals(message, response.getBody().getError().getMessage());
        }

        @Test
        @DisplayName("Should handle BadRequestException with validation context")
        void shouldHandleBadRequestExceptionWithValidationContext() {
            // Given
            String validationMessage = "Email format is invalid: 'not-an-email'";
            BadRequestException exception = new BadRequestException(validationMessage);
            
            // When
            ResponseEntity<ApiResponse<Void>> response = globalExceptionHandler
                .handleBadRequestException(exception);
            
            // Then
            assertNotNull(response);
            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertEquals(validationMessage, response.getBody().getError().getMessage());
        }

        @Test
        @DisplayName("Should handle BadRequestException with cause")
        void shouldHandleBadRequestExceptionWithCause() {
            // Given
            NumberFormatException cause = new NumberFormatException("Invalid number format");
            BadRequestException exception = new BadRequestException("Invalid numeric parameter", cause);
            
            // When
            ResponseEntity<ApiResponse<Void>> response = globalExceptionHandler
                .handleBadRequestException(exception);
            
            // Then
            assertNotNull(response);
            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertEquals("Invalid numeric parameter", response.getBody().getError().getMessage());
        }
    }

    @Nested
    @DisplayName("MethodArgumentNotValidException Handler Tests")
    class MethodArgumentNotValidExceptionHandlerTests {

        @Test
        @DisplayName("Should handle MethodArgumentNotValidException and return validation errors")
        void shouldHandleMethodArgumentNotValidExceptionAndReturnValidationErrors() throws NoSuchMethodException {
            // Given
            Method method = this.getClass().getMethod("dummyMethod", String.class);
            when(methodParameter.getMethod()).thenReturn(method);
            when(methodParameter.getParameterIndex()).thenReturn(0);
            
            BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "testObject");
            bindingResult.addError(new FieldError("testObject", "email", "Email is required"));
            bindingResult.addError(new FieldError("testObject", "name", "Name must be at least 2 characters"));
            
            MethodArgumentNotValidException exception = new MethodArgumentNotValidException(methodParameter, bindingResult);
            
            // When
            ResponseEntity<ApiResponse<Map<String, String>>> response = globalExceptionHandler
                .handleValidationExceptions(exception);
            
            // Then
            assertNotNull(response);
            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            
            ApiResponse<Map<String, String>> responseBody = response.getBody();
            assertNotNull(responseBody);
            assertFalse(responseBody.isSuccess());
            assertNotNull(responseBody.getData());
            
            Map<String, String> validationErrors = responseBody.getData();
            assertEquals(2, validationErrors.size());
            assertEquals("Email is required", validationErrors.get("email"));
            assertEquals("Name must be at least 2 characters", validationErrors.get("name"));
            
            ApiResponse.ErrorDetail error = responseBody.getError();
            assertNotNull(error);
            assertEquals("VALIDATION_ERROR", error.getCode());
            assertEquals("Validation failed", error.getMessage());
            assertNotNull(error.getTimestamp());
            assertEquals(validationErrors, error.getDetails());
        }

        public void dummyMethod(String param) {
            // Dummy method for reflection testing
        }

        @Test
        @DisplayName("Should handle MethodArgumentNotValidException with multiple field errors")
        void shouldHandleMethodArgumentNotValidExceptionWithMultipleFieldErrors() throws NoSuchMethodException {
            // Given
            Method method = this.getClass().getMethod("dummyMethod", String.class);
            when(methodParameter.getMethod()).thenReturn(method);
            when(methodParameter.getParameterIndex()).thenReturn(0);
            
            BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "userRequest");
            bindingResult.addError(new FieldError("userRequest", "email", "Invalid email format"));
            bindingResult.addError(new FieldError("userRequest", "password", "Password must be at least 8 characters"));
            bindingResult.addError(new FieldError("userRequest", "age", "Age must be between 1 and 120"));
            bindingResult.addError(new FieldError("userRequest", "phoneNumber", "Phone number format is invalid"));
            
            MethodArgumentNotValidException exception = new MethodArgumentNotValidException(methodParameter, bindingResult);
            
            // When
            ResponseEntity<ApiResponse<Map<String, String>>> response = globalExceptionHandler
                .handleValidationExceptions(exception);
            
            // Then
            assertNotNull(response);
            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            
            Map<String, String> validationErrors = response.getBody().getData();
            assertEquals(4, validationErrors.size());
            assertEquals("Invalid email format", validationErrors.get("email"));
            assertEquals("Password must be at least 8 characters", validationErrors.get("password"));
            assertEquals("Age must be between 1 and 120", validationErrors.get("age"));
            assertEquals("Phone number format is invalid", validationErrors.get("phoneNumber"));
        }

        @Test
        @DisplayName("Should handle MethodArgumentNotValidException with single field error")
        void shouldHandleMethodArgumentNotValidExceptionWithSingleFieldError() throws NoSuchMethodException {
            // Given
            Method method = this.getClass().getMethod("dummyMethod", String.class);
            when(methodParameter.getMethod()).thenReturn(method);
            
            BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
            bindingResult.addError(new FieldError("request", "title", "Title cannot be empty"));
            
            MethodArgumentNotValidException exception = new MethodArgumentNotValidException(methodParameter, bindingResult);
            
            // When
            ResponseEntity<ApiResponse<Map<String, String>>> response = globalExceptionHandler
                .handleValidationExceptions(exception);
            
            // Then
            assertNotNull(response);
            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            
            Map<String, String> validationErrors = response.getBody().getData();
            assertEquals(1, validationErrors.size());
            assertEquals("Title cannot be empty", validationErrors.get("title"));
        }

        @Test
        @DisplayName("Should handle MethodArgumentNotValidException with no errors gracefully")
        void shouldHandleMethodArgumentNotValidExceptionWithNoErrorsGracefully() throws NoSuchMethodException {
            // Given
            Method method = this.getClass().getMethod("dummyMethod", String.class);
            when(methodParameter.getMethod()).thenReturn(method);
            
            BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
            // No errors added
            
            MethodArgumentNotValidException exception = new MethodArgumentNotValidException(methodParameter, bindingResult);
            
            // When
            ResponseEntity<ApiResponse<Map<String, String>>> response = globalExceptionHandler
                .handleValidationExceptions(exception);
            
            // Then
            assertNotNull(response);
            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            
            Map<String, String> validationErrors = response.getBody().getData();
            assertTrue(validationErrors.isEmpty());
        }

        @Test
        @DisplayName("Should handle MethodArgumentNotValidException error detail structure correctly")
        void shouldHandleMethodArgumentNotValidExceptionErrorDetailStructureCorrectly() throws NoSuchMethodException {
            // Given
            Method method = this.getClass().getMethod("dummyMethod", String.class);
            when(methodParameter.getMethod()).thenReturn(method);
            
            BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
            bindingResult.addError(new FieldError("request", "field1", "Error message 1"));
            
            MethodArgumentNotValidException exception = new MethodArgumentNotValidException(methodParameter, bindingResult);
            
            // When
            ResponseEntity<ApiResponse<Map<String, String>>> response = globalExceptionHandler
                .handleValidationExceptions(exception);
            
            // Then
            ApiResponse.ErrorDetail error = response.getBody().getError();
            assertNotNull(error);
            assertEquals("VALIDATION_ERROR", error.getCode());
            assertEquals("Validation failed", error.getMessage());
            assertNotNull(error.getDetails());
            assertTrue(error.getDetails() instanceof Map);
            assertNotNull(error.getTimestamp());
            
            @SuppressWarnings("unchecked")
            Map<String, String> details = (Map<String, String>) error.getDetails();
            assertEquals("Error message 1", details.get("field1"));
        }
    }

    @Nested
    @DisplayName("Generic Exception Handler Tests")
    class GenericExceptionHandlerTests {

        @Test
        @DisplayName("Should handle generic Exception and return 500 with generic message")
        void shouldHandleGenericExceptionAndReturn500WithGenericMessage() {
            // Given
            Exception exception = new Exception("Unexpected database error");
            
            // When
            ResponseEntity<ApiResponse<Void>> response = globalExceptionHandler
                .handleGenericException(exception);
            
            // Then
            assertNotNull(response);
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
            
            ApiResponse<Void> responseBody = response.getBody();
            assertNotNull(responseBody);
            assertFalse(responseBody.isSuccess());
            assertNull(responseBody.getData());
            
            ApiResponse.ErrorDetail error = responseBody.getError();
            assertNotNull(error);
            assertEquals("INTERNAL_SERVER_ERROR", error.getCode());
            assertEquals("An unexpected error occurred", error.getMessage());
            assertNotNull(error.getTimestamp());
        }

        @Test
        @DisplayName("Should handle RuntimeException and return generic error message")
        void shouldHandleRuntimeExceptionAndReturnGenericErrorMessage() {
            // Given
            RuntimeException exception = new RuntimeException("Specific runtime error details");
            
            // When
            ResponseEntity<ApiResponse<Void>> response = globalExceptionHandler
                .handleGenericException(exception);
            
            // Then
            assertNotNull(response);
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
            // Should not expose the specific error details for security
            assertEquals("An unexpected error occurred", response.getBody().getError().getMessage());
        }

        @Test
        @DisplayName("Should handle NullPointerException and return generic error message")
        void shouldHandleNullPointerExceptionAndReturnGenericErrorMessage() {
            // Given
            NullPointerException exception = new NullPointerException("Null pointer in service layer");
            
            // When
            ResponseEntity<ApiResponse<Void>> response = globalExceptionHandler
                .handleGenericException(exception);
            
            // Then
            assertNotNull(response);
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
            assertEquals("INTERNAL_SERVER_ERROR", response.getBody().getError().getCode());
            assertEquals("An unexpected error occurred", response.getBody().getError().getMessage());
        }

        @Test
        @DisplayName("Should handle IllegalArgumentException and return generic error message")
        void shouldHandleIllegalArgumentExceptionAndReturnGenericErrorMessage() {
            // Given
            IllegalArgumentException exception = new IllegalArgumentException("Invalid argument provided");
            
            // When
            ResponseEntity<ApiResponse<Void>> response = globalExceptionHandler
                .handleGenericException(exception);
            
            // Then
            assertNotNull(response);
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
            assertEquals("An unexpected error occurred", response.getBody().getError().getMessage());
        }

        @Test
        @DisplayName("Should handle Exception with null message")
        void shouldHandleExceptionWithNullMessage() {
            // Given
            Exception exception = new Exception((String) null);
            
            // When
            ResponseEntity<ApiResponse<Void>> response = globalExceptionHandler
                .handleGenericException(exception);
            
            // Then
            assertNotNull(response);
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
            assertEquals("An unexpected error occurred", response.getBody().getError().getMessage());
        }

        @Test
        @DisplayName("Should handle Exception with cause chain")
        void shouldHandleExceptionWithCauseChain() {
            // Given
            SQLException rootCause = new SQLException("Database connection timeout");
            RuntimeException intermediateCause = new RuntimeException("Service layer error", rootCause);
            Exception topLevelException = new Exception("Controller layer error", intermediateCause);
            
            // When
            ResponseEntity<ApiResponse<Void>> response = globalExceptionHandler
                .handleGenericException(topLevelException);
            
            // Then
            assertNotNull(response);
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
            // Should not expose internal error details
            assertEquals("An unexpected error occurred", response.getBody().getError().getMessage());
        }

        // Mock class for SQLException testing
        private static class SQLException extends Exception {
            public SQLException(String message) {
                super(message);
            }
        }

        @Test
        @DisplayName("Should handle concurrent generic exceptions")
        void shouldHandleConcurrentGenericExceptions() throws InterruptedException {
            // Given
            int threadCount = 20;
            Thread[] threads = new Thread[threadCount];
            ResponseEntity<ApiResponse<Void>>[] responses = new ResponseEntity[threadCount];
            
            // When - Handle exceptions concurrently
            for (int i = 0; i < threadCount; i++) {
                final int index = i;
                threads[i] = new Thread(() -> {
                    Exception exception = new Exception("Concurrent exception " + index);
                    responses[index] = globalExceptionHandler.handleGenericException(exception);
                });
                threads[i].start();
            }
            
            // Wait for all threads to complete
            for (Thread thread : threads) {
                thread.join();
            }
            
            // Then
            for (int i = 0; i < threadCount; i++) {
                assertNotNull(responses[i]);
                assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, responses[i].getStatusCode());
                assertEquals("An unexpected error occurred", responses[i].getBody().getError().getMessage());
            }
        }
    }

    @Nested
    @DisplayName("Handler Priority and Integration Tests")
    class HandlerPriorityAndIntegrationTests {

        @Test
        @DisplayName("Should prioritize specific exception handlers over generic handler")
        void shouldPrioritizeSpecificExceptionHandlersOverGenericHandler() {
            // Given - Create instances of specific exceptions that could also be caught by generic handler
            ResourceNotFoundException resourceException = new ResourceNotFoundException("Resource not found");
            UnauthorizedException unauthorizedException = new UnauthorizedException("Access denied");
            BadRequestException badRequestException = new BadRequestException("Bad request");
            
            // When - Handle specific exceptions
            ResponseEntity<ApiResponse<Void>> resourceResponse = globalExceptionHandler
                .handleResourceNotFoundException(resourceException);
            ResponseEntity<ApiResponse<Void>> unauthorizedResponse = globalExceptionHandler
                .handleUnauthorizedException(unauthorizedException);
            ResponseEntity<ApiResponse<Void>> badRequestResponse = globalExceptionHandler
                .handleBadRequestException(badRequestException);
            
            // Then - Verify specific handlers are used with correct status codes
            assertEquals(HttpStatus.NOT_FOUND, resourceResponse.getStatusCode());
            assertEquals("RESOURCE_NOT_FOUND", resourceResponse.getBody().getError().getCode());
            
            assertEquals(HttpStatus.UNAUTHORIZED, unauthorizedResponse.getStatusCode());
            assertEquals("UNAUTHORIZED", unauthorizedResponse.getBody().getError().getCode());
            
            assertEquals(HttpStatus.BAD_REQUEST, badRequestResponse.getStatusCode());
            assertEquals("BAD_REQUEST", badRequestResponse.getBody().getError().getCode());
        }

        @Test
        @DisplayName("Should maintain consistent response structure across all handlers")
        void shouldMaintainConsistentResponseStructureAcrossAllHandlers() throws NoSuchMethodException {
            // Given - Different types of exceptions
            ResourceNotFoundException resourceException = new ResourceNotFoundException("Resource error");
            UnauthorizedException unauthorizedException = new UnauthorizedException("Auth error");
            BadRequestException badRequestException = new BadRequestException("Request error");
            Exception genericException = new Exception("Generic error");
            
            Method method = this.getClass().getMethod("dummyMethod", String.class);
            when(methodParameter.getMethod()).thenReturn(method);
            BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "test");
            bindingResult.addError(new FieldError("test", "field", "Validation error"));
            MethodArgumentNotValidException validationException = new MethodArgumentNotValidException(methodParameter, bindingResult);
            
            // When - Handle all exception types
            ResponseEntity<ApiResponse<Void>> resourceResponse = globalExceptionHandler
                .handleResourceNotFoundException(resourceException);
            ResponseEntity<ApiResponse<Void>> unauthorizedResponse = globalExceptionHandler
                .handleUnauthorizedException(unauthorizedException);
            ResponseEntity<ApiResponse<Void>> badRequestResponse = globalExceptionHandler
                .handleBadRequestException(badRequestException);
            ResponseEntity<ApiResponse<Void>> genericResponse = globalExceptionHandler
                .handleGenericException(genericException);
            ResponseEntity<ApiResponse<Map<String, String>>> validationResponse = globalExceptionHandler
                .handleValidationExceptions(validationException);
            
            // Then - Verify consistent structure
            verifyConsistentErrorStructure(resourceResponse.getBody());
            verifyConsistentErrorStructure(unauthorizedResponse.getBody());
            verifyConsistentErrorStructure(badRequestResponse.getBody());
            verifyConsistentErrorStructure(genericResponse.getBody());
            
            // Validation response has different data type but same error structure
            assertNotNull(validationResponse.getBody());
            assertFalse(validationResponse.getBody().isSuccess());
            assertNotNull(validationResponse.getBody().getError());
        }

        public void dummyMethod(String param) {
            // Dummy method for reflection testing
        }

        private void verifyConsistentErrorStructure(ApiResponse<Void> response) {
            assertNotNull(response);
            assertFalse(response.isSuccess());
            assertNull(response.getData());
            
            ApiResponse.ErrorDetail error = response.getError();
            assertNotNull(error);
            assertNotNull(error.getCode());
            assertNotNull(error.getTimestamp());
            assertTrue(error.getTimestamp().isBefore(LocalDateTime.now().plusSeconds(1)));
        }

        @Test
        @DisplayName("Should handle exception inheritance hierarchy correctly")
        void shouldHandleExceptionInheritanceHierarchyCorrectly() {
            // Given - Custom exceptions that extend the base exceptions
            class CustomResourceNotFoundException extends ResourceNotFoundException {
                public CustomResourceNotFoundException(String message) {
                    super(message);
                }
            }
            
            class CustomUnauthorizedException extends UnauthorizedException {
                public CustomUnauthorizedException(String message) {
                    super(message);
                }
            }
            
            CustomResourceNotFoundException customResourceException = new CustomResourceNotFoundException("Custom resource error");
            CustomUnauthorizedException customUnauthorizedException = new CustomUnauthorizedException("Custom auth error");
            
            // When - Handle custom exceptions
            ResponseEntity<ApiResponse<Void>> resourceResponse = globalExceptionHandler
                .handleResourceNotFoundException(customResourceException);
            ResponseEntity<ApiResponse<Void>> unauthorizedResponse = globalExceptionHandler
                .handleUnauthorizedException(customUnauthorizedException);
            
            // Then - Should be handled by specific handlers
            assertEquals(HttpStatus.NOT_FOUND, resourceResponse.getStatusCode());
            assertEquals("RESOURCE_NOT_FOUND", resourceResponse.getBody().getError().getCode());
            assertEquals("Custom resource error", resourceResponse.getBody().getError().getMessage());
            
            assertEquals(HttpStatus.UNAUTHORIZED, unauthorizedResponse.getStatusCode());
            assertEquals("UNAUTHORIZED", unauthorizedResponse.getBody().getError().getCode());
            assertEquals("Custom auth error", unauthorizedResponse.getBody().getError().getMessage());
        }
    }
}