package com.oddiya.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileResponse {
    private String id;
    private String email;
    private String name;
    private String bio;
    private String profilePicture;
    private String phoneNumber;
    private String preferredLanguage;
    private String timezone;
    private Boolean notificationsEnabled;
    private Boolean isPublic;
    private Integer followersCount;
    private Integer followingCount;
    private Integer travelPlansCount;
    private Integer reviewsCount;
    private Integer videosCount;
    private Boolean isFollowing;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}