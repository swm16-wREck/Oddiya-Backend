package com.oddiya.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oddiya.dto.response.AuthResponse;
import com.oddiya.entity.User;
import com.oddiya.exception.BadRequestException;
import com.oddiya.exception.UnauthorizedException;
import com.oddiya.repository.UserRepository;
import com.oddiya.service.SupabaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SupabaseServiceImpl implements SupabaseService {
    
    private final WebClient webClient;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    
    @Value("${app.supabase.url}")
    private String supabaseUrl;
    
    @Value("${app.supabase.anon-key}")
    private String supabaseAnonKey;
    
    @Value("${app.supabase.service-key}")
    private String supabaseServiceKey;
    
    @Override
    public AuthResponse signInWithEmail(String email, String password) {
        Map<String, String> body = new HashMap<>();
        body.put("email", email);
        body.put("password", password);
        
        try {
            String response = webClient.post()
                    .uri(supabaseUrl + "/auth/v1/token?grant_type=password")
                    .header("apikey", supabaseAnonKey)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            
            JsonNode jsonNode = objectMapper.readTree(response);
            return createAuthResponse(jsonNode);
        } catch (Exception e) {
            log.error("Supabase sign in failed", e);
            throw new UnauthorizedException("Authentication failed");
        }
    }
    
    @Override
    public AuthResponse signInWithOAuth(String provider, String idToken) {
        Map<String, String> body = new HashMap<>();
        body.put("provider", provider);
        body.put("id_token", idToken);
        
        try {
            String response = webClient.post()
                    .uri(supabaseUrl + "/auth/v1/token?grant_type=id_token")
                    .header("apikey", supabaseAnonKey)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            
            JsonNode jsonNode = objectMapper.readTree(response);
            return createAuthResponse(jsonNode);
        } catch (Exception e) {
            log.error("Supabase OAuth sign in failed", e);
            throw new UnauthorizedException("OAuth authentication failed");
        }
    }
    
    @Override
    public AuthResponse signUp(String email, String password) {
        Map<String, String> body = new HashMap<>();
        body.put("email", email);
        body.put("password", password);
        
        try {
            String response = webClient.post()
                    .uri(supabaseUrl + "/auth/v1/signup")
                    .header("apikey", supabaseAnonKey)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            
            JsonNode jsonNode = objectMapper.readTree(response);
            return createAuthResponse(jsonNode);
        } catch (Exception e) {
            log.error("Supabase sign up failed", e);
            throw new BadRequestException("Sign up failed");
        }
    }
    
    @Override
    public void signOut(String accessToken) {
        try {
            webClient.post()
                    .uri(supabaseUrl + "/auth/v1/logout")
                    .header("apikey", supabaseAnonKey)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .block();
        } catch (Exception e) {
            log.error("Supabase sign out failed", e);
        }
    }
    
    @Override
    public AuthResponse refreshToken(String refreshToken) {
        Map<String, String> body = new HashMap<>();
        body.put("refresh_token", refreshToken);
        
        try {
            String response = webClient.post()
                    .uri(supabaseUrl + "/auth/v1/token?grant_type=refresh_token")
                    .header("apikey", supabaseAnonKey)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            
            JsonNode jsonNode = objectMapper.readTree(response);
            return createAuthResponse(jsonNode);
        } catch (Exception e) {
            log.error("Supabase refresh token failed", e);
            throw new UnauthorizedException("Token refresh failed");
        }
    }
    
    @Override
    public boolean verifyToken(String accessToken) {
        try {
            String response = webClient.get()
                    .uri(supabaseUrl + "/auth/v1/user")
                    .header("apikey", supabaseAnonKey)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            
            return response != null && !response.isEmpty();
        } catch (Exception e) {
            log.debug("Token verification failed", e);
            return false;
        }
    }
    
    @Override
    public String getUserId(String accessToken) {
        try {
            String response = webClient.get()
                    .uri(supabaseUrl + "/auth/v1/user")
                    .header("apikey", supabaseAnonKey)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            
            JsonNode jsonNode = objectMapper.readTree(response);
            return jsonNode.get("id").asText();
        } catch (Exception e) {
            log.error("Failed to get user ID from token", e);
            throw new UnauthorizedException("Invalid token");
        }
    }
    
    private AuthResponse createAuthResponse(JsonNode jsonNode) {
        String userId = jsonNode.get("user").get("id").asText();
        String email = jsonNode.get("user").get("email").asText();
        String accessToken = jsonNode.get("access_token").asText();
        String refreshToken = jsonNode.get("refresh_token").asText();
        long expiresIn = jsonNode.get("expires_in").asLong();
        
        // Find or create user in our database
        User user = userRepository.findByEmail(email)
                .orElseGet(() -> {
                    User newUser = User.builder()
                            .id(userId)
                            .email(email)
                            .nickname(email.split("@")[0])
                            .provider("supabase")
                            .providerId(userId)
                            .isEmailVerified(true)
                            .isActive(true)
                            .build();
                    return userRepository.save(newUser);
                });
        
        return AuthResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .profileImageUrl(user.getProfileImageUrl())
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(expiresIn)
                .isNewUser(false)
                .build();
    }
}