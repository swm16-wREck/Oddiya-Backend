package com.oddiya.controller;

import com.oddiya.dto.request.OAuthRequest;
import com.oddiya.dto.response.AuthResponse;
import com.oddiya.service.OAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth/oauth")
@RequiredArgsConstructor
@Tag(name = "OAuth", description = "OAuth authentication endpoints")
public class OAuthController {
    
    private final OAuthService oAuthService;
    
    @PostMapping("/google")
    @Operation(summary = "Authenticate with Google", description = "Exchange Google auth code for tokens")
    public ResponseEntity<AuthResponse> authenticateWithGoogle(@Valid @RequestBody OAuthRequest request) {
        log.info("Google OAuth authentication request");
        
        AuthResponse response;
        if (request.getAuthCode() != null) {
            response = oAuthService.authenticateWithGoogle(request.getAuthCode());
        } else if (request.getIdToken() != null) {
            response = oAuthService.authenticateWithGoogleIdToken(request.getIdToken());
        } else {
            return ResponseEntity.badRequest().build();
        }
        
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/apple")
    @Operation(summary = "Authenticate with Apple", description = "Exchange Apple auth code for tokens")
    public ResponseEntity<AuthResponse> authenticateWithApple(@Valid @RequestBody OAuthRequest request) {
        log.info("Apple OAuth authentication request");
        
        AuthResponse response;
        if (request.getAuthCode() != null) {
            response = oAuthService.authenticateWithApple(request.getAuthCode());
        } else if (request.getIdToken() != null) {
            response = oAuthService.authenticateWithAppleIdToken(request.getIdToken());
        } else {
            return ResponseEntity.badRequest().build();
        }
        
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/verify")
    @Operation(summary = "Verify OAuth token", description = "Verify an OAuth ID token")
    public ResponseEntity<Map<String, Object>> verifyToken(
            @RequestParam String provider,
            @RequestParam String idToken) {
        log.info("OAuth token verification for provider: {}", provider);
        
        Map<String, Object> userInfo = oAuthService.verifyToken(provider, idToken);
        return ResponseEntity.ok(userInfo);
    }
}