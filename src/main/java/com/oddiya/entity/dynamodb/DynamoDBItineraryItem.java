package com.oddiya.entity.dynamodb;

import lombok.*;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

import java.time.Instant;

@DynamoDbBean
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DynamoDBItineraryItem {
    
    private String id;
    private String travelPlanId;
    private String placeId;
    private Integer dayNumber;
    private Integer sequence;
    private String startTime; // ISO format: YYYY-MM-DDTHH:mm:ss
    private String endTime;
    private String title;
    private String description;
    private String placeName;
    private String address;
    private Double latitude;
    private Double longitude;
    private String estimatedCost; // BigDecimal as String
    private String actualCost; // BigDecimal as String
    private Integer durationMinutes;
    private String transportMode;
    private Integer transportDurationMinutes;
    private String notes;
    private Boolean isCompleted;
    
    // Base entity fields
    private Instant createdAt;
    private Instant updatedAt;
    private Long version;
    private Boolean isDeleted;
    private Instant deletedAt;
    
    public static class DynamoDBItineraryItemBuilder {
        public DynamoDBItineraryItemBuilder() {
            this.isCompleted = false;
            this.isDeleted = false;
        }
    }
    
    @DynamoDbPartitionKey
    public String getId() {
        return id;
    }
    
    @DynamoDbSecondaryPartitionKey(indexNames = "travelPlan-index")
    @DynamoDbAttribute("travel_plan_id")
    public String getTravelPlanId() {
        return travelPlanId;
    }
    
    @DynamoDbSecondaryPartitionKey(indexNames = "place-index")
    @DynamoDbAttribute("place_id")
    public String getPlaceId() {
        return placeId;
    }
    
    @DynamoDbAttribute("day_number")
    public Integer getDayNumber() {
        return dayNumber;
    }
    
    @DynamoDbAttribute("start_time")
    public String getStartTime() {
        return startTime;
    }
    
    @DynamoDbAttribute("end_time")
    public String getEndTime() {
        return endTime;
    }
    
    @DynamoDbAttribute("place_name")
    public String getPlaceName() {
        return placeName;
    }
    
    @DynamoDbAttribute("estimated_cost")
    public String getEstimatedCost() {
        return estimatedCost;
    }
    
    @DynamoDbAttribute("actual_cost")
    public String getActualCost() {
        return actualCost;
    }
    
    @DynamoDbAttribute("duration_minutes")
    public Integer getDurationMinutes() {
        return durationMinutes;
    }
    
    @DynamoDbAttribute("transport_mode")
    public String getTransportMode() {
        return transportMode;
    }
    
    @DynamoDbAttribute("transport_duration_minutes")
    public Integer getTransportDurationMinutes() {
        return transportDurationMinutes;
    }
    
    @DynamoDbAttribute("is_completed")
    public Boolean getIsCompleted() {
        return isCompleted;
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