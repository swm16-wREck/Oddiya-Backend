package com.oddiya.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Response DTO for file upload operations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadResponse {
    
    /**
     * Storage key/path of the uploaded file
     */
    private String key;
    
    /**
     * Storage bucket name (for S3) or "local" for local storage
     */
    private String bucket;
    
    /**
     * File size in bytes
     */
    private long size;
    
    /**
     * MIME type of the file
     */
    private String contentType;
    
    /**
     * ETag or checksum of the file
     */
    private String etag;
    
    /**
     * Public URL to access the file
     */
    private String url;
    
    /**
     * Presigned download URL (if different from public URL)
     */
    private String downloadUrl;
    
    /**
     * File metadata
     */
    private Map<String, String> metadata;
    
    /**
     * Upload timestamp
     */
    private Instant uploadTimestamp;
    
    /**
     * Upload success status
     */
    private boolean success;
    
    /**
     * Error message if upload failed
     */
    private String errorMessage;
    
    /**
     * Error code if upload failed
     */
    private String errorCode;
    
    /**
     * Upload progress (0-100) for async uploads
     */
    private Integer progress;
    
    /**
     * Virus scan status
     */
    private String virusScanStatus;
    
    /**
     * Original filename
     */
    private String originalFilename;
    
    /**
     * File extension
     */
    private String extension;
    
    /**
     * Whether the file is publicly accessible
     */
    private boolean publicAccess;
    
    /**
     * CloudFront distribution URL (if applicable)
     */
    private String cdnUrl;
    
    /**
     * Storage class (STANDARD, STANDARD_IA, GLACIER, etc.)
     */
    private String storageClass;
    
    /**
     * Server-side encryption status
     */
    private String encryption;
}