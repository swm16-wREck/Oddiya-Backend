package com.oddiya.service.storage;

import com.oddiya.dto.response.FileUploadResponse;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Storage service interface for file operations.
 * Supports different storage implementations (S3, local filesystem).
 */
public interface StorageService {

    /**
     * Upload a file to storage.
     *
     * @param file the multipart file to upload
     * @param key the storage key/path for the file
     * @param metadata additional metadata for the file
     * @return FileUploadResponse containing upload details
     * @throws StorageException if upload fails
     */
    FileUploadResponse uploadFile(MultipartFile file, String key, Map<String, String> metadata) throws StorageException;

    /**
     * Upload a file asynchronously.
     *
     * @param file the multipart file to upload
     * @param key the storage key/path for the file
     * @param metadata additional metadata for the file
     * @return CompletableFuture with FileUploadResponse
     */
    CompletableFuture<FileUploadResponse> uploadFileAsync(MultipartFile file, String key, Map<String, String> metadata);

    /**
     * Upload a file from input stream.
     *
     * @param inputStream the input stream containing file data
     * @param key the storage key/path for the file
     * @param contentType the MIME type of the file
     * @param contentLength the size of the file in bytes
     * @param metadata additional metadata for the file
     * @return FileUploadResponse containing upload details
     * @throws StorageException if upload fails
     */
    FileUploadResponse uploadFile(InputStream inputStream, String key, String contentType, long contentLength, Map<String, String> metadata) throws StorageException;

    /**
     * Download a file from storage.
     *
     * @param key the storage key/path of the file
     * @return InputStream containing the file data
     * @throws StorageException if download fails or file not found
     */
    InputStream downloadFile(String key) throws StorageException;

    /**
     * Delete a file from storage.
     *
     * @param key the storage key/path of the file to delete
     * @return true if file was deleted successfully, false if file didn't exist
     * @throws StorageException if deletion fails
     */
    boolean deleteFile(String key) throws StorageException;

    /**
     * Delete multiple files from storage.
     *
     * @param keys list of storage keys/paths to delete
     * @return map of key to deletion success status
     */
    Map<String, Boolean> deleteFiles(List<String> keys);

    /**
     * Generate a presigned URL for direct browser upload.
     *
     * @param key the storage key/path for the file
     * @param contentType the expected MIME type of the file
     * @param expirationSeconds how long the URL should be valid (in seconds)
     * @return presigned upload URL
     * @throws StorageException if URL generation fails
     */
    String generatePresignedUploadUrl(String key, String contentType, int expirationSeconds) throws StorageException;

    /**
     * Generate a presigned URL for direct browser upload with default expiration.
     *
     * @param key the storage key/path for the file
     * @param contentType the expected MIME type of the file
     * @return presigned upload URL
     * @throws StorageException if URL generation fails
     */
    String generatePresignedUploadUrl(String key, String contentType) throws StorageException;

    /**
     * Generate a presigned URL for file download.
     *
     * @param key the storage key/path of the file
     * @param expirationSeconds how long the URL should be valid (in seconds)
     * @return presigned download URL
     * @throws StorageException if URL generation fails
     */
    String generatePresignedDownloadUrl(String key, int expirationSeconds) throws StorageException;

    /**
     * Generate a presigned URL for file download with default expiration.
     *
     * @param key the storage key/path of the file
     * @return presigned download URL
     * @throws StorageException if URL generation fails
     */
    String generatePresignedDownloadUrl(String key) throws StorageException;

    /**
     * Check if a file exists in storage.
     *
     * @param key the storage key/path of the file
     * @return true if file exists, false otherwise
     */
    boolean fileExists(String key);

    /**
     * Get file size in bytes.
     *
     * @param key the storage key/path of the file
     * @return file size in bytes, or -1 if file doesn't exist
     */
    long getFileSize(String key);

    /**
     * Get file metadata.
     *
     * @param key the storage key/path of the file
     * @return map containing file metadata, or empty map if file doesn't exist
     */
    Map<String, String> getFileMetadata(String key);

    /**
     * Copy a file within storage.
     *
     * @param sourceKey source storage key/path
     * @param destinationKey destination storage key/path
     * @return true if copy was successful
     * @throws StorageException if copy fails
     */
    boolean copyFile(String sourceKey, String destinationKey) throws StorageException;

    /**
     * Move a file within storage (copy and delete source).
     *
     * @param sourceKey source storage key/path
     * @param destinationKey destination storage key/path
     * @return true if move was successful
     * @throws StorageException if move fails
     */
    boolean moveFile(String sourceKey, String destinationKey) throws StorageException;

    /**
     * List files with a given prefix.
     *
     * @param prefix the prefix to search for
     * @param maxResults maximum number of results to return
     * @return list of file keys matching the prefix
     */
    List<String> listFiles(String prefix, int maxResults);

    /**
     * Get the public URL for a file (if storage supports public access).
     *
     * @param key the storage key/path of the file
     * @return public URL, or null if not supported/available
     */
    String getPublicUrl(String key);

    /**
     * Validate file type and size before upload.
     *
     * @param file the file to validate
     * @param allowedTypes list of allowed MIME types (null means all allowed)
     * @param maxSizeBytes maximum allowed file size in bytes (0 means no limit)
     * @throws FileValidationException if validation fails
     */
    void validateFile(MultipartFile file, List<String> allowedTypes, long maxSizeBytes) throws FileValidationException;

    /**
     * Generate a unique storage key for a file.
     *
     * @param originalFilename the original filename
     * @param prefix optional prefix for the key
     * @return unique storage key
     */
    String generateFileKey(String originalFilename, String prefix);

    /**
     * Get storage statistics.
     *
     * @return map containing storage statistics
     */
    Map<String, Object> getStorageStats();
}