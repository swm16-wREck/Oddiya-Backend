package com.oddiya.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.s3.S3Client;

import java.time.Duration;

/**
 * AWS Configuration for Bedrock, S3, and other AWS services
 * Supports multiple profiles and conditional bean creation
 */
@Slf4j
@Configuration
public class AWSConfiguration {

    @Value("${app.aws.region:ap-northeast-2}")
    private String awsRegion;

    @Value("${app.aws.bedrock.model-id:anthropic.claude-3-sonnet-20240229-v1:0}")
    private String bedrockModelId;

    @Bean
    @ConditionalOnProperty(name = "app.aws.mock.enabled", havingValue = "false", matchIfMissing = true)
    @Profile("!test")
    public BedrockRuntimeClient bedrockRuntimeClient() {
        log.info("Creating BedrockRuntimeClient for region: {}", awsRegion);
        
        try {
            return BedrockRuntimeClient.builder()
                    .region(Region.of(awsRegion))
                    .credentialsProvider(DefaultCredentialsProvider.create())
                    .overrideConfiguration(builder -> builder
                            .apiCallTimeout(Duration.ofMinutes(2))
                            .apiCallAttemptTimeout(Duration.ofSeconds(30)))
                    .build();
        } catch (Exception e) {
            log.error("Failed to create BedrockRuntimeClient: {}", e.getMessage());
            throw new RuntimeException("AWS Bedrock client initialization failed", e);
        }
    }

    // S3Client is configured in AWSConfig.java to avoid bean conflicts

    @Bean
    @ConditionalOnProperty(name = "app.aws.mock.enabled", havingValue = "true")
    @Profile("test")
    public BedrockRuntimeClient mockBedrockClient() {
        log.info("Creating mock BedrockRuntimeClient for testing");
        // In a real implementation, you would return a mock client
        // For now, we'll return null and handle it in the service
        return null;
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}