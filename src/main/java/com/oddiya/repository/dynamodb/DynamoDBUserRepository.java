package com.oddiya.repository.dynamodb;

import com.oddiya.entity.User;
import com.oddiya.entity.dynamodb.DynamoDBUser;
import com.oddiya.repository.UserRepository;
import com.oddiya.repository.dynamodb.config.DynamoDBRepositoryMetrics;
import com.oddiya.repository.dynamodb.enhanced.DynamoDBRepositoryEnhancements;
import com.oddiya.converter.DynamoDBConverters;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
 * DynamoDB implementation of UserRepository interface.
 * Provides all user-related database operations using DynamoDB.
 */
@Repository
@Slf4j
@ConditionalOnProperty(name = "spring.profiles.active", havingValue = "dynamodb")
public class DynamoDBUserRepository extends AbstractDynamoDBRepository<DynamoDBUser, String> 
                                    implements UserRepository {
    
    private final DynamoDBConverters converters;
    
    @Autowired(required = false)
    private DynamoDBRepositoryEnhancements enhancements;
    
    @Autowired(required = false)  
    private DynamoDBRepositoryMetrics metrics;
    
    public DynamoDBUserRepository(DynamoDbEnhancedClient enhancedClient,
                                 DynamoDbClient client,
                                 DynamoDBConverters converters,
                                 String tableName) {
        super(enhancedClient, client, DynamoDBUser.class, tableName != null ? tableName : "oddiya_users");
        this.converters = converters;
    }
    
    @Override
    protected Key buildKey(String id) {
        return Key.builder().partitionValue(id).build();
    }
    
    // JPA Repository methods implementation
    
    @Override
    public <S extends User> S save(S entity) {
        try {
            DynamoDBUser dynamoEntity = converters.toUserDynamoDB(entity);
            DynamoDBUser saved = save(dynamoEntity);
            return (S) converters.toUserJPA(saved);
        } catch (Exception e) {
            log.error("Error saving user {}: {}", entity.getId(), e.getMessage(), e);
            throw new DynamoDBOperationException("Failed to save user", e);
        }
    }
    
    @Override
    public <S extends User> List<S> saveAll(Iterable<S> entities) {
        try {
            List<DynamoDBUser> dynamoEntities = 
                ((List<S>) entities).stream()
                                   .map(converters::toUserDynamoDB)
                                   .collect(Collectors.toList());
            
            List<DynamoDBUser> saved = saveAll(dynamoEntities);
            
            return (List<S>) saved.stream()
                                  .map(converters::toUserJPA)
                                  .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error batch saving users: {}", e.getMessage(), e);
            throw new DynamoDBOperationException("Failed to batch save users", e);
        }
    }
    
    @Override
    public Optional<User> findById(String id) {
        try {
            Optional<DynamoDBUser> result = super.findById(id);
            return result.map(converters::toUserJPA);
        } catch (Exception e) {
            log.error("Error finding user by id {}: {}", id, e.getMessage(), e);
            throw new DynamoDBOperationException("Failed to find user by id", e);
        }
    }
    
    @Override
    public boolean existsById(String id) {
        return findById(id).isPresent();
    }
    
    @Override
    public List<User> findAll() {
        try {
            return scanAll().stream()
                           .flatMap(page -> page.items().stream())
                           .map(converters::toUserJPA)
                           .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error finding all users: {}", e.getMessage(), e);
            throw new DynamoDBOperationException("Failed to find all users", e);
        }
    }
    
    @Override
    public List<User> findAllById(Iterable<String> ids) {
        try {
            List<DynamoDBUser> results = super.findAllById((List<String>) ids);
            return results.stream()
                         .map(converters::convertDynamoDBToUser)
                         .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error finding users by ids: {}", e.getMessage(), e);
            throw new DynamoDBOperationException("Failed to find users by ids", e);
        }
    }
    
    @Override
    public void deleteById(String id) {
        try {
            super.deleteById(id);
        } catch (Exception e) {
            log.error("Error deleting user by id {}: {}", id, e.getMessage(), e);
            throw new DynamoDBOperationException("Failed to delete user by id", e);
        }
    }
    
    @Override
    public void delete(User entity) {
        deleteById(entity.getId());
    }
    
    @Override
    public void deleteAllById(Iterable<? extends String> ids) {
        try {
            List<DynamoDBUser> toDelete = super.findAllById((List<String>) ids);
            deleteAll(toDelete);
        } catch (Exception e) {
            log.error("Error batch deleting users by ids: {}", e.getMessage(), e);
            throw new DynamoDBOperationException("Failed to batch delete users by ids", e);
        }
    }
    
    @Override
    public void deleteAll(Iterable<? extends User> entities) {
        try {
            List<DynamoDBUser> dynamoEntities = 
                ((List<User>) entities).stream()
                                      .map(converters::toUserDynamoDB)
                                      .collect(Collectors.toList());
            
            super.deleteAll(dynamoEntities);
        } catch (Exception e) {
            log.error("Error batch deleting users: {}", e.getMessage(), e);
            throw new DynamoDBOperationException("Failed to batch delete users", e);
        }
    }
    
    @Override
    public void deleteAll() {
        try {
            List<User> allUsers = findAll();
            deleteAll(allUsers);
        } catch (Exception e) {
            log.error("Error deleting all users: {}", e.getMessage(), e);
            throw new DynamoDBOperationException("Failed to delete all users", e);
        }
    }
    
    // Custom repository methods implementation
    
    @Override
    public Optional<User> findByEmail(String email) {
        try {
            QueryConditional queryConditional = QueryConditional.keyEqualTo(
                Key.builder().partitionValue(email).build());
            
            PageIterable<DynamoDBUser> results = queryIndex("email-index", queryConditional);
            
            Optional<DynamoDBUser> firstResult = results.stream()
                                                        .flatMap(page -> page.items().stream())
                                                        .filter(user -> !Boolean.TRUE.equals(user.getIsDeleted()))
                                                        .findFirst();
            
            return firstResult.map(converters::convertDynamoDBToUser);
            
        } catch (Exception e) {
            log.error("Error finding user by email {}: {}", email, e.getMessage(), e);
            throw new DynamoDBOperationException("Failed to find user by email", e);
        }
    }
    
    @Override
    public Optional<User> findByProviderAndProviderId(String provider, String providerId) {
        try {
            QueryConditional queryConditional = QueryConditional.keyEqualTo(
                Key.builder()
                   .partitionValue(provider)
                   .sortValue(providerId)
                   .build());
            
            PageIterable<DynamoDBUser> results = queryIndex("provider-index", queryConditional);
            
            Optional<DynamoDBUser> firstResult = results.stream()
                                                        .flatMap(page -> page.items().stream())
                                                        .filter(user -> !Boolean.TRUE.equals(user.getIsDeleted()))
                                                        .findFirst();
            
            return firstResult.map(converters::convertDynamoDBToUser);
            
        } catch (Exception e) {
            log.error("Error finding user by provider {} and providerId {}: {}", 
                     provider, providerId, e.getMessage(), e);
            throw new DynamoDBOperationException("Failed to find user by provider and providerId", e);
        }
    }
    
    @Override
    public boolean existsByEmail(String email) {
        return findByEmail(email).isPresent();
    }
    
    @Override
    public boolean existsByNickname(String nickname) {
        try {
            Expression filterExpression = Expression.builder()
                .expression("#nickname = :nickname AND (attribute_not_exists(#deleted) OR #deleted = :false)")
                .putExpressionName("#nickname", "nickname")
                .putExpressionName("#deleted", "isDeleted")
                .putExpressionValue(":nickname", AttributeValue.fromS(nickname))
                .putExpressionValue(":false", AttributeValue.fromBool(false))
                .build();
            
            PageIterable<DynamoDBUser> results = scanWithFilter(filterExpression);
            
            return results.stream()
                         .flatMap(page -> page.items().stream())
                         .findAny()
                         .isPresent();
            
        } catch (Exception e) {
            log.error("Error checking if nickname {} exists: {}", nickname, e.getMessage(), e);
            throw new DynamoDBOperationException("Failed to check nickname existence", e);
        }
    }
    
    @Override
    public Page<User> findActiveUsers(Pageable pageable) {
        try {
            Expression filterExpression = Expression.builder()
                .expression("#active = :true AND (attribute_not_exists(#deleted) OR #deleted = :false)")
                .putExpressionName("#active", "isActive")
                .putExpressionName("#deleted", "isDeleted")
                .putExpressionValue(":true", AttributeValue.fromBool(true))
                .putExpressionValue(":false", AttributeValue.fromBool(false))
                .build();
            
            PageIterable<DynamoDBUser> results = scanWithFilter(filterExpression);
            
            List<User> users = results.stream()
                                     .flatMap(page -> page.items().stream())
                                     .map(converters::toUserJPA)
                                     .collect(Collectors.toList());
            
            return createPage(users, pageable, users.size());
            
        } catch (Exception e) {
            log.error("Error finding active users: {}", e.getMessage(), e);
            throw new DynamoDBOperationException("Failed to find active users", e);
        }
    }
    
    @Override
    public Page<User> searchUsers(String query, Pageable pageable) {
        try {
            String lowercaseQuery = query.toLowerCase();
            
            Expression filterExpression = Expression.builder()
                .expression("(contains(#nickname, :query) OR contains(#bio, :query)) " +
                           "AND #active = :true AND (attribute_not_exists(#deleted) OR #deleted = :false)")
                .putExpressionName("#nickname", "nickname")
                .putExpressionName("#bio", "bio")
                .putExpressionName("#active", "isActive")
                .putExpressionName("#deleted", "isDeleted")
                .putExpressionValue(":query", AttributeValue.fromS(lowercaseQuery))
                .putExpressionValue(":true", AttributeValue.fromBool(true))
                .putExpressionValue(":false", AttributeValue.fromBool(false))
                .build();
            
            PageIterable<DynamoDBUser> results = scanWithFilter(filterExpression);
            
            List<User> users = results.stream()
                                     .flatMap(page -> page.items().stream())
                                     .map(converters::toUserJPA)
                                     .collect(Collectors.toList());
            
            return createPage(users, pageable, users.size());
            
        } catch (Exception e) {
            log.error("Error searching users with query {}: {}", query, e.getMessage(), e);
            throw new DynamoDBOperationException("Failed to search users", e);
        }
    }
    
    @Override
    public Page<User> findFollowing(String followerId, Pageable pageable) {
        try {
            // Get the follower user to access their following list
            Optional<DynamoDBUser> followerOpt = super.findById(followerId);
            if (!followerOpt.isPresent()) {
                return createPage(List.of(), pageable, 0);
            }
            
            DynamoDBUser follower = followerOpt.get();
            List<String> followingIds = follower.getFollowingIds();
            
            if (followingIds == null || followingIds.isEmpty()) {
                return createPage(List.of(), pageable, 0);
            }
            
            List<DynamoDBUser> followingUsers = super.findAllById(followingIds);
            List<User> users = followingUsers.stream()
                                            .map(converters::toUserJPA)
                                            .collect(Collectors.toList());
            
            return createPage(users, pageable, users.size());
            
        } catch (Exception e) {
            log.error("Error finding following for user {}: {}", followerId, e.getMessage(), e);
            throw new DynamoDBOperationException("Failed to find following users", e);
        }
    }
    
    @Override
    public Page<User> findFollowers(String userId, Pageable pageable) {
        try {
            // Get the user to access their followers list
            Optional<DynamoDBUser> userOpt = super.findById(userId);
            if (!userOpt.isPresent()) {
                return createPage(List.of(), pageable, 0);
            }
            
            DynamoDBUser user = userOpt.get();
            List<String> followerIds = user.getFollowerIds();
            
            if (followerIds == null || followerIds.isEmpty()) {
                return createPage(List.of(), pageable, 0);
            }
            
            List<DynamoDBUser> followerUsers = super.findAllById(followerIds);
            List<User> users = followerUsers.stream()
                                           .map(converters::toUserJPA)
                                           .collect(Collectors.toList());
            
            return createPage(users, pageable, users.size());
            
        } catch (Exception e) {
            log.error("Error finding followers for user {}: {}", userId, e.getMessage(), e);
            throw new DynamoDBOperationException("Failed to find followers", e);
        }
    }
    
    @Override
    public Long countFollowers(String userId) {
        try {
            Optional<DynamoDBUser> userOpt = super.findById(userId);
            if (!userOpt.isPresent()) {
                return 0L;
            }
            
            DynamoDBUser user = userOpt.get();
            List<String> followerIds = user.getFollowerIds();
            
            return followerIds != null ? (long) followerIds.size() : 0L;
            
        } catch (Exception e) {
            log.error("Error counting followers for user {}: {}", userId, e.getMessage(), e);
            throw new DynamoDBOperationException("Failed to count followers", e);
        }
    }
    
    @Override
    public Long countFollowing(String userId) {
        try {
            Optional<DynamoDBUser> userOpt = super.findById(userId);
            if (!userOpt.isPresent()) {
                return 0L;
            }
            
            DynamoDBUser user = userOpt.get();
            List<String> followingIds = user.getFollowingIds();
            
            return followingIds != null ? (long) followingIds.size() : 0L;
            
        } catch (Exception e) {
            log.error("Error counting following for user {}: {}", userId, e.getMessage(), e);
            throw new DynamoDBOperationException("Failed to count following", e);
        }
    }
}