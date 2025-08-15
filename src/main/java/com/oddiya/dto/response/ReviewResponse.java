package com.oddiya.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewResponse {
    private String id;
    private String userId;
    private String userName;
    private String userProfilePicture;
    private String placeId;
    private String placeName;
    private Integer rating;
    private String content;
    private List<String> images;
    private String visitDate;
    private String travelPlanId;
    private Integer helpfulCount;
    private Boolean isHelpful;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // For compatibility with tests - return user info as UserProfileResponse
    public UserProfileResponse getUser() {
        return UserProfileResponse.builder()
                .id(this.userId)
                .name(this.userName)
                .profileImageUrl(this.userProfilePicture)
                .build();
    }
    
    // For compatibility with tests - return place info as PlaceResponse
    public PlaceResponse getPlace() {
        return PlaceResponse.builder()
                .id(this.placeId)
                .name(this.placeName)
                .build();
    }
}