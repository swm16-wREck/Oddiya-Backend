package com.oddiya.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.bucket4j.Bucket;
import com.bucket4j.BucketConfiguration;
import com.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Rate Limiting Filter
 * 
 * Implements distributed rate limiting across all API endpoints
 * Protects against DDoS attacks and API abuse
 * 
 * OWASP Top 10 2021:
 * - A04: Insecure Design - Implements rate limiting by design
 * - A05: Security Misconfiguration - Prevents abuse through configuration
 * - A09: Security Logging - Logs rate limit violations
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.security.rate-limiting.enabled", havingValue = "true", matchIfMissing = true)
public class RateLimitingFilter extends OncePerRequestFilter {
    
    private final RateLimitingConfig rateLimitingConfig;
    
    @Qualifier("authRateLimitSupplier")
    private final Supplier<BucketConfiguration> authRateLimitSupplier;
    
    @Qualifier("aiGenerationRateLimitSupplier")
    private final Supplier<BucketConfiguration> aiGenerationRateLimitSupplier;
    
    @Qualifier("searchRateLimitSupplier")
    private final Supplier<BucketConfiguration> searchRateLimitSupplier;
    
    @Qualifier("mediaUploadRateLimitSupplier")
    private final Supplier<BucketConfiguration> mediaUploadRateLimitSupplier;
    
    @Qualifier("generalRateLimitSupplier")
    private final Supplier<BucketConfiguration> generalRateLimitSupplier;
    
    @Qualifier("ipRateLimitSupplier")
    private final Supplier<BucketConfiguration> ipRateLimitSupplier;
    
    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    
    // Rate limiting path patterns
    private static final List<RateLimitPattern> RATE_LIMIT_PATTERNS = List.of(
        new RateLimitPattern("/api/v1/auth/**", "auth"),
        new RateLimitPattern("/api/v1/travel-plans/generate", "ai_generation"),
        new RateLimitPattern("/api/v1/ai/**", "ai_generation"),
        new RateLimitPattern("/api/v1/places/search", "search"),
        new RateLimitPattern("/api/v1/places/nearby", "search"),
        new RateLimitPattern("/api/v1/travel-plans/search", "search"),
        new RateLimitPattern("/api/v1/files/**", "media_upload"),
        new RateLimitPattern("/api/v1/**", "general")
    );
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, 
                                  FilterChain filterChain) throws ServletException, IOException {
        
        String requestPath = request.getRequestURI();
        String method = request.getMethod();
        String clientIp = getClientIpAddress(request);
        
        // Skip rate limiting for health checks and actuator endpoints
        if (shouldSkipRateLimit(requestPath, method)) {
            filterChain.doFilter(request, response);
            return;
        }
        
        // Determine rate limit type based on path
        String rateLimitType = determineRateLimitType(requestPath);
        if (rateLimitType == null) {
            filterChain.doFilter(request, response);
            return;
        }
        
        // Get appropriate bucket configuration
        Supplier<BucketConfiguration> configSupplier = getBucketConfigSupplier(rateLimitType);
        if (configSupplier == null) {
            log.warn("No rate limit configuration found for type: {}", rateLimitType);
            filterChain.doFilter(request, response);
            return;
        }
        
        // Generate rate limit key (user-based or IP-based)
        String rateLimitKey = generateRateLimitKey(request, rateLimitType, clientIp);
        
        // Create and check bucket
        Bucket bucket = rateLimitingConfig.createBucket(rateLimitKey, configSupplier);
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        
        if (probe.isConsumed()) {
            // Add rate limit headers to response
            addRateLimitHeaders(response, probe);
            
            log.debug("Rate limit check passed for key: {}, remaining: {}", 
                     rateLimitKey, probe.getRemainingTokens());
            
            filterChain.doFilter(request, response);
        } else {
            // Rate limit exceeded
            handleRateLimitExceeded(request, response, probe, rateLimitKey, rateLimitType);
        }
    }
    
    private boolean shouldSkipRateLimit(String requestPath, String method) {
        // Skip rate limiting for health checks and monitoring
        return requestPath.equals("/actuator/health") ||
               requestPath.equals("/actuator/info") ||
               requestPath.startsWith("/actuator/health/") ||
               (method.equals("OPTIONS")); // Skip CORS preflight requests
    }
    
    private String determineRateLimitType(String requestPath) {
        for (RateLimitPattern pattern : RATE_LIMIT_PATTERNS) {
            if (pathMatcher.match(pattern.getPattern(), requestPath)) {
                return pattern.getType();
            }
        }
        return null;
    }
    
    private Supplier<BucketConfiguration> getBucketConfigSupplier(String rateLimitType) {
        return switch (rateLimitType) {
            case "auth" -> authRateLimitSupplier;
            case "ai_generation" -> aiGenerationRateLimitSupplier;
            case "search" -> searchRateLimitSupplier;
            case "media_upload" -> mediaUploadRateLimitSupplier;
            case "general" -> generalRateLimitSupplier;
            case "ip" -> ipRateLimitSupplier;
            default -> null;
        };
    }
    
    private String generateRateLimitKey(HttpServletRequest request, String rateLimitType, String clientIp) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication != null && authentication.isAuthenticated() && 
            !authentication.getPrincipal().equals("anonymousUser")) {
            // User-based rate limiting for authenticated users
            String userId = authentication.getName();
            return RateLimitingConfig.RateLimitKey.forUser(userId, rateLimitType);
        } else {
            // IP-based rate limiting for anonymous users
            return RateLimitingConfig.RateLimitKey.forIp(clientIp, rateLimitType);
        }
    }
    
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // Take the first IP in case of multiple proxies
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
    
    private void addRateLimitHeaders(HttpServletResponse response, ConsumptionProbe probe) {
        response.addHeader("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));
        response.addHeader("X-RateLimit-Retry-After-Seconds", 
                          String.valueOf(probe.getNanosToWaitForRefill() / 1_000_000_000L));
    }
    
    private void handleRateLimitExceeded(HttpServletRequest request, HttpServletResponse response, 
                                       ConsumptionProbe probe, String rateLimitKey, String rateLimitType) 
                                       throws IOException {
        
        String clientIp = getClientIpAddress(request);
        String userAgent = request.getHeader("User-Agent");
        long waitTimeSeconds = probe.getNanosToWaitForRefill() / 1_000_000_000L;
        
        // Security event logging - A09: Security Logging and Monitoring
        log.warn("Rate limit exceeded - Key: {}, Type: {}, IP: {}, User-Agent: {}, Wait: {}s", 
                rateLimitKey, rateLimitType, clientIp, userAgent, waitTimeSeconds);
        
        // Set HTTP 429 Too Many Requests status
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        
        // Add rate limit headers
        response.addHeader("X-RateLimit-Remaining", "0");
        response.addHeader("X-RateLimit-Retry-After-Seconds", String.valueOf(waitTimeSeconds));
        response.addHeader("Retry-After", String.valueOf(waitTimeSeconds));
        
        // Create error response
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", "Rate limit exceeded");
        errorResponse.put("message", "Too many requests. Please try again later.");
        errorResponse.put("rateLimitType", rateLimitType);
        errorResponse.put("retryAfterSeconds", waitTimeSeconds);
        errorResponse.put("timestamp", Instant.now().toString());
        
        // Write JSON response
        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
        response.getWriter().flush();
        
        // Additional security logging for potential attacks
        if (rateLimitType.equals("auth")) {
            log.error("Potential brute force attack detected - IP: {}, User-Agent: {}", clientIp, userAgent);
        }
        
        if (waitTimeSeconds > 300) { // > 5 minutes
            log.error("Severe rate limit violation - IP: {}, Type: {}, Wait: {}s", clientIp, rateLimitType, waitTimeSeconds);
        }
    }
    
    /**
     * Rate limit pattern configuration
     */
    private static class RateLimitPattern {
        private final String pattern;
        private final String type;
        
        public RateLimitPattern(String pattern, String type) {
            this.pattern = pattern;
            this.type = type;
        }
        
        public String getPattern() { return pattern; }
        public String getType() { return type; }
    }
}