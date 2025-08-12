package com.oddiya.service.storage;

import com.oddiya.dto.response.FileUploadResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URL;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class S3StorageServiceTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Presigner s3Presigner;

    private S3StorageService storageService;

    private final String testBucketName = "test-bucket";
    private final String testRegion = "us-east-1";

    @BeforeEach
    void setUp() {
        storageService = new S3StorageService(s3Client, s3Presigner);
        // Use reflection to set private fields for testing
        setPrivateField(storageService, "bucketName", testBucketName);
        setPrivateField(storageService, "region", testRegion);
        setPrivateField(storageService, "defaultExpirationSeconds", 3600);
        setPrivateField(storageService, "multipartThreshold", 16777216L);
    }

    @Test
    void uploadFile_SmallFile_ShouldUploadSuccessfully() throws Exception {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
            "file", "test.txt", "text/plain", "Hello World".getBytes()
        );
        String key = "test/test.txt";
        
        PutObjectResponse mockResponse = PutObjectResponse.builder()
            .eTag("test-etag")
            .build();
        
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
            .thenReturn(mockResponse);

        // Act
        FileUploadResponse response = storageService.uploadFile(file, key, new HashMap<>());

        // Assert
        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertEquals(key, response.getKey());
        assertEquals(testBucketName, response.getBucket());
        assertEquals(file.getSize(), response.getSize());
        assertEquals("test-etag", response.getEtag());
        assertEquals("text/plain", response.getContentType());
        assertNotNull(response.getUrl());
        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void uploadFile_EmptyFile_ShouldThrowException() {
        // Arrange
        MockMultipartFile emptyFile = new MockMultipartFile("file", "", "text/plain", new byte[0]);
        String key = "test/empty.txt";

        // Act & Assert
        assertThrows(StorageException.class, () -> 
            storageService.uploadFile(emptyFile, key, new HashMap<>())
        );
        
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void uploadFile_NullFile_ShouldThrowException() {
        // Arrange
        String key = "test/null.txt";

        // Act & Assert
        assertThrows(StorageException.class, () -> 
            storageService.uploadFile(null, key, new HashMap<>())
        );
    }

    @Test
    void uploadFile_S3Exception_ShouldThrowStorageException() throws Exception {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
            "file", "test.txt", "text/plain", "Hello World".getBytes()
        );
        String key = "test/test.txt";
        
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
            .thenThrow(S3Exception.builder().message("AWS Error").build());

        // Act & Assert
        StorageException exception = assertThrows(StorageException.class, () ->
            storageService.uploadFile(file, key, new HashMap<>())
        );
        
        assertEquals("UPLOAD_FAILED", exception.getErrorCode());
        assertEquals(key, exception.getResourceId());
        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void uploadFile_WithInputStream_ShouldUploadSuccessfully() throws Exception {
        // Arrange
        String key = "test/stream.txt";
        String content = "Test content from stream";
        InputStream inputStream = new ByteArrayInputStream(content.getBytes());
        String contentType = "text/plain";
        long contentLength = content.length();
        Map<String, String> metadata = Map.of("custom", "value");
        
        PutObjectResponse mockResponse = PutObjectResponse.builder()
            .eTag("stream-etag")
            .build();
        
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
            .thenReturn(mockResponse);

        // Act
        FileUploadResponse response = storageService.uploadFile(
            inputStream, key, contentType, contentLength, metadata
        );

        // Assert
        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertEquals(key, response.getKey());
        assertEquals(testBucketName, response.getBucket());
        assertEquals(contentLength, response.getSize());
        assertEquals("stream-etag", response.getEtag());
        assertEquals(contentType, response.getContentType());
        assertTrue(response.getMetadata().containsKey("custom"));
        assertEquals("value", response.getMetadata().get("custom"));
        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void downloadFile_ExistingFile_ShouldReturnInputStream() throws Exception {
        // Arrange
        String key = "test/download.txt";
        String content = "Downloaded content";
        InputStream mockInputStream = new ByteArrayInputStream(content.getBytes());
        
        when(s3Client.getObject(any(GetObjectRequest.class)))
            .thenReturn(mockInputStream);

        // Act
        InputStream result = storageService.downloadFile(key);

        // Assert
        assertNotNull(result);
        assertEquals(mockInputStream, result);
        verify(s3Client).getObject(any(GetObjectRequest.class));
    }

    @Test
    void downloadFile_NonExistentFile_ShouldThrowStorageException() {
        // Arrange
        String key = "test/nonexistent.txt";
        
        when(s3Client.getObject(any(GetObjectRequest.class)))
            .thenThrow(NoSuchKeyException.builder().message("Key not found").build());

        // Act & Assert
        StorageException exception = assertThrows(StorageException.class, () ->
            storageService.downloadFile(key)
        );
        
        assertEquals("FILE_NOT_FOUND", exception.getErrorCode());
        assertEquals(key, exception.getResourceId());
        verify(s3Client).getObject(any(GetObjectRequest.class));
    }

    @Test
    void deleteFile_ExistingFile_ShouldDeleteSuccessfully() throws Exception {
        // Arrange
        String key = "test/delete.txt";
        
        doNothing().when(s3Client).deleteObject(any(DeleteObjectRequest.class));

        // Act
        boolean result = storageService.deleteFile(key);

        // Assert
        assertTrue(result);
        verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void deleteFile_S3Exception_ShouldThrowStorageException() {
        // Arrange
        String key = "test/error.txt";
        
        doThrow(S3Exception.builder().message("Delete failed").build())
            .when(s3Client).deleteObject(any(DeleteObjectRequest.class));

        // Act & Assert
        StorageException exception = assertThrows(StorageException.class, () ->
            storageService.deleteFile(key)
        );
        
        assertEquals("DELETE_FAILED", exception.getErrorCode());
        assertEquals(key, exception.getResourceId());
        verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void deleteFiles_MultipleFiles_ShouldReturnResults() {
        // Arrange
        List<String> keys = Arrays.asList("file1.txt", "file2.txt", "file3.txt");
        
        DeleteObjectsResponse mockResponse = DeleteObjectsResponse.builder()
            .deleted(
                DeletedObject.builder().key("file1.txt").build(),
                DeletedObject.builder().key("file2.txt").build()
            )
            .errors(
                S3Error.builder().key("file3.txt").code("AccessDenied").message("Access denied").build()
            )
            .build();
        
        when(s3Client.deleteObjects(any(DeleteObjectsRequest.class)))
            .thenReturn(mockResponse);

        // Act
        Map<String, Boolean> results = storageService.deleteFiles(keys);

        // Assert
        assertEquals(3, results.size());
        assertTrue(results.get("file1.txt"));
        assertTrue(results.get("file2.txt"));
        assertFalse(results.get("file3.txt"));
        verify(s3Client).deleteObjects(any(DeleteObjectsRequest.class));
    }

    @Test
    void generatePresignedUploadUrl_ValidInput_ShouldReturnUrl() throws Exception {
        // Arrange
        String key = "test/presigned.txt";
        String contentType = "text/plain";
        int expirationSeconds = 1800;
        
        URL mockUrl = new URL("https://test-bucket.s3.amazonaws.com/test/presigned.txt?signature=abc123");
        PresignedPutObjectRequest mockPresignedRequest = mock(PresignedPutObjectRequest.class);
        when(mockPresignedRequest.url()).thenReturn(mockUrl);
        
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
            .thenReturn(mockPresignedRequest);

        // Act
        String result = storageService.generatePresignedUploadUrl(key, contentType, expirationSeconds);

        // Assert
        assertEquals(mockUrl.toString(), result);
        verify(s3Presigner).presignPutObject(any(PutObjectPresignRequest.class));
    }

    @Test
    void generatePresignedDownloadUrl_ValidInput_ShouldReturnUrl() throws Exception {
        // Arrange
        String key = "test/download-presigned.txt";
        int expirationSeconds = 1800;
        
        URL mockUrl = new URL("https://test-bucket.s3.amazonaws.com/test/download-presigned.txt?signature=xyz789");
        PresignedGetObjectRequest mockPresignedRequest = mock(PresignedGetObjectRequest.class);
        when(mockPresignedRequest.url()).thenReturn(mockUrl);
        
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
            .thenReturn(mockPresignedRequest);

        // Act
        String result = storageService.generatePresignedDownloadUrl(key, expirationSeconds);

        // Assert
        assertEquals(mockUrl.toString(), result);
        verify(s3Presigner).presignGetObject(any(GetObjectPresignRequest.class));
    }

    @Test
    void fileExists_ExistingFile_ShouldReturnTrue() {
        // Arrange
        String key = "test/existing.txt";
        
        HeadObjectResponse mockResponse = HeadObjectResponse.builder()
            .contentLength(100L)
            .build();
        
        when(s3Client.headObject(any(HeadObjectRequest.class)))
            .thenReturn(mockResponse);

        // Act
        boolean result = storageService.fileExists(key);

        // Assert
        assertTrue(result);
        verify(s3Client).headObject(any(HeadObjectRequest.class));
    }

    @Test
    void fileExists_NonExistentFile_ShouldReturnFalse() {
        // Arrange
        String key = "test/nonexistent.txt";
        
        when(s3Client.headObject(any(HeadObjectRequest.class)))
            .thenThrow(NoSuchKeyException.builder().message("Not found").build());

        // Act
        boolean result = storageService.fileExists(key);

        // Assert
        assertFalse(result);
        verify(s3Client).headObject(any(HeadObjectRequest.class));
    }

    @Test
    void getFileSize_ExistingFile_ShouldReturnSize() {
        // Arrange
        String key = "test/sized.txt";
        long expectedSize = 1024L;
        
        HeadObjectResponse mockResponse = HeadObjectResponse.builder()
            .contentLength(expectedSize)
            .build();
        
        when(s3Client.headObject(any(HeadObjectRequest.class)))
            .thenReturn(mockResponse);

        // Act
        long result = storageService.getFileSize(key);

        // Assert
        assertEquals(expectedSize, result);
        verify(s3Client).headObject(any(HeadObjectRequest.class));
    }

    @Test
    void getFileSize_NonExistentFile_ShouldReturnMinusOne() {
        // Arrange
        String key = "test/nonexistent.txt";
        
        when(s3Client.headObject(any(HeadObjectRequest.class)))
            .thenThrow(NoSuchKeyException.builder().message("Not found").build());

        // Act
        long result = storageService.getFileSize(key);

        // Assert
        assertEquals(-1L, result);
        verify(s3Client).headObject(any(HeadObjectRequest.class));
    }

    @Test
    void getFileMetadata_ExistingFile_ShouldReturnMetadata() {
        // Arrange
        String key = "test/metadata.txt";
        Map<String, String> expectedMetadata = Map.of(
            "author", "test-user",
            "created", "2024-01-01"
        );
        
        HeadObjectResponse mockResponse = HeadObjectResponse.builder()
            .metadata(expectedMetadata)
            .build();
        
        when(s3Client.headObject(any(HeadObjectRequest.class)))
            .thenReturn(mockResponse);

        // Act
        Map<String, String> result = storageService.getFileMetadata(key);

        // Assert
        assertEquals(expectedMetadata, result);
        verify(s3Client).headObject(any(HeadObjectRequest.class));
    }

    @Test
    void copyFile_ValidFiles_ShouldCopySuccessfully() throws Exception {
        // Arrange
        String sourceKey = "source/file.txt";
        String destKey = "dest/file.txt";
        
        CopyObjectResponse mockResponse = CopyObjectResponse.builder()
            .copyObjectResult(CopyObjectResult.builder()
                .eTag("copy-etag")
                .lastModified(Instant.now())
                .build())
            .build();
        
        when(s3Client.copyObject(any(CopyObjectRequest.class)))
            .thenReturn(mockResponse);

        // Act
        boolean result = storageService.copyFile(sourceKey, destKey);

        // Assert
        assertTrue(result);
        verify(s3Client).copyObject(any(CopyObjectRequest.class));
    }

    @Test
    void listFiles_WithPrefix_ShouldReturnFileList() {
        // Arrange
        String prefix = "photos/";
        int maxResults = 10;
        
        List<S3Object> s3Objects = Arrays.asList(
            S3Object.builder().key("photos/image1.jpg").size(1024L).build(),
            S3Object.builder().key("photos/image2.jpg").size(2048L).build(),
            S3Object.builder().key("photos/image3.jpg").size(1536L).build()
        );
        
        ListObjectsV2Response mockResponse = ListObjectsV2Response.builder()
            .contents(s3Objects)
            .build();
        
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
            .thenReturn(mockResponse);

        // Act
        List<String> result = storageService.listFiles(prefix, maxResults);

        // Assert
        assertEquals(3, result.size());
        assertTrue(result.contains("photos/image1.jpg"));
        assertTrue(result.contains("photos/image2.jpg"));
        assertTrue(result.contains("photos/image3.jpg"));
        verify(s3Client).listObjectsV2(any(ListObjectsV2Request.class));
    }

    @Test
    void validateFile_ValidFile_ShouldPass() throws Exception {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
            "file", "test.jpg", "image/jpeg", "fake image content".getBytes()
        );
        List<String> allowedTypes = Arrays.asList("image/jpeg", "image/png");
        long maxSize = 1024L * 1024L; // 1MB

        // Act & Assert
        assertDoesNotThrow(() -> 
            storageService.validateFile(file, allowedTypes, maxSize)
        );
    }

    @Test
    void validateFile_InvalidContentType_ShouldThrowException() {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
            "file", "test.txt", "text/plain", "text content".getBytes()
        );
        List<String> allowedTypes = Arrays.asList("image/jpeg", "image/png");
        long maxSize = 1024L * 1024L;

        // Act & Assert
        FileValidationException exception = assertThrows(FileValidationException.class, () ->
            storageService.validateFile(file, allowedTypes, maxSize)
        );
        
        assertEquals("INVALID_FILE_TYPE", exception.getErrorCode());
    }

    @Test
    void validateFile_FileTooLarge_ShouldThrowException() {
        // Arrange
        byte[] largeContent = new byte[2048]; // 2KB
        MockMultipartFile file = new MockMultipartFile(
            "file", "large.jpg", "image/jpeg", largeContent
        );
        List<String> allowedTypes = Arrays.asList("image/jpeg", "image/png");
        long maxSize = 1024L; // 1KB limit

        // Act & Assert
        FileValidationException exception = assertThrows(FileValidationException.class, () ->
            storageService.validateFile(file, allowedTypes, maxSize)
        );
        
        assertEquals("FILE_TOO_LARGE", exception.getErrorCode());
    }

    @Test
    void validateFile_DangerousExtension_ShouldThrowException() {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
            "file", "malware.exe", "application/octet-stream", "malicious content".getBytes()
        );
        List<String> allowedTypes = Arrays.asList("application/octet-stream");
        long maxSize = 1024L * 1024L;

        // Act & Assert
        FileValidationException exception = assertThrows(FileValidationException.class, () ->
            storageService.validateFile(file, allowedTypes, maxSize)
        );
        
        assertEquals("DANGEROUS_FILE_TYPE", exception.getErrorCode());
    }

    @Test
    void generateFileKey_WithPrefix_ShouldCreateValidKey() {
        // Arrange
        String filename = "test file.jpg";
        String prefix = "uploads";

        // Act
        String key = storageService.generateFileKey(filename, prefix);

        // Assert
        assertNotNull(key);
        assertTrue(key.startsWith(prefix + "/"));
        assertTrue(key.contains("test_file.jpg"));
        assertTrue(key.matches("^uploads/\\d+_[a-f0-9]{8}_test_file\\.jpg$"));
    }

    @Test
    void generateFileKey_WithoutPrefix_ShouldCreateValidKey() {
        // Arrange
        String filename = "document.pdf";

        // Act
        String key = storageService.generateFileKey(filename, null);

        // Assert
        assertNotNull(key);
        assertFalse(key.startsWith("/"));
        assertTrue(key.contains("document.pdf"));
        assertTrue(key.matches("^\\d+_[a-f0-9]{8}_document\\.pdf$"));
    }

    @Test
    void getStorageStats_ValidBucket_ShouldReturnStats() {
        // Arrange
        List<S3Object> s3Objects = Arrays.asList(
            S3Object.builder().key("file1.txt").size(1024L).build(),
            S3Object.builder().key("file2.txt").size(2048L).build()
        );
        
        ListObjectsV2Response mockResponse = ListObjectsV2Response.builder()
            .contents(s3Objects)
            .isTruncated(false)
            .build();
        
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
            .thenReturn(mockResponse);

        // Act
        Map<String, Object> stats = storageService.getStorageStats();

        // Assert
        assertNotNull(stats);
        assertEquals(testBucketName, stats.get("bucket"));
        assertEquals(2, stats.get("objectCount"));
        assertEquals(3072L, stats.get("totalSize")); // 1024 + 2048
        assertEquals(false, stats.get("isTruncated"));
        verify(s3Client).listObjectsV2(any(ListObjectsV2Request.class));
    }

    // Helper method to set private fields using reflection
    private void setPrivateField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set private field: " + fieldName, e);
        }
    }
}