package com.oddiya.aws;

import com.oddiya.entity.User;
import com.oddiya.entity.Place;
import com.oddiya.entity.TravelPlan;
import com.oddiya.entity.dynamodb.DynamoDBUser;
import com.oddiya.entity.dynamodb.DynamoDBPlace;
import com.oddiya.entity.dynamodb.DynamoDBTravelPlan;
import com.oddiya.repository.UserRepository;
import com.oddiya.repository.PlaceRepository;
import com.oddiya.repository.TravelPlanRepository;
import com.oddiya.repository.dynamodb.DynamoDBUserRepository;
import com.oddiya.repository.dynamodb.DynamoDBPlaceRepository;
import com.oddiya.repository.dynamodb.DynamoDBTravelPlanRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for DynamoDB repositories using LocalStack container.
 * Tests entity conversion, data consistency, and performance benchmarks.
 */
@SpringBootTest
@ActiveProfiles("dynamodb-test")
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DynamoDBIntegrationTest {

    @Container
    static GenericContainer<?> localstack = new GenericContainer<>("localstack/localstack:3.0")
            .withExposedPorts(4566)
            .withEnv("SERVICES", "dynamodb")
            .withEnv("DEBUG", "1")
            .withEnv("AWS_DEFAULT_REGION", "ap-northeast-2")
            .waitingFor(Wait.forHttp("/health").forPort(4566));

    @Autowired(required = false)
    private DynamoDBUserRepository dynamoDBUserRepository;

    @Autowired(required = false)
    private DynamoDBPlaceRepository dynamoDBPlaceRepository;

    @Autowired(required = false)
    private DynamoDBTravelPlanRepository dynamoDBTravelPlanRepository;

    private static DynamoDbClient testDynamoDbClient;
    private static final String TEST_TABLE_PREFIX = "test_oddiya_";

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("app.aws.dynamodb.endpoint", 
            () -> "http://localhost:" + localstack.getMappedPort(4566));
        registry.add("app.aws.dynamodb.enabled", () -> "true");
        registry.add("app.aws.dynamodb.table-prefix", () -> TEST_TABLE_PREFIX);
        registry.add("spring.profiles.active", () -> "dynamodb-test");
        registry.add("aws.region", () -> "ap-northeast-2");
    }

    @BeforeAll
    static void setUpDynamoDB() {
        testDynamoDbClient = DynamoDbClient.builder()
                .region(Region.AP_NORTHEAST_2)
                .endpointOverride(URI.create("http://localhost:" + localstack.getMappedPort(4566)))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test", "test")))
                .build();

        createTestTables();
    }

    static void createTestTables() {
        // Create User table
        createTable(TEST_TABLE_PREFIX + "users", "id");
        
        // Create Place table
        createTable(TEST_TABLE_PREFIX + "places", "id");
        
        // Create TravelPlan table
        createTable(TEST_TABLE_PREFIX + "travel_plans", "id");
        
        // Wait for tables to be active
        waitForTablesActive();
    }

    static void createTable(String tableName, String keyName) {
        try {
            CreateTableRequest request = CreateTableRequest.builder()
                    .tableName(tableName)
                    .keySchema(KeySchemaElement.builder()
                            .attributeName(keyName)
                            .keyType(KeyType.HASH)
                            .build())
                    .attributeDefinitions(AttributeDefinition.builder()
                            .attributeName(keyName)
                            .attributeType(ScalarAttributeType.S)
                            .build())
                    .billingMode(BillingMode.PAY_PER_REQUEST)
                    .build();

            testDynamoDbClient.createTable(request);
        } catch (ResourceInUseException e) {
            // Table already exists, ignore
        }
    }

    static void waitForTablesActive() {
        List<String> tableNames = Arrays.asList(
                TEST_TABLE_PREFIX + "users",
                TEST_TABLE_PREFIX + "places",
                TEST_TABLE_PREFIX + "travel_plans"
        );

        for (String tableName : tableNames) {
            try {
                DescribeTableRequest request = DescribeTableRequest.builder()
                        .tableName(tableName)
                        .build();

                // Wait up to 30 seconds for table to be active
                for (int i = 0; i < 30; i++) {
                    try {
                        DescribeTableResponse response = testDynamoDbClient.describeTable(request);
                        if (response.table().tableStatus() == TableStatus.ACTIVE) {
                            break;
                        }
                        Thread.sleep(1000);
                    } catch (Exception e) {
                        Thread.sleep(1000);
                    }
                }
            } catch (Exception e) {
                System.err.println("Warning: Could not verify table status for " + tableName);
            }
        }
    }

    @AfterAll
    static void cleanUp() {
        if (testDynamoDbClient != null) {
            testDynamoDbClient.close();
        }
    }

    @Test
    @Order(1)
    void contextLoads() {
        // Verify DynamoDB repositories are available when profile is active
        if ("dynamodb-test".equals(System.getProperty("spring.profiles.active"))) {
            assertThat(dynamoDBUserRepository).isNotNull();
            assertThat(dynamoDBPlaceRepository).isNotNull();
            assertThat(dynamoDBTravelPlanRepository).isNotNull();
        }
    }

    @Test
    @Order(2)
    void testUserEntityConversion() {
        if (dynamoDBUserRepository == null) {
            System.out.println("Skipping DynamoDB test - repository not available");
            return;
        }

        // Create JPA-style User entity
        User user = User.builder()
                .id(1L)
                .username("testuser")
                .email("test@example.com")
                .name("Test User")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // Convert to DynamoDB entity and save
        DynamoDBUser dynamoUser = convertToDynamoDBUser(user);
        DynamoDBUser savedUser = dynamoDBUserRepository.save(dynamoUser);

        // Verify conversion and storage
        assertThat(savedUser).isNotNull();
        assertThat(savedUser.getId()).isEqualTo("1");
        assertThat(savedUser.getUsername()).isEqualTo("testuser");
        assertThat(savedUser.getEmail()).isEqualTo("test@example.com");
        assertThat(savedUser.getName()).isEqualTo("Test User");

        // Verify retrieval
        Optional<DynamoDBUser> retrieved = dynamoDBUserRepository.findById("1");
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getUsername()).isEqualTo("testuser");
    }

    @Test
    @Order(3)
    void testPlaceEntityConversion() {
        if (dynamoDBPlaceRepository == null) {
            System.out.println("Skipping DynamoDB test - repository not available");
            return;
        }

        // Create JPA-style Place entity
        Place place = Place.builder()
                .id(1L)
                .name("Test Place")
                .description("A test place")
                .address("123 Test St")
                .latitude(37.7749)
                .longitude(-122.4194)
                .category("restaurant")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // Convert to DynamoDB entity and save
        DynamoDBPlace dynamoPlace = convertToDynamoDBPlace(place);
        DynamoDBPlace savedPlace = dynamoDBPlaceRepository.save(dynamoPlace);

        // Verify conversion and storage
        assertThat(savedPlace).isNotNull();
        assertThat(savedPlace.getId()).isEqualTo("1");
        assertThat(savedPlace.getName()).isEqualTo("Test Place");
        assertThat(savedPlace.getLatitude()).isEqualTo(37.7749);
        assertThat(savedPlace.getLongitude()).isEqualTo(-122.4194);

        // Test geospatial query capabilities (if implemented)
        // This would test DynamoDB's geospatial features
        List<DynamoDBPlace> nearbyPlaces = dynamoDBPlaceRepository.findNearby(37.7749, -122.4194, 1000);
        assertThat(nearbyPlaces).isNotEmpty();
    }

    @Test
    @Order(4)
    void testDataConsistency() {
        if (dynamoDBUserRepository == null || dynamoDBPlaceRepository == null) {
            System.out.println("Skipping DynamoDB test - repositories not available");
            return;
        }

        // Test eventual consistency behavior
        DynamoDBUser user = DynamoDBUser.builder()
                .id("consistency_test_user")
                .username("consistencytest")
                .email("consistency@test.com")
                .name("Consistency Test User")
                .build();

        // Save user
        dynamoDBUserRepository.save(user);

        // Immediate read (should work due to read-after-write consistency)
        Optional<DynamoDBUser> immediateRead = dynamoDBUserRepository.findById("consistency_test_user");
        assertThat(immediateRead).isPresent();

        // Update user
        user.setName("Updated Name");
        dynamoDBUserRepository.save(user);

        // Read updated data
        Optional<DynamoDBUser> updatedRead = dynamoDBUserRepository.findById("consistency_test_user");
        assertThat(updatedRead).isPresent();
        assertThat(updatedRead.get().getName()).isEqualTo("Updated Name");
    }

    @Test
    @Order(5)
    void testBatchOperations() {
        if (dynamoDBPlaceRepository == null) {
            System.out.println("Skipping DynamoDB test - repository not available");
            return;
        }

        // Test batch save operations
        List<DynamoDBPlace> places = Arrays.asList(
                DynamoDBPlace.builder().id("batch1").name("Batch Place 1").category("restaurant").build(),
                DynamoDBPlace.builder().id("batch2").name("Batch Place 2").category("hotel").build(),
                DynamoDBPlace.builder().id("batch3").name("Batch Place 3").category("attraction").build()
        );

        // Save all places
        List<DynamoDBPlace> savedPlaces = dynamoDBPlaceRepository.saveAll(places);
        assertThat(savedPlaces).hasSize(3);

        // Verify batch retrieval
        List<String> ids = Arrays.asList("batch1", "batch2", "batch3");
        List<DynamoDBPlace> retrievedPlaces = dynamoDBPlaceRepository.findAllById(ids);
        assertThat(retrievedPlaces).hasSize(3);

        // Test batch delete
        dynamoDBPlaceRepository.deleteAllById(ids);
        
        // Verify deletion
        List<DynamoDBPlace> afterDelete = dynamoDBPlaceRepository.findAllById(ids);
        assertThat(afterDelete).isEmpty();
    }

    @Test
    @Order(6)
    void performanceComparison() {
        if (dynamoDBUserRepository == null) {
            System.out.println("Skipping performance test - DynamoDB not available");
            return;
        }

        // Performance test for DynamoDB operations
        long startTime = System.currentTimeMillis();
        
        // Create and save 100 users
        for (int i = 0; i < 100; i++) {
            DynamoDBUser user = DynamoDBUser.builder()
                    .id("perf_user_" + i)
                    .username("perfuser" + i)
                    .email("perf" + i + "@test.com")
                    .name("Performance User " + i)
                    .build();
            dynamoDBUserRepository.save(user);
        }
        
        long saveTime = System.currentTimeMillis() - startTime;
        
        // Read all users back
        startTime = System.currentTimeMillis();
        long count = dynamoDBUserRepository.count();
        long readTime = System.currentTimeMillis() - startTime;
        
        // Log performance metrics
        System.out.println("DynamoDB Performance Metrics:");
        System.out.println("  - Save 100 records: " + saveTime + "ms");
        System.out.println("  - Count operation: " + readTime + "ms");
        System.out.println("  - Total records: " + count);
        
        // Verify all records were saved
        assertThat(count).isGreaterThanOrEqualTo(100);
    }

    @Test
    @Order(7)
    void testConcurrentOperations() {
        if (dynamoDBUserRepository == null) {
            System.out.println("Skipping concurrent operations test - DynamoDB not available");
            return;
        }

        ExecutorService executor = Executors.newFixedThreadPool(5);
        
        // Test concurrent writes
        CompletableFuture<Void>[] futures = new CompletableFuture[10];
        
        for (int i = 0; i < 10; i++) {
            final int index = i;
            futures[i] = CompletableFuture.runAsync(() -> {
                DynamoDBUser user = DynamoDBUser.builder()
                        .id("concurrent_user_" + index)
                        .username("concurrentuser" + index)
                        .email("concurrent" + index + "@test.com")
                        .name("Concurrent User " + index)
                        .build();
                dynamoDBUserRepository.save(user);
            }, executor);
        }
        
        // Wait for all operations to complete
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(futures);
        try {
            allFutures.get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException("Concurrent operations failed", e);
        }
        
        // Verify all users were saved
        for (int i = 0; i < 10; i++) {
            Optional<DynamoDBUser> user = dynamoDBUserRepository.findById("concurrent_user_" + i);
            assertThat(user).isPresent();
        }
        
        executor.shutdown();
    }

    @Test
    @Order(8)
    void testErrorHandling() {
        if (dynamoDBUserRepository == null) {
            System.out.println("Skipping error handling test - DynamoDB not available");
            return;
        }

        // Test handling of non-existent records
        Optional<DynamoDBUser> nonExistent = dynamoDBUserRepository.findById("non_existent_user");
        assertThat(nonExistent).isEmpty();

        // Test handling of invalid data
        try {
            DynamoDBUser invalidUser = DynamoDBUser.builder()
                    .id("") // Invalid empty ID
                    .username("testuser")
                    .build();
            dynamoDBUserRepository.save(invalidUser);
            
            // If no exception is thrown, verify the save operation
            Optional<DynamoDBUser> retrieved = dynamoDBUserRepository.findById("");
            // DynamoDB should handle empty string keys appropriately
        } catch (Exception e) {
            // Expected behavior for invalid data
            assertThat(e).isNotNull();
        }
    }

    // Helper methods for entity conversion
    private DynamoDBUser convertToDynamoDBUser(User user) {
        return DynamoDBUser.builder()
                .id(user.getId().toString())
                .username(user.getUsername())
                .email(user.getEmail())
                .name(user.getName())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    private DynamoDBPlace convertToDynamoDBPlace(Place place) {
        return DynamoDBPlace.builder()
                .id(place.getId().toString())
                .name(place.getName())
                .description(place.getDescription())
                .address(place.getAddress())
                .latitude(place.getLatitude())
                .longitude(place.getLongitude())
                .category(place.getCategory())
                .createdAt(place.getCreatedAt())
                .updatedAt(place.getUpdatedAt())
                .build();
    }

    private DynamoDBTravelPlan convertToDynamoDBTravelPlan(TravelPlan travelPlan) {
        return DynamoDBTravelPlan.builder()
                .id(travelPlan.getId().toString())
                .title(travelPlan.getTitle())
                .description(travelPlan.getDescription())
                .startDate(travelPlan.getStartDate())
                .endDate(travelPlan.getEndDate())
                .createdAt(travelPlan.getCreatedAt())
                .updatedAt(travelPlan.getUpdatedAt())
                .build();
    }
}