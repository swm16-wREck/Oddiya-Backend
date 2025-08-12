package com.oddiya.dto.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Message DTO for AI recommendation updates
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationMessage {

    @NotBlank(message = "Message ID is required")
    @JsonProperty("messageId")
    private String messageId;

    @NotBlank(message = "Recommendation ID is required")
    @JsonProperty("recommendationId")
    private String recommendationId;

    @JsonProperty("userId")
    private String userId;

    @JsonProperty("travelPlanId")
    private String travelPlanId;

    @NotNull(message = "Recommendation type is required")
    @JsonProperty("recommendationType")
    private RecommendationType recommendationType;

    @JsonProperty("requestContext")
    private RecommendationContext requestContext;

    @JsonProperty("userPreferences")
    private UserPreferences userPreferences;

    @JsonProperty("locationConstraints")
    private LocationConstraints locationConstraints;

    @JsonProperty("timeConstraints")
    private TimeConstraints timeConstraints;

    @JsonProperty("budgetConstraints")
    private BudgetConstraints budgetConstraints;

    @JsonProperty("modelParameters")
    private ModelParameters modelParameters;

    @JsonProperty("triggerEvent")
    private TriggerEvent triggerEvent;

    @Builder.Default
    @JsonProperty("requiresRealTimeData")
    private boolean requiresRealTimeData = false;

    @JsonProperty("callbackUrl")
    private String callbackUrl;

    @JsonProperty("webhookUrl")
    private String webhookUrl;

    @JsonProperty("tags")
    private List<String> tags;

    @JsonProperty("metadata")
    private Map<String, Object> metadata;

    @Builder.Default
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @JsonProperty("createdAt")
    private LocalDateTime createdAt = LocalDateTime.now();

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @JsonProperty("requestedAt")
    private LocalDateTime requestedAt;

    @Builder.Default
    @JsonProperty("priority")
    private RecommendationPriority priority = RecommendationPriority.NORMAL;

    @Builder.Default
    @JsonProperty("retryCount")
    private int retryCount = 0;

    @Builder.Default
    @JsonProperty("maxRetries")
    private int maxRetries = 3;

    /**
     * Types of recommendations
     */
    public enum RecommendationType {
        DESTINATION_RECOMMENDATION,
        ACCOMMODATION_RECOMMENDATION,
        ACTIVITY_RECOMMENDATION,
        RESTAURANT_RECOMMENDATION,
        TRANSPORTATION_RECOMMENDATION,
        ITINERARY_OPTIMIZATION,
        PERSONALIZED_SUGGESTIONS,
        SIMILAR_TRAVELERS,
        SEASONAL_RECOMMENDATIONS,
        BUDGET_OPTIMIZATION,
        REAL_TIME_ALERTS,
        COLLABORATIVE_FILTERING,
        CONTENT_BASED_FILTERING,
        HYBRID_RECOMMENDATION
    }

    /**
     * Recommendation priority levels
     */
    public enum RecommendationPriority {
        LOW(1),
        NORMAL(2),
        HIGH(3),
        URGENT(4);

        private final int level;

        RecommendationPriority(int level) {
            this.level = level;
        }

        public int getLevel() {
            return level;
        }
    }

    /**
     * Context for the recommendation request
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecommendationContext {
        private String currentLocation;
        private String sessionId;
        private String deviceType;
        private List<String> recentSearches;
        private List<String> recentViews;
        private List<String> recentBookings;
        private String referrerSource;
        private Map<String, Object> behaviorData;
    }

    /**
     * User preferences for recommendations
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserPreferences {
        private List<String> preferredCategories;
        private List<String> preferredActivityTypes;
        private List<String> preferredCuisines;
        private String accommodationPreference;
        private String transportationPreference;
        private List<String> interests;
        private List<String> avoidedCategories;
        private String travelStyle; // adventure, luxury, budget, cultural, etc.
        private String groupSize;
        private List<String> languages;
        private Map<String, Object> customPreferences;
    }

    /**
     * Location-based constraints
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LocationConstraints {
        private String centerPoint; // lat,lon
        private Double radiusKm;
        private List<String> includeRegions;
        private List<String> excludeRegions;
        private List<String> preferredLocations;
        private String countryCode;
        private String cityCode;
        private Boolean includeNearbyDestinations;
    }

    /**
     * Time-based constraints
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimeConstraints {
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDateTime startDate;
        
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDateTime endDate;
        
        private Integer durationDays;
        private String season;
        private List<String> preferredDaysOfWeek;
        private String timeOfDay; // morning, afternoon, evening, night
        private Boolean includeWeekends;
        private Boolean includeHolidays;
    }

    /**
     * Budget-based constraints
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BudgetConstraints {
        private Double minBudget;
        private Double maxBudget;
        private String currency;
        private String budgetLevel; // budget, mid-range, luxury
        private Map<String, Double> categoryBudgets; // accommodation, food, activities, transport
        private Boolean strictBudget;
    }

    /**
     * AI model parameters
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModelParameters {
        private String modelVersion;
        private String algorithm; // collaborative, content-based, hybrid
        private Double confidenceThreshold;
        private Integer maxResults;
        private Boolean includeSimilarUsers;
        private Boolean includePopularItems;
        private Double diversityFactor;
        private Double noveltyFactor;
        private Map<String, Object> customParameters;
    }

    /**
     * Event that triggered the recommendation request
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TriggerEvent {
        private String eventType;
        private String eventId;
        private LocalDateTime eventTime;
        private String sourceSystem;
        private Map<String, Object> eventData;
    }

    /**
     * Common trigger event types
     */
    public static class TriggerEvents {
        public static final String USER_LOGIN = "user_login";
        public static final String SEARCH_PERFORMED = "search_performed";
        public static final String TRAVEL_PLAN_CREATED = "travel_plan_created";
        public static final String ITEM_VIEWED = "item_viewed";
        public static final String ITEM_LIKED = "item_liked";
        public static final String BOOKING_COMPLETED = "booking_completed";
        public static final String PROFILE_UPDATED = "profile_updated";
        public static final String LOCATION_CHANGED = "location_changed";
        public static final String SCHEDULED_UPDATE = "scheduled_update";
        public static final String REAL_TIME_EVENT = "real_time_event";
        public static final String BATCH_PROCESSING = "batch_processing";
    }
}