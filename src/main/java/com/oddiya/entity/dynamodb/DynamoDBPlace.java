package com.oddiya.entity.dynamodb;

import lombok.*;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@DynamoDbBean
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DynamoDBPlace {
    
    private String id;
    private String naverPlaceId;
    private String name;
    private String category;
    private String description;
    private String address;
    private String roadAddress;
    
    // Store coordinates for geospatial queries
    private Double latitude;
    private Double longitude;
    
    // Composite geohash attribute for location-based searches
    private String geohash; // Generated from lat/lng for efficient geo queries
    
    private String phoneNumber;
    private String website;
    
    // Store as Map - DynamoDB Enhanced Client handles serialization
    private Map<String, String> openingHours; // day -> hours mapping
    
    private List<String> images; // List of image URLs
    private List<String> tags;
    
    // Store related IDs instead of full objects
    private List<String> reviewIds;
    private List<String> itineraryItemIds;
    
    private Double rating;
    private Integer reviewCount;
    private Integer bookmarkCount;
    private Boolean isVerified;
    private Double popularityScore;
    
    // Base entity fields
    private Instant createdAt;
    private Instant updatedAt;
    private Long version;
    private Boolean isDeleted;
    private Instant deletedAt;
    
    // Builder defaults
    public static class DynamoDBPlaceBuilder {
        public DynamoDBPlaceBuilder() {
            this.reviewCount = 0;
            this.bookmarkCount = 0;
            this.isVerified = false;
            this.popularityScore = 0.0;
            this.isDeleted = false;
        }
    }
    
    // DynamoDB annotations
    @DynamoDbPartitionKey
    public String getId() {
        return id;
    }
    
    @DynamoDbSecondaryPartitionKey(indexNames = "naver-place-index")
    @DynamoDbAttribute("naver_place_id")
    public String getNaverPlaceId() {
        return naverPlaceId;
    }
    
    @DynamoDbSecondaryPartitionKey(indexNames = "category-index")
    public String getCategory() {
        return category;
    }
    
    @DynamoDbSecondaryPartitionKey(indexNames = "geohash-index")
    public String getGeohash() {
        return geohash;
    }
    
    @DynamoDbSecondaryPartitionKey(indexNames = "rating-index")
    public Double getRating() {
        return rating;
    }
    
    @DynamoDbSecondarySortKey(indexNames = "category-popularity-index")
    public Double getPopularityScore() {
        return popularityScore;
    }
    
    @DynamoDbAttribute("road_address")
    public String getRoadAddress() {
        return roadAddress;
    }
    
    @DynamoDbAttribute("phone_number")
    public String getPhoneNumber() {
        return phoneNumber;
    }
    
    @DynamoDbAttribute("opening_hours")
    public Map<String, String> getOpeningHours() {
        return openingHours;
    }
    
    @DynamoDbAttribute("review_count")
    public Integer getReviewCount() {
        return reviewCount;
    }
    
    @DynamoDbAttribute("bookmark_count")
    public Integer getBookmarkCount() {
        return bookmarkCount;
    }
    
    @DynamoDbAttribute("is_verified")
    public Boolean getIsVerified() {
        return isVerified;
    }
    
    @DynamoDbAttribute("popularity_score")
    public Double getPopularityScoreAttr() {
        return popularityScore;
    }
    
    @DynamoDbAttribute("created_at")
    public Instant getCreatedAt() {
        return createdAt;
    }
    
    @DynamoDbAttribute("updated_at")
    public Instant getUpdatedAt() {
        return updatedAt;
    }
    
    
    public Long getVersion() {
        return version;
    }
    
    @DynamoDbAttribute("is_deleted")
    public Boolean getIsDeleted() {
        return isDeleted;
    }
    
    @DynamoDbAttribute("deleted_at")
    public Instant getDeletedAt() {
        return deletedAt;
    }
    
    // Helper method to calculate and set geohash from coordinates
    public void calculateAndSetGeohash() {
        if (latitude != null && longitude != null) {
            // This is a simple example - use a proper geohash library like ch.hsr.geohash
            this.geohash = String.format("%.2f,%.2f", latitude, longitude);
        }
    }
}