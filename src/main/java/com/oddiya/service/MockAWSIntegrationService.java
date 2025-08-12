package com.oddiya.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Mock AWS Integration Service for Local Development
 * 
 * Provides the same interface as AWSIntegrationService but with mock implementations
 * for local development and testing. This allows the application to work without
 * requiring actual AWS credentials or services.
 */
@Service
@Profile({"local", "test", "h2"})
@ConditionalOnProperty(name = "app.aws.mock.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class MockAWSIntegrationService {

    private final Map<String, Boolean> mockFiles = new ConcurrentHashMap<>();
    private final Map<String, Long> mockFileSizes = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> mockCounters = new ConcurrentHashMap<>();
    
    public MockAWSIntegrationService() {
        log.info("Mock AWS Integration Service initialized for local development");
        
        // Initialize some mock files
        mockFiles.put("test-image.jpg", true);
        mockFiles.put("sample-video.mp4", true);
        mockFileSizes.put("test-image.jpg", 1024L * 500); // 500KB
        mockFileSizes.put("sample-video.mp4", 1024L * 1024 * 10); // 10MB
    }

    // S3 Operations
    
    public String generateUploadUrl(String key) {
        log.debug("Mock: Generating upload URL for key: {}", key);
        return String.format("http://localhost:9000/mock-bucket/%s?upload=true&expires=%d", 
                key, System.currentTimeMillis() + 3600000);
    }

    public String generateDownloadUrl(String key) {
        log.debug("Mock: Generating download URL for key: {}", key);
        return String.format("http://localhost:9000/mock-bucket/%s", key);
    }

    public boolean fileExists(String key) {
        boolean exists = mockFiles.getOrDefault(key, false);
        log.debug("Mock: File exists check for {}: {}", key, exists);
        return exists;
    }

    public void deleteFile(String key) {
        log.info("Mock: Deleting file: {}", key);
        mockFiles.remove(key);
        mockFileSizes.remove(key);
    }

    public long getFileSize(String key) {
        long size = mockFileSizes.getOrDefault(key, 1024L);
        log.debug("Mock: File size for {}: {} bytes", key, size);
        return size;
    }

    // SQS Operations
    
    public void sendEmailNotification(String to, String subject, String body) {
        log.info("Mock: Sending email notification to '{}' with subject '{}'", to, subject);
        recordMetric("EmailNotificationsSent", 1.0);
        
        // Simulate processing time
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void sendImageProcessingMessage(String imageKey, String userId, String operation) {
        log.info("Mock: Queuing image processing - Key: '{}', User: '{}', Operation: '{}'", 
                imageKey, userId, operation);
        recordMetric("ImageProcessingJobsQueued", 1.0);
        
        // Simulate async processing
        new Thread(() -> {
            try {
                Thread.sleep(2000); // Simulate 2 second processing time
                log.info("Mock: Completed image processing for key: {}", imageKey);
                recordMetric("ImageProcessingJobsCompleted", 1.0);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    public void sendAnalyticsEvent(String eventType, String userId, Map<String, Object> eventData) {
        log.info("Mock: Recording analytics event - Type: '{}', User: '{}', Data: {}", 
                eventType, userId, eventData.size() + " properties");
        recordMetric("AnalyticsEventsQueued", 1.0);
        
        // Log event details in debug mode
        if (log.isDebugEnabled()) {
            eventData.forEach((key, value) -> 
                log.debug("  {}: {}", key, value));
        }
    }

    public void sendRecommendationUpdate(String userId, String type, Map<String, Object> data) {
        log.info("Mock: Queuing recommendation update - User: '{}', Type: '{}', Data: {} properties", 
                userId, type, data.size());
        recordMetric("RecommendationUpdatesQueued", 1.0);
        
        // Simulate processing
        new Thread(() -> {
            try {
                Thread.sleep(1000);
                log.info("Mock: Completed recommendation update for user: {}", userId);
                recordMetric("RecommendationUpdatesCompleted", 1.0);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    public void sendVideoProcessingMessage(String videoKey, String userId, String operation, Map<String, Object> params) {
        log.info("Mock: Queuing video processing - Key: '{}', User: '{}', Operation: '{}', Params: {} items", 
                videoKey, userId, operation, params.size());
        recordMetric("VideoProcessingJobsQueued", 1.0);
        
        // Simulate long-running video processing
        new Thread(() -> {
            try {
                Thread.sleep(5000); // Simulate 5 second processing time
                log.info("Mock: Completed video processing for key: {}", videoKey);
                recordMetric("VideoProcessingJobsCompleted", 1.0);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    // CloudWatch Metrics Operations
    
    public void recordMetric(String metricName, double value) {
        log.debug("Mock: Recording metric '{}' = {}", metricName, value);
        mockCounters.computeIfAbsent(metricName, k -> new AtomicLong(0))
                .addAndGet((long) value);
    }

    public void recordTimer(String metricName, long durationMs) {
        log.debug("Mock: Recording timer metric '{}' = {}ms", metricName, durationMs);
        mockCounters.computeIfAbsent(metricName + "_Timer", k -> new AtomicLong(0))
                .addAndGet(durationMs);
    }

    public void recordGauge(String metricName, double value, Map<String, String> dimensions) {
        log.debug("Mock: Recording gauge metric '{}' = {} with {} dimensions", 
                metricName, value, dimensions.size());
        
        if (log.isDebugEnabled() && !dimensions.isEmpty()) {
            dimensions.forEach((key, val) -> 
                log.debug("  Dimension {}: {}", key, val));
        }
        
        mockCounters.computeIfAbsent(metricName + "_Gauge", k -> new AtomicLong(0))
                .set((long) value);
    }

    public void incrementCounter(String counterName) {
        long newValue = mockCounters.computeIfAbsent(counterName, k -> new AtomicLong(0))
                .incrementAndGet();
        log.debug("Mock: Incremented counter '{}' to {}", counterName, newValue);
    }

    // Utility Methods
    
    public boolean isAWSAvailable() {
        return true; // Mock is always "available"
    }

    public boolean isS3Available() {
        return true;
    }

    public boolean isSQSAvailable() {
        return true;
    }

    public boolean isCloudWatchAvailable() {
        return true;
    }

    public Map<String, Boolean> getServiceStatus() {
        Map<String, Boolean> status = new HashMap<>();
        status.put("s3", true);
        status.put("sqs", true);
        status.put("cloudwatch", true);
        status.put("overall", true);
        status.put("mock", true);
        return status;
    }

    // Mock-specific utility methods
    
    /**
     * Get current counter value
     */
    public long getCounterValue(String counterName) {
        AtomicLong counter = mockCounters.get(counterName);
        return counter != null ? counter.get() : 0;
    }

    /**
     * Reset all counters (useful for testing)
     */
    public void resetCounters() {
        log.info("Mock: Resetting all counters");
        mockCounters.clear();
    }

    /**
     * Add a mock file for testing
     */
    public void addMockFile(String key, long size) {
        log.info("Mock: Adding mock file '{}' with size {} bytes", key, size);
        mockFiles.put(key, true);
        mockFileSizes.put(key, size);
    }

    /**
     * Remove a mock file
     */
    public void removeMockFile(String key) {
        log.info("Mock: Removing mock file '{}'", key);
        mockFiles.remove(key);
        mockFileSizes.remove(key);
    }

    /**
     * Get all mock files
     */
    public Map<String, Long> getAllMockFiles() {
        return new HashMap<>(mockFileSizes);
    }

    /**
     * Get all counters
     */
    public Map<String, Long> getAllCounters() {
        Map<String, Long> result = new HashMap<>();
        mockCounters.forEach((key, atomicValue) -> result.put(key, atomicValue.get()));
        return result;
    }
}