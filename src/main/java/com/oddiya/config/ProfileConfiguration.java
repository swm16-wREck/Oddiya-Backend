package com.oddiya.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Central configuration for profile-based bean switching.
 * Defines profile constants and provides utilities for profile-aware bean loading.
 */
@Configuration
@Slf4j
public class ProfileConfiguration {
    
    // Profile Constants
    public static final String LOCAL_PROFILE = "local";
    public static final String TEST_PROFILE = "test";
    public static final String AWS_PROFILE = "aws";
    public static final String DYNAMODB_PROFILE = "dynamodb";
    public static final String H2_PROFILE = "h2";
    public static final String DOCKER_PROFILE = "docker";
    
    // Profile Groups for easier management
    public static final String[] JPA_PROFILES = {LOCAL_PROFILE, TEST_PROFILE, H2_PROFILE, DOCKER_PROFILE};
    public static final String[] NOSQL_PROFILES = {DYNAMODB_PROFILE};
    public static final String[] AWS_PROFILES = {AWS_PROFILE, DYNAMODB_PROFILE};
    public static final String[] LOCAL_PROFILES = {LOCAL_PROFILE, TEST_PROFILE, H2_PROFILE};
    
    private final Environment environment;
    private final String activeProfiles;
    private Set<String> activeProfileSet;
    
    public ProfileConfiguration(Environment environment, 
                              @Value("${spring.profiles.active:local}") String activeProfiles) {
        this.environment = environment;
        this.activeProfiles = activeProfiles;
    }
    
    @PostConstruct
    public void initializeProfileConfiguration() {
        activeProfileSet = Arrays.stream(environment.getActiveProfiles())
                .collect(Collectors.toSet());
        
        log.info("Profile Configuration initialized");
        log.info("Active profiles: {}", activeProfileSet);
        log.info("Storage type: {}", getStorageType());
        log.info("Environment type: {}", getEnvironmentType());
        
        validateProfileConfiguration();
    }
    
    /**
     * Determines the primary storage type based on active profiles
     */
    public StorageType getStorageType() {
        if (isDynamoDBProfile()) {
            return StorageType.DYNAMODB;
        } else if (isJpaProfile()) {
            return StorageType.JPA;
        } else {
            log.warn("No recognized storage profile found, defaulting to JPA");
            return StorageType.JPA;
        }
    }
    
    /**
     * Determines the environment type
     */
    public EnvironmentType getEnvironmentType() {
        if (isAwsProfile()) {
            return EnvironmentType.AWS;
        } else if (isTestProfile()) {
            return EnvironmentType.TEST;
        } else if (isLocalProfile()) {
            return EnvironmentType.LOCAL;
        } else {
            return EnvironmentType.UNKNOWN;
        }
    }
    
    /**
     * Check if DynamoDB profile is active
     */
    public boolean isDynamoDBProfile() {
        return activeProfileSet.contains(DYNAMODB_PROFILE);
    }
    
    /**
     * Check if any JPA profile is active
     */
    public boolean isJpaProfile() {
        return Arrays.stream(JPA_PROFILES)
                .anyMatch(activeProfileSet::contains) || 
                (!isDynamoDBProfile() && activeProfileSet.isEmpty());
    }
    
    /**
     * Check if AWS profile is active
     */
    public boolean isAwsProfile() {
        return Arrays.stream(AWS_PROFILES)
                .anyMatch(activeProfileSet::contains);
    }
    
    /**
     * Check if local development profile is active
     */
    public boolean isLocalProfile() {
        return Arrays.stream(LOCAL_PROFILES)
                .anyMatch(activeProfileSet::contains) ||
                activeProfileSet.isEmpty(); // Default to local
    }
    
    /**
     * Check if test profile is active
     */
    public boolean isTestProfile() {
        return activeProfileSet.contains(TEST_PROFILE);
    }
    
    /**
     * Check if Docker profile is active
     */
    public boolean isDockerProfile() {
        return activeProfileSet.contains(DOCKER_PROFILE);
    }
    
    /**
     * Get datasource type for current profile
     */
    public DataSourceType getDataSourceType() {
        if (isDynamoDBProfile()) {
            return DataSourceType.DUMMY_H2; // DynamoDB uses dummy H2 to avoid conflicts
        } else if (isTestProfile()) {
            return DataSourceType.H2_MEMORY;
        } else if (isLocalProfile() && !isDockerProfile()) {
            return DataSourceType.POSTGRESQL_LOCAL;
        } else if (isDockerProfile()) {
            return DataSourceType.POSTGRESQL_DOCKER;
        } else {
            return DataSourceType.POSTGRESQL_LOCAL;
        }
    }
    
    /**
     * Get storage service type for current profile
     */
    public StorageServiceType getStorageServiceType() {
        if (isAwsProfile()) {
            return StorageServiceType.S3;
        } else {
            return StorageServiceType.LOCAL;
        }
    }
    
    /**
     * Get messaging service type for current profile
     */
    public MessagingServiceType getMessagingServiceType() {
        if (isAwsProfile()) {
            return MessagingServiceType.SQS;
        } else {
            return MessagingServiceType.LOCAL;
        }
    }
    
    /**
     * Validate profile configuration to ensure consistency
     */
    private void validateProfileConfiguration() {
        // Check for conflicting profiles
        boolean hasJpaProfile = Arrays.stream(JPA_PROFILES)
                .anyMatch(activeProfileSet::contains);
        boolean hasDynamoDBProfile = isDynamoDBProfile();
        
        if (hasJpaProfile && hasDynamoDBProfile) {
            log.warn("Both JPA and DynamoDB profiles are active. DynamoDB will take precedence.");
        }
        
        // Validate AWS dependencies
        if (isDynamoDBProfile() && !isAwsProfile()) {
            log.info("DynamoDB profile is active without AWS profile. Adding AWS profile implicitly.");
        }
        
        // Log configuration summary
        log.info("Profile Configuration Summary:");
        log.info("  Storage: {} ({})", getStorageType(), 
                isDynamoDBProfile() ? "NoSQL" : "Relational");
        log.info("  Environment: {}", getEnvironmentType());
        log.info("  DataSource: {}", getDataSourceType());
        log.info("  Storage Service: {}", getStorageServiceType());
        log.info("  Messaging Service: {}", getMessagingServiceType());
    }
    
    /**
     * Get profile-specific configuration value
     */
    public <T> T getProfileProperty(String propertyName, Class<T> targetType, T defaultValue) {
        return environment.getProperty(propertyName, targetType, defaultValue);
    }
    
    /**
     * Check if profile supports feature
     */
    public boolean supportsFeature(Feature feature) {
        switch (feature) {
            case COMPLEX_QUERIES:
                return isJpaProfile();
            case HIGH_SCALABILITY:
                return isDynamoDBProfile();
            case TRANSACTIONS:
                return isJpaProfile();
            case GLOBAL_SECONDARY_INDEXES:
                return isDynamoDBProfile();
            case FULL_TEXT_SEARCH:
                return isJpaProfile();
            case AUTO_SCALING:
                return isDynamoDBProfile();
            case ACID_COMPLIANCE:
                return isJpaProfile();
            case EVENTUAL_CONSISTENCY:
                return isDynamoDBProfile();
            default:
                return false;
        }
    }
    
    // Enums for type safety
    public enum StorageType {
        JPA("Java Persistence API"),
        DYNAMODB("Amazon DynamoDB");
        
        private final String description;
        
        StorageType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    public enum EnvironmentType {
        LOCAL("Local Development"),
        TEST("Test Environment"),
        AWS("AWS Cloud Environment"),
        UNKNOWN("Unknown Environment");
        
        private final String description;
        
        EnvironmentType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    public enum DataSourceType {
        H2_MEMORY("H2 In-Memory Database"),
        DUMMY_H2("H2 Dummy Database for DynamoDB"),
        POSTGRESQL_LOCAL("PostgreSQL Local"),
        POSTGRESQL_DOCKER("PostgreSQL in Docker"),
        POSTGRESQL_AWS("PostgreSQL on AWS");
        
        private final String description;
        
        DataSourceType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    public enum StorageServiceType {
        LOCAL("Local File Storage"),
        S3("Amazon S3");
        
        private final String description;
        
        StorageServiceType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    public enum MessagingServiceType {
        LOCAL("Local In-Memory Messaging"),
        SQS("Amazon SQS");
        
        private final String description;
        
        MessagingServiceType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    public enum Feature {
        COMPLEX_QUERIES,
        HIGH_SCALABILITY,
        TRANSACTIONS,
        GLOBAL_SECONDARY_INDEXES,
        FULL_TEXT_SEARCH,
        AUTO_SCALING,
        ACID_COMPLIANCE,
        EVENTUAL_CONSISTENCY
    }
    
    /**
     * Get recommended profile for use case
     */
    public static String getRecommendedProfile(String useCase) {
        switch (useCase.toLowerCase()) {
            case "development":
            case "debugging":
                return LOCAL_PROFILE;
            case "testing":
            case "ci/cd":
                return TEST_PROFILE;
            case "production":
            case "scaling":
            case "high-availability":
                return DYNAMODB_PROFILE;
            case "complex-analytics":
            case "reporting":
                return LOCAL_PROFILE; // JPA for complex queries
            case "containerized":
                return DOCKER_PROFILE;
            default:
                return LOCAL_PROFILE;
        }
    }
    
    /**
     * Get migration path from current to target profile
     */
    public String getMigrationPath(String targetProfile) {
        String currentStorageType = getStorageType().name();
        StorageType targetStorageType = targetProfile.equals(DYNAMODB_PROFILE) ? 
                StorageType.DYNAMODB : StorageType.JPA;
        
        if (getStorageType() == targetStorageType) {
            return "No migration needed - same storage type";
        } else {
            return String.format("Migration path: %s -> %s (Use DataMigrationService)", 
                    currentStorageType, targetStorageType.name());
        }
    }
}