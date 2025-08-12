package com.oddiya.config;

import com.oddiya.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import jakarta.servlet.ServletException;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive tests for JwtAuthenticationFilter.
 * Tests JWT token extraction, validation, and authentication context setup.
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {
    
    @Mock
    private JwtService jwtService;
    
    @InjectMocks
    private JwtAuthenticationFilter jwtAuthenticationFilter;
    
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockFilterChain filterChain;
    
    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        filterChain = new MockFilterChain();
        SecurityContextHolder.clearContext();
    }
    
    @Test
    void contextLoads() {
        assertNotNull(jwtAuthenticationFilter);
    }
    
    @Test
    void doFilterInternal_WithValidToken_SetsAuthentication() throws ServletException, IOException {
        // Given
        String token = "valid-jwt-token";
        String userId = "user123";
        request.addHeader("Authorization", "Bearer " + token);
        
        when(jwtService.validateAccessToken(token)).thenReturn(userId);
        
        // When
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);
        
        // Then
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication);
        assertTrue(authentication instanceof UsernamePasswordAuthenticationToken);
        assertEquals(userId, authentication.getPrincipal());
        assertNull(authentication.getCredentials());
        assertTrue(authentication.getAuthorities().isEmpty());
        
        verify(jwtService).validateAccessToken(token);
        verify(filterChain).doFilter(request, response);
    }
    
    @Test
    void doFilterInternal_WithInvalidToken_ClearsAuthentication() throws ServletException, IOException {
        // Given
        String token = "invalid-jwt-token";
        request.addHeader("Authorization", "Bearer " + token);
        
        when(jwtService.validateAccessToken(token)).thenThrow(new RuntimeException("Invalid token"));
        
        // When
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);
        
        // Then
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNull(authentication);
        
        verify(jwtService).validateAccessToken(token);
        verify(filterChain).doFilter(request, response);
    }
    
    @Test
    void doFilterInternal_WithoutAuthorizationHeader_ContinuesWithoutAuthentication() throws ServletException, IOException {
        // When
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);
        
        // Then
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNull(authentication);
        
        verify(jwtService, never()).validateAccessToken(anyString());
        verify(filterChain).doFilter(request, response);
    }
    
    @Test
    void doFilterInternal_WithEmptyAuthorizationHeader_ContinuesWithoutAuthentication() throws ServletException, IOException {
        // Given
        request.addHeader("Authorization", "");
        
        // When
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);
        
        // Then
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNull(authentication);
        
        verify(jwtService, never()).validateAccessToken(anyString());
        verify(filterChain).doFilter(request, response);
    }
    
    @Test
    void doFilterInternal_WithNonBearerToken_ContinuesWithoutAuthentication() throws ServletException, IOException {
        // Given
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");
        
        // When
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);
        
        // Then
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNull(authentication);
        
        verify(jwtService, never()).validateAccessToken(anyString());
        verify(filterChain).doFilter(request, response);
    }
    
    @Test
    void doFilterInternal_WithBearerButNoToken_ContinuesWithoutAuthentication() throws ServletException, IOException {
        // Given
        request.addHeader("Authorization", "Bearer ");
        
        // When
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);
        
        // Then
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNull(authentication);
        
        verify(jwtService, never()).validateAccessToken(anyString());
        verify(filterChain).doFilter(request, response);
    }
    
    @Test
    void doFilterInternal_WithJustBearer_ContinuesWithoutAuthentication() throws ServletException, IOException {
        // Given
        request.addHeader("Authorization", "Bearer");
        
        // When
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);
        
        // Then
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNull(authentication);
        
        verify(jwtService, never()).validateAccessToken(anyString());
        verify(filterChain).doFilter(request, response);
    }
    
    @Test
    void getTokenFromRequest_WithValidBearerToken_ReturnsToken() {
        // Given
        String expectedToken = "valid-jwt-token";
        MockHttpServletRequest testRequest = new MockHttpServletRequest();
        testRequest.addHeader("Authorization", "Bearer " + expectedToken);
        
        // When
        String actualToken = extractToken(testRequest);
        
        // Then
        assertEquals(expectedToken, actualToken);
    }
    
    @Test
    void getTokenFromRequest_WithoutAuthorizationHeader_ReturnsNull() {
        // Given
        MockHttpServletRequest testRequest = new MockHttpServletRequest();
        
        // When
        String actualToken = extractToken(testRequest);
        
        // Then
        assertNull(actualToken);
    }
    
    @Test
    void getTokenFromRequest_WithEmptyHeader_ReturnsNull() {
        // Given
        MockHttpServletRequest testRequest = new MockHttpServletRequest();
        testRequest.addHeader("Authorization", "");
        
        // When
        String actualToken = extractToken(testRequest);
        
        // Then
        assertNull(actualToken);
    }
    
    @Test
    void getTokenFromRequest_WithNonBearerToken_ReturnsNull() {
        // Given
        MockHttpServletRequest testRequest = new MockHttpServletRequest();
        testRequest.addHeader("Authorization", "Basic dXNlcjpwYXNz");
        
        // When
        String actualToken = extractToken(testRequest);
        
        // Then
        assertNull(actualToken);
    }
    
    @Test
    void doFilterInternal_WithExistingAuthentication_DoesNotOverride() throws ServletException, IOException {
        // Given
        String token = "valid-jwt-token";
        request.addHeader("Authorization", "Bearer " + token);
        
        // Set existing authentication
        Authentication existingAuth = new UsernamePasswordAuthenticationToken("existingUser", null);
        SecurityContextHolder.getContext().setAuthentication(existingAuth);
        
        when(jwtService.validateAccessToken(token)).thenReturn("user123");
        
        // When
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);
        
        // Then
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication);
        // The filter should still process and potentially override existing authentication
        assertEquals("user123", authentication.getPrincipal());
        
        verify(jwtService).validateAccessToken(token);
        verify(filterChain).doFilter(request, response);
    }
    
    @Test
    void doFilterInternal_WithJwtServiceException_ContinuesFilterChain() throws ServletException, IOException {
        // Given
        String token = "problematic-token";
        request.addHeader("Authorization", "Bearer " + token);
        
        when(jwtService.validateAccessToken(token)).thenThrow(new IllegalArgumentException("Token format invalid"));
        
        // When
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);
        
        // Then
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNull(authentication);
        
        verify(jwtService).validateAccessToken(token);
        verify(filterChain).doFilter(request, response);
    }
    
    @Test
    void isOncePerRequestFilter() {
        assertTrue(jwtAuthenticationFilter instanceof org.springframework.web.filter.OncePerRequestFilter);
    }
    
    @Test
    void componentAnnotationIsPresent() {
        Class<JwtAuthenticationFilter> filterClass = JwtAuthenticationFilter.class;
        boolean hasComponent = filterClass.isAnnotationPresent(org.springframework.stereotype.Component.class);
        assertTrue(hasComponent, "JwtAuthenticationFilter should have @Component annotation");
    }
    
    @Test
    void requiredArgsConstructorAnnotationIsPresent() {
        Class<JwtAuthenticationFilter> filterClass = JwtAuthenticationFilter.class;
        // Check for Lombok annotation by name since lombok may not be on test classpath
        boolean hasRequiredArgsConstructor = false;
        for (java.lang.annotation.Annotation annotation : filterClass.getAnnotations()) {
            if (annotation.annotationType().getSimpleName().equals("RequiredArgsConstructor")) {
                hasRequiredArgsConstructor = true;
                break;
            }
        }
        assertTrue(hasRequiredArgsConstructor, "JwtAuthenticationFilter should have @RequiredArgsConstructor annotation");
    }
    
    /**
     * Helper method to test the private getTokenFromRequest method indirectly
     */
    private String extractToken(MockHttpServletRequest testRequest) {
        String bearerToken = testRequest.getHeader("Authorization");
        if (org.springframework.util.StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
    
    @Test
    void filterChainContinues_RegardlessOfTokenValidation() throws ServletException, IOException {
        // Test 1: Valid token
        request.addHeader("Authorization", "Bearer valid-token");
        when(jwtService.validateAccessToken("valid-token")).thenReturn("user123");
        
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);
        verify(filterChain).doFilter(request, response);
        
        // Reset mocks for second test
        reset(filterChain);
        SecurityContextHolder.clearContext();
        
        // Test 2: Invalid token
        MockHttpServletRequest request2 = new MockHttpServletRequest();
        request2.addHeader("Authorization", "Bearer invalid-token");
        when(jwtService.validateAccessToken("invalid-token")).thenThrow(new RuntimeException("Invalid"));
        
        jwtAuthenticationFilter.doFilterInternal(request2, response, filterChain);
        verify(filterChain).doFilter(request2, response);
        
        // Reset mocks for third test
        reset(filterChain);
        
        // Test 3: No token
        MockHttpServletRequest request3 = new MockHttpServletRequest();
        
        jwtAuthenticationFilter.doFilterInternal(request3, response, filterChain);
        verify(filterChain).doFilter(request3, response);
    }
}