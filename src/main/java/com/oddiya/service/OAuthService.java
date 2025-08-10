package com.oddiya.service;

import com.oddiya.dto.response.AuthResponse;
import java.util.Map;

public interface OAuthService {
    Map<String, Object> verifyToken(String provider, String idToken);
    AuthResponse authenticateWithGoogle(String authCode);
    AuthResponse authenticateWithApple(String authCode);
    AuthResponse authenticateWithGoogleIdToken(String idToken);
    AuthResponse authenticateWithAppleIdToken(String idToken);
}