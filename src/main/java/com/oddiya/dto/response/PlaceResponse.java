package com.oddiya.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaceResponse {
    private String id;
    private String name;
    private String description;
    private String address;
    private Double latitude;
    private Double longitude;
    private String category;
    private String phoneNumber;
    private String website;
    private Map<String, String> openingHours;
    private Double priceRange;
    private Double averageRating;
    private Integer reviewCount;
    private List<String> images;
    private List<String> tags;
    private String googlePlaceId;
    private Map<String, Object> metadata;
    private Boolean isSaved;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}