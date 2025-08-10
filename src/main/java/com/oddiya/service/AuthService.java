package com.oddiya.service;

import com.oddiya.dto.request.LoginRequest;
import com.oddiya.dto.request.RefreshTokenRequest;
import com.oddiya.dto.response.AuthResponse;

public interface AuthService {
    AuthResponse login(LoginRequest request);
    AuthResponse refreshToken(RefreshTokenRequest request);
    void logout(String userId);
    void validateToken(String token);
}