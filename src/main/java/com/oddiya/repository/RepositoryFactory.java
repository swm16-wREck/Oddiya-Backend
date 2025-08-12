package com.oddiya.repository;

import com.oddiya.repository.dynamodb.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

/**
 * Repository factory to choose between JPA and DynamoDB repositories based on active profile.
 * Provides a central configuration point for repository selection.
 */
@Configuration
@Slf4j
public class RepositoryFactory {
    
    // DynamoDB Repository Beans - Active when DynamoDB profile is used
    
    @Bean
    @Primary
    @ConditionalOnProperty(name = "spring.profiles.active", havingValue = "dynamodb")
    public UserRepository dynamoDbUserRepository(
            @Autowired(required = false) DynamoDBUserRepository dynamoDBUserRepository) {
        
        if (dynamoDBUserRepository != null) {
            log.info("Using DynamoDB UserRepository implementation");
            return dynamoDBUserRepository;
        }
        
        log.warn("DynamoDB UserRepository not available, falling back to JPA");
        return null;
    }
    
    @Bean
    @Primary
    @ConditionalOnProperty(name = "spring.profiles.active", havingValue = "dynamodb")
    public TravelPlanRepository dynamoDbTravelPlanRepository(
            @Autowired(required = false) DynamoDBTravelPlanRepository dynamoDBTravelPlanRepository) {
        
        if (dynamoDBTravelPlanRepository != null) {
            log.info("Using DynamoDB TravelPlanRepository implementation");
            return dynamoDBTravelPlanRepository;
        }
        
        log.warn("DynamoDB TravelPlanRepository not available, falling back to JPA");
        return null;
    }
    
    @Bean
    @Primary
    @ConditionalOnProperty(name = "spring.profiles.active", havingValue = "dynamodb")
    public PlaceRepository dynamoDbPlaceRepository(
            @Autowired(required = false) DynamoDBPlaceRepository dynamoDBPlaceRepository) {
        
        if (dynamoDBPlaceRepository != null) {
            log.info("Using DynamoDB PlaceRepository implementation");
            return dynamoDBPlaceRepository;
        }
        
        log.warn("DynamoDB PlaceRepository not available, falling back to JPA");
        return null;
    }
    
    @Bean
    @Primary
    @ConditionalOnProperty(name = "spring.profiles.active", havingValue = "dynamodb")
    public SavedPlanRepository dynamoDbSavedPlanRepository(
            @Autowired(required = false) DynamoDBSavedPlanRepository dynamoDBSavedPlanRepository) {
        
        if (dynamoDBSavedPlanRepository != null) {
            log.info("Using DynamoDB SavedPlanRepository implementation");
            return dynamoDBSavedPlanRepository;
        }
        
        log.warn("DynamoDB SavedPlanRepository not available, falling back to JPA");
        return null;
    }
    
    // JPA Repository Beans - Active when JPA profiles are used (h2, postgresql, etc.)
    
    @Bean
    @Primary
    @Profile("!dynamodb")
    public UserRepository jpaUserRepository() {
        log.info("Using JPA UserRepository implementation");
        // Spring Data JPA will automatically provide the implementation
        return null; // Let Spring Data JPA auto-configure
    }
    
    @Bean
    @Primary
    @Profile("!dynamodb")
    public TravelPlanRepository jpaTravelPlanRepository() {
        log.info("Using JPA TravelPlanRepository implementation");
        // Spring Data JPA will automatically provide the implementation
        return null; // Let Spring Data JPA auto-configure
    }
    
    @Bean
    @Primary
    @Profile("!dynamodb")
    public PlaceRepository jpaPlaceRepository() {
        log.info("Using JPA PlaceRepository implementation");
        // Spring Data JPA will automatically provide the implementation
        return null; // Let Spring Data JPA auto-configure
    }
    
    @Bean
    @Primary
    @Profile("!dynamodb")
    public SavedPlanRepository jpaSavedPlanRepository() {
        log.info("Using JPA SavedPlanRepository implementation");
        // Spring Data JPA will automatically provide the implementation
        return null; // Let Spring Data JPA auto-configure
    }
    
    /**
     * Repository selector utility class for manual repository selection
     */
    @Configuration
    public static class RepositorySelector {
        
        private final UserRepository userRepository;
        private final TravelPlanRepository travelPlanRepository;
        private final PlaceRepository placeRepository;
        private final SavedPlanRepository savedPlanRepository;
        private final ItineraryItemRepository itineraryItemRepository;
        
        public RepositorySelector(@Autowired UserRepository userRepository,
                                @Autowired TravelPlanRepository travelPlanRepository,
                                @Autowired PlaceRepository placeRepository,
                                @Autowired SavedPlanRepository savedPlanRepository,
                                @Autowired ItineraryItemRepository itineraryItemRepository) {
            this.userRepository = userRepository;
            this.travelPlanRepository = travelPlanRepository;
            this.placeRepository = placeRepository;
            this.savedPlanRepository = savedPlanRepository;
            this.itineraryItemRepository = itineraryItemRepository;
        }
        
        public UserRepository getUserRepository() {
            return userRepository;
        }
        
        public TravelPlanRepository getTravelPlanRepository() {
            return travelPlanRepository;
        }
        
        public PlaceRepository getPlaceRepository() {
            return placeRepository;
        }
        
        public SavedPlanRepository getSavedPlanRepository() {
            return savedPlanRepository;
        }
        
        public ItineraryItemRepository getItineraryItemRepository() {
            return itineraryItemRepository;
        }
        
        public boolean isDynamoDBRepository(Object repository) {
            return repository.getClass().getPackage().getName().contains("dynamodb");
        }
        
        public boolean isJpaRepository(Object repository) {
            return !isDynamoDBRepository(repository);
        }
        
        public String getRepositoryType() {
            if (isDynamoDBRepository(userRepository)) {
                return "DynamoDB";
            } else {
                return "JPA";
            }
        }
        
        public void logRepositoryConfiguration() {
            String repoType = getRepositoryType();
            log.info("Repository Configuration Summary:");
            log.info("  Repository Type: {}", repoType);
            log.info("  UserRepository: {}", userRepository.getClass().getSimpleName());
            log.info("  TravelPlanRepository: {}", travelPlanRepository.getClass().getSimpleName());
            log.info("  PlaceRepository: {}", placeRepository.getClass().getSimpleName());
            log.info("  SavedPlanRepository: {}", savedPlanRepository.getClass().getSimpleName());
            log.info("  ItineraryItemRepository: {}", itineraryItemRepository.getClass().getSimpleName());
        }
    }
    
    /**
     * Configuration properties for repository selection
     */
    public static class RepositoryProperties {
        
        public static final String DYNAMODB_PROFILE = "dynamodb";
        public static final String H2_PROFILE = "h2";
        public static final String POSTGRESQL_PROFILE = "postgresql";
        public static final String MYSQL_PROFILE = "mysql";
        
        /**
         * Get the recommended repository type based on use case
         */
        public static String getRecommendedRepositoryType(String useCase) {
            switch (useCase.toLowerCase()) {
                case "development":
                case "testing":
                    return H2_PROFILE;
                case "production":
                case "scaling":
                    return DYNAMODB_PROFILE;
                case "complex-queries":
                case "analytics":
                    return POSTGRESQL_PROFILE;
                default:
                    return H2_PROFILE;
            }
        }
        
        /**
         * Get repository capabilities for different implementations
         */
        public static String getRepositoryCapabilities(String repositoryType) {
            switch (repositoryType.toLowerCase()) {
                case DYNAMODB_PROFILE:
                    return "NoSQL, Scalable, Fast reads/writes, Limited complex queries, "
                         + "Global secondary indexes, Automatic scaling, Geographic distribution";
                case H2_PROFILE:
                    return "In-memory, Fast startup, Development friendly, "
                         + "Full SQL support, Not persistent, Testing optimized";
                case POSTGRESQL_PROFILE:
                    return "Relational, ACID compliant, Complex queries, "
                         + "Full-text search, JSON support, Mature ecosystem";
                case MYSQL_PROFILE:
                    return "Relational, High performance, Web applications, "
                         + "Replication support, Large community, Proven reliability";
                default:
                    return "Unknown repository type";
            }
        }
    }
    
    /**
     * Repository health check utilities
     */
    @Configuration
    public static class RepositoryHealthCheck {
        
        private final RepositorySelector repositorySelector;
        
        public RepositoryHealthCheck(RepositorySelector repositorySelector) {
            this.repositorySelector = repositorySelector;
        }
        
        public boolean checkRepositoryHealth() {
            try {
                // Basic health check - try to count entities
                long userCount = repositorySelector.getUserRepository().count();
                long planCount = repositorySelector.getTravelPlanRepository().count();
                long placeCount = repositorySelector.getPlaceRepository().count();
                long savedPlanCount = repositorySelector.getSavedPlanRepository().count();
                
                log.info("Repository Health Check - Counts: Users={}, Plans={}, Places={}, SavedPlans={}", 
                        userCount, planCount, placeCount, savedPlanCount);
                
                return true;
            } catch (Exception e) {
                log.error("Repository health check failed: {}", e.getMessage(), e);
                return false;
            }
        }
        
        public void logRepositoryStatus() {
            String repoType = repositorySelector.getRepositoryType();
            boolean isHealthy = checkRepositoryHealth();
            
            log.info("Repository Status:");
            log.info("  Type: {}", repoType);
            log.info("  Health: {}", isHealthy ? "HEALTHY" : "UNHEALTHY");
            log.info("  Capabilities: {}", 
                    RepositoryProperties.getRepositoryCapabilities(repoType));
        }
    }
}