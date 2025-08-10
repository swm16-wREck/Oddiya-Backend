package com.oddiya.service.impl;

import com.oddiya.dto.request.LoginRequest;
import com.oddiya.dto.request.RefreshTokenRequest;
import com.oddiya.dto.response.AuthResponse;
import com.oddiya.entity.User;
import com.oddiya.exception.UnauthorizedException;
import com.oddiya.repository.UserRepository;
import com.oddiya.service.AuthService;
import com.oddiya.service.JwtService;
import com.oddiya.service.OAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final OAuthService oAuthService;
    
    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        // Verify OAuth token with provider
        Map<String, Object> oAuthUser = oAuthService.verifyToken(request.getProvider(), request.getIdToken());
        
        String email = (String) oAuthUser.get("email");
        String providerId = (String) oAuthUser.get("sub");
        String name = (String) oAuthUser.get("name");
        String picture = (String) oAuthUser.get("picture");
        
        // Find or create user
        User user = userRepository.findByProviderAndProviderId(request.getProvider(), providerId)
            .orElseGet(() -> createNewUser(request.getProvider(), providerId, email, name, picture));
        
        // Generate tokens
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail());
        String refreshToken = jwtService.generateRefreshToken(user.getId());
        
        // Update refresh token
        user.setRefreshToken(refreshToken);
        userRepository.save(user);
        
        return AuthResponse.builder()
            .userId(user.getId())
            .email(user.getEmail())
            .nickname(user.getNickname())
            .profileImageUrl(user.getProfileImageUrl())
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .expiresIn(3600L)
            .isNewUser(user.getCreatedAt().equals(user.getUpdatedAt()))
            .build();
    }
    
    @Override
    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        // Validate refresh token
        String userId = jwtService.validateRefreshToken(request.getRefreshToken());
        
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UnauthorizedException("User not found"));
        
        if (!request.getRefreshToken().equals(user.getRefreshToken())) {
            throw new UnauthorizedException("Invalid refresh token");
        }
        
        // Generate new tokens
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail());
        String refreshToken = jwtService.generateRefreshToken(user.getId());
        
        // Update refresh token
        user.setRefreshToken(refreshToken);
        userRepository.save(user);
        
        return AuthResponse.builder()
            .userId(user.getId())
            .email(user.getEmail())
            .nickname(user.getNickname())
            .profileImageUrl(user.getProfileImageUrl())
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .expiresIn(3600L)
            .isNewUser(false)
            .build();
    }
    
    @Override
    @Transactional
    public void logout(String userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UnauthorizedException("User not found"));
        
        user.setRefreshToken(null);
        userRepository.save(user);
    }
    
    @Override
    public void validateToken(String token) {
        jwtService.validateAccessToken(token);
    }
    
    private User createNewUser(String provider, String providerId, String email, String name, String picture) {
        User newUser = User.builder()
            .email(email)
            .nickname(name != null ? name : email.split("@")[0])
            .profileImageUrl(picture)
            .provider(provider)
            .providerId(providerId)
            .isEmailVerified(true)
            .isActive(true)
            .build();
        
        return userRepository.save(newUser);
    }
}