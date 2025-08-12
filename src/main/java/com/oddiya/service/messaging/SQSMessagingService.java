package com.oddiya.service.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oddiya.dto.message.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.Dimension;
import software.amazon.awssdk.services.cloudwatch.model.MetricDatum;
import software.amazon.awssdk.services.cloudwatch.model.PutMetricDataRequest;
import software.amazon.awssdk.services.cloudwatch.model.StandardUnit;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * AWS SQS implementation of MessagingService
 */
@Service
@ConditionalOnProperty(name = "app.aws.sqs.enabled", havingValue = "true")
@Slf4j
public class SQSMessagingService implements MessagingService {

    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;
    private final CloudWatchClient cloudWatchClient;
    private final Map<String, String> queueUrls;

    // Queue names from configuration
    private final String emailQueueName = "oddiya-email-notifications";
    private final String imageQueueName = "oddiya-image-processing";
    private final String analyticsQueueName = "oddiya-analytics-events";
    private final String recommendationQueueName = "oddiya-recommendation-updates";
    private final String videoQueueName = "oddiya-video-processing";

    // CloudWatch namespace
    private static final String CLOUDWATCH_NAMESPACE = "Oddiya/SQS";

    public SQSMessagingService(SqsClient sqsClient, 
                              @Qualifier("sqsObjectMapper") ObjectMapper objectMapper,
                              CloudWatchClient cloudWatchClient) {
        this.sqsClient = sqsClient;
        this.objectMapper = objectMapper;
        this.cloudWatchClient = cloudWatchClient;
        this.queueUrls = new HashMap<>();
        
        // Initialize queue URLs cache
        initializeQueueUrls();
    }

    private void initializeQueueUrls() {
        try {
            queueUrls.put(emailQueueName, getQueueUrlFromSQS(emailQueueName));
            queueUrls.put(imageQueueName, getQueueUrlFromSQS(imageQueueName));
            queueUrls.put(analyticsQueueName, getQueueUrlFromSQS(analyticsQueueName));
            queueUrls.put(recommendationQueueName, getQueueUrlFromSQS(recommendationQueueName));
            queueUrls.put(videoQueueName, getQueueUrlFromSQS(videoQueueName));
            
            log.info("Initialized {} SQS queue URLs", queueUrls.size());
        } catch (Exception e) {
            log.warn("Failed to initialize some queue URLs: {}", e.getMessage());
        }
    }

    private String getQueueUrlFromSQS(String queueName) {
        try {
            GetQueueUrlResponse response = sqsClient.getQueueUrl(
                GetQueueUrlRequest.builder().queueName(queueName).build()
            );
            return response.queueUrl();
        } catch (QueueDoesNotExistException e) {
            log.warn("Queue {} does not exist", queueName);
            return null;
        } catch (Exception e) {
            log.error("Error getting queue URL for {}: {}", queueName, e.getMessage());
            return null;
        }
    }

    @Override
    @Async
    public CompletableFuture<Void> sendEmailMessage(EmailMessage message) {
        return sendMessageToQueue(emailQueueName, message, createMessageAttributes(message));
    }

    @Override
    @Async
    public CompletableFuture<Void> sendImageProcessingMessage(ImageProcessingMessage message) {
        return sendMessageToQueue(imageQueueName, message, createMessageAttributes(message));
    }

    @Override
    @Async
    public CompletableFuture<Void> sendAnalyticsMessage(AnalyticsMessage message) {
        return sendMessageToQueue(analyticsQueueName, message, createMessageAttributes(message));
    }

    @Override
    @Async
    public CompletableFuture<Void> sendRecommendationMessage(RecommendationMessage message) {
        return sendMessageToQueue(recommendationQueueName, message, createMessageAttributes(message));
    }

    @Override
    @Async
    public CompletableFuture<Void> sendVideoProcessingMessage(VideoProcessingMessage message) {
        return sendMessageToQueue(videoQueueName, message, createMessageAttributes(message));
    }

    @Override
    @Async
    public CompletableFuture<Void> sendMessage(String queueName, Object message, Map<String, String> messageAttributes) {
        Map<String, MessageAttributeValue> sqsAttributes = convertToSqsAttributes(messageAttributes);
        return sendMessageToQueue(queueName, message, sqsAttributes);
    }

    @Override
    @Async
    public CompletableFuture<Void> sendMessage(String queueName, Object message) {
        return sendMessageToQueue(queueName, message, new HashMap<>());
    }

    @Override
    @Async
    public CompletableFuture<Void> sendBatchMessages(String queueName, List<?> messages) {
        return CompletableFuture.runAsync(() -> {
            try {
                String queueUrl = getQueueUrl(queueName);
                if (queueUrl == null) {
                    throw new RuntimeException("Queue URL not found for: " + queueName);
                }

                // Split into batches of 10 (SQS limit)
                List<List<?>> batches = partitionList(messages, 10);
                int successCount = 0;
                int failureCount = 0;

                for (List<?> batch : batches) {
                    try {
                        List<SendMessageBatchRequestEntry> entries = createBatchEntries(batch);
                        
                        SendMessageBatchRequest batchRequest = SendMessageBatchRequest.builder()
                            .queueUrl(queueUrl)
                            .entries(entries)
                            .build();

                        SendMessageBatchResponse response = sqsClient.sendMessageBatch(batchRequest);
                        
                        successCount += response.successful().size();
                        failureCount += response.failed().size();

                        // Log failed messages
                        if (!response.failed().isEmpty()) {
                            log.warn("Failed to send {} messages to queue {}", 
                                response.failed().size(), queueName);
                            response.failed().forEach(failure -> 
                                log.warn("Failed message ID: {}, Error: {}", 
                                    failure.id(), failure.message()));
                        }

                    } catch (Exception e) {
                        log.error("Error sending batch to queue {}: {}", queueName, e.getMessage());
                        failureCount += batch.size();
                    }
                }

                // Send CloudWatch metrics
                sendCloudWatchMetrics(queueName, "BatchMessagesSent", successCount);
                sendCloudWatchMetrics(queueName, "BatchMessagesFailed", failureCount);

                log.info("Batch send completed for queue {}: {} successful, {} failed", 
                    queueName, successCount, failureCount);

            } catch (Exception e) {
                log.error("Error in batch send for queue {}: {}", queueName, e.getMessage());
                throw new RuntimeException("Failed to send batch messages", e);
            }
        });
    }

    @Override
    @Async
    public CompletableFuture<Void> sendBatchEmailMessages(List<EmailMessage> messages) {
        return sendBatchMessages(emailQueueName, messages);
    }

    @Override
    @Async
    public CompletableFuture<Void> sendBatchAnalyticsMessages(List<AnalyticsMessage> messages) {
        return sendBatchMessages(analyticsQueueName, messages);
    }

    @Override
    public List<String> receiveMessages(String queueName, int maxMessages) {
        try {
            String queueUrl = getQueueUrl(queueName);
            if (queueUrl == null) {
                log.warn("Queue URL not found for: {}", queueName);
                return new ArrayList<>();
            }

            ReceiveMessageRequest receiveRequest = ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .maxNumberOfMessages(Math.min(maxMessages, 10)) // SQS limit is 10
                .waitTimeSeconds(20) // Long polling
                .visibilityTimeout(300) // 5 minutes
                .messageAttributeNames("All")
                .build();

            ReceiveMessageResponse response = sqsClient.receiveMessage(receiveRequest);
            
            List<String> messages = response.messages().stream()
                .map(Message::body)
                .collect(Collectors.toList());

            // Send CloudWatch metrics
            sendCloudWatchMetrics(queueName, "MessagesReceived", messages.size());

            log.debug("Received {} messages from queue {}", messages.size(), queueName);
            return messages;

        } catch (Exception e) {
            log.error("Error receiving messages from queue {}: {}", queueName, e.getMessage());
            return new ArrayList<>();
        }
    }

    @Override
    public List<String> receiveMessages(String queueName) {
        return receiveMessages(queueName, 10);
    }

    @Override
    public String getQueueUrl(String queueName) {
        String url = queueUrls.get(queueName);
        if (url == null) {
            // Try to get it from SQS and cache it
            url = getQueueUrlFromSQS(queueName);
            if (url != null) {
                queueUrls.put(queueName, url);
            }
        }
        return url;
    }

    @Override
    public boolean queueExists(String queueName) {
        try {
            String queueUrl = getQueueUrl(queueName);
            return queueUrl != null;
        } catch (Exception e) {
            log.debug("Queue {} does not exist: {}", queueName, e.getMessage());
            return false;
        }
    }

    @Override
    public int getMessageCount(String queueName) {
        try {
            String queueUrl = getQueueUrl(queueName);
            if (queueUrl == null) {
                return 0;
            }

            GetQueueAttributesRequest request = GetQueueAttributesRequest.builder()
                .queueUrl(queueUrl)
                .attributeNames(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES)
                .build();

            GetQueueAttributesResponse response = sqsClient.getQueueAttributes(request);
            String countStr = response.attributes().get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES);
            
            return countStr != null ? Integer.parseInt(countStr) : 0;

        } catch (Exception e) {
            log.error("Error getting message count for queue {}: {}", queueName, e.getMessage());
            return 0;
        }
    }

    @Override
    public Map<String, String> getQueueAttributes(String queueName) {
        try {
            String queueUrl = getQueueUrl(queueName);
            if (queueUrl == null) {
                return new HashMap<>();
            }

            GetQueueAttributesRequest request = GetQueueAttributesRequest.builder()
                .queueUrl(queueUrl)
                .attributeNames(QueueAttributeName.ALL)
                .build();

            GetQueueAttributesResponse response = sqsClient.getQueueAttributes(request);
            
            return response.attributes().entrySet().stream()
                .collect(Collectors.toMap(
                    entry -> entry.getKey().toString(),
                    entry -> entry.getValue()
                ));

        } catch (Exception e) {
            log.error("Error getting queue attributes for {}: {}", queueName, e.getMessage());
            return new HashMap<>();
        }
    }

    // Helper methods

    private CompletableFuture<Void> sendMessageToQueue(String queueName, Object message, 
                                                      Map<String, MessageAttributeValue> attributes) {
        return CompletableFuture.runAsync(() -> {
            try {
                String queueUrl = getQueueUrl(queueName);
                if (queueUrl == null) {
                    throw new RuntimeException("Queue URL not found for: " + queueName);
                }

                String messageBody = objectMapper.writeValueAsString(message);

                SendMessageRequest.Builder requestBuilder = SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(messageBody);

                if (attributes != null && !attributes.isEmpty()) {
                    requestBuilder.messageAttributes(attributes);
                }

                // Add deduplication for FIFO queues
                if (queueName.endsWith(".fifo")) {
                    String messageId = extractMessageId(message);
                    if (messageId != null) {
                        requestBuilder.messageDeduplicationId(messageId);
                        requestBuilder.messageGroupId(extractMessageGroupId(message, queueName));
                    }
                }

                SendMessageResponse response = sqsClient.sendMessage(requestBuilder.build());

                // Send CloudWatch metrics
                sendCloudWatchMetrics(queueName, "MessagesSent", 1);

                log.debug("Message sent to queue {}: messageId={}", queueName, response.messageId());

            } catch (JsonProcessingException e) {
                log.error("Error serializing message for queue {}: {}", queueName, e.getMessage());
                sendCloudWatchMetrics(queueName, "MessagesFailed", 1);
                throw new RuntimeException("Failed to serialize message", e);
            } catch (Exception e) {
                log.error("Error sending message to queue {}: {}", queueName, e.getMessage());
                sendCloudWatchMetrics(queueName, "MessagesFailed", 1);
                throw new RuntimeException("Failed to send message to SQS queue", e);
            }
        });
    }

    private Map<String, MessageAttributeValue> createMessageAttributes(Object message) {
        Map<String, MessageAttributeValue> attributes = new HashMap<>();
        
        // Common attributes
        attributes.put("MessageType", MessageAttributeValue.builder()
            .stringValue(message.getClass().getSimpleName())
            .dataType("String")
            .build());

        attributes.put("Timestamp", MessageAttributeValue.builder()
            .stringValue(Instant.now().toString())
            .dataType("String")
            .build());

        // Message-specific attributes
        if (message instanceof EmailMessage emailMsg) {
            attributes.put("Priority", MessageAttributeValue.builder()
                .stringValue(emailMsg.getPriority().name())
                .dataType("String")
                .build());
            attributes.put("TemplateType", MessageAttributeValue.builder()
                .stringValue(emailMsg.getTemplateType())
                .dataType("String")
                .build());
        } else if (message instanceof ImageProcessingMessage imgMsg) {
            attributes.put("ProcessingType", MessageAttributeValue.builder()
                .stringValue(imgMsg.getProcessingType().name())
                .dataType("String")
                .build());
            attributes.put("Priority", MessageAttributeValue.builder()
                .stringValue(imgMsg.getPriority().name())
                .dataType("String")
                .build());
        } else if (message instanceof AnalyticsMessage analyticsMsg) {
            attributes.put("EventType", MessageAttributeValue.builder()
                .stringValue(analyticsMsg.getEventType())
                .dataType("String")
                .build());
            attributes.put("Priority", MessageAttributeValue.builder()
                .stringValue(analyticsMsg.getPriority().name())
                .dataType("String")
                .build());
        } else if (message instanceof RecommendationMessage recMsg) {
            attributes.put("RecommendationType", MessageAttributeValue.builder()
                .stringValue(recMsg.getRecommendationType().name())
                .dataType("String")
                .build());
            attributes.put("Priority", MessageAttributeValue.builder()
                .stringValue(recMsg.getPriority().name())
                .dataType("String")
                .build());
        } else if (message instanceof VideoProcessingMessage videoMsg) {
            attributes.put("ProcessingType", MessageAttributeValue.builder()
                .stringValue(videoMsg.getProcessingType().name())
                .dataType("String")
                .build());
            attributes.put("Priority", MessageAttributeValue.builder()
                .stringValue(videoMsg.getPriority().name())
                .dataType("String")
                .build());
        }

        return attributes;
    }

    private Map<String, MessageAttributeValue> convertToSqsAttributes(Map<String, String> attributes) {
        if (attributes == null) {
            return new HashMap<>();
        }
        
        return attributes.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> MessageAttributeValue.builder()
                    .stringValue(entry.getValue())
                    .dataType("String")
                    .build()
            ));
    }

    private List<SendMessageBatchRequestEntry> createBatchEntries(List<?> messages) {
        return IntStream.range(0, messages.size())
            .mapToObj(i -> {
                try {
                    Object message = messages.get(i);
                    String messageBody = objectMapper.writeValueAsString(message);
                    
                    return SendMessageBatchRequestEntry.builder()
                        .id("msg-" + i)
                        .messageBody(messageBody)
                        .messageAttributes(createMessageAttributes(message))
                        .build();
                } catch (JsonProcessingException e) {
                    log.error("Error serializing batch message: {}", e.getMessage());
                    return null;
                }
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    private <T> List<List<T>> partitionList(List<T> list, int batchSize) {
        return IntStream.range(0, (list.size() + batchSize - 1) / batchSize)
            .mapToObj(i -> list.subList(i * batchSize, Math.min((i + 1) * batchSize, list.size())))
            .collect(Collectors.toList());
    }

    private String extractMessageId(Object message) {
        // Extract message ID for deduplication
        if (message instanceof EmailMessage emailMsg) {
            return emailMsg.getMessageId();
        } else if (message instanceof ImageProcessingMessage imgMsg) {
            return imgMsg.getMessageId();
        } else if (message instanceof AnalyticsMessage analyticsMsg) {
            return analyticsMsg.getMessageId();
        } else if (message instanceof RecommendationMessage recMsg) {
            return recMsg.getMessageId();
        } else if (message instanceof VideoProcessingMessage videoMsg) {
            return videoMsg.getMessageId();
        }
        return null;
    }

    private String extractMessageGroupId(Object message, String queueName) {
        // For FIFO queues, group messages by type or user
        String groupId = queueName.replace(".fifo", "");
        
        if (message instanceof EmailMessage emailMsg && emailMsg.getUserId() != null) {
            groupId += "-user-" + emailMsg.getUserId();
        } else if (message instanceof ImageProcessingMessage imgMsg) {
            groupId += "-user-" + imgMsg.getUserId();
        } else if (message instanceof AnalyticsMessage analyticsMsg && analyticsMsg.getUserId() != null) {
            groupId += "-user-" + analyticsMsg.getUserId();
        } else if (message instanceof RecommendationMessage recMsg && recMsg.getUserId() != null) {
            groupId += "-user-" + recMsg.getUserId();
        } else if (message instanceof VideoProcessingMessage videoMsg) {
            groupId += "-user-" + videoMsg.getUserId();
        }
        
        return groupId;
    }

    private void sendCloudWatchMetrics(String queueName, String metricName, double value) {
        try {
            MetricDatum metric = MetricDatum.builder()
                .metricName(metricName)
                .value(value)
                .unit(StandardUnit.COUNT)
                .timestamp(Instant.now())
                .dimensions(
                    Dimension.builder()
                        .name("QueueName")
                        .value(queueName)
                        .build()
                )
                .build();

            PutMetricDataRequest request = PutMetricDataRequest.builder()
                .namespace(CLOUDWATCH_NAMESPACE)
                .metricData(metric)
                .build();

            cloudWatchClient.putMetricData(request);

        } catch (Exception e) {
            log.debug("Failed to send CloudWatch metric: {}", e.getMessage());
            // Don't throw exception for metric failures
        }
    }
}