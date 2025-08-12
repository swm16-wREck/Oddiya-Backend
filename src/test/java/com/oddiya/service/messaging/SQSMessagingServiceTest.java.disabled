package com.oddiya.service.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SQSMessagingServiceTest {

    @Mock
    private SqsClient sqsClient;

    @Mock
    private ObjectMapper objectMapper;

    private SQSMessagingService messagingService;

    private final String testQueueUrl = "https://sqs.us-east-1.amazonaws.com/123456789012/test-queue";
    private final String testDlqUrl = "https://sqs.us-east-1.amazonaws.com/123456789012/test-queue-dlq";

    @BeforeEach
    void setUp() {
        messagingService = new SQSMessagingService(sqsClient, objectMapper);
        // Set private fields for testing
        setPrivateField(messagingService, "defaultQueueUrl", testQueueUrl);
        setPrivateField(messagingService, "dlqUrl", testDlqUrl);
        setPrivateField(messagingService, "maxRetries", 3);
        setPrivateField(messagingService, "defaultDelaySeconds", 0);
        setPrivateField(messagingService, "messageRetentionPeriod", 1209600); // 14 days
    }

    @Test
    void sendMessage_ValidMessage_ShouldSendSuccessfully() throws Exception {
        // Arrange
        String message = "Test message";
        Map<String, Object> attributes = Map.of("priority", "high", "source", "test");
        
        SendMessageResponse mockResponse = SendMessageResponse.builder()
            .messageId("test-message-id-123")
            .md5OfBody("mock-md5")
            .build();
        
        when(objectMapper.writeValueAsString(message)).thenReturn("\"Test message\"");
        when(sqsClient.sendMessage(any(SendMessageRequest.class))).thenReturn(mockResponse);

        // Act
        String result = messagingService.sendMessage(message, attributes);

        // Assert
        assertEquals("test-message-id-123", result);
        verify(sqsClient).sendMessage(any(SendMessageRequest.class));
        verify(objectMapper).writeValueAsString(message);
    }

    @Test
    void sendMessage_SerializationError_ShouldThrowException() throws Exception {
        // Arrange
        String message = "Test message";
        
        when(objectMapper.writeValueAsString(message))
            .thenThrow(new RuntimeException("Serialization failed"));

        // Act & Assert
        MessagingException exception = assertThrows(MessagingException.class, () ->
            messagingService.sendMessage(message, new HashMap<>())
        );
        
        assertEquals("SERIALIZATION_ERROR", exception.getErrorCode());
        verify(sqsClient, never()).sendMessage(any(SendMessageRequest.class));
    }

    @Test
    void sendMessage_SQSError_ShouldThrowMessagingException() throws Exception {
        // Arrange
        String message = "Test message";
        
        when(objectMapper.writeValueAsString(message)).thenReturn("\"Test message\"");
        when(sqsClient.sendMessage(any(SendMessageRequest.class)))
            .thenThrow(SqsException.builder().message("SQS Error").build());

        // Act & Assert
        MessagingException exception = assertThrows(MessagingException.class, () ->
            messagingService.sendMessage(message, new HashMap<>())
        );
        
        assertEquals("SEND_FAILED", exception.getErrorCode());
        verify(sqsClient).sendMessage(any(SendMessageRequest.class));
    }

    @Test
    void sendMessage_WithDelay_ShouldIncludeDelayInRequest() throws Exception {
        // Arrange
        String message = "Delayed message";
        int delaySeconds = 30;
        
        SendMessageResponse mockResponse = SendMessageResponse.builder()
            .messageId("delayed-message-id")
            .build();
        
        when(objectMapper.writeValueAsString(message)).thenReturn("\"Delayed message\"");
        when(sqsClient.sendMessage(any(SendMessageRequest.class))).thenReturn(mockResponse);

        // Act
        String result = messagingService.sendMessage(message, delaySeconds, new HashMap<>());

        // Assert
        assertEquals("delayed-message-id", result);
        verify(sqsClient).sendMessage(argThat(request -> 
            request.delaySeconds() == delaySeconds
        ));
    }

    @Test
    void sendMessageAsync_ValidMessage_ShouldReturnCompletableFuture() throws Exception {
        // Arrange
        String message = "Async message";
        Map<String, Object> attributes = Map.of("async", "true");
        
        SendMessageResponse mockResponse = SendMessageResponse.builder()
            .messageId("async-message-id")
            .build();
        
        when(objectMapper.writeValueAsString(message)).thenReturn("\"Async message\"");
        when(sqsClient.sendMessage(any(SendMessageRequest.class))).thenReturn(mockResponse);

        // Act
        CompletableFuture<String> future = messagingService.sendMessageAsync(message, attributes);
        String result = future.get();

        // Assert
        assertEquals("async-message-id", result);
        verify(sqsClient).sendMessage(any(SendMessageRequest.class));
    }

    @Test
    void sendBatchMessages_ValidMessages_ShouldSendBatch() throws Exception {
        // Arrange
        List<Object> messages = Arrays.asList("Message 1", "Message 2", "Message 3");
        
        SendMessageBatchResponse mockResponse = SendMessageBatchResponse.builder()
            .successful(
                SendMessageBatchResultEntry.builder()
                    .id("msg1")
                    .messageId("id1")
                    .build(),
                SendMessageBatchResultEntry.builder()
                    .id("msg2")
                    .messageId("id2")
                    .build(),
                SendMessageBatchResultEntry.builder()
                    .id("msg3")
                    .messageId("id3")
                    .build()
            )
            .failed(Collections.emptyList())
            .build();
        
        when(objectMapper.writeValueAsString("Message 1")).thenReturn("\"Message 1\"");
        when(objectMapper.writeValueAsString("Message 2")).thenReturn("\"Message 2\"");
        when(objectMapper.writeValueAsString("Message 3")).thenReturn("\"Message 3\"");
        when(sqsClient.sendMessageBatch(any(SendMessageBatchRequest.class))).thenReturn(mockResponse);

        // Act
        Map<String, String> results = messagingService.sendBatchMessages(messages, new HashMap<>());

        // Assert
        assertEquals(3, results.size());
        assertEquals("id1", results.get("msg1"));
        assertEquals("id2", results.get("msg2"));
        assertEquals("id3", results.get("msg3"));
        verify(sqsClient).sendMessageBatch(any(SendMessageBatchRequest.class));
    }

    @Test
    void sendBatchMessages_SomeFailures_ShouldReturnPartialResults() throws Exception {
        // Arrange
        List<Object> messages = Arrays.asList("Message 1", "Message 2");
        
        SendMessageBatchResponse mockResponse = SendMessageBatchResponse.builder()
            .successful(
                SendMessageBatchResultEntry.builder()
                    .id("msg1")
                    .messageId("id1")
                    .build()
            )
            .failed(
                BatchResultErrorEntry.builder()
                    .id("msg2")
                    .code("InvalidMessage")
                    .message("Invalid message format")
                    .build()
            )
            .build();
        
        when(objectMapper.writeValueAsString("Message 1")).thenReturn("\"Message 1\"");
        when(objectMapper.writeValueAsString("Message 2")).thenReturn("\"Message 2\"");
        when(sqsClient.sendMessageBatch(any(SendMessageBatchRequest.class))).thenReturn(mockResponse);

        // Act
        Map<String, String> results = messagingService.sendBatchMessages(messages, new HashMap<>());

        // Assert
        assertEquals(1, results.size());
        assertEquals("id1", results.get("msg1"));
        assertNull(results.get("msg2"));
        verify(sqsClient).sendMessageBatch(any(SendMessageBatchRequest.class));
    }

    @Test
    void receiveMessages_HasMessages_ShouldReturnMessages() throws Exception {
        // Arrange
        int maxMessages = 5;
        int waitTimeSeconds = 10;
        
        Message message1 = Message.builder()
            .messageId("msg1")
            .receiptHandle("receipt1")
            .body("\"Message 1 content\"")
            .attributes(Map.of(
                MessageSystemAttributeName.SENT_TIMESTAMP, "1640995200000"
            ))
            .build();
        
        Message message2 = Message.builder()
            .messageId("msg2")
            .receiptHandle("receipt2")
            .body("\"Message 2 content\"")
            .build();
        
        ReceiveMessageResponse mockResponse = ReceiveMessageResponse.builder()
            .messages(Arrays.asList(message1, message2))
            .build();
        
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(mockResponse);
        when(objectMapper.readValue("\"Message 1 content\"", Object.class)).thenReturn("Message 1 content");
        when(objectMapper.readValue("\"Message 2 content\"", Object.class)).thenReturn("Message 2 content");

        // Act
        List<MessageWrapper> results = messagingService.receiveMessages(maxMessages, waitTimeSeconds);

        // Assert
        assertEquals(2, results.size());
        
        MessageWrapper wrapper1 = results.get(0);
        assertEquals("msg1", wrapper1.getMessageId());
        assertEquals("receipt1", wrapper1.getReceiptHandle());
        assertEquals("Message 1 content", wrapper1.getBody());
        assertNotNull(wrapper1.getSentTimestamp());
        
        MessageWrapper wrapper2 = results.get(1);
        assertEquals("msg2", wrapper2.getMessageId());
        assertEquals("receipt2", wrapper2.getReceiptHandle());
        assertEquals("Message 2 content", wrapper2.getBody());
        
        verify(sqsClient).receiveMessage(any(ReceiveMessageRequest.class));
    }

    @Test
    void receiveMessages_EmptyQueue_ShouldReturnEmptyList() {
        // Arrange
        ReceiveMessageResponse mockResponse = ReceiveMessageResponse.builder()
            .messages(Collections.emptyList())
            .build();
        
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(mockResponse);

        // Act
        List<MessageWrapper> results = messagingService.receiveMessages(5, 10);

        // Assert
        assertTrue(results.isEmpty());
        verify(sqsClient).receiveMessage(any(ReceiveMessageRequest.class));
    }

    @Test
    void receiveMessages_DeserializationError_ShouldSkipInvalidMessages() throws Exception {
        // Arrange
        Message validMessage = Message.builder()
            .messageId("valid")
            .receiptHandle("receipt1")
            .body("\"Valid message\"")
            .build();
        
        Message invalidMessage = Message.builder()
            .messageId("invalid")
            .receiptHandle("receipt2")
            .body("{ invalid json")
            .build();
        
        ReceiveMessageResponse mockResponse = ReceiveMessageResponse.builder()
            .messages(Arrays.asList(validMessage, invalidMessage))
            .build();
        
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(mockResponse);
        when(objectMapper.readValue("\"Valid message\"", Object.class)).thenReturn("Valid message");
        when(objectMapper.readValue("{ invalid json", Object.class))
            .thenThrow(new RuntimeException("Invalid JSON"));

        // Act
        List<MessageWrapper> results = messagingService.receiveMessages(5, 10);

        // Assert
        assertEquals(1, results.size());
        assertEquals("valid", results.get(0).getMessageId());
        assertEquals("Valid message", results.get(0).getBody());
    }

    @Test
    void deleteMessage_ValidReceipt_ShouldDeleteSuccessfully() {
        // Arrange
        String receiptHandle = "receipt-handle-123";
        
        DeleteMessageResponse mockResponse = DeleteMessageResponse.builder().build();
        when(sqsClient.deleteMessage(any(DeleteMessageRequest.class))).thenReturn(mockResponse);

        // Act
        boolean result = messagingService.deleteMessage(receiptHandle);

        // Assert
        assertTrue(result);
        verify(sqsClient).deleteMessage(argThat(request -> 
            request.receiptHandle().equals(receiptHandle) &&
            request.queueUrl().equals(testQueueUrl)
        ));
    }

    @Test
    void deleteMessage_SQSError_ShouldReturnFalse() {
        // Arrange
        String receiptHandle = "invalid-receipt";
        
        when(sqsClient.deleteMessage(any(DeleteMessageRequest.class)))
            .thenThrow(SqsException.builder().message("Invalid receipt handle").build());

        // Act
        boolean result = messagingService.deleteMessage(receiptHandle);

        // Assert
        assertFalse(result);
        verify(sqsClient).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void deleteBatchMessages_ValidReceipts_ShouldDeleteBatch() {
        // Arrange
        List<String> receiptHandles = Arrays.asList("receipt1", "receipt2", "receipt3");
        
        DeleteMessageBatchResponse mockResponse = DeleteMessageBatchResponse.builder()
            .successful(
                DeleteMessageBatchResultEntry.builder().id("msg1").build(),
                DeleteMessageBatchResultEntry.builder().id("msg2").build(),
                DeleteMessageBatchResultEntry.builder().id("msg3").build()
            )
            .failed(Collections.emptyList())
            .build();
        
        when(sqsClient.deleteMessageBatch(any(DeleteMessageBatchRequest.class))).thenReturn(mockResponse);

        // Act
        Map<String, Boolean> results = messagingService.deleteBatchMessages(receiptHandles);

        // Assert
        assertEquals(3, results.size());
        assertTrue(results.get("msg1"));
        assertTrue(results.get("msg2"));
        assertTrue(results.get("msg3"));
        verify(sqsClient).deleteMessageBatch(any(DeleteMessageBatchRequest.class));
    }

    @Test
    void deleteBatchMessages_SomeFailures_ShouldReturnPartialResults() {
        // Arrange
        List<String> receiptHandles = Arrays.asList("receipt1", "receipt2");
        
        DeleteMessageBatchResponse mockResponse = DeleteMessageBatchResponse.builder()
            .successful(
                DeleteMessageBatchResultEntry.builder().id("msg1").build()
            )
            .failed(
                BatchResultErrorEntry.builder()
                    .id("msg2")
                    .code("ReceiptHandleIsInvalid")
                    .message("Invalid receipt handle")
                    .build()
            )
            .build();
        
        when(sqsClient.deleteMessageBatch(any(DeleteMessageBatchRequest.class))).thenReturn(mockResponse);

        // Act
        Map<String, Boolean> results = messagingService.deleteBatchMessages(receiptHandles);

        // Assert
        assertEquals(2, results.size());
        assertTrue(results.get("msg1"));
        assertFalse(results.get("msg2"));
        verify(sqsClient).deleteMessageBatch(any(DeleteMessageBatchRequest.class));
    }

    @Test
    void getQueueAttributes_ValidQueue_ShouldReturnAttributes() {
        // Arrange
        GetQueueAttributesResponse mockResponse = GetQueueAttributesResponse.builder()
            .attributes(Map.of(
                QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES, "5",
                QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE, "2",
                QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_DELAYED, "0",
                QueueAttributeName.CREATED_TIMESTAMP, "1640995200"
            ))
            .build();
        
        when(sqsClient.getQueueAttributes(any(GetQueueAttributesRequest.class))).thenReturn(mockResponse);

        // Act
        Map<QueueAttributeName, String> results = messagingService.getQueueAttributes();

        // Assert
        assertEquals(4, results.size());
        assertEquals("5", results.get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES));
        assertEquals("2", results.get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE));
        assertEquals("0", results.get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_DELAYED));
        assertEquals("1640995200", results.get(QueueAttributeName.CREATED_TIMESTAMP));
        verify(sqsClient).getQueueAttributes(any(GetQueueAttributesRequest.class));
    }

    @Test
    void purgeQueue_ValidQueue_ShouldPurgeSuccessfully() {
        // Arrange
        PurgeQueueResponse mockResponse = PurgeQueueResponse.builder().build();
        when(sqsClient.purgeQueue(any(PurgeQueueRequest.class))).thenReturn(mockResponse);

        // Act
        boolean result = messagingService.purgeQueue();

        // Assert
        assertTrue(result);
        verify(sqsClient).purgeQueue(argThat(request -> 
            request.queueUrl().equals(testQueueUrl)
        ));
    }

    @Test
    void purgeQueue_SQSError_ShouldReturnFalse() {
        // Arrange
        when(sqsClient.purgeQueue(any(PurgeQueueRequest.class)))
            .thenThrow(SqsException.builder().message("Purge failed").build());

        // Act
        boolean result = messagingService.purgeQueue();

        // Assert
        assertFalse(result);
        verify(sqsClient).purgeQueue(any(PurgeQueueRequest.class));
    }

    @Test
    void sendToDeadLetterQueue_ValidMessage_ShouldSendToDLQ() throws Exception {
        // Arrange
        String originalMessage = "Failed message";
        String errorReason = "Processing failed after 3 retries";
        Map<String, Object> originalAttributes = Map.of("source", "test");
        
        SendMessageResponse mockResponse = SendMessageResponse.builder()
            .messageId("dlq-message-id")
            .build();
        
        when(objectMapper.writeValueAsString(originalMessage)).thenReturn("\"Failed message\"");
        when(sqsClient.sendMessage(any(SendMessageRequest.class))).thenReturn(mockResponse);

        // Act
        String result = messagingService.sendToDeadLetterQueue(originalMessage, errorReason, originalAttributes);

        // Assert
        assertEquals("dlq-message-id", result);
        verify(sqsClient).sendMessage(argThat(request -> 
            request.queueUrl().equals(testDlqUrl) &&
            request.messageAttributes().containsKey("error_reason") &&
            request.messageAttributes().containsKey("failed_at") &&
            request.messageAttributes().containsKey("original_source")
        ));
    }

    @Test
    void changeMessageVisibility_ValidParameters_ShouldChangeVisibility() {
        // Arrange
        String receiptHandle = "receipt-123";
        int visibilityTimeoutSeconds = 300;
        
        ChangeMessageVisibilityResponse mockResponse = ChangeMessageVisibilityResponse.builder().build();
        when(sqsClient.changeMessageVisibility(any(ChangeMessageVisibilityRequest.class)))
            .thenReturn(mockResponse);

        // Act
        boolean result = messagingService.changeMessageVisibility(receiptHandle, visibilityTimeoutSeconds);

        // Assert
        assertTrue(result);
        verify(sqsClient).changeMessageVisibility(argThat(request -> 
            request.receiptHandle().equals(receiptHandle) &&
            request.visibilityTimeout() == visibilityTimeoutSeconds &&
            request.queueUrl().equals(testQueueUrl)
        ));
    }

    @Test
    void getQueueSize_ValidQueue_ShouldReturnSize() {
        // Arrange
        GetQueueAttributesResponse mockResponse = GetQueueAttributesResponse.builder()
            .attributes(Map.of(
                QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES, "42",
                QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE, "8"
            ))
            .build();
        
        when(sqsClient.getQueueAttributes(any(GetQueueAttributesRequest.class))).thenReturn(mockResponse);

        // Act
        long result = messagingService.getQueueSize();

        // Assert
        assertEquals(42L, result);
        verify(sqsClient).getQueueAttributes(any(GetQueueAttributesRequest.class));
    }

    @Test
    void getInFlightMessageCount_ValidQueue_ShouldReturnCount() {
        // Arrange
        GetQueueAttributesResponse mockResponse = GetQueueAttributesResponse.builder()
            .attributes(Map.of(
                QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES, "42",
                QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE, "15"
            ))
            .build();
        
        when(sqsClient.getQueueAttributes(any(GetQueueAttributesRequest.class))).thenReturn(mockResponse);

        // Act
        long result = messagingService.getInFlightMessageCount();

        // Assert
        assertEquals(15L, result);
        verify(sqsClient).getQueueAttributes(any(GetQueueAttributesRequest.class));
    }

    // Helper method to set private fields using reflection
    private void setPrivateField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set private field: " + fieldName, e);
        }
    }
}