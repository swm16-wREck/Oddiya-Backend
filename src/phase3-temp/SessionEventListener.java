package com.oddiya.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.session.events.AbstractSessionEvent;
import org.springframework.session.events.SessionCreatedEvent;
import org.springframework.session.events.SessionDeletedEvent;
import org.springframework.session.events.SessionExpiredEvent;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Session Event Listener
 * Agent 4 - Session Management Specialist
 * 
 * Listens to session lifecycle events for analytics, security monitoring,
 * and resource management.
 */
@Slf4j
@Component
public class SessionEventListener implements ApplicationListener<AbstractSessionEvent> {

    private final AtomicLong activeSessionCount = new AtomicLong(0);
    private final AtomicLong totalSessionsCreated = new AtomicLong(0);
    private final ConcurrentHashMap<String, SessionInfo> sessionInfoMap = new ConcurrentHashMap<>();

    @Override
    public void onApplicationEvent(AbstractSessionEvent event) {
        try {
            if (event instanceof SessionCreatedEvent) {
                handleSessionCreated((SessionCreatedEvent) event);
            } else if (event instanceof SessionExpiredEvent) {
                handleSessionExpired((SessionExpiredEvent) event);
            } else if (event instanceof SessionDeletedEvent) {
                handleSessionDeleted((SessionDeletedEvent) event);
            }
        } catch (Exception e) {
            log.error("Error handling session event: {}", event.getClass().getSimpleName(), e);
        }
    }

    /**
     * Handle session creation
     */
    private void handleSessionCreated(SessionCreatedEvent event) {
        String sessionId = event.getSessionId();
        long currentCount = activeSessionCount.incrementAndGet();
        totalSessionsCreated.incrementAndGet();

        // Store session info for analytics
        SessionInfo sessionInfo = SessionInfo.builder()
                .sessionId(sessionId)
                .createdAt(Instant.now())
                .lastAccessedAt(Instant.now())
                .userAgent(getCurrentUserAgent())
                .ipAddress(getCurrentIpAddress())
                .username(getCurrentUsername())
                .build();
        
        sessionInfoMap.put(sessionId, sessionInfo);

        log.info("Session created: id={}, activeCount={}, totalCreated={}", 
                sessionId, currentCount, totalSessionsCreated.get());

        // Security monitoring - detect suspicious activity
        detectSuspiciousSessionCreation(sessionInfo);
    }

    /**
     * Handle session expiration
     */
    private void handleSessionExpired(SessionExpiredEvent event) {
        String sessionId = event.getSessionId();
        long currentCount = activeSessionCount.decrementAndGet();

        SessionInfo sessionInfo = sessionInfoMap.remove(sessionId);
        if (sessionInfo != null) {
            long sessionDuration = java.time.Duration.between(
                sessionInfo.getCreatedAt(), 
                Instant.now()
            ).toMinutes();

            log.info("Session expired: id={}, duration={}min, activeCount={}", 
                    sessionId, sessionDuration, currentCount);

            // Record session analytics
            recordSessionAnalytics(sessionInfo, "EXPIRED");
        } else {
            log.info("Session expired: id={}, activeCount={}", sessionId, currentCount);
        }
    }

    /**
     * Handle session deletion (logout)
     */
    private void handleSessionDeleted(SessionDeletedEvent event) {
        String sessionId = event.getSessionId();
        long currentCount = activeSessionCount.decrementAndGet();

        SessionInfo sessionInfo = sessionInfoMap.remove(sessionId);
        if (sessionInfo != null) {
            long sessionDuration = java.time.Duration.between(
                sessionInfo.getCreatedAt(), 
                Instant.now()
            ).toMinutes();

            log.info("Session deleted: id={}, duration={}min, activeCount={}", 
                    sessionId, sessionDuration, currentCount);

            // Record session analytics
            recordSessionAnalytics(sessionInfo, "DELETED");
        } else {
            log.info("Session deleted: id={}, activeCount={}", sessionId, currentCount);
        }
    }

    /**
     * Detect suspicious session creation patterns
     */
    private void detectSuspiciousSessionCreation(SessionInfo sessionInfo) {
        try {
            String ipAddress = sessionInfo.getIpAddress();
            if (ipAddress == null) return;

            // Count sessions from same IP in last 5 minutes
            long recentSessionsFromSameIp = sessionInfoMap.values().stream()
                    .filter(info -> ipAddress.equals(info.getIpAddress()))
                    .filter(info -> java.time.Duration.between(info.getCreatedAt(), Instant.now()).toMinutes() <= 5)
                    .count();

            // Alert if too many sessions from same IP
            if (recentSessionsFromSameIp > 5) {
                log.warn("SECURITY ALERT: Suspicious session creation pattern - {} sessions from IP {} in last 5 minutes",
                        recentSessionsFromSameIp, ipAddress);
                
                // In production, you might want to:
                // - Block the IP temporarily
                // - Send alert to security team
                // - Implement CAPTCHA requirement
            }

        } catch (Exception e) {
            log.error("Error detecting suspicious session creation", e);
        }
    }

    /**
     * Record session analytics
     */
    private void recordSessionAnalytics(SessionInfo sessionInfo, String endReason) {
        try {
            long sessionDurationMinutes = java.time.Duration.between(
                sessionInfo.getCreatedAt(), 
                Instant.now()
            ).toMinutes();

            // Log structured analytics data
            log.info("SESSION_ANALYTICS: sessionId={}, username={}, duration={}min, " +
                    "endReason={}, userAgent={}, ipAddress={}",
                    sessionInfo.getSessionId(),
                    sessionInfo.getUsername(),
                    sessionDurationMinutes,
                    endReason,
                    sessionInfo.getUserAgent(),
                    sessionInfo.getIpAddress());

            // In production, you might want to:
            // - Send to analytics service
            // - Store in time-series database
            // - Update user behavior metrics

        } catch (Exception e) {
            log.error("Error recording session analytics", e);
        }
    }

    /**
     * Get current user agent from request
     */
    private String getCurrentUserAgent() {
        try {
            HttpServletRequest request = getCurrentRequest();
            return request != null ? request.getHeader("User-Agent") : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Get current IP address from request
     */
    private String getCurrentIpAddress() {
        try {
            HttpServletRequest request = getCurrentRequest();
            if (request == null) return null;

            // Check for X-Forwarded-For header (common with load balancers)
            String xForwardedFor = request.getHeader("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
                return xForwardedFor.split(",")[0].trim();
            }

            // Check for X-Real-IP header
            String xRealIp = request.getHeader("X-Real-IP");
            if (xRealIp != null && !xRealIp.isEmpty()) {
                return xRealIp;
            }

            // Fall back to remote address
            return request.getRemoteAddr();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Get current username from security context
     */
    private String getCurrentUsername() {
        try {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            return authentication != null && authentication.isAuthenticated() 
                    ? authentication.getName() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Get current HTTP request
     */
    private HttpServletRequest getCurrentRequest() {
        try {
            org.springframework.web.context.request.RequestAttributes requestAttributes = 
                    org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
            
            if (requestAttributes instanceof org.springframework.web.context.request.ServletRequestAttributes) {
                return ((org.springframework.web.context.request.ServletRequestAttributes) requestAttributes)
                        .getRequest();
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Get session statistics
     */
    public SessionStatistics getSessionStatistics() {
        return SessionStatistics.builder()
                .activeSessionCount(activeSessionCount.get())
                .totalSessionsCreated(totalSessionsCreated.get())
                .averageSessionDuration(calculateAverageSessionDuration())
                .build();
    }

    /**
     * Calculate average session duration for active sessions
     */
    private long calculateAverageSessionDuration() {
        if (sessionInfoMap.isEmpty()) {
            return 0;
        }

        return (long) sessionInfoMap.values().stream()
                .mapToLong(info -> java.time.Duration.between(info.getCreatedAt(), Instant.now()).toMinutes())
                .average()
                .orElse(0.0);
    }

    /**
     * Session Information holder
     */
    @lombok.Data
    @lombok.Builder
    public static class SessionInfo {
        private String sessionId;
        private Instant createdAt;
        private Instant lastAccessedAt;
        private String userAgent;
        private String ipAddress;
        private String username;
    }

    /**
     * Session Statistics holder
     */
    @lombok.Data
    @lombok.Builder
    public static class SessionStatistics {
        private long activeSessionCount;
        private long totalSessionsCreated;
        private long averageSessionDuration; // in minutes
    }
}