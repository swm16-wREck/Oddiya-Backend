package com.oddiya.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TravelPlanResponse {
    private String id;
    private String userId;
    private String userName;
    private String userProfilePicture;
    private String title;
    private String description;
    private String destination;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
    private Boolean isPublic;
    private Boolean aiGenerated;
    private String imageUrl;
    private List<String> tags;
    private Long viewCount;
    private Long saveCount;
    private List<ItineraryItemResponse> itineraryItems;
    private List<String> collaboratorIds;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}