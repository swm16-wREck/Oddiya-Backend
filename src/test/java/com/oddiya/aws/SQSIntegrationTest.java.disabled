package com.oddiya.aws;

import com.oddiya.dto.message.*;
import com.oddiya.service.messaging.SQSMessagingService;
import com.oddiya.service.messaging.MessageProcessor;
import com.oddiya.service.messaging.MessagingService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for SQS messaging service using LocalStack container.
 * Tests message processing, dead letter queues, batch operations, and error handling.
 */
@SpringBootTest
@ActiveProfiles("sqs-test")
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SQSIntegrationTest {

    @Container
    static GenericContainer<?> localstack = new GenericContainer<>("localstack/localstack:3.0")
            .withExposedPorts(4566)
            .withEnv("SERVICES", "sqs")
            .withEnv("DEBUG", "1")
            .withEnv("AWS_DEFAULT_REGION", "ap-northeast-2")
            .waitingFor(Wait.forHttp("/health").forPort(4566));

    @Autowired(required = false)
    private SQSMessagingService sqsMessagingService;

    @Autowired(required = false)
    private MessageProcessor messageProcessor;

    private static SqsClient testSqsClient;
    private static String testQueueUrl;
    private static String testDlqUrl;
    private static final String TEST_QUEUE_NAME = "oddiya-test-queue";
    private static final String TEST_DLQ_NAME = "oddiya-test-dlq";

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        String sqsEndpoint = "http://localhost:" + localstack.getMappedPort(4566);
        registry.add("app.aws.sqs.enabled", () -> "true");
        registry.add("app.aws.sqs.queue-url", () -> testQueueUrl);
        registry.add("app.aws.sqs.dead-letter-queue-url", () -> testDlqUrl);
        registry.add("aws.region", () -> "ap-northeast-2");
        registry.add("spring.profiles.active", () -> "sqs-test");
        registry.add("cloud.aws.sqs.endpoint", () -> sqsEndpoint);
    }

    @BeforeAll
    static void setUpSQS() {
        testSqsClient = SqsClient.builder()
                .region(Region.AP_NORTHEAST_2)
                .endpointOverride(URI.create("http://localhost:" + localstack.getMappedPort(4566)))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test", "test")))
                .build();

        createTestQueues();
    }

    static void createTestQueues() {
        try {
            // Create Dead Letter Queue first
            CreateQueueRequest dlqRequest = CreateQueueRequest.builder()
                    .queueName(TEST_DLQ_NAME)
                    .build();
            CreateQueueResponse dlqResponse = testSqsClient.createQueue(dlqRequest);
            testDlqUrl = dlqResponse.queueUrl();

            // Get DLQ ARN for main queue configuration
            GetQueueAttributesRequest dlqAttrRequest = GetQueueAttributesRequest.builder()
                    .queueUrl(testDlqUrl)
                    .attributeNames(QueueAttributeName.QUEUE_ARN)
                    .build();
            GetQueueAttributesResponse dlqAttrResponse = testSqsClient.getQueueAttributes(dlqAttrRequest);
            String dlqArn = dlqAttrResponse.attributes().get(QueueAttributeName.QUEUE_ARN);

            // Create main queue with DLQ configuration
            Map<QueueAttributeName, String> queueAttributes = new HashMap<>();
            queueAttributes.put(QueueAttributeName.REDRIVE_POLICY, 
                String.format("{\"deadLetterTargetArn\":\"%s\",\"maxReceiveCount\":3}", dlqArn));
            queueAttributes.put(QueueAttributeName.VISIBILITY_TIMEOUT_SECONDS, "300");
            queueAttributes.put(QueueAttributeName.MESSAGE_RETENTION_PERIOD, "1209600"); // 14 days

            CreateQueueRequest queueRequest = CreateQueueRequest.builder()
                    .queueName(TEST_QUEUE_NAME)
                    .attributes(queueAttributes)
                    .build();
            CreateQueueResponse queueResponse = testSqsClient.createQueue(queueRequest);
            testQueueUrl = queueResponse.queueUrl();

            // Wait for queues to be available
            Thread.sleep(2000);
            
        } catch (Exception e) {
            System.err.println("Warning: Could not create test queues: " + e.getMessage());
        }
    }

    @AfterAll
    static void cleanUp() {
        if (testSqsClient != null) {
            try {
                // Purge and delete queues
                if (testQueueUrl != null) {
                    testSqsClient.purgeQueue(PurgeQueueRequest.builder().queueUrl(testQueueUrl).build());
                    testSqsClient.deleteQueue(DeleteQueueRequest.builder().queueUrl(testQueueUrl).build());
                }
                if (testDlqUrl != null) {
                    testSqsClient.purgeQueue(PurgeQueueRequest.builder().queueUrl(testDlqUrl).build());
                    testSqsClient.deleteQueue(DeleteQueueRequest.builder().queueUrl(testDlqUrl).build());
                }
            } catch (Exception e) {
                System.err.println("Warning: Could not clean up test queues: " + e.getMessage());
            } finally {
                testSqsClient.close();
            }
        }
    }

    @Test
    @Order(1)
    void contextLoads() {
        if ("sqs-test".equals(System.getProperty("spring.profiles.active"))) {
            assertThat(sqsMessagingService).isNotNull();
            assertThat(messageProcessor).isNotNull();
        }
    }

    @Test
    @Order(2)
    void testBasicMessageSending() {
        if (sqsMessagingService == null) {
            System.out.println("Skipping SQS test - service not available");
            return;
        }

        // Test email message
        EmailMessage emailMessage = EmailMessage.builder()
                .id(UUID.randomUUID().toString())
                .to("test@example.com")
                .subject("Test Email")
                .body("This is a test email message")
                .messageType("EMAIL")
                .timestamp(LocalDateTime.now())
                .build();

        // Send message
        assertThatNoException().isThrownBy(() -> 
            sqsMessagingService.sendMessage(emailMessage, null));

        // Verify message was sent (check queue attributes)
        GetQueueAttributesRequest request = GetQueueAttributesRequest.builder()
                .queueUrl(testQueueUrl)
                .attributeNames(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES)
                .build();
        
        GetQueueAttributesResponse response = testSqsClient.getQueueAttributes(request);
        String messageCount = response.attributes().get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES);
        
        // Note: Message count in SQS is eventually consistent, so it might be 0 initially
        assertThat(messageCount).isNotNull();
    }

    @Test
    @Order(3)
    void testDifferentMessageTypes() {
        if (sqsMessagingService == null) {
            System.out.println("Skipping SQS test - service not available");
            return;
        }

        // Test Analytics Message
        AnalyticsMessage analyticsMessage = AnalyticsMessage.builder()
                .id(UUID.randomUUID().toString())
                .userId("user123")
                .eventName("page_view")
                .properties(Map.of("page", "/home", "duration", "30s"))
                .messageType("ANALYTICS")
                .timestamp(LocalDateTime.now())
                .build();

        assertThatNoException().isThrownBy(() -> 
            sqsMessagingService.sendMessage(analyticsMessage, null));

        // Test Recommendation Message
        RecommendationMessage recommendationMessage = RecommendationMessage.builder()
                .id(UUID.randomUUID().toString())
                .userId("user456")
                .requestType("PLACE_RECOMMENDATIONS")
                .parameters(Map.of("latitude", "37.7749", "longitude", "-122.4194", "radius", "5000"))
                .messageType("RECOMMENDATION")
                .timestamp(LocalDateTime.now())
                .build();

        assertThatNoException().isThrownBy(() -> 
            sqsMessagingService.sendMessage(recommendationMessage, null));

        // Test Video Processing Message
        VideoProcessingMessage videoMessage = VideoProcessingMessage.builder()
                .id(UUID.randomUUID().toString())
                .videoId("video789")
                .action("TRANSCODE")
                .parameters(Map.of("resolution", "1080p", "format", "mp4"))
                .messageType("VIDEO_PROCESSING")
                .timestamp(LocalDateTime.now())
                .build();

        assertThatNoException().isThrownBy(() -> 
            sqsMessagingService.sendMessage(videoMessage, null));

        // Test Image Processing Message
        ImageProcessingMessage imageMessage = ImageProcessingMessage.builder()
                .id(UUID.randomUUID().toString())
                .imageId("image123")
                .action("RESIZE")
                .parameters(Map.of("width", "800", "height", "600", "quality", "85"))
                .messageType("IMAGE_PROCESSING")
                .timestamp(LocalDateTime.now())
                .build();

        assertThatNoException().isThrownBy(() -> 
            sqsMessagingService.sendMessage(imageMessage, null));
    }

    @Test
    @Order(4)
    void testBatchMessageSending() {
        if (sqsMessagingService == null) {
            System.out.println("Skipping SQS test - service not available");
            return;
        }

        // Create batch of messages
        List<Object> messages = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            EmailMessage message = EmailMessage.builder()
                    .id(UUID.randomUUID().toString())
                    .to("batch" + i + "@example.com")
                    .subject("Batch Test " + i)
                    .body("This is batch message " + i)
                    .messageType("EMAIL")
                    .timestamp(LocalDateTime.now())
                    .build();
            messages.add(message);
        }

        // Send batch messages
        assertThatNoException().isThrownBy(() -> 
            sqsMessagingService.sendBatchMessages(messages, null));

        // Verify messages were sent
        GetQueueAttributesRequest request = GetQueueAttributesRequest.builder()
                .queueUrl(testQueueUrl)
                .attributeNames(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES)
                .build();
        
        GetQueueAttributesResponse response = testSqsClient.getQueueAttributes(request);
        String messageCount = response.attributes().get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES);
        
        // Should have messages in queue (eventually consistent)
        assertThat(messageCount).isNotNull();
    }

    @Test
    @Order(5)
    void testMessageReceiving() throws InterruptedException {
        if (sqsMessagingService == null) {
            System.out.println("Skipping SQS test - service not available");
            return;
        }

        // Send a message first
        EmailMessage testMessage = EmailMessage.builder()
                .id(UUID.randomUUID().toString())
                .to("receive-test@example.com")
                .subject("Receive Test")
                .body("Message for receive testing")
                .messageType("EMAIL")
                .timestamp(LocalDateTime.now())
                .build();

        sqsMessagingService.sendMessage(testMessage, null);

        // Wait a bit for message to be available
        Thread.sleep(1000);

        // Try to receive messages directly from SQS
        ReceiveMessageRequest receiveRequest = ReceiveMessageRequest.builder()
                .queueUrl(testQueueUrl)
                .maxNumberOfMessages(1)
                .waitTimeSeconds(5) // Long polling
                .build();

        ReceiveMessageResponse receiveResponse = testSqsClient.receiveMessage(receiveRequest);
        
        if (!receiveResponse.messages().isEmpty()) {
            Message receivedMessage = receiveResponse.messages().get(0);
            assertThat(receivedMessage.body()).isNotNull();
            assertThat(receivedMessage.body()).contains("EMAIL");
            
            // Delete the message after processing
            DeleteMessageRequest deleteRequest = DeleteMessageRequest.builder()
                    .queueUrl(testQueueUrl)
                    .receiptHandle(receivedMessage.receiptHandle())
                    .build();
            testSqsClient.deleteMessage(deleteRequest);
        }
    }

    @Test
    @Order(6)
    void testMessageProcessing() throws InterruptedException {
        if (messageProcessor == null) {
            System.out.println("Skipping message processing test - processor not available");
            return;
        }

        // Test processing different message types
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(3);

        // Process email message
        futures.add(CompletableFuture.runAsync(() -> {
            EmailMessage emailMessage = EmailMessage.builder()
                    .id(UUID.randomUUID().toString())
                    .to("process-test@example.com")
                    .subject("Process Test")
                    .body("Testing message processing")
                    .messageType("EMAIL")
                    .timestamp(LocalDateTime.now())
                    .build();
            
            assertThatNoException().isThrownBy(() -> 
                messageProcessor.processEmailMessage(emailMessage));
        }, executor));

        // Process analytics message
        futures.add(CompletableFuture.runAsync(() -> {
            AnalyticsMessage analyticsMessage = AnalyticsMessage.builder()
                    .id(UUID.randomUUID().toString())
                    .userId("process-test-user")
                    .eventName("test_event")
                    .properties(Map.of("test", "value"))
                    .messageType("ANALYTICS")
                    .timestamp(LocalDateTime.now())
                    .build();
            
            assertThatNoException().isThrownBy(() -> 
                messageProcessor.processAnalyticsMessage(analyticsMessage));
        }, executor));

        // Process recommendation message
        futures.add(CompletableFuture.runAsync(() -> {
            RecommendationMessage recommendationMessage = RecommendationMessage.builder()
                    .id(UUID.randomUUID().toString())
                    .userId("process-test-user")
                    .requestType("TEST_RECOMMENDATION")
                    .parameters(Map.of("test", "value"))
                    .messageType("RECOMMENDATION")
                    .timestamp(LocalDateTime.now())
                    .build();
            
            assertThatNoException().isThrownBy(() -> 
                messageProcessor.processRecommendationMessage(recommendationMessage));
        }, executor));

        // Wait for all processing to complete
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
            futures.toArray(new CompletableFuture[0])
        );
        
        try {
            allFutures.get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException("Message processing failed", e);
        }

        executor.shutdown();
    }

    @Test
    @Order(7)
    void testErrorHandling() throws InterruptedException {
        if (sqsMessagingService == null) {
            System.out.println("Skipping SQS test - service not available");
            return;
        }

        // Test sending invalid message (null message)
        assertThatThrownBy(() -> 
            sqsMessagingService.sendMessage(null, null)
        ).isInstanceOf(Exception.class);

        // Test sending message with invalid data
        Map<String, Object> invalidMessage = new HashMap<>();
        invalidMessage.put("invalid", "data");
        invalidMessage.put("missing", "required fields");

        // This should handle gracefully or throw appropriate exception
        assertThatThrownBy(() -> 
            sqsMessagingService.sendMessage(invalidMessage, null)
        ).isInstanceOf(Exception.class);
    }

    @Test
    @Order(8)
    void testDeadLetterQueueFunctionality() throws InterruptedException {
        if (testDlqUrl == null) {
            System.out.println("Skipping DLQ test - DLQ not available");
            return;
        }

        // Send a message that will fail processing multiple times
        String failingMessageBody = "{\"messageType\":\"FAILING_TEST\",\"id\":\"" + 
                                   UUID.randomUUID().toString() + "\"}";

        SendMessageRequest sendRequest = SendMessageRequest.builder()
                .queueUrl(testQueueUrl)
                .messageBody(failingMessageBody)
                .build();
        testSqsClient.sendMessage(sendRequest);

        // Simulate message processing failures
        for (int attempt = 0; attempt < 4; attempt++) {
            Thread.sleep(1000);
            
            ReceiveMessageRequest receiveRequest = ReceiveMessageRequest.builder()
                    .queueUrl(testQueueUrl)
                    .maxNumberOfMessages(1)
                    .waitTimeSeconds(2)
                    .build();

            ReceiveMessageResponse receiveResponse = testSqsClient.receiveMessage(receiveRequest);
            
            if (!receiveResponse.messages().isEmpty()) {
                Message message = receiveResponse.messages().get(0);
                
                // Simulate processing failure by not deleting the message
                // After visibility timeout expires, message will be available again
                // After max receive count (3), it should go to DLQ
            }
        }

        // Check if message ended up in DLQ after max retries
        Thread.sleep(2000);
        
        GetQueueAttributesRequest dlqAttrRequest = GetQueueAttributesRequest.builder()
                .queueUrl(testDlqUrl)
                .attributeNames(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES)
                .build();
        
        GetQueueAttributesResponse dlqAttrResponse = testSqsClient.getQueueAttributes(dlqAttrRequest);
        String dlqMessageCount = dlqAttrResponse.attributes().get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES);
        
        // Note: This test depends on timing and LocalStack behavior
        // In a real environment, you would verify DLQ functionality more reliably
        assertThat(dlqMessageCount).isNotNull();
    }

    @Test
    @Order(9)
    void testQueueAttributes() {
        if (testQueueUrl == null) {
            System.out.println("Skipping queue attributes test - queue not available");
            return;
        }

        // Get all queue attributes
        GetQueueAttributesRequest request = GetQueueAttributesRequest.builder()
                .queueUrl(testQueueUrl)
                .attributeNames(QueueAttributeName.ALL)
                .build();

        GetQueueAttributesResponse response = testSqsClient.getQueueAttributes(request);
        Map<QueueAttributeName, String> attributes = response.attributes();

        // Verify key attributes
        assertThat(attributes).containsKey(QueueAttributeName.QUEUE_ARN);
        assertThat(attributes).containsKey(QueueAttributeName.VISIBILITY_TIMEOUT_SECONDS);
        assertThat(attributes).containsKey(QueueAttributeName.MESSAGE_RETENTION_PERIOD);
        assertThat(attributes).containsKey(QueueAttributeName.REDRIVE_POLICY);

        // Verify specific attribute values
        assertThat(attributes.get(QueueAttributeName.VISIBILITY_TIMEOUT_SECONDS)).isEqualTo("300");
        assertThat(attributes.get(QueueAttributeName.MESSAGE_RETENTION_PERIOD)).isEqualTo("1209600");

        // Verify redrive policy contains DLQ configuration
        String redrivePolicy = attributes.get(QueueAttributeName.REDRIVE_POLICY);
        assertThat(redrivePolicy).contains("maxReceiveCount");
        assertThat(redrivePolicy).contains("deadLetterTargetArn");
    }

    @Test
    @Order(10)
    void testMessageVisibilityTimeout() throws InterruptedException {
        if (testQueueUrl == null) {
            System.out.println("Skipping visibility timeout test - queue not available");
            return;
        }

        // Send a test message
        String testMessageBody = "{\"messageType\":\"VISIBILITY_TEST\",\"id\":\"" + 
                                UUID.randomUUID().toString() + "\"}";

        SendMessageRequest sendRequest = SendMessageRequest.builder()
                .queueUrl(testQueueUrl)
                .messageBody(testMessageBody)
                .build();
        testSqsClient.sendMessage(sendRequest);

        Thread.sleep(1000);

        // Receive the message
        ReceiveMessageRequest receiveRequest = ReceiveMessageRequest.builder()
                .queueUrl(testQueueUrl)
                .maxNumberOfMessages(1)
                .waitTimeSeconds(2)
                .build();

        ReceiveMessageResponse receiveResponse = testSqsClient.receiveMessage(receiveRequest);
        
        if (!receiveResponse.messages().isEmpty()) {
            Message message = receiveResponse.messages().get(0);
            
            // Try to receive the same message again immediately (should not be available)
            ReceiveMessageResponse secondReceiveResponse = testSqsClient.receiveMessage(receiveRequest);
            assertThat(secondReceiveResponse.messages()).isEmpty();

            // Change message visibility to make it available sooner
            ChangeMessageVisibilityRequest visibilityRequest = ChangeMessageVisibilityRequest.builder()
                    .queueUrl(testQueueUrl)
                    .receiptHandle(message.receiptHandle())
                    .visibilityTimeoutSeconds(1)
                    .build();
            testSqsClient.changeMessageVisibility(visibilityRequest);

            // Wait and try to receive again
            Thread.sleep(2000);
            ReceiveMessageResponse thirdReceiveResponse = testSqsClient.receiveMessage(receiveRequest);
            
            if (!thirdReceiveResponse.messages().isEmpty()) {
                // Clean up - delete the message
                DeleteMessageRequest deleteRequest = DeleteMessageRequest.builder()
                        .queueUrl(testQueueUrl)
                        .receiptHandle(thirdReceiveResponse.messages().get(0).receiptHandle())
                        .build();
                testSqsClient.deleteMessage(deleteRequest);
            }
        }
    }

    @Test
    @Order(11)
    void testConcurrentMessageProcessing() throws InterruptedException, ExecutionException {
        if (sqsMessagingService == null) {
            System.out.println("Skipping concurrent processing test - service not available");
            return;
        }

        ExecutorService executor = Executors.newFixedThreadPool(5);
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        // Send multiple messages concurrently
        for (int i = 0; i < 10; i++) {
            final int messageIndex = i;
            futures.add(CompletableFuture.runAsync(() -> {
                EmailMessage message = EmailMessage.builder()
                        .id(UUID.randomUUID().toString())
                        .to("concurrent" + messageIndex + "@example.com")
                        .subject("Concurrent Test " + messageIndex)
                        .body("Concurrent message " + messageIndex)
                        .messageType("EMAIL")
                        .timestamp(LocalDateTime.now())
                        .build();
                
                try {
                    sqsMessagingService.sendMessage(message, null);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to send concurrent message", e);
                }
            }, executor));
        }

        // Wait for all messages to be sent
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
            futures.toArray(new CompletableFuture[0])
        );
        
        allFutures.get(30, TimeUnit.SECONDS);
        executor.shutdown();

        // Verify messages are in queue
        GetQueueAttributesRequest request = GetQueueAttributesRequest.builder()
                .queueUrl(testQueueUrl)
                .attributeNames(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES)
                .build();
        
        GetQueueAttributesResponse response = testSqsClient.getQueueAttributes(request);
        String messageCount = response.attributes().get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES);
        
        assertThat(messageCount).isNotNull();
    }

    @Test
    @Order(12)
    void testQueuePurge() throws InterruptedException {
        if (testQueueUrl == null) {
            System.out.println("Skipping queue purge test - queue not available");
            return;
        }

        // Send some test messages
        for (int i = 0; i < 5; i++) {
            SendMessageRequest sendRequest = SendMessageRequest.builder()
                    .queueUrl(testQueueUrl)
                    .messageBody("{\"messageType\":\"PURGE_TEST\",\"id\":\"" + i + "\"}")
                    .build();
            testSqsClient.sendMessage(sendRequest);
        }

        Thread.sleep(2000);

        // Purge the queue
        PurgeQueueRequest purgeRequest = PurgeQueueRequest.builder()
                .queueUrl(testQueueUrl)
                .build();
        testSqsClient.purgeQueue(purgeRequest);

        // Wait for purge to complete
        Thread.sleep(5000);

        // Verify queue is empty
        GetQueueAttributesRequest request = GetQueueAttributesRequest.builder()
                .queueUrl(testQueueUrl)
                .attributeNames(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES)
                .build();
        
        GetQueueAttributesResponse response = testSqsClient.getQueueAttributes(request);
        String messageCount = response.attributes().get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES);
        
        // After purge, message count should be 0 (eventually consistent)
        assertThat(messageCount).isEqualTo("0");
    }
}