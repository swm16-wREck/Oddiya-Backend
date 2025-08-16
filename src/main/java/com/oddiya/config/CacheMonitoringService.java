package com.oddiya.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Cache Monitoring Service
 * Agent 3 - Application Caching Engineer
 * 
 * Service to monitor cache performance, hit rates, and health metrics.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnBean(RedisTemplate.class)
public class CacheMonitoringService {

    private final CacheManager cacheManager;
    private final RedisTemplate<String, Object> redisTemplate;
    private final MeterRegistry meterRegistry;

    private static final String CACHE_METRICS_PREFIX = "oddiya.cache";
    private static final String REDIS_METRICS_PREFIX = "oddiya.redis";

    /**
     * Collect cache statistics every minute
     */
    @Scheduled(fixedRate = 60000) // 1 minute
    public void collectCacheMetrics() {
        try {
            collectRedisCacheMetrics();
            collectRedisServerMetrics();
        } catch (Exception e) {
            log.error("Error collecting cache metrics", e);
        }
    }

    /**
     * Collect Redis cache-specific metrics
     */
    private void collectRedisCacheMetrics() {
        // Collect metrics for each cache
        cacheManager.getCacheNames().forEach(cacheName -> {
            try {
                org.springframework.cache.Cache cache = cacheManager.getCache(cacheName);
                if (cache instanceof org.springframework.data.redis.cache.RedisCache) {
                    collectCacheStatistics(cacheName);
                }
            } catch (Exception e) {
                log.warn("Failed to collect metrics for cache: {}", cacheName, e);
            }
        });
    }

    /**
     * Collect statistics for a specific cache
     */
    private void collectCacheStatistics(String cacheName) {
        try {
            // Get cache key pattern
            String pattern = "*:" + cacheName + ":*";
            
            // Count total keys
            long keyCount = redisTemplate.keys(pattern).size();
            meterRegistry.gauge(CACHE_METRICS_PREFIX + ".keys.total", 
                               Tags.of("cache", cacheName), keyCount);

            // Get memory usage for this cache (approximate)
            long memoryUsage = estimateCacheMemoryUsage(pattern);
            meterRegistry.gauge(CACHE_METRICS_PREFIX + ".memory.bytes", 
                               Tags.of("cache", cacheName), memoryUsage);

            log.debug("Cache '{}': {} keys, ~{} bytes", cacheName, keyCount, memoryUsage);

        } catch (Exception e) {
            log.warn("Failed to collect statistics for cache: {}", cacheName, e);
        }
    }

    /**
     * Estimate memory usage for cache keys matching a pattern
     */
    private long estimateCacheMemoryUsage(String pattern) {
        try {
            // This is an approximation - Redis doesn't provide exact memory usage per pattern
            return redisTemplate.keys(pattern).stream()
                    .mapToLong(key -> {
                        try {
                            // Estimate: key size + value size
                            byte[] keyBytes = key.getBytes();
                            Object value = redisTemplate.opsForValue().get(key);
                            if (value != null) {
                                // Rough estimation - in production you might want more accurate measurement
                                return keyBytes.length + value.toString().getBytes().length;
                            }
                            return keyBytes.length;
                        } catch (Exception e) {
                            return 100; // Default estimate
                        }
                    })
                    .sum();
        } catch (Exception e) {
            log.warn("Failed to estimate memory usage for pattern: {}", pattern, e);
            return 0;
        }
    }

    /**
     * Collect Redis server metrics
     */
    private void collectRedisServerMetrics() {
        try {
            RedisConnectionFactory connectionFactory = redisTemplate.getConnectionFactory();
            if (connectionFactory != null) {
                try (RedisConnection connection = connectionFactory.getConnection()) {
                    Properties info = connection.serverCommands().info();
                    parseAndRecordRedisInfo(info);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to collect Redis server metrics", e);
        }
    }

    /**
     * Parse Redis INFO command output and record metrics
     */
    private void parseAndRecordRedisInfo(Properties info) {
        try {
            // Memory metrics
            recordMetricIfExists(info, "used_memory", REDIS_METRICS_PREFIX + ".memory.used");
            recordMetricIfExists(info, "used_memory_peak", REDIS_METRICS_PREFIX + ".memory.peak");
            recordMetricIfExists(info, "used_memory_rss", REDIS_METRICS_PREFIX + ".memory.rss");

            // Connection metrics
            recordMetricIfExists(info, "connected_clients", REDIS_METRICS_PREFIX + ".clients.connected");
            recordMetricIfExists(info, "blocked_clients", REDIS_METRICS_PREFIX + ".clients.blocked");

            // Operations metrics
            recordMetricIfExists(info, "total_commands_processed", REDIS_METRICS_PREFIX + ".commands.processed");
            recordMetricIfExists(info, "total_connections_received", REDIS_METRICS_PREFIX + ".connections.received");

            // Cache performance metrics
            recordMetricIfExists(info, "keyspace_hits", REDIS_METRICS_PREFIX + ".keyspace.hits");
            recordMetricIfExists(info, "keyspace_misses", REDIS_METRICS_PREFIX + ".keyspace.misses");

            // Calculate hit rate
            String hits = info.getProperty("keyspace_hits");
            String misses = info.getProperty("keyspace_misses");
            if (hits != null && misses != null) {
                try {
                    long hitCount = Long.parseLong(hits);
                    long missCount = Long.parseLong(misses);
                    double hitRate = hitCount + missCount > 0 ? (double) hitCount / (hitCount + missCount) : 0;
                    meterRegistry.gauge(REDIS_METRICS_PREFIX + ".hitrate", hitRate);
                } catch (NumberFormatException e) {
                    log.warn("Failed to parse hit/miss counts", e);
                }
            }

            // Persistence metrics
            recordMetricIfExists(info, "rdb_last_save_time", REDIS_METRICS_PREFIX + ".persistence.last_save");
            recordMetricIfExists(info, "aof_last_rewrite_time_sec", REDIS_METRICS_PREFIX + ".persistence.aof_rewrite");

        } catch (Exception e) {
            log.warn("Failed to parse Redis info", e);
        }
    }

    /**
     * Record metric if it exists in the properties
     */
    private void recordMetricIfExists(Properties info, String key, String metricName) {
        String value = info.getProperty(key);
        if (value != null) {
            try {
                double numericValue = Double.parseDouble(value);
                meterRegistry.gauge(metricName, numericValue);
            } catch (NumberFormatException e) {
                log.debug("Non-numeric value for key {}: {}", key, value);
            }
        }
    }

    /**
     * Get cache health status
     */
    public Map<String, Object> getCacheHealthStatus() {
        Map<String, Object> health = new HashMap<>();
        
        try {
            // Test Redis connectivity
            String pong = redisTemplate.getConnectionFactory()
                    .getConnection()
                    .ping();
            
            health.put("redis_ping", "PONG".equals(pong) ? "OK" : "FAILED");
            
            // Get basic Redis info
            Properties info = redisTemplate.getConnectionFactory()
                    .getConnection()
                    .serverCommands().info();
            
            health.put("redis_version", info.getProperty("redis_version"));
            health.put("connected_clients", info.getProperty("connected_clients"));
            health.put("used_memory_human", info.getProperty("used_memory_human"));
            health.put("uptime_in_seconds", info.getProperty("uptime_in_seconds"));
            
            // Calculate hit rate
            String hits = info.getProperty("keyspace_hits", "0");
            String misses = info.getProperty("keyspace_misses", "0");
            long hitCount = Long.parseLong(hits);
            long missCount = Long.parseLong(misses);
            double hitRate = hitCount + missCount > 0 ? (double) hitCount / (hitCount + missCount) : 0;
            health.put("hit_rate", String.format("%.2f%%", hitRate * 100));
            
            // Cache status
            health.put("cache_manager", cacheManager.getClass().getSimpleName());
            health.put("cache_names", cacheManager.getCacheNames());
            
            health.put("status", "UP");
            
        } catch (Exception e) {
            log.error("Cache health check failed", e);
            health.put("status", "DOWN");
            health.put("error", e.getMessage());
        }
        
        return health;
    }

    /**
     * Get detailed cache statistics
     */
    public Map<String, Object> getCacheStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            Properties info = redisTemplate.getConnectionFactory()
                    .getConnection()
                    .serverCommands().info();
            
            // Server info
            Map<String, Object> server = new HashMap<>();
            server.put("version", info.getProperty("redis_version"));
            server.put("mode", info.getProperty("redis_mode"));
            server.put("uptime", info.getProperty("uptime_in_seconds"));
            stats.put("server", server);
            
            // Memory info
            Map<String, Object> memory = new HashMap<>();
            memory.put("used", info.getProperty("used_memory_human"));
            memory.put("peak", info.getProperty("used_memory_peak_human"));
            memory.put("fragmentation_ratio", info.getProperty("mem_fragmentation_ratio"));
            stats.put("memory", memory);
            
            // Client info
            Map<String, Object> clients = new HashMap<>();
            clients.put("connected", info.getProperty("connected_clients"));
            clients.put("blocked", info.getProperty("blocked_clients"));
            stats.put("clients", clients);
            
            // Performance info
            Map<String, Object> performance = new HashMap<>();
            performance.put("commands_processed", info.getProperty("total_commands_processed"));
            performance.put("connections_received", info.getProperty("total_connections_received"));
            performance.put("keyspace_hits", info.getProperty("keyspace_hits"));
            performance.put("keyspace_misses", info.getProperty("keyspace_misses"));
            
            // Calculate hit rate
            String hits = info.getProperty("keyspace_hits", "0");
            String misses = info.getProperty("keyspace_misses", "0");
            long hitCount = Long.parseLong(hits);
            long missCount = Long.parseLong(misses);
            double hitRate = hitCount + missCount > 0 ? (double) hitCount / (hitCount + missCount) : 0;
            performance.put("hit_rate", hitRate);
            
            stats.put("performance", performance);
            
            // Cache-specific stats
            Map<String, Object> caches = new HashMap<>();
            cacheManager.getCacheNames().forEach(cacheName -> {
                try {
                    String pattern = "*:" + cacheName + ":*";
                    long keyCount = redisTemplate.keys(pattern).size();
                    long memoryUsage = estimateCacheMemoryUsage(pattern);
                    
                    Map<String, Object> cacheStats = new HashMap<>();
                    cacheStats.put("key_count", keyCount);
                    cacheStats.put("estimated_memory_bytes", memoryUsage);
                    
                    caches.put(cacheName, cacheStats);
                } catch (Exception e) {
                    log.warn("Failed to get stats for cache: {}", cacheName, e);
                }
            });
            stats.put("caches", caches);
            
        } catch (Exception e) {
            log.error("Failed to get cache statistics", e);
            stats.put("error", e.getMessage());
        }
        
        return stats;
    }
}