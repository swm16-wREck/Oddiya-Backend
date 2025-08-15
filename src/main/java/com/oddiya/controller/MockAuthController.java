package com.oddiya.controller;

import com.oddiya.dto.response.ApiResponse;
import com.oddiya.dto.response.AuthResponse;
import com.oddiya.entity.User;
import com.oddiya.repository.UserRepository;
import com.oddiya.service.JwtService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Profile({"test", "local"})
@Tag(name = "Mock Authentication", description = "Mock authentication for testing")
public class MockAuthController {
    
    private final UserRepository userRepository;
    private final JwtService jwtService;
    
    @PostMapping("/mock-login")
    @Operation(summary = "Mock login for testing", description = "Create a test user and return JWT tokens")
    public ResponseEntity<ApiResponse<AuthResponse>> mockLogin(@RequestBody Map<String, String> request) {
        String email = request.getOrDefault("email", "test@example.com");
        String name = request.getOrDefault("name", "Test User");
        
        // Find or create test user
        User user = userRepository.findByEmail(email)
                .orElseGet(() -> {
                    User newUser = User.builder()
                            .email(email)
                            .nickname(name)
                            .provider("mock")
                            .providerId("mock-" + email)
                            .profileImageUrl("https://via.placeholder.com/150")
                            .bio("Test user for development")
                            .isEmailVerified(true)
                            .isActive(true)
                            .build();
                    return userRepository.save(newUser);
                });
        
        // Generate tokens
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail());
        String refreshToken = jwtService.generateRefreshToken(user.getId());
        
        // Update refresh token
        user.setRefreshToken(refreshToken);
        userRepository.save(user);
        
        AuthResponse response = AuthResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .profileImageUrl(user.getProfileImageUrl())
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(3600L)
                .isNewUser(false)
                .build();
        
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}