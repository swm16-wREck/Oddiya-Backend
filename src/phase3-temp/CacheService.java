package com.oddiya.cache;

import com.oddiya.config.RedisCacheProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Cache Service Implementation
 * Agent 5 - Cache Patterns Developer
 * 
 * Comprehensive cache service implementing multiple caching patterns:
 * - Cache-Aside Pattern
 * - Write-Through Pattern  
 * - Write-Behind Pattern
 * - Distributed Locking
 * - Cache Warming
 * - Intelligent Invalidation
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CacheService {

    private final CacheManager cacheManager;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisCacheProperties cacheProperties;
    private final RedissonClient redissonClient;

    private static final String LOCK_PREFIX = "cache:lock:";
    private static final String WRITE_BEHIND_QUEUE = "cache:write-behind:queue";
    private static final String CACHE_STATS_PREFIX = "cache:stats:";

    /**
     * Cache-Aside Pattern: Get from cache, load if miss, store result
     */
    public <T> T getOrLoad(String cacheName, String key, Callable<T> loader) {
        return getOrLoad(cacheName, key, loader, null);
    }

    /**
     * Cache-Aside Pattern with custom TTL
     */
    @SuppressWarnings("unchecked")
    public <T> T getOrLoad(String cacheName, String key, Callable<T> loader, Duration customTtl) {
        try {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache == null) {
                log.warn("Cache '{}' not found, executing loader directly", cacheName);
                return loader.call();
            }

            // Try to get from cache first
            Cache.ValueWrapper cachedValue = cache.get(key);
            if (cachedValue != null) {
                log.debug("Cache hit for cache '{}', key '{}'", cacheName, key);
                recordCacheHit(cacheName);
                return (T) cachedValue.get();
            }

            log.debug("Cache miss for cache '{}', key '{}'", cacheName, key);
            recordCacheMiss(cacheName);

            // Use distributed lock to prevent cache stampede
            String lockKey = LOCK_PREFIX + cacheName + ":" + key;
            RLock lock = redissonClient.getLock(lockKey);

            try {
                // Try to acquire lock with timeout
                if (lock.tryLock(5, 30, TimeUnit.SECONDS)) {
                    try {
                        // Check cache again in case another thread loaded it
                        cachedValue = cache.get(key);
                        if (cachedValue != null) {
                            log.debug("Cache populated by another thread for '{}':'{}'", cacheName, key);
                            return (T) cachedValue.get();
                        }

                        // Load data and store in cache
                        T result = loader.call();
                        if (result != null) {
                            if (customTtl != null) {
                                putWithTtl(cacheName, key, result, customTtl);
                            } else {
                                cache.put(key, result);
                            }
                            log.debug("Loaded and cached result for '{}':'{}'", cacheName, key);
                        }
                        return result;

                    } finally {
                        lock.unlock();
                    }
                } else {
                    log.warn("Could not acquire lock for cache loading: {}:{}", cacheName, key);
                    // Fallback to direct loading without caching
                    return loader.call();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Interrupted while waiting for cache lock", e);
                return loader.call();
            }

        } catch (Exception e) {
            log.error("Error in cache-aside pattern for {}:{}", cacheName, key, e);
            try {
                return loader.call();
            } catch (Exception loaderException) {
                log.error("Loader also failed for {}:{}", cacheName, key, loaderException);
                throw new RuntimeException("Both cache and loader failed", loaderException);
            }
        }
    }

    /**
     * Write-Through Pattern: Update cache and data store simultaneously
     */
    public <T> void writeThrough(String cacheName, String key, T value, Callable<Void> persister) {
        writeThrough(cacheName, key, value, persister, null);
    }

    /**
     * Write-Through Pattern with custom TTL
     */
    public <T> void writeThrough(String cacheName, String key, T value, Callable<Void> persister, Duration customTtl) {
        String lockKey = LOCK_PREFIX + "write:" + cacheName + ":" + key;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            if (lock.tryLock(5, 30, TimeUnit.SECONDS)) {
                try {
                    // First, persist to data store
                    persister.call();
                    log.debug("Persisted data for write-through: {}:{}", cacheName, key);

                    // Then, update cache
                    if (customTtl != null) {
                        putWithTtl(cacheName, key, value, customTtl);
                    } else {
                        Cache cache = cacheManager.getCache(cacheName);
                        if (cache != null) {
                            cache.put(key, value);
                        }
                    }
                    log.debug("Updated cache for write-through: {}:{}", cacheName, key);

                } finally {
                    lock.unlock();
                }
            } else {
                log.error("Could not acquire lock for write-through: {}:{}", cacheName, key);
                throw new RuntimeException("Could not acquire lock for write-through operation");
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted during write-through operation", e);
            throw new RuntimeException("Write-through operation interrupted", e);
        } catch (Exception e) {
            log.error("Error in write-through pattern for {}:{}", cacheName, key, e);
            throw new RuntimeException("Write-through operation failed", e);
        }
    }

    /**
     * Write-Behind Pattern: Update cache immediately, queue data store update
     */
    public <T> void writeBehind(String cacheName, String key, T value, Supplier<Void> persister) {
        writeBehind(cacheName, key, value, persister, null);
    }

    /**
     * Write-Behind Pattern with custom TTL
     */
    public <T> void writeBehind(String cacheName, String key, T value, Supplier<Void> persister, Duration customTtl) {
        try {
            // Update cache immediately
            if (customTtl != null) {
                putWithTtl(cacheName, key, value, customTtl);
            } else {
                Cache cache = cacheManager.getCache(cacheName);
                if (cache != null) {
                    cache.put(key, value);
                }
            }
            log.debug("Updated cache for write-behind: {}:{}", cacheName, key);

            // Queue persistence operation
            WriteBehindOperation operation = WriteBehindOperation.builder()
                    .cacheName(cacheName)
                    .key(key)
                    .value(value)
                    .timestamp(System.currentTimeMillis())
                    .persister(persister)
                    .build();

            redisTemplate.opsForList().leftPush(WRITE_BEHIND_QUEUE, operation);
            log.debug("Queued write-behind operation: {}:{}", cacheName, key);

        } catch (Exception e) {
            log.error("Error in write-behind pattern for {}:{}", cacheName, key, e);
            throw new RuntimeException("Write-behind operation failed", e);
        }
    }

    /**
     * Intelligent Cache Invalidation
     */
    public void invalidate(String cacheName, String key) {
        try {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.evict(key);
                log.debug("Invalidated cache entry: {}:{}", cacheName, key);
            }

            // Also invalidate related cache entries
            invalidateRelatedEntries(cacheName, key);

        } catch (Exception e) {
            log.error("Error invalidating cache entry {}:{}", cacheName, key, e);
        }
    }

    /**
     * Batch Cache Invalidation
     */
    public void invalidateBatch(String cacheName, Collection<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return;
        }

        try {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache == null) {
                return;
            }

            for (String key : keys) {
                cache.evict(key);
            }
            log.debug("Batch invalidated {} cache entries from '{}'", keys.size(), cacheName);

            // Also invalidate related entries for each key
            keys.forEach(key -> invalidateRelatedEntries(cacheName, key));

        } catch (Exception e) {
            log.error("Error in batch cache invalidation for cache '{}'", cacheName, e);
        }
    }

    /**
     * Pattern-based Cache Invalidation
     */
    public void invalidateByPattern(String cacheName, String keyPattern) {
        try {
            String searchPattern = String.format("*:%s:%s", cacheName, keyPattern);
            Set<String> keysToInvalidate = redisTemplate.keys(searchPattern);

            if (keysToInvalidate != null && !keysToInvalidate.isEmpty()) {
                Cache cache = cacheManager.getCache(cacheName);
                if (cache != null) {
                    for (String fullKey : keysToInvalidate) {
                        // Extract the cache key (remove prefix)
                        String cacheKey = extractCacheKey(fullKey);
                        if (cacheKey != null) {
                            cache.evict(cacheKey);
                        }
                    }
                }
                log.debug("Pattern-based invalidation removed {} entries from '{}' with pattern '{}'",
                         keysToInvalidate.size(), cacheName, keyPattern);
            }

        } catch (Exception e) {
            log.error("Error in pattern-based cache invalidation: {}:{}", cacheName, keyPattern, e);
        }
    }

    /**
     * Cache Warming - Preload frequently accessed data
     */
    public <T> void warmUp(String cacheName, Map<String, Callable<T>> loaders) {
        warmUp(cacheName, loaders, null);
    }

    /**
     * Cache Warming with custom TTL
     */
    public <T> void warmUp(String cacheName, Map<String, Callable<T>> loaders, Duration customTtl) {
        if (loaders == null || loaders.isEmpty()) {
            return;
        }

        log.info("Starting cache warmup for '{}' with {} entries", cacheName, loaders.size());

        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            log.error("Cache '{}' not found for warmup", cacheName);
            return;
        }

        int successCount = 0;
        int errorCount = 0;

        for (Map.Entry<String, Callable<T>> entry : loaders.entrySet()) {
            try {
                String key = entry.getKey();
                Callable<T> loader = entry.getValue();

                // Skip if already in cache
                if (cache.get(key) != null) {
                    log.debug("Cache warmup: key '{}' already exists in cache '{}'", key, cacheName);
                    continue;
                }

                // Load and cache the data
                T value = loader.call();
                if (value != null) {
                    if (customTtl != null) {
                        putWithTtl(cacheName, key, value, customTtl);
                    } else {
                        cache.put(key, value);
                    }
                    successCount++;
                    log.debug("Cache warmup: loaded '{}':'{}'", cacheName, key);
                } else {
                    log.debug("Cache warmup: null value returned for '{}':'{}'", cacheName, key);
                }

            } catch (Exception e) {
                errorCount++;
                log.error("Cache warmup error for '{}':'{}'", cacheName, entry.getKey(), e);
            }
        }

        log.info("Cache warmup completed for '{}': {} successful, {} errors", 
                cacheName, successCount, errorCount);
    }

    /**
     * Multi-level Cache Get
     */
    public <T> Optional<T> multiLevelGet(List<String> cacheNames, String key) {
        for (String cacheName : cacheNames) {
            try {
                Cache cache = cacheManager.getCache(cacheName);
                if (cache != null) {
                    Cache.ValueWrapper cachedValue = cache.get(key);
                    if (cachedValue != null) {
                        @SuppressWarnings("unchecked")
                        T result = (T) cachedValue.get();
                        log.debug("Multi-level cache hit in '{}' for key '{}'", cacheName, key);
                        
                        // Promote to higher-level caches
                        promoteToHigherCaches(cacheNames, cacheName, key, result);
                        
                        return Optional.of(result);
                    }
                }
            } catch (Exception e) {
                log.error("Error accessing cache '{}' for key '{}'", cacheName, key, e);
            }
        }
        
        log.debug("Multi-level cache miss for key '{}'", key);
        return Optional.empty();
    }

    /**
     * Distributed Lock with Cache Operation
     */
    public <T> T withLock(String lockKey, Callable<T> operation) {
        return withLock(lockKey, operation, Duration.ofSeconds(30));
    }

    /**
     * Distributed Lock with custom timeout
     */
    public <T> T withLock(String lockKey, Callable<T> operation, Duration lockTimeout) {
        RLock lock = redissonClient.getLock(LOCK_PREFIX + lockKey);
        
        try {
            if (lock.tryLock(5, lockTimeout.toSeconds(), TimeUnit.SECONDS)) {
                try {
                    return operation.call();
                } finally {
                    lock.unlock();
                }
            } else {
                throw new RuntimeException("Could not acquire distributed lock: " + lockKey);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for lock: " + lockKey, e);
        } catch (Exception e) {
            throw new RuntimeException("Error executing operation with lock: " + lockKey, e);
        }
    }

    /**
     * Get Cache Statistics
     */
    public CacheStatistics getCacheStatistics(String cacheName) {
        try {
            String statsKey = CACHE_STATS_PREFIX + cacheName;
            Map<Object, Object> stats = redisTemplate.opsForHash().entries(statsKey);
            
            long hits = getLongValue(stats, "hits", 0L);
            long misses = getLongValue(stats, "misses", 0L);
            long evictions = getLongValue(stats, "evictions", 0L);
            
            double hitRate = (hits + misses) > 0 ? (double) hits / (hits + misses) : 0.0;
            
            return CacheStatistics.builder()
                    .cacheName(cacheName)
                    .hits(hits)
                    .misses(misses)
                    .evictions(evictions)
                    .hitRate(hitRate)
                    .build();
                    
        } catch (Exception e) {
            log.error("Error getting cache statistics for '{}'", cacheName, e);
            return CacheStatistics.builder()
                    .cacheName(cacheName)
                    .hits(0L)
                    .misses(0L)
                    .evictions(0L)
                    .hitRate(0.0)
                    .build();
        }
    }

    // Private helper methods

    private void putWithTtl(String cacheName, String key, Object value, Duration ttl) {
        try {
            String fullKey = String.format("%s:%s:%s", 
                    cacheProperties.getApplicationName(), cacheName, key);
            redisTemplate.opsForValue().set(fullKey, value, ttl);
        } catch (Exception e) {
            log.error("Error putting value with TTL: {}:{}", cacheName, key, e);
        }
    }

    private void invalidateRelatedEntries(String cacheName, String key) {
        if (!cacheProperties.getInvalidation().isAutoInvalidate()) {
            return;
        }

        try {
            // Define related cache invalidation rules
            switch (cacheName.toLowerCase()) {
                case "users":
                    // Invalidate user-related caches
                    invalidate("user-sessions", key);
                    invalidate("user-preferences", key);
                    break;
                
                case "places":
                    // Invalidate place-related caches
                    invalidateByPattern("place-nearby", key + "*");
                    invalidateByPattern("place-category", "*");
                    break;
                
                case "travel-plans":
                    // Invalidate travel plan related caches
                    invalidateByPattern("travel-plan-user", "*" + key + "*");
                    invalidate("trending-plans", "trending");
                    break;
                
                default:
                    log.debug("No related cache invalidation rules for cache: {}", cacheName);
            }
        } catch (Exception e) {
            log.error("Error invalidating related cache entries for {}:{}", cacheName, key, e);
        }
    }

    private String extractCacheKey(String fullKey) {
        // Extract cache key from full Redis key
        // Format: "app:profile:cacheName:actualKey"
        String[] parts = fullKey.split(":", 4);
        return parts.length >= 4 ? parts[3] : null;
    }

    private void promoteToHigherCaches(List<String> cacheNames, String foundInCache, String key, Object value) {
        try {
            int foundIndex = cacheNames.indexOf(foundInCache);
            if (foundIndex > 0) {
                // Promote to all higher-level caches
                for (int i = 0; i < foundIndex; i++) {
                    Cache higherCache = cacheManager.getCache(cacheNames.get(i));
                    if (higherCache != null) {
                        higherCache.put(key, value);
                        log.debug("Promoted cache entry to '{}' for key '{}'", cacheNames.get(i), key);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error promoting cache entry to higher levels", e);
        }
    }

    private void recordCacheHit(String cacheName) {
        try {
            String statsKey = CACHE_STATS_PREFIX + cacheName;
            redisTemplate.opsForHash().increment(statsKey, "hits", 1);
        } catch (Exception e) {
            log.debug("Error recording cache hit", e);
        }
    }

    private void recordCacheMiss(String cacheName) {
        try {
            String statsKey = CACHE_STATS_PREFIX + cacheName;
            redisTemplate.opsForHash().increment(statsKey, "misses", 1);
        } catch (Exception e) {
            log.debug("Error recording cache miss", e);
        }
    }

    private long getLongValue(Map<Object, Object> map, String key, long defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    // Inner classes for data structures

    @lombok.Data
    @lombok.Builder
    public static class CacheStatistics {
        private String cacheName;
        private long hits;
        private long misses;
        private long evictions;
        private double hitRate;
    }

    @lombok.Data
    @lombok.Builder
    public static class WriteBehindOperation {
        private String cacheName;
        private String key;
        private Object value;
        private long timestamp;
        private Supplier<Void> persister;
    }
}