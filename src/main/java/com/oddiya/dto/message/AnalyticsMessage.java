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
import java.util.Map;
import java.util.List;

/**
 * Message DTO for user analytics events
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsMessage {

    @NotBlank(message = "Message ID is required")
    @JsonProperty("messageId")
    private String messageId;

    @NotBlank(message = "Event ID is required")
    @JsonProperty("eventId")
    private String eventId;

    @JsonProperty("userId")
    private String userId;

    @JsonProperty("sessionId")
    private String sessionId;

    @NotBlank(message = "Event type is required")
    @JsonProperty("eventType")
    private String eventType;

    @NotBlank(message = "Event name is required")
    @JsonProperty("eventName")
    private String eventName;

    @JsonProperty("category")
    private EventCategory category;

    @JsonProperty("properties")
    private Map<String, Object> properties;

    @JsonProperty("userProperties")
    private Map<String, Object> userProperties;

    @JsonProperty("deviceInfo")
    private DeviceInfo deviceInfo;

    @JsonProperty("locationInfo")
    private LocationInfo locationInfo;

    @JsonProperty("pageInfo")
    private PageInfo pageInfo;

    @JsonProperty("referrerInfo")
    private ReferrerInfo referrerInfo;

    @JsonProperty("experimentInfo")
    private ExperimentInfo experimentInfo;

    @Builder.Default
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @JsonProperty("timestamp")
    private LocalDateTime timestamp = LocalDateTime.now();

    @Builder.Default
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @JsonProperty("receivedAt")
    private LocalDateTime receivedAt = LocalDateTime.now();

    @JsonProperty("value")
    private Double value; // For revenue tracking, duration, etc.

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("tags")
    private List<String> tags;

    @JsonProperty("customDimensions")
    private Map<String, String> customDimensions;

    @JsonProperty("customMetrics")
    private Map<String, Double> customMetrics;

    @Builder.Default
    @JsonProperty("priority")
    private EventPriority priority = EventPriority.NORMAL;

    @Builder.Default
    @JsonProperty("retryCount")
    private int retryCount = 0;

    @Builder.Default
    @JsonProperty("maxRetries")
    private int maxRetries = 3;

    /**
     * Analytics event categories
     */
    public enum EventCategory {
        USER_INTERACTION,
        PAGE_VIEW,
        TRAVEL_PLANNING,
        SEARCH,
        BOOKING,
        RECOMMENDATION,
        SOCIAL,
        MEDIA_CONSUMPTION,
        ERROR,
        PERFORMANCE,
        SYSTEM,
        CONVERSION,
        ENGAGEMENT,
        RETENTION
    }

    /**
     * Event priority levels
     */
    public enum EventPriority {
        LOW(1),
        NORMAL(2),
        HIGH(3),
        CRITICAL(4);

        private final int level;

        EventPriority(int level) {
            this.level = level;
        }

        public int getLevel() {
            return level;
        }
    }

    /**
     * Device information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeviceInfo {
        private String deviceType; // mobile, desktop, tablet
        private String operatingSystem;
        private String osVersion;
        private String browser;
        private String browserVersion;
        private String userAgent;
        private String ipAddress;
        private String screenResolution;
        private String timezone;
        private String language;
    }

    /**
     * Location information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LocationInfo {
        private String country;
        private String region;
        private String city;
        private Double latitude;
        private Double longitude;
        private String timezone;
        private String isp;
    }

    /**
     * Page information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PageInfo {
        private String url;
        private String path;
        private String title;
        private String referrer;
        private Map<String, String> queryParameters;
        private String hash;
    }

    /**
     * Referrer information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReferrerInfo {
        private String source;
        private String medium;
        private String campaign;
        private String term;
        private String content;
        private String referrerDomain;
    }

    /**
     * A/B testing and experiment information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExperimentInfo {
        private String experimentId;
        private String variantId;
        private String variantName;
        private Map<String, String> experimentProperties;
    }

    /**
     * Common analytics event types
     */
    public static class EventTypes {
        // User events
        public static final String USER_SIGNUP = "user_signup";
        public static final String USER_LOGIN = "user_login";
        public static final String USER_LOGOUT = "user_logout";
        public static final String PROFILE_UPDATED = "profile_updated";

        // Travel planning events
        public static final String TRAVEL_PLAN_CREATED = "travel_plan_created";
        public static final String TRAVEL_PLAN_VIEWED = "travel_plan_viewed";
        public static final String TRAVEL_PLAN_SHARED = "travel_plan_shared";
        public static final String TRAVEL_PLAN_DELETED = "travel_plan_deleted";

        // Search events
        public static final String SEARCH_PERFORMED = "search_performed";
        public static final String SEARCH_RESULT_CLICKED = "search_result_clicked";
        public static final String FILTER_APPLIED = "filter_applied";

        // Recommendation events
        public static final String RECOMMENDATION_VIEWED = "recommendation_viewed";
        public static final String RECOMMENDATION_CLICKED = "recommendation_clicked";
        public static final String RECOMMENDATION_LIKED = "recommendation_liked";
        public static final String RECOMMENDATION_SAVED = "recommendation_saved";

        // Media events
        public static final String IMAGE_UPLOADED = "image_uploaded";
        public static final String VIDEO_WATCHED = "video_watched";
        public static final String MEDIA_SHARED = "media_shared";

        // Error events
        public static final String ERROR_OCCURRED = "error_occurred";
        public static final String API_ERROR = "api_error";
        public static final String CLIENT_ERROR = "client_error";

        // Performance events
        public static final String PAGE_LOAD_TIME = "page_load_time";
        public static final String API_RESPONSE_TIME = "api_response_time";
    }
}