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
 * Message DTO for video processing operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoProcessingMessage {

    @NotBlank(message = "Message ID is required")
    @JsonProperty("messageId")
    private String messageId;

    @NotBlank(message = "Video ID is required")
    @JsonProperty("videoId")
    private String videoId;

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

    @JsonProperty("transcodeOptions")
    private List<TranscodeOption> transcodeOptions;

    @JsonProperty("thumbnailOptions")
    private ThumbnailOptions thumbnailOptions;

    @JsonProperty("subtitleOptions")
    private SubtitleOptions subtitleOptions;

    @JsonProperty("watermarkOptions")
    private WatermarkOptions watermarkOptions;

    @JsonProperty("compressionOptions")
    private CompressionOptions compressionOptions;

    @JsonProperty("analysisOptions")
    private AnalysisOptions analysisOptions;

    @JsonProperty("streamingOptions")
    private StreamingOptions streamingOptions;

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

    @JsonProperty("notificationEmails")
    private List<String> notificationEmails;

    @JsonProperty("tags")
    private List<String> tags;

    @JsonProperty("metadata")
    private Map<String, Object> metadata;

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
     * Video processing types
     */
    public enum ProcessingType {
        TRANSCODE,
        THUMBNAIL_GENERATION,
        SUBTITLE_GENERATION,
        COMPRESSION,
        WATERMARK,
        ANALYSIS,
        STREAMING_PREPARATION,
        FORMAT_CONVERSION,
        QUALITY_ENHANCEMENT,
        FULL_PROCESSING // Includes all above
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
     * Video transcoding configuration
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TranscodeOption {
        @NotBlank
        private String preset; // e.g., "1080p", "720p", "480p", "mobile"
        
        private String codec; // H.264, H.265, VP9, AV1
        private String container; // MP4, WebM, MKV
        private String resolution; // 1920x1080, 1280x720, etc.
        private Integer bitrate; // in kbps
        private Integer frameRate; // fps
        private String audioCodec; // AAC, MP3, Opus
        private Integer audioBitrate; // in kbps
        private String suffix; // "_1080p", "_720p", etc.
        
        @Builder.Default
        private boolean twoPass = false;
        
        @Builder.Default
        private boolean constantRateFactor = true;
        
        @Builder.Default
        private int crf = 23; // Constant Rate Factor (0-51)
    }

    /**
     * Thumbnail generation configuration
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ThumbnailOptions {
        @Builder.Default
        private int count = 5;
        
        @Builder.Default
        private int width = 1280;
        
        @Builder.Default
        private int height = 720;
        
        @Builder.Default
        private String format = "JPEG";
        
        @Builder.Default
        private int quality = 90;
        
        @Builder.Default
        private ThumbnailMode mode = ThumbnailMode.AUTO;
        
        private List<Double> specificTimestamps; // in seconds
        
        @Builder.Default
        private boolean generateAnimatedGif = false;
        
        @Builder.Default
        private int gifDurationSeconds = 3;
        
        public enum ThumbnailMode {
            AUTO,           // Automatically select best frames
            INTERVAL,       // At regular intervals
            SPECIFIC_TIMES, // At specified timestamps
            SCENE_CHANGES   // At scene changes
        }
    }

    /**
     * Subtitle/Caption generation configuration
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubtitleOptions {
        @Builder.Default
        private boolean generateSubtitles = false;
        
        private List<String> languages; // "en", "ko", "ja", etc.
        
        @Builder.Default
        private String sourceLanguage = "auto-detect";
        
        @Builder.Default
        private SubtitleFormat format = SubtitleFormat.SRT;
        
        @Builder.Default
        private boolean burnIntoVideo = false;
        
        @Builder.Default
        private String fontSize = "medium";
        
        @Builder.Default
        private String fontColor = "white";
        
        public enum SubtitleFormat {
            SRT,
            VTT,
            ASS,
            TTML
        }
    }

    /**
     * Video watermark configuration
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
        private int fontSize = 24;
        @Builder.Default
        private String fontColor = "#FFFFFF";
        @Builder.Default
        private double scale = 1.0;
        @Builder.Default
        private int fadeInDuration = 0; // seconds
        @Builder.Default
        private int fadeOutDuration = 0; // seconds
        
        public enum WatermarkPosition {
            TOP_LEFT,
            TOP_CENTER,
            TOP_RIGHT,
            CENTER_LEFT,
            CENTER,
            CENTER_RIGHT,
            BOTTOM_LEFT,
            BOTTOM_CENTER,
            BOTTOM_RIGHT
        }
    }

    /**
     * Video compression configuration
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompressionOptions {
        @Builder.Default
        private CompressionPreset preset = CompressionPreset.BALANCED;
        
        @Builder.Default
        private int targetFileSizeMB = 0; // 0 = no target
        
        @Builder.Default
        private double compressionRatio = 0.0; // 0.0 = no specific ratio
        
        @Builder.Default
        private boolean optimizeForStreaming = true;
        
        public enum CompressionPreset {
            FAST,           // Lower quality, faster processing
            BALANCED,       // Balanced quality and speed
            HIGH_QUALITY,   // Higher quality, slower processing
            MAX_COMPRESSION // Maximum compression, lowest file size
        }
    }

    /**
     * Video analysis configuration
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnalysisOptions {
        @Builder.Default
        private boolean detectScenes = false;
        
        @Builder.Default
        private boolean detectObjects = false;
        
        @Builder.Default
        private boolean detectFaces = false;
        
        @Builder.Default
        private boolean extractAudioFeatures = false;
        
        @Builder.Default
        private boolean generateTranscript = false;
        
        @Builder.Default
        private boolean detectNSFWContent = false;
        
        @Builder.Default
        private boolean extractMetadata = true;
        
        @Builder.Default
        private boolean qualityAssessment = false;
    }

    /**
     * Streaming preparation configuration
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StreamingOptions {
        @Builder.Default
        private boolean prepareForHLS = true;
        
        @Builder.Default
        private boolean prepareForDASH = false;
        
        @Builder.Default
        private int segmentDuration = 10; // seconds
        
        @Builder.Default
        private boolean generateMultipleBitrates = true;
        
        private List<Integer> bitrateLadder; // List of bitrates for ABR
        
        @Builder.Default
        private boolean generateClosedCaptions = false;
        
        @Builder.Default
        private boolean optimizeForMobile = true;
    }

    /**
     * Common processing presets
     */
    public static class ProcessingPresets {
        public static final String WEB_OPTIMIZED = "web_optimized";
        public static final String MOBILE_OPTIMIZED = "mobile_optimized";
        public static final String SOCIAL_MEDIA = "social_media";
        public static final String HIGH_QUALITY = "high_quality";
        public static final String STREAMING = "streaming";
        public static final String ARCHIVE = "archive";
    }
}