package com.oddiya.service;

import jakarta.servlet.http.HttpServletRequest;

/**
 * JWT Service Interface
 * Handles JWT token operations and user authentication
 */
public interface JwtService {
    
    /**
     * Generate access token
     */
    String generateAccessToken(String userId, String email);
    
    /**
     * Generate refresh token
     */
    String generateRefreshToken(String userId);
    
    /**
     * Validate access token and return user ID
     */
    String validateAccessToken(String token);
    
    /**
     * Validate refresh token and return user ID
     */
    String validateRefreshToken(String token);
    
    /**
     * Extract user ID from token
     */
    String extractUserId(String token);
    
    /**
     * Extract user ID from HTTP request Authorization header
     */
    String extractUserIdFromRequest(HttpServletRequest request);
    
    /**
     * Extract email from token
     */
    String extractEmail(String token);
    
    // Additional methods for testing and compatibility
    String generateToken(String username);
    boolean validateToken(String token);
    String getUsernameFromToken(String token);
    boolean isTokenExpired(String token);
}