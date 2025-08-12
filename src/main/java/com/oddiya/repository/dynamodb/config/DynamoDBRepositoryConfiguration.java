package com.oddiya.repository.dynamodb.config;

import com.oddiya.converter.DynamoDBConverters;
import com.oddiya.repository.dynamodb.*;
import com.oddiya.repository.dynamodb.enhanced.DynamoDBRepositoryEnhancements;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/**
 * Configuration class for DynamoDB repositories with proper bean management
 * and enhanced functionality integration.
 */
@Configuration
@ConditionalOnProperty(name = "spring.profiles.active", havingValue = "dynamodb")
@Slf4j
public class DynamoDBRepositoryConfiguration {

    @Bean
    @Primary
    public DynamoDBUserRepository dynamoDBUserRepository(
            DynamoDbEnhancedClient enhancedClient,
            DynamoDbClient client,
            DynamoDBConverters converters,
            DynamoDBRepositoryEnhancements enhancements) {
        
        log.info("Creating DynamoDBUserRepository bean");
        return new DynamoDBUserRepository(enhancedClient, client, converters, "oddiya_users");
    }

    @Bean
    @Primary
    public DynamoDBTravelPlanRepository dynamoDBTravelPlanRepository(
            DynamoDbEnhancedClient enhancedClient,
            DynamoDbClient client,
            DynamoDBConverters converters,
            DynamoDBRepositoryEnhancements enhancements) {
        
        log.info("Creating DynamoDBTravelPlanRepository bean");
        return new DynamoDBTravelPlanRepository(enhancedClient, client, converters, "oddiya_travel_plans");
    }

    @Bean
    @Primary
    public DynamoDBPlaceRepository dynamoDBPlaceRepository(
            DynamoDbEnhancedClient enhancedClient,
            DynamoDbClient client,
            DynamoDBConverters converters,
            DynamoDBRepositoryEnhancements enhancements) {
        
        log.info("Creating DynamoDBPlaceRepository bean");
        return new DynamoDBPlaceRepository(enhancedClient, client, converters, "oddiya_places");
    }

    @Bean
    @Primary
    public DynamoDBSavedPlanRepository dynamoDBSavedPlanRepository(
            DynamoDbEnhancedClient enhancedClient,
            DynamoDbClient client,
            DynamoDBConverters converters,
            DynamoDBRepositoryEnhancements enhancements) {
        
        log.info("Creating DynamoDBSavedPlanRepository bean");
        return new DynamoDBSavedPlanRepository(enhancedClient, client, converters, "oddiya_saved_plans");
    }

    @Bean
    public DynamoDBRepositoryEnhancements dynamoDBRepositoryEnhancements() {
        log.info("Creating DynamoDBRepositoryEnhancements bean");
        return new DynamoDBRepositoryEnhancements();
    }

    /**
     * Repository performance monitoring and metrics collection
     */
    @Bean
    public DynamoDBRepositoryMetrics dynamoDBRepositoryMetrics() {
        return new DynamoDBRepositoryMetrics();
    }
}