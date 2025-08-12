package com.oddiya.contract;

import com.oddiya.dto.response.ApiResponse;
import com.oddiya.dto.response.AuthResponse;
import com.oddiya.dto.request.LoginRequest;
import com.oddiya.dto.request.RefreshTokenRequest;
import com.oddiya.service.AuthService;
import org.mockito.BDDMockito;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

/**
 * Base class for Authentication contract tests
 */
public class AuthContractTestBase extends ContractTestBase {

    @MockBean
    private AuthService authService;

    public void setupAuthMocks() {
        // Mock successful login
        AuthResponse authResponse = AuthResponse.builder()
                .accessToken("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c2VyMTIzIiwiaWF0IjoxNjk4ODI0NDAwLCJleHAiOjE2OTg4MjgwMDB9.signature")
                .refreshToken("refresh-token-12345")
                .tokenType("Bearer")
                .expiresIn(3600L)
                .userId("user123")
                .build();
        
        BDDMockito.given(authService.login(any(LoginRequest.class)))
                .willReturn(authResponse);
        
        // Mock token refresh
        BDDMockito.given(authService.refreshToken(any(RefreshTokenRequest.class)))
                .willReturn(authResponse);
        
        // Mock token validation
        BDDMockito.given(authService.validateToken(anyString()))
                .willReturn(true);
        
        // Mock logout - void method, use doNothing
        BDDMockito.willDoNothing().given(authService).logout(anyString());
    }
}