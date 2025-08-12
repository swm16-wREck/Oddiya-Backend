package com.oddiya.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oddiya.dto.response.FileUploadResponse;
import com.oddiya.service.storage.StorageService;
import com.oddiya.service.storage.FileValidationException;
import com.oddiya.service.storage.StorageException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FileUploadController.class)
class FileUploadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StorageService storageService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void uploadFile_ValidImage_ShouldReturnSuccess() throws Exception {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
            "file", "test-image.jpg", "image/jpeg", "fake image content".getBytes()
        );
        
        FileUploadResponse expectedResponse = FileUploadResponse.builder()
            .key("uploads/123456_abc12345_test-image.jpg")
            .bucket("test-bucket")
            .size(file.getSize())
            .contentType("image/jpeg")
            .etag("test-etag")
            .url("https://example.com/file.jpg")
            .metadata(new HashMap<>())
            .uploadTimestamp(Instant.now())
            .success(true)
            .build();

        when(storageService.uploadFile(eq(file), anyString(), any(Map.class)))
            .thenReturn(expectedResponse);

        // Act & Assert
        mockMvc.perform(multipart("/api/v1/files/upload")
                .file(file)
                .param("category", "image"))
            .andExpect(status().isOk())
            .andExpected(jsonPath("$.success").value(true))
            .andExpected(jsonPath("$.key").value(expectedResponse.getKey()))
            .andExpected(jsonPath("$.bucket").value(expectedResponse.getBucket()))
            .andExpected(jsonPath("$.size").value(expectedResponse.getSize()))
            .andExpected(jsonPath("$.contentType").value("image/jpeg"))
            .andExpected(jsonPath("$.etag").value("test-etag"))
            .andExpected(jsonPath("$.url").exists());

        verify(storageService).uploadFile(eq(file), anyString(), any(Map.class));
    }

    @Test
    void uploadFile_ValidDocument_ShouldReturnSuccess() throws Exception {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
            "file", "document.pdf", "application/pdf", "fake pdf content".getBytes()
        );
        
        FileUploadResponse expectedResponse = FileUploadResponse.builder()
            .key("documents/123456_def67890_document.pdf")
            .bucket("test-bucket")
            .size(file.getSize())
            .contentType("application/pdf")
            .etag("doc-etag")
            .url("https://example.com/document.pdf")
            .metadata(new HashMap<>())
            .uploadTimestamp(Instant.now())
            .success(true)
            .build();

        when(storageService.uploadFile(eq(file), anyString(), any(Map.class)))
            .thenReturn(expectedResponse);

        // Act & Assert
        mockMvc.perform(multipart("/api/v1/files/upload")
                .file(file)
                .param("category", "document"))
            .andExpected(status().isOk())
            .andExpected(jsonPath("$.success").value(true))
            .andExpected(jsonPath("$.key").value(expectedResponse.getKey()))
            .andExpected(jsonPath("$.contentType").value("application/pdf"));

        verify(storageService).uploadFile(eq(file), anyString(), any(Map.class));
    }

    @Test
    void uploadFile_WithMetadata_ShouldIncludeMetadata() throws Exception {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
            "file", "test.jpg", "image/jpeg", "content".getBytes()
        );
        
        Map<String, String> metadata = Map.of("author", "testuser", "project", "oddiya");
        FileUploadResponse expectedResponse = FileUploadResponse.builder()
            .key("images/test.jpg")
            .bucket("test-bucket")
            .size(file.getSize())
            .contentType("image/jpeg")
            .etag("test-etag")
            .url("https://example.com/test.jpg")
            .metadata(metadata)
            .uploadTimestamp(Instant.now())
            .success(true)
            .build();

        when(storageService.uploadFile(eq(file), anyString(), any(Map.class)))
            .thenReturn(expectedResponse);

        // Act & Assert
        mockMvc.perform(multipart("/api/v1/files/upload")
                .file(file)
                .param("category", "image")
                .param("author", "testuser")
                .param("project", "oddiya"))
            .andExpected(status().isOk())
            .andExpected(jsonPath("$.success").value(true))
            .andExpected(jsonPath("$.metadata.author").value("testuser"))
            .andExpected(jsonPath("$.metadata.project").value("oddiya"));

        verify(storageService).uploadFile(eq(file), anyString(), argThat(map -> 
            map.containsKey("author") && map.get("author").equals("testuser") &&
            map.containsKey("project") && map.get("project").equals("oddiya")
        ));
    }

    @Test
    void uploadFile_EmptyFile_ShouldReturnBadRequest() throws Exception {
        // Arrange
        MockMultipartFile emptyFile = new MockMultipartFile(
            "file", "empty.txt", "text/plain", new byte[0]
        );

        when(storageService.uploadFile(eq(emptyFile), anyString(), any(Map.class)))
            .thenThrow(new StorageException("File is empty", "EMPTY_FILE"));

        // Act & Assert
        mockMvc.perform(multipart("/api/v1/files/upload")
                .file(emptyFile)
                .param("category", "document"))
            .andExpected(status().isBadRequest())
            .andExpected(jsonPath("$.success").value(false))
            .andExpected(jsonPath("$.error").exists())
            .andExpected(jsonPath("$.errorCode").value("EMPTY_FILE"));

        verify(storageService).uploadFile(eq(emptyFile), anyString(), any(Map.class));
    }

    @Test
    void uploadFile_InvalidFileType_ShouldReturnBadRequest() throws Exception {
        // Arrange
        MockMultipartFile invalidFile = new MockMultipartFile(
            "file", "malware.exe", "application/octet-stream", "malicious content".getBytes()
        );

        when(storageService.uploadFile(eq(invalidFile), anyString(), any(Map.class)))
            .thenThrow(new FileValidationException("Dangerous file type", "DANGEROUS_FILE_TYPE"));

        // Act & Assert
        mockMvc.perform(multipart("/api/v1/files/upload")
                .file(invalidFile)
                .param("category", "document"))
            .andExpected(status().isBadRequest())
            .andExpected(jsonPath("$.success").value(false))
            .andExpected(jsonPath("$.error").exists())
            .andExpected(jsonPath("$.errorCode").value("DANGEROUS_FILE_TYPE"));

        verify(storageService).uploadFile(eq(invalidFile), anyString(), any(Map.class));
    }

    @Test
    void uploadFile_FileTooLarge_ShouldReturnBadRequest() throws Exception {
        // Arrange
        MockMultipartFile largeFile = new MockMultipartFile(
            "file", "large.jpg", "image/jpeg", new byte[1024 * 1024 * 11] // 11MB
        );

        when(storageService.uploadFile(eq(largeFile), anyString(), any(Map.class)))
            .thenThrow(new FileValidationException("File too large", "FILE_TOO_LARGE", 
                largeFile.getSize(), 10L * 1024 * 1024));

        // Act & Assert
        mockMvc.perform(multipart("/api/v1/files/upload")
                .file(largeFile)
                .param("category", "image"))
            .andExpected(status().isBadRequest())
            .andExpected(jsonPath("$.success").value(false))
            .andExpected(jsonPath("$.error").exists())
            .andExpected(jsonPath("$.errorCode").value("FILE_TOO_LARGE"));

        verify(storageService).uploadFile(eq(largeFile), anyString(), any(Map.class));
    }

    @Test
    void uploadFile_StorageServiceFailure_ShouldReturnInternalServerError() throws Exception {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
            "file", "test.jpg", "image/jpeg", "content".getBytes()
        );

        when(storageService.uploadFile(eq(file), anyString(), any(Map.class)))
            .thenThrow(new StorageException("S3 upload failed", "UPLOAD_FAILED"));

        // Act & Assert
        mockMvc.perform(multipart("/api/v1/files/upload")
                .file(file)
                .param("category", "image"))
            .andExpected(status().isInternalServerError())
            .andExpected(jsonPath("$.success").value(false))
            .andExpected(jsonPath("$.error").exists())
            .andExpected(jsonPath("$.errorCode").value("UPLOAD_FAILED"));

        verify(storageService).uploadFile(eq(file), anyString(), any(Map.class));
    }

    @Test
    void uploadFile_UnexpectedException_ShouldReturnInternalServerError() throws Exception {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
            "file", "test.jpg", "image/jpeg", "content".getBytes()
        );

        when(storageService.uploadFile(eq(file), anyString(), any(Map.class)))
            .thenThrow(new RuntimeException("Unexpected error"));

        // Act & Assert
        mockMvc.perform(multipart("/api/v1/files/upload")
                .file(file)
                .param("category", "image"))
            .andExpected(status().isInternalServerError())
            .andExpected(jsonPath("$.success").value(false))
            .andExpected(jsonPath("$.error").exists());

        verify(storageService).uploadFile(eq(file), anyString(), any(Map.class));
    }

    @Test
    void uploadFile_InvalidCategory_ShouldUseGeneral() throws Exception {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
            "file", "test.txt", "text/plain", "content".getBytes()
        );
        
        FileUploadResponse expectedResponse = FileUploadResponse.builder()
            .key("general/test.txt")
            .bucket("test-bucket")
            .size(file.getSize())
            .contentType("text/plain")
            .etag("test-etag")
            .url("https://example.com/test.txt")
            .metadata(new HashMap<>())
            .uploadTimestamp(Instant.now())
            .success(true)
            .build();

        when(storageService.uploadFile(eq(file), anyString(), any(Map.class)))
            .thenReturn(expectedResponse);

        // Act & Assert
        mockMvc.perform(multipart("/api/v1/files/upload")
                .file(file)
                .param("category", "invalid-category"))
            .andExpected(status().isOk())
            .andExpected(jsonPath("$.success").value(true));

        // Verify that a key was generated (should start with general/ for invalid category)
        verify(storageService).uploadFile(eq(file), argThat(key -> key.contains("general")), any(Map.class));
    }

    @Test
    void uploadFile_NoCategory_ShouldUseGeneral() throws Exception {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
            "file", "test.txt", "text/plain", "content".getBytes()
        );
        
        FileUploadResponse expectedResponse = FileUploadResponse.builder()
            .key("general/test.txt")
            .bucket("test-bucket")
            .size(file.getSize())
            .contentType("text/plain")
            .etag("test-etag")
            .url("https://example.com/test.txt")
            .metadata(new HashMap<>())
            .uploadTimestamp(Instant.now())
            .success(true)
            .build();

        when(storageService.uploadFile(eq(file), anyString(), any(Map.class)))
            .thenReturn(expectedResponse);

        // Act & Assert
        mockMvc.perform(multipart("/api/v1/files/upload")
                .file(file))
            .andExpected(status().isOk())
            .andExpected(jsonPath("$.success").value(true));

        verify(storageService).uploadFile(eq(file), anyString(), any(Map.class));
    }

    @Test
    void uploadFile_VideoCategory_ShouldReturnSuccess() throws Exception {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
            "file", "video.mp4", "video/mp4", "fake video content".getBytes()
        );
        
        FileUploadResponse expectedResponse = FileUploadResponse.builder()
            .key("videos/123456_abc12345_video.mp4")
            .bucket("test-bucket")
            .size(file.getSize())
            .contentType("video/mp4")
            .etag("video-etag")
            .url("https://example.com/video.mp4")
            .metadata(new HashMap<>())
            .uploadTimestamp(Instant.now())
            .success(true)
            .build();

        when(storageService.uploadFile(eq(file), anyString(), any(Map.class)))
            .thenReturn(expectedResponse);

        // Act & Assert
        mockMvc.perform(multipart("/api/v1/files/upload")
                .file(file)
                .param("category", "video"))
            .andExpected(status().isOk())
            .andExpected(jsonPath("$.success").value(true))
            .andExpected(jsonPath("$.contentType").value("video/mp4"));

        verify(storageService).uploadFile(eq(file), argThat(key -> key.contains("videos")), any(Map.class));
    }

    @Test
    void uploadFile_MissingFileParameter_ShouldReturnBadRequest() throws Exception {
        // Act & Assert
        mockMvc.perform(multipart("/api/v1/files/upload")
                .param("category", "image"))
            .andExpected(status().isBadRequest());

        verify(storageService, never()).uploadFile(any(), anyString(), any());
    }

    @Test
    void uploadFile_WithCustomKey_ShouldUseCustomKey() throws Exception {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
            "file", "custom.jpg", "image/jpeg", "content".getBytes()
        );
        
        FileUploadResponse expectedResponse = FileUploadResponse.builder()
            .key("custom/path/custom.jpg")
            .bucket("test-bucket")
            .size(file.getSize())
            .contentType("image/jpeg")
            .etag("custom-etag")
            .url("https://example.com/custom/path/custom.jpg")
            .metadata(new HashMap<>())
            .uploadTimestamp(Instant.now())
            .success(true)
            .build();

        when(storageService.uploadFile(eq(file), eq("custom/path/custom.jpg"), any(Map.class)))
            .thenReturn(expectedResponse);

        // Act & Assert
        mockMvc.perform(multipart("/api/v1/files/upload")
                .file(file)
                .param("category", "image")
                .param("key", "custom/path/custom.jpg"))
            .andExpected(status().isOk())
            .andExpected(jsonPath("$.success").value(true))
            .andExpected(jsonPath("$.key").value("custom/path/custom.jpg"));

        verify(storageService).uploadFile(eq(file), eq("custom/path/custom.jpg"), any(Map.class));
    }

    @Test
    void uploadFile_LargeSizeInResponse_ShouldFormatCorrectly() throws Exception {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
            "file", "large.jpg", "image/jpeg", new byte[1024 * 1024] // 1MB
        );
        
        FileUploadResponse expectedResponse = FileUploadResponse.builder()
            .key("images/large.jpg")
            .bucket("test-bucket")
            .size(1024L * 1024L)
            .contentType("image/jpeg")
            .etag("large-etag")
            .url("https://example.com/large.jpg")
            .metadata(new HashMap<>())
            .uploadTimestamp(Instant.now())
            .success(true)
            .build();

        when(storageService.uploadFile(eq(file), anyString(), any(Map.class)))
            .thenReturn(expectedResponse);

        // Act & Assert
        mockMvc.perform(multipart("/api/v1/files/upload")
                .file(file)
                .param("category", "image"))
            .andExpected(status().isOk())
            .andExpected(jsonPath("$.success").value(true))
            .andExpected(jsonPath("$.size").value(1048576)); // 1MB in bytes

        verify(storageService).uploadFile(eq(file), anyString(), any(Map.class));
    }
}