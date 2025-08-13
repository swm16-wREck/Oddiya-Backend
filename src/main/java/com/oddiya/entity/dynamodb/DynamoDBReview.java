package com.oddiya.entity.dynamodb;

import lombok.*;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

import java.time.Instant;
import java.util.List;

@DynamoDbBean
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DynamoDBReview {
    
    private String id;
    private String placeId;
    private String userId;
    private Integer rating; // 1-5
    private String content;
    private List<String> images; // List of image URLs
    private String visitDate; // LocalDateTime as ISO string
    private Integer likesCount;
    private Boolean isVerifiedPurchase;
    
    // Store user IDs who liked this review instead of full objects
    private List<String> likedByIds;
    
    // Additional fields for better querying
    private String placeRatingComposite; // Format: "placeId#rating" for compound queries
    private String reviewDateSort; // ISO date for sorting
    
    // Base entity fields
    private Instant createdAt;
    private Instant updatedAt;
    private Long version;
    private Boolean isDeleted;
    private Instant deletedAt;
    
    public static class DynamoDBReviewBuilder {
        public DynamoDBReviewBuilder() {
            this.likesCount = 0;
            this.isVerifiedPurchase = false;
            this.isDeleted = false;
        }
    }
    
    @DynamoDbPartitionKey
    public String getId() {
        return id;
    }
    
    @DynamoDbSecondaryPartitionKey(indexNames = "place-index")
    @DynamoDbAttribute("place_id")
    public String getPlaceId() {
        return placeId;
    }
    
    @DynamoDbSecondaryPartitionKey(indexNames = "user-index")
    @DynamoDbAttribute("user_id")
    public String getUserId() {
        return userId;
    }
    
    @DynamoDbSecondaryPartitionKey(indexNames = "rating-index")
    public Integer getRating() {
        return rating;
    }
    
    @DynamoDbSecondaryPartitionKey(indexNames = "place-rating-index")
    @DynamoDbAttribute("place_rating_composite")
    public String getPlaceRatingComposite() {
        return placeRatingComposite;
    }
    
    @DynamoDbSecondarySortKey(indexNames = "place-date-index")
    @DynamoDbAttribute("review_date_sort")
    public String getReviewDateSort() {
        return reviewDateSort;
    }
    
    @DynamoDbAttribute("visit_date")
    public String getVisitDate() {
        return visitDate;
    }
    
    @DynamoDbAttribute("likes_count")
    public Integer getLikesCount() {
        return likesCount;
    }
    
    @DynamoDbAttribute("is_verified_purchase")
    public Boolean getIsVerifiedPurchase() {
        return isVerifiedPurchase;
    }
    
    @DynamoDbAttribute("liked_by_ids")
    public List<String> getLikedByIds() {
        return likedByIds;
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
    
    // Helper method to create composite key for place-rating queries
    public void generatePlaceRatingComposite() {
        if (placeId != null && rating != null) {
            this.placeRatingComposite = placeId + "#" + rating;
        }
    }
    
    // Helper method to set review date for sorting
    public void setReviewDateSortFromCreated() {
        if (createdAt != null) {
            this.reviewDateSort = createdAt.toString();
        }
    }
}