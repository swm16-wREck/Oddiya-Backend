package com.oddiya.service.storage;

import com.oddiya.dto.response.FileUploadResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * Local filesystem implementation of StorageService for development.
 * Stores files in a configurable local directory.
 */
@Service
@ConditionalOnProperty(name = "app.aws.s3.enabled", havingValue = "false", matchIfMissing = true)
@Slf4j
public class LocalStorageService implements StorageService {

    @Value("${app.storage.local.base-path:./uploads}")
    private String basePath;

    @Value("${server.port:8080}")
    private String serverPort;

    @Value("${app.storage.local.create-directories:true}")
    private boolean createDirectories;

    private Path baseDirectory;

    @PostConstruct
    public void initialize() {
        try {
            baseDirectory = Paths.get(basePath).toAbsolutePath().normalize();
            
            if (createDirectories && !Files.exists(baseDirectory)) {
                Files.createDirectories(baseDirectory);
                log.info("Created local storage directory: {}", baseDirectory);
            }
            
            if (!Files.isDirectory(baseDirectory)) {
                throw new RuntimeException("Base path is not a directory: " + baseDirectory);
            }
            
            log.info("Local storage service initialized: base directory = {}", baseDirectory);
            
        } catch (Exception e) {
            log.error("Failed to initialize local storage service: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize local storage service", e);
        }
    }

    @Override
    public FileUploadResponse uploadFile(MultipartFile file, String key, Map<String, String> metadata) throws StorageException {
        validateMultipartFile(file);
        
        try {
            Path filePath = resolveFilePath(key);
            ensureDirectoryExists(filePath.getParent());
            
            // Copy file to local filesystem
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING);
            }
            
            // Save metadata as a separate JSON file
            saveMetadata(key, file, metadata);
            
            log.info("Successfully uploaded file to local storage: path={}, size={}", filePath, file.getSize());
            
            return FileUploadResponse.builder()
                    .key(key)
                    .bucket("local")
                    .size(file.getSize())
                    .contentType(file.getContentType())
                    .etag(generateEtag(filePath))
                    .url(generateLocalUrl(key))
                    .metadata(metadata)
                    .uploadTimestamp(Instant.now())
                    .success(true)
                    .build();
                    
        } catch (IOException e) {
            log.error("Failed to upload file to local storage: key={}, error={}", key, e.getMessage(), e);
            throw new StorageException("Failed to upload file to local storage", e, "UPLOAD_FAILED", key);
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
            Path filePath = resolveFilePath(key);
            ensureDirectoryExists(filePath.getParent());
            
            // Copy stream to local filesystem
            Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING);
            
            // Save metadata
            Map<String, String> enrichedMetadata = new HashMap<>(metadata != null ? metadata : new HashMap<>());
            enrichedMetadata.put("content-type", contentType);
            enrichedMetadata.put("content-length", String.valueOf(contentLength));
            enrichedMetadata.put("upload-timestamp", Instant.now().toString());
            saveMetadataToFile(key, enrichedMetadata);
            
            log.info("Successfully uploaded file stream to local storage: path={}, size={}", filePath, contentLength);
            
            return FileUploadResponse.builder()
                    .key(key)
                    .bucket("local")
                    .size(contentLength)
                    .contentType(contentType)
                    .etag(generateEtag(filePath))
                    .url(generateLocalUrl(key))
                    .metadata(enrichedMetadata)
                    .uploadTimestamp(Instant.now())
                    .success(true)
                    .build();
                    
        } catch (IOException e) {
            log.error("Failed to upload file stream to local storage: key={}, error={}", key, e.getMessage(), e);
            throw new StorageException("Failed to upload file stream to local storage", e, "UPLOAD_FAILED", key);
        }
    }

    @Override
    public InputStream downloadFile(String key) throws StorageException {
        try {
            Path filePath = resolveFilePath(key);
            
            if (!Files.exists(filePath)) {
                throw new StorageException("File not found", "FILE_NOT_FOUND", key);
            }
            
            return Files.newInputStream(filePath);
            
        } catch (IOException e) {
            log.error("Failed to download file from local storage: key={}, error={}", key, e.getMessage(), e);
            throw new StorageException("Failed to download file from local storage", e, "DOWNLOAD_FAILED", key);
        }
    }

    @Override
    public boolean deleteFile(String key) throws StorageException {
        try {
            Path filePath = resolveFilePath(key);
            Path metadataPath = getMetadataPath(key);
            
            boolean fileDeleted = Files.deleteIfExists(filePath);
            Files.deleteIfExists(metadataPath); // Delete metadata file if exists
            
            if (fileDeleted) {
                log.info("Successfully deleted file from local storage: path={}", filePath);
            } else {
                log.debug("File did not exist for deletion: path={}", filePath);
            }
            
            return fileDeleted;
            
        } catch (IOException e) {
            log.error("Failed to delete file from local storage: key={}, error={}", key, e.getMessage(), e);
            throw new StorageException("Failed to delete file from local storage", e, "DELETE_FAILED", key);
        }
    }

    @Override
    public Map<String, Boolean> deleteFiles(List<String> keys) {
        Map<String, Boolean> results = new HashMap<>();
        
        for (String key : keys) {
            try {
                boolean deleted = deleteFile(key);
                results.put(key, deleted);
            } catch (StorageException e) {
                log.error("Failed to delete file in batch operation: key={}, error={}", key, e.getMessage());
                results.put(key, false);
            }
        }
        
        return results;
    }

    @Override
    public String generatePresignedUploadUrl(String key, String contentType, int expirationSeconds) throws StorageException {
        // For local storage, return a mock URL that simulates presigned upload
        log.debug("Generating mock presigned upload URL for local storage: key={}", key);
        return String.format("http://localhost:%s/api/files/local-upload?key=%s&contentType=%s&expires=%d", 
                            serverPort, key, contentType, System.currentTimeMillis() + (expirationSeconds * 1000));
    }

    @Override
    public String generatePresignedUploadUrl(String key, String contentType) throws StorageException {
        return generatePresignedUploadUrl(key, contentType, 3600); // 1 hour default
    }

    @Override
    public String generatePresignedDownloadUrl(String key, int expirationSeconds) throws StorageException {
        // For local storage, return a mock URL that simulates presigned download
        log.debug("Generating mock presigned download URL for local storage: key={}", key);
        return String.format("http://localhost:%s/api/files/local-download?key=%s&expires=%d", 
                            serverPort, key, System.currentTimeMillis() + (expirationSeconds * 1000));
    }

    @Override
    public String generatePresignedDownloadUrl(String key) throws StorageException {
        return generatePresignedDownloadUrl(key, 3600); // 1 hour default
    }

    @Override
    public boolean fileExists(String key) {
        Path filePath = resolveFilePath(key);
        return Files.exists(filePath) && Files.isRegularFile(filePath);
    }

    @Override
    public long getFileSize(String key) {
        try {
            Path filePath = resolveFilePath(key);
            if (Files.exists(filePath)) {
                return Files.size(filePath);
            }
            return -1;
        } catch (IOException e) {
            log.warn("Error getting file size for key={}: {}", key, e.getMessage());
            return -1;
        }
    }

    @Override
    public Map<String, String> getFileMetadata(String key) {
        try {
            Path metadataPath = getMetadataPath(key);
            if (Files.exists(metadataPath)) {
                return loadMetadataFromFile(metadataPath);
            }
            return new HashMap<>();
        } catch (Exception e) {
            log.warn("Error loading metadata for key={}: {}", key, e.getMessage());
            return new HashMap<>();
        }
    }

    @Override
    public boolean copyFile(String sourceKey, String destinationKey) throws StorageException {
        try {
            Path sourcePath = resolveFilePath(sourceKey);
            Path destinationPath = resolveFilePath(destinationKey);
            
            if (!Files.exists(sourcePath)) {
                throw new StorageException("Source file not found", "SOURCE_NOT_FOUND", sourceKey);
            }
            
            ensureDirectoryExists(destinationPath.getParent());
            Files.copy(sourcePath, destinationPath, StandardCopyOption.REPLACE_EXISTING);
            
            // Copy metadata as well
            Path sourceMetadata = getMetadataPath(sourceKey);
            if (Files.exists(sourceMetadata)) {
                Path destinationMetadata = getMetadataPath(destinationKey);
                Files.copy(sourceMetadata, destinationMetadata, StandardCopyOption.REPLACE_EXISTING);
            }
            
            log.info("Successfully copied file in local storage: from={} to={}", sourceKey, destinationKey);
            return true;
            
        } catch (IOException e) {
            log.error("Failed to copy file in local storage: from={}, to={}, error={}", sourceKey, destinationKey, e.getMessage(), e);
            throw new StorageException("Failed to copy file in local storage", e, "COPY_FAILED", sourceKey);
        }
    }

    @Override
    public boolean moveFile(String sourceKey, String destinationKey) throws StorageException {
        try {
            Path sourcePath = resolveFilePath(sourceKey);
            Path destinationPath = resolveFilePath(destinationKey);
            
            if (!Files.exists(sourcePath)) {
                throw new StorageException("Source file not found", "SOURCE_NOT_FOUND", sourceKey);
            }
            
            ensureDirectoryExists(destinationPath.getParent());
            Files.move(sourcePath, destinationPath, StandardCopyOption.REPLACE_EXISTING);
            
            // Move metadata as well
            Path sourceMetadata = getMetadataPath(sourceKey);
            if (Files.exists(sourceMetadata)) {
                Path destinationMetadata = getMetadataPath(destinationKey);
                Files.move(sourceMetadata, destinationMetadata, StandardCopyOption.REPLACE_EXISTING);
            }
            
            log.info("Successfully moved file in local storage: from={} to={}", sourceKey, destinationKey);
            return true;
            
        } catch (IOException e) {
            log.error("Failed to move file in local storage: from={}, to={}, error={}", sourceKey, destinationKey, e.getMessage(), e);
            throw new StorageException("Failed to move file in local storage", e, "MOVE_FAILED", sourceKey);
        }
    }

    @Override
    public List<String> listFiles(String prefix, int maxResults) {
        List<String> fileKeys = new ArrayList<>();
        
        try {
            Path searchPath = prefix.isEmpty() ? baseDirectory : baseDirectory.resolve(prefix);
            
            if (!Files.exists(searchPath)) {
                return fileKeys;
            }
            
            try (Stream<Path> paths = Files.walk(searchPath)) {
                paths.filter(Files::isRegularFile)
                     .filter(path -> !path.getFileName().toString().endsWith(".metadata"))
                     .limit(maxResults)
                     .forEach(path -> {
                         String key = baseDirectory.relativize(path).toString().replace("\\", "/");
                         fileKeys.add(key);
                     });
            }
            
        } catch (IOException e) {
            log.error("Error listing files with prefix={}: {}", prefix, e.getMessage(), e);
        }
        
        return fileKeys;
    }

    @Override
    public String getPublicUrl(String key) {
        return generateLocalUrl(key);
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
            long totalSize = 0;
            int fileCount = 0;
            
            try (Stream<Path> paths = Files.walk(baseDirectory)) {
                for (Path path : paths.filter(Files::isRegularFile)
                                      .filter(p -> !p.getFileName().toString().endsWith(".metadata"))
                                      .toList()) {
                    totalSize += Files.size(path);
                    fileCount++;
                }
            }
            
            stats.put("baseDirectory", baseDirectory.toString());
            stats.put("fileCount", fileCount);
            stats.put("totalSize", totalSize);
            stats.put("freeSpace", Files.getFileStore(baseDirectory).getUsableSpace());
            
        } catch (IOException e) {
            log.error("Error getting storage stats: {}", e.getMessage(), e);
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

    private Path resolveFilePath(String key) {
        // Sanitize the key to prevent path traversal
        String sanitizedKey = key.replaceAll("\\.\\.", "").replaceAll("//+", "/");
        if (sanitizedKey.startsWith("/")) {
            sanitizedKey = sanitizedKey.substring(1);
        }
        return baseDirectory.resolve(sanitizedKey).normalize();
    }

    private void ensureDirectoryExists(Path directory) throws IOException {
        if (directory != null && !Files.exists(directory)) {
            Files.createDirectories(directory);
        }
    }

    private String generateLocalUrl(String key) {
        return String.format("http://localhost:%s/api/files/download/%s", serverPort, key);
    }

    private String generateEtag(Path filePath) {
        try {
            BasicFileAttributes attrs = Files.readAttributes(filePath, BasicFileAttributes.class);
            return String.format("\"%d-%d\"", attrs.size(), attrs.lastModifiedTime().toMillis());
        } catch (IOException e) {
            return "\"unknown\"";
        }
    }

    private void saveMetadata(String key, MultipartFile file, Map<String, String> metadata) {
        Map<String, String> allMetadata = new HashMap<>(metadata != null ? metadata : new HashMap<>());
        allMetadata.put("original-filename", file.getOriginalFilename());
        allMetadata.put("content-type", file.getContentType());
        allMetadata.put("file-size", String.valueOf(file.getSize()));
        allMetadata.put("upload-timestamp", Instant.now().toString());
        
        saveMetadataToFile(key, allMetadata);
    }

    private void saveMetadataToFile(String key, Map<String, String> metadata) {
        try {
            Path metadataPath = getMetadataPath(key);
            ensureDirectoryExists(metadataPath.getParent());
            
            Properties props = new Properties();
            props.putAll(metadata);
            
            try (OutputStream out = Files.newOutputStream(metadataPath)) {
                props.store(out, "File metadata for key: " + key);
            }
            
        } catch (IOException e) {
            log.warn("Failed to save metadata for key={}: {}", key, e.getMessage());
        }
    }

    private Map<String, String> loadMetadataFromFile(Path metadataPath) throws IOException {
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(metadataPath)) {
            props.load(in);
        }
        
        Map<String, String> metadata = new HashMap<>();
        for (String key : props.stringPropertyNames()) {
            metadata.put(key, props.getProperty(key));
        }
        return metadata;
    }

    private Path getMetadataPath(String key) {
        return resolveFilePath(key + ".metadata");
    }

    private String sanitizeFilename(String filename) {
        if (filename == null) return "unnamed_file";
        
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_")
                      .replaceAll("_{2,}", "_")
                      .trim();
    }
}