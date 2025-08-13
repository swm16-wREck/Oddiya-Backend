package com.oddiya.dto.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Message DTO for image processing operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageProcessingMessage {

    @NotBlank(message = "Message ID is required")
    @JsonProperty("messageId")
    private String messageId;

    @NotBlank(message = "Image ID is required")
    @JsonProperty("imageId")
    private String imageId;

    @NotBlank(message = "User ID is required")
    @JsonProperty("userId")
    private String userId;

    @NotBlank(message = "Source S3 key is required")
    @JsonProperty("sourceS3Key")
    private String sourceS3Key;

    @NotBlank(message = "Source bucket is required")
    @JsonProperty("sourceBucket")
    private String sourceBucket;

    @NotBlank(message = "Destination bucket is required")
    @JsonProperty("destinationBucket")
    private String destinationBucket;

    @JsonProperty("destinationS3Prefix")
    private String destinationS3Prefix;

    @NotNull(message = "Processing type is required")
    @JsonProperty("processingType")
    private ProcessingType processingType;

    @JsonProperty("resizeOptions")
    private List<ResizeOption> resizeOptions;

    @JsonProperty("compressionOptions")
    private CompressionOptions compressionOptions;

    @JsonProperty("watermarkOptions")
    private WatermarkOptions watermarkOptions;

    @JsonProperty("filterOptions")
    private FilterOptions filterOptions;

    @JsonProperty("metadataOptions")
    private MetadataOptions metadataOptions;

    @Builder.Default
    @JsonProperty("generateThumbnails")
    private boolean generateThumbnails = true;

    @Builder.Default
    @JsonProperty("preserveOriginal")
    private boolean preserveOriginal = true;

    @JsonProperty("callbackUrl")
    private String callbackUrl;

    @JsonProperty("webhookUrl")
    private String webhookUrl;

    @JsonProperty("tags")
    private List<String> tags;

    @JsonProperty("metadata")
    private Map<String, String> metadata;

    @Builder.Default
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @JsonProperty("createdAt")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    @JsonProperty("priority")
    private ProcessingPriority priority = ProcessingPriority.NORMAL;

    @Builder.Default
    @JsonProperty("retryCount")
    private int retryCount = 0;

    @Builder.Default
    @JsonProperty("maxRetries")
    private int maxRetries = 3;

    /**
     * Image processing types
     */
    public enum ProcessingType {
        RESIZE,
        COMPRESS,
        OPTIMIZE,
        THUMBNAIL_GENERATION,
        WATERMARK,
        FORMAT_CONVERSION,
        FILTER_APPLICATION,
        METADATA_EXTRACTION,
        FULL_PROCESSING // Includes resize, compress, thumbnails, metadata
    }

    /**
     * Processing priority levels
     */
    public enum ProcessingPriority {
        LOW(1),
        NORMAL(2),
        HIGH(3),
        URGENT(4);

        private final int level;

        ProcessingPriority(int level) {
            this.level = level;
        }

        public int getLevel() {
            return level;
        }
    }

    /**
     * Resize configuration
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResizeOption {
        @Positive
        private int width;
        
        @Positive
        private int height;
        
        @Builder.Default
        private boolean maintainAspectRatio = true;
        
        @Builder.Default
        private ResizeMode resizeMode = ResizeMode.FIT;
        
        private String suffix; // e.g., "_large", "_medium", "_small"
        
        public enum ResizeMode {
            FIT,        // Resize to fit within dimensions
            FILL,       // Resize to fill dimensions (may crop)
            STRETCH,    // Stretch to exact dimensions
            THUMBNAIL   // Generate thumbnail
        }
    }

    /**
     * Compression configuration
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompressionOptions {
        @Builder.Default
        private int quality = 85; // 0-100
        
        @Builder.Default
        private CompressionFormat format = CompressionFormat.JPEG;
        
        @Builder.Default
        private boolean progressive = true;
        
        @Builder.Default
        private boolean lossless = false;
        
        public enum CompressionFormat {
            JPEG,
            PNG,
            WEBP,
            AVIF
        }
    }

    /**
     * Watermark configuration
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WatermarkOptions {
        private String watermarkImageKey;
        private String watermarkText;
        @Builder.Default
        private WatermarkPosition position = WatermarkPosition.BOTTOM_RIGHT;
        @Builder.Default
        private double opacity = 0.7;
        @Builder.Default
        private int fontSize = 12;
        @Builder.Default
        private String fontColor = "#FFFFFF";
        
        public enum WatermarkPosition {
            TOP_LEFT,
            TOP_RIGHT,
            BOTTOM_LEFT,
            BOTTOM_RIGHT,
            CENTER
        }
    }

    /**
     * Filter configuration
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FilterOptions {
        private FilterType filterType;
        @Builder.Default
        private double intensity = 1.0;
        private Map<String, Object> parameters;
        
        public enum FilterType {
            BLUR,
            SHARPEN,
            BRIGHTNESS,
            CONTRAST,
            SATURATION,
            SEPIA,
            GRAYSCALE,
            VINTAGE
        }
    }

    /**
     * Metadata extraction options
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MetadataOptions {
        @Builder.Default
        private boolean extractExif = true;
        
        @Builder.Default
        private boolean extractLocation = true;
        
        @Builder.Default
        private boolean extractColors = true;
        
        @Builder.Default
        private boolean detectObjects = false;
        
        @Builder.Default
        private boolean extractText = false;
    }
}