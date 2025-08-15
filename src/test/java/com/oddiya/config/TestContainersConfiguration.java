package com.oddiya.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;

/**
 * TestContainers Configuration for Oddiya Testing
 * Provides comprehensive integration testing infrastructure as per PRD requirements
 */
@TestConfiguration
@Testcontainers
@Slf4j
public class TestContainersConfiguration {

    /**
     * PostgreSQL container with PostGIS extension for spatial testing
     * Phase 2: Updated for PostgreSQL migration with PostGIS support
     */
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgis/postgis:15-3.3")
            .withDatabaseName("oddiya_test")
            .withUsername("test_user")
            .withPassword("test_password")
            .withInitScript("test-init.sql")
            .withCommand("postgres", 
                "-c", "shared_preload_libraries=postgis",
                "-c", "max_connections=100",
                "-c", "log_statement=none")
            .withReuse(true);

    /**
     * LocalStack container for AWS services testing
     * Supports S3, DynamoDB, SQS, Bedrock (mock) as per PRD AWS services
     */
    @Container
    static LocalStackContainer localstack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:2.3"))
            .withServices(
                LocalStackContainer.Service.S3,
                LocalStackContainer.Service.DYNAMODB,
                LocalStackContainer.Service.SQS,
                LocalStackContainer.Service.CLOUDWATCH
            )
            .withReuse(true);

    /**
     * Configure Spring Boot properties dynamically based on TestContainers
     */
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // PostgreSQL configuration
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        
        // JPA configuration for testing (Phase 2 PostgreSQL)
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.show-sql", () -> "false");
        registry.add("spring.jpa.properties.hibernate.format_sql", () -> "false");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.spatial.dialect.postgis.PostgisPG15Dialect");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.spatial.dialect.postgis.PostgisPG15Dialect");
        
        // Flyway configuration for testing
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration,classpath:db/test-migration");
        registry.add("spring.flyway.clean-disabled", () -> "false");
        
        // AWS LocalStack configuration
        registry.add("cloud.aws.credentials.access-key", () -> "test");
        registry.add("cloud.aws.credentials.secret-key", () -> "test");
        registry.add("cloud.aws.region.static", () -> "us-east-1");
        registry.add("cloud.aws.region.auto", () -> "false");
        registry.add("cloud.aws.stack.auto", () -> "false");
        
        // S3 LocalStack configuration
        registry.add("cloud.aws.s3.endpoint", () -> localstack.getEndpointOverride(LocalStackContainer.Service.S3).toString());
        registry.add("aws.s3.bucket.name", () -> "oddiya-test-bucket");
        
        // DynamoDB LocalStack configuration  
        registry.add("cloud.aws.dynamodb.endpoint", () -> localstack.getEndpointOverride(LocalStackContainer.Service.DYNAMODB).toString());
        
        // SQS LocalStack configuration
        registry.add("cloud.aws.sqs.endpoint", () -> localstack.getEndpointOverride(LocalStackContainer.Service.SQS).toString());
        
        // Test-specific properties
        registry.add("oddiya.testing.enabled", () -> "true");
        registry.add("oddiya.ai.mock.enabled", () -> "true");
        registry.add("oddiya.external-apis.mock.enabled", () -> "true");
        
        log.info("TestContainers configuration applied:");
        log.info("  PostgreSQL: {}", postgres.getJdbcUrl());
        log.info("  LocalStack S3: {}", localstack.getEndpointOverride(LocalStackContainer.Service.S3));
        log.info("  LocalStack DynamoDB: {}", localstack.getEndpointOverride(LocalStackContainer.Service.DYNAMODB));
    }

    /**
     * Start containers before any tests run
     */
    static {
        postgres.start();
        localstack.start();
        
        // Verify containers are healthy
        if (!postgres.isRunning()) {
            throw new RuntimeException("PostgreSQL TestContainer failed to start");
        }
        if (!localstack.isRunning()) {
            throw new RuntimeException("LocalStack TestContainer failed to start");
        }
        
        log.info("TestContainers started successfully:");
        log.info("  PostgreSQL running on port: {}", postgres.getMappedPort(5432));
        log.info("  LocalStack running on port: {}", localstack.getMappedPort(4566));
    }

    /**
     * Get PostgreSQL container for direct access in tests
     */
    @Bean
    @Primary
    public PostgreSQLContainer<?> testPostgreSQLContainer() {
        return postgres;
    }

    /**
     * Get LocalStack container for direct access in tests
     */
    @Bean
    @Primary
    public LocalStackContainer testLocalStackContainer() {
        return localstack;
    }
}