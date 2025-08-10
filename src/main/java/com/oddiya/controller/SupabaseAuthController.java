package com.oddiya.controller;

import com.oddiya.dto.request.EmailLoginRequest;
import com.oddiya.dto.request.SignUpRequest;
import com.oddiya.dto.request.RefreshTokenRequest;
import com.oddiya.dto.response.AuthResponse;
import com.oddiya.service.SupabaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth/supabase")
@RequiredArgsConstructor
@Tag(name = "Supabase Auth", description = "Supabase authentication endpoints")
public class SupabaseAuthController {
    
    private final SupabaseService supabaseService;
    
    @PostMapping("/signup")
    @Operation(summary = "Sign up with email", description = "Create a new account with email and password")
    public ResponseEntity<AuthResponse> signUp(@Valid @RequestBody SignUpRequest request) {
        log.info("Sign up request for email: {}", request.getEmail());
        
        AuthResponse response = supabaseService.signUp(request.getEmail(), request.getPassword());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    @PostMapping("/signin")
    @Operation(summary = "Sign in with email", description = "Authenticate with email and password")
    public ResponseEntity<AuthResponse> signIn(@Valid @RequestBody EmailLoginRequest request) {
        log.info("Sign in request for email: {}", request.getEmail());
        
        AuthResponse response = supabaseService.signInWithEmail(request.getEmail(), request.getPassword());
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/signout")
    @Operation(summary = "Sign out", description = "Sign out the current user")
    public ResponseEntity<Void> signOut(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        supabaseService.signOut(token);
        return ResponseEntity.ok().build();
    }
    
    @PostMapping("/refresh")
    @Operation(summary = "Refresh token", description = "Refresh the access token using refresh token")
    public ResponseEntity<AuthResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        log.info("Refresh token request");
        
        AuthResponse response = supabaseService.refreshToken(request.getRefreshToken());
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/verify")
    @Operation(summary = "Verify token", description = "Verify if the access token is valid")
    public ResponseEntity<Boolean> verifyToken(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        boolean isValid = supabaseService.verifyToken(token);
        return ResponseEntity.ok(isValid);
    }
    
    @GetMapping("/user")
    @Operation(summary = "Get user ID", description = "Get the user ID from the access token")
    public ResponseEntity<String> getUserId(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        String userId = supabaseService.getUserId(token);
        return ResponseEntity.ok(userId);
    }
}