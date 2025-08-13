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
public class DynamoDBTravelPlan {
    
    private String id;
    private String userId;
    private String title;
    private String description;
    private String destination;
    private String startDate; // ISO format: YYYY-MM-DD
    private String endDate;
    private Integer numberOfPeople;
    private String budget; // BigDecimal as String
    private String status; // TravelPlanStatus enum as String
    private Boolean isPublic;
    private Boolean isAiGenerated;
    
    // Store as Map - DynamoDB Enhanced Client handles serialization
    private Map<String, String> preferences;
    
    // Store nested items as List of Maps instead of separate table for simplicity
    private List<DynamoDBItineraryItem> itineraryItems;
    
    // Store collaborator IDs instead of full objects
    private List<String> collaboratorIds;
    private List<String> videoIds;
    
    private Long viewCount;
    private Long likeCount;
    private Long shareCount;
    private Long saveCount;
    
    private List<String> tags;
    private String coverImageUrl;
    
    // Base entity fields
    private Instant createdAt;
    private Instant updatedAt;
    private Long version;
    private Boolean isDeleted;
    private Instant deletedAt;
    
    // Builder defaults
    public static class DynamoDBTravelPlanBuilder {
        public DynamoDBTravelPlanBuilder() {
            this.isPublic = false;
            this.isAiGenerated = false;
            this.isDeleted = false;
            this.viewCount = 0L;
            this.likeCount = 0L;
            this.shareCount = 0L;
            this.saveCount = 0L;
        }
    }
    
    // DynamoDB annotations on getter methods
    @DynamoDbPartitionKey
    public String getId() {
        return id;
    }
    
    @DynamoDbSecondaryPartitionKey(indexNames = "userId-index")
    public String getUserId() {
        return userId;
    }
    
    @DynamoDbSecondaryPartitionKey(indexNames = "destination-index")
    public String getDestination() {
        return destination;
    }
    
    @DynamoDbSecondaryPartitionKey(indexNames = "status-index")
    public String getStatus() {
        return status;
    }
    
    @DynamoDbSecondaryPartitionKey(indexNames = "public-plans-index")
    public Boolean getIsPublic() {
        return isPublic;
    }
    
    @DynamoDbAttribute("start_date")
    public String getStartDate() {
        return startDate;
    }
    
    @DynamoDbAttribute("end_date") 
    public String getEndDate() {
        return endDate;
    }
    
    @DynamoDbAttribute("number_of_people")
    public Integer getNumberOfPeople() {
        return numberOfPeople;
    }
    
    @DynamoDbAttribute("is_ai_generated")
    public Boolean getIsAiGenerated() {
        return isAiGenerated;
    }
    
    @DynamoDbAttribute("view_count")
    public Long getViewCount() {
        return viewCount;
    }
    
    @DynamoDbAttribute("like_count")
    public Long getLikeCount() {
        return likeCount;
    }
    
    @DynamoDbAttribute("share_count")
    public Long getShareCount() {
        return shareCount;
    }
    
    @DynamoDbAttribute("save_count")
    public Long getSaveCount() {
        return saveCount;
    }
    
    @DynamoDbAttribute("cover_image_url")
    public String getCoverImageUrl() {
        return coverImageUrl;
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
}