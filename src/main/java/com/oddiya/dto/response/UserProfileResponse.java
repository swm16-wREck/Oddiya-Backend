package com.oddiya.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Enhanced User Profile Response DTO
 * Includes comprehensive user information and social features
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "User profile response with social and travel information")
public class UserProfileResponse {
    
    @Schema(description = "User ID")
    private String id;
    
    @Schema(description = "User email address")
    private String email;
    
    @Schema(description = "Username")
    private String username;
    
    @Schema(description = "Display name")
    private String name;
    
    @Schema(description = "User nickname")
    private String nickname;
    
    @Schema(description = "User bio/description")
    private String bio;
    
    @Schema(description = "Profile picture URL")
    private String profilePicture;
    
    @Schema(description = "Profile image URL (alternative)")
    private String profileImageUrl;
    
    @Schema(description = "Phone number")
    private String phoneNumber;
    
    @Schema(description = "Preferred language")
    private String preferredLanguage;
    
    @Schema(description = "User timezone")
    private String timezone;
    
    @Schema(description = "Notifications enabled")
    private Boolean notificationsEnabled;
    
    @Schema(description = "Profile is public")
    private Boolean isPublic;
    
    @Schema(description = "Email is verified")
    private Boolean isEmailVerified;
    
    @Schema(description = "Premium account")
    private Boolean isPremium;
    
    @Schema(description = "Account is active")
    private Boolean isActive;
    
    @Schema(description = "OAuth provider")
    private String provider;
    
    @Schema(description = "User preferences")
    private Map<String, String> preferences;
    
    @Schema(description = "Travel preferences for AI planning")
    private Map<String, String> travelPreferences;
    
    @Schema(description = "Number of followers")
    private Long followersCount;
    
    @Schema(description = "Number of users following")
    private Long followingCount;
    
    @Schema(description = "Number of travel plans created")
    private Long travelPlansCount;
    
    @Schema(description = "Number of reviews written")
    private Integer reviewsCount;
    
    @Schema(description = "Number of videos created")
    private Integer videosCount;
    
    @Schema(description = "Is current user following this user")
    private Boolean isFollowing;
    
    @Schema(description = "Last login timestamp")
    private LocalDateTime lastLoginAt;
    
    @Schema(description = "Account creation timestamp")
    private LocalDateTime createdAt;
    
    @Schema(description = "Last update timestamp")
    private LocalDateTime updatedAt;
}