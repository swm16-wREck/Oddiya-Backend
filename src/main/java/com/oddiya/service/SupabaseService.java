package com.oddiya.service;

import com.oddiya.dto.response.AuthResponse;

public interface SupabaseService {
    AuthResponse signInWithEmail(String email, String password);
    AuthResponse signInWithOAuth(String provider, String idToken);
    AuthResponse signUp(String email, String password);
    void signOut(String accessToken);
    AuthResponse refreshToken(String refreshToken);
    boolean verifyToken(String accessToken);
    String getUserId(String accessToken);
}