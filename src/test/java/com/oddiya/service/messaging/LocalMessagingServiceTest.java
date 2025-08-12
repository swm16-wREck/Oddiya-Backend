package com.oddiya.service.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.oddiya.dto.message.EmailMessage;
import com.oddiya.dto.message.AnalyticsMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class LocalMessagingServiceTest {

    private LocalMessagingService messagingService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        messagingService = new LocalMessagingService(objectMapper);
    }

    @Test
    @DisplayName("Should send email message successfully")
    void testSendEmailMessage() {
        // Given
        EmailMessage emailMessage = EmailMessage.builder()
            .messageId("test-email-001")
            .templateType(EmailMessage.TemplateTypes.WELCOME)
            .recipientEmail("test@example.com")
            .recipientName("Test User")
            .subject("Welcome to Oddiya")
            .priority(EmailMessage.EmailPriority.NORMAL)
            .userId(123L)
            .build();

        // When
        CompletableFuture<Void> future = messagingService.sendEmailMessage(emailMessage);
        
        // Then
        assertDoesNotThrow(() -> future.get());
        assertTrue(messagingService.queueExists("oddiya-email-notifications"));
        assertEquals(1, messagingService.getMessageCount("oddiya-email-notifications"));
    }

    @Test
    @DisplayName("Should send analytics message successfully")
    void testSendAnalyticsMessage() {
        // Given
        AnalyticsMessage analyticsMessage = AnalyticsMessage.builder()
            .messageId("test-analytics-001")
            .eventId("event-123")
            .eventType(AnalyticsMessage.EventTypes.USER_LOGIN)
            .eventName("user_logged_in")
            .category(AnalyticsMessage.EventCategory.USER_INTERACTION)
            .userId("user-456")
            .sessionId("session-789")
            .priority(AnalyticsMessage.EventPriority.NORMAL)
            .build();

        // When
        CompletableFuture<Void> future = messagingService.sendAnalyticsMessage(analyticsMessage);
        
        // Then
        assertDoesNotThrow(() -> future.get());
        assertTrue(messagingService.queueExists("oddiya-analytics-events"));
        assertEquals(1, messagingService.getMessageCount("oddiya-analytics-events"));
    }

    @Test
    @DisplayName("Should send batch messages successfully")
    void testSendBatchMessages() {
        // Given
        List<EmailMessage> emailMessages = Arrays.asList(
            EmailMessage.builder()
                .messageId("batch-email-001")
                .templateType(EmailMessage.TemplateTypes.WELCOME)
                .recipientEmail("user1@example.com")
                .subject("Welcome 1")
                .priority(EmailMessage.EmailPriority.NORMAL)
                .build(),
            EmailMessage.builder()
                .messageId("batch-email-002")
                .templateType(EmailMessage.TemplateTypes.WELCOME)
                .recipientEmail("user2@example.com")
                .subject("Welcome 2")
                .priority(EmailMessage.EmailPriority.NORMAL)
                .build()
        );

        // When
        CompletableFuture<Void> future = messagingService.sendBatchEmailMessages(emailMessages);
        
        // Then
        assertDoesNotThrow(() -> future.get());
        assertEquals(2, messagingService.getMessageCount("oddiya-email-notifications"));
    }

    @Test
    @DisplayName("Should receive messages successfully")
    void testReceiveMessages() {
        // Given
        String queueName = "test-queue";
        EmailMessage emailMessage = EmailMessage.builder()
            .messageId("receive-test-001")
            .templateType(EmailMessage.TemplateTypes.WELCOME)
            .recipientEmail("receive@example.com")
            .subject("Test Subject")
            .priority(EmailMessage.EmailPriority.NORMAL)
            .build();

        // Send message first
        messagingService.sendMessage(queueName, emailMessage).join();
        
        // When
        List<String> messages = messagingService.receiveMessages(queueName, 5);
        
        // Then
        assertEquals(1, messages.size());
        assertFalse(messages.get(0).isEmpty());
        assertEquals(0, messagingService.getMessageCount(queueName)); // Message consumed
    }

    @Test
    @DisplayName("Should get queue attributes correctly")
    void testGetQueueAttributes() {
        // Given
        String queueName = "oddiya-email-notifications";
        
        // When
        Map<String, String> attributes = messagingService.getQueueAttributes(queueName);
        
        // Then
        assertNotNull(attributes);
        assertFalse(attributes.isEmpty());
        assertTrue(attributes.containsKey("ApproximateNumberOfMessages"));
        assertTrue(attributes.containsKey("QueueArn"));
        assertTrue(attributes.containsKey("VisibilityTimeout"));
    }

    @Test
    @DisplayName("Should get queue statistics correctly")
    void testGetQueueStatistics() {
        // Given
        EmailMessage emailMessage = EmailMessage.builder()
            .messageId("stats-test-001")
            .templateType(EmailMessage.TemplateTypes.WELCOME)
            .recipientEmail("stats@example.com")
            .subject("Stats Test")
            .priority(EmailMessage.EmailPriority.NORMAL)
            .build();

        messagingService.sendEmailMessage(emailMessage).join();
        
        // When
        Map<String, Object> stats = messagingService.getQueueStatistics();
        
        // Then
        assertNotNull(stats);
        assertFalse(stats.isEmpty());
        assertTrue(stats.containsKey("oddiya-email-notifications"));
        
        @SuppressWarnings("unchecked")
        Map<String, Object> emailQueueStats = (Map<String, Object>) stats.get("oddiya-email-notifications");
        assertEquals(1, emailQueueStats.get("messageCount"));
    }

    @Test
    @DisplayName("Should clear queue successfully")
    void testClearQueue() {
        // Given
        String queueName = "test-clear-queue";
        EmailMessage emailMessage = EmailMessage.builder()
            .messageId("clear-test-001")
            .templateType(EmailMessage.TemplateTypes.WELCOME)
            .recipientEmail("clear@example.com")
            .subject("Clear Test")
            .priority(EmailMessage.EmailPriority.NORMAL)
            .build();

        messagingService.sendMessage(queueName, emailMessage).join();
        assertEquals(1, messagingService.getMessageCount(queueName));
        
        // When
        messagingService.clearQueue(queueName);
        
        // Then
        assertEquals(0, messagingService.getMessageCount(queueName));
    }

    @Test
    @DisplayName("Should handle custom message with attributes")
    void testSendMessageWithAttributes() {
        // Given
        String queueName = "test-attributes-queue";
        Object testMessage = Map.of("key", "value", "number", 123);
        Map<String, String> attributes = Map.of(
            "Priority", "HIGH",
            "Source", "TEST"
        );
        
        // When
        CompletableFuture<Void> future = messagingService.sendMessage(queueName, testMessage, attributes);
        
        // Then
        assertDoesNotThrow(() -> future.get());
        assertTrue(messagingService.queueExists(queueName));
        assertEquals(1, messagingService.getMessageCount(queueName));
    }

    @Test
    @DisplayName("Should handle high priority messages correctly")
    void testHighPriorityMessage() {
        // Given
        EmailMessage highPriorityEmail = EmailMessage.builder()
            .messageId("high-priority-001")
            .templateType(EmailMessage.TemplateTypes.PASSWORD_RESET)
            .recipientEmail("urgent@example.com")
            .subject("Password Reset Request")
            .priority(EmailMessage.EmailPriority.URGENT)
            .build();

        // When
        long startTime = System.currentTimeMillis();
        messagingService.sendEmailMessage(highPriorityEmail).join();
        long duration = System.currentTimeMillis() - startTime;
        
        // Then - High priority messages might have slight processing delay
        assertTrue(messagingService.queueExists("oddiya-email-notifications"));
        assertEquals(1, messagingService.getMessageCount("oddiya-email-notifications"));
        // Duration should be at least 100ms due to simulated urgent processing
        assertTrue(duration >= 90); // Allow some variance
    }

    @Test
    @DisplayName("Should return correct queue URL format")
    void testGetQueueUrl() {
        // Given
        String queueName = "test-queue-url";
        
        // When
        String queueUrl = messagingService.getQueueUrl(queueName);
        
        // Then
        assertNotNull(queueUrl);
        assertTrue(queueUrl.startsWith("http://localhost:9324/000000000000/"));
        assertTrue(queueUrl.endsWith(queueName));
    }

    @Test
    @DisplayName("Should handle empty receive correctly")
    void testReceiveFromEmptyQueue() {
        // Given
        String emptyQueueName = "empty-test-queue";
        
        // When
        List<String> messages = messagingService.receiveMessages(emptyQueueName);
        
        // Then
        assertNotNull(messages);
        assertTrue(messages.isEmpty());
        assertEquals(0, messagingService.getMessageCount(emptyQueueName));
    }
}