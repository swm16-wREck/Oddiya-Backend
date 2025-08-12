package com.oddiya.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Rate Limiting Configuration using Bucket4j and Redis
 * 
 * Implements distributed rate limiting to prevent:
 * - DDoS attacks
 * - API abuse
 * - Resource exhaustion
 * 
 * OWASP Top 10 2021 - A04: Insecure Design & A05: Security Misconfiguration
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.security.rate-limiting.enabled", havingValue = "true", matchIfMissing = true)
public class RateLimitingConfig {
    
    @Value("${app.rate-limit.auth:10}")
    private long authRateLimit;
    
    @Value("${app.rate-limit.ai-generation:5}")
    private long aiGenerationRateLimit;
    
    @Value("${app.rate-limit.search:30}")
    private long searchRateLimit;
    
    @Value("${app.rate-limit.media-upload:10}")
    private long mediaUploadRateLimit;
    
    @Value("${app.rate-limit.general:100}")
    private long generalRateLimit;
    
    @Value("${app.rate-limit.window:60}")
    private long windowInSeconds;
    
    @Value("${app.security.rate-limiting.strict-mode:false}")
    private boolean strictMode;
    
    @Value("${app.security.rate-limiting.burst-capacity:true}")
    private boolean enableBurstCapacity;
    
    private final RedisConnectionFactory redisConnectionFactory;
    
    /**
     * Redis-based distributed rate limiting proxy manager
     * Allows rate limits to work across multiple application instances
     */
    @Bean
    @ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis")
    public ProxyManager<String> proxyManager() {
        LettuceConnectionFactory lettuceFactory = (LettuceConnectionFactory) redisConnectionFactory;
        RedisClient redisClient = RedisClient.create(lettuceFactory.getClientConfiguration().getRedisURI());
        StatefulRedisConnection<String, byte[]> redisConnection = redisClient.connect(
            io.lettuce.core.codec.RedisCodec.of(
                io.lettuce.core.codec.StringCodec.UTF8,
                io.lettuce.core.codec.ByteArrayCodec.INSTANCE
            )
        );
        
        return LettuceBasedProxyManager.builderFor(redisConnection)
                .withExpirationAfterWriteStrategy(Duration.ofMinutes(5))
                .build();
    }
    
    /**
     * Authentication rate limiting configuration
     * Prevents brute force attacks on login endpoints
     */
    @Bean("authRateLimitSupplier")
    public Supplier<BucketConfiguration> authRateLimitSupplier() {
        return () -> {
            Bandwidth primaryLimit = Bandwidth.builder()
                    .capacity(authRateLimit)
                    .refillGreedy(authRateLimit, Duration.ofSeconds(windowInSeconds))
                    .build();
            
            BucketConfiguration.Builder builder = BucketConfiguration.builder()
                    .addLimit(primaryLimit);
            
            // Add stricter burst protection for auth endpoints
            if (strictMode) {
                Bandwidth burstLimit = Bandwidth.builder()
                        .capacity(3)
                        .refillGreedy(3, Duration.ofMinutes(1))
                        .build();
                builder.addLimit(burstLimit);
            }
            
            return builder.build();
        };
    }
    
    /**
     * AI Generation rate limiting configuration
     * Prevents abuse of expensive AI operations
     */
    @Bean("aiGenerationRateLimitSupplier")
    public Supplier<BucketConfiguration> aiGenerationRateLimitSupplier() {
        return () -> {
            Bandwidth primaryLimit = Bandwidth.builder()
                    .capacity(aiGenerationRateLimit)
                    .refillGreedy(aiGenerationRateLimit, Duration.ofSeconds(windowInSeconds))
                    .build();
            
            BucketConfiguration.Builder builder = BucketConfiguration.builder()
                    .addLimit(primaryLimit);
            
            // Stricter limits for AI operations due to cost
            if (strictMode) {
                Bandwidth hourlyLimit = Bandwidth.builder()
                        .capacity(50)
                        .refillGreedy(50, Duration.ofHours(1))
                        .build();
                builder.addLimit(hourlyLimit);
            }
            
            return builder.build();
        };
    }
    
    /**
     * Search operations rate limiting
     * Prevents abuse of search APIs and database queries
     */
    @Bean("searchRateLimitSupplier")
    public Supplier<BucketConfiguration> searchRateLimitSupplier() {
        return () -> {
            Bandwidth primaryLimit = Bandwidth.builder()
                    .capacity(searchRateLimit)
                    .refillGreedy(searchRateLimit, Duration.ofSeconds(windowInSeconds))
                    .build();
            
            BucketConfiguration.Builder builder = BucketConfiguration.builder()
                    .addLimit(primaryLimit);
            
            // Add burst capacity for legitimate users
            if (enableBurstCapacity && !strictMode) {
                Bandwidth burstLimit = Bandwidth.builder()
                        .capacity(searchRateLimit * 2)
                        .refillGreedy(searchRateLimit, Duration.ofMinutes(5))
                        .build();
                builder.addLimit(burstLimit);
            }
            
            return builder.build();
        };
    }
    
    /**
     * Media upload rate limiting
     * Prevents storage abuse and bandwidth exhaustion
     */
    @Bean("mediaUploadRateLimitSupplier")
    public Supplier<BucketConfiguration> mediaUploadRateLimitSupplier() {
        return () -> {
            Bandwidth primaryLimit = Bandwidth.builder()
                    .capacity(mediaUploadRateLimit)
                    .refillGreedy(mediaUploadRateLimit, Duration.ofSeconds(windowInSeconds))
                    .build();
            
            BucketConfiguration.Builder builder = BucketConfiguration.builder()
                    .addLimit(primaryLimit);
            
            // Daily upload limits
            Bandwidth dailyLimit = Bandwidth.builder()
                    .capacity(100)
                    .refillGreedy(100, Duration.ofDays(1))
                    .build();
            builder.addLimit(dailyLimit);
            
            return builder.build();
        };
    }
    
    /**
     * General API rate limiting
     * Catch-all rate limiting for other endpoints
     */
    @Bean("generalRateLimitSupplier")
    public Supplier<BucketConfiguration> generalRateLimitSupplier() {
        return () -> {
            Bandwidth primaryLimit = Bandwidth.builder()
                    .capacity(generalRateLimit)
                    .refillGreedy(generalRateLimit, Duration.ofSeconds(windowInSeconds))
                    .build();
            
            BucketConfiguration.Builder builder = BucketConfiguration.builder()
                    .addLimit(primaryLimit);
            
            // Add hourly limits for general usage
            if (strictMode) {
                Bandwidth hourlyLimit = Bandwidth.builder()
                        .capacity(1000)
                        .refillGreedy(1000, Duration.ofHours(1))
                        .build();
                builder.addLimit(hourlyLimit);
            }
            
            return builder.build();
        };
    }
    
    /**
     * IP-based rate limiting for unauthenticated requests
     * Prevents anonymous abuse
     */
    @Bean("ipRateLimitSupplier")
    public Supplier<BucketConfiguration> ipRateLimitSupplier() {
        return () -> {
            Bandwidth primaryLimit = Bandwidth.builder()
                    .capacity(20)
                    .refillGreedy(20, Duration.ofSeconds(windowInSeconds))
                    .build();
            
            BucketConfiguration.Builder builder = BucketConfiguration.builder()
                    .addLimit(primaryLimit);
            
            // Stricter limits for IP-based rate limiting
            if (strictMode) {
                Bandwidth minuteLimit = Bandwidth.builder()
                        .capacity(10)
                        .refillGreedy(10, Duration.ofMinutes(1))
                        .build();
                builder.addLimit(minuteLimit);
            }
            
            return builder.build();
        };
    }
    
    /**
     * Create a bucket for a specific key using the provided configuration
     */
    public Bucket createBucket(String key, Supplier<BucketConfiguration> configSupplier) {
        ProxyManager<String> proxyManager = proxyManager();
        if (proxyManager != null) {
            return proxyManager.builder()
                    .build(key, configSupplier);
        } else {
            // Fallback to local bucket if Redis is not available
            return Bucket.builder()
                    .addLimit(Bandwidth.builder()
                            .capacity(generalRateLimit)
                            .refillGreedy(generalRateLimit, Duration.ofSeconds(windowInSeconds))
                            .build())
                    .build();
        }
    }
    
    /**
     * Rate limiting keys generation utility
     */
    public static class RateLimitKey {
        public static String forUser(String userId, String operation) {
            return "rate_limit:user:" + userId + ":" + operation;
        }
        
        public static String forIp(String ipAddress, String operation) {
            return "rate_limit:ip:" + ipAddress + ":" + operation;
        }
        
        public static String forGlobal(String operation) {
            return "rate_limit:global:" + operation;
        }
    }
}