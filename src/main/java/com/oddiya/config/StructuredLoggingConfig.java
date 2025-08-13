package com.oddiya.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Structured logging configuration with correlation IDs and security event tracking
 * Provides comprehensive logging for observability and security auditing
 */
@Configuration
@Slf4j
public class StructuredLoggingConfig implements WebMvcConfigurer {

    @Bean
    public LoggingInterceptor loggingInterceptor() {
        return new LoggingInterceptor();
    }

    @Bean
    public StructuredLogger structuredLogger() {
        return new StructuredLogger();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loggingInterceptor())
                .addPathPatterns("/api/**");
    }

    @PostConstruct
    public void configureLogging() {
        log.info("Configuring structured logging with correlation IDs");
        
        // Set up MDC keys for consistent logging
        LoggingContext.setupMDCKeys();
    }

    /**
     * HTTP request logging interceptor with correlation ID tracking
     */
    @Component
    @Slf4j
    public static class LoggingInterceptor implements HandlerInterceptor {

        private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
        private static final String REQUEST_ID_HEADER = "X-Request-Id";

        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
            long startTime = System.currentTimeMillis();
            
            // Generate or extract correlation ID
            String correlationId = extractOrGenerateCorrelationId(request);
            String requestId = UUID.randomUUID().toString();
            
            // Set up logging context
            LoggingContext.setCorrelationId(correlationId);
            LoggingContext.setRequestId(requestId);
            LoggingContext.setUserId(extractUserId(request));
            LoggingContext.setUserAgent(request.getHeader("User-Agent"));
            LoggingContext.setClientIp(getClientIpAddress(request));
            LoggingContext.setStartTime(startTime);
            
            // Add headers to response
            response.setHeader(CORRELATION_ID_HEADER, correlationId);
            response.setHeader(REQUEST_ID_HEADER, requestId);
            
            // Log request start
            log.info("HTTP Request Started: {} {} from {}",
                request.getMethod(),
                request.getRequestURI(),
                LoggingContext.getClientIp()
            );
            
            return true;
        }

        @Override
        public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                   Object handler, Exception ex) {
            try {
                long duration = System.currentTimeMillis() - LoggingContext.getStartTime();
                
                // Log request completion
                if (ex != null) {
                    log.error("HTTP Request Failed: {} {} - Status: {} - Duration: {}ms - Error: {}",
                        request.getMethod(),
                        request.getRequestURI(),
                        response.getStatus(),
                        duration,
                        ex.getMessage(),
                        ex
                    );
                } else if (response.getStatus() >= 400) {
                    log.warn("HTTP Request Error: {} {} - Status: {} - Duration: {}ms",
                        request.getMethod(),
                        request.getRequestURI(),
                        response.getStatus(),
                        duration
                    );
                } else {
                    log.info("HTTP Request Completed: {} {} - Status: {} - Duration: {}ms",
                        request.getMethod(),
                        request.getRequestURI(),
                        response.getStatus(),
                        duration
                    );
                }
                
            } finally {
                // Clean up MDC
                LoggingContext.clear();
            }
        }

        private String extractOrGenerateCorrelationId(HttpServletRequest request) {
            String correlationId = request.getHeader(CORRELATION_ID_HEADER);
            if (correlationId == null || correlationId.trim().isEmpty()) {
                correlationId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            }
            return correlationId;
        }

        private String extractUserId(HttpServletRequest request) {
            // Try to extract user ID from JWT token or session
            // This is a simplified implementation
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                // In production, decode JWT to extract user ID
                return "user-from-jwt";
            }
            return "anonymous";
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
    }

    /**
     * Structured logger for consistent JSON logging
     */
    @Component
    @Slf4j
    public static class StructuredLogger {

        private final ObjectMapper objectMapper = new ObjectMapper();

        /**
         * Log business event with structured data
         */
        public void logBusinessEvent(String eventType, String description, Map<String, Object> data) {
            try {
                Map<String, Object> logEntry = createBaseLogEntry("BUSINESS", eventType);
                logEntry.put("description", description);
                logEntry.put("data", data);
                
                log.info("Business Event: {}", objectMapper.writeValueAsString(logEntry));
                
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize business event log: {}", e.getMessage());
                log.info("Business Event: {} - {} - {}", eventType, description, data);
            }
        }

        /**
         * Log performance metrics
         */
        public void logPerformanceMetric(String operation, long durationMs, Map<String, Object> metrics) {
            try {
                Map<String, Object> logEntry = createBaseLogEntry("PERFORMANCE", operation);
                logEntry.put("duration_ms", durationMs);
                logEntry.put("metrics", metrics);
                
                if (durationMs > 2000) {
                    log.warn("Performance Metric (SLOW): {}", objectMapper.writeValueAsString(logEntry));
                } else {
                    log.info("Performance Metric: {}", objectMapper.writeValueAsString(logEntry));
                }
                
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize performance metric log: {}", e.getMessage());
                log.info("Performance Metric: {} - {}ms - {}", operation, durationMs, metrics);
            }
        }

        /**
         * Log external service interaction
         */
        public void logExternalServiceCall(String serviceName, String operation, long durationMs, 
                                         boolean success, Map<String, Object> details) {
            try {
                Map<String, Object> logEntry = createBaseLogEntry("EXTERNAL_SERVICE", serviceName);
                logEntry.put("operation", operation);
                logEntry.put("duration_ms", durationMs);
                logEntry.put("success", success);
                logEntry.put("details", details);
                
                if (success) {
                    log.info("External Service Call: {}", objectMapper.writeValueAsString(logEntry));
                } else {
                    log.error("External Service Call Failed: {}", objectMapper.writeValueAsString(logEntry));
                }
                
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize external service call log: {}", e.getMessage());
                log.info("External Service Call: {} {} - {}ms - Success: {}", 
                    serviceName, operation, durationMs, success);
            }
        }

        /**
         * Log database operation
         */
        public void logDatabaseOperation(String operation, String query, long durationMs, 
                                       boolean success, Integer rowCount) {
            try {
                Map<String, Object> logEntry = createBaseLogEntry("DATABASE", operation);
                logEntry.put("query", maskSensitiveData(query));
                logEntry.put("duration_ms", durationMs);
                logEntry.put("success", success);
                logEntry.put("row_count", rowCount);
                
                if (durationMs > 1000) {
                    log.warn("Database Operation (SLOW): {}", objectMapper.writeValueAsString(logEntry));
                } else {
                    log.debug("Database Operation: {}", objectMapper.writeValueAsString(logEntry));
                }
                
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize database operation log: {}", e.getMessage());
                log.debug("Database Operation: {} - {}ms - Rows: {}", operation, durationMs, rowCount);
            }
        }

        private Map<String, Object> createBaseLogEntry(String category, String type) {
            Map<String, Object> logEntry = new HashMap<>();
            logEntry.put("timestamp", Instant.now().toString());
            logEntry.put("category", category);
            logEntry.put("type", type);
            logEntry.put("correlation_id", LoggingContext.getCorrelationId());
            logEntry.put("request_id", LoggingContext.getRequestId());
            logEntry.put("user_id", LoggingContext.getUserId());
            logEntry.put("client_ip", LoggingContext.getClientIp());
            
            return logEntry;
        }

        private String maskSensitiveData(String data) {
            if (data == null) return null;
            
            // Mask sensitive data patterns
            return data
                .replaceAll("(?i)(password|token|secret|key)\\s*[=:]\\s*['\"]?[^'\"\\s,)]+", "$1=***")
                .replaceAll("(?i)'[^']*(?:password|token|secret|key)[^']*'", "'***'")
                .replaceAll("\\b\\d{4}[-\\s]?\\d{4}[-\\s]?\\d{4}[-\\s]?\\d{4}\\b", "****-****-****-****"); // Credit card
        }
    }

    /**
     * Security event logger for audit and monitoring
     */
    @Component
    @Slf4j
    public static class SecurityEventLogger {

        private final ObjectMapper objectMapper = new ObjectMapper();

        /**
         * Log authentication attempt
         */
        public void logAuthenticationAttempt(String username, boolean success, String method, 
                                           HttpServletRequest request) {
            try {
                Map<String, Object> event = createSecurityLogEntry("AUTHENTICATION", "ATTEMPT");
                event.put("username", maskUsername(username));
                event.put("success", success);
                event.put("method", method);
                event.put("client_ip", getClientIp(request));
                event.put("user_agent", request.getHeader("User-Agent"));
                
                if (success) {
                    log.info("Security Event: {}", objectMapper.writeValueAsString(event));
                } else {
                    log.warn("Security Event (FAILED_AUTH): {}", objectMapper.writeValueAsString(event));
                }
                
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize authentication log: {}", e.getMessage());
            }
        }

        /**
         * Log authentication failure with details
         */
        public void logAuthenticationFailure(String username, String reason, HttpServletRequest request) {
            try {
                Map<String, Object> event = createSecurityLogEntry("AUTHENTICATION", "FAILURE");
                event.put("username", maskUsername(username));
                event.put("reason", reason);
                event.put("client_ip", getClientIp(request));
                event.put("user_agent", request.getHeader("User-Agent"));
                
                log.warn("Security Event (AUTH_FAILURE): {}", objectMapper.writeValueAsString(event));
                
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize authentication failure log: {}", e.getMessage());
            }
        }

        /**
         * Log authorization failure
         */
        public void logAuthorizationFailure(String userId, String resource, String action, 
                                          HttpServletRequest request) {
            try {
                Map<String, Object> event = createSecurityLogEntry("AUTHORIZATION", "FAILURE");
                event.put("user_id", userId);
                event.put("resource", resource);
                event.put("action", action);
                event.put("client_ip", getClientIp(request));
                
                log.warn("Security Event (AUTHZ_FAILURE): {}", objectMapper.writeValueAsString(event));
                
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize authorization failure log: {}", e.getMessage());
            }
        }

        /**
         * Log suspicious activity
         */
        public void logSuspiciousActivity(String activityType, String description, 
                                        Map<String, Object> details, HttpServletRequest request) {
            try {
                Map<String, Object> event = createSecurityLogEntry("SUSPICIOUS", activityType);
                event.put("description", description);
                event.put("details", details);
                event.put("client_ip", getClientIp(request));
                event.put("user_agent", request.getHeader("User-Agent"));
                
                log.error("Security Event (SUSPICIOUS): {}", objectMapper.writeValueAsString(event));
                
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize suspicious activity log: {}", e.getMessage());
            }
        }

        /**
         * Log data access event
         */
        public void logDataAccess(String userId, String dataType, String operation, 
                                 String resourceId, boolean success) {
            try {
                Map<String, Object> event = createSecurityLogEntry("DATA_ACCESS", operation);
                event.put("user_id", userId);
                event.put("data_type", dataType);
                event.put("resource_id", resourceId);
                event.put("success", success);
                
                log.info("Security Event (DATA_ACCESS): {}", objectMapper.writeValueAsString(event));
                
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize data access log: {}", e.getMessage());
            }
        }

        /**
         * Log privilege escalation attempt
         */
        public void logPrivilegeEscalation(String userId, String attemptedAction, 
                                         String requiredRole, HttpServletRequest request) {
            try {
                Map<String, Object> event = createSecurityLogEntry("PRIVILEGE", "ESCALATION_ATTEMPT");
                event.put("user_id", userId);
                event.put("attempted_action", attemptedAction);
                event.put("required_role", requiredRole);
                event.put("client_ip", getClientIp(request));
                
                log.error("Security Event (PRIVILEGE_ESCALATION): {}", objectMapper.writeValueAsString(event));
                
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize privilege escalation log: {}", e.getMessage());
            }
        }

        private Map<String, Object> createSecurityLogEntry(String category, String type) {
            Map<String, Object> event = new HashMap<>();
            event.put("timestamp", Instant.now().toString());
            event.put("category", "SECURITY");
            event.put("sub_category", category);
            event.put("type", type);
            event.put("correlation_id", LoggingContext.getCorrelationId());
            event.put("request_id", LoggingContext.getRequestId());
            event.put("severity", "HIGH");
            
            return event;
        }

        private String maskUsername(String username) {
            if (username == null || username.length() <= 3) return "***";
            return username.substring(0, 1) + "***" + username.substring(username.length() - 1);
        }

        private String getClientIp(HttpServletRequest request) {
            String xForwardedFor = request.getHeader("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
                return xForwardedFor.split(",")[0].trim();
            }
            return request.getRemoteAddr();
        }
    }

    /**
     * Thread-local logging context for correlation IDs and request metadata
     */
    public static class LoggingContext {
        
        private static final String CORRELATION_ID_KEY = "correlationId";
        private static final String REQUEST_ID_KEY = "requestId";
        private static final String USER_ID_KEY = "userId";
        private static final String CLIENT_IP_KEY = "clientIp";
        private static final String USER_AGENT_KEY = "userAgent";
        private static final String START_TIME_KEY = "startTime";

        public static void setupMDCKeys() {
            // This method can be used to configure additional MDC keys if needed
        }

        public static void setCorrelationId(String correlationId) {
            MDC.put(CORRELATION_ID_KEY, correlationId);
        }

        public static String getCorrelationId() {
            return MDC.get(CORRELATION_ID_KEY);
        }

        public static void setRequestId(String requestId) {
            MDC.put(REQUEST_ID_KEY, requestId);
        }

        public static String getRequestId() {
            return MDC.get(REQUEST_ID_KEY);
        }

        public static void setUserId(String userId) {
            MDC.put(USER_ID_KEY, userId);
        }

        public static String getUserId() {
            return MDC.get(USER_ID_KEY);
        }

        public static void setClientIp(String clientIp) {
            MDC.put(CLIENT_IP_KEY, clientIp);
        }

        public static String getClientIp() {
            return MDC.get(CLIENT_IP_KEY);
        }

        public static void setUserAgent(String userAgent) {
            MDC.put(USER_AGENT_KEY, userAgent);
        }

        public static String getUserAgent() {
            return MDC.get(USER_AGENT_KEY);
        }

        public static void setStartTime(long startTime) {
            MDC.put(START_TIME_KEY, String.valueOf(startTime));
        }

        public static long getStartTime() {
            String startTime = MDC.get(START_TIME_KEY);
            return startTime != null ? Long.parseLong(startTime) : System.currentTimeMillis();
        }

        public static void clear() {
            MDC.clear();
        }
    }
}

/**
 * Logback configuration for structured logging
 */
@Configuration
@ConfigurationProperties(prefix = "logging.structured")
@Profile({"aws", "production"})
@Data
@Slf4j
class StructuredLoggingProperties {
    
    private boolean enabled = true;
    private String pattern = "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level [%X{correlationId}] %logger{36} - %msg%n";
    private String jsonPattern = "{\"timestamp\":\"%d{yyyy-MM-dd'T'HH:mm:ss.SSS'Z'}\",\"level\":\"%level\",\"thread\":\"%thread\",\"logger\":\"%logger\",\"correlationId\":\"%X{correlationId}\",\"requestId\":\"%X{requestId}\",\"userId\":\"%X{userId}\",\"clientIp\":\"%X{clientIp}\",\"message\":\"%msg\",\"exception\":\"%ex\"}%n";
    
    @PostConstruct
    public void configureLogback() {
        if (enabled) {
            log.info("Configuring structured logging with JSON format");
            // Additional Logback configuration can be added here
        }
    }
}