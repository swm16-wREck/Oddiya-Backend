package com.oddiya.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private String userId;
    private String email;
    private String nickname;
    private String profileImageUrl;
    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private Long expiresIn;
    private boolean isNewUser;
    private UserProfileResponse user; // User profile information
}