package com.oddiya.service.impl;

import com.oddiya.exception.UnauthorizedException;
import com.oddiya.service.JwtService;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class JwtServiceImpl implements JwtService {
    
    private final SecretKey key;
    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;
    
    public JwtServiceImpl(
            @Value("${app.jwt.secret:${jwt.secret:default-secret-key-for-jwt-token-generation-must-be-at-least-256-bits}}") String secret,
            @Value("${app.jwt.expiration:${jwt.expiration:3600000}}") long accessTokenExpiration,
            @Value("${app.jwt.refresh-expiration:${jwt.refresh-expiration:604800000}}") long refreshTokenExpiration) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiration = accessTokenExpiration;
        this.refreshTokenExpiration = refreshTokenExpiration;
    }
    
    @Override
    public String generateAccessToken(String userId, String email) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("email", email);
        claims.put("type", "access");
        
        return createToken(claims, userId, accessTokenExpiration);
    }
    
    @Override
    public String generateRefreshToken(String userId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("type", "refresh");
        
        return createToken(claims, userId, refreshTokenExpiration);
    }
    
    @Override
    public String validateAccessToken(String token) {
        try {
            Claims claims = extractAllClaims(token);
            
            if (!"access".equals(claims.get("type"))) {
                throw new UnauthorizedException("Invalid token type");
            }
            
            return claims.getSubject();
        } catch (JwtException e) {
            throw new UnauthorizedException("Invalid access token");
        }
    }
    
    @Override
    public String validateRefreshToken(String token) {
        try {
            Claims claims = extractAllClaims(token);
            
            if (!"refresh".equals(claims.get("type"))) {
                throw new UnauthorizedException("Invalid token type");
            }
            
            return claims.getSubject();
        } catch (JwtException e) {
            throw new UnauthorizedException("Invalid refresh token");
        }
    }
    
    @Override
    public String extractUserId(String token) {
        return extractAllClaims(token).getSubject();
    }
    
    private String createToken(Map<String, Object> claims, String subject, long expiration) {
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(key)
                .compact();
    }
    
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}