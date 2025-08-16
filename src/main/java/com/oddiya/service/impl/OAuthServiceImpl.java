package com.oddiya.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oddiya.config.OAuthConfig;
import com.oddiya.dto.response.AuthResponse;
import com.oddiya.entity.User;
import com.oddiya.exception.UnauthorizedException;
import com.oddiya.repository.UserRepository;
import com.oddiya.service.OAuthService;
import com.oddiya.service.SupabaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OAuthServiceImpl implements OAuthService {
    
    private final WebClient webClient;
    private final OAuthConfig oAuthConfig;
    private final UserRepository userRepository;
    private final SupabaseService supabaseService;
    private final ObjectMapper objectMapper;
    
    @Value("${app.supabase.url}")
    private String supabaseUrl;
    
    @Value("${app.supabase.anon-key}")
    private String supabaseAnonKey;
    
    @Override
    public Map<String, Object> verifyToken(String provider, String idToken) {
        log.info("Verifying OAuth token for provider: {}", provider);
        
        try {
            // For Supabase OAuth verification
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("provider", provider);
            requestBody.put("id_token", idToken);
            
            Map<String, Object> response = webClient.post()
                .uri(supabaseUrl + "/auth/v1/verify")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + supabaseAnonKey)
                .header("apikey", supabaseAnonKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .onErrorResume(e -> {
                    log.error("Error verifying OAuth token: ", e);
                    return Mono.error(new UnauthorizedException("Invalid OAuth token"));
                })
                .block();
            
            if (response == null || !response.containsKey("user")) {
                throw new UnauthorizedException("Invalid OAuth token");
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> user = (Map<String, Object>) response.get("user");
            return user;
            
        } catch (Exception e) {
            log.error("OAuth verification failed: ", e);
            throw new UnauthorizedException("OAuth verification failed");
        }
    }
    
    @Override
    public AuthResponse authenticateWithGoogle(String authCode) {
        log.info("Authenticating with Google using auth code");
        
        try {
            // Exchange auth code for tokens
            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("code", authCode);
            formData.add("client_id", oAuthConfig.getGoogle().getClientId());
            formData.add("client_secret", oAuthConfig.getGoogle().getClientSecret());
            formData.add("redirect_uri", oAuthConfig.getGoogle().getRedirectUri());
            formData.add("grant_type", "authorization_code");
            
            String tokenResponse = webClient.post()
                    .uri(oAuthConfig.getGoogle().getTokenUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData(formData))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            
            JsonNode tokenJson = objectMapper.readTree(tokenResponse);
            String idToken = tokenJson.get("id_token").asText();
            
            // Use Supabase OAuth flow
            return supabaseService.signInWithOAuth("google", idToken);
            
        } catch (Exception e) {
            log.error("Google authentication failed", e);
            throw new UnauthorizedException("Google authentication failed");
        }
    }
    
    @Override
    public AuthResponse authenticateWithApple(String authCode) {
        log.info("Authenticating with Apple using auth code");
        
        try {
            // Exchange auth code for tokens
            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("code", authCode);
            formData.add("client_id", oAuthConfig.getApple().getClientId());
            formData.add("client_secret", generateAppleClientSecret());
            formData.add("redirect_uri", oAuthConfig.getApple().getRedirectUri());
            formData.add("grant_type", "authorization_code");
            
            String tokenResponse = webClient.post()
                    .uri(oAuthConfig.getApple().getTokenUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData(formData))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            
            JsonNode tokenJson = objectMapper.readTree(tokenResponse);
            String idToken = tokenJson.get("id_token").asText();
            
            // Use Supabase OAuth flow
            return supabaseService.signInWithOAuth("apple", idToken);
            
        } catch (Exception e) {
            log.error("Apple authentication failed", e);
            throw new UnauthorizedException("Apple authentication failed");
        }
    }
    
    @Override
    public AuthResponse authenticateWithGoogleIdToken(String idToken) {
        log.info("Authenticating with Google ID token");
        
        try {
            // Directly use the ID token with Supabase
            return supabaseService.signInWithOAuth("google", idToken);
        } catch (Exception e) {
            log.error("Google ID token authentication failed", e);
            throw new UnauthorizedException("Google authentication failed");
        }
    }
    
    @Override
    public AuthResponse authenticateWithAppleIdToken(String idToken) {
        log.info("Authenticating with Apple ID token");
        
        try {
            // Directly use the ID token with Supabase
            return supabaseService.signInWithOAuth("apple", idToken);
        } catch (Exception e) {
            log.error("Apple ID token authentication failed", e);
            throw new UnauthorizedException("Apple authentication failed");
        }
    }
    
    private String generateAppleClientSecret() {
        // In production, this should generate a JWT using Apple's private key
        // For now, returning a placeholder
        // Implement JWT generation with Apple's requirements:
        // - Algorithm: ES256
        // - Claims: iss (team ID), iat (issued at), exp (expiration), aud (https://appleid.apple.com), sub (client ID)
        log.warn("Apple client secret generation not fully implemented");
        return "placeholder_secret";
    }
}