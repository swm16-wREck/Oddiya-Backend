package com.oddiya.config.aws;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Configuration
@ConfigurationProperties(prefix = "app.aws.sqs")
@ConditionalOnProperty(name = "app.aws.sqs.enabled", havingValue = "true", matchIfMissing = false)
@ConditionalOnBean(SqsClient.class)
@Data
@Slf4j
public class SQSConfig {

    private String queueUrl;
    private int maxMessages = 10;
    private int visibilityTimeoutSeconds = 300; // 5 minutes
    private int waitTimeSeconds = 20; // Long polling
    private int maxReceiveCount = 3;
    private String deadLetterQueueUrl;
    private boolean createQueuesOnStartup = false;
    
    private QueueNames queueNames = new QueueNames();
    private MessageProcessing messageProcessing = new MessageProcessing();

    @Bean
    public ObjectMapper sqsObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }

    @Bean
    public SQSQueueManager sqsQueueManager(SqsClient sqsClient) {
        return new SQSQueueManager(sqsClient, this);
    }

    @Bean
    public SQSMessageSender sqsMessageSender(SqsClient sqsClient, ObjectMapper sqsObjectMapper) {
        return new SQSMessageSender(sqsClient, sqsObjectMapper, this);
    }

    @Bean
    public SQSMessageReceiver sqsMessageReceiver(SqsClient sqsClient, ObjectMapper sqsObjectMapper) {
        return new SQSMessageReceiver(sqsClient, sqsObjectMapper, this);
    }

    @PostConstruct
    public void initializeQueueNames() {
        log.info("Initializing SQS queue names");
        if (queueNames.getEmailNotifications() == null) {
            queueNames.setEmailNotifications("oddiya-email-notifications");
        }
        if (queueNames.getImageProcessing() == null) {
            queueNames.setImageProcessing("oddiya-image-processing");
        }
        if (queueNames.getAnalyticsEvents() == null) {
            queueNames.setAnalyticsEvents("oddiya-analytics-events");
        }
        if (queueNames.getRecommendationUpdates() == null) {
            queueNames.setRecommendationUpdates("oddiya-recommendation-updates");
        }
        if (queueNames.getVideoProcessing() == null) {
            queueNames.setVideoProcessing("oddiya-video-processing");
        }
    }

    @Data
    public static class QueueNames {
        private String emailNotifications;
        private String imageProcessing;
        private String analyticsEvents;
        private String recommendationUpdates;
        private String videoProcessing;
    }

    @Data
    public static class MessageProcessing {
        private int batchSize = 10;
        private int pollingIntervalSeconds = 30;
        private int maxRetries = 3;
        private int retryDelaySeconds = 60;
        private boolean enableDeadLetterQueue = true;
        private boolean enableFifo = false;
    }

    /**
     * SQS Queue Manager for queue operations
     */
    public static class SQSQueueManager {

        private final SqsClient sqsClient;
        private final SQSConfig config;

        public SQSQueueManager(SqsClient sqsClient, SQSConfig config) {
            this.sqsClient = sqsClient;
            this.config = config;
        }

        @PostConstruct
        public void initializeQueues() {
            if (config.isCreateQueuesOnStartup()) {
                log.info("Creating SQS queues on startup");
                createQueuesIfNotExist();
            }
        }

        public void createQueuesIfNotExist() {
            createEmailNotificationsQueue();
            createImageProcessingQueue();
            createAnalyticsEventsQueue();
            createRecommendationUpdatesQueue();
            createVideoProcessingQueue();
        }

        private void createEmailNotificationsQueue() {
            String queueName = config.getQueueNames().getEmailNotifications();
            createQueueIfNotExists(queueName, false);
        }

        private void createImageProcessingQueue() {
            String queueName = config.getQueueNames().getImageProcessing();
            createQueueIfNotExists(queueName, false);
        }

        private void createAnalyticsEventsQueue() {
            String queueName = config.getQueueNames().getAnalyticsEvents();
            createQueueIfNotExists(queueName, false);
        }

        private void createRecommendationUpdatesQueue() {
            String queueName = config.getQueueNames().getRecommendationUpdates();
            createQueueIfNotExists(queueName, false);
        }

        private void createVideoProcessingQueue() {
            String queueName = config.getQueueNames().getVideoProcessing();
            createQueueIfNotExists(queueName, false);
        }

        private void createQueueIfNotExists(String queueName, boolean fifo) {
            try {
                if (queueExists(queueName)) {
                    log.info("Queue {} already exists", queueName);
                    return;
                }

                log.info("Creating SQS queue: {}", queueName);

                Map<QueueAttributeName, String> attributes = new HashMap<>();
                attributes.put(QueueAttributeName.VISIBILITY_TIMEOUT, String.valueOf(config.getVisibilityTimeoutSeconds()));
                attributes.put(QueueAttributeName.MESSAGE_RETENTION_PERIOD, "1209600"); // 14 days
                attributes.put(QueueAttributeName.RECEIVE_MESSAGE_WAIT_TIME_SECONDS, String.valueOf(config.getWaitTimeSeconds()));

                if (config.getMessageProcessing().isEnableDeadLetterQueue()) {
                    // Create dead letter queue first
                    String dlqName = queueName + "-dlq";
                    if (fifo) {
                        dlqName += ".fifo";
                    }

                    if (!queueExists(dlqName)) {
                        Map<QueueAttributeName, String> dlqAttributes = new HashMap<>();
                        if (fifo) {
                            dlqAttributes.put(QueueAttributeName.FIFO_QUEUE, "true");
                            dlqAttributes.put(QueueAttributeName.CONTENT_BASED_DEDUPLICATION, "true");
                        }

                        CreateQueueRequest dlqRequest = CreateQueueRequest.builder()
                                .queueName(dlqName)
                                .attributes(dlqAttributes)
                                .build();

                        CreateQueueResponse dlqResponse = sqsClient.createQueue(dlqRequest);
                        log.info("Created dead letter queue: {} with URL: {}", dlqName, dlqResponse.queueUrl());
                    }

                    // Get DLQ ARN
                    String dlqUrl = getQueueUrl(dlqName);
                    GetQueueAttributesResponse dlqAttrsResponse = sqsClient.getQueueAttributes(
                        GetQueueAttributesRequest.builder()
                            .queueUrl(dlqUrl)
                            .attributeNames(QueueAttributeName.QUEUE_ARN)
                            .build()
                    );
                    String dlqArn = dlqAttrsResponse.attributes().get(QueueAttributeName.QUEUE_ARN);

                    // Configure redrive policy
                    String redrivePolicy = String.format(
                        "{\"deadLetterTargetArn\":\"%s\",\"maxReceiveCount\":%d}",
                        dlqArn, config.getMaxReceiveCount()
                    );
                    attributes.put(QueueAttributeName.REDRIVE_POLICY, redrivePolicy);
                }

                if (fifo) {
                    attributes.put(QueueAttributeName.FIFO_QUEUE, "true");
                    attributes.put(QueueAttributeName.CONTENT_BASED_DEDUPLICATION, "true");
                    queueName += ".fifo";
                }

                CreateQueueRequest request = CreateQueueRequest.builder()
                        .queueName(queueName)
                        .attributes(attributes)
                        .build();

                CreateQueueResponse response = sqsClient.createQueue(request);
                log.info("Created SQS queue: {} with URL: {}", queueName, response.queueUrl());

            } catch (Exception e) {
                log.error("Error creating SQS queue {}: {}", queueName, e.getMessage());
                throw new RuntimeException("Failed to create SQS queue", e);
            }
        }

        private boolean queueExists(String queueName) {
            try {
                GetQueueUrlResponse response = sqsClient.getQueueUrl(
                    GetQueueUrlRequest.builder().queueName(queueName).build()
                );
                return response.queueUrl() != null;
            } catch (QueueDoesNotExistException e) {
                return false;
            } catch (Exception e) {
                log.warn("Error checking if queue {} exists: {}", queueName, e.getMessage());
                return false;
            }
        }

        private String getQueueUrl(String queueName) {
            try {
                GetQueueUrlResponse response = sqsClient.getQueueUrl(
                    GetQueueUrlRequest.builder().queueName(queueName).build()
                );
                return response.queueUrl();
            } catch (Exception e) {
                log.error("Error getting queue URL for {}: {}", queueName, e.getMessage());
                return null;
            }
        }
    }

    /**
     * SQS Message Sender for sending messages
     */
    public static class SQSMessageSender {

        private final SqsClient sqsClient;
        private final ObjectMapper objectMapper;
        private final SQSConfig config;

        public SQSMessageSender(SqsClient sqsClient, ObjectMapper objectMapper, SQSConfig config) {
            this.sqsClient = sqsClient;
            this.objectMapper = objectMapper;
            this.config = config;
        }

        public void sendEmailNotification(Object message) {
            sendMessage(config.getQueueNames().getEmailNotifications(), message);
        }

        public void sendImageProcessingMessage(Object message) {
            sendMessage(config.getQueueNames().getImageProcessing(), message);
        }

        public void sendAnalyticsEvent(Object message) {
            sendMessage(config.getQueueNames().getAnalyticsEvents(), message);
        }

        public void sendRecommendationUpdate(Object message) {
            sendMessage(config.getQueueNames().getRecommendationUpdates(), message);
        }

        public void sendVideoProcessingMessage(Object message) {
            sendMessage(config.getQueueNames().getVideoProcessing(), message);
        }

        public void sendMessage(String queueName, Object message) {
            sendMessage(queueName, message, null);
        }

        public void sendMessage(String queueName, Object message, Map<String, MessageAttributeValue> messageAttributes) {
            try {
                String queueUrl = getQueueUrl(queueName);
                if (queueUrl == null) {
                    log.error("Queue URL not found for queue: {}", queueName);
                    return;
                }

                String messageBody = objectMapper.writeValueAsString(message);

                SendMessageRequest.Builder requestBuilder = SendMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .messageBody(messageBody);

                if (messageAttributes != null && !messageAttributes.isEmpty()) {
                    requestBuilder.messageAttributes(messageAttributes);
                }

                SendMessageResponse response = sqsClient.sendMessage(requestBuilder.build());
                log.debug("Message sent to queue {}: messageId={}", queueName, response.messageId());

            } catch (Exception e) {
                log.error("Error sending message to queue {}: {}", queueName, e.getMessage());
                throw new RuntimeException("Failed to send message to SQS queue", e);
            }
        }

        public void sendBatchMessages(String queueName, List<Object> messages) {
            try {
                String queueUrl = getQueueUrl(queueName);
                if (queueUrl == null) {
                    log.error("Queue URL not found for queue: {}", queueName);
                    return;
                }

                // SQS batch limit is 10 messages
                int batchSize = Math.min(10, config.getMessageProcessing().getBatchSize());
                
                for (int i = 0; i < messages.size(); i += batchSize) {
                    List<Object> batch = messages.subList(i, Math.min(i + batchSize, messages.size()));
                    
                    List<SendMessageBatchRequestEntry> entries = batch.stream()
                            .map(message -> {
                                try {
                                    return SendMessageBatchRequestEntry.builder()
                                            .id(String.valueOf(System.currentTimeMillis() + Math.random()))
                                            .messageBody(objectMapper.writeValueAsString(message))
                                            .build();
                                } catch (Exception e) {
                                    log.error("Error serializing message: {}", e.getMessage());
                                    return null;
                                }
                            })
                            .filter(entry -> entry != null)
                            .toList();

                    if (!entries.isEmpty()) {
                        SendMessageBatchRequest batchRequest = SendMessageBatchRequest.builder()
                                .queueUrl(queueUrl)
                                .entries(entries)
                                .build();

                        SendMessageBatchResponse response = sqsClient.sendMessageBatch(batchRequest);
                        log.debug("Batch messages sent to queue {}: successful={}, failed={}", 
                                queueName, response.successful().size(), response.failed().size());
                    }
                }

            } catch (Exception e) {
                log.error("Error sending batch messages to queue {}: {}", queueName, e.getMessage());
                throw new RuntimeException("Failed to send batch messages to SQS queue", e);
            }
        }

        private String getQueueUrl(String queueName) {
            try {
                GetQueueUrlResponse response = sqsClient.getQueueUrl(
                    GetQueueUrlRequest.builder().queueName(queueName).build()
                );
                return response.queueUrl();
            } catch (Exception e) {
                log.error("Error getting queue URL for {}: {}", queueName, e.getMessage());
                return null;
            }
        }
    }

    /**
     * SQS Message Receiver for receiving and processing messages
     */
    public static class SQSMessageReceiver {

        private final SqsClient sqsClient;
        private final ObjectMapper objectMapper;
        private final SQSConfig config;
        private final ScheduledExecutorService executorService;

        public SQSMessageReceiver(SqsClient sqsClient, ObjectMapper objectMapper, SQSConfig config) {
            this.sqsClient = sqsClient;
            this.objectMapper = objectMapper;
            this.config = config;
            this.executorService = Executors.newScheduledThreadPool(5);
        }

        public void startPolling(String queueName, MessageProcessor processor) {
            executorService.scheduleWithFixedDelay(
                () -> pollMessages(queueName, processor),
                0,
                config.getMessageProcessing().getPollingIntervalSeconds(),
                TimeUnit.SECONDS
            );
        }

        public void pollMessages(String queueName, MessageProcessor processor) {
            try {
                String queueUrl = getQueueUrl(queueName);
                if (queueUrl == null) {
                    log.error("Queue URL not found for queue: {}", queueName);
                    return;
                }

                ReceiveMessageRequest receiveRequest = ReceiveMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .maxNumberOfMessages(config.getMaxMessages())
                        .waitTimeSeconds(config.getWaitTimeSeconds())
                        .visibilityTimeout(config.getVisibilityTimeoutSeconds())
                        .messageAttributeNames("All")
                        .build();

                ReceiveMessageResponse response = sqsClient.receiveMessage(receiveRequest);
                
                if (!response.messages().isEmpty()) {
                    log.debug("Received {} messages from queue {}", response.messages().size(), queueName);
                    
                    List<CompletableFuture<Void>> futures = response.messages().stream()
                            .map(message -> CompletableFuture.runAsync(() -> processMessage(queueUrl, message, processor)))
                            .toList();

                    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                            .exceptionally(throwable -> {
                                log.error("Error processing messages from queue {}: {}", queueName, throwable.getMessage());
                                return null;
                            });
                }

            } catch (Exception e) {
                log.error("Error polling messages from queue {}: {}", queueName, e.getMessage());
            }
        }

        private void processMessage(String queueUrl, Message message, MessageProcessor processor) {
            try {
                log.debug("Processing message: {}", message.messageId());
                
                processor.processMessage(message.body(), message.messageAttributes());
                
                // Delete message after successful processing
                sqsClient.deleteMessage(
                    DeleteMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .receiptHandle(message.receiptHandle())
                        .build()
                );
                
                log.debug("Message processed and deleted: {}", message.messageId());
                
            } catch (Exception e) {
                log.error("Error processing message {}: {}", message.messageId(), e.getMessage());
                // Message will become visible again after visibility timeout
            }
        }

        private String getQueueUrl(String queueName) {
            try {
                GetQueueUrlResponse response = sqsClient.getQueueUrl(
                    GetQueueUrlRequest.builder().queueName(queueName).build()
                );
                return response.queueUrl();
            } catch (Exception e) {
                log.error("Error getting queue URL for {}: {}", queueName, e.getMessage());
                return null;
            }
        }

        public void shutdown() {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Interface for message processors
     */
    @FunctionalInterface
    public interface MessageProcessor {
        void processMessage(String messageBody, Map<String, MessageAttributeValue> messageAttributes);
    }
}

/**
 * Mock SQS configuration for local development and testing
 */
@Configuration
@Profile({"local", "test", "h2"})
@Slf4j
class MockSQSConfig {

    @Bean
    @ConditionalOnProperty(name = "app.aws.mock.enabled", havingValue = "true", matchIfMissing = true)
    public MockSQSMessageSender mockSqsMessageSender() {
        log.info("Creating mock SQS message sender for local development");
        return new MockSQSMessageSender();
    }

    public static class MockSQSMessageSender {

        public void sendEmailNotification(Object message) {
            log.info("Mock: Sending email notification: {}", message.toString());
        }

        public void sendImageProcessingMessage(Object message) {
            log.info("Mock: Sending image processing message: {}", message.toString());
        }

        public void sendAnalyticsEvent(Object message) {
            log.info("Mock: Sending analytics event: {}", message.toString());
        }

        public void sendRecommendationUpdate(Object message) {
            log.info("Mock: Sending recommendation update: {}", message.toString());
        }

        public void sendVideoProcessingMessage(Object message) {
            log.info("Mock: Sending video processing message: {}", message.toString());
        }

        public void sendMessage(String queueName, Object message) {
            log.info("Mock: Sending message to queue {}: {}", queueName, message.toString());
        }
    }
}