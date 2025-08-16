package com.oddiya.config.aws;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sqs.SqsClient;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.time.Duration;

@Configuration
@ConfigurationProperties(prefix = "app.aws")
@Profile("!test")  // Exclude from test profile to avoid bean conflicts
@Data
@Slf4j
public class AWSConfig {
    
    private String region = "ap-northeast-2";
    private String accessKey;
    private String secretKey;
    
    private S3Properties s3 = new S3Properties();
    private SQSProperties sqs = new SQSProperties();
    private CloudWatchProperties cloudwatch = new CloudWatchProperties();

    @Bean
    public Region awsRegion() {
        log.info("Configuring AWS Region: {}", region);
        return Region.of(region);
    }

    @Bean
    public AwsCredentialsProvider awsCredentialsProvider() {
        if (accessKey != null && !accessKey.trim().isEmpty() && 
            secretKey != null && !secretKey.trim().isEmpty()) {
            log.info("Using static AWS credentials");
            return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKey, secretKey)
            );
        } else {
            log.info("Using default AWS credentials provider chain");
            return DefaultCredentialsProvider.create();
        }
    }

    @Bean
    @ConditionalOnProperty(name = "app.aws.s3.enabled", havingValue = "true", matchIfMissing = true)
    public S3Client s3Client(Region region, AwsCredentialsProvider credentialsProvider) {
        log.info("Creating S3 Client for region: {}", region);
        return S3Client.builder()
                .region(region)
                .credentialsProvider(credentialsProvider)
                .build();
    }


    @Bean
    @ConditionalOnProperty(name = "app.aws.sqs.enabled", havingValue = "true", matchIfMissing = true)
    public SqsClient sqsClient(Region region, AwsCredentialsProvider credentialsProvider) {
        log.info("Creating SQS Client for region: {}", region);
        return SqsClient.builder()
                .region(region)
                .credentialsProvider(credentialsProvider)
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "app.aws.cloudwatch.enabled", havingValue = "true", matchIfMissing = true)
    public CloudWatchClient cloudWatchClient(Region region, AwsCredentialsProvider credentialsProvider) {
        log.info("Creating CloudWatch Client for region: {}", region);
        return CloudWatchClient.builder()
                .region(region)
                .credentialsProvider(credentialsProvider)
                .build();
    }
    
    @Bean
    @ConditionalOnProperty(name = "app.alerting.sns.enabled", havingValue = "true")
    public software.amazon.awssdk.services.sns.SnsClient snsClient(Region region, AwsCredentialsProvider credentialsProvider) {
        log.info("Creating SNS client for region: {}", region);
        
        return software.amazon.awssdk.services.sns.SnsClient.builder()
                .region(region)
                .credentialsProvider(credentialsProvider)
                .build();
    }
    
    @Bean
    @ConditionalOnProperty(name = "app.cost-monitoring.aws.enabled", havingValue = "true")
    public software.amazon.awssdk.services.costexplorer.CostExplorerClient costExplorerClient(AwsCredentialsProvider credentialsProvider) {
        log.info("Creating Cost Explorer client for us-east-1 (Cost Explorer only available in us-east-1)");
        
        return software.amazon.awssdk.services.costexplorer.CostExplorerClient.builder()
                .region(Region.US_EAST_1) // Cost Explorer is only available in us-east-1
                .credentialsProvider(credentialsProvider)
                .build();
    }
    
    @Bean
    @ConditionalOnProperty(name = "app.cost-monitoring.aws.enabled", havingValue = "true")
    public software.amazon.awssdk.services.budgets.BudgetsClient budgetsClient(AwsCredentialsProvider credentialsProvider) {
        log.info("Creating Budgets client for us-east-1");
        
        return software.amazon.awssdk.services.budgets.BudgetsClient.builder()
                .region(Region.US_EAST_1) // Budgets is only available in us-east-1
                .credentialsProvider(credentialsProvider)
                .build();
    }

    @Data
    public static class S3Properties {
        private boolean enabled = true;
        private String bucket;
        private String region;
        private int presignedUrlExpiration = 3600; // 1 hour in seconds
        private long multipartThreshold = 16 * 1024 * 1024; // 16MB
        private int connectionTimeout = 30000; // 30 seconds
        private int socketTimeout = 30000; // 30 seconds
    }


    @Data
    public static class SQSProperties {
        private boolean enabled = true;
        private String queueUrl;
        private int maxMessages = 10;
        private int visibilityTimeoutSeconds = 300; // 5 minutes
        private int waitTimeSeconds = 20; // Long polling
        private int maxReceiveCount = 3;
        private String deadLetterQueueUrl;
    }

    @Data
    public static class CloudWatchProperties {
        private boolean enabled = true;
        private String namespace = "Oddiya";
        private int batchSize = 20;
        private Duration flushInterval = Duration.ofSeconds(60);
        private boolean enableDetailedMetrics = false;
    }
}

/**
 * Mock AWS configuration for local development and testing
 */
@Configuration
@Profile({"local", "test"})
@Slf4j
class MockAWSConfig {

    @Bean
    @ConditionalOnProperty(name = "app.aws.mock.enabled", havingValue = "true", matchIfMissing = true)
    public Region mockAwsRegion() {  // Renamed to avoid conflict
        log.info("Using mock AWS Region: ap-northeast-2");
        return Region.of("ap-northeast-2");
    }

    @Bean
    @ConditionalOnProperty(name = "app.aws.mock.enabled", havingValue = "true", matchIfMissing = true)
    public AwsCredentialsProvider mockAwsCredentialsProvider() {
        log.info("Using mock AWS credentials");
        return StaticCredentialsProvider.create(
            AwsBasicCredentials.create("mock-access-key", "mock-secret-key")
        );
    }
}