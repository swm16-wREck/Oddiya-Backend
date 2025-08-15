package com.oddiya.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurerSupport;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.CacheResolver;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.*;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Redis Cache Configuration
 * Agent 3 - Application Caching Engineer
 * 
 * Comprehensive Redis caching configuration with multiple cache managers,
 * custom serializers, error handling, and monitoring integration.
 */
@Slf4j
@Configuration
@EnableCaching
@EnableConfigurationProperties(RedisCacheProperties.class)
@ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis")
@Profile({"redis", "redis-dev", "redis-prod", "aws"})
public class RedisCacheConfig extends CachingConfigurerSupport {

    private final RedisCacheProperties cacheProperties;

    @Value("${spring.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.redis.port:6379}")
    private int redisPort;

    @Value("${spring.redis.password:}")
    private String redisPassword;

    @Value("${spring.redis.database:0}")
    private int redisDatabase;

    @Value("${spring.redis.timeout:2000ms}")
    private Duration redisTimeout;

    @Value("${spring.application.name:oddiya}")
    private String applicationName;

    @Value("${spring.profiles.active:default}")
    private String activeProfile;

    public RedisCacheConfig(RedisCacheProperties cacheProperties) {
        this.cacheProperties = cacheProperties;
    }

    /**
     * Primary Redis Connection Factory with optimized settings
     */
    @Bean
    @Primary
    public LettuceConnectionFactory redisConnectionFactory() {
        log.info("Configuring Redis connection to {}:{}", redisHost, redisPort);

        // Redis Configuration
        RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration();
        redisConfig.setHostName(redisHost);
        redisConfig.setPort(redisPort);
        redisConfig.setDatabase(redisDatabase);
        
        if (redisPassword != null && !redisPassword.trim().isEmpty()) {
            redisConfig.setPassword(redisPassword);
        }

        // Client Configuration with optimizations
        SocketOptions socketOptions = SocketOptions.builder()
                .connectTimeout(Duration.ofMillis(10000))
                .keepAlive(true)
                .build();

        ClientOptions clientOptions = ClientOptions.builder()
                .socketOptions(socketOptions)
                .autoReconnect(true)
                .build();

        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .clientOptions(clientOptions)
                .commandTimeout(redisTimeout)
                .build();

        LettuceConnectionFactory factory = new LettuceConnectionFactory(redisConfig, clientConfig);
        factory.setShareNativeConnection(true);
        factory.setValidateConnection(true);

        return factory;
    }

    /**
     * Optimized Redis Template with JSON serialization
     */
    @Bean
    @Primary
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // JSON Serializer configuration
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(objectMapper());

        // Key serialization
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());

        // Value serialization
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.setDefaultSerializer(jsonSerializer);
        template.setEnableDefaultSerializer(true);
        template.afterPropertiesSet();

        return template;
    }

    /**
     * String-specific Redis Template for simple operations
     */
    @Bean
    public RedisTemplate<String, String> stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setDefaultSerializer(new StringRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }

    /**
     * Primary Cache Manager with multiple cache configurations
     */
    @Bean
    @Primary
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        log.info("Configuring Redis Cache Manager with {} cache configurations", 
                 cacheProperties.getTtl().size());

        RedisCacheManager.Builder builder = RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultCacheConfiguration())
                .transactionAware();

        // Configure individual cache settings
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        
        cacheProperties.getTtl().forEach((cacheName, ttlMillis) -> {
            Duration ttl = Duration.ofMillis(ttlMillis);
            RedisCacheConfiguration config = defaultCacheConfiguration()
                    .entryTtl(ttl);
            
            cacheConfigurations.put(cacheName, config);
            log.debug("Configured cache '{}' with TTL: {}", cacheName, ttl);
        });

        builder.withInitialCacheConfigurations(cacheConfigurations);

        return builder.build();
    }

    /**
     * Session-specific Cache Manager for Spring Session
     */
    @Bean("sessionCacheManager")
    public CacheManager sessionCacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMillis(cacheProperties.getTtl().getOrDefault("sessions", 1800000L)))
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer(objectMapper())))
                .computePrefixWith(cacheName -> applicationName + ":sessions:")
                .disableCachingNullValues();

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .transactionAware()
                .build();
    }

    /**
     * API Response Cache Manager with shorter TTL
     */
    @Bean("apiCacheManager")
    public CacheManager apiCacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMillis(cacheProperties.getTtl().getOrDefault("api-responses", 300000L)))
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer(objectMapper())))
                .computePrefixWith(cacheName -> applicationName + ":api:")
                .disableCachingNullValues();

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .build();
    }

    /**
     * Default Redis Cache Configuration
     */
    private RedisCacheConfiguration defaultCacheConfiguration() {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(1)) // Default 1 hour TTL
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer(objectMapper())))
                .computePrefixWith(cacheName -> applicationName + ":" + activeProfile + ":")
                .disableCachingNullValues();
    }

    /**
     * Custom ObjectMapper for Redis serialization
     */
    @Bean("redisObjectMapper")
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );
        mapper.findAndRegisterModules();
        return mapper;
    }

    /**
     * Custom Key Generator for cache keys
     */
    @Override
    @Bean
    public KeyGenerator keyGenerator() {
        return new CustomKeyGenerator();
    }

    /**
     * Cache Error Handler to prevent cache failures from breaking the application
     */
    @Override
    @Bean
    public CacheErrorHandler errorHandler() {
        return new RedisCacheErrorHandler();
    }

    /**
     * Cache Resolver for dynamic cache selection
     */
    @Override
    @Bean
    public CacheResolver cacheResolver() {
        return new CustomCacheResolver(cacheManager(redisConnectionFactory()));
    }

    /**
     * Custom Key Generator Implementation
     */
    public static class CustomKeyGenerator implements KeyGenerator {
        @Override
        public Object generate(Object target, java.lang.reflect.Method method, Object... params) {
            StringBuilder key = new StringBuilder();
            key.append(target.getClass().getSimpleName()).append(":");
            key.append(method.getName());
            
            for (Object param : params) {
                key.append(":").append(param != null ? param.toString() : "null");
            }
            
            return key.toString();
        }
    }

    /**
     * Custom Cache Error Handler
     */
    public static class RedisCacheErrorHandler implements CacheErrorHandler {
        
        @Override
        public void handleCacheGetError(RuntimeException exception, org.springframework.cache.Cache cache, Object key) {
            log.error("Cache get error for cache '{}' and key '{}': {}", 
                     cache.getName(), key, exception.getMessage());
            // Don't propagate the error - fall back to calling the method
        }

        @Override
        public void handleCachePutError(RuntimeException exception, org.springframework.cache.Cache cache, Object key, Object value) {
            log.error("Cache put error for cache '{}' and key '{}': {}", 
                     cache.getName(), key, exception.getMessage());
            // Don't propagate the error
        }

        @Override
        public void handleCacheEvictError(RuntimeException exception, org.springframework.cache.Cache cache, Object key) {
            log.error("Cache evict error for cache '{}' and key '{}': {}", 
                     cache.getName(), key, exception.getMessage());
            // Don't propagate the error
        }

        @Override
        public void handleCacheClearError(RuntimeException exception, org.springframework.cache.Cache cache) {
            log.error("Cache clear error for cache '{}': {}", cache.getName(), exception.getMessage());
            // Don't propagate the error
        }
    }

    /**
     * Custom Cache Resolver for dynamic cache management
     */
    public static class CustomCacheResolver implements CacheResolver {
        private final CacheManager cacheManager;

        public CustomCacheResolver(CacheManager cacheManager) {
            this.cacheManager = cacheManager;
        }

        @Override
        public java.util.Collection<? extends org.springframework.cache.Cache> resolveCaches(
                org.springframework.cache.interceptor.CacheOperationInvocationContext<?> context) {
            
            java.util.Collection<String> cacheNames = context.getOperation().getCacheNames();
            java.util.Collection<org.springframework.cache.Cache> caches = new java.util.ArrayList<>();
            
            for (String cacheName : cacheNames) {
                org.springframework.cache.Cache cache = cacheManager.getCache(cacheName);
                if (cache != null) {
                    caches.add(cache);
                }
            }
            
            return caches;
        }
    }

    /**
     * Cache Warming Bean
     */
    @Bean
    @ConditionalOnProperty(name = "app.cache.warm-up.enabled", havingValue = "true", matchIfMissing = true)
    public CacheWarmupService cacheWarmupService(CacheManager cacheManager, RedisTemplate<String, Object> redisTemplate) {
        return new CacheWarmupService(cacheManager, redisTemplate, cacheProperties);
    }

    /**
     * Cache Monitoring Bean
     */
    @Bean
    @ConditionalOnProperty(name = "app.cache.monitoring.enabled", havingValue = "true", matchIfMissing = true)
    public CacheMonitoringService cacheMonitoringService(CacheManager cacheManager, RedisTemplate<String, Object> redisTemplate) {
        return new CacheMonitoringService(cacheManager, redisTemplate);
    }
}