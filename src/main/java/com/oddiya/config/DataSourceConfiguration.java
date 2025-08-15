package com.oddiya.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;

/**
 * Centralized datasource configuration that handles switching between
 * JPA datasources (PostgreSQL).
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class DataSourceConfiguration {
    
    private final Environment environment;
    private final ProfileConfiguration profileConfiguration;
    
    /**
     * Primary datasource for JPA profiles (all profiles)
     */
    @Bean
    @Primary
    public DataSource jpaDataSource() {
        ProfileConfiguration.DataSourceType dataSourceType = profileConfiguration.getDataSourceType();
        
        log.info("Configuring JPA DataSource: {}", dataSourceType.getDescription());
        
        HikariConfig config = new HikariConfig();
        
        switch (dataSourceType) {
            case POSTGRESQL_TESTCONTAINERS:
                configurePostgreSQLTestContainersDataSource(config);
                break;
            case POSTGRESQL_LOCAL:
                configurePostgreSQLLocalDataSource(config);
                break;
            case POSTGRESQL_DOCKER:
                configurePostgreSQLDockerDataSource(config);
                break;
            case POSTGRESQL_AWS:
                configurePostgreSQLAwsDataSource(config);
                break;
            default:
                log.warn("Unknown datasource type {}, falling back to PostgreSQL Local", dataSourceType);
                configurePostgreSQLLocalDataSource(config);
        }
        
        // Common HikariCP settings
        configureCommonHikariSettings(config);
        
        HikariDataSource dataSource = new HikariDataSource(config);
        log.info("JPA DataSource configured successfully: {}", dataSource.getJdbcUrl());
        
        return dataSource;
    }
    
    
    
    private void configurePostgreSQLTestContainersDataSource(HikariConfig config) {
        // TestContainers PostgreSQL configuration - handled by TestContainersConfiguration
        String url = environment.getProperty("spring.datasource.url", 
                "jdbc:tc:postgresql:15://localhost/testdb?TC_INITSCRIPT=test-init.sql&TC_DAEMON=true");
        String username = environment.getProperty("spring.datasource.username", "test");
        String password = environment.getProperty("spring.datasource.password", "test");
        
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("org.testcontainers.jdbc.ContainerDatabaseDriver");
        
        // TestContainers specific optimizations
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(10000);
        
        log.info("Configuring PostgreSQL TestContainers: {}", maskUrl(url));
    }
    
    private void configurePostgreSQLLocalDataSource(HikariConfig config) {
        String url = environment.getProperty("spring.datasource.url", 
                "jdbc:postgresql://localhost:5432/oddiya");
        String username = environment.getProperty("spring.datasource.username", "oddiya");
        String password = environment.getProperty("spring.datasource.password", "oddiya123");
        
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("org.postgresql.Driver");
        
        log.info("Configuring PostgreSQL Local: {}", maskUrl(url));
    }
    
    private void configurePostgreSQLDockerDataSource(HikariConfig config) {
        // Docker typically uses different networking
        String url = environment.getProperty("spring.datasource.url", 
                "jdbc:postgresql://db:5432/oddiya");
        String username = environment.getProperty("spring.datasource.username", "oddiya");
        String password = environment.getProperty("spring.datasource.password", "oddiya123");
        
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("org.postgresql.Driver");
        
        log.info("Configuring PostgreSQL Docker: {}", maskUrl(url));
    }
    
    private void configurePostgreSQLAwsDataSource(HikariConfig config) {
        // AWS RDS configuration
        String url = environment.getProperty("spring.datasource.url");
        String username = environment.getProperty("spring.datasource.username");
        String password = environment.getProperty("spring.datasource.password");
        
        if (url == null || username == null || password == null) {
            throw new IllegalStateException("AWS RDS configuration incomplete. " +
                    "Please set spring.datasource.url, username, and password");
        }
        
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("org.postgresql.Driver");
        
        // AWS RDS specific optimizations
        config.setMaximumPoolSize(15);
        config.setMinimumIdle(5);
        config.setConnectionTimeout(30000);
        
        log.info("Configuring PostgreSQL AWS RDS: {}", maskUrl(url));
    }
    
    private void configureCommonHikariSettings(HikariConfig config) {
        // Connection pool settings from application.yml or defaults
        config.setMaximumPoolSize(
                environment.getProperty("spring.datasource.hikari.maximum-pool-size", 
                        Integer.class, 10));
        config.setMinimumIdle(
                environment.getProperty("spring.datasource.hikari.minimum-idle", 
                        Integer.class, 5));
        config.setConnectionTimeout(
                environment.getProperty("spring.datasource.hikari.connection-timeout", 
                        Long.class, 30000L));
        config.setIdleTimeout(
                environment.getProperty("spring.datasource.hikari.idle-timeout", 
                        Long.class, 600000L));
        config.setMaxLifetime(
                environment.getProperty("spring.datasource.hikari.max-lifetime", 
                        Long.class, 1800000L));
        
        // Performance optimizations
        config.setLeakDetectionThreshold(60000);
        config.setConnectionTestQuery("SELECT 1");
        
        // Connection pool name for monitoring
        String poolName = String.format("OddiyaCP-%s", 
                profileConfiguration.getStorageType().name());
        config.setPoolName(poolName);
        
        log.debug("Configured HikariCP settings: maxPoolSize={}, minIdle={}", 
                config.getMaximumPoolSize(), config.getMinimumIdle());
    }
    
    /**
     * Mask sensitive information in URLs for logging
     */
    private String maskUrl(String url) {
        if (url == null) return "null";
        
        // Mask passwords in connection strings
        return url.replaceAll("password=[^;\\s]+", "password=***");
    }
    
    /**
     * DataSource health check and information
     */
    @Configuration
    public static class DataSourceInfo {
        
        private final ProfileConfiguration profileConfiguration;
        private final DataSource dataSource;
        
        public DataSourceInfo(ProfileConfiguration profileConfiguration,
                             DataSource dataSource) {
            this.profileConfiguration = profileConfiguration;
            this.dataSource = dataSource;
        }
        
        public DataSourceStatus getDataSourceStatus() {
            return DataSourceStatus.builder()
                    .storageType(profileConfiguration.getStorageType())
                    .dataSourceType(profileConfiguration.getDataSourceType())
                    .environmentType(profileConfiguration.getEnvironmentType())
                    .isHealthy(checkDataSourceHealth())
                    .connectionPoolInfo(getConnectionPoolInfo())
                    .build();
        }
        
        private boolean checkDataSourceHealth() {
            try {
                if (dataSource instanceof HikariDataSource) {
                    HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
                    return !hikariDataSource.isClosed() && 
                           hikariDataSource.getHikariPoolMXBean().getActiveConnections() >= 0;
                }
                return dataSource != null;
            } catch (Exception e) {
                log.error("DataSource health check failed: {}", e.getMessage());
                return false;
            }
        }
        
        private ConnectionPoolInfo getConnectionPoolInfo() {
            try {
                if (dataSource instanceof HikariDataSource) {
                    HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
                    var poolMXBean = hikariDataSource.getHikariPoolMXBean();
                    
                    return ConnectionPoolInfo.builder()
                            .poolName(hikariDataSource.getPoolName())
                            .activeConnections(poolMXBean.getActiveConnections())
                            .idleConnections(poolMXBean.getIdleConnections())
                            .totalConnections(poolMXBean.getTotalConnections())
                            .threadsAwaitingConnection(poolMXBean.getThreadsAwaitingConnection())
                            .build();
                }
            } catch (Exception e) {
                log.error("Failed to get connection pool info: {}", e.getMessage());
            }
            
            return ConnectionPoolInfo.builder()
                    .poolName("Unknown")
                    .activeConnections(0)
                    .idleConnections(0)
                    .totalConnections(0)
                    .threadsAwaitingConnection(0)
                    .build();
        }
        
        public void logDataSourceInfo() {
            DataSourceStatus status = getDataSourceStatus();
            log.info("DataSource Information:");
            log.info("  Storage Type: {}", status.getStorageType().getDescription());
            log.info("  DataSource Type: {}", status.getDataSourceType().getDescription());
            log.info("  Environment: {}", status.getEnvironmentType().getDescription());
            log.info("  Health Status: {}", status.isHealthy() ? "HEALTHY" : "UNHEALTHY");
            
            ConnectionPoolInfo poolInfo = status.getConnectionPoolInfo();
            log.info("  Connection Pool: {} (Active: {}, Idle: {}, Total: {})",
                    poolInfo.getPoolName(),
                    poolInfo.getActiveConnections(),
                    poolInfo.getIdleConnections(),
                    poolInfo.getTotalConnections());
        }
    }
    
    // Data transfer objects for status reporting
    @lombok.Builder
    @lombok.Data
    public static class DataSourceStatus {
        private ProfileConfiguration.StorageType storageType;
        private ProfileConfiguration.DataSourceType dataSourceType;
        private ProfileConfiguration.EnvironmentType environmentType;
        private boolean isHealthy;
        private ConnectionPoolInfo connectionPoolInfo;
    }
    
    @lombok.Builder
    @lombok.Data
    public static class ConnectionPoolInfo {
        private String poolName;
        private int activeConnections;
        private int idleConnections;
        private int totalConnections;
        private int threadsAwaitingConnection;
    }
}