package com.oddiya.integration;

import com.oddiya.config.TestContainersConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
// EnableJUnitPlatform is not needed in Spring Boot 3.x with JUnit 5
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Base Integration Test Class for Oddiya Testing Framework
 * 
 * Provides comprehensive testing infrastructure as per PRD specifications:
 * - PostgreSQL + PostGIS for spatial queries
 * - LocalStack for AWS services (S3, DynamoDB, SQS)
 * - MockMvc for API testing
 * - TestRestTemplate for full HTTP integration tests
 * - Performance testing foundation (sub-200ms response times)
 * - Database transaction management for test isolation
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.profiles.active=integration-test",
        "logging.level.com.oddiya=DEBUG",
        "logging.level.org.testcontainers=INFO",
        "spring.jpa.show-sql=false"
    }
)
@Import(TestContainersConfiguration.class)
@Testcontainers
@ActiveProfiles("integration-test")
@AutoConfigureWebMvc
@Transactional
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Slf4j
public abstract class BaseIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected TestRestTemplate restTemplate;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected DataSource dataSource;

    @Autowired
    protected PostgreSQLContainer<?> postgresContainer;

    @Autowired
    protected LocalStackContainer localStackContainer;

    /**
     * Performance testing thresholds as per PRD requirements
     */
    protected static final long MAX_API_RESPONSE_TIME_MS = 200; // <200ms API response time
    protected static final long MAX_DATABASE_QUERY_TIME_MS = 50; // <50ms database queries
    protected static final long MAX_AI_GENERATION_TIME_MS = 5000; // <5s AI generation

    /**
     * Test data management constants
     */
    protected static final String TEST_USER_ID = "test-user-123";
    protected static final String TEST_TRAVEL_PLAN_ID = "test-plan-123";
    protected static final String TEST_PLACE_ID = "test-place-123";
    
    // Seoul coordinates for spatial testing (as per PRD Korea focus)
    protected static final double SEOUL_LATITUDE = 37.5665;
    protected static final double SEOUL_LONGITUDE = 126.9780;
    protected static final double BUSAN_LATITUDE = 35.1796;
    protected static final double BUSAN_LONGITUDE = 129.0756;

    @BeforeEach
    void setUpBaseIntegrationTest() {
        log.debug("Setting up base integration test");
        
        // Verify TestContainers are running
        verifyContainersHealth();
        
        // Verify database connection and PostGIS
        verifyPostGISConnection();
        
        // Clean test data if needed (handled by @Transactional rollback)
        log.debug("Base integration test setup completed");
    }

    /**
     * Verify all TestContainers are healthy
     */
    protected void verifyContainersHealth() {
        if (!postgresContainer.isRunning()) {
            throw new RuntimeException("PostgreSQL TestContainer is not running");
        }
        
        if (!localStackContainer.isRunning()) {
            throw new RuntimeException("LocalStack TestContainer is not running");
        }
        
        log.debug("All TestContainers are healthy");
    }

    /**
     * Verify PostGIS connection and spatial capabilities
     */
    protected void verifyPostGISConnection() {
        try (Connection connection = dataSource.getConnection()) {
            var statement = connection.createStatement();
            var resultSet = statement.executeQuery("SELECT PostGIS_Version();");
            
            if (resultSet.next()) {
                String version = resultSet.getString(1);
                log.debug("PostGIS version: {}", version);
            } else {
                throw new RuntimeException("PostGIS not available in test database");
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to verify PostGIS connection", e);
        }
    }

    /**
     * Execute a spatial query for testing PostGIS functionality
     * Tests the core spatial search requirement from PRD
     */
    protected void executeSpatialQueryTest() {
        try (Connection connection = dataSource.getConnection()) {
            var statement = connection.createStatement();
            
            // Test spatial distance calculation (Seoul to Busan)
            var query = """
                SELECT ST_Distance(
                    ST_GeogFromText('POINT(%f %f)'),
                    ST_GeogFromText('POINT(%f %f)')
                ) / 1000 AS distance_km;
                """.formatted(SEOUL_LONGITUDE, SEOUL_LATITUDE, BUSAN_LONGITUDE, BUSAN_LATITUDE);
            
            var resultSet = statement.executeQuery(query);
            if (resultSet.next()) {
                double distanceKm = resultSet.getDouble("distance_km");
                log.debug("Spatial query test: Seoul to Busan distance = {} km", distanceKm);
                
                // Verify reasonable distance (Seoul to Busan is ~325km)
                if (distanceKm < 300 || distanceKm > 400) {
                    log.warn("Spatial distance calculation seems incorrect: {} km", distanceKm);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to execute spatial query test", e);
        }
    }

    /**
     * Helper method to measure method execution time
     * Used for performance testing as per PRD requirements
     */
    protected long measureExecutionTime(Runnable operation) {
        long startTime = System.currentTimeMillis();
        operation.run();
        return System.currentTimeMillis() - startTime;
    }

    /**
     * Helper method to measure method execution time with return value
     */
    protected <T> TimedResult<T> measureExecutionTime(java.util.function.Supplier<T> operation) {
        long startTime = System.currentTimeMillis();
        T result = operation.get();
        long executionTime = System.currentTimeMillis() - startTime;
        return new TimedResult<>(result, executionTime);
    }

    /**
     * Record class for timed operations
     */
    public record TimedResult<T>(T result, long executionTimeMs) {
        public void assertPerformance(long maxTimeMs, String operationName) {
            if (executionTimeMs > maxTimeMs) {
                throw new AssertionError(String.format(
                    "%s took %dms, exceeding maximum allowed %dms", 
                    operationName, executionTimeMs, maxTimeMs
                ));
            }
        }
    }

    /**
     * Create test JWT token for authentication testing
     * This method should be overridden by tests that need authentication
     */
    protected String createTestJwtToken() {
        // Default implementation - override in specific test classes
        return "Bearer mock-jwt-token-for-testing";
    }

    /**
     * Clean up method called after each test
     * TestContainers and @Transactional handle most cleanup automatically
     */
    protected void cleanupTestData() {
        // Override in subclasses if specific cleanup is needed
        // Default: rely on transaction rollback for database cleanup
        log.debug("Test data cleanup completed via transaction rollback");
    }
}