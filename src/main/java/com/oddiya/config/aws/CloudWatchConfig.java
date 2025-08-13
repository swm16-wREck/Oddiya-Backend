package com.oddiya.config.aws;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.*;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Configuration
@ConfigurationProperties(prefix = "app.aws.cloudwatch")
@ConditionalOnProperty(name = "app.aws.cloudwatch.enabled", havingValue = "true", matchIfMissing = false)
@ConditionalOnBean(CloudWatchClient.class)
@Data
@Slf4j
public class CloudWatchConfig {

    private String namespace = "Oddiya";
    private int batchSize = 20;
    private Duration flushInterval = Duration.ofSeconds(60);
    private boolean enableDetailedMetrics = false;
    private boolean enableCustomDashboard = false;
    private boolean enableAlarms = false;
    
    private MetricDefinitions metricDefinitions = new MetricDefinitions();
    private AlarmConfig alarmConfig = new AlarmConfig();

    @Bean
    public CloudWatchMetricsCollector cloudWatchMetricsCollector(CloudWatchClient cloudWatchClient) {
        return new CloudWatchMetricsCollector(cloudWatchClient, this);
    }

    @Bean
    public CloudWatchDashboardManager cloudWatchDashboardManager(CloudWatchClient cloudWatchClient) {
        return new CloudWatchDashboardManager(cloudWatchClient, this);
    }

    @Bean
    public CloudWatchAlarmManager cloudWatchAlarmManager(CloudWatchClient cloudWatchClient) {
        return new CloudWatchAlarmManager(cloudWatchClient, this);
    }

    @PostConstruct
    public void initializeMetricNames() {
        log.info("Initializing CloudWatch metric definitions");
        if (metricDefinitions.getApplicationMetrics().isEmpty()) {
            // Initialize default application metrics
            Map<String, String> appMetrics = new HashMap<>();
            appMetrics.put("UserRegistrations", "Count of new user registrations");
            appMetrics.put("TravelPlanCreations", "Count of travel plan creations");
            appMetrics.put("PlaceSearches", "Count of place searches");
            appMetrics.put("VideoUploads", "Count of video uploads");
            appMetrics.put("ApiRequestCount", "Count of API requests");
            appMetrics.put("ApiResponseTime", "Average API response time in milliseconds");
            appMetrics.put("DatabaseConnections", "Number of active database connections");
            appMetrics.put("CacheHitRatio", "Cache hit ratio percentage");
            metricDefinitions.setApplicationMetrics(appMetrics);
        }

        if (metricDefinitions.getPerformanceMetrics().isEmpty()) {
            Map<String, String> perfMetrics = new HashMap<>();
            perfMetrics.put("CPUUtilization", "CPU utilization percentage");
            perfMetrics.put("MemoryUtilization", "Memory utilization percentage");
            perfMetrics.put("DiskIOPS", "Disk I/O operations per second");
            perfMetrics.put("NetworkBytesIn", "Network bytes received");
            perfMetrics.put("NetworkBytesOut", "Network bytes sent");
            metricDefinitions.setPerformanceMetrics(perfMetrics);
        }

        if (metricDefinitions.getBusinessMetrics().isEmpty()) {
            Map<String, String> bizMetrics = new HashMap<>();
            bizMetrics.put("ActiveUsers", "Number of active users");
            bizMetrics.put("RevenueMetrics", "Revenue-related metrics");
            bizMetrics.put("ConversionRate", "User conversion rate");
            bizMetrics.put("UserEngagement", "User engagement metrics");
            metricDefinitions.setBusinessMetrics(bizMetrics);
        }
    }

    @Data
    public static class MetricDefinitions {
        private Map<String, String> applicationMetrics = new HashMap<>();
        private Map<String, String> performanceMetrics = new HashMap<>();
        private Map<String, String> businessMetrics = new HashMap<>();
        private Map<String, String> customMetrics = new HashMap<>();
    }

    @Data
    public static class AlarmConfig {
        private boolean enableHighErrorRate = true;
        private boolean enableHighLatency = true;
        private boolean enableHighCpuUsage = true;
        private boolean enableHighMemoryUsage = true;
        private double errorRateThreshold = 5.0; // 5%
        private double latencyThreshold = 2000.0; // 2 seconds
        private double cpuThreshold = 80.0; // 80%
        private double memoryThreshold = 85.0; // 85%
        private int evaluationPeriods = 2;
        private int datapointsToAlarm = 2;
    }

    /**
     * CloudWatch Metrics Collector for collecting and sending metrics
     */
    public static class CloudWatchMetricsCollector {

        private final CloudWatchClient cloudWatchClient;
        private final CloudWatchConfig config;
        private final ScheduledExecutorService executorService;
        private final Map<String, List<MetricDatum>> pendingMetrics;
        private final Map<String, AtomicLong> counters;

        public CloudWatchMetricsCollector(CloudWatchClient cloudWatchClient, CloudWatchConfig config) {
            this.cloudWatchClient = cloudWatchClient;
            this.config = config;
            this.executorService = Executors.newScheduledThreadPool(2);
            this.pendingMetrics = new ConcurrentHashMap<>();
            this.counters = new ConcurrentHashMap<>();
            
            startPeriodicFlush();
        }

        @PostConstruct
        public void initialize() {
            log.info("Starting CloudWatch metrics collector with namespace: {}", config.getNamespace());
        }

        private void startPeriodicFlush() {
            executorService.scheduleWithFixedDelay(
                this::flushMetrics,
                config.getFlushInterval().getSeconds(),
                config.getFlushInterval().getSeconds(),
                TimeUnit.SECONDS
            );
        }

        public void recordCount(String metricName) {
            recordCount(metricName, 1.0);
        }

        public void recordCount(String metricName, double value) {
            recordCount(metricName, value, new HashMap<>());
        }

        public void recordCount(String metricName, double value, Map<String, String> dimensions) {
            MetricDatum metricDatum = MetricDatum.builder()
                    .metricName(metricName)
                    .value(value)
                    .unit(StandardUnit.COUNT)
                    .timestamp(Instant.now())
                    .dimensions(convertDimensions(dimensions))
                    .build();

            addPendingMetric(metricDatum);
        }

        public void recordTimer(String metricName, long durationMs) {
            recordTimer(metricName, durationMs, new HashMap<>());
        }

        public void recordTimer(String metricName, long durationMs, Map<String, String> dimensions) {
            MetricDatum metricDatum = MetricDatum.builder()
                    .metricName(metricName)
                    .value((double) durationMs)
                    .unit(StandardUnit.MILLISECONDS)
                    .timestamp(Instant.now())
                    .dimensions(convertDimensions(dimensions))
                    .build();

            addPendingMetric(metricDatum);
        }

        public void recordGauge(String metricName, double value) {
            recordGauge(metricName, value, StandardUnit.NONE, new HashMap<>());
        }

        public void recordGauge(String metricName, double value, StandardUnit unit, Map<String, String> dimensions) {
            MetricDatum metricDatum = MetricDatum.builder()
                    .metricName(metricName)
                    .value(value)
                    .unit(unit)
                    .timestamp(Instant.now())
                    .dimensions(convertDimensions(dimensions))
                    .build();

            addPendingMetric(metricDatum);
        }

        public void recordPercentage(String metricName, double percentage, Map<String, String> dimensions) {
            MetricDatum metricDatum = MetricDatum.builder()
                    .metricName(metricName)
                    .value(percentage)
                    .unit(StandardUnit.PERCENT)
                    .timestamp(Instant.now())
                    .dimensions(convertDimensions(dimensions))
                    .build();

            addPendingMetric(metricDatum);
        }

        public void incrementCounter(String counterName) {
            counters.computeIfAbsent(counterName, k -> new AtomicLong(0)).incrementAndGet();
        }

        public void addToCounter(String counterName, long value) {
            counters.computeIfAbsent(counterName, k -> new AtomicLong(0)).addAndGet(value);
        }

        public long getCounter(String counterName) {
            AtomicLong counter = counters.get(counterName);
            return counter != null ? counter.get() : 0;
        }

        public void resetCounter(String counterName) {
            AtomicLong counter = counters.get(counterName);
            if (counter != null) {
                counter.set(0);
            }
        }

        private void addPendingMetric(MetricDatum metricDatum) {
            String key = config.getNamespace();
            pendingMetrics.computeIfAbsent(key, k -> Collections.synchronizedList(new ArrayList<>()))
                    .add(metricDatum);

            // Flush immediately if batch size is reached
            if (pendingMetrics.get(key).size() >= config.getBatchSize()) {
                flushMetrics();
            }
        }

        private List<Dimension> convertDimensions(Map<String, String> dimensions) {
            return dimensions.entrySet().stream()
                    .map(entry -> Dimension.builder()
                            .name(entry.getKey())
                            .value(entry.getValue())
                            .build())
                    .toList();
        }

        public void flushMetrics() {
            for (Map.Entry<String, List<MetricDatum>> entry : pendingMetrics.entrySet()) {
                List<MetricDatum> metrics = entry.getValue();
                if (!metrics.isEmpty()) {
                    synchronized (metrics) {
                        List<MetricDatum> toFlush = new ArrayList<>(metrics);
                        metrics.clear();
                        
                        if (!toFlush.isEmpty()) {
                            sendMetricsToCloudWatch(entry.getKey(), toFlush);
                        }
                    }
                }
            }

            // Flush counters as metrics
            flushCounters();
        }

        private void flushCounters() {
            for (Map.Entry<String, AtomicLong> entry : counters.entrySet()) {
                long value = entry.getValue().getAndSet(0);
                if (value > 0) {
                    recordCount(entry.getKey(), value);
                }
            }
        }

        private void sendMetricsToCloudWatch(String namespace, List<MetricDatum> metrics) {
            try {
                // CloudWatch allows max 20 metrics per request
                int batchSize = Math.min(20, config.getBatchSize());
                
                for (int i = 0; i < metrics.size(); i += batchSize) {
                    List<MetricDatum> batch = metrics.subList(i, Math.min(i + batchSize, metrics.size()));
                    
                    PutMetricDataRequest request = PutMetricDataRequest.builder()
                            .namespace(namespace)
                            .metricData(batch)
                            .build();

                    cloudWatchClient.putMetricData(request);
                    log.debug("Sent {} metrics to CloudWatch namespace: {}", batch.size(), namespace);
                }
                
            } catch (Exception e) {
                log.error("Error sending metrics to CloudWatch: {}", e.getMessage());
            }
        }

        @PreDestroy
        public void shutdown() {
            log.info("Shutting down CloudWatch metrics collector");
            flushMetrics(); // Flush remaining metrics
            
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
     * CloudWatch Dashboard Manager for creating and managing dashboards
     */
    public static class CloudWatchDashboardManager {

        private final CloudWatchClient cloudWatchClient;
        private final CloudWatchConfig config;

        public CloudWatchDashboardManager(CloudWatchClient cloudWatchClient, CloudWatchConfig config) {
            this.cloudWatchClient = cloudWatchClient;
            this.config = config;
        }

        @PostConstruct
        public void initializeDashboard() {
            if (config.isEnableCustomDashboard()) {
                createOddiyaDashboard();
            }
        }

        public void createOddiyaDashboard() {
            try {
                String dashboardName = "Oddiya-Application-Dashboard";
                String dashboardBody = createDashboardJson();

                PutDashboardRequest request = PutDashboardRequest.builder()
                        .dashboardName(dashboardName)
                        .dashboardBody(dashboardBody)
                        .build();

                cloudWatchClient.putDashboard(request);
                log.info("Created CloudWatch dashboard: {}", dashboardName);

            } catch (Exception e) {
                log.error("Error creating CloudWatch dashboard: {}", e.getMessage());
            }
        }

        private String createDashboardJson() {
            return """
                {
                    "widgets": [
                        {
                            "type": "metric",
                            "x": 0,
                            "y": 0,
                            "width": 12,
                            "height": 6,
                            "properties": {
                                "metrics": [
                                    [ "%s", "ApiRequestCount" ],
                                    [ ".", "ApiResponseTime" ]
                                ],
                                "period": 300,
                                "stat": "Average",
                                "region": "ap-northeast-2",
                                "title": "API Metrics"
                            }
                        },
                        {
                            "type": "metric",
                            "x": 12,
                            "y": 0,
                            "width": 12,
                            "height": 6,
                            "properties": {
                                "metrics": [
                                    [ "%s", "UserRegistrations" ],
                                    [ ".", "TravelPlanCreations" ],
                                    [ ".", "PlaceSearches" ]
                                ],
                                "period": 300,
                                "stat": "Sum",
                                "region": "ap-northeast-2",
                                "title": "Business Metrics"
                            }
                        },
                        {
                            "type": "metric",
                            "x": 0,
                            "y": 6,
                            "width": 12,
                            "height": 6,
                            "properties": {
                                "metrics": [
                                    [ "%s", "CPUUtilization" ],
                                    [ ".", "MemoryUtilization" ]
                                ],
                                "period": 300,
                                "stat": "Average",
                                "region": "ap-northeast-2",
                                "title": "System Performance"
                            }
                        },
                        {
                            "type": "metric",
                            "x": 12,
                            "y": 6,
                            "width": 12,
                            "height": 6,
                            "properties": {
                                "metrics": [
                                    [ "%s", "DatabaseConnections" ],
                                    [ ".", "CacheHitRatio" ]
                                ],
                                "period": 300,
                                "stat": "Average",
                                "region": "ap-northeast-2",
                                "title": "Infrastructure Metrics"
                            }
                        }
                    ]
                }
                """.formatted(config.getNamespace(), config.getNamespace(), 
                             config.getNamespace(), config.getNamespace());
        }
    }

    /**
     * CloudWatch Alarm Manager for creating and managing alarms
     */
    public static class CloudWatchAlarmManager {

        private final CloudWatchClient cloudWatchClient;
        private final CloudWatchConfig config;

        public CloudWatchAlarmManager(CloudWatchClient cloudWatchClient, CloudWatchConfig config) {
            this.cloudWatchClient = cloudWatchClient;
            this.config = config;
        }

        @PostConstruct
        public void initializeAlarms() {
            if (config.isEnableAlarms()) {
                createApplicationAlarms();
            }
        }

        public void createApplicationAlarms() {
            if (config.getAlarmConfig().isEnableHighErrorRate()) {
                createHighErrorRateAlarm();
            }
            if (config.getAlarmConfig().isEnableHighLatency()) {
                createHighLatencyAlarm();
            }
            if (config.getAlarmConfig().isEnableHighCpuUsage()) {
                createHighCpuAlarm();
            }
            if (config.getAlarmConfig().isEnableHighMemoryUsage()) {
                createHighMemoryAlarm();
            }
        }

        private void createHighErrorRateAlarm() {
            try {
                PutMetricAlarmRequest request = PutMetricAlarmRequest.builder()
                        .alarmName("Oddiya-High-Error-Rate")
                        .alarmDescription("Alarm when error rate exceeds threshold")
                        .comparisonOperator(ComparisonOperator.GREATER_THAN_THRESHOLD)
                        .evaluationPeriods(config.getAlarmConfig().getEvaluationPeriods())
                        .datapointsToAlarm(config.getAlarmConfig().getDatapointsToAlarm())
                        .metricName("ErrorRate")
                        .namespace(config.getNamespace())
                        .period(300)
                        .statistic(Statistic.AVERAGE)
                        .threshold(config.getAlarmConfig().getErrorRateThreshold())
                        .actionsEnabled(true)
                        .unit(StandardUnit.PERCENT)
                        .build();

                cloudWatchClient.putMetricAlarm(request);
                log.info("Created high error rate alarm");

            } catch (Exception e) {
                log.error("Error creating high error rate alarm: {}", e.getMessage());
            }
        }

        private void createHighLatencyAlarm() {
            try {
                PutMetricAlarmRequest request = PutMetricAlarmRequest.builder()
                        .alarmName("Oddiya-High-Latency")
                        .alarmDescription("Alarm when API response time exceeds threshold")
                        .comparisonOperator(ComparisonOperator.GREATER_THAN_THRESHOLD)
                        .evaluationPeriods(config.getAlarmConfig().getEvaluationPeriods())
                        .datapointsToAlarm(config.getAlarmConfig().getDatapointsToAlarm())
                        .metricName("ApiResponseTime")
                        .namespace(config.getNamespace())
                        .period(300)
                        .statistic(Statistic.AVERAGE)
                        .threshold(config.getAlarmConfig().getLatencyThreshold())
                        .actionsEnabled(true)
                        .unit(StandardUnit.MILLISECONDS)
                        .build();

                cloudWatchClient.putMetricAlarm(request);
                log.info("Created high latency alarm");

            } catch (Exception e) {
                log.error("Error creating high latency alarm: {}", e.getMessage());
            }
        }

        private void createHighCpuAlarm() {
            try {
                PutMetricAlarmRequest request = PutMetricAlarmRequest.builder()
                        .alarmName("Oddiya-High-CPU-Usage")
                        .alarmDescription("Alarm when CPU usage exceeds threshold")
                        .comparisonOperator(ComparisonOperator.GREATER_THAN_THRESHOLD)
                        .evaluationPeriods(config.getAlarmConfig().getEvaluationPeriods())
                        .datapointsToAlarm(config.getAlarmConfig().getDatapointsToAlarm())
                        .metricName("CPUUtilization")
                        .namespace(config.getNamespace())
                        .period(300)
                        .statistic(Statistic.AVERAGE)
                        .threshold(config.getAlarmConfig().getCpuThreshold())
                        .actionsEnabled(true)
                        .unit(StandardUnit.PERCENT)
                        .build();

                cloudWatchClient.putMetricAlarm(request);
                log.info("Created high CPU usage alarm");

            } catch (Exception e) {
                log.error("Error creating high CPU usage alarm: {}", e.getMessage());
            }
        }

        private void createHighMemoryAlarm() {
            try {
                PutMetricAlarmRequest request = PutMetricAlarmRequest.builder()
                        .alarmName("Oddiya-High-Memory-Usage")
                        .alarmDescription("Alarm when memory usage exceeds threshold")
                        .comparisonOperator(ComparisonOperator.GREATER_THAN_THRESHOLD)
                        .evaluationPeriods(config.getAlarmConfig().getEvaluationPeriods())
                        .datapointsToAlarm(config.getAlarmConfig().getDatapointsToAlarm())
                        .metricName("MemoryUtilization")
                        .namespace(config.getNamespace())
                        .period(300)
                        .statistic(Statistic.AVERAGE)
                        .threshold(config.getAlarmConfig().getMemoryThreshold())
                        .actionsEnabled(true)
                        .unit(StandardUnit.PERCENT)
                        .build();

                cloudWatchClient.putMetricAlarm(request);
                log.info("Created high memory usage alarm");

            } catch (Exception e) {
                log.error("Error creating high memory usage alarm: {}", e.getMessage());
            }
        }
    }
}

/**
 * Mock CloudWatch configuration for local development and testing
 */
@Configuration
@Profile({"local", "test", "h2"})
@Slf4j
class MockCloudWatchConfig {

    @Bean
    @ConditionalOnProperty(name = "app.aws.mock.enabled", havingValue = "true", matchIfMissing = true)
    public MockCloudWatchMetricsCollector mockCloudWatchMetricsCollector() {
        log.info("Creating mock CloudWatch metrics collector for local development");
        return new MockCloudWatchMetricsCollector();
    }

    public static class MockCloudWatchMetricsCollector {

        public void recordCount(String metricName) {
            log.debug("Mock: Recording count metric: {}", metricName);
        }

        public void recordCount(String metricName, double value) {
            log.debug("Mock: Recording count metric: {} = {}", metricName, value);
        }

        public void recordTimer(String metricName, long durationMs) {
            log.debug("Mock: Recording timer metric: {} = {}ms", metricName, durationMs);
        }

        public void recordGauge(String metricName, double value) {
            log.debug("Mock: Recording gauge metric: {} = {}", metricName, value);
        }

        public void incrementCounter(String counterName) {
            log.debug("Mock: Incrementing counter: {}", counterName);
        }

        public void flushMetrics() {
            log.debug("Mock: Flushing metrics to CloudWatch");
        }
    }
}