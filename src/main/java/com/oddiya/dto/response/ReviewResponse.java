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
}