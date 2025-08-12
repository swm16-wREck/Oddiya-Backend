package com.oddiya.repository.dynamodb;

import com.oddiya.entity.TravelPlan;
import com.oddiya.entity.TravelPlanStatus;
import com.oddiya.entity.dynamodb.DynamoDBTravelPlan;
import com.oddiya.repository.TravelPlanRepository;
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

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * DynamoDB implementation of TravelPlanRepository interface.
 * Provides travel plan database operations using DynamoDB with GSI support.
 */
@Repository
@Slf4j
@ConditionalOnProperty(name = "spring.profiles.active", havingValue = "dynamodb")
public class DynamoDBTravelPlanRepository extends AbstractDynamoDBRepository<DynamoDBTravelPlan, String> 
                                          implements TravelPlanRepository {
    
    private final DynamoDBConverters converters;
    
    public DynamoDBTravelPlanRepository(DynamoDbEnhancedClient enhancedClient,
                                       DynamoDbClient client,
                                       DynamoDBConverters converters,
                                       String tableName) {
        super(enhancedClient, client, DynamoDBTravelPlan.class, tableName != null ? tableName : "oddiya_travel_plans");
        this.converters = converters;
    }
    
    @Override
    protected Key buildKey(String id) {
        return Key.builder().partitionValue(id).build();
    }
    
    // JPA Repository methods implementation
    
    @Override
    public <S extends TravelPlan> S save(S entity) {
        try {
            DynamoDBTravelPlan dynamoEntity = converters.toTravelPlanDynamoDB(entity);
            DynamoDBTravelPlan saved = save(dynamoEntity);
            return (S) converters.toTravelPlanJPA(saved);
        } catch (Exception e) {
            log.error("Error saving travel plan {}: {}", entity.getId(), e.getMessage(), e);
            throw new DynamoDBOperationException("Failed to save travel plan", e);
        }
    }
    
    @Override
    public <S extends TravelPlan> List<S> saveAll(Iterable<S> entities) {
        try {
            List<DynamoDBTravelPlan> dynamoEntities = 
                ((List<S>) entities).stream()
                                   .map(converters::toTravelPlanDynamoDB)
                                   .collect(Collectors.toList());
            
            List<DynamoDBTravelPlan> saved = saveAll(dynamoEntities);
            
            return (List<S>) saved.stream()
                                  .map(converters::toTravelPlanJPA)
                                  .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error batch saving travel plans: {}", e.getMessage(), e);
            throw new DynamoDBOperationException("Failed to batch save travel plans", e);
        }
    }
    
    @Override
    public Optional<TravelPlan> findById(String id) {
        try {
            Optional<DynamoDBTravelPlan> result = super.findById(id);
            return result.map(converters::toTravelPlanJPA);
        } catch (Exception e) {
            log.error("Error finding travel plan by id {}: {}", id, e.getMessage(), e);
            throw new DynamoDBOperationException("Failed to find travel plan by id", e);
        }
    }
    
    @Override
    public boolean existsById(String id) {
        return findById(id).isPresent();
    }
    
    @Override
    public List<TravelPlan> findAll() {
        try {
            return scanAll().stream()
                           .flatMap(page -> page.items().stream())
                           .filter(plan -> !Boolean.TRUE.equals(plan.getIsDeleted()))
                           .map(converters::toTravelPlanJPA)
                           .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error finding all travel plans: {}", e.getMessage(), e);
            throw new DynamoDBOperationException("Failed to find all travel plans", e);
        }
    }
    
    @Override
    public List<TravelPlan> findAllById(Iterable<String> ids) {
        try {
            List<DynamoDBTravelPlan> results = super.findAllById((List<String>) ids);
            return results.stream()
                         .filter(plan -> !Boolean.TRUE.equals(plan.getIsDeleted()))
                         .map(converters::toTravelPlanJPA)
                         .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error finding travel plans by ids: {}", e.getMessage(), e);
            throw new DynamoDBOperationException("Failed to find travel plans by ids", e);
        }
    }
    
    @Override
    public void deleteById(String id) {
        try {
            // Soft delete - mark as deleted instead of physical deletion
            Optional<DynamoDBTravelPlan> planOpt = super.findById(id);
            if (planOpt.isPresent()) {
                DynamoDBTravelPlan plan = planOpt.get();
                plan.setIsDeleted(true);
                plan.setDeletedAt(java.time.Instant.now());
                save(plan);
            }
        } catch (Exception e) {
            log.error("Error deleting travel plan by id {}: {}", id, e.getMessage(), e);
            throw new DynamoDBOperationException("Failed to delete travel plan by id", e);
        }
    }
    
    @Override
    public void delete(TravelPlan entity) {
        deleteById(entity.getId());
    }
    
    @Override
    public void deleteAllById(Iterable<? extends String> ids) {
        try {
            List<DynamoDBTravelPlan> toDelete = super.findAllById((List<String>) ids);
            toDelete.forEach(plan -> {
                plan.setIsDeleted(true);
                plan.setDeletedAt(java.time.Instant.now());
            });
            saveAll(toDelete);
        } catch (Exception e) {
            log.error("Error batch deleting travel plans by ids: {}", e.getMessage(), e);
            throw new DynamoDBOperationException("Failed to batch delete travel plans by ids", e);
        }
    }
    
    @Override
    public void deleteAll(Iterable<? extends TravelPlan> entities) {
        try {
            List<DynamoDBTravelPlan> dynamoEntities = 
                ((List<TravelPlan>) entities).stream()
                                            .map(converters::toTravelPlanDynamoDB)
                                            .collect(Collectors.toList());
            
            dynamoEntities.forEach(plan -> {
                plan.setIsDeleted(true);
                plan.setDeletedAt(java.time.Instant.now());
            });
            
            saveAll(dynamoEntities);
        } catch (Exception e) {
            log.error("Error batch deleting travel plans: {}", e.getMessage(), e);
            throw new DynamoDBOperationException("Failed to batch delete travel plans", e);
        }
    }
    
    @Override
    public void deleteAll() {
        try {
            List<TravelPlan> allPlans = findAll();
            deleteAll(allPlans);
        } catch (Exception e) {
            log.error("Error deleting all travel plans: {}", e.getMessage(), e);
            throw new DynamoDBOperationException("Failed to delete all travel plans", e);
        }
    }
    
    // Custom repository methods implementation
    
    @Override
    public Page<TravelPlan> findByUserIdAndIsDeletedFalse(String userId, Pageable pageable) {
        return findByUserId(userId, pageable);
    }
    
    @Override
    public Page<TravelPlan> findByUserIdAndStatusAndIsDeletedFalse(String userId, 
                                                                  TravelPlanStatus status, 
                                                                  Pageable pageable) {
        try {
            QueryConditional queryConditional = QueryConditional.keyEqualTo(
                Key.builder().partitionValue(userId).build());
            
            Expression filterExpression = Expression.builder()
                .expression("#status = :status AND (attribute_not_exists(#deleted) OR #deleted = :false)")
                .putExpressionName("#status", "status")
                .putExpressionName("#deleted", "isDeleted")
                .putExpressionValue(":status", AttributeValue.fromS(status.toString()))
                .putExpressionValue(":false", AttributeValue.fromBool(false))
                .build();
            
            PageIterable<DynamoDBTravelPlan> results = queryIndexWithFilter("userId-index", 
                                                                           queryConditional, 
                                                                           filterExpression);
            
            List<TravelPlan> plans = results.stream()
                                           .flatMap(page -> page.items().stream())
                                           .map(converters::toTravelPlanJPA)
                                           .collect(Collectors.toList());
            
            return createPage(plans, pageable, plans.size());
            
        } catch (Exception e) {
            log.error("Error finding travel plans by userId {} and status {}: {}", 
                     userId, status, e.getMessage(), e);
            throw new DynamoDBOperationException("Failed to find travel plans by user and status", e);
        }
    }
    
    @Override
    public Page<TravelPlan> searchPublicPlans(String query, Pageable pageable) {
        try {
            String lowercaseQuery = query.toLowerCase();
            
            Expression filterExpression = Expression.builder()
                .expression("(contains(#title, :query) OR contains(#destination, :query) OR contains(#description, :query)) " +
                           "AND #isPublic = :true AND (attribute_not_exists(#deleted) OR #deleted = :false)")
                .putExpressionName("#title", "title")
                .putExpressionName("#destination", "destination")
                .putExpressionName("#description", "description")
                .putExpressionName("#isPublic", "isPublic")
                .putExpressionName("#deleted", "isDeleted")
                .putExpressionValue(":query", AttributeValue.fromS(lowercaseQuery))
                .putExpressionValue(":true", AttributeValue.fromBool(true))
                .putExpressionValue(":false", AttributeValue.fromBool(false))
                .build();
            
            PageIterable<DynamoDBTravelPlan> results = scanWithFilter(filterExpression);
            
            List<TravelPlan> plans = results.stream()
                                           .flatMap(page -> page.items().stream())
                                           .map(converters::toTravelPlanJPA)
                                           .collect(Collectors.toList());
            
            return createPage(plans, pageable, plans.size());
            
        } catch (Exception e) {
            log.error("Error searching public plans with query {}: {}", query, e.getMessage(), e);
            throw new DynamoDBOperationException("Failed to search public plans", e);
        }
    }
    
    @Override
    public List<TravelPlan> findSimilarPlans(String destination, LocalDate startDate, LocalDate endDate) {
        try {
            long startEpoch = startDate.atStartOfDay().toEpochSecond(ZoneOffset.UTC);
            long endEpoch = endDate.atStartOfDay().toEpochSecond(ZoneOffset.UTC);
            
            Expression filterExpression = Expression.builder()
                .expression("#destination = :destination AND #startDate >= :startDate AND #endDate <= :endDate " +
                           "AND #isPublic = :true AND (attribute_not_exists(#deleted) OR #deleted = :false)")
                .putExpressionName("#destination", "destination")
                .putExpressionName("#startDate", "startDate")
                .putExpressionName("#endDate", "endDate")
                .putExpressionName("#isPublic", "isPublic")
                .putExpressionName("#deleted", "isDeleted")
                .putExpressionValue(":destination", AttributeValue.fromS(destination))
                .putExpressionValue(":startDate", AttributeValue.fromN(String.valueOf(startEpoch)))
                .putExpressionValue(":endDate", AttributeValue.fromN(String.valueOf(endEpoch)))
                .putExpressionValue(":true", AttributeValue.fromBool(true))
                .putExpressionValue(":false", AttributeValue.fromBool(false))
                .build();
            
            PageIterable<DynamoDBTravelPlan> results = scanWithFilter(filterExpression);
            
            return results.stream()
                         .flatMap(page -> page.items().stream())
                         .map(converters::toTravelPlanJPA)
                         .collect(Collectors.toList());
            
        } catch (Exception e) {
            log.error("Error finding similar plans for destination {} between {} and {}: {}", 
                     destination, startDate, endDate, e.getMessage(), e);
            throw new DynamoDBOperationException("Failed to find similar plans", e);
        }
    }
    
    @Override
    public List<TravelPlan> findOverlappingPlans(String userId, LocalDate startDate, LocalDate endDate) {
        try {
            long startEpoch = startDate.atStartOfDay().toEpochSecond(ZoneOffset.UTC);
            long endEpoch = endDate.atStartOfDay().toEpochSecond(ZoneOffset.UTC);
            
            QueryConditional queryConditional = QueryConditional.keyEqualTo(
                Key.builder().partitionValue(userId).build());
            
            Expression filterExpression = Expression.builder()
                .expression("((#startDate BETWEEN :rangeStart AND :rangeEnd) OR " +
                           "(#endDate BETWEEN :rangeStart AND :rangeEnd)) " +
                           "AND (attribute_not_exists(#deleted) OR #deleted = :false)")
                .putExpressionName("#startDate", "startDate")
                .putExpressionName("#endDate", "endDate")
                .putExpressionName("#deleted", "isDeleted")
                .putExpressionValue(":rangeStart", AttributeValue.fromN(String.valueOf(startEpoch)))
                .putExpressionValue(":rangeEnd", AttributeValue.fromN(String.valueOf(endEpoch)))
                .putExpressionValue(":false", AttributeValue.fromBool(false))
                .build();
            
            PageIterable<DynamoDBTravelPlan> results = queryIndexWithFilter("userId-index", 
                                                                           queryConditional, 
                                                                           filterExpression);
            
            return results.stream()
                         .flatMap(page -> page.items().stream())
                         .map(converters::toTravelPlanJPA)
                         .collect(Collectors.toList());
            
        } catch (Exception e) {
            log.error("Error finding overlapping plans for user {} between {} and {}: {}", 
                     userId, startDate, endDate, e.getMessage(), e);
            throw new DynamoDBOperationException("Failed to find overlapping plans", e);
        }
    }
    
    @Override
    public Optional<TravelPlan> findByIdAndUserIdAndIsDeletedFalse(String id, String userId) {
        try {
            Optional<DynamoDBTravelPlan> planOpt = super.findById(id);
            
            if (planOpt.isPresent()) {
                DynamoDBTravelPlan plan = planOpt.get();
                if (userId.equals(plan.getUserId()) && !Boolean.TRUE.equals(plan.getIsDeleted())) {
                    return Optional.of(converters.toTravelPlanJPA(plan));
                }
            }
            
            return Optional.empty();
            
        } catch (Exception e) {
            log.error("Error finding travel plan by id {} and userId {}: {}", id, userId, e.getMessage(), e);
            throw new DynamoDBOperationException("Failed to find travel plan by id and userId", e);
        }
    }
    
    @Override
    public Page<TravelPlan> findPopularPlans(Pageable pageable) {
        try {
            Expression filterExpression = Expression.builder()
                .expression("#isPublic = :true AND (attribute_not_exists(#deleted) OR #deleted = :false)")
                .putExpressionName("#isPublic", "isPublic")
                .putExpressionName("#deleted", "isDeleted")
                .putExpressionValue(":true", AttributeValue.fromBool(true))
                .putExpressionValue(":false", AttributeValue.fromBool(false))
                .build();
            
            PageIterable<DynamoDBTravelPlan> results = scanWithFilter(filterExpression);
            
            List<TravelPlan> plans = results.stream()
                                           .flatMap(page -> page.items().stream())
                                           .map(converters::toTravelPlanJPA)
                                           .sorted((p1, p2) -> Integer.compare(p2.getViewCount(), p1.getViewCount()))
                                           .collect(Collectors.toList());
            
            return createPage(plans, pageable, plans.size());
            
        } catch (Exception e) {
            log.error("Error finding popular plans: {}", e.getMessage(), e);
            throw new DynamoDBOperationException("Failed to find popular plans", e);
        }
    }
    
    @Override
    public Page<TravelPlan> findCollaboratingPlans(String userId, Pageable pageable) {
        try {
            Expression filterExpression = Expression.builder()
                .expression("contains(#collaborators, :userId) AND (attribute_not_exists(#deleted) OR #deleted = :false)")
                .putExpressionName("#collaborators", "collaboratorIds")
                .putExpressionName("#deleted", "isDeleted")
                .putExpressionValue(":userId", AttributeValue.fromS(userId))
                .putExpressionValue(":false", AttributeValue.fromBool(false))
                .build();
            
            PageIterable<DynamoDBTravelPlan> results = scanWithFilter(filterExpression);
            
            List<TravelPlan> plans = results.stream()
                                           .flatMap(page -> page.items().stream())
                                           .map(converters::toTravelPlanJPA)
                                           .collect(Collectors.toList());
            
            return createPage(plans, pageable, plans.size());
            
        } catch (Exception e) {
            log.error("Error finding collaborating plans for user {}: {}", userId, e.getMessage(), e);
            throw new DynamoDBOperationException("Failed to find collaborating plans", e);
        }
    }
    
    @Override
    public Page<TravelPlan> findByUserId(String userId, Pageable pageable) {
        try {
            QueryConditional queryConditional = QueryConditional.keyEqualTo(
                Key.builder().partitionValue(userId).build());
            
            Expression filterExpression = buildNotDeletedExpression();
            
            PageIterable<DynamoDBTravelPlan> results = queryIndexWithFilter("userId-index", 
                                                                           queryConditional, 
                                                                           filterExpression);
            
            List<TravelPlan> plans = results.stream()
                                           .flatMap(page -> page.items().stream())
                                           .map(converters::toTravelPlanJPA)
                                           .collect(Collectors.toList());
            
            return createPage(plans, pageable, plans.size());
            
        } catch (Exception e) {
            log.error("Error finding travel plans by userId {}: {}", userId, e.getMessage(), e);
            throw new DynamoDBOperationException("Failed to find travel plans by userId", e);
        }
    }
    
    @Override
    public Page<TravelPlan> findByIsPublicTrue(Pageable pageable) {
        try {
            Expression filterExpression = Expression.builder()
                .expression("#isPublic = :true AND (attribute_not_exists(#deleted) OR #deleted = :false)")
                .putExpressionName("#isPublic", "isPublic")
                .putExpressionName("#deleted", "isDeleted")
                .putExpressionValue(":true", AttributeValue.fromBool(true))
                .putExpressionValue(":false", AttributeValue.fromBool(false))
                .build();
            
            PageIterable<DynamoDBTravelPlan> results = scanWithFilter(filterExpression);
            
            List<TravelPlan> plans = results.stream()
                                           .flatMap(page -> page.items().stream())
                                           .map(converters::toTravelPlanJPA)
                                           .collect(Collectors.toList());
            
            return createPage(plans, pageable, plans.size());
            
        } catch (Exception e) {
            log.error("Error finding public travel plans: {}", e.getMessage(), e);
            throw new DynamoDBOperationException("Failed to find public travel plans", e);
        }
    }
    
    @Override
    public Page<TravelPlan> findByTitleContainingIgnoreCaseOrDestinationContainingIgnoreCase(
            String title, String destination, Pageable pageable) {
        try {
            String titleLower = title.toLowerCase();
            String destinationLower = destination.toLowerCase();
            
            Expression filterExpression = Expression.builder()
                .expression("(contains(#title, :title) OR contains(#destination, :destination)) " +
                           "AND (attribute_not_exists(#deleted) OR #deleted = :false)")
                .putExpressionName("#title", "title")
                .putExpressionName("#destination", "destination")
                .putExpressionName("#deleted", "isDeleted")
                .putExpressionValue(":title", AttributeValue.fromS(titleLower))
                .putExpressionValue(":destination", AttributeValue.fromS(destinationLower))
                .putExpressionValue(":false", AttributeValue.fromBool(false))
                .build();
            
            PageIterable<DynamoDBTravelPlan> results = scanWithFilter(filterExpression);
            
            List<TravelPlan> plans = results.stream()
                                           .flatMap(page -> page.items().stream())
                                           .map(converters::toTravelPlanJPA)
                                           .collect(Collectors.toList());
            
            return createPage(plans, pageable, plans.size());
            
        } catch (Exception e) {
            log.error("Error searching travel plans by title {} or destination {}: {}", 
                     title, destination, e.getMessage(), e);
            throw new DynamoDBOperationException("Failed to search travel plans", e);
        }
    }
}