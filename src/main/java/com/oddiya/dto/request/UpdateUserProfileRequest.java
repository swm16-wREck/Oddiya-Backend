package com.oddiya.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserProfileRequest {
    
    @Size(min = 1, max = 100, message = "Name must be between 1 and 100 characters")
    private String name;
    
    @Size(min = 1, max = 50, message = "Nickname must be between 1 and 50 characters")
    private String nickname;
    
    @Size(max = 500, message = "Bio cannot exceed 500 characters")
    private String bio;
    
    private String profilePicture;
    
    private String profileImageUrl;
    
    private String phoneNumber;
    
    private String preferredLanguage;
    
    private String timezone;
    
    private Boolean notificationsEnabled;
    
    private Boolean isPublic;
}