package com.oddiya.performance;

import com.oddiya.testdata.ComprehensiveTestDataFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Performance Test Configuration for Oddiya
 * 
 * Provides comprehensive performance testing infrastructure as per PRD requirements:
 * - Support for 10,000+ concurrent users
 * - <200ms API response times
 * - <5s AI generation response times
 * - Database query optimization (<50ms)
 * - Memory and resource management
 * - Thread pool configuration for load testing
 */
@TestConfiguration
@ActiveProfiles("performance-test")
@Slf4j
public class PerformanceTestConfiguration {

    // Performance testing constants as per PRD
    public static final long MAX_API_RESPONSE_TIME_MS = 200;
    public static final long MAX_DATABASE_QUERY_TIME_MS = 50;
    public static final long MAX_AI_GENERATION_TIME_MS = 5000;
    public static final int MAX_CONCURRENT_USERS = 10000;
    public static final int TYPICAL_LOAD_CONCURRENT_USERS = 1000;
    public static final int STRESS_TEST_CONCURRENT_USERS = 15000;

    // Thread pool configuration for performance testing
    public static final int CORE_THREAD_POOL_SIZE = 50;
    public static final int MAX_THREAD_POOL_SIZE = 200;
    public static final int THREAD_KEEP_ALIVE_TIME_SECONDS = 60;

    /**
     * Thread pool executor for concurrent performance testing
     */
    @Bean
    @Primary
    public ThreadPoolExecutor performanceTestExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
            CORE_THREAD_POOL_SIZE,
            MAX_THREAD_POOL_SIZE,
            THREAD_KEEP_ALIVE_TIME_SECONDS,
            java.util.concurrent.TimeUnit.SECONDS,
            new java.util.concurrent.LinkedBlockingQueue<>()
        );
        
        executor.setThreadFactory(r -> {
            Thread thread = new Thread(r, "PerformanceTest-" + System.currentTimeMillis());
            thread.setDaemon(true);
            return thread;
        });

        log.info("Performance test thread pool configured: core={}, max={}, keepAlive={}s", 
            CORE_THREAD_POOL_SIZE, MAX_THREAD_POOL_SIZE, THREAD_KEEP_ALIVE_TIME_SECONDS);

        return executor;
    }

    /**
     * Scheduled executor for timed performance tests
     */
    @Bean
    public ScheduledExecutorService performanceScheduledExecutor() {
        return Executors.newScheduledThreadPool(10, r -> {
            Thread thread = new Thread(r, "PerformanceScheduled-" + System.currentTimeMillis());
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * Performance test data factory with optimized settings
     */
    @Bean
    @Primary
    public ComprehensiveTestDataFactory performanceTestDataFactory() {
        return new ComprehensiveTestDataFactory();
    }

    /**
     * Performance monitoring configuration
     */
    @Bean
    public PerformanceMonitor performanceMonitor() {
        return new PerformanceMonitor();
    }

    /**
     * Load testing configuration for different test scenarios
     */
    @Bean
    public LoadTestConfiguration loadTestConfiguration() {
        return LoadTestConfiguration.builder()
            .lightLoad(LoadTestScenario.builder()
                .name("Light Load")
                .concurrentUsers(100)
                .rampUpTimeSeconds(30)
                .testDurationMinutes(5)
                .expectedResponseTimeMs(100)
                .build())
            .normalLoad(LoadTestScenario.builder()
                .name("Normal Load") 
                .concurrentUsers(TYPICAL_LOAD_CONCURRENT_USERS)
                .rampUpTimeSeconds(120)
                .testDurationMinutes(10)
                .expectedResponseTimeMs(MAX_API_RESPONSE_TIME_MS)
                .build())
            .heavyLoad(LoadTestScenario.builder()
                .name("Heavy Load")
                .concurrentUsers(5000)
                .rampUpTimeSeconds(300)
                .testDurationMinutes(15)
                .expectedResponseTimeMs(500) // Relaxed for heavy load
                .build())
            .stressTest(LoadTestScenario.builder()
                .name("Stress Test")
                .concurrentUsers(STRESS_TEST_CONCURRENT_USERS)
                .rampUpTimeSeconds(600)
                .testDurationMinutes(20)
                .expectedResponseTimeMs(1000) // Stress test limits
                .build())
            .spikeTest(LoadTestScenario.builder()
                .name("Spike Test")
                .concurrentUsers(MAX_CONCURRENT_USERS)
                .rampUpTimeSeconds(60) // Fast ramp-up
                .testDurationMinutes(5)
                .expectedResponseTimeMs(2000) // Allow degradation
                .build())
            .build();
    }

    /**
     * Performance test data record classes
     */
    public record LoadTestConfiguration(
        LoadTestScenario lightLoad,
        LoadTestScenario normalLoad, 
        LoadTestScenario heavyLoad,
        LoadTestScenario stressTest,
        LoadTestScenario spikeTest
    ) {
        public static LoadTestConfigurationBuilder builder() {
            return new LoadTestConfigurationBuilder();
        }
        
        public static class LoadTestConfigurationBuilder {
            private LoadTestScenario lightLoad;
            private LoadTestScenario normalLoad;
            private LoadTestScenario heavyLoad;
            private LoadTestScenario stressTest;
            private LoadTestScenario spikeTest;

            public LoadTestConfigurationBuilder lightLoad(LoadTestScenario lightLoad) {
                this.lightLoad = lightLoad;
                return this;
            }

            public LoadTestConfigurationBuilder normalLoad(LoadTestScenario normalLoad) {
                this.normalLoad = normalLoad;
                return this;
            }

            public LoadTestConfigurationBuilder heavyLoad(LoadTestScenario heavyLoad) {
                this.heavyLoad = heavyLoad;
                return this;
            }

            public LoadTestConfigurationBuilder stressTest(LoadTestScenario stressTest) {
                this.stressTest = stressTest;
                return this;
            }

            public LoadTestConfigurationBuilder spikeTest(LoadTestScenario spikeTest) {
                this.spikeTest = spikeTest;
                return this;
            }

            public LoadTestConfiguration build() {
                return new LoadTestConfiguration(lightLoad, normalLoad, heavyLoad, stressTest, spikeTest);
            }
        }
    }

    public record LoadTestScenario(
        String name,
        int concurrentUsers,
        int rampUpTimeSeconds,
        int testDurationMinutes,
        long expectedResponseTimeMs
    ) {
        public static LoadTestScenarioBuilder builder() {
            return new LoadTestScenarioBuilder();
        }

        public static class LoadTestScenarioBuilder {
            private String name;
            private int concurrentUsers;
            private int rampUpTimeSeconds;
            private int testDurationMinutes;
            private long expectedResponseTimeMs;

            public LoadTestScenarioBuilder name(String name) {
                this.name = name;
                return this;
            }

            public LoadTestScenarioBuilder concurrentUsers(int concurrentUsers) {
                this.concurrentUsers = concurrentUsers;
                return this;
            }

            public LoadTestScenarioBuilder rampUpTimeSeconds(int rampUpTimeSeconds) {
                this.rampUpTimeSeconds = rampUpTimeSeconds;
                return this;
            }

            public LoadTestScenarioBuilder testDurationMinutes(int testDurationMinutes) {
                this.testDurationMinutes = testDurationMinutes;
                return this;
            }

            public LoadTestScenarioBuilder expectedResponseTimeMs(long expectedResponseTimeMs) {
                this.expectedResponseTimeMs = expectedResponseTimeMs;
                return this;
            }

            public LoadTestScenario build() {
                return new LoadTestScenario(name, concurrentUsers, rampUpTimeSeconds, testDurationMinutes, expectedResponseTimeMs);
            }
        }
    }

    /**
     * Performance monitoring class for test execution
     */
    public static class PerformanceMonitor {
        private final java.util.concurrent.atomic.AtomicLong totalRequests = new java.util.concurrent.atomic.AtomicLong(0);
        private final java.util.concurrent.atomic.AtomicLong totalResponseTime = new java.util.concurrent.atomic.AtomicLong(0);
        private final java.util.concurrent.atomic.AtomicLong errorCount = new java.util.concurrent.atomic.AtomicLong(0);
        private final java.util.concurrent.ConcurrentHashMap<String, Long> operationTimes = new java.util.concurrent.ConcurrentHashMap<>();

        public void recordRequest(long responseTimeMs) {
            totalRequests.incrementAndGet();
            totalResponseTime.addAndGet(responseTimeMs);
        }

        public void recordError() {
            errorCount.incrementAndGet();
        }

        public void recordOperation(String operationName, long durationMs) {
            operationTimes.put(operationName, durationMs);
        }

        public PerformanceMetrics getMetrics() {
            long requests = totalRequests.get();
            long totalTime = totalResponseTime.get();
            long errors = errorCount.get();
            
            double averageResponseTime = requests > 0 ? (double) totalTime / requests : 0.0;
            double errorRate = requests > 0 ? (double) errors / requests * 100 : 0.0;
            double throughput = requests; // requests per test duration

            return new PerformanceMetrics(
                requests,
                averageResponseTime,
                errorRate,
                throughput,
                operationTimes
            );
        }

        public void reset() {
            totalRequests.set(0);
            totalResponseTime.set(0);
            errorCount.set(0);
            operationTimes.clear();
        }
    }

    /**
     * Performance metrics record
     */
    public record PerformanceMetrics(
        long totalRequests,
        double averageResponseTimeMs,
        double errorRatePercent,
        double throughputRequestsPerSecond,
        java.util.Map<String, Long> operationTimes
    ) {
        public boolean meetsPerformanceRequirements() {
            return averageResponseTimeMs <= MAX_API_RESPONSE_TIME_MS && 
                   errorRatePercent <= 1.0; // Max 1% error rate
        }

        public String getFormattedReport() {
            return String.format("""
                Performance Test Results:
                ========================
                Total Requests: %d
                Average Response Time: %.2f ms
                Error Rate: %.2f%%
                Throughput: %.2f requests/sec
                Performance Requirements Met: %s
                
                Operation Times:
                %s
                """, 
                totalRequests,
                averageResponseTimeMs, 
                errorRatePercent,
                throughputRequestsPerSecond,
                meetsPerformanceRequirements() ? "✓ YES" : "✗ NO",
                formatOperationTimes()
            );
        }

        private String formatOperationTimes() {
            if (operationTimes.isEmpty()) {
                return "  No operation times recorded";
            }
            
            StringBuilder sb = new StringBuilder();
            operationTimes.forEach((operation, time) -> 
                sb.append(String.format("  %s: %d ms%n", operation, time))
            );
            return sb.toString();
        }
    }
}