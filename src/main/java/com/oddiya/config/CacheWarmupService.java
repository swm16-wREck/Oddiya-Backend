package com.oddiya.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cache.CacheManager;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Cache Warmup Service
 * Agent 3 - Application Caching Engineer
 * 
 * Service to pre-populate cache with frequently accessed data on application startup.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnBean(RedisTemplate.class)
public class CacheWarmupService {

    private final CacheManager cacheManager;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisCacheProperties cacheProperties;

    /**
     * Warm up caches on application startup
     */
    @EventListener(ApplicationReadyEvent.class)
    @Async
    public void warmUpCaches() {
        if (!cacheProperties.getWarmUp().isEnabled() || !cacheProperties.getWarmUp().isOnStartup()) {
            log.info("Cache warmup is disabled");
            return;
        }

        log.info("Starting cache warmup process...");
        long startTime = System.currentTimeMillis();

        CompletableFuture<Void> popularPlaces = CompletableFuture.runAsync(() -> {
            if (cacheProperties.getWarmUp().isPopularPlaces()) {
                warmUpPopularPlaces();
            }
        });

        CompletableFuture<Void> trendingPlans = CompletableFuture.runAsync(() -> {
            if (cacheProperties.getWarmUp().isTrendingPlans()) {
                warmUpTrendingPlans();
            }
        });

        CompletableFuture<Void> statistics = CompletableFuture.runAsync(() -> {
            if (cacheProperties.getWarmUp().isStatistics()) {
                warmUpStatistics();
            }
        });

        // Wait for all warmup operations to complete
        CompletableFuture.allOf(popularPlaces, trendingPlans, statistics)
                .thenRun(() -> {
                    long duration = System.currentTimeMillis() - startTime;
                    log.info("Cache warmup completed in {} ms", duration);
                })
                .exceptionally(throwable -> {
                    log.error("Cache warmup failed", throwable);
                    return null;
                });
    }

    /**
     * Warm up popular places cache
     */
    private void warmUpPopularPlaces() {
        try {
            log.debug("Warming up popular places cache...");
            
            // Simulate popular places data
            String[] categories = {"restaurant", "attraction", "hotel", "shopping", "entertainment"};
            
            for (String category : categories) {
                String cacheKey = String.format("places:category:%s:page:0", category);
                
                // Check if already cached
                if (redisTemplate.hasKey(cacheKey)) {
                    log.debug("Popular places for category '{}' already cached", category);
                    continue;
                }
                
                // In a real implementation, this would call the actual service method
                // placeService.getPlacesByCategory(category, PageRequest.of(0, 20));
                
                // For now, we'll just set a placeholder
                redisTemplate.opsForValue().set(
                    cacheKey, 
                    "popular-places-" + category, 
                    cacheProperties.getTtl().getOrDefault("places", 3600000L), 
                    TimeUnit.MILLISECONDS
                );
                
                log.debug("Warmed up popular places for category: {}", category);
            }
            
            log.info("Popular places cache warmup completed");
            
        } catch (Exception e) {
            log.error("Failed to warm up popular places cache", e);
        }
    }

    /**
     * Warm up trending travel plans cache
     */
    private void warmUpTrendingPlans() {
        try {
            log.debug("Warming up trending travel plans cache...");
            
            String cacheKey = "travel-plans:public:page:0";
            
            // Check if already cached
            if (redisTemplate.hasKey(cacheKey)) {
                log.debug("Trending travel plans already cached");
                return;
            }
            
            // In a real implementation, this would call the actual service method
            // travelPlanService.getPublicTravelPlans(PageRequest.of(0, 20));
            
            // For now, we'll just set a placeholder
            redisTemplate.opsForValue().set(
                cacheKey,
                "trending-travel-plans",
                cacheProperties.getTtl().getOrDefault("travel-plans", 1800000L),
                TimeUnit.MILLISECONDS
            );
            
            log.info("Trending travel plans cache warmup completed");
            
        } catch (Exception e) {
            log.error("Failed to warm up trending travel plans cache", e);
        }
    }

    /**
     * Warm up statistics cache
     */
    private void warmUpStatistics() {
        try {
            log.debug("Warming up statistics cache...");
            
            String[] statKeys = {
                "stats:popular-places",
                "stats:trending-plans",
                "leaderboards:top-contributors",
                "leaderboards:most-visited-places"
            };
            
            for (String key : statKeys) {
                // Check if already cached
                if (redisTemplate.hasKey(key)) {
                    log.debug("Statistics '{}' already cached", key);
                    continue;
                }
                
                // In a real implementation, this would call the actual service method
                // statisticsService.getStatistics(key);
                
                // For now, we'll just set a placeholder
                redisTemplate.opsForValue().set(
                    key,
                    "statistics-" + key,
                    cacheProperties.getTtl().getOrDefault("statistics", 300000L),
                    TimeUnit.MILLISECONDS
                );
                
                log.debug("Warmed up statistics: {}", key);
            }
            
            log.info("Statistics cache warmup completed");
            
        } catch (Exception e) {
            log.error("Failed to warm up statistics cache", e);
        }
    }

    /**
     * Manual cache warmup trigger (for admin endpoints)
     */
    public void triggerWarmup() {
        log.info("Manual cache warmup triggered");
        warmUpCaches();
    }

    /**
     * Warm up specific cache
     */
    public void warmUpCache(String cacheName) {
        log.info("Warming up specific cache: {}", cacheName);
        
        switch (cacheName.toLowerCase()) {
            case "places":
            case "popular-places":
                warmUpPopularPlaces();
                break;
            case "travel-plans":
            case "trending-plans":
                warmUpTrendingPlans();
                break;
            case "statistics":
                warmUpStatistics();
                break;
            default:
                log.warn("Unknown cache name for warmup: {}", cacheName);
        }
    }

    /**
     * Check cache warmup status
     */
    public boolean isCacheWarmedUp(String cacheName) {
        try {
            String pattern = switch (cacheName.toLowerCase()) {
                case "places" -> "places:category:*";
                case "travel-plans" -> "travel-plans:public:*";
                case "statistics" -> "stats:*";
                default -> cacheName + "*";
            };
            
            return !redisTemplate.keys(pattern).isEmpty();
            
        } catch (Exception e) {
            log.error("Error checking cache warmup status for {}", cacheName, e);
            return false;
        }
    }
}