package com.oddiya.config.aws;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Configuration
@ConfigurationProperties(prefix = "app.aws.dynamodb")
@ConditionalOnProperty(name = "app.aws.dynamodb.enabled", havingValue = "true", matchIfMissing = false)
@ConditionalOnBean(DynamoDbClient.class)
@Data
@Slf4j
public class DynamoDBConfig {

    private String tablePrefix = "oddiya_";
    private String billingMode = "PAY_PER_REQUEST";
    private int readCapacity = 5;
    private int writeCapacity = 5;
    private boolean createTablesOnStartup = false;
    private ConnectionPool connectionPool = new ConnectionPool();

    // Table name mappings
    private TableNames tableNames = new TableNames();

    @Bean
    public DynamoDBTableManager dynamoDBTableManager(DynamoDbClient dynamoDbClient) {
        return new DynamoDBTableManager(dynamoDbClient, this);
    }

    @PostConstruct
    public void initializeTableNames() {
        log.info("Initializing DynamoDB table names with prefix: {}", tablePrefix);
        tableNames.setUsers(tablePrefix + "users");
        tableNames.setPlaces(tablePrefix + "places");
        tableNames.setTravelPlans(tablePrefix + "travel_plans");
        tableNames.setItineraryItems(tablePrefix + "itinerary_items");
        tableNames.setReviews(tablePrefix + "reviews");
        tableNames.setSavedPlans(tablePrefix + "saved_plans");
        tableNames.setVideos(tablePrefix + "videos");
        tableNames.setUserSessions(tablePrefix + "user_sessions");
        tableNames.setAnalyticsEvents(tablePrefix + "analytics_events");
    }

    @Data
    public static class ConnectionPool {
        private int maxConnections = 50;
        private int connectionTimeout = 10000; // 10 seconds
        private int socketTimeout = 30000; // 30 seconds
        private int maxRetries = 3;
        private int retryDelayMs = 1000;
        private boolean enableConnectionPooling = true;
    }

    @Data
    public static class TableNames {
        private String users;
        private String places;
        private String travelPlans;
        private String itineraryItems;
        private String reviews;
        private String savedPlans;
        private String videos;
        private String userSessions;
        private String analyticsEvents;
    }

    /**
     * DynamoDB Table Manager for creating and managing tables
     */
    public static class DynamoDBTableManager {
        
        private final DynamoDbClient dynamoDbClient;
        private final DynamoDBConfig config;

        public DynamoDBTableManager(DynamoDbClient dynamoDbClient, DynamoDBConfig config) {
            this.dynamoDbClient = dynamoDbClient;
            this.config = config;
        }

        @PostConstruct
        public void initializeTables() {
            if (config.isCreateTablesOnStartup()) {
                log.info("Creating DynamoDB tables on startup");
                createTablesIfNotExist();
            }
        }

        public void createTablesIfNotExist() {
            createUsersTable();
            createPlacesTable();
            createTravelPlansTable();
            createItineraryItemsTable();
            createReviewsTable();
            createSavedPlansTable();
            createVideosTable();
            createUserSessionsTable();
            createAnalyticsEventsTable();
        }

        private void createUsersTable() {
            String tableName = config.getTableNames().getUsers();
            
            if (tableExists(tableName)) {
                log.info("Table {} already exists", tableName);
                return;
            }

            log.info("Creating table: {}", tableName);
            
            CreateTableRequest request = CreateTableRequest.builder()
                    .tableName(tableName)
                    .keySchema(
                        KeySchemaElement.builder()
                            .attributeName("userId")
                            .keyType(KeyType.HASH)
                            .build()
                    )
                    .attributeDefinitions(
                        AttributeDefinition.builder()
                            .attributeName("userId")
                            .attributeType(ScalarAttributeType.S)
                            .build(),
                        AttributeDefinition.builder()
                            .attributeName("email")
                            .attributeType(ScalarAttributeType.S)
                            .build()
                    )
                    .globalSecondaryIndexes(
                        GlobalSecondaryIndex.builder()
                            .indexName("email-index")
                            .keySchema(
                                KeySchemaElement.builder()
                                    .attributeName("email")
                                    .keyType(KeyType.HASH)
                                    .build()
                            )
                            .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                            .build()
                    )
                    .billingMode(BillingMode.fromValue(config.getBillingMode()))
                    .build();

            dynamoDbClient.createTable(request);
            waitForTableToBeActive(tableName);
        }

        private void createPlacesTable() {
            String tableName = config.getTableNames().getPlaces();
            
            if (tableExists(tableName)) {
                log.info("Table {} already exists", tableName);
                return;
            }

            log.info("Creating table: {}", tableName);
            
            CreateTableRequest request = CreateTableRequest.builder()
                    .tableName(tableName)
                    .keySchema(
                        KeySchemaElement.builder()
                            .attributeName("placeId")
                            .keyType(KeyType.HASH)
                            .build()
                    )
                    .attributeDefinitions(
                        AttributeDefinition.builder()
                            .attributeName("placeId")
                            .attributeType(ScalarAttributeType.S)
                            .build(),
                        AttributeDefinition.builder()
                            .attributeName("category")
                            .attributeType(ScalarAttributeType.S)
                            .build(),
                        AttributeDefinition.builder()
                            .attributeName("region")
                            .attributeType(ScalarAttributeType.S)
                            .build()
                    )
                    .globalSecondaryIndexes(
                        GlobalSecondaryIndex.builder()
                            .indexName("category-index")
                            .keySchema(
                                KeySchemaElement.builder()
                                    .attributeName("category")
                                    .keyType(KeyType.HASH)
                                    .build()
                            )
                            .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                            .build(),
                        GlobalSecondaryIndex.builder()
                            .indexName("region-index")
                            .keySchema(
                                KeySchemaElement.builder()
                                    .attributeName("region")
                                    .keyType(KeyType.HASH)
                                    .build()
                            )
                            .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                            .build()
                    )
                    .billingMode(BillingMode.fromValue(config.getBillingMode()))
                    .build();

            dynamoDbClient.createTable(request);
            waitForTableToBeActive(tableName);
        }

        private void createTravelPlansTable() {
            String tableName = config.getTableNames().getTravelPlans();
            
            if (tableExists(tableName)) {
                log.info("Table {} already exists", tableName);
                return;
            }

            log.info("Creating table: {}", tableName);
            
            CreateTableRequest request = CreateTableRequest.builder()
                    .tableName(tableName)
                    .keySchema(
                        KeySchemaElement.builder()
                            .attributeName("planId")
                            .keyType(KeyType.HASH)
                            .build()
                    )
                    .attributeDefinitions(
                        AttributeDefinition.builder()
                            .attributeName("planId")
                            .attributeType(ScalarAttributeType.S)
                            .build(),
                        AttributeDefinition.builder()
                            .attributeName("userId")
                            .attributeType(ScalarAttributeType.S)
                            .build()
                    )
                    .globalSecondaryIndexes(
                        GlobalSecondaryIndex.builder()
                            .indexName("userId-index")
                            .keySchema(
                                KeySchemaElement.builder()
                                    .attributeName("userId")
                                    .keyType(KeyType.HASH)
                                    .build()
                            )
                            .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                            .build()
                    )
                    .billingMode(BillingMode.fromValue(config.getBillingMode()))
                    .build();

            dynamoDbClient.createTable(request);
            waitForTableToBeActive(tableName);
        }

        private void createItineraryItemsTable() {
            String tableName = config.getTableNames().getItineraryItems();
            
            if (tableExists(tableName)) {
                log.info("Table {} already exists", tableName);
                return;
            }

            log.info("Creating table: {}", tableName);
            
            CreateTableRequest request = CreateTableRequest.builder()
                    .tableName(tableName)
                    .keySchema(
                        KeySchemaElement.builder()
                            .attributeName("itemId")
                            .keyType(KeyType.HASH)
                            .build()
                    )
                    .attributeDefinitions(
                        AttributeDefinition.builder()
                            .attributeName("itemId")
                            .attributeType(ScalarAttributeType.S)
                            .build(),
                        AttributeDefinition.builder()
                            .attributeName("planId")
                            .attributeType(ScalarAttributeType.S)
                            .build()
                    )
                    .globalSecondaryIndexes(
                        GlobalSecondaryIndex.builder()
                            .indexName("planId-index")
                            .keySchema(
                                KeySchemaElement.builder()
                                    .attributeName("planId")
                                    .keyType(KeyType.HASH)
                                    .build()
                            )
                            .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                            .build()
                    )
                    .billingMode(BillingMode.fromValue(config.getBillingMode()))
                    .build();

            dynamoDbClient.createTable(request);
            waitForTableToBeActive(tableName);
        }

        private void createReviewsTable() {
            String tableName = config.getTableNames().getReviews();
            
            if (tableExists(tableName)) {
                log.info("Table {} already exists", tableName);
                return;
            }

            log.info("Creating table: {}", tableName);
            
            CreateTableRequest request = CreateTableRequest.builder()
                    .tableName(tableName)
                    .keySchema(
                        KeySchemaElement.builder()
                            .attributeName("reviewId")
                            .keyType(KeyType.HASH)
                            .build()
                    )
                    .attributeDefinitions(
                        AttributeDefinition.builder()
                            .attributeName("reviewId")
                            .attributeType(ScalarAttributeType.S)
                            .build(),
                        AttributeDefinition.builder()
                            .attributeName("placeId")
                            .attributeType(ScalarAttributeType.S)
                            .build(),
                        AttributeDefinition.builder()
                            .attributeName("userId")
                            .attributeType(ScalarAttributeType.S)
                            .build()
                    )
                    .globalSecondaryIndexes(
                        GlobalSecondaryIndex.builder()
                            .indexName("placeId-index")
                            .keySchema(
                                KeySchemaElement.builder()
                                    .attributeName("placeId")
                                    .keyType(KeyType.HASH)
                                    .build()
                            )
                            .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                            .build(),
                        GlobalSecondaryIndex.builder()
                            .indexName("userId-index")
                            .keySchema(
                                KeySchemaElement.builder()
                                    .attributeName("userId")
                                    .keyType(KeyType.HASH)
                                    .build()
                            )
                            .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                            .build()
                    )
                    .billingMode(BillingMode.fromValue(config.getBillingMode()))
                    .build();

            dynamoDbClient.createTable(request);
            waitForTableToBeActive(tableName);
        }

        private void createSavedPlansTable() {
            String tableName = config.getTableNames().getSavedPlans();
            
            if (tableExists(tableName)) {
                log.info("Table {} already exists", tableName);
                return;
            }

            log.info("Creating table: {}", tableName);
            
            CreateTableRequest request = CreateTableRequest.builder()
                    .tableName(tableName)
                    .keySchema(
                        KeySchemaElement.builder()
                            .attributeName("savedPlanId")
                            .keyType(KeyType.HASH)
                            .build()
                    )
                    .attributeDefinitions(
                        AttributeDefinition.builder()
                            .attributeName("savedPlanId")
                            .attributeType(ScalarAttributeType.S)
                            .build(),
                        AttributeDefinition.builder()
                            .attributeName("userId")
                            .attributeType(ScalarAttributeType.S)
                            .build()
                    )
                    .globalSecondaryIndexes(
                        GlobalSecondaryIndex.builder()
                            .indexName("userId-index")
                            .keySchema(
                                KeySchemaElement.builder()
                                    .attributeName("userId")
                                    .keyType(KeyType.HASH)
                                    .build()
                            )
                            .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                            .build()
                    )
                    .billingMode(BillingMode.fromValue(config.getBillingMode()))
                    .build();

            dynamoDbClient.createTable(request);
            waitForTableToBeActive(tableName);
        }

        private void createVideosTable() {
            String tableName = config.getTableNames().getVideos();
            
            if (tableExists(tableName)) {
                log.info("Table {} already exists", tableName);
                return;
            }

            log.info("Creating table: {}", tableName);
            
            CreateTableRequest request = CreateTableRequest.builder()
                    .tableName(tableName)
                    .keySchema(
                        KeySchemaElement.builder()
                            .attributeName("videoId")
                            .keyType(KeyType.HASH)
                            .build()
                    )
                    .attributeDefinitions(
                        AttributeDefinition.builder()
                            .attributeName("videoId")
                            .attributeType(ScalarAttributeType.S)
                            .build(),
                        AttributeDefinition.builder()
                            .attributeName("placeId")
                            .attributeType(ScalarAttributeType.S)
                            .build()
                    )
                    .globalSecondaryIndexes(
                        GlobalSecondaryIndex.builder()
                            .indexName("placeId-index")
                            .keySchema(
                                KeySchemaElement.builder()
                                    .attributeName("placeId")
                                    .keyType(KeyType.HASH)
                                    .build()
                            )
                            .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                            .build()
                    )
                    .billingMode(BillingMode.fromValue(config.getBillingMode()))
                    .build();

            dynamoDbClient.createTable(request);
            waitForTableToBeActive(tableName);
        }

        private void createUserSessionsTable() {
            String tableName = config.getTableNames().getUserSessions();
            
            if (tableExists(tableName)) {
                log.info("Table {} already exists", tableName);
                return;
            }

            log.info("Creating table: {}", tableName);
            
            CreateTableRequest request = CreateTableRequest.builder()
                    .tableName(tableName)
                    .keySchema(
                        KeySchemaElement.builder()
                            .attributeName("sessionId")
                            .keyType(KeyType.HASH)
                            .build()
                    )
                    .attributeDefinitions(
                        AttributeDefinition.builder()
                            .attributeName("sessionId")
                            .attributeType(ScalarAttributeType.S)
                            .build(),
                        AttributeDefinition.builder()
                            .attributeName("userId")
                            .attributeType(ScalarAttributeType.S)
                            .build()
                    )
                    .globalSecondaryIndexes(
                        GlobalSecondaryIndex.builder()
                            .indexName("userId-index")
                            .keySchema(
                                KeySchemaElement.builder()
                                    .attributeName("userId")
                                    .keyType(KeyType.HASH)
                                    .build()
                            )
                            .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                            .build()
                    )
                    .billingMode(BillingMode.fromValue(config.getBillingMode()))
                    .build();

            dynamoDbClient.createTable(request);
            waitForTableToBeActive(tableName);
        }

        private void createAnalyticsEventsTable() {
            String tableName = config.getTableNames().getAnalyticsEvents();
            
            if (tableExists(tableName)) {
                log.info("Table {} already exists", tableName);
                return;
            }

            log.info("Creating table: {}", tableName);
            
            CreateTableRequest request = CreateTableRequest.builder()
                    .tableName(tableName)
                    .keySchema(
                        KeySchemaElement.builder()
                            .attributeName("eventId")
                            .keyType(KeyType.HASH)
                            .build()
                    )
                    .attributeDefinitions(
                        AttributeDefinition.builder()
                            .attributeName("eventId")
                            .attributeType(ScalarAttributeType.S)
                            .build(),
                        AttributeDefinition.builder()
                            .attributeName("eventType")
                            .attributeType(ScalarAttributeType.S)
                            .build(),
                        AttributeDefinition.builder()
                            .attributeName("timestamp")
                            .attributeType(ScalarAttributeType.N)
                            .build()
                    )
                    .globalSecondaryIndexes(
                        GlobalSecondaryIndex.builder()
                            .indexName("eventType-timestamp-index")
                            .keySchema(
                                KeySchemaElement.builder()
                                    .attributeName("eventType")
                                    .keyType(KeyType.HASH)
                                    .build(),
                                KeySchemaElement.builder()
                                    .attributeName("timestamp")
                                    .keyType(KeyType.RANGE)
                                    .build()
                            )
                            .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                            .build()
                    )
                    .billingMode(BillingMode.fromValue(config.getBillingMode()))
                    .build();

            dynamoDbClient.createTable(request);
            waitForTableToBeActive(tableName);
        }

        private boolean tableExists(String tableName) {
            try {
                DescribeTableResponse response = dynamoDbClient.describeTable(
                    DescribeTableRequest.builder().tableName(tableName).build()
                );
                return response.table().tableStatus() == TableStatus.ACTIVE;
            } catch (ResourceNotFoundException e) {
                return false;
            }
        }

        private void waitForTableToBeActive(String tableName) {
            log.info("Waiting for table {} to become active", tableName);
            
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    while (true) {
                        DescribeTableResponse response = dynamoDbClient.describeTable(
                            DescribeTableRequest.builder().tableName(tableName).build()
                        );
                        
                        if (response.table().tableStatus() == TableStatus.ACTIVE) {
                            log.info("Table {} is now active", tableName);
                            break;
                        }
                        
                        Thread.sleep(1000);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("Interrupted while waiting for table {} to be active", tableName);
                }
            });
            
            // Don't block application startup - let tables be created asynchronously
            future.exceptionally(throwable -> {
                log.error("Error waiting for table {} to be active: {}", tableName, throwable.getMessage());
                return null;
            });
        }
    }
}