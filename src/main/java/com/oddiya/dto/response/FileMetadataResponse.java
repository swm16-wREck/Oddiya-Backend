package com.oddiya.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Response DTO for file metadata operations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileMetadataResponse {
    
    /**
     * Storage key/path of the file
     */
    private String key;
    
    /**
     * Storage bucket name
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
     * File metadata
     */
    private Map<String, String> metadata;
    
    /**
     * Last modified timestamp
     */
    private Instant lastModified;
    
    /**
     * Creation timestamp
     */
    private Instant created;
    
    /**
     * Whether the file exists
     */
    private boolean exists;
    
    /**
     * Original filename
     */
    private String originalFilename;
    
    /**
     * File extension
     */
    private String extension;
    
    /**
     * Storage class (STANDARD, STANDARD_IA, GLACIER, etc.)
     */
    private String storageClass;
    
    /**
     * Server-side encryption status
     */
    private String encryption;
    
    /**
     * Virus scan status
     */
    private String virusScanStatus;
}