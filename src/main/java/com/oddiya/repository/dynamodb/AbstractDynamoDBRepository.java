package com.oddiya.repository.dynamodb;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import software.amazon.awssdk.core.retry.RetryMode;
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.enhanced.dynamodb.model.*;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Abstract base repository providing common DynamoDB operations
 * with error handling, retry logic, and pagination utilities.
 */
@Slf4j
public abstract class AbstractDynamoDBRepository<T, K> {
    
    protected final DynamoDbEnhancedClient enhancedClient;
    protected final DynamoDbClient client;
    protected final DynamoDbTable<T> table;
    protected final String tableName;
    
    // Retry configuration
    private static final int MAX_RETRIES = 3;
    private static final Duration INITIAL_DELAY = Duration.ofMillis(100);
    private static final Duration MAX_DELAY = Duration.ofSeconds(10);
    
    protected AbstractDynamoDBRepository(DynamoDbEnhancedClient enhancedClient, 
                                       DynamoDbClient client,
                                       Class<T> entityClass, 
                                       String tableName) {
        this.enhancedClient = enhancedClient;
        this.client = client;
        this.tableName = tableName;
        this.table = enhancedClient.table(tableName, TableSchema.fromBean(entityClass));
    }
    
    // CRUD Operations
    
    public T save(T entity) {
        try {
            return executeWithRetry(() -> {
                updateTimestamps(entity);
                table.putItem(entity);
                return entity;
            });
        } catch (Exception e) {
            log.error("Error saving entity to table {}: {}", tableName, e.getMessage(), e);
            throw new DynamoDBOperationException("Failed to save entity", e);
        }
    }
    
    public List<T> saveAll(Collection<T> entities) {
        if (entities == null || entities.isEmpty()) {
            return new ArrayList<>();
        }
        
        try {
            return executeWithRetry(() -> {
                List<T> savedEntities = new ArrayList<>();
                
                // DynamoDB batch write supports up to 25 items
                List<List<T>> batches = partition(entities, 25);
                
                for (List<T> batch : batches) {
                    batch.forEach(this::updateTimestamps);
                    
                    BatchWriteItemEnhancedRequest.Builder requestBuilder = 
                        BatchWriteItemEnhancedRequest.builder();
                    
                    WriteBatch.Builder<T> batchBuilder = WriteBatch.builder(table.tableSchema().itemType())
                                                                  .mappedTableResource(table);
                    
                    batch.forEach(batchBuilder::addPutItem);
                    requestBuilder.addWriteBatch(batchBuilder.build());
                    
                    enhancedClient.batchWriteItem(requestBuilder.build());
                    savedEntities.addAll(batch);
                }
                
                return savedEntities;
            });
        } catch (Exception e) {
            log.error("Error batch saving {} entities to table {}: {}", 
                     entities.size(), tableName, e.getMessage(), e);
            throw new DynamoDBOperationException("Failed to batch save entities", e);
        }
    }
    
    public Optional<T> findById(K id) {
        try {
            return executeWithRetry(() -> {
                Key key = buildKey(id);
                T item = table.getItem(key);
                return Optional.ofNullable(item);
            });
        } catch (Exception e) {
            log.error("Error finding entity by id {} in table {}: {}", id, tableName, e.getMessage(), e);
            throw new DynamoDBOperationException("Failed to find entity by id", e);
        }
    }
    
    public List<T> findAllById(Collection<K> ids) {
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<>();
        }
        
        try {
            return executeWithRetry(() -> {
                List<T> results = new ArrayList<>();
                
                // DynamoDB batch get supports up to 100 items
                List<List<K>> batches = partition(ids, 100);
                
                for (List<K> batch : batches) {
                    ReadBatch.Builder<T> batchBuilder = ReadBatch.builder(table.tableSchema().itemType())
                                                                 .mappedTableResource(table);
                    
                    batch.stream()
                         .map(this::buildKey)
                         .forEach(batchBuilder::addGetItem);
                    
                    BatchGetItemEnhancedRequest request = BatchGetItemEnhancedRequest.builder()
                                                                                    .addReadBatch(batchBuilder.build())
                                                                                    .build();
                    
                    BatchGetResultPageIterable resultPages = enhancedClient.batchGetItem(request);
                    
                    resultPages.resultsForTable(table).forEach(results::add);
                }
                
                return results;
            });
        } catch (Exception e) {
            log.error("Error finding entities by ids {} in table {}: {}", ids, tableName, e.getMessage(), e);
            throw new DynamoDBOperationException("Failed to find entities by ids", e);
        }
    }
    
    public void deleteById(K id) {
        try {
            executeWithRetry(() -> {
                Key key = buildKey(id);
                table.deleteItem(key);
                return null;
            });
        } catch (Exception e) {
            log.error("Error deleting entity by id {} in table {}: {}", id, tableName, e.getMessage(), e);
            throw new DynamoDBOperationException("Failed to delete entity by id", e);
        }
    }
    
    public void deleteAll(Collection<T> entities) {
        if (entities == null || entities.isEmpty()) {
            return;
        }
        
        try {
            executeWithRetry(() -> {
                // DynamoDB batch write supports up to 25 items
                List<List<T>> batches = partition(entities, 25);
                
                for (List<T> batch : batches) {
                    BatchWriteItemEnhancedRequest.Builder requestBuilder = 
                        BatchWriteItemEnhancedRequest.builder();
                    
                    WriteBatch.Builder<T> batchBuilder = WriteBatch.builder(table.tableSchema().itemType())
                                                                  .mappedTableResource(table);
                    
                    batch.forEach(batchBuilder::addDeleteItem);
                    requestBuilder.addWriteBatch(batchBuilder.build());
                    
                    enhancedClient.batchWriteItem(requestBuilder.build());
                }
                
                return null;
            });
        } catch (Exception e) {
            log.error("Error batch deleting {} entities from table {}: {}", 
                     entities.size(), tableName, e.getMessage(), e);
            throw new DynamoDBOperationException("Failed to batch delete entities", e);
        }
    }
    
    public long count() {
        try {
            return executeWithRetry(() -> {
                ScanEnhancedRequest scanRequest = ScanEnhancedRequest.builder()
                    .select(Select.COUNT)
                    .build();
                
                PageIterable<T> results = table.scan(scanRequest);
                return results.stream()
                             .mapToLong(page -> page.items().size())
                             .sum();
            });
        } catch (Exception e) {
            log.error("Error counting entities in table {}: {}", tableName, e.getMessage(), e);
            throw new DynamoDBOperationException("Failed to count entities", e);
        }
    }
    
    // Query Operations
    
    protected PageIterable<T> scanAll() {
        return table.scan();
    }
    
    protected PageIterable<T> scanWithFilter(Expression filterExpression) {
        ScanEnhancedRequest scanRequest = ScanEnhancedRequest.builder()
            .filterExpression(filterExpression)
            .build();
        
        return table.scan(scanRequest);
    }
    
    protected PageIterable<T> queryIndex(String indexName, QueryConditional queryConditional) {
        QueryEnhancedRequest queryRequest = QueryEnhancedRequest.builder()
            .queryConditional(queryConditional)
            .build();
        
        return table.index(indexName).query(queryRequest);
    }
    
    protected PageIterable<T> queryIndexWithFilter(String indexName, 
                                                  QueryConditional queryConditional,
                                                  Expression filterExpression) {
        QueryEnhancedRequest queryRequest = QueryEnhancedRequest.builder()
            .queryConditional(queryConditional)
            .filterExpression(filterExpression)
            .build();
        
        return table.index(indexName).query(queryRequest);
    }
    
    // Pagination Utilities
    
    protected Page<T> createPage(List<T> items, Pageable pageable, long total) {
        return new PageImpl<>(items, pageable, total);
    }
    
    protected Page<T> paginateResults(PageIterable<T> results, Pageable pageable) {
        List<T> allItems = results.stream()
                                 .flatMap(page -> page.items().stream())
                                 .collect(Collectors.toList());
        
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), allItems.size());
        
        List<T> pageItems = start < allItems.size() ? 
                           allItems.subList(start, end) : 
                           new ArrayList<>();
        
        return new PageImpl<>(pageItems, pageable, allItems.size());
    }
    
    protected List<T> paginateResults(PageIterable<T> results, int limit) {
        return results.stream()
                     .flatMap(page -> page.items().stream())
                     .limit(limit)
                     .collect(Collectors.toList());
    }
    
    // Query Builder Helpers
    
    protected Expression buildEqualExpression(String attributeName, Object value) {
        return Expression.builder()
                        .expression("#attr = :value")
                        .putExpressionName("#attr", attributeName)
                        .putExpressionValue(":value", AttributeValue.fromS(value.toString()))
                        .build();
    }
    
    protected Expression buildContainsExpression(String attributeName, String value) {
        return Expression.builder()
                        .expression("contains(#attr, :value)")
                        .putExpressionName("#attr", attributeName)
                        .putExpressionValue(":value", AttributeValue.fromS(value))
                        .build();
    }
    
    protected Expression buildNotDeletedExpression() {
        return Expression.builder()
                        .expression("attribute_not_exists(#deleted) OR #deleted = :false")
                        .putExpressionName("#deleted", "isDeleted")
                        .putExpressionValue(":false", AttributeValue.fromBool(false))
                        .build();
    }
    
    protected Expression combineExpressions(Expression expr1, Expression expr2, String operator) {
        return Expression.builder()
                        .expression("(" + expr1.expression() + ") " + operator + " (" + expr2.expression() + ")")
                        .expressionNames(mergeStringMap(expr1.expressionNames(), expr2.expressionNames()))
                        .expressionValues(mergeAttributeMap(expr1.expressionValues(), expr2.expressionValues()))
                        .build();
    }
    
    // Utility Methods
    
    protected <R> R executeWithRetry(RetryableOperation<R> operation) {
        int retryCount = 0;
        Duration delay = INITIAL_DELAY;
        
        while (retryCount < MAX_RETRIES) {
            try {
                return operation.execute();
            } catch (ProvisionedThroughputExceededException | 
                     ThrottlingException | 
                     RequestLimitExceededException e) {
                
                retryCount++;
                if (retryCount >= MAX_RETRIES) {
                    throw e;
                }
                
                log.warn("DynamoDB operation throttled, retrying in {}ms (attempt {}/{})", 
                        delay.toMillis(), retryCount, MAX_RETRIES);
                
                try {
                    Thread.sleep(delay.toMillis());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Retry interrupted", ie);
                }
                
                delay = Duration.ofMillis(Math.min(delay.toMillis() * 2, MAX_DELAY.toMillis()));
            }
        }
        
        throw new RuntimeException("Max retries exceeded");
    }
    
    protected void updateTimestamps(T entity) {
        try {
            if (entity.getClass().getMethod("getCreatedAt").invoke(entity) == null) {
                entity.getClass().getMethod("setCreatedAt", Instant.class).invoke(entity, Instant.now());
            }
            entity.getClass().getMethod("setUpdatedAt", Instant.class).invoke(entity, Instant.now());
        } catch (Exception e) {
            // Ignore if entity doesn't have timestamp fields
            log.debug("Entity {} doesn't have timestamp fields", entity.getClass().getSimpleName());
        }
    }
    
    protected <E> List<List<E>> partition(Collection<E> collection, int size) {
        List<E> list = new ArrayList<>(collection);
        List<List<E>> partitions = new ArrayList<>();
        
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }
        
        return partitions;
    }
    
    protected Map<String, String> mergeStringMap(Map<String, String> map1, Map<String, String> map2) {
        Map<String, String> merged = new HashMap<>(map1);
        merged.putAll(map2);
        return merged;
    }
    
    protected Map<String, AttributeValue> mergeAttributeMap(Map<String, AttributeValue> map1, 
                                                           Map<String, AttributeValue> map2) {
        Map<String, AttributeValue> merged = new HashMap<>(map1);
        merged.putAll(map2);
        return merged;
    }
    
    // Abstract methods to be implemented by subclasses
    protected abstract Key buildKey(K id);
    
    @FunctionalInterface
    protected interface RetryableOperation<R> {
        R execute() throws Exception;
    }
    
    // Custom Exception for DynamoDB operations
    public static class DynamoDBOperationException extends RuntimeException {
        public DynamoDBOperationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}