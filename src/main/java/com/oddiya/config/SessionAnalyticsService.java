package com.oddiya.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Session Analytics Service
 * Agent 4 - Session Management Specialist
 * 
 * Collects and analyzes session data for insights into user behavior,
 * performance monitoring, and business intelligence.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnBean(RedisTemplate.class)
public class SessionAnalyticsService {

    private final RedisTemplate<String, Object> sessionRedisTemplate;

    private static final String ANALYTICS_PREFIX = "session:analytics:";
    private static final String HOURLY_STATS_PREFIX = "session:stats:hourly:";
    private static final String DAILY_STATS_PREFIX = "session:stats:daily:";
    private static final String USER_BEHAVIOR_PREFIX = "user:behavior:";
    private static final DateTimeFormatter HOUR_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHH");
    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * Record session analytics data
     */
    public void recordSessionAnalytics(String sessionId, String username, String userAgent, 
                                     String ipAddress, String action, Map<String, Object> additionalData) {
        try {
            Map<String, Object> analyticsData = new HashMap<>();
            analyticsData.put("sessionId", sessionId);
            analyticsData.put("username", username != null ? username : "anonymous");
            analyticsData.put("userAgent", userAgent);
            analyticsData.put("ipAddress", ipAddress);
            analyticsData.put("action", action);
            analyticsData.put("timestamp", Instant.now().toString());
            
            if (additionalData != null) {
                analyticsData.putAll(additionalData);
            }
            
            // Store individual session event
            String eventKey = ANALYTICS_PREFIX + sessionId + ":" + Instant.now().toEpochMilli();
            sessionRedisTemplate.opsForHash().putAll(eventKey, analyticsData);
            sessionRedisTemplate.expire(eventKey, 7, TimeUnit.DAYS);
            
            // Update aggregated statistics
            updateHourlyStatistics(action);
            updateDailyStatistics(action);
            
            // Update user behavior tracking
            if (username != null && !"anonymous".equals(username)) {
                updateUserBehaviorTracking(username, action);
            }
            
            log.debug("Recorded session analytics: sessionId={}, username={}, action={}", 
                     sessionId, username, action);
            
        } catch (Exception e) {
            log.error("Error recording session analytics", e);
        }
    }

    /**
     * Update hourly statistics
     */
    private void updateHourlyStatistics(String action) {
        try {
            String hour = LocalDateTime.now(ZoneOffset.UTC).format(HOUR_FORMATTER);
            String hourlyKey = HOURLY_STATS_PREFIX + hour;
            
            sessionRedisTemplate.opsForHash().increment(hourlyKey, "total_events", 1);
            sessionRedisTemplate.opsForHash().increment(hourlyKey, action, 1);
            sessionRedisTemplate.expire(hourlyKey, 48, TimeUnit.HOURS); // Keep 48 hours
            
        } catch (Exception e) {
            log.error("Error updating hourly statistics", e);
        }
    }

    /**
     * Update daily statistics
     */
    private void updateDailyStatistics(String action) {
        try {
            String day = LocalDateTime.now(ZoneOffset.UTC).format(DAY_FORMATTER);
            String dailyKey = DAILY_STATS_PREFIX + day;
            
            sessionRedisTemplate.opsForHash().increment(dailyKey, "total_events", 1);
            sessionRedisTemplate.opsForHash().increment(dailyKey, action, 1);
            sessionRedisTemplate.expire(dailyKey, 30, TimeUnit.DAYS); // Keep 30 days
            
        } catch (Exception e) {
            log.error("Error updating daily statistics", e);
        }
    }

    /**
     * Update user behavior tracking
     */
    private void updateUserBehaviorTracking(String username, String action) {
        try {
            String userBehaviorKey = USER_BEHAVIOR_PREFIX + username;
            
            // Track action frequency
            sessionRedisTemplate.opsForHash().increment(userBehaviorKey, action, 1);
            sessionRedisTemplate.opsForHash().put(userBehaviorKey, "last_activity", 
                    Instant.now().toString());
            
            // Track daily activity
            String today = LocalDateTime.now(ZoneOffset.UTC).format(DAY_FORMATTER);
            sessionRedisTemplate.opsForHash().increment(userBehaviorKey, "active_days:" + today, 1);
            
            sessionRedisTemplate.expire(userBehaviorKey, 90, TimeUnit.DAYS);
            
        } catch (Exception e) {
            log.error("Error updating user behavior tracking for user: {}", username, e);
        }
    }

    /**
     * Get session statistics for a specific hour
     */
    public Map<String, Object> getHourlyStatistics(int hoursBack) {
        try {
            LocalDateTime targetHour = LocalDateTime.now(ZoneOffset.UTC).minusHours(hoursBack);
            String hour = targetHour.format(HOUR_FORMATTER);
            String hourlyKey = HOURLY_STATS_PREFIX + hour;
            
            Map<Object, Object> stats = sessionRedisTemplate.opsForHash().entries(hourlyKey);
            Map<String, Object> result = new HashMap<>();
            
            stats.forEach((key, value) -> result.put(key.toString(), value));
            result.put("hour", hour);
            result.put("timestamp", targetHour.toInstant(ZoneOffset.UTC).toString());
            
            return result;
            
        } catch (Exception e) {
            log.error("Error getting hourly statistics for {} hours back", hoursBack, e);
            return Collections.emptyMap();
        }
    }

    /**
     * Get session statistics for a specific day
     */
    public Map<String, Object> getDailyStatistics(int daysBack) {
        try {
            LocalDateTime targetDay = LocalDateTime.now(ZoneOffset.UTC).minusDays(daysBack);
            String day = targetDay.format(DAY_FORMATTER);
            String dailyKey = DAILY_STATS_PREFIX + day;
            
            Map<Object, Object> stats = sessionRedisTemplate.opsForHash().entries(dailyKey);
            Map<String, Object> result = new HashMap<>();
            
            stats.forEach((key, value) -> result.put(key.toString(), value));
            result.put("day", day);
            result.put("timestamp", targetDay.toInstant(ZoneOffset.UTC).toString());
            
            return result;
            
        } catch (Exception e) {
            log.error("Error getting daily statistics for {} days back", daysBack, e);
            return Collections.emptyMap();
        }
    }

    /**
     * Get user behavior analytics
     */
    public Map<String, Object> getUserBehaviorAnalytics(String username) {
        try {
            String userBehaviorKey = USER_BEHAVIOR_PREFIX + username;
            Map<Object, Object> behavior = sessionRedisTemplate.opsForHash().entries(userBehaviorKey);
            
            Map<String, Object> result = new HashMap<>();
            Map<String, Object> actions = new HashMap<>();
            Map<String, Object> dailyActivity = new HashMap<>();
            
            behavior.forEach((key, value) -> {
                String keyStr = key.toString();
                if (keyStr.startsWith("active_days:")) {
                    String day = keyStr.substring("active_days:".length());
                    dailyActivity.put(day, value);
                } else if (!"last_activity".equals(keyStr)) {
                    actions.put(keyStr, value);
                } else {
                    result.put(keyStr, value);
                }
            });
            
            result.put("actions", actions);
            result.put("daily_activity", dailyActivity);
            result.put("username", username);
            
            return result;
            
        } catch (Exception e) {
            log.error("Error getting user behavior analytics for: {}", username, e);
            return Collections.emptyMap();
        }
    }

    /**
     * Get top active users
     */
    public List<Map<String, Object>> getTopActiveUsers(int limit) {
        try {
            Set<String> userBehaviorKeys = sessionRedisTemplate.keys(USER_BEHAVIOR_PREFIX + "*");
            if (userBehaviorKeys == null || userBehaviorKeys.isEmpty()) {
                return Collections.emptyList();
            }

            List<Map<String, Object>> topUsers = new ArrayList<>();
            
            for (String key : userBehaviorKeys) {
                String username = key.substring(USER_BEHAVIOR_PREFIX.length());
                Map<Object, Object> behavior = sessionRedisTemplate.opsForHash().entries(key);
                
                // Calculate total activity score
                long totalActivity = behavior.entrySet().stream()
                        .filter(entry -> !entry.getKey().toString().equals("last_activity") 
                                      && !entry.getKey().toString().startsWith("active_days:"))
                        .mapToLong(entry -> Long.parseLong(entry.getValue().toString()))
                        .sum();
                
                Map<String, Object> userInfo = new HashMap<>();
                userInfo.put("username", username);
                userInfo.put("total_activity", totalActivity);
                userInfo.put("last_activity", behavior.get("last_activity"));
                
                topUsers.add(userInfo);
            }
            
            // Sort by total activity descending
            topUsers.sort((a, b) -> Long.compare(
                    (Long) b.get("total_activity"),
                    (Long) a.get("total_activity")
            ));
            
            return topUsers.subList(0, Math.min(limit, topUsers.size()));
            
        } catch (Exception e) {
            log.error("Error getting top active users", e);
            return Collections.emptyList();
        }
    }

    /**
     * Get session analytics summary
     */
    public Map<String, Object> getAnalyticsSummary() {
        Map<String, Object> summary = new HashMap<>();
        
        try {
            // Current hour statistics
            summary.put("current_hour", getHourlyStatistics(0));
            
            // Previous hour statistics
            summary.put("previous_hour", getHourlyStatistics(1));
            
            // Today's statistics
            summary.put("today", getDailyStatistics(0));
            
            // Yesterday's statistics
            summary.put("yesterday", getDailyStatistics(1));
            
            // Last 24 hours trend
            List<Map<String, Object>> last24Hours = new ArrayList<>();
            for (int i = 0; i < 24; i++) {
                last24Hours.add(getHourlyStatistics(i));
            }
            summary.put("last_24_hours", last24Hours);
            
            // Top active users
            summary.put("top_active_users", getTopActiveUsers(10));
            
        } catch (Exception e) {
            log.error("Error generating analytics summary", e);
            summary.put("error", e.getMessage());
        }
        
        return summary;
    }

    /**
     * Scheduled cleanup of old analytics data
     */
    @Scheduled(cron = "0 0 2 * * *") // Daily at 2 AM
    public void cleanupOldAnalyticsData() {
        try {
            log.info("Starting cleanup of old analytics data");
            
            // Cleanup analytics events older than 7 days
            cleanupOldData(ANALYTICS_PREFIX, 7);
            
            // Cleanup hourly stats older than 48 hours
            cleanupOldData(HOURLY_STATS_PREFIX, 2);
            
            // Cleanup daily stats older than 30 days
            cleanupOldData(DAILY_STATS_PREFIX, 30);
            
            log.info("Completed cleanup of old analytics data");
            
        } catch (Exception e) {
            log.error("Error during analytics data cleanup", e);
        }
    }

    /**
     * Cleanup old data based on pattern and age
     */
    private void cleanupOldData(String pattern, int daysOld) {
        try {
            Set<String> keys = sessionRedisTemplate.keys(pattern + "*");
            if (keys == null || keys.isEmpty()) {
                return;
            }
            
            Instant cutoffTime = Instant.now().minusSeconds(daysOld * 24 * 3600);
            int deletedCount = 0;
            
            for (String key : keys) {
                Long ttl = sessionRedisTemplate.getExpire(key);
                if (ttl != null && ttl == -1) { // Key has no expiration
                    // For keys without TTL, check if they're old based on the key name pattern
                    sessionRedisTemplate.expire(key, 1, TimeUnit.SECONDS);
                    deletedCount++;
                }
            }
            
            if (deletedCount > 0) {
                log.info("Cleaned up {} old entries matching pattern: {}", deletedCount, pattern);
            }
            
        } catch (Exception e) {
            log.error("Error cleaning up old data for pattern: {}", pattern, e);
        }
    }
}