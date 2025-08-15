package com.oddiya.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.session.data.redis.config.ConfigureRedisAction;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;
import org.springframework.session.security.SpringSessionBackedSessionRegistry;
import org.springframework.session.web.context.AbstractHttpSessionApplicationInitializer;
import org.springframework.session.web.http.CookieHttpSessionIdResolver;
import org.springframework.session.web.http.HttpSessionIdResolver;

import javax.servlet.http.Cookie;
import java.time.Duration;

/**
 * Redis Session Configuration
 * Agent 4 - Session Management Specialist
 * 
 * Comprehensive session management configuration using Redis for distributed sessions.
 * Includes security, concurrent session control, and session analytics.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableRedisHttpSession(
    maxInactiveIntervalInSeconds = 1800, // 30 minutes default
    redisNamespace = "oddiya:sessions"
)
@ConditionalOnProperty(name = "spring.session.store-type", havingValue = "redis")
@Profile({"redis", "redis-dev", "redis-prod", "aws"})
public class RedisSessionConfig extends AbstractHttpSessionApplicationInitializer {

    @Value("${spring.application.name:oddiya}")
    private String applicationName;

    @Value("${spring.profiles.active:default}")
    private String activeProfile;

    @Value("${spring.session.timeout:1800s}")
    private Duration sessionTimeout;

    @Value("${spring.session.cookie.name:ODDIYA_SESSION}")
    private String sessionCookieName;

    @Value("${spring.session.cookie.http-only:true}")
    private boolean httpOnly;

    @Value("${spring.session.cookie.secure:false}")
    private boolean secure;

    @Value("${spring.session.cookie.same-site:lax}")
    private String sameSite;

    @Value("${spring.session.cookie.max-age:1800}")
    private int maxAge;

    @Value("${app.session.max-concurrent-sessions:5}")
    private int maxConcurrentSessions;

    @Value("${app.session.prevent-invalid-session-creation:true}")
    private boolean preventInvalidSessionCreation;

    /**
     * Disable Redis configuration commands (required for AWS ElastiCache)
     */
    @Bean
    public static ConfigureRedisAction configureRedisAction() {
        return ConfigureRedisAction.NO_OP;
    }

    /**
     * Custom HTTP Session ID Resolver for cookie configuration
     */
    @Bean
    public HttpSessionIdResolver httpSessionIdResolver() {
        CookieHttpSessionIdResolver resolver = new CookieHttpSessionIdResolver();
        
        resolver.setCookieName(sessionCookieName);
        resolver.setCookieMaxAge(Duration.ofSeconds(maxAge));
        
        // Custom cookie serializer for enhanced security
        resolver.setCookieSerializer(cookieSerializer -> {
            cookieSerializer.setCookieName(sessionCookieName);
            cookieSerializer.setCookieMaxAge(Duration.ofSeconds(maxAge));
            cookieSerializer.setUseHttpOnlyCookie(httpOnly);
            cookieSerializer.setUseSecureCookie(secure);
            cookieSerializer.setSameSite(sameSite);
            cookieSerializer.setCookiePath("/");
            cookieSerializer.setDomainNamePattern("^.+?\\.(\\w+\\.[a-z]+)$"); // Matches subdomains
        });
        
        log.info("Configured session cookie: name={}, maxAge={}, httpOnly={}, secure={}, sameSite={}", 
                sessionCookieName, maxAge, httpOnly, secure, sameSite);
        
        return resolver;
    }

    /**
     * Session-specific Redis Template with optimized serialization
     */
    @Bean("sessionRedisTemplate")
    public RedisTemplate<String, Object> sessionRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Use String serializer for keys to ensure readability
        StringRedisSerializer keySerializer = new StringRedisSerializer();
        template.setKeySerializer(keySerializer);
        template.setHashKeySerializer(keySerializer);

        // Use JSON serializer for session data
        GenericJackson2JsonRedisSerializer valueSerializer = 
                new GenericJackson2JsonRedisSerializer(sessionObjectMapper());
        template.setValueSerializer(valueSerializer);
        template.setHashValueSerializer(valueSerializer);

        template.setDefaultSerializer(valueSerializer);
        template.afterPropertiesSet();

        return template;
    }

    /**
     * ObjectMapper optimized for session serialization
     */
    @Bean("sessionObjectMapper")
    public ObjectMapper sessionObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        mapper.findAndRegisterModules();
        
        // Configure for session security
        mapper.enableDefaultTyping(ObjectMapper.DefaultTyping.NON_FINAL);
        
        return mapper;
    }

    /**
     * Session Registry for concurrent session management
     */
    @Bean
    public SessionRegistry sessionRegistry() {
        SpringSessionBackedSessionRegistry registry = new SpringSessionBackedSessionRegistry<>();
        
        log.info("Configured session registry with concurrent session support");
        return registry;
    }

    /**
     * Session Event Listener for analytics and security
     */
    @Bean
    public SessionEventListener sessionEventListener() {
        return new SessionEventListener();
    }

    /**
     * Session Analytics Service
     */
    @Bean
    @ConditionalOnProperty(name = "app.session.analytics.enabled", havingValue = "true", matchIfMissing = true)
    public SessionAnalyticsService sessionAnalyticsService(RedisTemplate<String, Object> sessionRedisTemplate) {
        return new SessionAnalyticsService(sessionRedisTemplate);
    }

    /**
     * Session Security Service for enhanced security features
     */
    @Bean
    public SessionSecurityService sessionSecurityService(
            RedisTemplate<String, Object> sessionRedisTemplate,
            SessionRegistry sessionRegistry) {
        return new SessionSecurityService(
                sessionRedisTemplate, 
                sessionRegistry, 
                maxConcurrentSessions,
                preventInvalidSessionCreation
        );
    }

    /**
     * Session Cleanup Service for managing expired and invalid sessions
     */
    @Bean
    public SessionCleanupService sessionCleanupService(RedisTemplate<String, Object> sessionRedisTemplate) {
        return new SessionCleanupService(sessionRedisTemplate, applicationName);
    }

    /**
     * Custom Session Cookie Configuration for different environments
     */
    @Configuration
    @Profile("redis-dev")
    static class DevSessionConfig {
        
        @Bean
        public HttpSessionIdResolver devHttpSessionIdResolver() {
            CookieHttpSessionIdResolver resolver = new CookieHttpSessionIdResolver();
            resolver.setCookieName("ODDIYA_DEV_SESSION");
            resolver.setCookieSerializer(cookieSerializer -> {
                cookieSerializer.setCookieName("ODDIYA_DEV_SESSION");
                cookieSerializer.setCookieMaxAge(Duration.ofSeconds(3600)); // 1 hour for dev
                cookieSerializer.setUseHttpOnlyCookie(true);
                cookieSerializer.setUseSecureCookie(false); // HTTP allowed in dev
                cookieSerializer.setSameSite("Lax");
                cookieSerializer.setCookiePath("/");
            });
            return resolver;
        }
    }

    /**
     * Production Session Configuration with enhanced security
     */
    @Configuration
    @Profile("redis-prod")
    static class ProdSessionConfig {
        
        @Bean
        public HttpSessionIdResolver prodHttpSessionIdResolver() {
            CookieHttpSessionIdResolver resolver = new CookieHttpSessionIdResolver();
            resolver.setCookieName("ODDIYA_SESSION");
            resolver.setCookieSerializer(cookieSerializer -> {
                cookieSerializer.setCookieName("ODDIYA_SESSION");
                cookieSerializer.setCookieMaxAge(Duration.ofSeconds(1800)); // 30 minutes
                cookieSerializer.setUseHttpOnlyCookie(true);
                cookieSerializer.setUseSecureCookie(true); // HTTPS required
                cookieSerializer.setSameSite("Strict"); // Strict for production
                cookieSerializer.setCookiePath("/");
                // Set domain for production
                cookieSerializer.setDomainName("oddiya.com");
            });
            return resolver;
        }
    }

    /**
     * Session Metrics Configuration
     */
    @Bean
    @ConditionalOnProperty(name = "management.metrics.enabled", havingValue = "true", matchIfMissing = true)
    public SessionMetricsService sessionMetricsService(
            RedisTemplate<String, Object> sessionRedisTemplate,
            io.micrometer.core.instrument.MeterRegistry meterRegistry) {
        return new SessionMetricsService(sessionRedisTemplate, meterRegistry, applicationName);
    }
}