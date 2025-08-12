package com.oddiya.service.storage;

import com.oddiya.dto.response.FileUploadResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import java.util.concurrent.Executors;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import software.amazon.awssdk.transfer.s3.model.*;
import software.amazon.awssdk.services.s3.S3AsyncClient;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * S3-based implementation of StorageService.
 * Uses AWS S3 for file storage with Transfer Manager for optimized uploads.
 */
@Service
@ConditionalOnProperty(name = "app.aws.s3.enabled", havingValue = "true")
@Slf4j
public class S3StorageService implements StorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private S3TransferManager transferManager;
    private S3AsyncClient s3AsyncClient;

    @Value("${app.aws.s3.bucket}")
    private String bucketName;

    @Value("${app.aws.s3.region}")
    private String region;

    @Value("${app.aws.s3.presigned-url-expiration:3600}")
    private int defaultExpirationSeconds;

    @Value("${app.aws.s3.multipart-threshold:16777216}")
    private long multipartThreshold; // 16MB

    @Value("${spring.servlet.multipart.max-file-size:10MB}")
    private String maxFileSize;

    // Supported file types for different categories
    private static final Map<String, List<String>> ALLOWED_TYPES = Map.of(
        "image", List.of("image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp"),
        "video", List.of("video/mp4", "video/avi", "video/mov", "video/wmv", "video/flv", "video/webm"),
        "document", List.of("application/pdf", "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                           "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                           "text/plain", "text/csv")
    );

    // Virus scanning integration point
    private static final String VIRUS_SCAN_METADATA_KEY = "virus-scan-status";
    private static final String VIRUS_SCAN_PENDING = "pending";
    private static final String VIRUS_SCAN_CLEAN = "clean";
    private static final String VIRUS_SCAN_INFECTED = "infected";

    public S3StorageService(S3Client s3Client, S3Presigner s3Presigner) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
    }

    @PostConstruct
    public void initializeTransferManager() {
        try {
            this.s3AsyncClient = S3AsyncClient.builder()
                    .region(software.amazon.awssdk.regions.Region.of(region))
                    .build();
            
            this.transferManager = S3TransferManager.builder()
                    .s3Client(s3AsyncClient)
                    .build();
            log.info("S3 Transfer Manager initialized for bucket: {}", bucketName);
        } catch (Exception e) {
            log.error("Failed to initialize S3 Transfer Manager: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize S3 Transfer Manager", e);
        }
    }

    @PreDestroy
    public void cleanup() {
        if (transferManager != null) {
            transferManager.close();
            log.info("S3 Transfer Manager closed");
        }
        if (s3AsyncClient != null) {
            s3AsyncClient.close();
            log.info("S3 Async Client closed");
        }
    }

    @Override
    public FileUploadResponse uploadFile(MultipartFile file, String key, Map<String, String> metadata) throws StorageException {
        validateMultipartFile(file);
        
        try {
            Map<String, String> enrichedMetadata = enrichMetadata(file, metadata);
            
            // Use Transfer Manager for large files or regular S3 client for small files
            if (file.getSize() > multipartThreshold) {
                return uploadLargeFile(file, key, enrichedMetadata);
            } else {
                return uploadRegularFile(file, key, enrichedMetadata);
            }
        } catch (Exception e) {
            log.error("Failed to upload file to S3: key={}, error={}", key, e.getMessage(), e);
            throw new StorageException("Failed to upload file to S3", e, "UPLOAD_FAILED", key);
        }
    }

    @Override
    public CompletableFuture<FileUploadResponse> uploadFileAsync(MultipartFile file, String key, Map<String, String> metadata) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return uploadFile(file, key, metadata);
            } catch (StorageException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public FileUploadResponse uploadFile(InputStream inputStream, String key, String contentType, long contentLength, Map<String, String> metadata) throws StorageException {
        try {
            Map<String, String> enrichedMetadata = new HashMap<>(metadata != null ? metadata : new HashMap<>());
            enrichedMetadata.put("content-type", contentType);
            enrichedMetadata.put("content-length", String.valueOf(contentLength));
            enrichedMetadata.put("upload-timestamp", Instant.now().toString());
            enrichedMetadata.put(VIRUS_SCAN_METADATA_KEY, VIRUS_SCAN_PENDING);

            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(contentType)
                    .contentLength(contentLength)
                    .metadata(enrichedMetadata)
                    .serverSideEncryption(ServerSideEncryption.AES256)
                    .build();

            PutObjectResponse response = s3Client.putObject(request, RequestBody.fromInputStream(inputStream, contentLength));

            log.info("Successfully uploaded file to S3: bucket={}, key={}, size={}", bucketName, key, contentLength);

            return FileUploadResponse.builder()
                    .key(key)
                    .bucket(bucketName)
                    .size(contentLength)
                    .contentType(contentType)
                    .etag(response.eTag())
                    .url(generatePublicUrl(key))
                    .metadata(enrichedMetadata)
                    .uploadTimestamp(Instant.now())
                    .success(true)
                    .build();

        } catch (Exception e) {
            log.error("Failed to upload file stream to S3: key={}, error={}", key, e.getMessage(), e);
            throw new StorageException("Failed to upload file stream to S3", e, "UPLOAD_FAILED", key);
        }
    }

    private FileUploadResponse uploadRegularFile(MultipartFile file, String key, Map<String, String> metadata) throws StorageException {
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(file.getContentType())
                    .contentLength(file.getSize())
                    .metadata(metadata)
                    .serverSideEncryption(ServerSideEncryption.AES256)
                    .build();

            PutObjectResponse response = s3Client.putObject(request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

            log.info("Successfully uploaded regular file to S3: bucket={}, key={}, size={}", bucketName, key, file.getSize());

            return buildFileUploadResponse(file, key, response.eTag(), metadata);

        } catch (IOException e) {
            throw new StorageException("Failed to read file input stream", e, "IO_ERROR", key);
        }
    }

    private FileUploadResponse uploadLargeFile(MultipartFile file, String key, Map<String, String> metadata) throws StorageException {
        try {
            UploadRequest uploadRequest = UploadRequest.builder()
                    .putObjectRequest(PutObjectRequest.builder()
                            .bucket(bucketName)
                            .key(key)
                            .contentType(file.getContentType())
                            .metadata(metadata)
                            .serverSideEncryption(ServerSideEncryption.AES256)
                            .build())
                    .requestBody(AsyncRequestBody.fromInputStream(file.getInputStream(), file.getSize(), Executors.newSingleThreadExecutor()))
                    .build();

            Upload upload = transferManager.upload(uploadRequest);
            CompletedUpload completedUpload = upload.completionFuture().join();

            log.info("Successfully uploaded large file to S3 using Transfer Manager: bucket={}, key={}, size={}", bucketName, key, file.getSize());

            return buildFileUploadResponse(file, key, completedUpload.response().eTag(), metadata);

        } catch (IOException e) {
            throw new StorageException("Failed to read file input stream for large file upload", e, "IO_ERROR", key);
        } catch (Exception e) {
            throw new StorageException("Failed to upload large file using Transfer Manager", e, "TRANSFER_FAILED", key);
        }
    }

    private FileUploadResponse buildFileUploadResponse(MultipartFile file, String key, String etag, Map<String, String> metadata) {
        return FileUploadResponse.builder()
                .key(key)
                .bucket(bucketName)
                .size(file.getSize())
                .contentType(file.getContentType())
                .etag(etag)
                .url(generatePublicUrl(key))
                .metadata(metadata)
                .uploadTimestamp(Instant.now())
                .success(true)
                .build();
    }

    @Override
    public InputStream downloadFile(String key) throws StorageException {
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            return s3Client.getObject(request);
        } catch (NoSuchKeyException e) {
            throw new StorageException("File not found", e, "FILE_NOT_FOUND", key);
        } catch (Exception e) {
            log.error("Failed to download file from S3: key={}, error={}", key, e.getMessage(), e);
            throw new StorageException("Failed to download file from S3", e, "DOWNLOAD_FAILED", key);
        }
    }

    @Override
    public boolean deleteFile(String key) throws StorageException {
        try {
            DeleteObjectRequest request = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            s3Client.deleteObject(request);
            log.info("Successfully deleted file from S3: bucket={}, key={}", bucketName, key);
            return true;
        } catch (Exception e) {
            log.error("Failed to delete file from S3: key={}, error={}", key, e.getMessage(), e);
            throw new StorageException("Failed to delete file from S3", e, "DELETE_FAILED", key);
        }
    }

    @Override
    public Map<String, Boolean> deleteFiles(List<String> keys) {
        Map<String, Boolean> results = new HashMap<>();
        
        if (keys.isEmpty()) {
            return results;
        }

        try {
            List<ObjectIdentifier> objectIdentifiers = keys.stream()
                    .map(key -> ObjectIdentifier.builder().key(key).build())
                    .collect(Collectors.toList());

            DeleteObjectsRequest request = DeleteObjectsRequest.builder()
                    .bucket(bucketName)
                    .delete(Delete.builder().objects(objectIdentifiers).build())
                    .build();

            DeleteObjectsResponse response = s3Client.deleteObjects(request);
            
            // Mark successful deletions
            response.deleted().forEach(deleted -> results.put(deleted.key(), true));
            
            // Mark failed deletions
            response.errors().forEach(error -> {
                results.put(error.key(), false);
                log.error("Failed to delete file: key={}, error={}", error.key(), error.message());
            });
            
            // Mark any remaining keys as failed if not in response
            keys.forEach(key -> results.putIfAbsent(key, false));

            log.info("Batch delete completed: successful={}, failed={}", 
                    response.deleted().size(), response.errors().size());

        } catch (Exception e) {
            log.error("Failed to perform batch delete: error={}", e.getMessage(), e);
            // Mark all as failed
            keys.forEach(key -> results.put(key, false));
        }

        return results;
    }

    @Override
    public String generatePresignedUploadUrl(String key, String contentType, int expirationSeconds) throws StorageException {
        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(contentType)
                    .serverSideEncryption(ServerSideEncryption.AES256)
                    .build();

            PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofSeconds(expirationSeconds))
                    .putObjectRequest(putObjectRequest)
                    .build();

            String url = s3Presigner.presignPutObject(presignRequest).url().toString();
            log.debug("Generated presigned upload URL: key={}, expiration={}s", key, expirationSeconds);
            return url;
        } catch (Exception e) {
            log.error("Failed to generate presigned upload URL: key={}, error={}", key, e.getMessage(), e);
            throw new StorageException("Failed to generate presigned upload URL", e, "PRESIGN_FAILED", key);
        }
    }

    @Override
    public String generatePresignedUploadUrl(String key, String contentType) throws StorageException {
        return generatePresignedUploadUrl(key, contentType, defaultExpirationSeconds);
    }

    @Override
    public String generatePresignedDownloadUrl(String key, int expirationSeconds) throws StorageException {
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofSeconds(expirationSeconds))
                    .getObjectRequest(getObjectRequest)
                    .build();

            String url = s3Presigner.presignGetObject(presignRequest).url().toString();
            log.debug("Generated presigned download URL: key={}, expiration={}s", key, expirationSeconds);
            return url;
        } catch (Exception e) {
            log.error("Failed to generate presigned download URL: key={}, error={}", key, e.getMessage(), e);
            throw new StorageException("Failed to generate presigned download URL", e, "PRESIGN_FAILED", key);
        }
    }

    @Override
    public String generatePresignedDownloadUrl(String key) throws StorageException {
        return generatePresignedDownloadUrl(key, defaultExpirationSeconds);
    }

    @Override
    public boolean fileExists(String key) {
        try {
            HeadObjectRequest request = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            s3Client.headObject(request);
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (Exception e) {
            log.warn("Error checking if file exists: key={}, error={}", key, e.getMessage());
            return false;
        }
    }

    @Override
    public long getFileSize(String key) {
        try {
            HeadObjectRequest request = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            HeadObjectResponse response = s3Client.headObject(request);
            return response.contentLength();
        } catch (Exception e) {
            log.warn("Error getting file size: key={}, error={}", key, e.getMessage());
            return -1;
        }
    }

    @Override
    public Map<String, String> getFileMetadata(String key) {
        try {
            HeadObjectRequest request = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            HeadObjectResponse response = s3Client.headObject(request);
            return response.metadata();
        } catch (Exception e) {
            log.warn("Error getting file metadata: key={}, error={}", key, e.getMessage());
            return new HashMap<>();
        }
    }

    @Override
    public boolean copyFile(String sourceKey, String destinationKey) throws StorageException {
        try {
            CopyObjectRequest request = CopyObjectRequest.builder()
                    .sourceBucket(bucketName)
                    .sourceKey(sourceKey)
                    .destinationBucket(bucketName)
                    .destinationKey(destinationKey)
                    .build();

            s3Client.copyObject(request);
            log.info("Successfully copied file in S3: from={} to={}", sourceKey, destinationKey);
            return true;
        } catch (Exception e) {
            log.error("Failed to copy file in S3: from={}, to={}, error={}", sourceKey, destinationKey, e.getMessage(), e);
            throw new StorageException("Failed to copy file in S3", e, "COPY_FAILED", sourceKey);
        }
    }

    @Override
    public boolean moveFile(String sourceKey, String destinationKey) throws StorageException {
        try {
            // Copy file first
            copyFile(sourceKey, destinationKey);
            
            // Delete source file
            deleteFile(sourceKey);
            
            log.info("Successfully moved file in S3: from={} to={}", sourceKey, destinationKey);
            return true;
        } catch (Exception e) {
            log.error("Failed to move file in S3: from={}, to={}, error={}", sourceKey, destinationKey, e.getMessage(), e);
            throw new StorageException("Failed to move file in S3", e, "MOVE_FAILED", sourceKey);
        }
    }

    @Override
    public List<String> listFiles(String prefix, int maxResults) {
        try {
            ListObjectsV2Request request = ListObjectsV2Request.builder()
                    .bucket(bucketName)
                    .prefix(prefix)
                    .maxKeys(maxResults)
                    .build();

            ListObjectsV2Response response = s3Client.listObjectsV2(request);
            return response.contents().stream()
                    .map(S3Object::key)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to list files in S3: prefix={}, error={}", prefix, e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    @Override
    public String getPublicUrl(String key) {
        // Return CloudFront URL if configured, otherwise direct S3 URL
        return generatePublicUrl(key);
    }

    @Override
    public void validateFile(MultipartFile file, List<String> allowedTypes, long maxSizeBytes) throws FileValidationException {
        if (file == null || file.isEmpty()) {
            throw new FileValidationException("File is empty or null", "EMPTY_FILE");
        }

        // Validate file size
        if (maxSizeBytes > 0 && file.getSize() > maxSizeBytes) {
            throw new FileValidationException(
                String.format("File size %d bytes exceeds maximum allowed size %d bytes", file.getSize(), maxSizeBytes),
                "FILE_TOO_LARGE", file.getSize(), maxSizeBytes);
        }

        // Validate content type
        if (allowedTypes != null && !allowedTypes.isEmpty() && !allowedTypes.contains(file.getContentType())) {
            throw new FileValidationException(
                String.format("File type %s is not allowed", file.getContentType()),
                "INVALID_FILE_TYPE", file.getContentType(), allowedTypes);
        }

        // Validate filename
        String filename = file.getOriginalFilename();
        if (filename == null || filename.trim().isEmpty()) {
            throw new FileValidationException("Filename is empty", "EMPTY_FILENAME");
        }

        // Check for potentially dangerous file extensions
        String extension = FilenameUtils.getExtension(filename).toLowerCase();
        List<String> dangerousExtensions = Arrays.asList("exe", "bat", "cmd", "scr", "pif", "com", "jar", "js", "vbs", "ps1");
        if (dangerousExtensions.contains(extension)) {
            throw new FileValidationException(
                String.format("File extension .%s is not allowed for security reasons", extension),
                "DANGEROUS_FILE_TYPE", extension, "Safe file types only");
        }
    }

    @Override
    public String generateFileKey(String originalFilename, String prefix) {
        String sanitizedFilename = sanitizeFilename(originalFilename);
        String timestamp = String.valueOf(System.currentTimeMillis());
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        
        String key = String.format("%s_%s_%s", timestamp, uuid, sanitizedFilename);
        
        if (prefix != null && !prefix.trim().isEmpty()) {
            key = prefix.trim() + "/" + key;
        }
        
        return key;
    }

    @Override
    public Map<String, Object> getStorageStats() {
        Map<String, Object> stats = new HashMap<>();
        try {
            // Get bucket size and object count (this is a simplified version)
            ListObjectsV2Request request = ListObjectsV2Request.builder()
                    .bucket(bucketName)
                    .maxKeys(1000)
                    .build();

            ListObjectsV2Response response = s3Client.listObjectsV2(request);
            
            long totalSize = response.contents().stream()
                    .mapToLong(S3Object::size)
                    .sum();
            
            stats.put("bucket", bucketName);
            stats.put("objectCount", response.contents().size());
            stats.put("totalSize", totalSize);
            stats.put("isTruncated", response.isTruncated());
            
        } catch (Exception e) {
            log.error("Failed to get storage stats: error={}", e.getMessage(), e);
            stats.put("error", e.getMessage());
        }
        
        return stats;
    }

    // Helper methods

    private void validateMultipartFile(MultipartFile file) throws StorageException {
        if (file == null || file.isEmpty()) {
            throw new StorageException("File is empty or null", "EMPTY_FILE");
        }
        
        if (file.getOriginalFilename() == null || file.getOriginalFilename().trim().isEmpty()) {
            throw new StorageException("Filename is empty", "EMPTY_FILENAME");
        }
    }

    private Map<String, String> enrichMetadata(MultipartFile file, Map<String, String> metadata) {
        Map<String, String> enriched = new HashMap<>(metadata != null ? metadata : new HashMap<>());
        
        enriched.put("original-filename", file.getOriginalFilename());
        enriched.put("upload-timestamp", Instant.now().toString());
        enriched.put("file-size", String.valueOf(file.getSize()));
        enriched.put("content-type", file.getContentType());
        enriched.put(VIRUS_SCAN_METADATA_KEY, VIRUS_SCAN_PENDING);
        
        return enriched;
    }

    private String generatePublicUrl(String key) {
        // In a real implementation, this should check for CloudFront configuration
        return String.format("https://%s.s3.%s.amazonaws.com/%s", bucketName, region, key);
    }

    private String sanitizeFilename(String filename) {
        if (filename == null) return "unnamed_file";
        
        // Remove path separators and other problematic characters
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_")
                      .replaceAll("_{2,}", "_")
                      .trim();
    }

    // Virus scanning integration point
    public void markFileAsScanned(String key, boolean isClean) {
        try {
            Map<String, String> metadata = getFileMetadata(key);
            metadata.put(VIRUS_SCAN_METADATA_KEY, isClean ? VIRUS_SCAN_CLEAN : VIRUS_SCAN_INFECTED);
            
            // Update metadata (this would require copying the object in S3)
            // Implementation depends on your virus scanning workflow
            log.info("File virus scan completed: key={}, clean={}", key, isClean);
            
            if (!isClean) {
                // Optionally quarantine or delete infected files
                log.warn("Infected file detected: key={}", key);
            }
            
        } catch (Exception e) {
            log.error("Failed to update virus scan metadata: key={}, error={}", key, e.getMessage(), e);
        }
    }
}