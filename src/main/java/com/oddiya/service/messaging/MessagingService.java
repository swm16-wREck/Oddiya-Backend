package com.oddiya.service.messaging;

import com.oddiya.dto.message.EmailMessage;
import com.oddiya.dto.message.ImageProcessingMessage;
import com.oddiya.dto.message.AnalyticsMessage;
import com.oddiya.dto.message.RecommendationMessage;
import com.oddiya.dto.message.VideoProcessingMessage;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Messaging service interface for async operations in Oddiya.
 * Provides methods for sending different types of messages to queues.
 */
public interface MessagingService {

    /**
     * Send an email message asynchronously
     * @param message the email message to send
     * @return CompletableFuture for async processing
     */
    CompletableFuture<Void> sendEmailMessage(EmailMessage message);

    /**
     * Send an image processing message asynchronously
     * @param message the image processing message to send
     * @return CompletableFuture for async processing
     */
    CompletableFuture<Void> sendImageProcessingMessage(ImageProcessingMessage message);

    /**
     * Send an analytics message asynchronously
     * @param message the analytics message to send
     * @return CompletableFuture for async processing
     */
    CompletableFuture<Void> sendAnalyticsMessage(AnalyticsMessage message);

    /**
     * Send a recommendation message asynchronously
     * @param message the recommendation message to send
     * @return CompletableFuture for async processing
     */
    CompletableFuture<Void> sendRecommendationMessage(RecommendationMessage message);

    /**
     * Send a video processing message asynchronously
     * @param message the video processing message to send
     * @return CompletableFuture for async processing
     */
    CompletableFuture<Void> sendVideoProcessingMessage(VideoProcessingMessage message);

    /**
     * Send a generic message to a specific queue
     * @param queueName the queue to send to
     * @param message the message object to send
     * @param messageAttributes additional message attributes
     * @return CompletableFuture for async processing
     */
    CompletableFuture<Void> sendMessage(String queueName, Object message, Map<String, String> messageAttributes);

    /**
     * Send a generic message to a specific queue (without attributes)
     * @param queueName the queue to send to
     * @param message the message object to send
     * @return CompletableFuture for async processing
     */
    CompletableFuture<Void> sendMessage(String queueName, Object message);

    /**
     * Send batch messages to a specific queue
     * @param queueName the queue to send to
     * @param messages list of messages to send
     * @return CompletableFuture for async processing
     */
    CompletableFuture<Void> sendBatchMessages(String queueName, List<?> messages);

    /**
     * Send batch email messages
     * @param messages list of email messages to send
     * @return CompletableFuture for async processing
     */
    CompletableFuture<Void> sendBatchEmailMessages(List<EmailMessage> messages);

    /**
     * Send batch analytics messages
     * @param messages list of analytics messages to send
     * @return CompletableFuture for async processing
     */
    CompletableFuture<Void> sendBatchAnalyticsMessages(List<AnalyticsMessage> messages);

    /**
     * Receive messages from a queue
     * @param queueName the queue to receive from
     * @param maxMessages maximum number of messages to retrieve
     * @return List of messages received
     */
    List<String> receiveMessages(String queueName, int maxMessages);

    /**
     * Receive messages from a queue with default max messages
     * @param queueName the queue to receive from
     * @return List of messages received
     */
    List<String> receiveMessages(String queueName);

    /**
     * Get queue URL for a given queue name
     * @param queueName the queue name
     * @return the queue URL
     */
    String getQueueUrl(String queueName);

    /**
     * Check if a queue exists
     * @param queueName the queue name
     * @return true if queue exists, false otherwise
     */
    boolean queueExists(String queueName);

    /**
     * Get message count in a queue
     * @param queueName the queue name
     * @return approximate number of messages in the queue
     */
    int getMessageCount(String queueName);

    /**
     * Get queue attributes as a map
     * @param queueName the queue name
     * @return map of queue attributes
     */
    Map<String, String> getQueueAttributes(String queueName);
}