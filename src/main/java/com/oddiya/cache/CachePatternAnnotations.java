package com.oddiya.cache;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.*;

/**
 * Custom Cache Pattern Annotations
 * Agent 5 - Cache Patterns Developer
 * 
 * Specialized caching annotations for common patterns in the Oddiya platform.
 * These annotations combine multiple Spring cache annotations and add custom behavior.
 */
public class CachePatternAnnotations {

    /**
     * Place-related caching annotation
     */
    @Target({ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @Cacheable(
        cacheNames = "places",
        key = "#p0",
        condition = "#p0 != null && #p0 > 0",
        unless = "#result == null"
    )
    public @interface CachePlace {
        
        @AliasFor(annotation = Cacheable.class, attribute = "key")
        String key() default "#p0";
        
        @AliasFor(annotation = Cacheable.class, attribute = "condition")
        String condition() default "#p0 != null && #p0 > 0";
    }

    /**
     * Travel plan caching annotation
     */
    @Target({ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @Cacheable(
        cacheNames = "travel-plans",
        key = "#p0",
        condition = "#p0 != null && #p0 > 0",
        unless = "#result == null"
    )
    public @interface CacheTravelPlan {
        
        @AliasFor(annotation = Cacheable.class, attribute = "key")
        String key() default "#p0";
        
        @AliasFor(annotation = Cacheable.class, attribute = "condition")
        String condition() default "#p0 != null && #p0 > 0";
    }

    /**
     * User profile caching annotation
     */
    @Target({ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @Cacheable(
        cacheNames = "users",
        key = "#userId",
        condition = "#userId != null && #userId > 0",
        unless = "#result == null"
    )
    public @interface CacheUserProfile {
        
        @AliasFor(annotation = Cacheable.class, attribute = "key")
        String key() default "#userId";
        
        @AliasFor(annotation = Cacheable.class, attribute = "condition")
        String condition() default "#userId != null && #userId > 0";
    }

    /**
     * Search results caching annotation with shorter TTL
     */
    @Target({ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @Cacheable(
        cacheNames = "search-results",
        key = "'search:' + #query + ':' + (#page != null ? #page : 0)",
        condition = "#query != null && #query.length() > 0",
        unless = "#result == null || #result.isEmpty()"
    )
    public @interface CacheSearchResults {
        
        @AliasFor(annotation = Cacheable.class, attribute = "key")
        String key() default "'search:' + #query + ':' + (#page != null ? #page : 0)";
        
        @AliasFor(annotation = Cacheable.class, attribute = "condition")
        String condition() default "#query != null && #query.length() > 0";
    }

    /**
     * API response caching annotation for external API calls
     */
    @Target({ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @Cacheable(
        cacheNames = "api-responses",
        key = "'api:' + #apiEndpoint + ':' + #parameters.hashCode()",
        condition = "#apiEndpoint != null",
        unless = "#result == null"
    )
    public @interface CacheApiResponse {
        
        @AliasFor(annotation = Cacheable.class, attribute = "key")
        String key() default "'api:' + #apiEndpoint + ':' + #parameters.hashCode()";
        
        @AliasFor(annotation = Cacheable.class, attribute = "condition")
        String condition() default "#apiEndpoint != null";
    }

    /**
     * Statistics caching annotation for aggregated data
     */
    @Target({ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @Cacheable(
        cacheNames = "statistics",
        key = "'stats:' + #statType + ':' + (#timeRange != null ? #timeRange : 'all')",
        condition = "#statType != null"
    )
    public @interface CacheStatistics {
        
        @AliasFor(annotation = Cacheable.class, attribute = "key")
        String key() default "'stats:' + #statType + ':' + (#timeRange != null ? #timeRange : 'all')";
        
        @AliasFor(annotation = Cacheable.class, attribute = "condition")
        String condition() default "#statType != null";
    }

    /**
     * Write-through pattern: Update cache and trigger related cache invalidation
     */
    @Target({ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @Caching(
        put = @CachePut(cacheNames = "places", key = "#result.id", condition = "#result != null"),
        evict = {
            @CacheEvict(cacheNames = "search-results", allEntries = true),
            @CacheEvict(cacheNames = "statistics", allEntries = true)
        }
    )
    public @interface UpdatePlace {
        
        @AliasFor(annotation = CachePut.class, attribute = "key")
        String key() default "#result.id";
    }

    /**
     * Write-through pattern for travel plans
     */
    @Target({ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @Caching(
        put = @CachePut(cacheNames = "travel-plans", key = "#result.id", condition = "#result != null"),
        evict = {
            @CacheEvict(cacheNames = "search-results", allEntries = true),
            @CacheEvict(cacheNames = "statistics", allEntries = true),
            @CacheEvict(cacheNames = "recommendations", key = "'user:' + #result.userId")
        }
    )
    public @interface UpdateTravelPlan {
        
        @AliasFor(annotation = CachePut.class, attribute = "key")
        String key() default "#result.id";
    }

    /**
     * Write-through pattern for user profiles
     */
    @Target({ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @Caching(
        put = @CachePut(cacheNames = "users", key = "#result.id", condition = "#result != null"),
        evict = {
            @CacheEvict(cacheNames = "recommendations", key = "'user:' + #result.id"),
            @CacheEvict(cacheNames = "statistics", allEntries = true)
        }
    )
    public @interface UpdateUserProfile {
        
        @AliasFor(annotation = CachePut.class, attribute = "key")
        String key() default "#result.id";
    }

    /**
     * Comprehensive cache eviction for place deletion
     */
    @Target({ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @Caching(evict = {
        @CacheEvict(cacheNames = "places", key = "#placeId"),
        @CacheEvict(cacheNames = "search-results", allEntries = true),
        @CacheEvict(cacheNames = "statistics", allEntries = true),
        @CacheEvict(cacheNames = "recommendations", allEntries = true),
        @CacheEvict(cacheNames = "reviews", allEntries = true)
    })
    public @interface DeletePlace {
        
        @AliasFor(annotation = CacheEvict.class, attribute = "key")
        String placeKey() default "#placeId";
    }

    /**
     * Comprehensive cache eviction for travel plan deletion
     */
    @Target({ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @Caching(evict = {
        @CacheEvict(cacheNames = "travel-plans", key = "#planId"),
        @CacheEvict(cacheNames = "search-results", allEntries = true),
        @CacheEvict(cacheNames = "statistics", allEntries = true),
        @CacheEvict(cacheNames = "recommendations", allEntries = true)
    })
    public @interface DeleteTravelPlan {
        
        @AliasFor(annotation = CacheEvict.class, attribute = "key")
        String planKey() default "#planId";
    }

    /**
     * Nearby places caching with location-based key
     */
    @Target({ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @Cacheable(
        cacheNames = "places",
        key = "'nearby:' + #latitude + ':' + #longitude + ':' + #radius + ':' + (#page != null ? #page : 0)",
        condition = "#latitude != null && #longitude != null && #radius != null && #radius > 0",
        unless = "#result == null || #result.isEmpty()"
    )
    public @interface CacheNearbyPlaces {
        
        @AliasFor(annotation = Cacheable.class, attribute = "key")
        String key() default "'nearby:' + #latitude + ':' + #longitude + ':' + #radius + ':' + (#page != null ? #page : 0)";
    }

    /**
     * Category-based place caching
     */
    @Target({ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @Cacheable(
        cacheNames = "places",
        key = "'category:' + #category + ':' + (#page != null ? #page : 0)",
        condition = "#category != null && #category.length() > 0",
        unless = "#result == null || #result.isEmpty()"
    )
    public @interface CachePlacesByCategory {
        
        @AliasFor(annotation = Cacheable.class, attribute = "key")
        String key() default "'category:' + #category + ':' + (#page != null ? #page : 0)";
    }

    /**
     * User-specific recommendations caching
     */
    @Target({ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @Cacheable(
        cacheNames = "recommendations",
        key = "'user:' + #userId + ':' + #type",
        condition = "#userId != null && #userId > 0 && #type != null",
        unless = "#result == null || #result.isEmpty()"
    )
    public @interface CacheRecommendations {
        
        @AliasFor(annotation = Cacheable.class, attribute = "key")
        String key() default "'user:' + #userId + ':' + #type";
        
        @AliasFor(annotation = Cacheable.class, attribute = "condition")
        String condition() default "#userId != null && #userId > 0 && #type != null";
    }

    /**
     * Reviews caching with multiple key strategies
     */
    @Target({ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @Cacheable(
        cacheNames = "reviews",
        condition = "#p0 != null && #p0 > 0",
        unless = "#result == null || #result.isEmpty()"
    )
    public @interface CacheReviews {
        
        @AliasFor(annotation = Cacheable.class, attribute = "key")
        String key() default "#p0";
        
        @AliasFor(annotation = Cacheable.class, attribute = "condition")
        String condition() default "#p0 != null && #p0 > 0";
    }

    /**
     * Leaderboard caching with time-based key
     */
    @Target({ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @Cacheable(
        cacheNames = "leaderboards",
        key = "#type + ':' + #timeRange + ':' + (#limit != null ? #limit : 10)",
        condition = "#type != null && #timeRange != null"
    )
    public @interface CacheLeaderboard {
        
        @AliasFor(annotation = Cacheable.class, attribute = "key")
        String key() default "#type + ':' + #timeRange + ':' + (#limit != null ? #limit : 10)";
        
        @AliasFor(annotation = Cacheable.class, attribute = "condition")
        String condition() default "#type != null && #timeRange != null";
    }

    /**
     * Conditional caching based on user authentication
     */
    @Target({ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @Cacheable(
        condition = "@securityService.isAuthenticated() && @securityService.hasRole('USER')",
        unless = "#result == null"
    )
    public @interface CacheForAuthenticatedUsers {
        
        @AliasFor(annotation = Cacheable.class, attribute = "cacheNames")
        String[] cacheNames();
        
        @AliasFor(annotation = Cacheable.class, attribute = "key")
        String key() default "";
        
        @AliasFor(annotation = Cacheable.class, attribute = "condition")
        String condition() default "@securityService.isAuthenticated() && @securityService.hasRole('USER')";
    }

    /**
     * Cache warming annotation for startup data loading
     */
    @Target({ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    public @interface WarmUpCache {
        
        String[] cacheNames();
        
        int priority() default 0; // Higher number = higher priority
        
        boolean async() default true;
        
        String condition() default "";
    }

    /**
     * Cache metrics recording annotation
     */
    @Target({ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    public @interface RecordCacheMetrics {
        
        String operation() default "";
        
        String[] tags() default {};
        
        boolean recordTiming() default true;
        
        boolean recordHitRate() default true;
    }
}