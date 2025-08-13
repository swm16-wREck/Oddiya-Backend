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
public class DynamoDBUser {
    
    private String id;
    private String email;
    private String username;
    private String password;
    private String nickname;
    private String profileImageUrl;
    private String bio;
    private String provider; // google, apple
    private String providerId;
    
    // Store as JSON string or Map - DynamoDB Enhanced Client handles Map serialization
    private Map<String, String> preferences;
    private Map<String, String> travelPreferences;
    
    // Store lists of IDs instead of full objects for NoSQL design
    private List<String> travelPlanIds;
    private List<String> reviewIds;
    private List<String> videoIds;
    private List<String> followerIds;
    private List<String> followingIds;
    
    private Boolean isEmailVerified;
    private Boolean isPremium;
    private Boolean isActive;
    private String refreshToken;
    
    // Base entity fields
    private Instant createdAt;
    private Instant updatedAt;
    private Long version;
    private Boolean isDeleted;
    private Instant deletedAt;
    
    // Builder for DynamoDB compatibility
    public static class DynamoDBUserBuilder {
        public DynamoDBUserBuilder() {
            this.isEmailVerified = false;
            this.isPremium = false;
            this.isActive = true;
            this.isDeleted = false;
        }
    }
    
    // DynamoDB annotations on getter methods
    @DynamoDbPartitionKey
    public String getId() {
        return id;
    }
    
    @DynamoDbSecondaryPartitionKey(indexNames = "email-index")
    public String getEmail() {
        return email;
    }
    
    @DynamoDbSecondaryPartitionKey(indexNames = "provider-index")
    public String getProvider() {
        return provider;
    }
    
    @DynamoDbSecondarySortKey(indexNames = "provider-index")
    public String getProviderId() {
        return providerId;
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