package com.oddiya.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "OAuth authentication request")
public class OAuthRequest {
    
    @Schema(description = "Authorization code from OAuth provider", example = "4/0AX4XfWh...")
    private String authCode;
    
    @Schema(description = "ID token from OAuth provider", example = "eyJhbGciOiJSUzI1NiIsImtpZCI6Ij...")
    private String idToken;
    
    @Schema(description = "OAuth provider name", example = "google")
    private String provider;
}