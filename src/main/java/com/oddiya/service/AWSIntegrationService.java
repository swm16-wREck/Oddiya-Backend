package com.oddiya.service;

import com.oddiya.config.aws.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.HashMap;
import java.util.Optional;

/**
 * AWS Integration Service
 * 
 * Demonstrates how to use the AWS configuration classes in application services.
 * This service provides unified access to AWS services with proper error handling
 * and fallback mechanisms.
 */
@Service
@ConditionalOnProperty(name = "spring.profiles.active", havingValue = "aws", matchIfMissing = false)
@Slf4j
public class AWSIntegrationService {

    private final Optional<S3Config.S3FileManager> s3FileManager;
    private final Optional<SQSConfig.SQSMessageSender> sqsMessageSender;
    private final Optional<CloudWatchConfig.CloudWatchMetricsCollector> metricsCollector;

    @Autowired
    public AWSIntegrationService(
            @Autowired(required = false) S3Config.S3FileManager s3FileManager,
            @Autowired(required = false) SQSConfig.SQSMessageSender sqsMessageSender,
            @Autowired(required = false) CloudWatchConfig.CloudWatchMetricsCollector metricsCollector) {
        
        this.s3FileManager = Optional.ofNullable(s3FileManager);
        this.sqsMessageSender = Optional.ofNullable(sqsMessageSender);
        this.metricsCollector = Optional.ofNullable(metricsCollector);
        
        log.info("AWS Integration Service initialized - S3: {}, SQS: {}, CloudWatch: {}", 
                this.s3FileManager.isPresent(), 
                this.sqsMessageSender.isPresent(), 
                this.metricsCollector.isPresent());
    }

    // S3 Operations
    
    /**
     * Generate a presigned URL for file upload
     */
    public String generateUploadUrl(String key) {
        return s3FileManager
                .map(manager -> manager.generatePresignedUploadUrl(key))
                .orElse(null);
    }

    /**
     * Generate a presigned URL for file download
     */
    public String generateDownloadUrl(String key) {
        return s3FileManager
                .map(manager -> manager.generatePresignedDownloadUrl(key))
                .orElse(null);
    }

    /**
     * Check if a file exists in S3
     */
    public boolean fileExists(String key) {
        return s3FileManager
                .map(manager -> manager.fileExists(key))
                .orElse(false);
    }

    /**
     * Delete a file from S3
     */
    public void deleteFile(String key) {
        s3FileManager.ifPresent(manager -> manager.deleteFile(key));
    }

    /**
     * Get file size from S3
     */
    public long getFileSize(String key) {
        return s3FileManager
                .map(manager -> manager.getFileSize(key))
                .orElse(-1L);
    }

    // SQS Operations
    
    /**
     * Send email notification message
     */
    public void sendEmailNotification(String to, String subject, String body) {
        Map<String, Object> message = new HashMap<>();
        message.put("to", to);
        message.put("subject", subject);
        message.put("body", body);
        message.put("timestamp", System.currentTimeMillis());
        
        sqsMessageSender.ifPresent(sender -> {
            try {
                sender.sendEmailNotification(message);
                recordMetric("EmailNotificationsSent", 1.0);
            } catch (Exception e) {
                log.error("Failed to send email notification: {}", e.getMessage());
                recordMetric("EmailNotificationErrors", 1.0);
            }
        });
    }

    /**
     * Send image processing message
     */
    public void sendImageProcessingMessage(String imageKey, String userId, String operation) {
        Map<String, Object> message = new HashMap<>();
        message.put("imageKey", imageKey);
        message.put("userId", userId);
        message.put("operation", operation);
        message.put("timestamp", System.currentTimeMillis());
        
        sqsMessageSender.ifPresent(sender -> {
            try {
                sender.sendImageProcessingMessage(message);
                recordMetric("ImageProcessingJobsQueued", 1.0);
            } catch (Exception e) {
                log.error("Failed to send image processing message: {}", e.getMessage());
                recordMetric("ImageProcessingErrors", 1.0);
            }
        });
    }

    /**
     * Send analytics event
     */
    public void sendAnalyticsEvent(String eventType, String userId, Map<String, Object> eventData) {
        Map<String, Object> message = new HashMap<>();
        message.put("eventType", eventType);
        message.put("userId", userId);
        message.put("eventData", eventData);
        message.put("timestamp", System.currentTimeMillis());
        
        sqsMessageSender.ifPresent(sender -> {
            try {
                sender.sendAnalyticsEvent(message);
                recordMetric("AnalyticsEventsQueued", 1.0);
            } catch (Exception e) {
                log.error("Failed to send analytics event: {}", e.getMessage());
                recordMetric("AnalyticsEventErrors", 1.0);
            }
        });
    }

    /**
     * Send recommendation update message
     */
    public void sendRecommendationUpdate(String userId, String type, Map<String, Object> data) {
        Map<String, Object> message = new HashMap<>();
        message.put("userId", userId);
        message.put("type", type);
        message.put("data", data);
        message.put("timestamp", System.currentTimeMillis());
        
        sqsMessageSender.ifPresent(sender -> {
            try {
                sender.sendRecommendationUpdate(message);
                recordMetric("RecommendationUpdatesQueued", 1.0);
            } catch (Exception e) {
                log.error("Failed to send recommendation update: {}", e.getMessage());
                recordMetric("RecommendationUpdateErrors", 1.0);
            }
        });
    }

    /**
     * Send video processing message
     */
    public void sendVideoProcessingMessage(String videoKey, String userId, String operation, Map<String, Object> params) {
        Map<String, Object> message = new HashMap<>();
        message.put("videoKey", videoKey);
        message.put("userId", userId);
        message.put("operation", operation);
        message.put("params", params);
        message.put("timestamp", System.currentTimeMillis());
        
        sqsMessageSender.ifPresent(sender -> {
            try {
                sender.sendVideoProcessingMessage(message);
                recordMetric("VideoProcessingJobsQueued", 1.0);
            } catch (Exception e) {
                log.error("Failed to send video processing message: {}", e.getMessage());
                recordMetric("VideoProcessingErrors", 1.0);
            }
        });
    }

    // CloudWatch Metrics Operations
    
    /**
     * Record a simple count metric
     */
    public void recordMetric(String metricName, double value) {
        metricsCollector.ifPresent(collector -> {
            try {
                collector.recordCount(metricName, value);
            } catch (Exception e) {
                log.error("Failed to record metric {}: {}", metricName, e.getMessage());
            }
        });
    }

    /**
     * Record a timer metric (duration in milliseconds)
     */
    public void recordTimer(String metricName, long durationMs) {
        metricsCollector.ifPresent(collector -> {
            try {
                collector.recordTimer(metricName, durationMs);
            } catch (Exception e) {
                log.error("Failed to record timer metric {}: {}", metricName, e.getMessage());
            }
        });
    }

    /**
     * Record a gauge metric with dimensions
     */
    public void recordGauge(String metricName, double value, Map<String, String> dimensions) {
        metricsCollector.ifPresent(collector -> {
            try {
                // Note: This would need to be implemented in the CloudWatchMetricsCollector
                // collector.recordGauge(metricName, value, StandardUnit.NONE, dimensions);
                collector.recordGauge(metricName, value);
            } catch (Exception e) {
                log.error("Failed to record gauge metric {}: {}", metricName, e.getMessage());
            }
        });
    }

    /**
     * Increment a counter
     */
    public void incrementCounter(String counterName) {
        metricsCollector.ifPresent(collector -> {
            try {
                collector.incrementCounter(counterName);
            } catch (Exception e) {
                log.error("Failed to increment counter {}: {}", counterName, e.getMessage());
            }
        });
    }

    // Utility Methods
    
    /**
     * Check if AWS services are available
     */
    public boolean isAWSAvailable() {
        return s3FileManager.isPresent() || sqsMessageSender.isPresent() || metricsCollector.isPresent();
    }

    /**
     * Check if S3 is available
     */
    public boolean isS3Available() {
        return s3FileManager.isPresent();
    }

    /**
     * Check if SQS is available
     */
    public boolean isSQSAvailable() {
        return sqsMessageSender.isPresent();
    }

    /**
     * Check if CloudWatch is available
     */
    public boolean isCloudWatchAvailable() {
        return metricsCollector.isPresent();
    }

    /**
     * Get AWS service status
     */
    public Map<String, Boolean> getServiceStatus() {
        Map<String, Boolean> status = new HashMap<>();
        status.put("s3", isS3Available());
        status.put("sqs", isSQSAvailable());
        status.put("cloudwatch", isCloudWatchAvailable());
        status.put("overall", isAWSAvailable());
        return status;
    }
}