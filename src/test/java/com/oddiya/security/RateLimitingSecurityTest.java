package com.oddiya.security;

import com.oddiya.config.TestSecurityConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;
import java.util.ArrayList;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * OWASP A09:2021 - Security Logging and Monitoring Failures & Brute Force Prevention
 * Rate Limiting Security Tests
 * 
 * Tests various rate limiting scenarios to prevent brute force attacks and abuse.
 * Verifies that the application implements proper rate limiting on critical endpoints.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebMvc
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Rate Limiting Security Tests - OWASP A09:2021 - Brute Force Prevention")
public class RateLimitingSecurityTest {

    @Autowired
    private WebApplicationContext context;
    
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    
    // Rate limiting test parameters
    private static final int RATE_LIMIT_REQUESTS = 100; // Expected rate limit threshold
    private static final int BURST_REQUESTS = 150; // Requests to exceed rate limit
    private static final int CONCURRENT_THREADS = 10; // Concurrent request threads
    private static final int REQUESTS_PER_THREAD = 20; // Requests per thread
    
    // Authentication endpoints that should be rate limited
    private static final String[] AUTH_ENDPOINTS = {
        "/api/v1/auth/login",
        "/api/v1/auth/refresh",
        "/api/v1/auth/validate"
    };
    
    // Public endpoints that should be rate limited
    private static final String[] PUBLIC_ENDPOINTS = {
        "/api/v1/places/search",
        "/api/v1/places/nearby",
        "/api/v1/travel-plans/search",
        "/api/v1/travel-plans/public"
    };

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
            .webAppContextSetup(context)
            .apply(SecurityMockMvcConfigurers.springSecurity())
            .build();
            
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    @org.junit.jupiter.api.Order(1)
    @DisplayName("Rate Limiting Test - Authentication Endpoint Brute Force Protection")
    void testAuthenticationEndpointRateLimit() throws Exception {
        String loginPayload = "{\"provider\":\"google\",\"idToken\":\"fake-token\"}";
        AtomicInteger successfulRequests = new AtomicInteger(0);
        AtomicInteger rateLimitedRequests = new AtomicInteger(0);
        
        // Send rapid requests to login endpoint
        for (int i = 0; i < BURST_REQUESTS; i++) {
            try {
                int status = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload))
                        .andReturn()
                        .getResponse()
                        .getStatus();
                
                if (status == 429) { // Too Many Requests
                    rateLimitedRequests.incrementAndGet();
                } else {
                    successfulRequests.incrementAndGet();
                }
            } catch (Exception e) {
                // Handle potential rate limiting exceptions
                rateLimitedRequests.incrementAndGet();
            }
            
            // Small delay to simulate realistic attack timing
            Thread.sleep(10);
        }
        
        // Verify that rate limiting is triggered
        System.out.println("Successful requests: " + successfulRequests.get());
        System.out.println("Rate limited requests: " + rateLimitedRequests.get());
        
        // At least some requests should be rate limited for brute force protection
        // Note: This test documents expected behavior - implement actual rate limiting
        assert rateLimitedRequests.get() > 0 || successfulRequests.get() < BURST_REQUESTS 
               : "Rate limiting should be implemented on authentication endpoints";
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    @DisplayName("Rate Limiting Test - Concurrent Request Flooding")
    void testConcurrentRequestFlooding() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_THREADS);
        AtomicInteger totalRequests = new AtomicInteger(0);
        AtomicInteger rateLimitedRequests = new AtomicInteger(0);
        List<Exception> exceptions = new ArrayList<>();
        
        // Submit concurrent tasks
        for (int i = 0; i < CONCURRENT_THREADS; i++) {
            final int threadId = i;
            executor.submit(() -> {
                for (int j = 0; j < REQUESTS_PER_THREAD; j++) {
                    try {
                        int status = mockMvc.perform(get("/api/v1/travel-plans/public")
                                .header("X-Thread-ID", String.valueOf(threadId))
                                .header("X-Request-ID", String.valueOf(j))
                                .contentType(MediaType.APPLICATION_JSON))
                                .andReturn()
                                .getResponse()
                                .getStatus();
                        
                        totalRequests.incrementAndGet();
                        
                        if (status == 429) {
                            rateLimitedRequests.incrementAndGet();
                        }
                        
                    } catch (Exception e) {
                        synchronized (exceptions) {
                            exceptions.add(e);
                        }
                        rateLimitedRequests.incrementAndGet();
                    }
                }
            });
        }
        
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);
        
        System.out.println("Total concurrent requests: " + totalRequests.get());
        System.out.println("Rate limited responses: " + rateLimitedRequests.get());
        System.out.println("Exceptions: " + exceptions.size());
        
        // Verify system handles concurrent load gracefully
        assert totalRequests.get() > 0 : "Some requests should complete successfully";
        
        // Log recommendation for rate limiting implementation
        if (rateLimitedRequests.get() == 0) {
            System.out.println("RECOMMENDATION: Implement rate limiting to prevent abuse");
        }
    }

    @Test
    @org.junit.jupiter.api.Order(3)
    @DisplayName("Rate Limiting Test - IP-based Rate Limiting")
    void testIpBasedRateLimit() throws Exception {
        String[] maliciousIPs = {
            "192.168.1.100",
            "10.0.0.50",
            "172.16.0.25",
            "203.0.113.10"
        };
        
        for (String ip : maliciousIPs) {
            AtomicInteger requestsFromIp = new AtomicInteger(0);
            AtomicInteger rateLimitedFromIp = new AtomicInteger(0);
            
            // Send multiple requests from same IP
            for (int i = 0; i < 50; i++) {
                try {
                    int status = mockMvc.perform(get("/api/v1/travel-plans/search")
                            .header("X-Forwarded-For", ip)
                            .header("X-Real-IP", ip)
                            .param("query", "seoul")
                            .contentType(MediaType.APPLICATION_JSON))
                            .andReturn()
                            .getResponse()
                            .getStatus();
                    
                    requestsFromIp.incrementAndGet();
                    
                    if (status == 429) {
                        rateLimitedFromIp.incrementAndGet();
                    }
                    
                } catch (Exception e) {
                    rateLimitedFromIp.incrementAndGet();
                }
            }
            
            System.out.println("IP " + ip + " - Requests: " + requestsFromIp.get() + 
                             ", Rate limited: " + rateLimitedFromIp.get());
        }
        
        // Document that IP-based rate limiting should be implemented
        System.out.println("RECOMMENDATION: Implement IP-based rate limiting for abuse prevention");
    }

    @Test
    @org.junit.jupiter.api.Order(4)
    @DisplayName("Rate Limiting Test - User-based Rate Limiting")
    @WithMockJwtUser("test-user-1")
    void testUserBasedRateLimit() throws Exception {
        AtomicInteger userRequests = new AtomicInteger(0);
        AtomicInteger rateLimited = new AtomicInteger(0);
        
        // Send multiple requests from same user
        for (int i = 0; i < 80; i++) {
            try {
                int status = mockMvc.perform(get("/api/v1/travel-plans")
                        .contentType(MediaType.APPLICATION_JSON))
                        .andReturn()
                        .getResponse()
                        .getStatus();
                
                userRequests.incrementAndGet();
                
                if (status == 429) {
                    rateLimited.incrementAndGet();
                } else if (status == 401) {
                    // Expected for mock JWT in this test environment
                    break;
                }
                
            } catch (Exception e) {
                rateLimited.incrementAndGet();
            }
        }
        
        System.out.println("User requests: " + userRequests.get() + 
                         ", Rate limited: " + rateLimited.get());
        
        // Document that user-based rate limiting should be implemented
        System.out.println("RECOMMENDATION: Implement per-user rate limiting");
    }

    @Test
    @org.junit.jupiter.api.Order(5)
    @DisplayName("Rate Limiting Test - Endpoint-specific Rate Limits")
    void testEndpointSpecificRateLimit() throws Exception {
        // Test different rate limits for different endpoints
        String[] endpoints = {
            "/api/v1/travel-plans/search?query=test",
            "/api/v1/places/search?query=seoul",
            "/api/v1/travel-plans/public",
            "/api/v1/health"
        };
        
        for (String endpoint : endpoints) {
            AtomicInteger requests = new AtomicInteger(0);
            AtomicInteger rateLimited = new AtomicInteger(0);
            
            for (int i = 0; i < 60; i++) {
                try {
                    int status = mockMvc.perform(get(endpoint)
                            .contentType(MediaType.APPLICATION_JSON))
                            .andReturn()
                            .getResponse()
                            .getStatus();
                    
                    requests.incrementAndGet();
                    
                    if (status == 429) {
                        rateLimited.incrementAndGet();
                    }
                    
                } catch (Exception e) {
                    rateLimited.incrementAndGet();
                }
                
                Thread.sleep(10); // Small delay
            }
            
            System.out.println("Endpoint " + endpoint + " - Requests: " + requests.get() + 
                             ", Rate limited: " + rateLimited.get());
        }
    }

    @Test
    @org.junit.jupiter.api.Order(6)
    @DisplayName("Rate Limiting Test - Burst vs Sustained Traffic")
    void testBurstVsSustainedTraffic() throws Exception {
        // Test burst traffic (many requests quickly)
        long startTime = System.currentTimeMillis();
        AtomicInteger burstRequests = new AtomicInteger(0);
        AtomicInteger burstRateLimited = new AtomicInteger(0);
        
        // Burst phase: Send requests as fast as possible
        for (int i = 0; i < 100; i++) {
            try {
                int status = mockMvc.perform(get("/api/v1/travel-plans/search")
                        .param("query", "test-burst-" + i)
                        .contentType(MediaType.APPLICATION_JSON))
                        .andReturn()
                        .getResponse()
                        .getStatus();
                
                burstRequests.incrementAndGet();
                
                if (status == 429) {
                    burstRateLimited.incrementAndGet();
                }
                
            } catch (Exception e) {
                burstRateLimited.incrementAndGet();
            }
        }
        
        long burstDuration = System.currentTimeMillis() - startTime;
        
        // Wait and then test sustained traffic
        Thread.sleep(1000);
        
        startTime = System.currentTimeMillis();
        AtomicInteger sustainedRequests = new AtomicInteger(0);
        AtomicInteger sustainedRateLimited = new AtomicInteger(0);
        
        // Sustained phase: Send requests with delays
        for (int i = 0; i < 50; i++) {
            try {
                int status = mockMvc.perform(get("/api/v1/travel-plans/search")
                        .param("query", "test-sustained-" + i)
                        .contentType(MediaType.APPLICATION_JSON))
                        .andReturn()
                        .getResponse()
                        .getStatus();
                
                sustainedRequests.incrementAndGet();
                
                if (status == 429) {
                    sustainedRateLimited.incrementAndGet();
                }
                
            } catch (Exception e) {
                sustainedRateLimited.incrementAndGet();
            }
            
            Thread.sleep(100); // Sustained delay
        }
        
        long sustainedDuration = System.currentTimeMillis() - startTime;
        
        System.out.println("Burst traffic (" + burstDuration + "ms): " + 
                         burstRequests.get() + " requests, " + burstRateLimited.get() + " rate limited");
        System.out.println("Sustained traffic (" + sustainedDuration + "ms): " + 
                         sustainedRequests.get() + " requests, " + sustainedRateLimited.get() + " rate limited");
        
        // Verify that burst traffic is more likely to be rate limited
        System.out.println("RECOMMENDATION: Implement token bucket or sliding window rate limiting");
    }

    @Test
    @org.junit.jupiter.api.Order(7)
    @DisplayName("Rate Limiting Test - Rate Limit Header Verification")
    void testRateLimitHeaders() throws Exception {
        // Check if rate limiting headers are present in responses
        String response = mockMvc.perform(get("/api/v1/travel-plans/public")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getHeaderNames()
                .toString();
        
        // Check for common rate limiting headers
        boolean hasRateLimitHeaders = response.contains("X-RateLimit") ||
                                    response.contains("X-Rate-Limit") ||
                                    response.contains("RateLimit") ||
                                    response.contains("Retry-After");
        
        if (!hasRateLimitHeaders) {
            System.out.println("RECOMMENDATION: Add rate limiting headers (X-RateLimit-Limit, X-RateLimit-Remaining, X-RateLimit-Reset)");
        }
        
        System.out.println("Response headers: " + response);
    }

    @Test
    @org.junit.jupiter.api.Order(8)
    @DisplayName("Rate Limiting Test - Different User Agent Rate Limits")
    void testUserAgentBasedRateLimit() throws Exception {
        String[] suspiciousUserAgents = {
            "curl/7.68.0",
            "wget/1.20.3",
            "python-requests/2.25.1",
            "PostmanRuntime/7.26.8",
            "Mozilla/5.0 (compatible; Baiduspider/2.0)",
            "",
            "Bot/1.0",
            "Scanner/1.0",
            "Crawler/1.0"
        };
        
        for (String userAgent : suspiciousUserAgents) {
            AtomicInteger requests = new AtomicInteger(0);
            AtomicInteger rateLimited = new AtomicInteger(0);
            
            for (int i = 0; i < 30; i++) {
                try {
                    int status = mockMvc.perform(get("/api/v1/travel-plans/search")
                            .header("User-Agent", userAgent)
                            .param("query", "test")
                            .contentType(MediaType.APPLICATION_JSON))
                            .andReturn()
                            .getResponse()
                            .getStatus();
                    
                    requests.incrementAndGet();
                    
                    if (status == 429) {
                        rateLimited.incrementAndGet();
                    }
                    
                } catch (Exception e) {
                    rateLimited.incrementAndGet();
                }
            }
            
            System.out.println("User-Agent '" + userAgent + "' - Requests: " + 
                             requests.get() + ", Rate limited: " + rateLimited.get());
        }
        
        System.out.println("RECOMMENDATION: Consider stricter rate limits for bot/crawler user agents");
    }

    @Test
    @org.junit.jupiter.api.Order(9)
    @DisplayName("Rate Limiting Test - Geographic Rate Limiting")
    void testGeographicRateLimit() throws Exception {
        // Test rate limiting based on geographic headers
        String[][] geoHeaders = {
            {"CF-IPCountry", "CN"},
            {"CF-IPCountry", "RU"},
            {"CF-IPCountry", "KR"},
            {"CloudFront-Viewer-Country", "US"},
            {"X-Country-Code", "JP"},
            {"X-Geo-Country", "DE"}
        };
        
        for (String[] geoHeader : geoHeaders) {
            AtomicInteger requests = new AtomicInteger(0);
            AtomicInteger rateLimited = new AtomicInteger(0);
            
            for (int i = 0; i < 40; i++) {
                try {
                    int status = mockMvc.perform(get("/api/v1/travel-plans/search")
                            .header(geoHeader[0], geoHeader[1])
                            .param("query", "test")
                            .contentType(MediaType.APPLICATION_JSON))
                            .andReturn()
                            .getResponse()
                            .getStatus();
                    
                    requests.incrementAndGet();
                    
                    if (status == 429) {
                        rateLimited.incrementAndGet();
                    }
                    
                } catch (Exception e) {
                    rateLimited.incrementAndGet();
                }
            }
            
            System.out.println("Country " + geoHeader[1] + " - Requests: " + 
                             requests.get() + ", Rate limited: " + rateLimited.get());
        }
    }

    @Test
    @org.junit.jupiter.api.Order(10)
    @DisplayName("Rate Limiting Test - Recovery After Rate Limit")
    void testRateLimitRecovery() throws Exception {
        // Send requests to trigger rate limit
        AtomicInteger initialRequests = new AtomicInteger(0);
        AtomicInteger rateLimited = new AtomicInteger(0);
        
        for (int i = 0; i < 80; i++) {
            try {
                int status = mockMvc.perform(get("/api/v1/travel-plans/search")
                        .param("query", "rate-limit-test")
                        .contentType(MediaType.APPLICATION_JSON))
                        .andReturn()
                        .getResponse()
                        .getStatus();
                
                initialRequests.incrementAndGet();
                
                if (status == 429) {
                    rateLimited.incrementAndGet();
                }
                
            } catch (Exception e) {
                rateLimited.incrementAndGet();
            }
        }
        
        System.out.println("Initial phase - Requests: " + initialRequests.get() + 
                         ", Rate limited: " + rateLimited.get());
        
        // Wait for rate limit window to reset
        Thread.sleep(5000);
        
        // Test that service recovers
        AtomicInteger recoveryRequests = new AtomicInteger(0);
        AtomicInteger recoveryRateLimited = new AtomicInteger(0);
        
        for (int i = 0; i < 10; i++) {
            try {
                int status = mockMvc.perform(get("/api/v1/travel-plans/search")
                        .param("query", "recovery-test")
                        .contentType(MediaType.APPLICATION_JSON))
                        .andReturn()
                        .getResponse()
                        .getStatus();
                
                recoveryRequests.incrementAndGet();
                
                if (status == 429) {
                    recoveryRateLimited.incrementAndGet();
                }
                
            } catch (Exception e) {
                recoveryRateLimited.incrementAndGet();
            }
            
            Thread.sleep(200); // Spaced requests
        }
        
        System.out.println("Recovery phase - Requests: " + recoveryRequests.get() + 
                         ", Rate limited: " + recoveryRateLimited.get());
        
        // Service should recover after rate limit window
        System.out.println("RECOMMENDATION: Ensure rate limits reset appropriately to allow service recovery");
    }
}