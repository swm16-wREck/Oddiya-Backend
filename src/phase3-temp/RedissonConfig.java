package com.oddiya.config;

import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SentinelServersConfig;
import org.redisson.config.SingleServerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Redisson Configuration for Distributed Locks
 * Agent 5 - Cache Patterns Developer
 * 
 * Configuration for Redisson client used for distributed locking,
 * semaphores, and other advanced Redis patterns.
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "app.cache.distributed-locks.enabled", havingValue = "true", matchIfMissing = true)
@Profile({"redis", "redis-dev", "redis-prod", "aws"})
public class RedissonConfig {

    @Value("${spring.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.redis.port:6379}")
    private int redisPort;

    @Value("${spring.redis.password:}")
    private String redisPassword;

    @Value("${spring.redis.database:0}")
    private int redisDatabase;

    @Value("${spring.redis.timeout:2000ms}")
    private String redisTimeout;

    @Value("${spring.redis.sentinel.master:}")
    private String sentinelMaster;

    @Value("${spring.redis.sentinel.nodes:}")
    private String sentinelNodes;

    @Value("${spring.redis.sentinel.password:}")
    private String sentinelPassword;

    @Value("${spring.application.name:oddiya}")
    private String applicationName;

    /**
     * Redisson Client Configuration
     */
    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();
        
        // Configure based on whether Sentinel is available
        if (sentinelMaster != null && !sentinelMaster.trim().isEmpty() && 
            sentinelNodes != null && !sentinelNodes.trim().isEmpty()) {
            
            log.info("Configuring Redisson with Redis Sentinel: master={}, nodes={}", sentinelMaster, sentinelNodes);
            configureSentinelMode(config);
        } else {
            log.info("Configuring Redisson with single Redis server: {}:{}", redisHost, redisPort);
            configureSingleServerMode(config);
        }

        // Common configuration
        configureCommon(config);

        RedissonClient client = Redisson.create(config);
        log.info("Redisson client created successfully");
        
        return client;
    }

    /**
     * Configure Redisson for single server mode
     */
    private void configureSingleServerMode(Config config) {
        String address = String.format("redis://%s:%d", redisHost, redisPort);
        
        SingleServerConfig serverConfig = config.useSingleServer()
                .setAddress(address)
                .setDatabase(redisDatabase)
                .setConnectTimeout(parseTimeout(redisTimeout))
                .setTimeout(parseTimeout(redisTimeout))
                .setRetryAttempts(3)
                .setRetryInterval(1500)
                .setConnectionPoolSize(64)
                .setConnectionMinimumIdleSize(10)
                .setSubscriptionConnectionPoolSize(50)
                .setSubscriptionConnectionMinimumIdleSize(1)
                .setIdleConnectionTimeout(10000)
                .setPingConnectionInterval(30000)
                .setKeepAlive(true)
                .setTcpNoDelay(true);

        if (redisPassword != null && !redisPassword.trim().isEmpty()) {
            serverConfig.setPassword(redisPassword);
        }
    }

    /**
     * Configure Redisson for Sentinel mode
     */
    private void configureSentinelMode(Config config) {
        String[] nodes = sentinelNodes.split(",");
        String[] sentinelAddresses = new String[nodes.length];
        
        for (int i = 0; i < nodes.length; i++) {
            String node = nodes[i].trim();
            if (!node.startsWith("redis://")) {
                node = "redis://" + node;
            }
            sentinelAddresses[i] = node;
        }

        SentinelServersConfig sentinelConfig = config.useSentinelServers()
                .setMasterName(sentinelMaster)
                .addSentinelAddress(sentinelAddresses)
                .setDatabase(redisDatabase)
                .setConnectTimeout(parseTimeout(redisTimeout))
                .setTimeout(parseTimeout(redisTimeout))
                .setRetryAttempts(3)
                .setRetryInterval(1500)
                .setMasterConnectionPoolSize(64)
                .setMasterConnectionMinimumIdleSize(10)
                .setSlaveConnectionPoolSize(64)
                .setSlaveConnectionMinimumIdleSize(10)
                .setSubscriptionConnectionPoolSize(50)
                .setSubscriptionConnectionMinimumIdleSize(1)
                .setIdleConnectionTimeout(10000)
                .setPingConnectionInterval(30000)
                .setKeepAlive(true)
                .setTcpNoDelay(true)
                .setCheckSentinelsList(false) // Set to false for AWS ElastiCache compatibility
                .setScanInterval(2000); // Scan interval for sentinel monitoring

        if (redisPassword != null && !redisPassword.trim().isEmpty()) {
            sentinelConfig.setPassword(redisPassword);
        }

        if (sentinelPassword != null && !sentinelPassword.trim().isEmpty()) {
            sentinelConfig.setSentinelPassword(sentinelPassword);
        }
    }

    /**
     * Configure common Redisson settings
     */
    private void configureCommon(Config config) {
        // Codec configuration for JSON serialization
        config.setCodec(new org.redisson.codec.JsonJacksonCodec());
        
        // Threading configuration
        config.setThreads(Runtime.getRuntime().availableProcessors() * 2);
        config.setNettyThreads(Runtime.getRuntime().availableProcessors() * 2);
        
        // Lock watchdog timeout (default is 30 seconds)
        config.setLockWatchdogTimeout(30000);
        
        // Keep pubsub order
        config.setKeepPubSubOrder(true);
        
        // Use script cache
        config.setUseScriptCache(true);
        
        // Configure transport mode
        config.setTransportMode(org.redisson.config.TransportMode.NIO);
        
        log.debug("Redisson common configuration applied");
    }

    /**
     * Parse timeout string to milliseconds
     */
    private int parseTimeout(String timeout) {
        try {
            if (timeout.endsWith("ms")) {
                return Integer.parseInt(timeout.substring(0, timeout.length() - 2));
            } else if (timeout.endsWith("s")) {
                return Integer.parseInt(timeout.substring(0, timeout.length() - 1)) * 1000;
            } else {
                return Integer.parseInt(timeout);
            }
        } catch (NumberFormatException e) {
            log.warn("Could not parse timeout '{}', using default 2000ms", timeout);
            return 2000;
        }
    }
}