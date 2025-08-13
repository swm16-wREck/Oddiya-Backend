package com.oddiya.controller;

import com.oddiya.dto.response.FileMetadataResponse;
import com.oddiya.dto.response.FileUploadResponse;
import com.oddiya.dto.response.PresignedUrlResponse;
import com.oddiya.dto.response.ApiResponse;
import com.oddiya.service.storage.FileValidationException;
import com.oddiya.service.storage.StorageException;
import com.oddiya.service.storage.StorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.io.InputStream;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * REST controller for file upload and management operations.
 * Supports direct uploads, presigned URL generation, and file metadata operations.
 */
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
@Validated
@Slf4j
@Tag(name = "File Upload", description = "File upload and management API")
public class FileUploadController {

    private final StorageService storageService;

    @Value("${spring.servlet.multipart.max-file-size:10MB}")
    private String maxFileSize;

    @Value("${app.file-upload.allowed-image-types:image/jpeg,image/jpg,image/png,image/gif,image/webp}")
    private List<String> allowedImageTypes;

    @Value("${app.file-upload.allowed-video-types:video/mp4,video/avi,video/mov,video/wmv,video/flv,video/webm}")
    private List<String> allowedVideoTypes;

    @Value("${app.file-upload.allowed-document-types:application/pdf,text/plain}")
    private List<String> allowedDocumentTypes;

    @Value("${app.file-upload.max-image-size:10485760}") // 10MB
    private long maxImageSize;

    @Value("${app.file-upload.max-video-size:104857600}") // 100MB
    private long maxVideoSize;

    @Value("${app.file-upload.max-document-size:10485760}") // 10MB
    private long maxDocumentSize;

    // File upload endpoints

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a file", description = "Upload a file to storage")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "File uploaded successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid file or request"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "413", description = "File too large"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Upload failed")
    })
    @PreAuthorize("hasAuthority('USER')")
    public ResponseEntity<ApiResponse<FileUploadResponse>> uploadFile(
            @Parameter(description = "File to upload", required = true)
            @RequestParam("file") MultipartFile file,
            
            @Parameter(description = "File category (image, video, document)", example = "image")
            @RequestParam(value = "category", defaultValue = "document") String category,
            
            @Parameter(description = "Custom storage prefix", example = "user-uploads/profile")
            @RequestParam(value = "prefix", required = false) String prefix,
            
            @Parameter(description = "Make file publicly accessible", example = "false")
            @RequestParam(value = "public", defaultValue = "false") boolean publicAccess,
            
            @Parameter(description = "Additional metadata as JSON", example = "{\"description\":\"Profile picture\"}")
            @RequestParam(value = "metadata", required = false) Map<String, String> metadata,

            HttpServletRequest request) {

        try {
            // Validate file based on category
            validateFileByCategory(file, category);
            
            // Generate unique storage key
            String key = storageService.generateFileKey(file.getOriginalFilename(), 
                                                       prefix != null ? prefix : category);
            
            // Add request metadata
            Map<String, String> enrichedMetadata = enrichMetadata(metadata, request, category, publicAccess);
            
            // Upload file
            FileUploadResponse response = storageService.uploadFile(file, key, enrichedMetadata);
            
            log.info("File uploaded successfully: key={}, size={}, user={}", 
                    key, file.getSize(), getCurrentUserId(request));
            
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (FileValidationException e) {
            log.warn("File validation failed: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("FILE_VALIDATION_FAILED", e.getMessage()));
                    
        } catch (StorageException e) {
            log.error("File upload failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(e.getErrorCode(), e.getMessage()));
                    
        } catch (Exception e) {
            log.error("Unexpected error during file upload: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("UPLOAD_ERROR", "File upload failed"));
        }
    }

    @PostMapping("/upload/async")
    @Operation(summary = "Upload a file asynchronously", description = "Upload a file asynchronously and get immediate response")
    @PreAuthorize("hasAuthority('USER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> uploadFileAsync(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "category", defaultValue = "document") String category,
            @RequestParam(value = "prefix", required = false) String prefix,
            @RequestParam(value = "public", defaultValue = "false") boolean publicAccess,
            @RequestParam(value = "metadata", required = false) Map<String, String> metadata,
            HttpServletRequest request) {

        try {
            validateFileByCategory(file, category);
            
            String key = storageService.generateFileKey(file.getOriginalFilename(), 
                                                       prefix != null ? prefix : category);
            
            Map<String, String> enrichedMetadata = enrichMetadata(metadata, request, category, publicAccess);
            
            // Start async upload
            CompletableFuture<FileUploadResponse> future = storageService.uploadFileAsync(file, key, enrichedMetadata);
            
            Map<String, Object> response = Map.of(
                "key", key,
                "status", "UPLOADING",
                "message", "File upload started",
                "checkUrl", "/api/files/" + key + "/status"
            );
            
            log.info("Async file upload started: key={}, size={}, user={}", 
                    key, file.getSize(), getCurrentUserId(request));
            
            return ResponseEntity.accepted().body(ApiResponse.success(response));
            
        } catch (FileValidationException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("FILE_VALIDATION_FAILED", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to start async upload: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("ASYNC_UPLOAD_ERROR", "Failed to start async upload"));
        }
    }

    // Presigned URL endpoints

    @PostMapping("/presigned-upload-url")
    @Operation(summary = "Generate presigned upload URL", description = "Generate a presigned URL for direct browser upload")
    @PreAuthorize("hasAuthority('USER')")
    public ResponseEntity<ApiResponse<PresignedUrlResponse>> generatePresignedUploadUrl(
            @Parameter(description = "Filename", required = true, example = "profile.jpg")
            @RequestParam @NotBlank String filename,
            
            @Parameter(description = "Content type", required = true, example = "image/jpeg")
            @RequestParam @NotBlank String contentType,
            
            @Parameter(description = "File category", example = "image")
            @RequestParam(value = "category", defaultValue = "document") String category,
            
            @Parameter(description = "Custom storage prefix", example = "user-uploads/profile")
            @RequestParam(value = "prefix", required = false) String prefix,
            
            @Parameter(description = "URL expiration in seconds", example = "3600")
            @RequestParam(value = "expirationSeconds", defaultValue = "3600") 
            @Min(300) @Max(86400) int expirationSeconds,

            HttpServletRequest request) {

        try {
            // Validate content type for category
            validateContentTypeForCategory(contentType, category);
            
            String key = storageService.generateFileKey(filename, prefix != null ? prefix : category);
            String url = storageService.generatePresignedUploadUrl(key, contentType, expirationSeconds);
            
            PresignedUrlResponse response = PresignedUrlResponse.builder()
                    .key(key)
                    .url(url)
                    .method("PUT")
                    .expiresAt(Instant.now().plusSeconds(expirationSeconds))
                    .contentType(contentType)
                    .maxFileSize(getMaxFileSizeForCategory(category))
                    .success(true)
                    .instructions("Use PUT method to upload file to this URL")
                    .build();
            
            log.info("Generated presigned upload URL: key={}, user={}", key, getCurrentUserId(request));
            
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (FileValidationException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("INVALID_CONTENT_TYPE", e.getMessage()));
                    
        } catch (StorageException e) {
            log.error("Failed to generate presigned upload URL: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(e.getErrorCode(), e.getMessage()));
                    
        } catch (Exception e) {
            log.error("Unexpected error generating presigned URL: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("PRESIGN_ERROR", "Failed to generate presigned URL"));
        }
    }

    @GetMapping("/presigned-download-url/{key:.*}")
    @Operation(summary = "Generate presigned download URL", description = "Generate a presigned URL for file download")
    @PreAuthorize("hasAuthority('USER')")
    public ResponseEntity<ApiResponse<PresignedUrlResponse>> generatePresignedDownloadUrl(
            @Parameter(description = "File storage key", required = true)
            @PathVariable String key,
            
            @Parameter(description = "URL expiration in seconds", example = "3600")
            @RequestParam(value = "expirationSeconds", defaultValue = "3600")
            @Min(300) @Max(86400) int expirationSeconds,

            HttpServletRequest request) {

        try {
            if (!storageService.fileExists(key)) {
                return ResponseEntity.notFound().build();
            }
            
            String url = storageService.generatePresignedDownloadUrl(key, expirationSeconds);
            
            PresignedUrlResponse response = PresignedUrlResponse.builder()
                    .key(key)
                    .url(url)
                    .method("GET")
                    .expiresAt(Instant.now().plusSeconds(expirationSeconds))
                    .success(true)
                    .instructions("Use GET method to download file from this URL")
                    .build();
            
            log.info("Generated presigned download URL: key={}, user={}", key, getCurrentUserId(request));
            
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (StorageException e) {
            log.error("Failed to generate presigned download URL: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(e.getErrorCode(), e.getMessage()));
                    
        } catch (Exception e) {
            log.error("Unexpected error generating presigned download URL: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("PRESIGN_ERROR", "Failed to generate presigned download URL"));
        }
    }

    // File management endpoints

    @GetMapping("/download/{key:.*}")
    @Operation(summary = "Download a file", description = "Download a file from storage")
    public ResponseEntity<Resource> downloadFile(
            @Parameter(description = "File storage key", required = true)
            @PathVariable String key,
            
            @Parameter(description = "Download as attachment", example = "false")
            @RequestParam(value = "attachment", defaultValue = "false") boolean attachment,

            HttpServletRequest request) {

        try {
            if (!storageService.fileExists(key)) {
                return ResponseEntity.notFound().build();
            }
            
            InputStream inputStream = storageService.downloadFile(key);
            Map<String, String> metadata = storageService.getFileMetadata(key);
            
            String contentType = metadata.getOrDefault("content-type", "application/octet-stream");
            String originalFilename = metadata.getOrDefault("original-filename", "download");
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(contentType));
            
            if (attachment) {
                headers.setContentDispositionFormData("attachment", originalFilename);
            } else {
                headers.setContentDispositionFormData("inline", originalFilename);
            }
            
            // Set cache headers for static content
            headers.setCacheControl("max-age=3600");
            
            InputStreamResource resource = new InputStreamResource(inputStream);
            
            log.info("File downloaded: key={}, user={}", key, getCurrentUserId(request));
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(resource);
                    
        } catch (StorageException e) {
            log.error("Failed to download file: key={}, error={}", key, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            
        } catch (Exception e) {
            log.error("Unexpected error downloading file: key={}, error={}", key, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/metadata/{key:.*}")
    @Operation(summary = "Get file metadata", description = "Get metadata information for a file")
    @PreAuthorize("hasAuthority('USER')")
    public ResponseEntity<ApiResponse<FileMetadataResponse>> getFileMetadata(
            @Parameter(description = "File storage key", required = true)
            @PathVariable String key) {

        try {
            if (!storageService.fileExists(key)) {
                return ResponseEntity.notFound().build();
            }
            
            Map<String, String> metadata = storageService.getFileMetadata(key);
            long fileSize = storageService.getFileSize(key);
            
            FileMetadataResponse response = FileMetadataResponse.builder()
                    .key(key)
                    .bucket(metadata.getOrDefault("bucket", "unknown"))
                    .size(fileSize)
                    .contentType(metadata.getOrDefault("content-type", "application/octet-stream"))
                    .etag(metadata.getOrDefault("etag", ""))
                    .metadata(metadata)
                    .exists(true)
                    .originalFilename(metadata.getOrDefault("original-filename", ""))
                    .extension(FilenameUtils.getExtension(metadata.getOrDefault("original-filename", "")))
                    .virusScanStatus(metadata.getOrDefault("virus-scan-status", "unknown"))
                    .build();
            
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (Exception e) {
            log.error("Failed to get file metadata: key={}, error={}", key, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("METADATA_ERROR", "Failed to retrieve file metadata"));
        }
    }

    @DeleteMapping("/{key:.*}")
    @Operation(summary = "Delete a file", description = "Delete a file from storage")
    @PreAuthorize("hasAuthority('USER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> deleteFile(
            @Parameter(description = "File storage key", required = true)
            @PathVariable String key,

            HttpServletRequest request) {

        try {
            boolean deleted = storageService.deleteFile(key);
            
            Map<String, Object> response = Map.of(
                "key", key,
                "deleted", deleted,
                "message", deleted ? "File deleted successfully" : "File not found"
            );
            
            log.info("File deletion attempt: key={}, deleted={}, user={}", 
                    key, deleted, getCurrentUserId(request));
            
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (StorageException e) {
            log.error("Failed to delete file: key={}, error={}", key, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(e.getErrorCode(), e.getMessage()));
                    
        } catch (Exception e) {
            log.error("Unexpected error deleting file: key={}, error={}", key, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("DELETE_ERROR", "Failed to delete file"));
        }
    }

    @GetMapping("/list")
    @Operation(summary = "List files", description = "List files with optional prefix filter")
    @PreAuthorize("hasAuthority('USER')")
    public ResponseEntity<ApiResponse<List<String>>> listFiles(
            @Parameter(description = "Prefix to filter files", example = "user-uploads/")
            @RequestParam(value = "prefix", defaultValue = "") String prefix,
            
            @Parameter(description = "Maximum number of results", example = "100")
            @RequestParam(value = "maxResults", defaultValue = "100")
            @Min(1) @Max(1000) int maxResults) {

        try {
            List<String> files = storageService.listFiles(prefix, maxResults);
            return ResponseEntity.ok(ApiResponse.success(files));
            
        } catch (Exception e) {
            log.error("Failed to list files: prefix={}, error={}", prefix, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("LIST_ERROR", "Failed to list files"));
        }
    }

    @GetMapping("/storage-stats")
    @Operation(summary = "Get storage statistics", description = "Get storage usage statistics")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStorageStats() {
        try {
            Map<String, Object> stats = storageService.getStorageStats();
            return ResponseEntity.ok(ApiResponse.success(stats));
            
        } catch (Exception e) {
            log.error("Failed to get storage stats: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("STATS_ERROR", "Failed to retrieve storage statistics"));
        }
    }

    // Helper methods

    private void validateFileByCategory(MultipartFile file, String category) throws FileValidationException {
        switch (category.toLowerCase()) {
            case "image" -> storageService.validateFile(file, allowedImageTypes, maxImageSize);
            case "video" -> storageService.validateFile(file, allowedVideoTypes, maxVideoSize);
            case "document" -> storageService.validateFile(file, allowedDocumentTypes, maxDocumentSize);
            default -> storageService.validateFile(file, null, maxDocumentSize);
        }
    }

    private void validateContentTypeForCategory(String contentType, String category) throws FileValidationException {
        List<String> allowedTypes = switch (category.toLowerCase()) {
            case "image" -> allowedImageTypes;
            case "video" -> allowedVideoTypes;
            case "document" -> allowedDocumentTypes;
            default -> List.of();
        };
        
        if (!allowedTypes.isEmpty() && !allowedTypes.contains(contentType)) {
            throw new FileValidationException(
                String.format("Content type %s not allowed for category %s", contentType, category),
                "INVALID_CONTENT_TYPE", contentType, allowedTypes);
        }
    }

    private long getMaxFileSizeForCategory(String category) {
        return switch (category.toLowerCase()) {
            case "image" -> maxImageSize;
            case "video" -> maxVideoSize;
            case "document" -> maxDocumentSize;
            default -> maxDocumentSize;
        };
    }

    private Map<String, String> enrichMetadata(Map<String, String> metadata, HttpServletRequest request, 
                                               String category, boolean publicAccess) {
        Map<String, String> enriched = new HashMap<>(metadata != null ? metadata : new HashMap<>());
        
        enriched.put("category", category);
        enriched.put("public-access", String.valueOf(publicAccess));
        enriched.put("uploader-ip", getClientIpAddress(request));
        enriched.put("user-agent", request.getHeader("User-Agent"));
        enriched.put("upload-source", "direct");
        enriched.put("uploader-id", getCurrentUserId(request));
        
        return enriched;
    }

    private String getCurrentUserId(HttpServletRequest request) {
        // Extract user ID from JWT token or session
        // This is a simplified implementation
        return "user-123"; // Replace with actual user ID extraction
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
}