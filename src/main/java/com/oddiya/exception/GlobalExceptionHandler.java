package com.oddiya.exception;

import com.oddiya.config.SecurityEventLogger;
import com.oddiya.dto.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.NoHandlerFoundException;

import javax.crypto.BadPaddingException;
// Using fully qualified name to avoid conflict with Spring Security's AccessDeniedException
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Global Exception Handler with Security Hardening
 * 
 * Implements secure error handling without information leakage:
 * - No stack traces in responses
 * - Generic error messages for security exceptions
 * - Comprehensive security event logging
 * - Error correlation IDs for troubleshooting
 * 
 * OWASP Top 10 2021:
 * - A05: Security Misconfiguration - Secure error handling
 * - A09: Security Logging - Comprehensive error logging
 * - A01: Broken Access Control - Access denial handling
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {
    
    private final SecurityEventLogger securityEventLogger;
    
    @Value("${app.security.error-details.enabled:false}")
    private boolean includeDetailedErrors;
    
    @Value("${spring.profiles.active:unknown}")
    private String activeProfile;
    
    // Security-related exceptions
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationException(
            AuthenticationException ex, HttpServletRequest request) {
        String errorId = generateErrorId();
        logSecurityError(errorId, "Authentication failure", ex, request);
        
        securityEventLogger.logAuthenticationFailure(
            "unknown", 
            ex.getMessage(),
            request
        );
        
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("AUTHENTICATION_FAILED", 
                      "Authentication required", 
                      errorId));
    }
    
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(
            AccessDeniedException ex, HttpServletRequest request) {
        String errorId = generateErrorId();
        logSecurityError(errorId, "Access denied", ex, request);
        
        securityEventLogger.logAuthorizationFailure(
            getCurrentUserId(),
            request.getRequestURI(),
            request.getMethod(),
            request
        );
        
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("ACCESS_DENIED", 
                      "Access denied", 
                      errorId));
    }
    
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthorizedException(
            UnauthorizedException ex, HttpServletRequest request) {
        String errorId = generateErrorId();
        logSecurityError(errorId, "Unauthorized access attempt", ex, request);
        
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("UNAUTHORIZED", 
                      sanitizeErrorMessage(ex.getMessage()), 
                      errorId));
    }
    
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFoundException(
            ResourceNotFoundException ex, HttpServletRequest request) {
        String errorId = generateErrorId();
        logApplicationError(errorId, "Resource not found", ex, request);
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("RESOURCE_NOT_FOUND", 
                      sanitizeErrorMessage(ex.getMessage()), 
                      errorId));
    }
    
    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequestException(
            BadRequestException ex, HttpServletRequest request) {
        String errorId = generateErrorId();
        logApplicationError(errorId, "Bad request", ex, request);
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("BAD_REQUEST", 
                      sanitizeErrorMessage(ex.getMessage()), 
                      errorId));
    }
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationExceptions(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        String errorId = generateErrorId();
        
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = sanitizeErrorMessage(error.getDefaultMessage());
            errors.put(fieldName, errorMessage);
        });
        
        logValidationError(errorId, "Validation failed", errors, request);
        
        ApiResponse.ErrorDetail errorDetail = ApiResponse.ErrorDetail.builder()
                .code("VALIDATION_ERROR")
                .message("Request validation failed")
                .details(errors)
                .timestamp(LocalDateTime.now())
                .errorId(errorId)
                .build();
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(errorDetail));
    }
    
    // Database-related exceptions
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataAccessException(
            DataAccessException ex, HttpServletRequest request) {
        String errorId = generateErrorId();
        logSystemError(errorId, "Database error", ex, request);
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("DATABASE_ERROR", 
                      "Data access error occurred", 
                      errorId));
    }
    
    // Encryption-related exceptions
    @ExceptionHandler(BadPaddingException.class)
    public ResponseEntity<ApiResponse<Void>> handleEncryptionException(
            BadPaddingException ex, HttpServletRequest request) {
        String errorId = generateErrorId();
        logSecurityError(errorId, "Encryption/decryption error", ex, request);
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("INVALID_DATA", 
                      "Data format error", 
                      errorId));
    }
    
    // File access exceptions
    @ExceptionHandler(java.nio.file.AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleFileAccessDeniedException(
            java.nio.file.AccessDeniedException ex, HttpServletRequest request) {
        String errorId = generateErrorId();
        logSecurityError(errorId, "File access denied", ex, request);
        
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("FILE_ACCESS_DENIED", 
                      "File access not allowed", 
                      errorId));
    }
    
    // HTTP method not supported
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        String errorId = generateErrorId();
        logApplicationError(errorId, "Method not supported", ex, request);
        
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.error("METHOD_NOT_ALLOWED", 
                      "HTTP method not supported", 
                      errorId));
    }
    
    // No handler found (404)
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoHandlerFound(
            NoHandlerFoundException ex, HttpServletRequest request) {
        String errorId = generateErrorId();
        logApplicationError(errorId, "Endpoint not found", ex, request);
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("ENDPOINT_NOT_FOUND", 
                      "Requested endpoint not found", 
                      errorId));
    }
    
    // Generic exception handler (catch-all)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(
            Exception ex, HttpServletRequest request) {
        String errorId = generateErrorId();
        logSystemError(errorId, "Unexpected error", ex, request);
        
        // Check if it's a potential security issue
        if (isPotentialSecurityException(ex)) {
            securityEventLogger.logSuspiciousActivity(
                "Unexpected exception with security implications: " + ex.getClass().getSimpleName(),
                SecurityEventLogger.SecuritySeverity.MEDIUM,
                request
            );
        }
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_SERVER_ERROR", 
                      "An unexpected error occurred", 
                      errorId));
    }
    
    // Security-aware error logging methods
    
    private void logSecurityError(String errorId, String message, Exception ex, HttpServletRequest request) {
        log.error("[SECURITY_ERROR] ID: {} - {} - URI: {} - User: {} - IP: {} - Error: {}", 
                 errorId, message, request.getRequestURI(), getCurrentUserId(), 
                 getClientIpAddress(request), ex.getMessage());
        
        if (includeDetailedErrors || isDevelopmentProfile()) {
            log.debug("[SECURITY_ERROR_DEBUG] ID: {} - Stack trace:", errorId, ex);
        }
    }
    
    private void logApplicationError(String errorId, String message, Exception ex, HttpServletRequest request) {
        log.warn("[APP_ERROR] ID: {} - {} - URI: {} - User: {} - Error: {}", 
                errorId, message, request.getRequestURI(), getCurrentUserId(), ex.getMessage());
        
        if (includeDetailedErrors || isDevelopmentProfile()) {
            log.debug("[APP_ERROR_DEBUG] ID: {} - Stack trace:", errorId, ex);
        }
    }
    
    private void logSystemError(String errorId, String message, Exception ex, HttpServletRequest request) {
        log.error("[SYSTEM_ERROR] ID: {} - {} - URI: {} - User: {} - Error: {}", 
                 errorId, message, request.getRequestURI(), getCurrentUserId(), ex.getMessage());
        
        if (includeDetailedErrors || isDevelopmentProfile()) {
            log.error("[SYSTEM_ERROR_DEBUG] ID: {} - Stack trace:", errorId, ex);
        }
    }
    
    private void logValidationError(String errorId, String message, Map<String, String> errors, HttpServletRequest request) {
        log.info("[VALIDATION_ERROR] ID: {} - {} - URI: {} - User: {} - Errors: {}", 
                errorId, message, request.getRequestURI(), getCurrentUserId(), errors);
    }
    
    // Utility methods
    
    private String generateErrorId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
    
    private String getCurrentUserId() {
        try {
            // This would typically be extracted from SecurityContext
            return "unknown"; // Placeholder
        } catch (Exception e) {
            return "unknown";
        }
    }
    
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
    
    private String sanitizeErrorMessage(String message) {
        if (message == null) {
            return "An error occurred";
        }
        
        // Remove potentially sensitive information
        String sanitized = message
                .replaceAll("password", "***")
                .replaceAll("token", "***")
                .replaceAll("key", "***")
                .replaceAll("secret", "***")
                .replaceAll("\\b\\d{3}-\\d{2}-\\d{4}\\b", "***-**-****") // SSN pattern
                .replaceAll("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b", "***@***.com"); // Email
        
        // Limit message length
        if (sanitized.length() > 200) {
            sanitized = sanitized.substring(0, 200) + "...";
        }
        
        return sanitized;
    }
    
    private boolean isPotentialSecurityException(Exception ex) {
        String className = ex.getClass().getSimpleName().toLowerCase();
        String message = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";
        
        return className.contains("security") || 
               className.contains("access") || 
               className.contains("auth") || 
               message.contains("unauthorized") ||
               message.contains("forbidden") ||
               message.contains("permission");
    }
    
    private boolean isDevelopmentProfile() {
        return "local".equals(activeProfile) || 
               "dev".equals(activeProfile) || 
               "development".equals(activeProfile) ||
               "test".equals(activeProfile);
    }
    
    private HttpServletRequest getCurrentRequest() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            return attributes.getRequest();
        } catch (Exception e) {
            return null;
        }
    }
}