package com.oddiya.health;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuator.health.Health;
import org.springframework.boot.actuator.health.Status;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.time.Instant;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class S3HealthIndicatorTest {

    @Mock
    private S3Client s3Client;

    private S3HealthIndicator healthIndicator;

    private final String testBucketName = "test-bucket";

    @BeforeEach
    void setUp() {
        healthIndicator = new S3HealthIndicator(s3Client, testBucketName);
    }

    @Test
    void doHealthCheck_BucketAccessible_ShouldReturnUp() {
        // Arrange
        HeadBucketResponse headResponse = HeadBucketResponse.builder().build();
        
        ListObjectsV2Response listResponse = ListObjectsV2Response.builder()
            .contents(Arrays.asList(
                S3Object.builder().key("file1.jpg").size(1024L).lastModified(Instant.now()).build(),
                S3Object.builder().key("file2.pdf").size(2048L).lastModified(Instant.now()).build(),
                S3Object.builder().key("file3.txt").size(512L).lastModified(Instant.now()).build()
            ))
            .isTruncated(false)
            .build();
        
        GetBucketLocationResponse locationResponse = GetBucketLocationResponse.builder()
            .locationConstraint(BucketLocationConstraint.US_EAST_1)
            .build();

        when(s3Client.headBucket(any(HeadBucketRequest.class))).thenReturn(headResponse);
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(listResponse);
        when(s3Client.getBucketLocation(any(GetBucketLocationRequest.class))).thenReturn(locationResponse);

        // Act
        Health health = healthIndicator.doHealthCheck();

        // Assert
        assertEquals(Status.UP, health.getStatus());
        
        var details = health.getDetails();
        assertEquals(testBucketName, details.get("bucket"));
        assertEquals("US_EAST_1", details.get("region"));
        assertEquals(3L, details.get("objectCount"));
        assertEquals(3584L, details.get("totalSize")); // 1024 + 2048 + 512
        assertEquals("accessible", details.get("bucketStatus"));
        assertFalse((Boolean) details.get("isTruncated"));
        assertTrue(details.containsKey("lastChecked"));
        
        verify(s3Client).headBucket(any(HeadBucketRequest.class));
        verify(s3Client).listObjectsV2(any(ListObjectsV2Request.class));
        verify(s3Client).getBucketLocation(any(GetBucketLocationRequest.class));
    }

    @Test
    void doHealthCheck_EmptyBucket_ShouldReturnUp() {
        // Arrange
        HeadBucketResponse headResponse = HeadBucketResponse.builder().build();
        
        ListObjectsV2Response listResponse = ListObjectsV2Response.builder()
            .contents(Arrays.asList()) // Empty bucket
            .isTruncated(false)
            .build();
        
        GetBucketLocationResponse locationResponse = GetBucketLocationResponse.builder()
            .locationConstraint(BucketLocationConstraint.AP_NORTHEAST_2)
            .build();

        when(s3Client.headBucket(any(HeadBucketRequest.class))).thenReturn(headResponse);
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(listResponse);
        when(s3Client.getBucketLocation(any(GetBucketLocationRequest.class))).thenReturn(locationResponse);

        // Act
        Health health = healthIndicator.doHealthCheck();

        // Assert
        assertEquals(Status.UP, health.getStatus());
        
        var details = health.getDetails();
        assertEquals(testBucketName, details.get("bucket"));
        assertEquals("AP_NORTHEAST_2", details.get("region"));
        assertEquals(0L, details.get("objectCount"));
        assertEquals(0L, details.get("totalSize"));
        assertEquals("accessible", details.get("bucketStatus"));
        assertFalse((Boolean) details.get("isTruncated"));
    }

    @Test
    void doHealthCheck_BucketNotFound_ShouldReturnDown() {
        // Arrange
        when(s3Client.headBucket(any(HeadBucketRequest.class)))
            .thenThrow(NoSuchBucketException.builder()
                .message("The specified bucket does not exist")
                .build());

        // Act
        Health health = healthIndicator.doHealthCheck();

        // Assert
        assertEquals(Status.DOWN, health.getStatus());
        
        var details = health.getDetails();
        assertEquals(testBucketName, details.get("bucket"));
        assertEquals("not_found", details.get("bucketStatus"));
        assertTrue(details.get("error").toString().contains("The specified bucket does not exist"));
        assertEquals("NoSuchBucket", details.get("errorCode"));
        
        verify(s3Client).headBucket(any(HeadBucketRequest.class));
        verify(s3Client, never()).listObjectsV2(any(ListObjectsV2Request.class));
        verify(s3Client, never()).getBucketLocation(any(GetBucketLocationRequest.class));
    }

    @Test
    void doHealthCheck_AccessDenied_ShouldReturnDown() {
        // Arrange
        when(s3Client.headBucket(any(HeadBucketRequest.class)))
            .thenThrow(S3Exception.builder()
                .message("Access Denied")
                .awsErrorDetails(AwsErrorDetails.builder()
                    .errorCode("AccessDenied")
                    .errorMessage("Access Denied")
                    .build())
                .build());

        // Act
        Health health = healthIndicator.doHealthCheck();

        // Assert
        assertEquals(Status.DOWN, health.getStatus());
        
        var details = health.getDetails();
        assertEquals(testBucketName, details.get("bucket"));
        assertEquals("access_denied", details.get("bucketStatus"));
        assertTrue(details.get("error").toString().contains("Access Denied"));
        assertEquals("AccessDenied", details.get("errorCode"));
    }

    @Test
    void doHealthCheck_HeadBucketSuccessButListFails_ShouldReturnDown() {
        // Arrange
        HeadBucketResponse headResponse = HeadBucketResponse.builder().build();
        
        when(s3Client.headBucket(any(HeadBucketRequest.class))).thenReturn(headResponse);
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
            .thenThrow(S3Exception.builder()
                .message("List operation failed")
                .awsErrorDetails(AwsErrorDetails.builder()
                    .errorCode("InternalError")
                    .errorMessage("List operation failed")
                    .build())
                .build());

        // Act
        Health health = healthIndicator.doHealthCheck();

        // Assert
        assertEquals(Status.DOWN, health.getStatus());
        
        var details = health.getDetails();
        assertEquals(testBucketName, details.get("bucket"));
        assertTrue(details.get("error").toString().contains("List operation failed"));
        assertEquals("InternalError", details.get("errorCode"));
        
        verify(s3Client).headBucket(any(HeadBucketRequest.class));
        verify(s3Client).listObjectsV2(any(ListObjectsV2Request.class));
        verify(s3Client, never()).getBucketLocation(any(GetBucketLocationRequest.class));
    }

    @Test
    void doHealthCheck_GetBucketLocationFails_ShouldStillReturnUp() {
        // Arrange
        HeadBucketResponse headResponse = HeadBucketResponse.builder().build();
        
        ListObjectsV2Response listResponse = ListObjectsV2Response.builder()
            .contents(Arrays.asList(
                S3Object.builder().key("test.txt").size(100L).lastModified(Instant.now()).build()
            ))
            .isTruncated(false)
            .build();

        when(s3Client.headBucket(any(HeadBucketRequest.class))).thenReturn(headResponse);
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(listResponse);
        when(s3Client.getBucketLocation(any(GetBucketLocationRequest.class)))
            .thenThrow(S3Exception.builder()
                .message("GetBucketLocation failed")
                .build());

        // Act
        Health health = healthIndicator.doHealthCheck();

        // Assert
        assertEquals(Status.UP, health.getStatus());
        
        var details = health.getDetails();
        assertEquals(testBucketName, details.get("bucket"));
        assertEquals("unknown", details.get("region")); // Should default to unknown when location fails
        assertEquals(1L, details.get("objectCount"));
        assertEquals(100L, details.get("totalSize"));
        assertEquals("accessible", details.get("bucketStatus"));
        assertTrue(details.containsKey("regionError"));
        
        verify(s3Client).headBucket(any(HeadBucketRequest.class));
        verify(s3Client).listObjectsV2(any(ListObjectsV2Request.class));
        verify(s3Client).getBucketLocation(any(GetBucketLocationRequest.class));
    }

    @Test
    void doHealthCheck_TruncatedResults_ShouldIndicateTruncation() {
        // Arrange
        HeadBucketResponse headResponse = HeadBucketResponse.builder().build();
        
        ListObjectsV2Response listResponse = ListObjectsV2Response.builder()
            .contents(Arrays.asList(
                S3Object.builder().key("file1.txt").size(1000L).lastModified(Instant.now()).build(),
                S3Object.builder().key("file2.txt").size(2000L).lastModified(Instant.now()).build()
            ))
            .isTruncated(true) // Results are truncated
            .nextContinuationToken("next-token")
            .build();
        
        GetBucketLocationResponse locationResponse = GetBucketLocationResponse.builder()
            .locationConstraint(BucketLocationConstraint.EU_WEST_1)
            .build();

        when(s3Client.headBucket(any(HeadBucketRequest.class))).thenReturn(headResponse);
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(listResponse);
        when(s3Client.getBucketLocation(any(GetBucketLocationRequest.class))).thenReturn(locationResponse);

        // Act
        Health health = healthIndicator.doHealthCheck();

        // Assert
        assertEquals(Status.UP, health.getStatus());
        
        var details = health.getDetails();
        assertEquals(testBucketName, details.get("bucket"));
        assertEquals("EU_WEST_1", details.get("region"));
        assertEquals(2L, details.get("objectCount"));
        assertEquals(3000L, details.get("totalSize"));
        assertTrue((Boolean) details.get("isTruncated"));
        assertTrue(details.containsKey("note"));
        assertTrue(details.get("note").toString().contains("truncated"));
    }

    @Test
    void doHealthCheck_LargeObjects_ShouldCalculateCorrectSizes() {
        // Arrange
        HeadBucketResponse headResponse = HeadBucketResponse.builder().build();
        
        ListObjectsV2Response listResponse = ListObjectsV2Response.builder()
            .contents(Arrays.asList(
                S3Object.builder().key("small.txt").size(1024L).lastModified(Instant.now()).build(),
                S3Object.builder().key("medium.jpg").size(1048576L).lastModified(Instant.now()).build(), // 1MB
                S3Object.builder().key("large.mp4").size(1073741824L).lastModified(Instant.now()).build() // 1GB
            ))
            .isTruncated(false)
            .build();
        
        GetBucketLocationResponse locationResponse = GetBucketLocationResponse.builder()
            .locationConstraint(BucketLocationConstraint.US_WEST_2)
            .build();

        when(s3Client.headBucket(any(HeadBucketRequest.class))).thenReturn(headResponse);
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(listResponse);
        when(s3Client.getBucketLocation(any(GetBucketLocationRequest.class))).thenReturn(locationResponse);

        // Act
        Health health = healthIndicator.doHealthCheck();

        // Assert
        assertEquals(Status.UP, health.getStatus());
        
        var details = health.getDetails();
        assertEquals(3L, details.get("objectCount"));
        assertEquals(1074791424L, details.get("totalSize")); // 1024 + 1048576 + 1073741824
        assertTrue(details.containsKey("sizeFormatted"));
        
        // Verify the formatted size makes sense (should be around 1.0 GB)
        String sizeFormatted = (String) details.get("sizeFormatted");
        assertTrue(sizeFormatted.contains("GB") || sizeFormatted.contains("MB"));
    }

    @Test
    void doHealthCheck_ZeroSizeObjects_ShouldHandleCorrectly() {
        // Arrange
        HeadBucketResponse headResponse = HeadBucketResponse.builder().build();
        
        ListObjectsV2Response listResponse = ListObjectsV2Response.builder()
            .contents(Arrays.asList(
                S3Object.builder().key("empty1.txt").size(0L).lastModified(Instant.now()).build(),
                S3Object.builder().key("empty2.log").size(0L).lastModified(Instant.now()).build(),
                S3Object.builder().key("normal.txt").size(100L).lastModified(Instant.now()).build()
            ))
            .isTruncated(false)
            .build();
        
        GetBucketLocationResponse locationResponse = GetBucketLocationResponse.builder()
            .locationConstraint(BucketLocationConstraint.AP_SOUTHEAST_1)
            .build();

        when(s3Client.headBucket(any(HeadBucketRequest.class))).thenReturn(headResponse);
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(listResponse);
        when(s3Client.getBucketLocation(any(GetBucketLocationRequest.class))).thenReturn(locationResponse);

        // Act
        Health health = healthIndicator.doHealthCheck();

        // Assert
        assertEquals(Status.UP, health.getStatus());
        
        var details = health.getDetails();
        assertEquals(3L, details.get("objectCount"));
        assertEquals(100L, details.get("totalSize")); // Only the non-empty file counts
    }

    @Test
    void doHealthCheck_NullBucketName_ShouldReturnDown() {
        // Arrange
        S3HealthIndicator nullBucketIndicator = new S3HealthIndicator(s3Client, null);

        // Act
        Health health = nullBucketIndicator.doHealthCheck();

        // Assert
        assertEquals(Status.DOWN, health.getStatus());
        assertTrue(health.getDetails().get("error").toString().contains("Bucket name is not configured"));
        
        verify(s3Client, never()).headBucket(any(HeadBucketRequest.class));
    }

    @Test
    void doHealthCheck_EmptyBucketName_ShouldReturnDown() {
        // Arrange
        S3HealthIndicator emptyBucketIndicator = new S3HealthIndicator(s3Client, "");

        // Act
        Health health = emptyBucketIndicator.doHealthCheck();

        // Assert
        assertEquals(Status.DOWN, health.getStatus());
        assertTrue(health.getDetails().get("error").toString().contains("Bucket name is not configured"));
        
        verify(s3Client, never()).headBucket(any(HeadBucketRequest.class));
    }

    @Test
    void doHealthCheck_UnknownS3Exception_ShouldReturnDownWithGenericError() {
        // Arrange
        when(s3Client.headBucket(any(HeadBucketRequest.class)))
            .thenThrow(S3Exception.builder()
                .message("Unknown S3 error")
                .awsErrorDetails(AwsErrorDetails.builder()
                    .errorCode("UnknownError")
                    .errorMessage("Something went wrong")
                    .build())
                .build());

        // Act
        Health health = healthIndicator.doHealthCheck();

        // Assert
        assertEquals(Status.DOWN, health.getStatus());
        
        var details = health.getDetails();
        assertEquals("error", details.get("bucketStatus"));
        assertTrue(details.get("error").toString().contains("Unknown S3 error"));
        assertEquals("UnknownError", details.get("errorCode"));
    }

    @Test
    void doHealthCheck_NonS3Exception_ShouldReturnDownWithGenericHandling() {
        // Arrange
        when(s3Client.headBucket(any(HeadBucketRequest.class)))
            .thenThrow(new RuntimeException("Network timeout"));

        // Act
        Health health = healthIndicator.doHealthCheck();

        // Assert
        assertEquals(Status.DOWN, health.getStatus());
        
        var details = health.getDetails();
        assertEquals("error", details.get("bucketStatus"));
        assertTrue(details.get("error").toString().contains("Network timeout"));
        assertEquals("RuntimeException", details.get("errorType"));
    }
}