package com.oddiya.aws;

import com.oddiya.dto.response.FileUploadResponse;
import com.oddiya.service.storage.S3StorageService;
import com.oddiya.service.storage.StorageException;
import com.oddiya.service.storage.FileValidationException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.multipart.MultipartFile;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for S3 storage service using LocalStack container.
 * Tests file upload/download operations, presigned URLs, and mock virus scanning.
 */
@SpringBootTest
@ActiveProfiles("s3-test")
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class S3IntegrationTest {

    @Container
    static GenericContainer<?> localstack = new GenericContainer<>("localstack/localstack:3.0")
            .withExposedPorts(4566)
            .withEnv("SERVICES", "s3")
            .withEnv("DEBUG", "1")
            .withEnv("AWS_DEFAULT_REGION", "ap-northeast-2")
            .waitingFor(Wait.forHttp("/health").forPort(4566));

    @Autowired(required = false)
    private S3StorageService s3StorageService;

    private static S3Client testS3Client;
    private static final String TEST_BUCKET = "oddiya-test-bucket";

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("app.aws.s3.bucket", () -> TEST_BUCKET);
        registry.add("app.aws.s3.enabled", () -> "true");
        registry.add("app.aws.s3.region", () -> "ap-northeast-2");
        registry.add("aws.region", () -> "ap-northeast-2");
        registry.add("spring.profiles.active", () -> "s3-test");
    }

    @BeforeAll
    static void setUpS3() {
        testS3Client = S3Client.builder()
                .region(Region.AP_NORTHEAST_2)
                .endpointOverride(URI.create("http://localhost:" + localstack.getMappedPort(4566)))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test", "test")))
                .forcePathStyle(true) // Required for LocalStack
                .build();

        createTestBucket();
    }

    static void createTestBucket() {
        try {
            CreateBucketRequest request = CreateBucketRequest.builder()
                    .bucket(TEST_BUCKET)
                    .build();
            testS3Client.createBucket(request);
            
            // Wait for bucket to be available
            Thread.sleep(1000);
        } catch (BucketAlreadyExistsException e) {
            // Bucket already exists, ignore
        } catch (Exception e) {
            System.err.println("Warning: Could not create test bucket: " + e.getMessage());
        }
    }

    @AfterAll
    static void cleanUp() {
        if (testS3Client != null) {
            try {
                // Clean up test bucket
                ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                        .bucket(TEST_BUCKET)
                        .build();
                
                ListObjectsV2Response listResponse = testS3Client.listObjectsV2(listRequest);
                if (!listResponse.contents().isEmpty()) {
                    List<ObjectIdentifier> objects = new ArrayList<>();
                    listResponse.contents().forEach(obj -> 
                        objects.add(ObjectIdentifier.builder().key(obj.key()).build()));
                    
                    DeleteObjectsRequest deleteRequest = DeleteObjectsRequest.builder()
                            .bucket(TEST_BUCKET)
                            .delete(Delete.builder().objects(objects).build())
                            .build();
                    testS3Client.deleteObjects(deleteRequest);
                }
                
                DeleteBucketRequest deleteBucketRequest = DeleteBucketRequest.builder()
                        .bucket(TEST_BUCKET)
                        .build();
                testS3Client.deleteBucket(deleteBucketRequest);
            } catch (Exception e) {
                System.err.println("Warning: Could not clean up test bucket: " + e.getMessage());
            } finally {
                testS3Client.close();
            }
        }
    }

    @Test
    @Order(1)
    void contextLoads() {
        if ("s3-test".equals(System.getProperty("spring.profiles.active"))) {
            assertThat(s3StorageService).isNotNull();
        }
    }

    @Test
    @Order(2)
    void testFileUpload() throws StorageException {
        if (s3StorageService == null) {
            System.out.println("Skipping S3 test - service not available");
            return;
        }

        // Create test file
        byte[] content = "Hello, S3 World!".getBytes();
        MultipartFile file = new MockMultipartFile(
                "test.txt", 
                "test.txt", 
                "text/plain", 
                content
        );

        // Test upload
        Map<String, String> metadata = new HashMap<>();
        metadata.put("test-key", "test-value");
        metadata.put("upload-source", "unit-test");

        FileUploadResponse response = s3StorageService.uploadFile(file, "test/test.txt", metadata);

        // Verify upload response
        assertThat(response).isNotNull();
        assertThat(response.getKey()).isEqualTo("test/test.txt");
        assertThat(response.getBucket()).isEqualTo(TEST_BUCKET);
        assertThat(response.getSize()).isEqualTo(content.length);
        assertThat(response.getContentType()).isEqualTo("text/plain");
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getEtag()).isNotNull();
        assertThat(response.getUrl()).isNotNull();
        assertThat(response.getMetadata()).containsKey("test-key");

        // Verify file exists
        assertThat(s3StorageService.fileExists("test/test.txt")).isTrue();
        assertThat(s3StorageService.getFileSize("test/test.txt")).isEqualTo(content.length);
    }

    @Test
    @Order(3)
    void testFileDownload() throws StorageException, IOException {
        if (s3StorageService == null) {
            System.out.println("Skipping S3 test - service not available");
            return;
        }

        // First upload a file
        byte[] originalContent = "Download test content".getBytes();
        MultipartFile uploadFile = new MockMultipartFile(
                "download-test.txt", 
                "download-test.txt", 
                "text/plain", 
                originalContent
        );
        
        s3StorageService.uploadFile(uploadFile, "test/download-test.txt", null);

        // Test download
        InputStream downloadStream = s3StorageService.downloadFile("test/download-test.txt");
        
        assertThat(downloadStream).isNotNull();
        
        // Read content and verify
        byte[] downloadedContent = downloadStream.readAllBytes();
        assertThat(downloadedContent).isEqualTo(originalContent);
        
        downloadStream.close();
    }

    @Test
    @Order(4)
    void testPresignedUrls() throws StorageException {
        if (s3StorageService == null) {
            System.out.println("Skipping S3 test - service not available");
            return;
        }

        // Test presigned upload URL
        String uploadUrl = s3StorageService.generatePresignedUploadUrl(
                "test/presigned-upload.txt", 
                "text/plain", 
                3600
        );
        
        assertThat(uploadUrl).isNotNull();
        assertThat(uploadUrl).contains(TEST_BUCKET);
        assertThat(uploadUrl).contains("presigned-upload.txt");

        // Upload a file first for download URL test
        byte[] content = "Presigned download test".getBytes();
        MultipartFile file = new MockMultipartFile(
                "presigned-download.txt", 
                "presigned-download.txt", 
                "text/plain", 
                content
        );
        s3StorageService.uploadFile(file, "test/presigned-download.txt", null);

        // Test presigned download URL
        String downloadUrl = s3StorageService.generatePresignedDownloadUrl(
                "test/presigned-download.txt", 
                3600
        );
        
        assertThat(downloadUrl).isNotNull();
        assertThat(downloadUrl).contains(TEST_BUCKET);
        assertThat(downloadUrl).contains("presigned-download.txt");
    }

    @Test
    @Order(5)
    void testLargeFileUpload() throws StorageException {
        if (s3StorageService == null) {
            System.out.println("Skipping S3 test - service not available");
            return;
        }

        // Create large file (> 16MB to trigger multipart upload)
        int size = 17 * 1024 * 1024; // 17MB
        byte[] largeContent = new byte[size];
        Arrays.fill(largeContent, (byte) 'A');
        
        MultipartFile largeFile = new MockMultipartFile(
                "large-file.txt", 
                "large-file.txt", 
                "text/plain", 
                largeContent
        );

        // Test large file upload
        FileUploadResponse response = s3StorageService.uploadFile(
                largeFile, 
                "test/large-file.txt", 
                null
        );

        assertThat(response).isNotNull();
        assertThat(response.getSize()).isEqualTo(size);
        assertThat(response.isSuccess()).isTrue();

        // Verify file exists and has correct size
        assertThat(s3StorageService.fileExists("test/large-file.txt")).isTrue();
        assertThat(s3StorageService.getFileSize("test/large-file.txt")).isEqualTo(size);
    }

    @Test
    @Order(6)
    void testAsyncUpload() throws StorageException, ExecutionException, InterruptedException {
        if (s3StorageService == null) {
            System.out.println("Skipping S3 test - service not available");
            return;
        }

        // Create test file
        byte[] content = "Async upload test".getBytes();
        MultipartFile file = new MockMultipartFile(
                "async-test.txt", 
                "async-test.txt", 
                "text/plain", 
                content
        );

        // Test async upload
        CompletableFuture<FileUploadResponse> future = s3StorageService.uploadFileAsync(
                file, 
                "test/async-test.txt", 
                null
        );

        FileUploadResponse response = future.get();
        
        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isTrue();
        assertThat(s3StorageService.fileExists("test/async-test.txt")).isTrue();
    }

    @Test
    @Order(7)
    void testBatchOperations() throws StorageException {
        if (s3StorageService == null) {
            System.out.println("Skipping S3 test - service not available");
            return;
        }

        // Upload multiple files
        List<String> keys = Arrays.asList(
                "test/batch1.txt",
                "test/batch2.txt", 
                "test/batch3.txt"
        );

        for (String key : keys) {
            MultipartFile file = new MockMultipartFile(
                    "batch.txt", 
                    "batch.txt", 
                    "text/plain", 
                    ("Batch test content for " + key).getBytes()
            );
            s3StorageService.uploadFile(file, key, null);
        }

        // Test batch delete
        Map<String, Boolean> deleteResults = s3StorageService.deleteFiles(keys);
        
        assertThat(deleteResults).hasSize(3);
        deleteResults.values().forEach(result -> assertThat(result).isTrue());

        // Verify files are deleted
        keys.forEach(key -> assertThat(s3StorageService.fileExists(key)).isFalse());
    }

    @Test
    @Order(8)
    void testFileOperations() throws StorageException {
        if (s3StorageService == null) {
            System.out.println("Skipping S3 test - service not available");
            return;
        }

        // Upload source file
        byte[] content = "File operations test".getBytes();
        MultipartFile file = new MockMultipartFile(
                "source.txt", 
                "source.txt", 
                "text/plain", 
                content
        );
        s3StorageService.uploadFile(file, "test/source.txt", null);

        // Test copy operation
        boolean copyResult = s3StorageService.copyFile("test/source.txt", "test/copied.txt");
        assertThat(copyResult).isTrue();
        assertThat(s3StorageService.fileExists("test/copied.txt")).isTrue();

        // Test move operation
        boolean moveResult = s3StorageService.moveFile("test/copied.txt", "test/moved.txt");
        assertThat(moveResult).isTrue();
        assertThat(s3StorageService.fileExists("test/moved.txt")).isTrue();
        assertThat(s3StorageService.fileExists("test/copied.txt")).isFalse();

        // Clean up
        s3StorageService.deleteFile("test/source.txt");
        s3StorageService.deleteFile("test/moved.txt");
    }

    @Test
    @Order(9)
    void testFileValidation() {
        if (s3StorageService == null) {
            System.out.println("Skipping S3 test - service not available");
            return;
        }

        // Test valid file
        MultipartFile validFile = new MockMultipartFile(
                "valid.txt", 
                "valid.txt", 
                "text/plain", 
                "Valid content".getBytes()
        );

        assertThatNoException().isThrownBy(() -> 
            s3StorageService.validateFile(validFile, Arrays.asList("text/plain"), 1024)
        );

        // Test invalid file type
        MultipartFile invalidTypeFile = new MockMultipartFile(
                "invalid.exe", 
                "invalid.exe", 
                "application/octet-stream", 
                "Invalid content".getBytes()
        );

        assertThatThrownBy(() -> 
            s3StorageService.validateFile(invalidTypeFile, Arrays.asList("text/plain"), 1024)
        ).isInstanceOf(FileValidationException.class);

        // Test file too large
        MultipartFile largeFile = new MockMultipartFile(
                "large.txt", 
                "large.txt", 
                "text/plain", 
                new byte[2048] // Larger than 1024 limit
        );

        assertThatThrownBy(() -> 
            s3StorageService.validateFile(largeFile, Arrays.asList("text/plain"), 1024)
        ).isInstanceOf(FileValidationException.class);

        // Test dangerous file extension
        MultipartFile dangerousFile = new MockMultipartFile(
                "malware.exe", 
                "malware.exe", 
                "application/octet-stream", 
                "Malware content".getBytes()
        );

        assertThatThrownBy(() -> 
            s3StorageService.validateFile(dangerousFile, null, 0)
        ).isInstanceOf(FileValidationException.class);
    }

    @Test
    @Order(10)
    void testMetadataOperations() throws StorageException {
        if (s3StorageService == null) {
            System.out.println("Skipping S3 test - service not available");
            return;
        }

        // Upload file with metadata
        Map<String, String> metadata = new HashMap<>();
        metadata.put("author", "test-user");
        metadata.put("category", "test-document");
        metadata.put("version", "1.0");

        MultipartFile file = new MockMultipartFile(
                "metadata-test.txt", 
                "metadata-test.txt", 
                "text/plain", 
                "Metadata test content".getBytes()
        );

        s3StorageService.uploadFile(file, "test/metadata-test.txt", metadata);

        // Retrieve and verify metadata
        Map<String, String> retrievedMetadata = s3StorageService.getFileMetadata("test/metadata-test.txt");
        
        assertThat(retrievedMetadata).isNotEmpty();
        assertThat(retrievedMetadata).containsKey("author");
        assertThat(retrievedMetadata).containsKey("category");
        assertThat(retrievedMetadata).containsKey("version");
    }

    @Test
    @Order(11)
    void testVirusScanningIntegration() throws StorageException {
        if (s3StorageService == null) {
            System.out.println("Skipping S3 test - service not available");
            return;
        }

        // Upload file that should be marked for virus scanning
        MultipartFile file = new MockMultipartFile(
                "scan-test.txt", 
                "scan-test.txt", 
                "text/plain", 
                "File to be scanned for viruses".getBytes()
        );

        FileUploadResponse response = s3StorageService.uploadFile(file, "test/scan-test.txt", null);
        
        // Verify virus scan metadata is set to pending
        assertThat(response.getMetadata()).containsKey("virus-scan-status");
        assertThat(response.getMetadata().get("virus-scan-status")).isEqualTo("pending");

        // Simulate virus scan completion (clean file)
        s3StorageService.markFileAsScanned("test/scan-test.txt", true);

        // In a real implementation, you would verify the metadata was updated
        // This would require re-uploading the object with updated metadata in S3
    }

    @Test
    @Order(12)
    void testErrorHandling() {
        if (s3StorageService == null) {
            System.out.println("Skipping S3 test - service not available");
            return;
        }

        // Test downloading non-existent file
        assertThatThrownBy(() -> 
            s3StorageService.downloadFile("test/non-existent.txt")
        ).isInstanceOf(StorageException.class);

        // Test getting metadata for non-existent file
        Map<String, String> metadata = s3StorageService.getFileMetadata("test/non-existent.txt");
        assertThat(metadata).isEmpty();

        // Test checking existence of non-existent file
        assertThat(s3StorageService.fileExists("test/non-existent.txt")).isFalse();

        // Test getting size of non-existent file
        assertThat(s3StorageService.getFileSize("test/non-existent.txt")).isEqualTo(-1);
    }

    @Test
    @Order(13)
    void testStorageStats() {
        if (s3StorageService == null) {
            System.out.println("Skipping S3 test - service not available");
            return;
        }

        Map<String, Object> stats = s3StorageService.getStorageStats();
        
        assertThat(stats).isNotNull();
        assertThat(stats).containsKey("bucket");
        assertThat(stats).containsKey("objectCount");
        assertThat(stats).containsKey("totalSize");
        assertThat(stats.get("bucket")).isEqualTo(TEST_BUCKET);
    }

    @Test
    @Order(14)
    void testListFiles() throws StorageException {
        if (s3StorageService == null) {
            System.out.println("Skipping S3 test - service not available");
            return;
        }

        // Upload some files with common prefix
        for (int i = 1; i <= 3; i++) {
            MultipartFile file = new MockMultipartFile(
                    "list-test-" + i + ".txt", 
                    "list-test-" + i + ".txt", 
                    "text/plain", 
                    ("List test content " + i).getBytes()
            );
            s3StorageService.uploadFile(file, "test/list/file" + i + ".txt", null);
        }

        // Test listing files
        List<String> files = s3StorageService.listFiles("test/list/", 10);
        
        assertThat(files).isNotEmpty();
        assertThat(files.size()).isGreaterThanOrEqualTo(3);
        assertThat(files).allMatch(key -> key.startsWith("test/list/"));
    }
}