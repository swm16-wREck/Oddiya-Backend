package com.oddiya.actuator;

import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.oddiya.repository.TravelPlanRepository;
import com.oddiya.repository.UserRepository;
import com.oddiya.repository.PlaceRepository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;

/**
 * Collector for business metrics and performance data
 * Provides efficient data collection methods for monitoring and observability
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BusinessMetricsCollector {

    private final TravelPlanRepository travelPlanRepository;
    private final UserRepository userRepository;
    private final PlaceRepository placeRepository;
    private final DataSource dataSource;

    // Performance counters
    private final AtomicLong aiRequestCount = new AtomicLong(0);
    private final AtomicLong aiFailureCount = new AtomicLong(0);
    private final DoubleAdder aiResponseTimeSum = new DoubleAdder();
    
    private final AtomicLong apiRequestCount = new AtomicLong(0);
    private final AtomicLong apiErrorCount = new AtomicLong(0);
    private final DoubleAdder apiResponseTimeSum = new DoubleAdder();
    
    // External service metrics
    private final Map<String, ServiceMetrics> externalServiceMetrics = new ConcurrentHashMap<>();
    
    // Cache metrics
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    private final AtomicLong cacheEvictions = new AtomicLong(0);
    
    // Database metrics
    private final DoubleAdder queryTimeSum = new DoubleAdder();
    private final AtomicLong queryCount = new AtomicLong(0);
    private final AtomicLong slowQueryCount = new AtomicLong(0);

    // User engagement methods
    public long getActiveUsersCount(LocalDateTime since) {
        try {
            String sql = """
                SELECT COUNT(DISTINCT tp.user_id) 
                FROM travel_plans tp 
                WHERE tp.created_at >= ?
                """;
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                
                ps.setObject(1, since);
                ResultSet rs = ps.executeQuery();
                
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } catch (SQLException e) {
            log.error("Error getting active users count: {}", e.getMessage());
        }
        return 0;
    }

    // AI usage methods
    public long getAIGeneratedPlansCount() {
        try {
            String sql = """
                SELECT COUNT(*) 
                FROM travel_plans 
                WHERE ai_generated = true
                """;
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } catch (SQLException e) {
            log.error("Error getting AI generated plans count: {}", e.getMessage());
        }
        return 0;
    }

    public double getAverageAIResponseTime() {
        long count = aiRequestCount.get();
        return count > 0 ? aiResponseTimeSum.sum() / count : 0.0;
    }

    public double getAISuccessRate() {
        long total = aiRequestCount.get();
        long failures = aiFailureCount.get();
        return total > 0 ? (double)(total - failures) / total * 100.0 : 100.0;
    }

    public long getAIFailureCount() {
        return aiFailureCount.get();
    }

    public long getDailyAIRequests() {
        return getAIRequestsSince(LocalDateTime.now().minusDays(1));
    }

    public long getWeeklyAIRequests() {
        return getAIRequestsSince(LocalDateTime.now().minusDays(7));
    }

    public long getMonthlyAIRequests() {
        return getAIRequestsSince(LocalDateTime.now().minusDays(30));
    }

    private long getAIRequestsSince(LocalDateTime since) {
        try {
            String sql = """
                SELECT COUNT(*) 
                FROM travel_plans 
                WHERE ai_generated = true AND created_at >= ?
                """;
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                
                ps.setObject(1, since);
                ResultSet rs = ps.executeQuery();
                
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } catch (SQLException e) {
            log.error("Error getting AI requests since {}: {}", since, e.getMessage());
        }
        return 0;
    }

    // Travel plan metrics methods
    public long getPlansCreatedSince(LocalDateTime since) {
        try {
            String sql = """
                SELECT COUNT(*) 
                FROM travel_plans 
                WHERE created_at >= ?
                """;
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                
                ps.setObject(1, since);
                ResultSet rs = ps.executeQuery();
                
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } catch (SQLException e) {
            log.error("Error getting plans created since {}: {}", since, e.getMessage());
        }
        return 0;
    }

    public long getPlansCreatedBetween(LocalDateTime start, LocalDateTime end) {
        try {
            String sql = """
                SELECT COUNT(*) 
                FROM travel_plans 
                WHERE created_at >= ? AND created_at < ?
                """;
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                
                ps.setObject(1, start);
                ps.setObject(2, end);
                ResultSet rs = ps.executeQuery();
                
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } catch (SQLException e) {
            log.error("Error getting plans created between {} and {}: {}", start, end, e.getMessage());
        }
        return 0;
    }

    public double getAveragePlanRating() {
        try {
            String sql = """
                SELECT AVG(r.rating) 
                FROM reviews r 
                INNER JOIN travel_plans tp ON r.travel_plan_id = tp.id
                """;
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    return rs.getDouble(1);
                }
            }
        } catch (SQLException e) {
            log.error("Error getting average plan rating: {}", e.getMessage());
        }
        return 0.0;
    }

    public long getPublicPlansCount() {
        try {
            String sql = """
                SELECT COUNT(*) 
                FROM travel_plans 
                WHERE is_public = true
                """;
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } catch (SQLException e) {
            log.error("Error getting public plans count: {}", e.getMessage());
        }
        return 0;
    }

    public long getSharedPlansCount() {
        // For now, assume shared plans are public plans
        // This can be enhanced with a separate sharing table
        return getPublicPlansCount();
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getPopularDestinations(int limit) {
        try {
            String sql = """
                SELECT 
                    tp.destination,
                    COUNT(*) as plan_count,
                    AVG(r.rating) as avg_rating
                FROM travel_plans tp
                LEFT JOIN reviews r ON tp.id = r.travel_plan_id
                WHERE tp.destination IS NOT NULL
                GROUP BY tp.destination
                ORDER BY plan_count DESC, avg_rating DESC
                LIMIT ?
                """;
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                
                ps.setInt(1, limit);
                ResultSet rs = ps.executeQuery();
                
                return List.of(); // Simplified for now - would need proper mapping
            }
        } catch (SQLException e) {
            log.error("Error getting popular destinations: {}", e.getMessage());
        }
        return List.of();
    }

    // Performance metrics methods
    public double getAverageResponseTime() {
        long count = apiRequestCount.get();
        return count > 0 ? apiResponseTimeSum.sum() / count : 0.0;
    }

    public double getP95ResponseTime() {
        // Simplified - in production would use histogram
        return getAverageResponseTime() * 1.5;
    }

    public double getP99ResponseTime() {
        // Simplified - in production would use histogram
        return getAverageResponseTime() * 2.0;
    }

    public double getErrorRate() {
        long total = apiRequestCount.get();
        long errors = apiErrorCount.get();
        return total > 0 ? (double) errors / total * 100.0 : 0.0;
    }

    public double getRequestsPerMinute() {
        // Simplified calculation - would need time window tracking
        return apiRequestCount.get() / 60.0;
    }

    public double getRequestsPerHour() {
        return getRequestsPerMinute() * 60;
    }

    // External service metrics
    public double getNaverMapsResponseTime() {
        return getServiceMetric("naver-maps").getAverageResponseTime();
    }

    public double getNaverMapsSuccessRate() {
        return getServiceMetric("naver-maps").getSuccessRate();
    }

    public long getNaverMapsRequestCount() {
        return getServiceMetric("naver-maps").getRequestCount();
    }

    public double getBedrockResponseTime() {
        return getServiceMetric("bedrock").getAverageResponseTime();
    }

    public double getBedrockSuccessRate() {
        return getServiceMetric("bedrock").getSuccessRate();
    }

    public long getBedrockRequestCount() {
        return getServiceMetric("bedrock").getRequestCount();
    }

    public double getSupabaseResponseTime() {
        return getServiceMetric("supabase").getAverageResponseTime();
    }

    public double getSupabaseSuccessRate() {
        return getServiceMetric("supabase").getSuccessRate();
    }

    public long getSupabaseRequestCount() {
        return getServiceMetric("supabase").getRequestCount();
    }

    private ServiceMetrics getServiceMetric(String serviceName) {
        return externalServiceMetrics.computeIfAbsent(serviceName, k -> new ServiceMetrics());
    }

    // Database metrics
    public int getActiveConnectionCount() {
        try {
            String sql = """
                SELECT COUNT(*) 
                FROM pg_stat_activity 
                WHERE state = 'active' AND datname = current_database()
                """;
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            log.debug("Error getting active connection count (may not have permissions): {}", e.getMessage());
        }
        return 0;
    }

    public int getIdleConnectionCount() {
        try {
            String sql = """
                SELECT COUNT(*) 
                FROM pg_stat_activity 
                WHERE state = 'idle' AND datname = current_database()
                """;
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            log.debug("Error getting idle connection count (may not have permissions): {}", e.getMessage());
        }
        return 0;
    }

    public double getAverageQueryTime() {
        long count = queryCount.get();
        return count > 0 ? queryTimeSum.sum() / count : 0.0;
    }

    public long getSlowQueryCount() {
        return slowQueryCount.get();
    }

    // Cache metrics
    public double getCacheHitRate() {
        long hits = cacheHits.get();
        long misses = cacheMisses.get();
        long total = hits + misses;
        return total > 0 ? (double) hits / total * 100.0 : 0.0;
    }

    public double getCacheMissRate() {
        return 100.0 - getCacheHitRate();
    }

    public long getCacheEvictionCount() {
        return cacheEvictions.get();
    }

    public long getCacheSize() {
        // This would need integration with actual cache implementation
        return 0;
    }

    // Recording methods for metrics collection
    public void recordAIRequest(long responseTimeMs, boolean success) {
        aiRequestCount.incrementAndGet();
        aiResponseTimeSum.add(responseTimeMs);
        if (!success) {
            aiFailureCount.incrementAndGet();
        }
    }

    public void recordAPIRequest(long responseTimeMs, boolean success) {
        apiRequestCount.incrementAndGet();
        apiResponseTimeSum.add(responseTimeMs);
        if (!success) {
            apiErrorCount.incrementAndGet();
        }
    }

    public void recordExternalServiceCall(String serviceName, long responseTimeMs, boolean success) {
        ServiceMetrics metrics = getServiceMetric(serviceName);
        metrics.recordRequest(responseTimeMs, success);
    }

    public void recordCacheHit() {
        cacheHits.incrementAndGet();
    }

    public void recordCacheMiss() {
        cacheMisses.incrementAndGet();
    }

    public void recordCacheEviction() {
        cacheEvictions.incrementAndGet();
    }

    public void recordDatabaseQuery(long executionTimeMs) {
        queryCount.incrementAndGet();
        queryTimeSum.add(executionTimeMs);
        
        if (executionTimeMs > 1000) { // Slow query threshold: 1 second
            slowQueryCount.incrementAndGet();
        }
    }

    // Inner class for service metrics
    private static class ServiceMetrics {
        private final AtomicLong requestCount = new AtomicLong(0);
        private final AtomicLong errorCount = new AtomicLong(0);
        private final DoubleAdder responseTimeSum = new DoubleAdder();

        public void recordRequest(long responseTimeMs, boolean success) {
            requestCount.incrementAndGet();
            responseTimeSum.add(responseTimeMs);
            if (!success) {
                errorCount.incrementAndGet();
            }
        }

        public long getRequestCount() {
            return requestCount.get();
        }

        public double getAverageResponseTime() {
            long count = requestCount.get();
            return count > 0 ? responseTimeSum.sum() / count : 0.0;
        }

        public double getSuccessRate() {
            long total = requestCount.get();
            long errors = errorCount.get();
            return total > 0 ? (double)(total - errors) / total * 100.0 : 100.0;
        }
    }
}