package com.oddiya.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Security Event Logger
 * 
 * Centralized security event logging for audit trails and security monitoring
 * 
 * OWASP Top 10 2021:
 * - A09: Security Logging and Monitoring Failures
 * - A01: Broken Access Control (audit trail)
 * - A07: Identification and Authentication Failures (auth events)
 * 
 * Compliance:
 * - GDPR Article 25 (Data Protection by Design)
 * - GDPR Article 32 (Security of Processing)
 * - Korean PIPA Act (Personal Information Protection)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityEventLogger {
    
    private final ObjectMapper objectMapper;
    
    @Value("${app.security.audit.enabled:true}")
    private boolean auditEnabled;
    
    @Value("${app.security.audit.include-ip:true}")
    private boolean includeIpAddress;
    
    @Value("${app.security.audit.include-user-agent:true}")
    private boolean includeUserAgent;
    
    @Value("${app.security.audit.mask-sensitive-data:true}")
    private boolean maskSensitiveData;
    
    private static final DateTimeFormatter AUDIT_TIMESTAMP_FORMAT = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
                         .withZone(ZoneId.of("UTC"));
    
    // Security Event Types
    public enum SecurityEventType {
        AUTHENTICATION_SUCCESS,
        AUTHENTICATION_FAILURE,
        AUTHENTICATION_BYPASS_ATTEMPT,
        AUTHORIZATION_SUCCESS,
        AUTHORIZATION_FAILURE,
        RATE_LIMIT_EXCEEDED,
        SUSPICIOUS_ACTIVITY,
        DATA_ACCESS,
        DATA_MODIFICATION,
        DATA_EXPORT,
        PASSWORD_CHANGE,
        ACCOUNT_LOCKED,
        ACCOUNT_UNLOCKED,
        TOKEN_ISSUED,
        TOKEN_REFRESHED,
        TOKEN_REVOKED,
        CSRF_PROTECTION_TRIGGERED,
        SQL_INJECTION_ATTEMPT,
        XSS_ATTEMPT,
        MALICIOUS_FILE_UPLOAD,
        SECURITY_CONFIGURATION_CHANGE,
        ADMIN_ACTION,
        PRIVACY_VIOLATION,
        COMPLIANCE_VIOLATION
    }
    
    // Security Event Severity
    public enum SecuritySeverity {
        INFO,
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }
    
    /**
     * Log authentication success event
     */
    public void logAuthenticationSuccess(String userId, String provider, HttpServletRequest request) {
        if (!auditEnabled) return;
        
        SecurityEvent event = SecurityEvent.builder()
            .eventType(SecurityEventType.AUTHENTICATION_SUCCESS)
            .severity(SecuritySeverity.INFO)
            .userId(userId)
            .message("User successfully authenticated")
            .details(Map.of(
                "provider", provider,
                "success", true
            ))
            .build();
            
        logSecurityEvent(event, request);
    }
    
    /**
     * Log authentication failure event
     */
    public void logAuthenticationFailure(String identifier, String reason, HttpServletRequest request) {
        if (!auditEnabled) return;
        
        SecurityEvent event = SecurityEvent.builder()
            .eventType(SecurityEventType.AUTHENTICATION_FAILURE)
            .severity(SecuritySeverity.MEDIUM)
            .userId(maskSensitiveData ? maskEmail(identifier) : identifier)
            .message("Authentication failed")
            .details(Map.of(
                "reason", reason,
                "success", false
            ))
            .build();
            
        logSecurityEvent(event, request);
    }
    
    /**
     * Log authorization failure event
     */
    public void logAuthorizationFailure(String userId, String resource, String action, HttpServletRequest request) {
        if (!auditEnabled) return;
        
        SecurityEvent event = SecurityEvent.builder()
            .eventType(SecurityEventType.AUTHORIZATION_FAILURE)
            .severity(SecuritySeverity.HIGH)
            .userId(userId)
            .message("Access denied to resource")
            .details(Map.of(
                "resource", resource,
                "action", action,
                "authorized", false
            ))
            .build();
            
        logSecurityEvent(event, request);
    }
    
    /**
     * Log rate limiting event
     */
    public void logRateLimitExceeded(String identifier, String rateLimitType, long waitTimeSeconds, HttpServletRequest request) {
        if (!auditEnabled) return;
        
        SecurityEvent event = SecurityEvent.builder()
            .eventType(SecurityEventType.RATE_LIMIT_EXCEEDED)
            .severity(SecuritySeverity.MEDIUM)
            .userId(identifier)
            .message("Rate limit exceeded")
            .details(Map.of(
                "rateLimitType", rateLimitType,
                "waitTimeSeconds", waitTimeSeconds,
                "blocked", true
            ))
            .build();
            
        logSecurityEvent(event, request);
    }
    
    /**
     * Log suspicious activity
     */
    public void logSuspiciousActivity(String description, SecuritySeverity severity, HttpServletRequest request) {
        if (!auditEnabled) return;
        
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String userId = auth != null && auth.isAuthenticated() ? auth.getName() : "anonymous";
        
        SecurityEvent event = SecurityEvent.builder()
            .eventType(SecurityEventType.SUSPICIOUS_ACTIVITY)
            .severity(severity)
            .userId(userId)
            .message(description)
            .details(Map.of(
                "detected", true,
                "requiresInvestigation", severity.ordinal() >= SecuritySeverity.HIGH.ordinal()
            ))
            .build();
            
        logSecurityEvent(event, request);
    }
    
    /**
     * Log data access event (for GDPR compliance)
     */
    public void logDataAccess(String userId, String dataType, String purpose) {
        if (!auditEnabled) return;
        
        SecurityEvent event = SecurityEvent.builder()
            .eventType(SecurityEventType.DATA_ACCESS)
            .severity(SecuritySeverity.INFO)
            .userId(userId)
            .message("Personal data accessed")
            .details(Map.of(
                "dataType", dataType,
                "purpose", purpose,
                "legalBasis", "legitimate_interest",
                "gdprCompliant", true
            ))
            .build();
            
        logSecurityEvent(event, null);
    }
    
    /**
     * Log data modification event (for audit trail)
     */
    public void logDataModification(String userId, String entityType, String entityId, String operation) {
        if (!auditEnabled) return;
        
        SecurityEvent event = SecurityEvent.builder()
            .eventType(SecurityEventType.DATA_MODIFICATION)
            .severity(SecuritySeverity.INFO)
            .userId(userId)
            .message("Data modified")
            .details(Map.of(
                "entityType", entityType,
                "entityId", entityId,
                "operation", operation,
                "auditTrail", true
            ))
            .build();
            
        logSecurityEvent(event, null);
    }
    
    /**
     * Log potential injection attack
     */
    public void logInjectionAttempt(String attackType, String payload, HttpServletRequest request) {
        if (!auditEnabled) return;
        
        SecurityEvent event = SecurityEvent.builder()
            .eventType(attackType.toLowerCase().contains("sql") ? 
                      SecurityEventType.SQL_INJECTION_ATTEMPT : SecurityEventType.XSS_ATTEMPT)
            .severity(SecuritySeverity.HIGH)
            .userId("anonymous")
            .message("Injection attack detected")
            .details(Map.of(
                "attackType", attackType,
                "payload", maskSensitiveData ? maskPayload(payload) : payload,
                "blocked", true,
                "threatLevel", "HIGH"
            ))
            .build();
            
        logSecurityEvent(event, request);
    }
    
    /**
     * Log admin actions for compliance
     */
    public void logAdminAction(String adminUserId, String action, String target, String details) {
        if (!auditEnabled) return;
        
        SecurityEvent event = SecurityEvent.builder()
            .eventType(SecurityEventType.ADMIN_ACTION)
            .severity(SecuritySeverity.INFO)
            .userId(adminUserId)
            .message("Administrative action performed")
            .details(Map.of(
                "action", action,
                "target", target,
                "adminDetails", details,
                "requiresApproval", false
            ))
            .build();
            
        logSecurityEvent(event, null);
    }
    
    /**
     * Core security event logging method
     */
    private void logSecurityEvent(SecurityEvent event, HttpServletRequest request) {
        try {
            Map<String, Object> logEntry = new HashMap<>();
            logEntry.put("timestamp", AUDIT_TIMESTAMP_FORMAT.format(Instant.now()));
            logEntry.put("eventType", event.getEventType().name());
            logEntry.put("severity", event.getSeverity().name());
            logEntry.put("userId", event.getUserId());
            logEntry.put("message", event.getMessage());
            logEntry.put("details", event.getDetails());
            
            // Add request context if available
            if (request != null) {
                Map<String, Object> requestContext = new HashMap<>();
                requestContext.put("method", request.getMethod());
                requestContext.put("uri", request.getRequestURI());
                requestContext.put("queryString", request.getQueryString());
                
                if (includeIpAddress) {
                    requestContext.put("clientIp", getClientIpAddress(request));
                }
                
                if (includeUserAgent) {
                    requestContext.put("userAgent", request.getHeader("User-Agent"));
                }
                
                requestContext.put("sessionId", request.getSession(false) != null ? 
                                  request.getSession().getId() : null);
                
                logEntry.put("request", requestContext);
            }
            
            // Add system context
            Map<String, Object> systemContext = new HashMap<>();
            systemContext.put("application", "oddiya");
            systemContext.put("version", "1.0.0");
            systemContext.put("environment", System.getProperty("spring.profiles.active", "unknown"));
            logEntry.put("system", systemContext);
            
            // Log to appropriate logger based on severity
            String jsonLog = objectMapper.writeValueAsString(logEntry);
            
            switch (event.getSeverity()) {
                case CRITICAL -> log.error("SECURITY_EVENT: {}", jsonLog);
                case HIGH -> log.error("SECURITY_EVENT: {}", jsonLog);
                case MEDIUM -> log.warn("SECURITY_EVENT: {}", jsonLog);
                case LOW -> log.info("SECURITY_EVENT: {}", jsonLog);
                case INFO -> log.info("SECURITY_EVENT: {}", jsonLog);
            }
            
        } catch (JsonProcessingException e) {
            log.error("Failed to log security event", e);
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
    
    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return email;
        }
        
        String[] parts = email.split("@");
        if (parts[0].length() <= 2) {
            return "***@" + parts[1];
        }
        
        return parts[0].substring(0, 2) + "***@" + parts[1];
    }
    
    private String maskPayload(String payload) {
        if (payload == null || payload.length() <= 20) {
            return "***";
        }
        
        return payload.substring(0, 10) + "..." + payload.substring(payload.length() - 10);
    }
    
    /**
     * Security Event data class
     */
    private static class SecurityEvent {
        private final SecurityEventType eventType;
        private final SecuritySeverity severity;
        private final String userId;
        private final String message;
        private final Map<String, Object> details;
        
        private SecurityEvent(Builder builder) {
            this.eventType = builder.eventType;
            this.severity = builder.severity;
            this.userId = builder.userId;
            this.message = builder.message;
            this.details = builder.details;
        }
        
        public static Builder builder() {
            return new Builder();
        }
        
        public SecurityEventType getEventType() { return eventType; }
        public SecuritySeverity getSeverity() { return severity; }
        public String getUserId() { return userId; }
        public String getMessage() { return message; }
        public Map<String, Object> getDetails() { return details; }
        
        private static class Builder {
            private SecurityEventType eventType;
            private SecuritySeverity severity;
            private String userId;
            private String message;
            private Map<String, Object> details = new HashMap<>();
            
            public Builder eventType(SecurityEventType eventType) {
                this.eventType = eventType;
                return this;
            }
            
            public Builder severity(SecuritySeverity severity) {
                this.severity = severity;
                return this;
            }
            
            public Builder userId(String userId) {
                this.userId = userId;
                return this;
            }
            
            public Builder message(String message) {
                this.message = message;
                return this;
            }
            
            public Builder details(Map<String, Object> details) {
                this.details = details;
                return this;
            }
            
            public SecurityEvent build() {
                return new SecurityEvent(this);
            }
        }
    }
}