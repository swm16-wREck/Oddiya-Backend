package com.oddiya.health;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuator.health.Health;
import org.springframework.boot.actuator.health.Status;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SQSHealthIndicatorTest {

    @Mock
    private SqsClient sqsClient;

    private SQSHealthIndicator healthIndicator;

    private final String testQueueUrl = "https://sqs.us-east-1.amazonaws.com/123456789012/test-queue";
    private final String testDlqUrl = "https://sqs.us-east-1.amazonaws.com/123456789012/test-queue-dlq";

    @BeforeEach
    void setUp() {
        healthIndicator = new SQSHealthIndicator(sqsClient, testQueueUrl, testDlqUrl);
    }

    @Test
    void doHealthCheck_QueuesAccessible_ShouldReturnUp() {
        // Arrange
        // Main queue attributes
        GetQueueAttributesResponse mainQueueResponse = GetQueueAttributesResponse.builder()
            .attributes(Map.of(
                QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES, "5",
                QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE, "2",
                QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_DELAYED, "0",
                QueueAttributeName.VISIBILITY_TIMEOUT, "30",
                QueueAttributeName.MESSAGE_RETENTION_PERIOD, "1209600",
                QueueAttributeName.CREATED_TIMESTAMP, "1640995200"
            ))
            .build();
        
        // DLQ attributes
        GetQueueAttributesResponse dlqResponse = GetQueueAttributesResponse.builder()
            .attributes(Map.of(
                QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES, "0",
                QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE, "0",
                QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_DELAYED, "0",
                QueueAttributeName.VISIBILITY_TIMEOUT, "30",
                QueueAttributeName.MESSAGE_RETENTION_PERIOD, "1209600",
                QueueAttributeName.CREATED_TIMESTAMP, "1640995200"
            ))
            .build();

        when(sqsClient.getQueueAttributes(argThat((GetQueueAttributesRequest req) ->
            req.queueUrl().equals(testQueueUrl)))).thenReturn(mainQueueResponse);
        when(sqsClient.getQueueAttributes(argThat((GetQueueAttributesRequest req) ->
            req.queueUrl().equals(testDlqUrl)))).thenReturn(dlqResponse);

        // Act
        Health health = healthIndicator.doHealthCheck();

        // Assert
        assertEquals(Status.UP, health.getStatus());
        
        var details = health.getDetails();
        assertEquals("accessible", details.get("queueStatus"));
        assertEquals(5L, details.get("messagesAvailable"));
        assertEquals(2L, details.get("messagesInFlight"));
        assertEquals(0L, details.get("messagesDelayed"));
        assertEquals(30L, details.get("visibilityTimeout"));
        assertEquals(1209600L, details.get("messageRetentionPeriod"));
        assertTrue(details.containsKey("lastChecked"));
        
        // DLQ details
        assertTrue(details.containsKey("deadLetterQueue"));
        @SuppressWarnings("unchecked")
        var dlqDetails = (Map<String, Object>) details.get("deadLetterQueue");
        assertEquals("accessible", dlqDetails.get("status"));
        assertEquals(0L, dlqDetails.get("messagesAvailable"));
        assertEquals(0L, dlqDetails.get("messagesInFlight"));
        
        verify(sqsClient, times(2)).getQueueAttributes(any(GetQueueAttributesRequest.class));
    }

    @Test
    void doHealthCheck_MessagesInDLQ_ShouldReturnUpWithWarning() {
        // Arrange
        GetQueueAttributesResponse mainQueueResponse = GetQueueAttributesResponse.builder()
            .attributes(Map.of(
                QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES, "3",
                QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE, "1",
                QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_DELAYED, "0"
            ))
            .build();
        
        GetQueueAttributesResponse dlqResponse = GetQueueAttributesResponse.builder()
            .attributes(Map.of(
                QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES, "5", // Messages in DLQ
                QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE, "0",
                QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_DELAYED, "0"
            ))
            .build();

        when(sqsClient.getQueueAttributes(argThat((GetQueueAttributesRequest req) ->
            req.queueUrl().equals(testQueueUrl)))).thenReturn(mainQueueResponse);
        when(sqsClient.getQueueAttributes(argThat((GetQueueAttributesRequest req) ->
            req.queueUrl().equals(testDlqUrl)))).thenReturn(dlqResponse);

        // Act
        Health health = healthIndicator.doHealthCheck();

        // Assert
        assertEquals(Status.UP, health.getStatus());
        
        var details = health.getDetails();
        assertEquals("accessible", details.get("queueStatus"));
        assertTrue(details.containsKey("warning"));
        assertTrue(details.get("warning").toString().contains("messages in dead letter queue"));
        
        @SuppressWarnings("unchecked")
        var dlqDetails = (Map<String, Object>) details.get("deadLetterQueue");
        assertEquals("accessible", dlqDetails.get("status"));
        assertEquals(5L, dlqDetails.get("messagesAvailable"));
    }

    @Test
    void doHealthCheck_MainQueueNotFound_ShouldReturnDown() {
        // Arrange
        when(sqsClient.getQueueAttributes(argThat((GetQueueAttributesRequest req) ->
            req.queueUrl().equals(testQueueUrl))))
            .thenThrow(QueueDoesNotExistException.builder()
                .message("The specified queue does not exist")
                .build());

        // Act
        Health health = healthIndicator.doHealthCheck();

        // Assert
        assertEquals(Status.DOWN, health.getStatus());
        
        var details = health.getDetails();
        assertEquals("not_found", details.get("queueStatus"));
        assertTrue(details.get("error").toString().contains("The specified queue does not exist"));
        assertEquals("QueueDoesNotExist", details.get("errorCode"));
        
        verify(sqsClient).getQueueAttributes(any(GetQueueAttributesRequest.class));
    }

    @Test
    void doHealthCheck_AccessDenied_ShouldReturnDown() {
        // Arrange
        when(sqsClient.getQueueAttributes(argThat((GetQueueAttributesRequest req) ->
            req.queueUrl().equals(testQueueUrl))))
            .thenThrow(SqsException.builder()
                .message("Access to the resource is denied")
                .awsErrorDetails(AwsErrorDetails.builder()
                    .errorCode("AccessDenied")
                    .errorMessage("Access denied")
                    .build())
                .build());

        // Act
        Health health = healthIndicator.doHealthCheck();

        // Assert
        assertEquals(Status.DOWN, health.getStatus());
        
        var details = health.getDetails();
        assertEquals("access_denied", details.get("queueStatus"));
        assertTrue(details.get("error").toString().contains("Access to the resource is denied"));
        assertEquals("AccessDenied", details.get("errorCode"));
    }

    @Test
    void doHealthCheck_MainQueueAccessibleButDLQFails_ShouldReturnUpWithDLQError() {
        // Arrange
        GetQueueAttributesResponse mainQueueResponse = GetQueueAttributesResponse.builder()
            .attributes(Map.of(
                QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES, "2",
                QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE, "0",
                QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_DELAYED, "0"
            ))
            .build();

        when(sqsClient.getQueueAttributes(argThat((GetQueueAttributesRequest req) ->
            req.queueUrl().equals(testQueueUrl)))).thenReturn(mainQueueResponse);
        when(sqsClient.getQueueAttributes(argThat((GetQueueAttributesRequest req) ->
            req.queueUrl().equals(testDlqUrl))))
            .thenThrow(QueueDoesNotExistException.builder()
                .message("DLQ does not exist")
                .build());

        // Act
        Health health = healthIndicator.doHealthCheck();

        // Assert
        assertEquals(Status.UP, health.getStatus()); // Main queue is accessible
        
        var details = health.getDetails();
        assertEquals("accessible", details.get("queueStatus"));
        assertEquals(2L, details.get("messagesAvailable"));
        
        @SuppressWarnings("unchecked")
        var dlqDetails = (Map<String, Object>) details.get("deadLetterQueue");
        assertEquals("error", dlqDetails.get("status"));
        assertTrue(dlqDetails.get("error").toString().contains("DLQ does not exist"));
    }

    @Test
    void doHealthCheck_HighMessageCount_ShouldIncludeWarning() {
        // Arrange
        GetQueueAttributesResponse mainQueueResponse = GetQueueAttributesResponse.builder()
            .attributes(Map.of(
                QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES, "1500", // High message count
                QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE, "50",
                QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_DELAYED, "0"
            ))
            .build();
        
        GetQueueAttributesResponse dlqResponse = GetQueueAttributesResponse.builder()
            .attributes(Map.of(
                QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES, "0",
                QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE, "0",
                QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_DELAYED, "0"
            ))
            .build();

        when(sqsClient.getQueueAttributes(argThat((GetQueueAttributesRequest req) ->
            req.queueUrl().equals(testQueueUrl)))).thenReturn(mainQueueResponse);
        when(sqsClient.getQueueAttributes(argThat((GetQueueAttributesRequest req) ->
            req.queueUrl().equals(testDlqUrl)))).thenReturn(dlqResponse);

        // Act
        Health health = healthIndicator.doHealthCheck();

        // Assert
        assertEquals(Status.UP, health.getStatus());
        
        var details = health.getDetails();
        assertEquals("accessible", details.get("queueStatus"));
        assertEquals(1500L, details.get("messagesAvailable"));
        assertTrue(details.containsKey("warning"));
        assertTrue(details.get("warning").toString().contains("High message count"));
    }

    @Test
    void doHealthCheck_OnlyMainQueue_ShouldReturnUpWithoutDLQ() {
        // Arrange
        SQSHealthIndicator singleQueueIndicator = new SQSHealthIndicator(sqsClient, testQueueUrl, null);
        
        GetQueueAttributesResponse mainQueueResponse = GetQueueAttributesResponse.builder()
            .attributes(Map.of(
                QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES, "10",
                QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE, "3",
                QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_DELAYED, "1",
                QueueAttributeName.VISIBILITY_TIMEOUT, "60",
                QueueAttributeName.MESSAGE_RETENTION_PERIOD, "1209600"
            ))
            .build();

        when(sqsClient.getQueueAttributes(any(GetQueueAttributesRequest.class))).thenReturn(mainQueueResponse);

        // Act
        Health health = singleQueueIndicator.doHealthCheck();

        // Assert
        assertEquals(Status.UP, health.getStatus());
        
        var details = health.getDetails();
        assertEquals("accessible", details.get("queueStatus"));
        assertEquals(10L, details.get("messagesAvailable"));
        assertEquals(3L, details.get("messagesInFlight"));
        assertEquals(1L, details.get("messagesDelayed"));
        assertEquals(60L, details.get("visibilityTimeout"));
        assertFalse(details.containsKey("deadLetterQueue"));
        
        verify(sqsClient, times(1)).getQueueAttributes(any(GetQueueAttributesRequest.class));
    }

    @Test
    void doHealthCheck_NullMainQueueUrl_ShouldReturnDown() {
        // Arrange
        SQSHealthIndicator nullQueueIndicator = new SQSHealthIndicator(sqsClient, null, testDlqUrl);

        // Act
        Health health = nullQueueIndicator.doHealthCheck();

        // Assert
        assertEquals(Status.DOWN, health.getStatus());
        assertTrue(health.getDetails().get("error").toString().contains("Queue URL is not configured"));
        
        verify(sqsClient, never()).getQueueAttributes(any(GetQueueAttributesRequest.class));
    }

    @Test
    void doHealthCheck_EmptyMainQueueUrl_ShouldReturnDown() {
        // Arrange
        SQSHealthIndicator emptyQueueIndicator = new SQSHealthIndicator(sqsClient, "", testDlqUrl);

        // Act
        Health health = emptyQueueIndicator.doHealthCheck();

        // Assert
        assertEquals(Status.DOWN, health.getStatus());
        assertTrue(health.getDetails().get("error").toString().contains("Queue URL is not configured"));
        
        verify(sqsClient, never()).getQueueAttributes(any(GetQueueAttributesRequest.class));
    }

    @Test
    void doHealthCheck_MissingAttributes_ShouldHandleGracefully() {
        // Arrange
        GetQueueAttributesResponse mainQueueResponse = GetQueueAttributesResponse.builder()
            .attributes(Map.of(
                QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES, "7"
                // Missing other attributes
            ))
            .build();
        
        GetQueueAttributesResponse dlqResponse = GetQueueAttributesResponse.builder()
            .attributes(Map.of(
                QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES, "0"
            ))
            .build();

        when(sqsClient.getQueueAttributes(argThat((GetQueueAttributesRequest req) ->
            req.queueUrl().equals(testQueueUrl)))).thenReturn(mainQueueResponse);
        when(sqsClient.getQueueAttributes(argThat((GetQueueAttributesRequest req) ->
            req.queueUrl().equals(testDlqUrl)))).thenReturn(dlqResponse);

        // Act
        Health health = healthIndicator.doHealthCheck();

        // Assert
        assertEquals(Status.UP, health.getStatus());
        
        var details = health.getDetails();
        assertEquals("accessible", details.get("queueStatus"));
        assertEquals(7L, details.get("messagesAvailable"));
        assertEquals(0L, details.get("messagesInFlight")); // Should default to 0
        assertEquals(0L, details.get("messagesDelayed")); // Should default to 0
    }

    @Test
    void doHealthCheck_InvalidAttributeValues_ShouldHandleGracefully() {
        // Arrange
        GetQueueAttributesResponse mainQueueResponse = GetQueueAttributesResponse.builder()
            .attributes(Map.of(
                QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES, "not-a-number",
                QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE, "5",
                QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_DELAYED, "invalid"
            ))
            .build();
        
        GetQueueAttributesResponse dlqResponse = GetQueueAttributesResponse.builder()
            .attributes(Map.of(
                QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES, "0"
            ))
            .build();

        when(sqsClient.getQueueAttributes(argThat((GetQueueAttributesRequest req) ->
            req.queueUrl().equals(testQueueUrl)))).thenReturn(mainQueueResponse);
        when(sqsClient.getQueueAttributes(argThat((GetQueueAttributesRequest req) ->
            req.queueUrl().equals(testDlqUrl)))).thenReturn(dlqResponse);

        // Act
        Health health = healthIndicator.doHealthCheck();

        // Assert
        assertEquals(Status.UP, health.getStatus());
        
        var details = health.getDetails();
        assertEquals("accessible", details.get("queueStatus"));
        assertEquals(0L, details.get("messagesAvailable")); // Should default to 0 for invalid values
        assertEquals(5L, details.get("messagesInFlight")); // Valid value should be used
        assertEquals(0L, details.get("messagesDelayed")); // Should default to 0 for invalid values
    }

    @Test
    void doHealthCheck_NetworkError_ShouldReturnDown() {
        // Arrange
        when(sqsClient.getQueueAttributes(any(GetQueueAttributesRequest.class)))
            .thenThrow(new RuntimeException("Network connection failed"));

        // Act
        Health health = healthIndicator.doHealthCheck();

        // Assert
        assertEquals(Status.DOWN, health.getStatus());
        
        var details = health.getDetails();
        assertEquals("error", details.get("queueStatus"));
        assertTrue(details.get("error").toString().contains("Network connection failed"));
        assertEquals("RuntimeException", details.get("errorType"));
    }

    @Test
    void doHealthCheck_DelayedMessages_ShouldIncludeInDetails() {
        // Arrange
        GetQueueAttributesResponse mainQueueResponse = GetQueueAttributesResponse.builder()
            .attributes(Map.of(
                QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES, "20",
                QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE, "5",
                QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_DELAYED, "8" // Delayed messages
            ))
            .build();
        
        GetQueueAttributesResponse dlqResponse = GetQueueAttributesResponse.builder()
            .attributes(Map.of(
                QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES, "0",
                QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE, "0",
                QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_DELAYED, "0"
            ))
            .build();

        when(sqsClient.getQueueAttributes(argThat((GetQueueAttributesRequest req) ->
            req.queueUrl().equals(testQueueUrl)))).thenReturn(mainQueueResponse);
        when(sqsClient.getQueueAttributes(argThat((GetQueueAttributesRequest req) ->
            req.queueUrl().equals(testDlqUrl)))).thenReturn(dlqResponse);

        // Act
        Health health = healthIndicator.doHealthCheck();

        // Assert
        assertEquals(Status.UP, health.getStatus());
        
        var details = health.getDetails();
        assertEquals("accessible", details.get("queueStatus"));
        assertEquals(20L, details.get("messagesAvailable"));
        assertEquals(5L, details.get("messagesInFlight"));
        assertEquals(8L, details.get("messagesDelayed"));
        assertEquals(33L, details.get("totalMessages")); // 20 + 5 + 8
    }
}