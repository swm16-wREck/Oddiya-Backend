package com.oddiya.repository.dynamodb;

import com.oddiya.entity.SavedPlan;
import com.oddiya.entity.dynamodb.DynamoDBSavedPlan;
import com.oddiya.repository.SavedPlanRepository;
import com.oddiya.converter.DynamoDBConverters;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.*;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * DynamoDB implementation of SavedPlanRepository interface.
 * Handles composite key operations (userId + planId) with batch operations support.
 */
@Repository
@Slf4j
@ConditionalOnProperty(name = "spring.profiles.active", havingValue = "dynamodb")
public class DynamoDBSavedPlanRepository extends AbstractDynamoDBRepository<DynamoDBSavedPlan, String> 
                                        implements SavedPlanRepository {
    
    private final DynamoDBConverters converters;
    
    public DynamoDBSavedPlanRepository(DynamoDbEnhancedClient enhancedClient,
                                      DynamoDbClient client,
                                      DynamoDBConverters converters,
                                      String tableName) {
        super(enhancedClient, client, DynamoDBSavedPlan.class, tableName != null ? tableName : "oddiya_saved_plans");
        this.converters = converters;
    }
    
    @Override
    protected Key buildKey(String id) {
        return Key.builder().partitionValue(id).build();
    }
    
    // JPA Repository methods implementation
    
    @Override
    public <S extends SavedPlan> S save(S entity) {
        try {
            DynamoDBSavedPlan dynamoEntity = converters.toSavedPlanDynamoDB(entity);
            DynamoDBSavedPlan saved = save(dynamoEntity);
            return (S) converters.toSavedPlanJPA(saved);
        } catch (Exception e) {
            log.error("Error saving saved plan {}: {}", entity.getId(), e.getMessage(), e);
            throw new DynamoDBOperationException("Failed to save saved plan", e);
        }
    }
    
    @Override
    public <S extends SavedPlan> List<S> saveAll(Iterable<S> entities) {
        try {
            List<DynamoDBSavedPlan> dynamoEntities = 
                ((List<S>) entities).stream()
                                   .map(converters::toSavedPlanDynamoDB)
                                   .collect(Collectors.toList());
            
            List<DynamoDBSavedPlan> saved = saveAll(dynamoEntities);
            
            return (List<S>) saved.stream()
                                  .map(converters::toSavedPlanJPA)
                                  .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error batch saving saved plans: {}", e.getMessage(), e);
            throw new DynamoDBOperationException("Failed to batch save saved plans", e);
        }
    }
    
    @Override
    public Optional<SavedPlan> findById(String id) {
        try {
            Optional<DynamoDBSavedPlan> result = super.findById(id);
            return result.map(converters::toSavedPlanJPA);
        } catch (Exception e) {
            log.error("Error finding saved plan by id {}: {}", id, e.getMessage(), e);
            throw new DynamoDBOperationException("Failed to find saved plan by id", e);
        }
    }
    
    @Override
    public boolean existsById(String id) {
        return findById(id).isPresent();
    }
    
    @Override
    public List<SavedPlan> findAll() {
        try {
            return scanAll().stream()
                           .flatMap(page -> page.items().stream())
                           .map(converters::toSavedPlanJPA)
                           .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error finding all saved plans: {}", e.getMessage(), e);
            throw new DynamoDBOperationException("Failed to find all saved plans", e);
        }
    }
    
    @Override
    public List<SavedPlan> findAllById(Iterable<String> ids) {
        try {
            List<DynamoDBSavedPlan> results = super.findAllById((List<String>) ids);
            return results.stream()
                         .map(converters::toSavedPlanJPA)
                         .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error finding saved plans by ids: {}", e.getMessage(), e);
            throw new DynamoDBOperationException("Failed to find saved plans by ids", e);
        }
    }
    
    @Override
    public void deleteById(String id) {
        try {
            super.deleteById(id);
        } catch (Exception e) {
            log.error("Error deleting saved plan by id {}: {}", id, e.getMessage(), e);
            throw new DynamoDBOperationException("Failed to delete saved plan by id", e);
        }
    }
    
    @Override
    public void delete(SavedPlan entity) {
        deleteById(entity.getId());
    }
    
    @Override
    public void deleteAllById(Iterable<? extends String> ids) {
        try {
            List<DynamoDBSavedPlan> toDelete = super.findAllById((List<String>) ids);
            deleteAll(toDelete);
        } catch (Exception e) {
            log.error("Error batch deleting saved plans by ids: {}", e.getMessage(), e);
            throw new DynamoDBOperationException("Failed to batch delete saved plans by ids", e);
        }
    }
    
    @Override
    public void deleteAll(Iterable<? extends SavedPlan> entities) {
        try {
            List<DynamoDBSavedPlan> dynamoEntities = 
                ((List<SavedPlan>) entities).stream()
                                           .map(converters::toSavedPlanDynamoDB)
                                           .collect(Collectors.toList());
            
            super.deleteAll(dynamoEntities);
        } catch (Exception e) {
            log.error("Error batch deleting saved plans: {}", e.getMessage(), e);
            throw new DynamoDBOperationException("Failed to batch delete saved plans", e);
        }
    }
    
    @Override
    public void deleteAll() {
        try {
            List<SavedPlan> allSavedPlans = findAll();
            deleteAll(allSavedPlans);
        } catch (Exception e) {
            log.error("Error deleting all saved plans: {}", e.getMessage(), e);
            throw new DynamoDBOperationException("Failed to delete all saved plans", e);
        }
    }
    
    // Custom repository methods implementation
    
    @Override
    public boolean existsByUserIdAndTravelPlanId(String userId, String travelPlanId) {
        return findByUserIdAndTravelPlanId(userId, travelPlanId).isPresent();
    }
    
    @Override
    public Optional<SavedPlan> findByUserIdAndTravelPlanId(String userId, String travelPlanId) {
        try {
            Expression filterExpression = Expression.builder()
                .expression("#userId = :userId AND #travelPlanId = :travelPlanId")
                .putExpressionName("#userId", "userId")
                .putExpressionName("#travelPlanId", "travelPlanId")
                .putExpressionValue(":userId", AttributeValue.fromS(userId))
                .putExpressionValue(":travelPlanId", AttributeValue.fromS(travelPlanId))
                .build();
            
            PageIterable<DynamoDBSavedPlan> results = scanWithFilter(filterExpression);
            
            Optional<DynamoDBSavedPlan> firstResult = results.stream()
                                                            .flatMap(page -> page.items().stream())
                                                            .findFirst();
            
            return firstResult.map(converters::toSavedPlanJPA);
            
        } catch (Exception e) {
            log.error("Error finding saved plan by userId {} and travelPlanId {}: {}", 
                     userId, travelPlanId, e.getMessage(), e);
            throw new DynamoDBOperationException("Failed to find saved plan by userId and travelPlanId", e);
        }
    }
    
    @Override
    public Page<SavedPlan> findByUserId(String userId, Pageable pageable) {
        try {
            QueryConditional queryConditional = QueryConditional.keyEqualTo(
                Key.builder().partitionValue(userId).build());
            
            PageIterable<DynamoDBSavedPlan> results = queryIndex("userId-index", queryConditional);
            
            List<SavedPlan> savedPlans = results.stream()
                                               .flatMap(page -> page.items().stream())
                                               .map(converters::toSavedPlanJPA)
                                               .collect(Collectors.toList());
            
            return createPage(savedPlans, pageable, savedPlans.size());
            
        } catch (Exception e) {
            log.error("Error finding saved plans by userId {}: {}", userId, e.getMessage(), e);
            throw new DynamoDBOperationException("Failed to find saved plans by userId", e);
        }
    }
    
    @Override
    public void deleteByUserIdAndTravelPlanId(String userId, String travelPlanId) {
        try {
            Optional<SavedPlan> savedPlanOpt = findByUserIdAndTravelPlanId(userId, travelPlanId);
            
            if (savedPlanOpt.isPresent()) {
                SavedPlan savedPlan = savedPlanOpt.get();
                deleteById(savedPlan.getId());
                log.info("Deleted saved plan for userId {} and travelPlanId {}", userId, travelPlanId);
            } else {
                log.warn("No saved plan found for userId {} and travelPlanId {}", userId, travelPlanId);
            }
            
        } catch (Exception e) {
            log.error("Error deleting saved plan by userId {} and travelPlanId {}: {}", 
                     userId, travelPlanId, e.getMessage(), e);
            throw new DynamoDBOperationException("Failed to delete saved plan by userId and travelPlanId", e);
        }
    }
    
    // Batch operations for performance optimization
    
    /**
     * Batch save multiple saved plans for a user
     */
    public List<SavedPlan> batchSaveForUser(String userId, List<String> travelPlanIds) {
        try {
            return executeWithRetry(() -> {
                List<SavedPlan> savedPlans = travelPlanIds.stream()
                    .map(planId -> {
                        SavedPlan savedPlan = SavedPlan.builder()
                                .savedAt(java.time.LocalDateTime.now())
                                .build();
                        // Note: User and TravelPlan objects would need to be set separately
                        // This method assumes the caller has the necessary entity references
                        return savedPlan;
                    })
                    .collect(Collectors.toList());
                
                return (List<SavedPlan>) saveAll(savedPlans);
            });
        } catch (Exception e) {
            log.error("Error batch saving {} plans for user {}: {}", 
                     travelPlanIds.size(), userId, e.getMessage(), e);
            throw new DynamoDBOperationException("Failed to batch save saved plans for user", e);
        }
    }
    
    /**
     * Batch delete multiple saved plans for a user
     */
    public void batchDeleteForUser(String userId, List<String> travelPlanIds) {
        try {
            executeWithRetry(() -> {
                List<SavedPlan> toDelete = travelPlanIds.stream()
                    .map(planId -> findByUserIdAndTravelPlanId(userId, planId))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .collect(Collectors.toList());
                
                if (!toDelete.isEmpty()) {
                    deleteAll(toDelete);
                    log.info("Batch deleted {} saved plans for user {}", toDelete.size(), userId);
                }
                
                return null;
            });
        } catch (Exception e) {
            log.error("Error batch deleting {} plans for user {}: {}", 
                     travelPlanIds.size(), userId, e.getMessage(), e);
            throw new DynamoDBOperationException("Failed to batch delete saved plans for user", e);
        }
    }
    
    /**
     * Get saved plan count for a user
     */
    public long countByUserId(String userId) {
        try {
            QueryConditional queryConditional = QueryConditional.keyEqualTo(
                Key.builder().partitionValue(userId).build());
            
            PageIterable<DynamoDBSavedPlan> results = queryIndex("userId-index", queryConditional);
            
            return results.stream()
                         .mapToLong(page -> page.items().size())
                         .sum();
            
        } catch (Exception e) {
            log.error("Error counting saved plans for userId {}: {}", userId, e.getMessage(), e);
            throw new DynamoDBOperationException("Failed to count saved plans for user", e);
        }
    }
    
    /**
     * Check if multiple plans are saved by a user
     */
    public List<Boolean> checkMultiplePlansExist(String userId, List<String> travelPlanIds) {
        try {
            return executeWithRetry(() -> {
                return travelPlanIds.stream()
                    .map(planId -> existsByUserIdAndTravelPlanId(userId, planId))
                    .collect(Collectors.toList());
            });
        } catch (Exception e) {
            log.error("Error checking existence of multiple plans for user {}: {}", userId, e.getMessage(), e);
            throw new DynamoDBOperationException("Failed to check multiple plans existence", e);
        }
    }
    
    /**
     * Get all saved travel plan IDs for a user
     */
    public List<String> getSavedPlanIds(String userId) {
        try {
            QueryConditional queryConditional = QueryConditional.keyEqualTo(
                Key.builder().partitionValue(userId).build());
            
            PageIterable<DynamoDBSavedPlan> results = queryIndex("userId-index", queryConditional);
            
            return results.stream()
                         .flatMap(page -> page.items().stream())
                         .map(DynamoDBSavedPlan::getTravelPlanId)
                         .collect(Collectors.toList());
            
        } catch (Exception e) {
            log.error("Error getting saved plan IDs for userId {}: {}", userId, e.getMessage(), e);
            throw new DynamoDBOperationException("Failed to get saved plan IDs for user", e);
        }
    }
    
    /**
     * Remove all saved plans for a user (when user is deleted)
     */
    public void deleteAllByUserId(String userId) {
        try {
            executeWithRetry(() -> {
                Page<SavedPlan> userSavedPlans = findByUserId(userId, Pageable.unpaged());
                List<SavedPlan> allPlans = userSavedPlans.getContent();
                
                if (!allPlans.isEmpty()) {
                    deleteAll(allPlans);
                    log.info("Deleted all {} saved plans for user {}", allPlans.size(), userId);
                }
                
                return null;
            });
        } catch (Exception e) {
            log.error("Error deleting all saved plans for userId {}: {}", userId, e.getMessage(), e);
            throw new DynamoDBOperationException("Failed to delete all saved plans for user", e);
        }
    }
    
    /**
     * Remove all instances of a travel plan from saved plans (when travel plan is deleted)
     */
    public void deleteAllByTravelPlanId(String travelPlanId) {
        try {
            executeWithRetry(() -> {
                Expression filterExpression = Expression.builder()
                    .expression("#travelPlanId = :travelPlanId")
                    .putExpressionName("#travelPlanId", "travelPlanId")
                    .putExpressionValue(":travelPlanId", AttributeValue.fromS(travelPlanId))
                    .build();
                
                PageIterable<DynamoDBSavedPlan> results = scanWithFilter(filterExpression);
                
                List<SavedPlan> toDelete = results.stream()
                                                 .flatMap(page -> page.items().stream())
                                                 .map(converters::toSavedPlanJPA)
                                                 .collect(Collectors.toList());
                
                if (!toDelete.isEmpty()) {
                    deleteAll(toDelete);
                    log.info("Deleted {} saved plan references for travel plan {}", toDelete.size(), travelPlanId);
                }
                
                return null;
            });
        } catch (Exception e) {
            log.error("Error deleting saved plans for travelPlanId {}: {}", travelPlanId, e.getMessage(), e);
            throw new DynamoDBOperationException("Failed to delete saved plans for travel plan", e);
        }
    }
}