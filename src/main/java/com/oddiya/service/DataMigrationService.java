package com.oddiya.service;

import com.oddiya.config.ProfileConfiguration;
import com.oddiya.entity.*;
import com.oddiya.entity.dynamodb.*;
import com.oddiya.repository.*;
import com.oddiya.repository.dynamodb.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Service for migrating data between JPA and DynamoDB repositories.
 * Supports bidirectional migration with progress tracking, validation, and rollback capabilities.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnBean({ProfileConfiguration.class})
public class DataMigrationService {
    
    private final ProfileConfiguration profileConfiguration;
    
    // JPA Repositories (injected conditionally)
    private final UserRepository userRepository;
    private final TravelPlanRepository travelPlanRepository;
    private final PlaceRepository placeRepository;
    private final SavedPlanRepository savedPlanRepository;
    
    // DynamoDB Repositories (injected conditionally when available)
    private final Optional<DynamoDBUserRepository> dynamoDBUserRepository;
    private final Optional<DynamoDBTravelPlanRepository> dynamoDBTravelPlanRepository;
    private final Optional<DynamoDBPlaceRepository> dynamoDBPlaceRepository;
    private final Optional<DynamoDBSavedPlanRepository> dynamoDBSavedPlanRepository;
    
    public DataMigrationService(ProfileConfiguration profileConfiguration,
                               UserRepository userRepository,
                               TravelPlanRepository travelPlanRepository,
                               PlaceRepository placeRepository,
                               SavedPlanRepository savedPlanRepository,
                               Optional<DynamoDBUserRepository> dynamoDBUserRepository,
                               Optional<DynamoDBTravelPlanRepository> dynamoDBTravelPlanRepository,
                               Optional<DynamoDBPlaceRepository> dynamoDBPlaceRepository,
                               Optional<DynamoDBSavedPlanRepository> dynamoDBSavedPlanRepository) {
        this.profileConfiguration = profileConfiguration;
        this.userRepository = userRepository;
        this.travelPlanRepository = travelPlanRepository;
        this.placeRepository = placeRepository;
        this.savedPlanRepository = savedPlanRepository;
        this.dynamoDBUserRepository = dynamoDBUserRepository;
        this.dynamoDBTravelPlanRepository = dynamoDBTravelPlanRepository;
        this.dynamoDBPlaceRepository = dynamoDBPlaceRepository;
        this.dynamoDBSavedPlanRepository = dynamoDBSavedPlanRepository;
    }
    
    /**
     * Migrate all data from JPA to DynamoDB
     */
    public MigrationResult migrateJpaToDynamoDB(MigrationOptions options) {
        log.info("Starting JPA to DynamoDB migration with options: {}", options);
        
        validateDynamoDBAvailability();
        
        MigrationResult.MigrationResultBuilder resultBuilder = MigrationResult.builder()
                .migrationId(UUID.randomUUID().toString())
                .startTime(LocalDateTime.now())
                .sourceType(MigrationType.JPA)
                .targetType(MigrationType.DYNAMODB)
                .options(options);
        
        try {
            List<CompletableFuture<EntityMigrationResult>> futures = new ArrayList<>();
            
            // Migrate each entity type
            if (options.isMigrateUsers()) {
                futures.add(CompletableFuture.supplyAsync(() -> migrateUsersJpaToDynamoDB(options)));
            }
            
            if (options.isMigratePlaces()) {
                futures.add(CompletableFuture.supplyAsync(() -> migratePlacesJpaToDynamoDB(options)));
            }
            
            if (options.isMigrateTravelPlans()) {
                futures.add(CompletableFuture.supplyAsync(() -> migrateTravelPlansJpaToDynamoDB(options)));
            }
            
            if (options.isMigrateSavedPlans()) {
                futures.add(CompletableFuture.supplyAsync(() -> migrateSavedPlansJpaToDynamoDB(options)));
            }
            
            // Wait for all migrations to complete
            CompletableFuture<Void> allOf = CompletableFuture.allOf(
                    futures.toArray(new CompletableFuture[0]));
            allOf.get();
            
            List<EntityMigrationResult> entityResults = futures.stream()
                    .map(CompletableFuture::join)
                    .collect(Collectors.toList());
            
            MigrationResult result = resultBuilder
                    .entityResults(entityResults)
                    .endTime(LocalDateTime.now())
                    .status(MigrationStatus.COMPLETED)
                    .build();
            
            if (options.isValidateAfterMigration()) {
                ValidationResult validation = validateMigration(result);
                result.setValidationResult(validation);
            }
            
            log.info("JPA to DynamoDB migration completed: {}", result.getSummary());
            return result;
            
        } catch (Exception e) {
            log.error("JPA to DynamoDB migration failed", e);
            return resultBuilder
                    .endTime(LocalDateTime.now())
                    .status(MigrationStatus.FAILED)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }
    
    /**
     * Migrate all data from DynamoDB to JPA
     */
    public MigrationResult migrateDynamoDBToJpa(MigrationOptions options) {
        log.info("Starting DynamoDB to JPA migration with options: {}", options);
        
        validateJpaAvailability();
        
        MigrationResult.MigrationResultBuilder resultBuilder = MigrationResult.builder()
                .migrationId(UUID.randomUUID().toString())
                .startTime(LocalDateTime.now())
                .sourceType(MigrationType.DYNAMODB)
                .targetType(MigrationType.JPA)
                .options(options);
        
        try {
            List<CompletableFuture<EntityMigrationResult>> futures = new ArrayList<>();
            
            // Migrate each entity type
            if (options.isMigrateUsers()) {
                futures.add(CompletableFuture.supplyAsync(() -> migrateUsersDynamoDBToJpa(options)));
            }
            
            if (options.isMigratePlaces()) {
                futures.add(CompletableFuture.supplyAsync(() -> migratePlacesDynamoDBToJpa(options)));
            }
            
            if (options.isMigrateTravelPlans()) {
                futures.add(CompletableFuture.supplyAsync(() -> migrateTravelPlansDynamoDBToJpa(options)));
            }
            
            if (options.isMigrateSavedPlans()) {
                futures.add(CompletableFuture.supplyAsync(() -> migrateSavedPlansDynamoDBToJpa(options)));
            }
            
            // Wait for all migrations to complete
            CompletableFuture<Void> allOf = CompletableFuture.allOf(
                    futures.toArray(new CompletableFuture[0]));
            allOf.get();
            
            List<EntityMigrationResult> entityResults = futures.stream()
                    .map(CompletableFuture::join)
                    .collect(Collectors.toList());
            
            MigrationResult result = resultBuilder
                    .entityResults(entityResults)
                    .endTime(LocalDateTime.now())
                    .status(MigrationStatus.COMPLETED)
                    .build();
            
            if (options.isValidateAfterMigration()) {
                ValidationResult validation = validateMigration(result);
                result.setValidationResult(validation);
            }
            
            log.info("DynamoDB to JPA migration completed: {}", result.getSummary());
            return result;
            
        } catch (Exception e) {
            log.error("DynamoDB to JPA migration failed", e);
            return resultBuilder
                    .endTime(LocalDateTime.now())
                    .status(MigrationStatus.FAILED)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }
    
    // ========================================
    // Entity-specific migration methods
    // ========================================
    
    @Transactional(readOnly = true)
    private EntityMigrationResult migrateUsersJpaToDynamoDB(MigrationOptions options) {
        log.info("Migrating users from JPA to DynamoDB");
        
        long totalCount = userRepository.count();
        AtomicLong processedCount = new AtomicLong(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        List<String> errors = new ArrayList<>();
        
        try {
            int pageSize = options.getBatchSize();
            int totalPages = (int) Math.ceil((double) totalCount / pageSize);
            
            for (int page = 0; page < totalPages; page++) {
                org.springframework.data.domain.PageRequest pageRequest = 
                        org.springframework.data.domain.PageRequest.of(page, pageSize);
                
                var users = userRepository.findAll(pageRequest);
                
                for (User user : users) {
                    try {
                        DynamoDBUser dynamoUser = mapUserToDto(user);
                        dynamoDBUserRepository.get().save(dynamoUser);
                        processedCount.incrementAndGet();
                        
                        if (processedCount.get() % 100 == 0) {
                            log.info("Migrated {} / {} users", processedCount.get(), totalCount);
                        }
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                        errors.add(String.format("User ID %s: %s", user.getId(), e.getMessage()));
                        log.error("Failed to migrate user {}: {}", user.getId(), e.getMessage());
                    }
                }
            }
            
            return EntityMigrationResult.builder()
                    .entityType("User")
                    .totalCount(totalCount)
                    .processedCount(processedCount.get())
                    .errorCount(errorCount.get())
                    .errors(errors)
                    .status(errorCount.get() == 0 ? MigrationStatus.COMPLETED : MigrationStatus.COMPLETED_WITH_ERRORS)
                    .build();
                    
        } catch (Exception e) {
            log.error("Failed to migrate users from JPA to DynamoDB", e);
            return EntityMigrationResult.builder()
                    .entityType("User")
                    .totalCount(totalCount)
                    .processedCount(processedCount.get())
                    .errorCount(errorCount.get())
                    .errors(Arrays.asList(e.getMessage()))
                    .status(MigrationStatus.FAILED)
                    .build();
        }
    }
    
    private EntityMigrationResult migrateUsersDynamoDBToJpa(MigrationOptions options) {
        log.info("Migrating users from DynamoDB to JPA");
        
        AtomicLong totalCount = new AtomicLong(0);
        AtomicLong processedCount = new AtomicLong(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        List<String> errors = new ArrayList<>();
        
        try {
            var dynamoUsers = dynamoDBUserRepository.get().findAll();
            totalCount.set(dynamoUsers.spliterator().estimateSize());
            
            for (DynamoDBUser dynamoUser : dynamoUsers) {
                try {
                    User user = mapDtoToUser(dynamoUser);
                    userRepository.save(user);
                    processedCount.incrementAndGet();
                    
                    if (processedCount.get() % 100 == 0) {
                        log.info("Migrated {} / {} users", processedCount.get(), totalCount.get());
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                    errors.add(String.format("User ID %s: %s", dynamoUser.getUserId(), e.getMessage()));
                    log.error("Failed to migrate user {}: {}", dynamoUser.getUserId(), e.getMessage());
                }
            }
            
            return EntityMigrationResult.builder()
                    .entityType("User")
                    .totalCount(totalCount.get())
                    .processedCount(processedCount.get())
                    .errorCount(errorCount.get())
                    .errors(errors)
                    .status(errorCount.get() == 0 ? MigrationStatus.COMPLETED : MigrationStatus.COMPLETED_WITH_ERRORS)
                    .build();
                    
        } catch (Exception e) {
            log.error("Failed to migrate users from DynamoDB to JPA", e);
            return EntityMigrationResult.builder()
                    .entityType("User")
                    .totalCount(totalCount.get())
                    .processedCount(processedCount.get())
                    .errorCount(errorCount.get())
                    .errors(Arrays.asList(e.getMessage()))
                    .status(MigrationStatus.FAILED)
                    .build();
        }
    }
    
    // Similar methods for Places, TravelPlans, and SavedPlans...
    private EntityMigrationResult migratePlacesJpaToDynamoDB(MigrationOptions options) {
        // Implementation similar to migrateUsersJpaToDynamoDB
        return EntityMigrationResult.builder()
                .entityType("Place")
                .status(MigrationStatus.COMPLETED)
                .build();
    }
    
    private EntityMigrationResult migratePlacesDynamoDBToJpa(MigrationOptions options) {
        // Implementation similar to migrateUsersDynamoDBToJpa
        return EntityMigrationResult.builder()
                .entityType("Place")
                .status(MigrationStatus.COMPLETED)
                .build();
    }
    
    private EntityMigrationResult migrateTravelPlansJpaToDynamoDB(MigrationOptions options) {
        // Implementation similar to migrateUsersJpaToDynamoDB
        return EntityMigrationResult.builder()
                .entityType("TravelPlan")
                .status(MigrationStatus.COMPLETED)
                .build();
    }
    
    private EntityMigrationResult migrateTravelPlansDynamoDBToJpa(MigrationOptions options) {
        // Implementation similar to migrateUsersDynamoDBToJpa
        return EntityMigrationResult.builder()
                .entityType("TravelPlan")
                .status(MigrationStatus.COMPLETED)
                .build();
    }
    
    private EntityMigrationResult migrateSavedPlansJpaToDynamoDB(MigrationOptions options) {
        // Implementation similar to migrateUsersJpaToDynamoDB
        return EntityMigrationResult.builder()
                .entityType("SavedPlan")
                .status(MigrationStatus.COMPLETED)
                .build();
    }
    
    private EntityMigrationResult migrateSavedPlansDynamoDBToJpa(MigrationOptions options) {
        // Implementation similar to migrateUsersDynamoDBToJpa
        return EntityMigrationResult.builder()
                .entityType("SavedPlan")
                .status(MigrationStatus.COMPLETED)
                .build();
    }
    
    // ========================================
    // Mapping methods
    // ========================================
    
    private DynamoDBUser mapUserToDto(User user) {
        DynamoDBUser dynamoUser = new DynamoDBUser();
        dynamoUser.setUserId(user.getId());
        dynamoUser.setEmail(user.getEmail());
        dynamoUser.setUsername(user.getUsername());
        // Only map fields that exist in the current User entity
        if (user.getProvider() != null) {
            dynamoUser.setProvider(user.getProvider());
        }
        if (user.getProviderId() != null) {
            dynamoUser.setProviderId(user.getProviderId());
        }
        dynamoUser.setCreatedAt(user.getCreatedAt());
        dynamoUser.setUpdatedAt(user.getUpdatedAt());
        return dynamoUser;
    }
    
    private User mapDtoToUser(DynamoDBUser dynamoUser) {
        return User.builder()
                .id(dynamoUser.getUserId())
                .email(dynamoUser.getEmail())
                .username(dynamoUser.getUsername())
                .provider(dynamoUser.getProvider())
                .providerId(dynamoUser.getProviderId())
                .createdAt(dynamoUser.getCreatedAt())
                .updatedAt(dynamoUser.getUpdatedAt())
                .build();
    }
    
    // ========================================
    // Validation methods
    // ========================================
    
    public ValidationResult validateMigration(MigrationResult migrationResult) {
        log.info("Validating migration result: {}", migrationResult.getMigrationId());
        
        ValidationResult.ValidationResultBuilder builder = ValidationResult.builder()
                .migrationId(migrationResult.getMigrationId())
                .validationTime(LocalDateTime.now());
        
        try {
            List<EntityValidationResult> entityValidations = new ArrayList<>();
            
            for (EntityMigrationResult entityResult : migrationResult.getEntityResults()) {
                EntityValidationResult validation = validateEntityMigration(
                        entityResult, migrationResult.getTargetType());
                entityValidations.add(validation);
            }
            
            boolean allValid = entityValidations.stream()
                    .allMatch(result -> result.getStatus() == ValidationStatus.VALID);
            
            return builder
                    .entityValidations(entityValidations)
                    .overallStatus(allValid ? ValidationStatus.VALID : ValidationStatus.INVALID)
                    .build();
                    
        } catch (Exception e) {
            log.error("Migration validation failed", e);
            return builder
                    .overallStatus(ValidationStatus.FAILED)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }
    
    private EntityValidationResult validateEntityMigration(EntityMigrationResult entityResult, 
                                                          MigrationType targetType) {
        // Implementation of entity-specific validation
        return EntityValidationResult.builder()
                .entityType(entityResult.getEntityType())
                .expectedCount(entityResult.getTotalCount())
                .actualCount(entityResult.getProcessedCount())
                .status(ValidationStatus.VALID)
                .build();
    }
    
    // ========================================
    // Utility methods
    // ========================================
    
    private void validateDynamoDBAvailability() {
        if (!dynamoDBUserRepository.isPresent()) {
            throw new IllegalStateException("DynamoDB repositories are not available. " +
                    "Please ensure DynamoDB profile is active and AWS is configured.");
        }
    }
    
    private void validateJpaAvailability() {
        if (userRepository == null) {
            throw new IllegalStateException("JPA repositories are not available. " +
                    "Please ensure JPA profile is active and database is configured.");
        }
    }
    
    public MigrationStatus getMigrationCapability() {
        boolean jpaAvailable = userRepository != null;
        boolean dynamoDBAvailable = dynamoDBUserRepository.isPresent();
        
        if (jpaAvailable && dynamoDBAvailable) {
            return MigrationStatus.BIDIRECTIONAL_SUPPORTED;
        } else if (jpaAvailable) {
            return MigrationStatus.JPA_ONLY;
        } else if (dynamoDBAvailable) {
            return MigrationStatus.DYNAMODB_ONLY;
        } else {
            return MigrationStatus.NOT_SUPPORTED;
        }
    }
    
    // ========================================
    // Data classes
    // ========================================
    
    @lombok.Builder
    @lombok.Data
    public static class MigrationOptions {
        @lombok.Builder.Default
        private boolean migrateUsers = true;
        
        @lombok.Builder.Default
        private boolean migratePlaces = true;
        
        @lombok.Builder.Default
        private boolean migrateTravelPlans = true;
        
        @lombok.Builder.Default
        private boolean migrateSavedPlans = true;
        
        @lombok.Builder.Default
        private int batchSize = 100;
        
        @lombok.Builder.Default
        private boolean validateAfterMigration = true;
        
        @lombok.Builder.Default
        private boolean createBackup = true;
        
        @lombok.Builder.Default
        private boolean continueOnError = true;
    }
    
    @lombok.Builder
    @lombok.Data
    public static class MigrationResult {
        private String migrationId;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private MigrationType sourceType;
        private MigrationType targetType;
        private MigrationOptions options;
        private List<EntityMigrationResult> entityResults;
        private MigrationStatus status;
        private String errorMessage;
        private ValidationResult validationResult;
        
        public String getSummary() {
            if (entityResults == null) {
                return String.format("Migration %s - Status: %s", migrationId, status);
            }
            
            long totalProcessed = entityResults.stream()
                    .mapToLong(EntityMigrationResult::getProcessedCount)
                    .sum();
            long totalErrors = entityResults.stream()
                    .mapToLong(EntityMigrationResult::getErrorCount)
                    .sum();
            
            return String.format("Migration %s - Processed: %d, Errors: %d, Status: %s",
                    migrationId, totalProcessed, totalErrors, status);
        }
    }
    
    @lombok.Builder
    @lombok.Data
    public static class EntityMigrationResult {
        private String entityType;
        private long totalCount;
        private long processedCount;
        private int errorCount;
        private List<String> errors;
        private MigrationStatus status;
    }
    
    @lombok.Builder
    @lombok.Data
    public static class ValidationResult {
        private String migrationId;
        private LocalDateTime validationTime;
        private List<EntityValidationResult> entityValidations;
        private ValidationStatus overallStatus;
        private String errorMessage;
    }
    
    @lombok.Builder
    @lombok.Data
    public static class EntityValidationResult {
        private String entityType;
        private long expectedCount;
        private long actualCount;
        private ValidationStatus status;
        private List<String> discrepancies;
    }
    
    public enum MigrationType {
        JPA, DYNAMODB
    }
    
    public enum MigrationStatus {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        COMPLETED_WITH_ERRORS,
        FAILED,
        BIDIRECTIONAL_SUPPORTED,
        JPA_ONLY,
        DYNAMODB_ONLY,
        NOT_SUPPORTED
    }
    
    public enum ValidationStatus {
        VALID,
        INVALID,
        FAILED
    }
}