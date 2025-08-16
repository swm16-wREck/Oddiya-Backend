package com.oddiya.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;

/**
 * Test configuration for AWS services.
 * Provides mock AWS beans for testing without requiring actual AWS credentials.
 */
@TestConfiguration
@Profile("test")
@Slf4j
public class TestAWSConfig {

    @Bean
    @Primary
    public Region awsRegion() {
        log.info("Using test AWS Region: ap-northeast-2");
        return Region.of("ap-northeast-2");
    }

    @Bean
    @Primary
    public AwsCredentialsProvider awsCredentialsProvider() {
        log.info("Using test AWS credentials");
        return StaticCredentialsProvider.create(
            AwsBasicCredentials.create("test-access-key", "test-secret-key")
        );
    }
}