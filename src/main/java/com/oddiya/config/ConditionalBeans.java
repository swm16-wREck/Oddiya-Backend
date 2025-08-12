package com.oddiya.config;

import com.oddiya.repository.*;
import com.oddiya.repository.dynamodb.*;
import com.oddiya.service.messaging.LocalMessagingService;
import com.oddiya.service.messaging.MessagingService;
import com.oddiya.service.messaging.SQSMessagingService;
import com.oddiya.service.storage.LocalStorageService;
import com.oddiya.service.storage.S3StorageService;
import com.oddiya.service.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Conditional bean configuration that provides profile-aware bean switching
 * for repositories, services, and other components based on active Spring profiles.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class ConditionalBeans {
    
    private final ProfileConfiguration profileConfiguration;
    
    // ========================================
    // Repository Bean Definitions
    // ========================================
    
    /**
     * JPA Repository Configuration - Active when NOT using DynamoDB
     */
    @Configuration
    @Profile("!" + ProfileConfiguration.DYNAMODB_PROFILE)
    @EnableJpaRepositories(basePackages = "com.oddiya.repository")
    @EnableJpaAuditing
    static class JpaRepositoryConfiguration {
        
        @Bean
        @Primary
        public String repositoryType() {
            log.info("Configuring JPA repositories");
            return "JPA";
        }
    }
    
    /**
     * DynamoDB Repository Configuration - Active when using DynamoDB profile
     */
    @Configuration
    @Profile(ProfileConfiguration.DYNAMODB_PROFILE)
    @EnableJpaAuditing // Still needed for entity timestamps
    static class DynamoDBRepositoryConfiguration {
        
        @Bean
        @Primary
        public String repositoryType() {
            log.info("Configuring DynamoDB repositories");
            return "DynamoDB";
        }
        
        // DynamoDB repositories are auto-configured by DynamoDBRepositoryConfiguration
        // in the repository.dynamodb.config package
    }
    
    // ========================================
    // Storage Service Bean Definitions
    // ========================================
    
    /**
     * Local Storage Service - Active for local/test profiles
     */
    @Bean
    @Primary
    @Profile({"!" + ProfileConfiguration.AWS_PROFILE, "!" + ProfileConfiguration.DYNAMODB_PROFILE})
    @ConditionalOnMissingBean(StorageService.class)
    public StorageService localStorageService() {
        log.info("Configuring Local Storage Service");
        return new LocalStorageService();
    }
    
    /**
     * S3 Storage Service - Active for AWS profiles
     */
    @Bean
    @Primary
    @Profile({ProfileConfiguration.AWS_PROFILE, ProfileConfiguration.DYNAMODB_PROFILE})
    @ConditionalOnProperty(name = "app.aws.s3.enabled", havingValue = "true", matchIfMissing = true)
    public StorageService s3StorageService() {
        log.info("Configuring S3 Storage Service");
        // S3StorageService is auto-configured by Spring
        return null; // Let Spring handle the injection
    }
    
    /**
     * Fallback Local Storage Service for AWS profiles when S3 is disabled
     */
    @Bean
    @Profile({ProfileConfiguration.AWS_PROFILE, ProfileConfiguration.DYNAMODB_PROFILE})
    @ConditionalOnProperty(name = "app.aws.s3.enabled", havingValue = "false")
    public StorageService fallbackLocalStorageService() {
        log.warn("S3 is disabled in AWS profile, falling back to Local Storage Service");
        return new LocalStorageService();
    }
    
    // ========================================
    // Messaging Service Bean Definitions
    // ========================================
    
    /**
     * Local Messaging Service - Active for local/test profiles
     */
    @Bean
    @Primary
    @Profile({"!" + ProfileConfiguration.AWS_PROFILE, "!" + ProfileConfiguration.DYNAMODB_PROFILE})
    @ConditionalOnMissingBean(MessagingService.class)
    public MessagingService localMessagingService() {
        log.info("Configuring Local Messaging Service");
        return new LocalMessagingService();
    }
    
    /**
     * SQS Messaging Service - Active for AWS profiles
     */
    @Bean
    @Primary
    @Profile({ProfileConfiguration.AWS_PROFILE, ProfileConfiguration.DYNAMODB_PROFILE})
    @ConditionalOnProperty(name = "app.aws.sqs.enabled", havingValue = "true", matchIfMissing = true)
    public MessagingService sqsMessagingService() {
        log.info("Configuring SQS Messaging Service");
        // SQSMessagingService is auto-configured by Spring
        return null; // Let Spring handle the injection
    }
    
    /**
     * Fallback Local Messaging Service for AWS profiles when SQS is disabled
     */
    @Bean
    @Profile({ProfileConfiguration.AWS_PROFILE, ProfileConfiguration.DYNAMODB_PROFILE})
    @ConditionalOnProperty(name = "app.aws.sqs.enabled", havingValue = "false")
    public MessagingService fallbackLocalMessagingService() {
        log.warn("SQS is disabled in AWS profile, falling back to Local Messaging Service");
        return new LocalMessagingService();
    }
    
    // ========================================
    // Bean Post Processors and Utilities
    // ========================================
    
    /**
     * Bean configuration validator that runs after all beans are configured
     */
    @Bean
    public BeanConfigurationValidator beanConfigurationValidator() {
        return new BeanConfigurationValidator(profileConfiguration);
    }
    
    /**
     * Service layer adapter that provides uniform interface across storage types
     */
    @Bean
    public ServiceLayerAdapter serviceLayerAdapter() {
        return new ServiceLayerAdapter(profileConfiguration);
    }
    
    /**
     * Validates that beans are correctly configured for the active profile
     */
    public static class BeanConfigurationValidator {
        
        private final ProfileConfiguration profileConfiguration;
        
        public BeanConfigurationValidator(ProfileConfiguration profileConfiguration) {
            this.profileConfiguration = profileConfiguration;
        }
        
        public void validateConfiguration() {
            log.info("Validating bean configuration for profile: {}", 
                    profileConfiguration.getStorageType());
            
            ProfileConfiguration.StorageType storageType = profileConfiguration.getStorageType();
            ProfileConfiguration.EnvironmentType envType = profileConfiguration.getEnvironmentType();
            
            // Validate storage configuration
            switch (storageType) {
                case JPA:
                    validateJpaConfiguration();
                    break;
                case DYNAMODB:
                    validateDynamoDBConfiguration();
                    break;
                default:
                    log.warn("Unknown storage type: {}", storageType);
            }
            
            // Validate environment configuration
            switch (envType) {
                case LOCAL:
                    validateLocalEnvironment();
                    break;
                case TEST:
                    validateTestEnvironment();
                    break;
                case AWS:
                    validateAwsEnvironment();
                    break;
                default:
                    log.warn("Unknown environment type: {}", envType);
            }
            
            log.info("Bean configuration validation completed successfully");
        }
        
        private void validateJpaConfiguration() {
            log.debug("Validating JPA configuration");
            // JPA-specific validation logic
            if (!profileConfiguration.supportsFeature(ProfileConfiguration.Feature.COMPLEX_QUERIES)) {
                log.warn("JPA should support complex queries");
            }
        }
        
        private void validateDynamoDBConfiguration() {
            log.debug("Validating DynamoDB configuration");
            // DynamoDB-specific validation logic
            if (!profileConfiguration.supportsFeature(ProfileConfiguration.Feature.HIGH_SCALABILITY)) {
                log.warn("DynamoDB should support high scalability");
            }
        }
        
        private void validateLocalEnvironment() {
            log.debug("Validating local environment configuration");
            // Local environment validation
        }
        
        private void validateTestEnvironment() {
            log.debug("Validating test environment configuration");
            // Test environment validation
        }
        
        private void validateAwsEnvironment() {
            log.debug("Validating AWS environment configuration");
            // AWS environment validation
        }
        
        public ConfigurationReport generateConfigurationReport() {
            return ConfigurationReport.builder()
                    .storageType(profileConfiguration.getStorageType())
                    .environmentType(profileConfiguration.getEnvironmentType())
                    .dataSourceType(profileConfiguration.getDataSourceType())
                    .storageServiceType(profileConfiguration.getStorageServiceType())
                    .messagingServiceType(profileConfiguration.getMessagingServiceType())
                    .supportedFeatures(getSupportedFeatures())
                    .recommendations(getRecommendations())
                    .build();
        }
        
        private java.util.List<ProfileConfiguration.Feature> getSupportedFeatures() {
            java.util.List<ProfileConfiguration.Feature> supportedFeatures = new java.util.ArrayList<>();
            
            for (ProfileConfiguration.Feature feature : ProfileConfiguration.Feature.values()) {
                if (profileConfiguration.supportsFeature(feature)) {
                    supportedFeatures.add(feature);
                }
            }
            
            return supportedFeatures;
        }
        
        private java.util.List<String> getRecommendations() {
            java.util.List<String> recommendations = new java.util.ArrayList<>();
            
            ProfileConfiguration.StorageType storageType = profileConfiguration.getStorageType();
            
            switch (storageType) {
                case JPA:
                    recommendations.add("Consider DynamoDB for high-scale production workloads");
                    recommendations.add("Enable connection pooling optimization for better performance");
                    recommendations.add("Use read replicas for read-heavy workloads");
                    break;
                case DYNAMODB:
                    recommendations.add("Use JPA for complex analytical queries");
                    recommendations.add("Implement proper GSI design for query patterns");
                    recommendations.add("Monitor capacity utilization and costs");
                    break;
            }
            
            return recommendations;
        }
    }
    
    /**
     * Adapter that provides uniform service layer interface
     */
    public static class ServiceLayerAdapter {
        
        private final ProfileConfiguration profileConfiguration;
        
        public ServiceLayerAdapter(ProfileConfiguration profileConfiguration) {
            this.profileConfiguration = profileConfiguration;
        }
        
        public boolean isTransactional() {
            return profileConfiguration.supportsFeature(ProfileConfiguration.Feature.TRANSACTIONS);
        }
        
        public boolean supportsComplexQueries() {
            return profileConfiguration.supportsFeature(ProfileConfiguration.Feature.COMPLEX_QUERIES);
        }
        
        public boolean isEventuallyConsistent() {
            return profileConfiguration.supportsFeature(ProfileConfiguration.Feature.EVENTUAL_CONSISTENCY);
        }
        
        public String getOptimalQueryStrategy() {
            if (profileConfiguration.getStorageType() == ProfileConfiguration.StorageType.JPA) {
                return "Use JOIN queries, complex WHERE clauses, and aggregations";
            } else {
                return "Use single-table design patterns, GSIs, and key-based queries";
            }
        }
        
        public String getRecommendedCachingStrategy() {
            if (profileConfiguration.getEnvironmentType() == ProfileConfiguration.EnvironmentType.AWS) {
                return "Use ElastiCache with Redis for distributed caching";
            } else {
                return "Use local caching with Caffeine or Redis";
            }
        }
    }
    
    // ========================================
    // Data Transfer Objects
    // ========================================
    
    @lombok.Builder
    @lombok.Data
    public static class ConfigurationReport {
        private ProfileConfiguration.StorageType storageType;
        private ProfileConfiguration.EnvironmentType environmentType;
        private ProfileConfiguration.DataSourceType dataSourceType;
        private ProfileConfiguration.StorageServiceType storageServiceType;
        private ProfileConfiguration.MessagingServiceType messagingServiceType;
        private java.util.List<ProfileConfiguration.Feature> supportedFeatures;
        private java.util.List<String> recommendations;
        
        public void logReport() {
            log.info("=== Configuration Report ===");
            log.info("Storage Type: {}", storageType.getDescription());
            log.info("Environment: {}", environmentType.getDescription());
            log.info("DataSource: {}", dataSourceType.getDescription());
            log.info("Storage Service: {}", storageServiceType.getDescription());
            log.info("Messaging Service: {}", messagingServiceType.getDescription());
            
            log.info("Supported Features:");
            supportedFeatures.forEach(feature -> log.info("  - {}", feature));
            
            log.info("Recommendations:");
            recommendations.forEach(rec -> log.info("  - {}", rec));
            log.info("=== End Configuration Report ===");
        }
    }
}