package com.oddiya.service;

public interface JwtService {
    String generateAccessToken(String userId, String email);
    String generateRefreshToken(String userId);
    String validateAccessToken(String token);
    String validateRefreshToken(String token);
    String extractUserId(String token);
    
    // Additional methods for testing
    String generateToken(String username);
    boolean validateToken(String token);
    String getUsernameFromToken(String token);
    boolean isTokenExpired(String token);
}