package com.oddiya.entity.dynamodb;

import lombok.*;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

import java.time.Instant;

@DynamoDbBean
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DynamoDBSavedPlan {
    
    private String userId;
    private String travelPlanId;
    private String id; // Optional: UUID for cases where you need a unique record ID
    private String travelPlanIdReverse; // Same as travelPlanId, for reverse GSI
    private String userIdReverse; // Same as userId, for reverse GSI
    
    // Additional metadata about the save action
    private Instant savedAt;
    private String notes; // Optional: user notes about why they saved this plan
    private Boolean isFavorite; // Optional: mark as favorite
    private Boolean notificationEnabled; // Optional: notify on plan updates
    
    // Base entity fields
    private Instant createdAt;
    private Instant updatedAt;
    private Long version;
    private Boolean isDeleted;
    private Instant deletedAt;
    
    public static class DynamoDBSavedPlanBuilder {
        public DynamoDBSavedPlanBuilder() {
            this.isFavorite = false;
            this.notificationEnabled = true;
            this.isDeleted = false;
            this.savedAt = Instant.now();
        }
    }
    
    @DynamoDbPartitionKey
    public String getUserId() {
        return userId;
    }
    
    @DynamoDbSortKey
    @DynamoDbAttribute("travel_plan_id")
    public String getTravelPlanId() {
        return travelPlanId;
    }
    
    @DynamoDbSecondaryPartitionKey(indexNames = "savedPlan-index")
    public String getId() {
        return id;
    }
    
    @DynamoDbSecondaryPartitionKey(indexNames = "travelPlan-users-index")
    public String getTravelPlanIdReverse() {
        return travelPlanIdReverse;
    }
    
    @DynamoDbSecondarySortKey(indexNames = "travelPlan-users-index")
    public String getUserIdReverse() {
        return userIdReverse;
    }
    
    @DynamoDbAttribute("saved_at")
    public Instant getSavedAt() {
        return savedAt;
    }
    
    @DynamoDbAttribute("is_favorite")
    public Boolean getIsFavorite() {
        return isFavorite;
    }
    
    @DynamoDbAttribute("notification_enabled")
    public Boolean getNotificationEnabled() {
        return notificationEnabled;
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
    
    // Helper method to automatically populate reverse lookup fields
    public void populateReverseLookupFields() {
        this.travelPlanIdReverse = this.travelPlanId;
        this.userIdReverse = this.userId;
    }
    
    // Helper method to create composite key for unique constraint emulation
    public String getCompositeKey() {
        return userId + "#" + travelPlanId;
    }
}