package com.oddiya.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaceDTO {
    private String id;
    private String naverPlaceId;
    private String name;
    private String category;
    private String description;
    private String address;
    private String roadAddress;
    private Double latitude;
    private Double longitude;
    private String phoneNumber;
    private String website;
    private Map<String, String> openingHours = new HashMap<>();
    private List<String> images = new ArrayList<>();
    private List<String> tags = new ArrayList<>();
    private Double rating;
    private Integer reviewCount;
    private Integer bookmarkCount;
    private boolean isVerified;
    private Double popularityScore;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}