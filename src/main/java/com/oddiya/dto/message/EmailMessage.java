package com.oddiya.dto.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.List;

/**
 * Message DTO for email notifications
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailMessage {

    @NotBlank(message = "Message ID is required")
    @JsonProperty("messageId")
    private String messageId;

    @NotBlank(message = "Email template type is required")
    @JsonProperty("templateType")
    private String templateType;

    @NotBlank(message = "Recipient email is required")
    @Email(message = "Invalid email format")
    @JsonProperty("recipientEmail")
    private String recipientEmail;

    @JsonProperty("recipientName")
    private String recipientName;

    @NotBlank(message = "Subject is required")
    @JsonProperty("subject")
    private String subject;

    @JsonProperty("htmlContent")
    private String htmlContent;

    @JsonProperty("textContent")
    private String textContent;

    @JsonProperty("templateVariables")
    private Map<String, Object> templateVariables;

    @JsonProperty("attachments")
    private List<EmailAttachment> attachments;

    @NotNull(message = "Priority is required")
    @JsonProperty("priority")
    private EmailPriority priority;

    @JsonProperty("tags")
    private List<String> tags;

    @JsonProperty("metadata")
    private Map<String, String> metadata;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @JsonProperty("scheduledAt")
    private LocalDateTime scheduledAt;

    @Builder.Default
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @JsonProperty("createdAt")
    private LocalDateTime createdAt = LocalDateTime.now();

    @JsonProperty("userId")
    private Long userId;

    @JsonProperty("campaignId")
    private String campaignId;

    @Builder.Default
    @JsonProperty("retryCount")
    private int retryCount = 0;

    @Builder.Default
    @JsonProperty("maxRetries")
    private int maxRetries = 3;

    /**
     * Email attachment data
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmailAttachment {
        @NotBlank
        private String fileName;
        
        @NotBlank
        private String contentType;
        
        private String s3Key;
        
        private String presignedUrl;
        
        private long fileSizeBytes;
    }

    /**
     * Email priority levels
     */
    public enum EmailPriority {
        LOW(1),
        NORMAL(2), 
        HIGH(3),
        URGENT(4);

        private final int level;

        EmailPriority(int level) {
            this.level = level;
        }

        public int getLevel() {
            return level;
        }
    }

    /**
     * Common email template types
     */
    public static class TemplateTypes {
        public static final String WELCOME = "welcome";
        public static final String EMAIL_VERIFICATION = "email_verification";
        public static final String PASSWORD_RESET = "password_reset";
        public static final String TRAVEL_PLAN_CREATED = "travel_plan_created";
        public static final String TRAVEL_PLAN_SHARED = "travel_plan_shared";
        public static final String RECOMMENDATION_DIGEST = "recommendation_digest";
        public static final String ACCOUNT_NOTIFICATION = "account_notification";
        public static final String WEEKLY_DIGEST = "weekly_digest";
        public static final String MARKETING_NEWSLETTER = "marketing_newsletter";
        public static final String SYSTEM_ALERT = "system_alert";
    }
}