package com.oddiya.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Response DTO for presigned URL generation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PresignedUrlResponse {
    
    /**
     * Storage key/path for the file
     */
    private String key;
    
    /**
     * Presigned URL
     */
    private String url;
    
    /**
     * HTTP method to use with the URL (GET, PUT, POST)
     */
    private String method;
    
    /**
     * URL expiration timestamp
     */
    private Instant expiresAt;
    
    /**
     * Expected content type (for upload URLs)
     */
    private String contentType;
    
    /**
     * Maximum file size allowed (for upload URLs)
     */
    private Long maxFileSize;
    
    /**
     * Additional headers required for the request
     */
    private Map<String, String> requiredHeaders;
    
    /**
     * Form fields for multipart upload (if applicable)
     */
    private Map<String, String> formFields;
    
    /**
     * Upload ID for multipart upload (if applicable)
     */
    private String uploadId;
    
    /**
     * Instructions for using the presigned URL
     */
    private String instructions;
    
    /**
     * Success status
     */
    private boolean success;
    
    /**
     * Error message if generation failed
     */
    private String errorMessage;
}