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
public class DynamoDBVideo {
    
    private String id;
    private String userId;
    private String travelPlanId;
    private String title;
    private String description;
    private String videoUrl;
    private String thumbnailUrl;
    private Integer durationSeconds;
    private Long fileSize;
    private String status; // VideoStatus enum as String
    private Boolean isPublic;
    
    private List<String> tags;
    
    // Store as Map - DynamoDB Enhanced Client handles serialization
    private Map<String, String> metadata;
    
    private Long viewCount;
    private Long likeCount;
    private Long shareCount;
    
    // Store user IDs who liked this video instead of full objects
    private List<String> likedByIds;
    
    // Base entity fields
    private Instant createdAt;
    private Instant updatedAt;
    private Long version;
    private Boolean isDeleted;
    private Instant deletedAt;
    
    public static class DynamoDBVideoBuilder {
        public DynamoDBVideoBuilder() {
            this.isPublic = false;
            this.isDeleted = false;
            this.viewCount = 0L;
            this.likeCount = 0L;
            this.shareCount = 0L;
        }
    }
    
    @DynamoDbPartitionKey
    public String getId() {
        return id;
    }
    
    @DynamoDbSecondaryPartitionKey(indexNames = "user-index")
    @DynamoDbAttribute("user_id")
    public String getUserId() {
        return userId;
    }
    
    @DynamoDbSecondaryPartitionKey(indexNames = "travelPlan-index")
    @DynamoDbAttribute("travel_plan_id")
    public String getTravelPlanId() {
        return travelPlanId;
    }
    
    @DynamoDbSecondaryPartitionKey(indexNames = "status-index")
    public String getStatus() {
        return status;
    }
    
    @DynamoDbSecondaryPartitionKey(indexNames = "public-videos-index")
    @DynamoDbAttribute("is_public")
    public Boolean getIsPublic() {
        return isPublic;
    }
    
    @DynamoDbAttribute("video_url")
    public String getVideoUrl() {
        return videoUrl;
    }
    
    @DynamoDbAttribute("thumbnail_url")
    public String getThumbnailUrl() {
        return thumbnailUrl;
    }
    
    @DynamoDbAttribute("duration_seconds")
    public Integer getDurationSeconds() {
        return durationSeconds;
    }
    
    @DynamoDbAttribute("file_size")
    public Long getFileSize() {
        return fileSize;
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
}