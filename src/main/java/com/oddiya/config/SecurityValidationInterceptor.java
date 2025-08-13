package com.oddiya.config;

import com.oddiya.util.SecurityValidationUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;

/**
 * Security Validation Interceptor
 * 
 * Automatically validates all incoming requests for security threats
 * 
 * OWASP Top 10 2021 Coverage:
 * - A01: Broken Access Control - Path validation
 * - A03: Injection - SQL injection, XSS, command injection prevention
 * - A10: Server-Side Request Forgery (SSRF) - URL validation
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityValidationInterceptor implements HandlerInterceptor {
    
    private final SecurityValidationUtils validationUtils;
    private final SecurityEventLogger securityEventLogger;
    
    @Value("${app.security.validation.enabled:true}")
    private boolean validationEnabled;
    
    @Value("${app.security.validation.strict-mode:false}")
    private boolean strictMode;
    
    @Value("${app.security.validation.block-on-violation:true}")
    private boolean blockOnViolation;
    
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    
    // Paths that should be skipped from validation
    private static final List<String> SKIP_VALIDATION_PATHS = Arrays.asList(
        "/actuator/health",
        "/actuator/info",
        "/api/v1/health",
        "/swagger-ui/**",
        "/api-docs/**"
    );
    
    // Parameters that typically contain URLs and need SSRF validation
    private static final List<String> URL_PARAMETERS = Arrays.asList(
        "url", "imageUrl", "profileImageUrl", "callback", "redirect",
        "link", "source", "target", "endpoint"
    );
    
    // Sensitive parameters that need extra validation
    private static final List<String> SENSITIVE_PARAMETERS = Arrays.asList(
        "password", "token", "key", "secret", "code", "auth",
        "email", "username", "userId", "id"
    );
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, 
                           Object handler) throws Exception {
        
        if (!validationEnabled) {
            return true;
        }
        
        // Skip validation for certain paths
        if (shouldSkipValidation(request.getRequestURI())) {
            return true;
        }
        
        // Validate request parameters
        if (!validateRequestParameters(request)) {
            if (blockOnViolation) {
                handleSecurityViolation(request, response, "Invalid request parameters");
                return false;
            } else {
                log.warn("Security validation failed but not blocking request: {}", request.getRequestURI());
            }
        }
        
        // Validate request headers
        if (!validateRequestHeaders(request)) {
            if (blockOnViolation) {
                handleSecurityViolation(request, response, "Invalid request headers");
                return false;
            }
        }
        
        // Validate request path
        if (!validateRequestPath(request)) {
            if (blockOnViolation) {
                handleSecurityViolation(request, response, "Invalid request path");
                return false;
            }
        }
        
        return true;
    }
    
    private boolean shouldSkipValidation(String requestPath) {
        return SKIP_VALIDATION_PATHS.stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, requestPath));
    }
    
    private boolean validateRequestParameters(HttpServletRequest request) {
        Enumeration<String> parameterNames = request.getParameterNames();
        
        while (parameterNames.hasMoreElements()) {
            String paramName = parameterNames.nextElement();
            String[] paramValues = request.getParameterValues(paramName);
            
            if (paramValues != null) {
                for (String paramValue : paramValues) {
                    if (!validateParameter(paramName, paramValue, request)) {
                        return false;
                    }
                }
            }
        }
        
        return true;
    }
    
    private boolean validateParameter(String paramName, String paramValue, HttpServletRequest request) {
        if (paramValue == null) {
            return true;
        }
        
        // Check for URL parameters (SSRF protection)
        if (URL_PARAMETERS.contains(paramName.toLowerCase())) {
            if (!validationUtils.isSsrfSafe(paramValue)) {
                securityEventLogger.logSuspiciousActivity(
                    "SSRF attempt in parameter: " + paramName, 
                    SecurityEventLogger.SecuritySeverity.HIGH, 
                    request
                );
                return false;
            }
        }
        
        // Validate against injection attacks
        if (!validationUtils.isInputSafe(paramValue)) {
            String attackType = determineAttackType(paramValue);
            securityEventLogger.logInjectionAttempt(attackType, paramValue, request);
            return false;
        }
        
        // Additional validation for sensitive parameters
        if (SENSITIVE_PARAMETERS.contains(paramName.toLowerCase()) && strictMode) {
            if (!validateSensitiveParameter(paramName, paramValue)) {
                securityEventLogger.logSuspiciousActivity(
                    "Invalid sensitive parameter: " + paramName,
                    SecurityEventLogger.SecuritySeverity.MEDIUM,
                    request
                );
                return false;
            }
        }
        
        // Check parameter length limits
        if (paramValue.length() > 10000) { // 10KB limit
            securityEventLogger.logSuspiciousActivity(
                "Oversized parameter detected: " + paramName + " (" + paramValue.length() + " chars)",
                SecurityEventLogger.SecuritySeverity.MEDIUM,
                request
            );
            return false;
        }
        
        return true;
    }
    
    private boolean validateRequestHeaders(HttpServletRequest request) {
        Enumeration<String> headerNames = request.getHeaderNames();
        
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            String headerValue = request.getHeader(headerName);
            
            if (!validateHeader(headerName, headerValue, request)) {
                return false;
            }
        }
        
        return true;
    }
    
    private boolean validateHeader(String headerName, String headerValue, HttpServletRequest request) {
        if (headerValue == null) {
            return true;
        }
        
        // Check for header injection attacks
        if (headerValue.contains("\r") || headerValue.contains("\n")) {
            securityEventLogger.logSuspiciousActivity(
                "Header injection attempt in: " + headerName,
                SecurityEventLogger.SecuritySeverity.HIGH,
                request
            );
            return false;
        }
        
        // Validate specific headers
        switch (headerName.toLowerCase()) {
            case "user-agent":
                return validateUserAgent(headerValue, request);
            case "referer":
                return validateReferer(headerValue, request);
            case "x-forwarded-for":
            case "x-real-ip":
                return validateIpHeader(headerValue, request);
            case "content-type":
                return validateContentType(headerValue, request);
            default:
                return validationUtils.isInputSafe(headerValue);
        }
    }
    
    private boolean validateRequestPath(HttpServletRequest request) {
        String requestPath = request.getRequestURI();
        
        // Path traversal validation
        if (!validationUtils.isPathTraversalSafe(requestPath)) {
            securityEventLogger.logSuspiciousActivity(
                "Path traversal attempt: " + requestPath,
                SecurityEventLogger.SecuritySeverity.HIGH,
                request
            );
            return false;
        }
        
        // Check for suspicious path patterns
        if (requestPath.contains("..") || 
            requestPath.toLowerCase().contains("web-inf") ||
            requestPath.toLowerCase().contains("meta-inf") ||
            requestPath.contains("%00")) {
            
            securityEventLogger.logSuspiciousActivity(
                "Suspicious path pattern: " + requestPath,
                SecurityEventLogger.SecuritySeverity.HIGH,
                request
            );
            return false;
        }
        
        return true;
    }
    
    private boolean validateUserAgent(String userAgent, HttpServletRequest request) {
        // Check for suspicious user agent patterns
        String lowerUserAgent = userAgent.toLowerCase();
        
        List<String> suspiciousPatterns = Arrays.asList(
            "sqlmap", "nikto", "nmap", "burp", "dirb", "gobuster",
            "wget", "curl", "python", "java", "perl", "powershell"
        );
        
        for (String pattern : suspiciousPatterns) {
            if (lowerUserAgent.contains(pattern)) {
                securityEventLogger.logSuspiciousActivity(
                    "Suspicious user agent detected: " + pattern,
                    SecurityEventLogger.SecuritySeverity.MEDIUM,
                    request
                );
                return !strictMode; // Block only in strict mode
            }
        }
        
        return validationUtils.isInputSafe(userAgent);
    }
    
    private boolean validateReferer(String referer, HttpServletRequest request) {
        // Validate referer URL for SSRF
        return validationUtils.isSsrfSafe(referer);
    }
    
    private boolean validateIpHeader(String ipHeader, HttpServletRequest request) {
        // Basic IP format validation
        String[] ips = ipHeader.split(",");
        
        for (String ip : ips) {
            ip = ip.trim();
            // Basic IPv4/IPv6 validation pattern
            if (!ip.matches("^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$") && 
                !ip.matches("^(?:[0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$")) {
                
                if (!validationUtils.isInputSafe(ip)) {
                    return false;
                }
            }
        }
        
        return true;
    }
    
    private boolean validateContentType(String contentType, HttpServletRequest request) {
        // Ensure content type doesn't contain injection attempts
        return validationUtils.isInputSafe(contentType);
    }
    
    private boolean validateSensitiveParameter(String paramName, String paramValue) {
        switch (paramName.toLowerCase()) {
            case "email":
                return validationUtils.isValidEmail(paramValue);
            case "id", "userid":
                return validationUtils.isAlphanumeric(paramValue) || 
                       validationUtils.isValidUuid(paramValue);
            case "token", "code":
                return validationUtils.isValidBase64(paramValue) || 
                       validationUtils.isAlphanumeric(paramValue);
            default:
                return validationUtils.isValidLength(paramValue, 1, 255);
        }
    }
    
    private String determineAttackType(String input) {
        if (!validationUtils.isSqlInjectionSafe(input)) {
            return "SQL Injection";
        } else if (!validationUtils.isXssSafe(input)) {
            return "XSS";
        } else if (!validationUtils.isCommandInjectionSafe(input)) {
            return "Command Injection";
        } else if (!validationUtils.isPathTraversalSafe(input)) {
            return "Path Traversal";
        } else {
            return "Unknown Injection";
        }
    }
    
    private void handleSecurityViolation(HttpServletRequest request, HttpServletResponse response, 
                                       String message) throws IOException {
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        response.setContentType("application/json");
        response.getWriter().write(
            "{\"error\":\"Security validation failed\",\"message\":\"" + message + "\"}"
        );
        response.getWriter().flush();
        
        log.warn("Security validation blocked request: {} - {}", request.getRequestURI(), message);
    }
}