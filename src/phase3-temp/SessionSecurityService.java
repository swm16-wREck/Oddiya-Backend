package com.oddiya.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Session Security Service
 * Agent 4 - Session Management Specialist
 * 
 * Provides enhanced session security features including concurrent session control,
 * session fixation protection, and security monitoring.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionSecurityService {

    private final RedisTemplate<String, Object> sessionRedisTemplate;
    private final SessionRegistry sessionRegistry;
    private final int maxConcurrentSessions;
    private final boolean preventInvalidSessionCreation;

    private static final String SESSION_SECURITY_PREFIX = "session:security:";
    private static final String USER_SESSIONS_PREFIX = "user:sessions:";
    private static final String SUSPICIOUS_ACTIVITY_PREFIX = "suspicious:activity:";
    private static final String SESSION_FINGERPRINT_PREFIX = "session:fingerprint:";

    /**
     * Enforce concurrent session limits for a user
     */
    public boolean enforceConcurrentSessionLimit(String username, String currentSessionId) {
        try {
            String userSessionsKey = USER_SESSIONS_PREFIX + username;
            Set<Object> userSessions = sessionRedisTemplate.opsForSet().members(userSessionsKey);

            if (userSessions == null) {
                userSessions = new HashSet<>();
            }

            // Add current session
            sessionRedisTemplate.opsForSet().add(userSessionsKey, currentSessionId);
            sessionRedisTemplate.expire(userSessionsKey, 24, TimeUnit.HOURS);

            // Check if we exceed the limit
            if (userSessions.size() >= maxConcurrentSessions) {
                log.warn("User {} exceeded concurrent session limit ({}/{}). Invalidating oldest sessions.",
                        username, userSessions.size(), maxConcurrentSessions);

                // Get session information for all user sessions
                List<SessionInformation> sessionInfos = sessionRegistry.getAllSessions(username, false);
                
                if (sessionInfos.size() > maxConcurrentSessions) {
                    // Sort by last request time and invalidate oldest sessions
                    sessionInfos.sort(Comparator.comparing(SessionInformation::getLastRequest));
                    
                    int sessionsToInvalidate = sessionInfos.size() - maxConcurrentSessions;
                    for (int i = 0; i < sessionsToInvalidate; i++) {
                        SessionInformation sessionInfo = sessionInfos.get(i);
                        sessionInfo.expireNow();
                        
                        // Remove from Redis tracking
                        sessionRedisTemplate.opsForSet().remove(userSessionsKey, sessionInfo.getSessionId());
                        
                        log.info("Invalidated session {} for user {} due to concurrent session limit",
                                sessionInfo.getSessionId(), username);
                    }
                }
                
                return false; // Limit was exceeded
            }

            return true; // Within limits
            
        } catch (Exception e) {
            log.error("Error enforcing concurrent session limit for user: {}", username, e);
            return true; // Allow session creation on error to avoid blocking users
        }
    }

    /**
     * Create and validate session fingerprint for additional security
     */
    public boolean validateSessionFingerprint(HttpServletRequest request, String sessionId) {
        try {
            String fingerprint = generateSessionFingerprint(request);
            String fingerprintKey = SESSION_FINGERPRINT_PREFIX + sessionId;
            
            String storedFingerprint = (String) sessionRedisTemplate.opsForValue().get(fingerprintKey);
            
            if (storedFingerprint == null) {
                // First time - store the fingerprint
                sessionRedisTemplate.opsForValue().set(fingerprintKey, fingerprint, 
                        Duration.ofHours(24));
                return true;
            }
            
            // Validate fingerprint matches
            boolean matches = Objects.equals(fingerprint, storedFingerprint);
            
            if (!matches) {
                log.warn("Session fingerprint mismatch for session {}: expected={}, actual={}",
                        sessionId, storedFingerprint, fingerprint);
                
                // Record suspicious activity
                recordSuspiciousActivity(request, "SESSION_FINGERPRINT_MISMATCH", sessionId);
            }
            
            return matches;
            
        } catch (Exception e) {
            log.error("Error validating session fingerprint for session: {}", sessionId, e);
            return true; // Allow on error to avoid blocking legitimate users
        }
    }

    /**
     * Generate session fingerprint from request characteristics
     */
    private String generateSessionFingerprint(HttpServletRequest request) {
        StringBuilder fingerprint = new StringBuilder();
        
        // User Agent (partial - browsers may update)
        String userAgent = request.getHeader("User-Agent");
        if (userAgent != null && userAgent.length() > 20) {
            fingerprint.append(userAgent.substring(0, 20));
        }
        
        // Accept Language
        String acceptLanguage = request.getHeader("Accept-Language");
        if (acceptLanguage != null) {
            fingerprint.append("|").append(acceptLanguage);
        }
        
        // Accept Encoding
        String acceptEncoding = request.getHeader("Accept-Encoding");
        if (acceptEncoding != null) {
            fingerprint.append("|").append(acceptEncoding);
        }
        
        // X-Requested-With (for AJAX requests)
        String xRequestedWith = request.getHeader("X-Requested-With");
        if (xRequestedWith != null) {
            fingerprint.append("|").append(xRequestedWith);
        }
        
        return Integer.toString(fingerprint.toString().hashCode());
    }

    /**
     * Record suspicious activity for monitoring and analysis
     */
    public void recordSuspiciousActivity(HttpServletRequest request, String activityType, String sessionId) {
        try {
            String ipAddress = getClientIpAddress(request);
            String userAgent = request.getHeader("User-Agent");
            String username = getCurrentUsername();
            
            Map<String, Object> activityData = new HashMap<>();
            activityData.put("timestamp", Instant.now().toString());
            activityData.put("sessionId", sessionId);
            activityData.put("username", username);
            activityData.put("ipAddress", ipAddress);
            activityData.put("userAgent", userAgent);
            activityData.put("activityType", activityType);
            activityData.put("requestUri", request.getRequestURI());
            
            String activityKey = SUSPICIOUS_ACTIVITY_PREFIX + ipAddress + ":" + Instant.now().getEpochSecond();
            sessionRedisTemplate.opsForHash().putAll(activityKey, activityData);
            sessionRedisTemplate.expire(activityKey, 7, TimeUnit.DAYS);
            
            log.warn("SECURITY: Suspicious activity recorded - Type: {}, IP: {}, Session: {}, User: {}",
                    activityType, ipAddress, sessionId, username);
            
            // Check for repeated suspicious activity from same IP
            checkForRepeatedSuspiciousActivity(ipAddress);
            
        } catch (Exception e) {
            log.error("Error recording suspicious activity", e);
        }
    }

    /**
     * Check for repeated suspicious activity from same IP
     */
    private void checkForRepeatedSuspiciousActivity(String ipAddress) {
        try {
            String pattern = SUSPICIOUS_ACTIVITY_PREFIX + ipAddress + ":*";
            Set<String> suspiciousKeys = sessionRedisTemplate.keys(pattern);
            
            if (suspiciousKeys != null && suspiciousKeys.size() > 5) {
                log.error("SECURITY ALERT: Multiple suspicious activities ({}) from IP: {}. " +
                         "Consider blocking this IP address.", suspiciousKeys.size(), ipAddress);
                
                // In production, you might want to:
                // 1. Automatically block the IP address
                // 2. Send alert to security team
                // 3. Implement rate limiting
                // 4. Require additional authentication
            }
            
        } catch (Exception e) {
            log.error("Error checking repeated suspicious activity", e);
        }
    }

    /**
     * Invalidate all sessions for a user (e.g., on password change)
     */
    public void invalidateAllUserSessions(String username, String exceptSessionId) {
        try {
            List<SessionInformation> sessionInfos = sessionRegistry.getAllSessions(username, false);
            
            int invalidatedCount = 0;
            for (SessionInformation sessionInfo : sessionInfos) {
                if (!Objects.equals(sessionInfo.getSessionId(), exceptSessionId)) {
                    sessionInfo.expireNow();
                    invalidatedCount++;
                }
            }
            
            // Clean up Redis tracking
            String userSessionsKey = USER_SESSIONS_PREFIX + username;
            if (exceptSessionId != null) {
                sessionRedisTemplate.delete(userSessionsKey);
                sessionRedisTemplate.opsForSet().add(userSessionsKey, exceptSessionId);
                sessionRedisTemplate.expire(userSessionsKey, 24, TimeUnit.HOURS);
            } else {
                sessionRedisTemplate.delete(userSessionsKey);
            }
            
            log.info("Invalidated {} sessions for user {} (kept session: {})",
                    invalidatedCount, username, exceptSessionId);
            
        } catch (Exception e) {
            log.error("Error invalidating user sessions for: {}", username, e);
        }
    }

    /**
     * Check if session is valid and not compromised
     */
    public boolean isSessionSecure(String sessionId, HttpServletRequest request) {
        try {
            // Check session fingerprint
            if (!validateSessionFingerprint(request, sessionId)) {
                return false;
            }
            
            // Check for session fixation attempts
            if (detectSessionFixation(sessionId, request)) {
                recordSuspiciousActivity(request, "SESSION_FIXATION_ATTEMPT", sessionId);
                return false;
            }
            
            // Check session age - invalidate very old sessions
            if (isSessionTooOld(sessionId)) {
                log.info("Session {} is too old, invalidating for security", sessionId);
                return false;
            }
            
            return true;
            
        } catch (Exception e) {
            log.error("Error checking session security for session: {}", sessionId, e);
            return true; // Allow on error to avoid blocking users
        }
    }

    /**
     * Detect session fixation attempts
     */
    private boolean detectSessionFixation(String sessionId, HttpServletRequest request) {
        try {
            // Check if session ID was provided in URL parameters (potential fixation)
            String sessionParam = request.getParameter("jsessionid");
            if (sessionParam != null) {
                log.warn("Session ID found in URL parameters - potential session fixation attempt");
                return true;
            }
            
            // Additional checks can be added here
            return false;
            
        } catch (Exception e) {
            log.error("Error detecting session fixation", e);
            return false;
        }
    }

    /**
     * Check if session is too old for security purposes
     */
    private boolean isSessionTooOld(String sessionId) {
        try {
            String creationTimeKey = SESSION_SECURITY_PREFIX + "creation:" + sessionId;
            String creationTimeStr = (String) sessionRedisTemplate.opsForValue().get(creationTimeKey);
            
            if (creationTimeStr == null) {
                // Store creation time if not exists
                sessionRedisTemplate.opsForValue().set(creationTimeKey, 
                        Instant.now().toString(), Duration.ofHours(24));
                return false;
            }
            
            Instant creationTime = Instant.parse(creationTimeStr);
            Duration sessionAge = Duration.between(creationTime, Instant.now());
            
            // Invalidate sessions older than 24 hours for security
            return sessionAge.toHours() > 24;
            
        } catch (Exception e) {
            log.error("Error checking session age", e);
            return false;
        }
    }

    /**
     * Get client IP address considering proxies and load balancers
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String[] headerNames = {
            "X-Forwarded-For",
            "X-Real-IP", 
            "X-Forwarded",
            "X-Cluster-Client-IP",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP"
        };
        
        for (String headerName : headerNames) {
            String ip = request.getHeader(headerName);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                // X-Forwarded-For can contain multiple IPs, get the first one
                if (ip.contains(",")) {
                    ip = ip.split(",")[0].trim();
                }
                return ip;
            }
        }
        
        return request.getRemoteAddr();
    }

    /**
     * Get current username from security context
     */
    private String getCurrentUsername() {
        try {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            return authentication != null && authentication.isAuthenticated() 
                    ? authentication.getName() : "anonymous";
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * Get security statistics for monitoring
     */
    public Map<String, Object> getSecurityStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            // Count suspicious activities in last 24 hours
            Set<String> suspiciousKeys = sessionRedisTemplate.keys(SUSPICIOUS_ACTIVITY_PREFIX + "*");
            stats.put("suspiciousActivitiesLast24h", suspiciousKeys != null ? suspiciousKeys.size() : 0);
            
            // Count active user session tracking entries
            Set<String> userSessionKeys = sessionRedisTemplate.keys(USER_SESSIONS_PREFIX + "*");
            stats.put("usersWithActiveSessions", userSessionKeys != null ? userSessionKeys.size() : 0);
            
            // Count session fingerprints
            Set<String> fingerprintKeys = sessionRedisTemplate.keys(SESSION_FINGERPRINT_PREFIX + "*");
            stats.put("sessionFingerprints", fingerprintKeys != null ? fingerprintKeys.size() : 0);
            
            stats.put("maxConcurrentSessionsPerUser", maxConcurrentSessions);
            stats.put("preventInvalidSessionCreation", preventInvalidSessionCreation);
            
        } catch (Exception e) {
            log.error("Error collecting security statistics", e);
            stats.put("error", e.getMessage());
        }
        
        return stats;
    }
}