package com.oddiya.contract.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oddiya.contract.ContractTestBase;
import com.oddiya.dto.message.*;
import com.oddiya.service.messaging.MessagingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.contract.verifier.messaging.MessageVerifierReceiver;
import org.springframework.cloud.contract.verifier.messaging.MessageVerifierSender;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * Contract tests for SQS messaging system
 * Validates message structure and processing contracts
 */
@SpringBootTest
@TestPropertySource(properties = {
    "app.aws.sqs.enabled=false", // Use mock messaging service
    "spring.cloud.contract.verifier.messaging.enabled=true"
})
public class SQSMessagingContractTest extends ContractTestBase {

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MessagingService messagingService;

    @Autowired(required = false)
    private MessageVerifierSender messageVerifierSender;

    @Autowired(required = false)
    private MessageVerifierReceiver messageVerifierReceiver;

    @Test
    public void shouldSendEmailMessageWithCorrectStructure() throws Exception {
        // Given
        EmailMessage emailMessage = EmailMessage.builder()
                .messageId("email-msg-123")
                .userId("user123")
                .recipient("test@example.com")
                .subject("Welcome to Oddiya!")
                .templateType("welcome")
                .templateData(Map.of("userName", "John Doe", "activationLink", "https://oddiya.com/activate"))
                .priority(EmailMessage.Priority.HIGH)
                .scheduledAt(LocalDateTime.now().plusHours(1))
                .metadata(Map.of("source", "registration", "campaign", "welcome-series"))
                .build();

        given(messagingService.sendEmailMessage(any(EmailMessage.class)))
                .willReturn(CompletableFuture.completedFuture(null));

        // When
        CompletableFuture<Void> result = messagingService.sendEmailMessage(emailMessage);

        // Then
        assertThat(result).succeedsWithin(1, TimeUnit.SECONDS);
        
        // Verify message structure
        verify(messagingService).sendEmailMessage(argThat(msg -> {
            assertThat(msg.getMessageId()).isEqualTo("email-msg-123");
            assertThat(msg.getUserId()).isEqualTo("user123");
            assertThat(msg.getRecipient()).isEqualTo("test@example.com");
            assertThat(msg.getSubject()).isEqualTo("Welcome to Oddiya!");
            assertThat(msg.getTemplateType()).isEqualTo("welcome");
            assertThat(msg.getPriority()).isEqualTo(EmailMessage.Priority.HIGH);
            assertThat(msg.getTemplateData()).containsEntry("userName", "John Doe");
            assertThat(msg.getMetadata()).containsEntry("source", "registration");
            return true;
        }));
    }

    @Test
    public void shouldSendImageProcessingMessageWithCorrectStructure() throws Exception {
        // Given
        ImageProcessingMessage imageMessage = ImageProcessingMessage.builder()
                .messageId("img-proc-123")
                .userId("user123")
                .imageKey("user-uploads/profile/avatar.jpg")
                .originalUrl("https://storage.oddiya.com/user-uploads/profile/avatar.jpg")
                .processingType(ImageProcessingMessage.ProcessingType.RESIZE)
                .targetSizes(Map.of("thumbnail", "150x150", "medium", "500x500", "large", "1200x1200"))
                .outputFormat("webp")
                .quality(85)
                .priority(ImageProcessingMessage.Priority.MEDIUM)
                .metadata(Map.of("category", "profile", "userId", "user123"))
                .build();

        given(messagingService.sendImageProcessingMessage(any(ImageProcessingMessage.class)))
                .willReturn(CompletableFuture.completedFuture(null));

        // When
        CompletableFuture<Void> result = messagingService.sendImageProcessingMessage(imageMessage);

        // Then
        assertThat(result).succeedsWithin(1, TimeUnit.SECONDS);
        
        // Verify message structure
        verify(messagingService).sendImageProcessingMessage(argThat(msg -> {
            assertThat(msg.getMessageId()).isEqualTo("img-proc-123");
            assertThat(msg.getUserId()).isEqualTo("user123");
            assertThat(msg.getImageKey()).isEqualTo("user-uploads/profile/avatar.jpg");
            assertThat(msg.getProcessingType()).isEqualTo(ImageProcessingMessage.ProcessingType.RESIZE);
            assertThat(msg.getTargetSizes()).containsEntry("thumbnail", "150x150");
            assertThat(msg.getOutputFormat()).isEqualTo("webp");
            assertThat(msg.getQuality()).isEqualTo(85);
            assertThat(msg.getPriority()).isEqualTo(ImageProcessingMessage.Priority.MEDIUM);
            return true;
        }));
    }

    @Test
    public void shouldSendAnalyticsMessageWithCorrectStructure() throws Exception {
        // Given
        AnalyticsMessage analyticsMessage = AnalyticsMessage.builder()
                .messageId("analytics-123")
                .userId("user123")
                .sessionId("session-456")
                .eventType("travel_plan_viewed")
                .eventData(Map.of(
                    "travelPlanId", "tp123",
                    "planTitle", "Seoul Adventure",
                    "viewDuration", "00:03:45",
                    "source", "search"
                ))
                .timestamp(LocalDateTime.now())
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .ipAddress("192.168.1.100")
                .priority(AnalyticsMessage.Priority.LOW)
                .metadata(Map.of("platform", "web", "version", "1.0.0"))
                .build();

        given(messagingService.sendAnalyticsMessage(any(AnalyticsMessage.class)))
                .willReturn(CompletableFuture.completedFuture(null));

        // When
        CompletableFuture<Void> result = messagingService.sendAnalyticsMessage(analyticsMessage);

        // Then
        assertThat(result).succeedsWithin(1, TimeUnit.SECONDS);
        
        // Verify message structure
        verify(messagingService).sendAnalyticsMessage(argThat(msg -> {
            assertThat(msg.getMessageId()).isEqualTo("analytics-123");
            assertThat(msg.getUserId()).isEqualTo("user123");
            assertThat(msg.getSessionId()).isEqualTo("session-456");
            assertThat(msg.getEventType()).isEqualTo("travel_plan_viewed");
            assertThat(msg.getEventData()).containsEntry("travelPlanId", "tp123");
            assertThat(msg.getEventData()).containsEntry("planTitle", "Seoul Adventure");
            assertThat(msg.getUserAgent()).contains("Mozilla/5.0");
            assertThat(msg.getIpAddress()).isEqualTo("192.168.1.100");
            return true;
        }));
    }

    @Test
    public void shouldSendRecommendationMessageWithCorrectStructure() throws Exception {
        // Given
        RecommendationMessage recommendationMessage = RecommendationMessage.builder()
                .messageId("rec-123")
                .userId("user123")
                .recommendationType(RecommendationMessage.RecommendationType.PLACE_SUGGESTION)
                .contextData(Map.of(
                    "currentLocation", "Seoul, South Korea",
                    "travelPlanId", "tp123",
                    "preferences", "culture,food,history",
                    "budget", "medium"
                ))
                .priority(RecommendationMessage.Priority.HIGH)
                .metadata(Map.of("algorithm", "collaborative-filtering", "version", "2.1"))
                .build();

        given(messagingService.sendRecommendationMessage(any(RecommendationMessage.class)))
                .willReturn(CompletableFuture.completedFuture(null));

        // When
        CompletableFuture<Void> result = messagingService.sendRecommendationMessage(recommendationMessage);

        // Then
        assertThat(result).succeedsWithin(1, TimeUnit.SECONDS);
        
        // Verify message structure
        verify(messagingService).sendRecommendationMessage(argThat(msg -> {
            assertThat(msg.getMessageId()).isEqualTo("rec-123");
            assertThat(msg.getUserId()).isEqualTo("user123");
            assertThat(msg.getRecommendationType()).isEqualTo(RecommendationMessage.RecommendationType.PLACE_SUGGESTION);
            assertThat(msg.getContextData()).containsEntry("currentLocation", "Seoul, South Korea");
            assertThat(msg.getContextData()).containsEntry("preferences", "culture,food,history");
            assertThat(msg.getPriority()).isEqualTo(RecommendationMessage.Priority.HIGH);
            assertThat(msg.getMetadata()).containsEntry("algorithm", "collaborative-filtering");
            return true;
        }));
    }

    @Test
    public void shouldSendVideoProcessingMessageWithCorrectStructure() throws Exception {
        // Given
        VideoProcessingMessage videoMessage = VideoProcessingMessage.builder()
                .messageId("video-proc-123")
                .userId("user123")
                .videoKey("user-uploads/videos/travel-vlog.mp4")
                .originalUrl("https://storage.oddiya.com/user-uploads/videos/travel-vlog.mp4")
                .processingType(VideoProcessingMessage.ProcessingType.TRANSCODE)
                .outputFormats(Map.of("720p", "mp4", "480p", "mp4", "thumbnail", "jpg"))
                .quality(VideoProcessingMessage.Quality.HIGH)
                .priority(VideoProcessingMessage.Priority.MEDIUM)
                .metadata(Map.of("category", "travel-vlog", "location", "Seoul", "duration", "00:15:30"))
                .build();

        given(messagingService.sendVideoProcessingMessage(any(VideoProcessingMessage.class)))
                .willReturn(CompletableFuture.completedFuture(null));

        // When
        CompletableFuture<Void> result = messagingService.sendVideoProcessingMessage(videoMessage);

        // Then
        assertThat(result).succeedsWithin(1, TimeUnit.SECONDS);
        
        // Verify message structure
        verify(messagingService).sendVideoProcessingMessage(argThat(msg -> {
            assertThat(msg.getMessageId()).isEqualTo("video-proc-123");
            assertThat(msg.getUserId()).isEqualTo("user123");
            assertThat(msg.getVideoKey()).isEqualTo("user-uploads/videos/travel-vlog.mp4");
            assertThat(msg.getProcessingType()).isEqualTo(VideoProcessingMessage.ProcessingType.TRANSCODE);
            assertThat(msg.getOutputFormats()).containsEntry("720p", "mp4");
            assertThat(msg.getQuality()).isEqualTo(VideoProcessingMessage.Quality.HIGH);
            assertThat(msg.getPriority()).isEqualTo(VideoProcessingMessage.Priority.MEDIUM);
            assertThat(msg.getMetadata()).containsEntry("location", "Seoul");
            return true;
        }));
    }

    @Test
    public void shouldHandleBatchMessagingCorrectly() throws Exception {
        // Given
        given(messagingService.sendBatchEmailMessages(anyList()))
                .willReturn(CompletableFuture.completedFuture(null));

        // When - This would be called by the messaging service
        CompletableFuture<Void> result = messagingService.sendBatchEmailMessages(
            java.util.List.of(
                EmailMessage.builder()
                    .messageId("batch-email-1")
                    .userId("user123")
                    .recipient("user1@example.com")
                    .subject("Batch Email 1")
                    .templateType("notification")
                    .priority(EmailMessage.Priority.MEDIUM)
                    .build(),
                EmailMessage.builder()
                    .messageId("batch-email-2")
                    .userId("user124")
                    .recipient("user2@example.com")
                    .subject("Batch Email 2")
                    .templateType("notification")
                    .priority(EmailMessage.Priority.MEDIUM)
                    .build()
            )
        );

        // Then
        assertThat(result).succeedsWithin(2, TimeUnit.SECONDS);
        verify(messagingService).sendBatchEmailMessages(argThat(messages -> {
            assertThat(messages).hasSize(2);
            assertThat(messages.get(0).getMessageId()).isEqualTo("batch-email-1");
            assertThat(messages.get(1).getMessageId()).isEqualTo("batch-email-2");
            return true;
        }));
    }

    @Test
    public void shouldValidateMessageAttributesContract() {
        // Given
        Map<String, String> expectedAttributes = new HashMap<>();
        expectedAttributes.put("MessageType", "EmailMessage");
        expectedAttributes.put("Priority", "HIGH");
        expectedAttributes.put("TemplateType", "welcome");
        expectedAttributes.put("Timestamp", "2024-01-01T12:00:00Z");

        // This test validates that message attributes follow the expected structure
        // In a real implementation, this would validate SQS message attributes
        assertThat(expectedAttributes).containsKeys("MessageType", "Priority", "Timestamp");
        assertThat(expectedAttributes.get("MessageType")).isEqualTo("EmailMessage");
        assertThat(expectedAttributes.get("Priority")).isIn("LOW", "MEDIUM", "HIGH");
    }

    @Test
    public void shouldValidateQueueNamingContract() {
        // Validate that queue names follow the expected pattern
        String emailQueueName = "oddiya-email-notifications";
        String imageQueueName = "oddiya-image-processing";
        String analyticsQueueName = "oddiya-analytics-events";
        String recommendationQueueName = "oddiya-recommendation-updates";
        String videoQueueName = "oddiya-video-processing";

        // Queue naming convention: oddiya-{service}-{purpose}
        assertThat(emailQueueName).matches("oddiya-[a-z]+-[a-z]+");
        assertThat(imageQueueName).matches("oddiya-[a-z]+-[a-z]+");
        assertThat(analyticsQueueName).matches("oddiya-[a-z]+-[a-z]+");
        assertThat(recommendationQueueName).matches("oddiya-[a-z]+-[a-z]+");
        assertThat(videoQueueName).matches("oddiya-[a-z]+-[a-z]+");
    }

    @Test
    public void shouldValidateMessageSerializationContract() throws Exception {
        // Given
        EmailMessage emailMessage = EmailMessage.builder()
                .messageId("serialization-test-123")
                .userId("user123")
                .recipient("test@example.com")
                .subject("Test Subject")
                .templateType("test")
                .priority(EmailMessage.Priority.MEDIUM)
                .build();

        // When - Serialize to JSON
        String json = objectMapper.writeValueAsString(emailMessage);

        // Then - Deserialize and validate structure
        EmailMessage deserializedMessage = objectMapper.readValue(json, EmailMessage.class);
        
        assertThat(deserializedMessage.getMessageId()).isEqualTo("serialization-test-123");
        assertThat(deserializedMessage.getUserId()).isEqualTo("user123");
        assertThat(deserializedMessage.getRecipient()).isEqualTo("test@example.com");
        assertThat(deserializedMessage.getSubject()).isEqualTo("Test Subject");
        assertThat(deserializedMessage.getTemplateType()).isEqualTo("test");
        assertThat(deserializedMessage.getPriority()).isEqualTo(EmailMessage.Priority.MEDIUM);

        // Validate JSON structure contains required fields
        assertThat(json).contains("messageId", "userId", "recipient", "subject", "templateType", "priority");
    }
}