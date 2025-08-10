package com.oddiya.service;

public interface JwtService {
    String generateAccessToken(String userId, String email);
    String generateRefreshToken(String userId);
    String validateAccessToken(String token);
    String validateRefreshToken(String token);
    String extractUserId(String token);
}