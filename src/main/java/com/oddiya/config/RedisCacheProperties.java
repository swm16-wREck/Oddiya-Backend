package com.oddiya.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Redis Cache Configuration Properties
 * Agent 3 - Application Caching Engineer
 * 
 * Configuration properties for Redis cache settings, TTLs, and monitoring.
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.cache")
public class RedisCacheProperties {

    /**
     * Cache TTL configurations in milliseconds
     */
    private Map<String, Long> ttl = new HashMap<>();

    /**
     * Cache size limits (number of entries)
     */
    private Map<String, Integer> maxSize = new HashMap<>();

    /**
     * Cache key patterns for dynamic key generation
     */
    private Map<String, String> keyPatterns = new HashMap<>();

    /**
     * Cache warming configuration
     */
    private WarmUp warmUp = new WarmUp();

    /**
     * Cache invalidation settings
     */
    private Invalidation invalidation = new Invalidation();

    /**
     * Cache monitoring configuration
     */
    private Monitoring monitoring = new Monitoring();

    /**
     * Distributed locks configuration
     */
    private DistributedLocks distributedLocks = new DistributedLocks();

    /**
     * Cache serialization settings
     */
    private Serialization serialization = new Serialization();

    @Data
    public static class WarmUp {
        private boolean enabled = true;
        private boolean onStartup = true;
        private boolean popularPlaces = true;
        private boolean trendingPlans = true;
        private boolean statistics = true;
    }

    @Data
    public static class Invalidation {
        private boolean autoInvalidate = true;
        private boolean batchInvalidation = true;
        private boolean asyncInvalidation = true;
    }

    @Data
    public static class Monitoring {
        private boolean enabled = true;
        private boolean metricsEnabled = true;
        private double hitRateThreshold = 0.8;
        private double evictionRateThreshold = 0.1;
    }

    @Data
    public static class DistributedLocks {
        private boolean enabled = true;
        private long waitTime = 10000; // milliseconds
        private long leaseTime = 30000; // milliseconds
    }

    @Data
    public static class Serialization {
        private String format = "json";
        private Compression compression = new Compression();

        @Data
        public static class Compression {
            private boolean enabled = true;
            private int minSize = 1024; // bytes
            private String algorithm = "gzip";
        }
    }
}