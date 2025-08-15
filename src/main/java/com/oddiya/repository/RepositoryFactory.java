package com.oddiya.repository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

/**
 * Repository factory to manage repository selection.
 * Currently only supports JPA repositories.
 */
@Configuration
@Slf4j
public class RepositoryFactory {
    
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
        
        public String getRepositoryType() {
            return "JPA";
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
        
        public static final String POSTGRESQL_PROFILE = "postgresql";
        public static final String MYSQL_PROFILE = "mysql";
        
        /**
         * Get the recommended repository type based on use case
         */
        public static String getRecommendedRepositoryType(String useCase) {
            switch (useCase.toLowerCase()) {
                case "development":
                case "testing":
                case "production":
                case "complex-queries":
                case "analytics":
                default:
                    return POSTGRESQL_PROFILE;
            }
        }
        
        /**
         * Get repository capabilities for different implementations
         */
        public static String getRepositoryCapabilities(String repositoryType) {
            switch (repositoryType.toLowerCase()) {
                case POSTGRESQL_PROFILE:
                    return "Relational, ACID compliant, Complex queries, "
                         + "Full-text search, JSON support, Spatial support, Mature ecosystem";
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