package com.oddiya.repository.dynamodb.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.model.ConsumedCapacity;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Metrics collection and performance monitoring for DynamoDB repositories.
 * Tracks operation performance, capacity consumption, and error rates.
 */
@Component
@Slf4j
public class DynamoDBRepositoryMetrics {

    // Operation counters
    private final ConcurrentHashMap<String, AtomicLong> operationCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> errorCounts = new ConcurrentHashMap<>();
    
    // Performance metrics
    private final ConcurrentHashMap<String, Long> totalResponseTime = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> totalCapacityUnits = new ConcurrentHashMap<>();
    
    // Recent operation tracking
    private final ConcurrentHashMap<String, Instant> lastOperationTime = new ConcurrentHashMap<>();

    /**
     * Record the start of an operation
     */
    public OperationTimer startOperation(String operationType, String tableName) {
        String key = operationType + ":" + tableName;
        operationCounts.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();
        lastOperationTime.put(key, Instant.now());
        
        return new OperationTimer(key);
    }

    /**
     * Record successful operation completion
     */
    public void recordSuccess(String operationKey, Duration duration) {
        totalResponseTime.merge(operationKey, duration.toMillis(), Long::sum);
        
        if (log.isDebugEnabled()) {
            log.debug("Operation {} completed successfully in {}ms", operationKey, duration.toMillis());
        }
    }

    /**
     * Record operation error
     */
    public void recordError(String operationKey, Duration duration, Throwable error) {
        errorCounts.computeIfAbsent(operationKey, k -> new AtomicLong(0)).incrementAndGet();
        totalResponseTime.merge(operationKey, duration.toMillis(), Long::sum);
        
        log.warn("Operation {} failed after {}ms: {}", operationKey, duration.toMillis(), error.getMessage());
    }

    /**
     * Record capacity units consumed
     */
    public void recordCapacityConsumption(String operationKey, ConsumedCapacity consumedCapacity) {
        if (consumedCapacity != null && consumedCapacity.capacityUnits() != null) {
            totalCapacityUnits.computeIfAbsent(operationKey, k -> new AtomicLong(0))
                             .addAndGet(consumedCapacity.capacityUnits().longValue());
        }
    }

    /**
     * Get operation statistics
     */
    public OperationStats getOperationStats(String operationType, String tableName) {
        String key = operationType + ":" + tableName;
        
        long totalOps = operationCounts.getOrDefault(key, new AtomicLong(0)).get();
        long totalErrors = errorCounts.getOrDefault(key, new AtomicLong(0)).get();
        long totalTime = totalResponseTime.getOrDefault(key, 0L);
        long totalCapacity = totalCapacityUnits.getOrDefault(key, new AtomicLong(0)).get();
        
        double avgResponseTime = totalOps > 0 ? (double) totalTime / totalOps : 0.0;
        double errorRate = totalOps > 0 ? (double) totalErrors / totalOps * 100 : 0.0;
        double avgCapacityPerOp = totalOps > 0 ? (double) totalCapacity / totalOps : 0.0;
        
        return new OperationStats(
            totalOps,
            totalErrors,
            errorRate,
            avgResponseTime,
            totalCapacity,
            avgCapacityPerOp,
            lastOperationTime.get(key)
        );
    }

    /**
     * Get all repository metrics summary
     */
    public void logMetricsSummary() {
        log.info("=== DynamoDB Repository Metrics Summary ===");
        
        operationCounts.forEach((key, count) -> {
            String[] parts = key.split(":");
            String operation = parts[0];
            String table = parts.length > 1 ? parts[1] : "unknown";
            
            OperationStats stats = getOperationStats(operation, table);
            
            log.info("Operation: {}, Table: {}", operation, table);
            log.info("  Total Operations: {}", stats.getTotalOperations());
            log.info("  Error Count: {} ({}%)", stats.getTotalErrors(), String.format("%.2f", stats.getErrorRate()));
            log.info("  Avg Response Time: {}ms", String.format("%.2f", stats.getAvgResponseTime()));
            log.info("  Total Capacity Units: {}", stats.getTotalCapacityUnits());
            log.info("  Avg Capacity per Op: {}", String.format("%.2f", stats.getAvgCapacityPerOperation()));
            log.info("  Last Operation: {}", stats.getLastOperationTime());
            log.info("---");
        });
    }

    /**
     * Reset all metrics
     */
    public void resetMetrics() {
        operationCounts.clear();
        errorCounts.clear();
        totalResponseTime.clear();
        totalCapacityUnits.clear();
        lastOperationTime.clear();
        log.info("DynamoDB repository metrics reset");
    }

    /**
     * Check if any operation has high error rate
     */
    public boolean hasHighErrorRate(double threshold) {
        return operationCounts.keySet().stream()
                .anyMatch(key -> {
                    String[] parts = key.split(":");
                    String operation = parts[0];
                    String table = parts.length > 1 ? parts[1] : "unknown";
                    OperationStats stats = getOperationStats(operation, table);
                    return stats.getErrorRate() > threshold;
                });
    }

    /**
     * Timer class for measuring operation duration
     */
    public class OperationTimer {
        private final String operationKey;
        private final Instant startTime;

        public OperationTimer(String operationKey) {
            this.operationKey = operationKey;
            this.startTime = Instant.now();
        }

        public void recordSuccess() {
            Duration duration = Duration.between(startTime, Instant.now());
            DynamoDBRepositoryMetrics.this.recordSuccess(operationKey, duration);
        }

        public void recordError(Throwable error) {
            Duration duration = Duration.between(startTime, Instant.now());
            DynamoDBRepositoryMetrics.this.recordError(operationKey, duration, error);
        }
    }

    /**
     * Operation statistics data class
     */
    public static class OperationStats {
        private final long totalOperations;
        private final long totalErrors;
        private final double errorRate;
        private final double avgResponseTime;
        private final long totalCapacityUnits;
        private final double avgCapacityPerOperation;
        private final Instant lastOperationTime;

        public OperationStats(long totalOperations, long totalErrors, double errorRate,
                            double avgResponseTime, long totalCapacityUnits,
                            double avgCapacityPerOperation, Instant lastOperationTime) {
            this.totalOperations = totalOperations;
            this.totalErrors = totalErrors;
            this.errorRate = errorRate;
            this.avgResponseTime = avgResponseTime;
            this.totalCapacityUnits = totalCapacityUnits;
            this.avgCapacityPerOperation = avgCapacityPerOperation;
            this.lastOperationTime = lastOperationTime;
        }

        // Getters
        public long getTotalOperations() { return totalOperations; }
        public long getTotalErrors() { return totalErrors; }
        public double getErrorRate() { return errorRate; }
        public double getAvgResponseTime() { return avgResponseTime; }
        public long getTotalCapacityUnits() { return totalCapacityUnits; }
        public double getAvgCapacityPerOperation() { return avgCapacityPerOperation; }
        public Instant getLastOperationTime() { return lastOperationTime; }
    }
}