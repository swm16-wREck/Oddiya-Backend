package com.oddiya.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {
    
    @NotBlank(message = "Provider is required")
    private String provider; // google, apple
    
    @NotBlank(message = "ID token is required")
    private String idToken;
    
    private String token; // Alternative field name for OAuth token
    
    private String deviceId;
    private String deviceType;
}