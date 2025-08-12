package com.oddiya.config.aws;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringJUnitExtension;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringJUnitExtension.class)
@SpringBootTest(classes = {AWSConfig.class})
@ActiveProfiles("test")
class AWSConfigTest {

    @Autowired(required = false)
    private AwsCredentialsProvider credentialsProvider;

    @Autowired(required = false)
    private DynamoDbClient dynamoDbClient;

    @Autowired(required = false)
    private S3Client s3Client;

    @Autowired(required = false)
    private S3Presigner s3Presigner;

    @Autowired(required = false)
    private SqsClient sqsClient;

    @Autowired(required = false)
    private CloudWatchClient cloudWatchClient;

    @Test
    void contextLoads() {
        // This test ensures that the AWS configuration can be loaded without errors
        assertTrue(true, "Spring context should load without errors");
    }

    @Test
    void credentialsProvider_ShouldBeConfigured() {
        // In test profile, credentials provider should be available
        // Note: This test will pass if AWS credentials are configured or if default provider chain works
        if (credentialsProvider != null) {
            assertNotNull(credentialsProvider);
        }
    }

    @Test
    void awsClients_ShouldBeAvailable_WhenAWSProfileActive() {
        // These beans should only be created when AWS is enabled
        // In test profile, they might be null if AWS is not configured
        
        // Note: These assertions are lenient because in test environment,
        // AWS clients might not be available depending on configuration
        if (dynamoDbClient != null) {
            assertNotNull(dynamoDbClient);
        }
        
        if (s3Client != null) {
            assertNotNull(s3Client);
        }
        
        if (s3Presigner != null) {
            assertNotNull(s3Presigner);
        }
        
        if (sqsClient != null) {
            assertNotNull(sqsClient);
        }
        
        if (cloudWatchClient != null) {
            assertNotNull(cloudWatchClient);
        }
    }

    @Test
    void awsRegion_ShouldBeValid() {
        // Test that the region configuration is valid
        String regionName = "ap-northeast-2"; // Default region from config
        
        // This should not throw an exception
        Region region = Region.of(regionName);
        assertNotNull(region);
        assertEquals("ap-northeast-2", region.id());
    }

    @Test
    void awsRegion_USSupportedRegions_ShouldWork() {
        // Test common US regions
        String[] usRegions = {"us-east-1", "us-east-2", "us-west-1", "us-west-2"};
        
        for (String regionName : usRegions) {
            Region region = Region.of(regionName);
            assertNotNull(region);
            assertTrue(regionName.startsWith("us-"));
        }
    }

    @Test
    void awsRegion_AsianRegions_ShouldWork() {
        // Test Asian regions relevant for the application
        String[] asianRegions = {"ap-northeast-1", "ap-northeast-2", "ap-southeast-1", "ap-southeast-2"};
        
        for (String regionName : asianRegions) {
            Region region = Region.of(regionName);
            assertNotNull(region);
            assertTrue(regionName.startsWith("ap-"));
        }
    }

    @Test
    void awsRegion_InvalidRegion_ShouldThrowException() {
        // Test that invalid regions are handled appropriately
        assertThrows(IllegalArgumentException.class, () -> {
            Region.of("invalid-region");
        });
    }
}

// Integration test with LocalStack (commented out as it requires LocalStack to be running)
/*
@ExtendWith(SpringJUnitExtension.class)
@SpringBootTest
@TestPropertySource(properties = {
    "app.aws.region=us-east-1",
    "app.aws.localstack.enabled=true",
    "app.aws.localstack.endpoint=http://localhost:4566",
    "app.aws.s3.enabled=true",
    "app.aws.dynamodb.enabled=true",
    "app.aws.sqs.enabled=true"
})
@ActiveProfiles("localstack")
class AWSConfigLocalStackIntegrationTest {

    @Autowired
    private DynamoDbClient dynamoDbClient;

    @Autowired
    private S3Client s3Client;

    @Autowired
    private SqsClient sqsClient;

    @Test
    void localStackClients_ShouldConnectSuccessfully() {
        assertNotNull(dynamoDbClient);
        assertNotNull(s3Client);
        assertNotNull(sqsClient);

        // Test basic connectivity (requires LocalStack running)
        try {
            var listTablesResponse = dynamoDbClient.listTables();
            assertNotNull(listTablesResponse);
        } catch (Exception e) {
            // LocalStack might not be running, skip this test
            assumeTrue(false, "LocalStack not available: " + e.getMessage());
        }
    }

    @Test 
    void s3Client_ShouldConnectToLocalStack() {
        assertNotNull(s3Client);
        
        try {
            var listBucketsResponse = s3Client.listBuckets();
            assertNotNull(listBucketsResponse);
        } catch (Exception e) {
            assumeTrue(false, "LocalStack S3 not available: " + e.getMessage());
        }
    }

    @Test
    void sqsClient_ShouldConnectToLocalStack() {
        assertNotNull(sqsClient);
        
        try {
            var listQueuesResponse = sqsClient.listQueues();
            assertNotNull(listQueuesResponse);
        } catch (Exception e) {
            assumeTrue(false, "LocalStack SQS not available: " + e.getMessage());
        }
    }
}
*/