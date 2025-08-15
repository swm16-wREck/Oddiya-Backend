package com.oddiya.service.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oddiya.dto.message.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory implementation of MessagingService for local development and testing
 */
@Service
@ConditionalOnProperty(name = "app.aws.sqs.enabled", havingValue = "false", matchIfMissing = true)
@org.springframework.context.annotation.Profile("!" + com.oddiya.config.ProfileConfiguration.AWS_PROFILE)
@Slf4j
public class LocalMessagingService implements MessagingService {

    private final ObjectMapper objectMapper;
    private final Map<String, List<String>> messageQueues;
    private final Map<String, Integer> messageCounters;
    private final Map<String, Map<String, String>> queueAttributes;

    public LocalMessagingService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.messageQueues = new ConcurrentHashMap<>();
        this.messageCounters = new ConcurrentHashMap<>();
        this.queueAttributes = new ConcurrentHashMap<>();
        
        // Initialize default queues
        initializeQueues();
    }

    private void initializeQueues() {
        String[] queueNames = {
            "oddiya-email-notifications",
            "oddiya-image-processing", 
            "oddiya-analytics-events",
            "oddiya-recommendation-updates",
            "oddiya-video-processing"
        };

        for (String queueName : queueNames) {
            messageQueues.put(queueName, new CopyOnWriteArrayList<>());
            messageCounters.put(queueName, 0);
            
            Map<String, String> attributes = new HashMap<>();
            attributes.put("CreatedTimestamp", String.valueOf(System.currentTimeMillis() / 1000));
            attributes.put("QueueArn", "arn:aws:sqs:local:000000000000:" + queueName);
            attributes.put("ApproximateNumberOfMessages", "0");
            attributes.put("VisibilityTimeout", "300");
            attributes.put("MessageRetentionPeriod", "1209600");
            queueAttributes.put(queueName, attributes);
        }

        log.info("Initialized {} local message queues", queueNames.length);
    }

    @Override
    @Async
    public CompletableFuture<Void> sendEmailMessage(EmailMessage message) {
        return sendMessage("oddiya-email-notifications", message);
    }

    @Override
    @Async
    public CompletableFuture<Void> sendImageProcessingMessage(ImageProcessingMessage message) {
        return sendMessage("oddiya-image-processing", message);
    }

    @Override
    @Async
    public CompletableFuture<Void> sendAnalyticsMessage(AnalyticsMessage message) {
        return sendMessage("oddiya-analytics-events", message);
    }

    @Override
    @Async
    public CompletableFuture<Void> sendRecommendationMessage(RecommendationMessage message) {
        return sendMessage("oddiya-recommendation-updates", message);
    }

    @Override
    @Async
    public CompletableFuture<Void> sendVideoProcessingMessage(VideoProcessingMessage message) {
        return sendMessage("oddiya-video-processing", message);
    }

    @Override
    @Async
    public CompletableFuture<Void> sendMessage(String queueName, Object message, Map<String, String> messageAttributes) {
        return CompletableFuture.runAsync(() -> {
            try {
                ensureQueueExists(queueName);
                
                String messageBody = objectMapper.writeValueAsString(message);
                List<String> queue = messageQueues.get(queueName);
                
                // Add message to queue
                queue.add(messageBody);
                
                // Update counters and attributes
                int newCount = messageCounters.merge(queueName, 1, Integer::sum);
                updateQueueAttribute(queueName, "ApproximateNumberOfMessages", String.valueOf(newCount));
                
                // Log based on message type and priority
                String logLevel = determineLogLevel(message);
                String messageInfo = getMessageInfo(message);
                
                switch (logLevel) {
                    case "INFO" -> log.info("LOCAL QUEUE [{}]: {} - {}", queueName, messageInfo, getSummary(message));
                    case "WARN" -> log.warn("LOCAL QUEUE [{}]: {} - {}", queueName, messageInfo, getSummary(message));
                    case "DEBUG" -> log.debug("LOCAL QUEUE [{}]: {} - {}", queueName, messageInfo, getSummary(message));
                    default -> log.debug("LOCAL QUEUE [{}]: {} - {}", queueName, messageInfo, getSummary(message));
                }

                // Simulate processing delay for high priority messages
                if (isHighPriority(message)) {
                    try {
                        Thread.sleep(100); // Simulate urgent processing
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }

            } catch (JsonProcessingException e) {
                log.error("LOCAL QUEUE [{}]: Failed to serialize message: {}", queueName, e.getMessage());
                throw new RuntimeException("Failed to serialize message", e);
            }
        });
    }

    @Override
    @Async
    public CompletableFuture<Void> sendMessage(String queueName, Object message) {
        return sendMessage(queueName, message, new HashMap<>());
    }

    @Override
    @Async
    public CompletableFuture<Void> sendBatchMessages(String queueName, List<?> messages) {
        return CompletableFuture.runAsync(() -> {
            ensureQueueExists(queueName);
            
            List<String> queue = messageQueues.get(queueName);
            int successCount = 0;
            int failureCount = 0;

            for (Object message : messages) {
                try {
                    String messageBody = objectMapper.writeValueAsString(message);
                    queue.add(messageBody);
                    successCount++;
                } catch (JsonProcessingException e) {
                    log.error("LOCAL QUEUE [{}]: Failed to serialize batch message: {}", queueName, e.getMessage());
                    failureCount++;
                }
            }

            // Update counters
            int newCount = messageCounters.merge(queueName, successCount, Integer::sum);
            updateQueueAttribute(queueName, "ApproximateNumberOfMessages", String.valueOf(newCount));

            log.info("LOCAL QUEUE [{}]: Batch completed - {} successful, {} failed", 
                queueName, successCount, failureCount);
        });
    }

    @Override
    @Async
    public CompletableFuture<Void> sendBatchEmailMessages(List<EmailMessage> messages) {
        return sendBatchMessages("oddiya-email-notifications", messages);
    }

    @Override
    @Async
    public CompletableFuture<Void> sendBatchAnalyticsMessages(List<AnalyticsMessage> messages) {
        return sendBatchMessages("oddiya-analytics-events", messages);
    }

    @Override
    public List<String> receiveMessages(String queueName, int maxMessages) {
        ensureQueueExists(queueName);
        
        List<String> queue = messageQueues.get(queueName);
        List<String> messages = new ArrayList<>();
        
        int messagesToReceive = Math.min(maxMessages, queue.size());
        
        for (int i = 0; i < messagesToReceive; i++) {
            if (!queue.isEmpty()) {
                String message = queue.remove(0); // FIFO
                messages.add(message);
            }
        }

        // Update counters
        if (!messages.isEmpty()) {
            int newCount = messageCounters.merge(queueName, -messages.size(), Integer::sum);
            updateQueueAttribute(queueName, "ApproximateNumberOfMessages", String.valueOf(Math.max(0, newCount)));
            
            log.debug("LOCAL QUEUE [{}]: Received {} messages", queueName, messages.size());
        }

        return messages;
    }

    @Override
    public List<String> receiveMessages(String queueName) {
        return receiveMessages(queueName, 10);
    }

    @Override
    public String getQueueUrl(String queueName) {
        // Return a mock local URL
        return "http://localhost:9324/000000000000/" + queueName;
    }

    @Override
    public boolean queueExists(String queueName) {
        return messageQueues.containsKey(queueName);
    }

    @Override
    public int getMessageCount(String queueName) {
        if (!queueExists(queueName)) {
            return 0;
        }
        return messageCounters.getOrDefault(queueName, 0);
    }

    @Override
    public Map<String, String> getQueueAttributes(String queueName) {
        if (!queueExists(queueName)) {
            return new HashMap<>();
        }
        return new HashMap<>(queueAttributes.get(queueName));
    }

    // Helper methods

    private void ensureQueueExists(String queueName) {
        if (!messageQueues.containsKey(queueName)) {
            messageQueues.put(queueName, new CopyOnWriteArrayList<>());
            messageCounters.put(queueName, 0);
            
            Map<String, String> attributes = new HashMap<>();
            attributes.put("CreatedTimestamp", String.valueOf(System.currentTimeMillis() / 1000));
            attributes.put("QueueArn", "arn:aws:sqs:local:000000000000:" + queueName);
            attributes.put("ApproximateNumberOfMessages", "0");
            attributes.put("VisibilityTimeout", "300");
            attributes.put("MessageRetentionPeriod", "1209600");
            queueAttributes.put(queueName, attributes);
            
            log.info("Created local queue: {}", queueName);
        }
    }

    private void updateQueueAttribute(String queueName, String attributeName, String value) {
        queueAttributes.computeIfPresent(queueName, (key, attributes) -> {
            attributes.put(attributeName, value);
            return attributes;
        });
    }

    private String determineLogLevel(Object message) {
        if (message instanceof EmailMessage emailMsg) {
            return switch (emailMsg.getPriority()) {
                case URGENT -> "WARN";
                case HIGH -> "INFO";
                default -> "DEBUG";
            };
        } else if (message instanceof ImageProcessingMessage imgMsg) {
            return switch (imgMsg.getPriority()) {
                case URGENT -> "WARN";
                case HIGH -> "INFO";
                default -> "DEBUG";
            };
        } else if (message instanceof AnalyticsMessage analyticsMsg) {
            return switch (analyticsMsg.getPriority()) {
                case CRITICAL -> "WARN";
                case HIGH -> "INFO";
                default -> "DEBUG";
            };
        } else if (message instanceof RecommendationMessage recMsg) {
            return switch (recMsg.getPriority()) {
                case URGENT -> "WARN";
                case HIGH -> "INFO";
                default -> "DEBUG";
            };
        } else if (message instanceof VideoProcessingMessage videoMsg) {
            return switch (videoMsg.getPriority()) {
                case URGENT -> "WARN";
                case HIGH -> "INFO";
                default -> "DEBUG";
            };
        }
        return "DEBUG";
    }

    private String getMessageInfo(Object message) {
        if (message instanceof EmailMessage emailMsg) {
            return String.format("Email [%s] to %s", 
                emailMsg.getTemplateType(), emailMsg.getRecipientEmail());
        } else if (message instanceof ImageProcessingMessage imgMsg) {
            return String.format("Image [%s] processing for %s", 
                imgMsg.getProcessingType(), imgMsg.getImageId());
        } else if (message instanceof AnalyticsMessage analyticsMsg) {
            return String.format("Analytics [%s] event %s", 
                analyticsMsg.getEventType(), analyticsMsg.getEventName());
        } else if (message instanceof RecommendationMessage recMsg) {
            return String.format("Recommendation [%s] for user %s", 
                recMsg.getRecommendationType(), recMsg.getUserId());
        } else if (message instanceof VideoProcessingMessage videoMsg) {
            return String.format("Video [%s] processing for %s", 
                videoMsg.getProcessingType(), videoMsg.getVideoId());
        }
        return "Generic message";
    }

    private String getSummary(Object message) {
        if (message instanceof EmailMessage emailMsg) {
            return String.format("Subject: %s", 
                emailMsg.getSubject() != null ? emailMsg.getSubject().substring(0, Math.min(50, emailMsg.getSubject().length())) : "N/A");
        } else if (message instanceof ImageProcessingMessage imgMsg) {
            return String.format("Source: %s", imgMsg.getSourceS3Key());
        } else if (message instanceof AnalyticsMessage analyticsMsg) {
            return String.format("User: %s, Session: %s", 
                analyticsMsg.getUserId(), analyticsMsg.getSessionId());
        } else if (message instanceof RecommendationMessage recMsg) {
            return String.format("Travel Plan: %s", recMsg.getTravelPlanId());
        } else if (message instanceof VideoProcessingMessage videoMsg) {
            return String.format("Source: %s", videoMsg.getSourceS3Key());
        }
        return "";
    }

    private boolean isHighPriority(Object message) {
        if (message instanceof EmailMessage emailMsg) {
            return emailMsg.getPriority().getLevel() >= 3;
        } else if (message instanceof ImageProcessingMessage imgMsg) {
            return imgMsg.getPriority().getLevel() >= 3;
        } else if (message instanceof AnalyticsMessage analyticsMsg) {
            return analyticsMsg.getPriority().getLevel() >= 3;
        } else if (message instanceof RecommendationMessage recMsg) {
            return recMsg.getPriority().getLevel() >= 3;
        } else if (message instanceof VideoProcessingMessage videoMsg) {
            return videoMsg.getPriority().getLevel() >= 3;
        }
        return false;
    }

    /**
     * Get all queue statistics for monitoring
     */
    public Map<String, Object> getQueueStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        for (String queueName : messageQueues.keySet()) {
            Map<String, Object> queueStats = new HashMap<>();
            queueStats.put("messageCount", getMessageCount(queueName));
            queueStats.put("queueUrl", getQueueUrl(queueName));
            queueStats.put("lastUpdated", LocalDateTime.now());
            stats.put(queueName, queueStats);
        }
        
        return stats;
    }

    /**
     * Clear all messages from a queue (for testing)
     */
    public void clearQueue(String queueName) {
        if (queueExists(queueName)) {
            messageQueues.get(queueName).clear();
            messageCounters.put(queueName, 0);
            updateQueueAttribute(queueName, "ApproximateNumberOfMessages", "0");
            log.info("Cleared local queue: {}", queueName);
        }
    }

    /**
     * Clear all queues (for testing)
     */
    public void clearAllQueues() {
        for (String queueName : messageQueues.keySet()) {
            clearQueue(queueName);
        }
        log.info("Cleared all local queues");
    }
}