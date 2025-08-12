package com.oddiya.util;

import com.oddiya.service.JwtService;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Utility class for JWT-related test operations
 * Provides helper methods for creating JWT tokens and authenticated requests
 */
public class JwtTestUtils {
    
    /**
     * Creates a valid JWT token for testing purposes
     * 
     * @param jwtService The mocked JWT service
     * @param userId The user ID to include in the token
     * @param email The email to include in the token
     * @return A test JWT token string
     */
    public static String createTestToken(JwtService jwtService, String userId, String email) {
        return "test-jwt-token-" + userId;
    }
    
    /**
     * Adds JWT Authorization header to a MockMvc request
     * 
     * @param request The MockMvc request builder
     * @param token The JWT token to add
     * @return The request builder with Authorization header
     */
    public static MockHttpServletRequestBuilder withJwtToken(MockHttpServletRequestBuilder request, String token) {
        return request.header("Authorization", "Bearer " + token);
    }
    
    /**
     * Creates a test JWT token and adds it to the request in one step
     * 
     * @param request The MockMvc request builder
     * @param jwtService The mocked JWT service
     * @param userId The user ID for the token
     * @param email The email for the token
     * @return The request builder with Authorization header
     */
    public static MockHttpServletRequestBuilder withTestJwtToken(MockHttpServletRequestBuilder request, 
                                                                JwtService jwtService, 
                                                                String userId, 
                                                                String email) {
        String token = createTestToken(jwtService, userId, email);
        return withJwtToken(request, token);
    }
    
    /**
     * Default test user token
     */
    public static MockHttpServletRequestBuilder withDefaultTestToken(MockHttpServletRequestBuilder request, 
                                                                     JwtService jwtService) {
        return withTestJwtToken(request, jwtService, "test-user-id", "test@example.com");
    }
}