package com.oddiya.config.aws;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "app.aws.s3")
@ConditionalOnProperty(name = "app.aws.s3.enabled", havingValue = "true", matchIfMissing = false)
@ConditionalOnBean(S3Client.class)
@Data
@Slf4j
public class S3Config {

    private String bucket;
    private String region;
    private int presignedUrlExpiration = 3600; // 1 hour in seconds
    private long multipartThreshold = 16 * 1024 * 1024; // 16MB
    private boolean createBucketOnStartup = false;
    private CorsConfig cors = new CorsConfig();
    private StorageClasses storageClasses = new StorageClasses();
    private LifecycleConfig lifecycle = new LifecycleConfig();

    @Bean
    public S3Presigner s3Presigner(S3Client s3Client) {
        log.info("Creating S3 Presigner for bucket: {}", bucket);
        return S3Presigner.builder()
                .s3Client(s3Client)
                .build();
    }

    @Bean
    public S3BucketManager s3BucketManager(S3Client s3Client) {
        return new S3BucketManager(s3Client, this);
    }

    @Bean
    public S3FileManager s3FileManager(S3Client s3Client, S3Presigner s3Presigner) {
        return new S3FileManager(s3Client, s3Presigner, this);
    }

    @Data
    public static class CorsConfig {
        private boolean enabled = true;
        private List<String> allowedOrigins = Arrays.asList("*");
        private List<String> allowedMethods = Arrays.asList("GET", "POST", "PUT", "DELETE", "HEAD");
        private List<String> allowedHeaders = Arrays.asList("*");
        private List<String> exposeHeaders = Arrays.asList("ETag");
        private int maxAgeSeconds = 3600;
    }

    @Data
    public static class StorageClasses {
        private String defaultClass = "STANDARD";
        private String infrequentAccessClass = "STANDARD_IA";
        private String archiveClass = "GLACIER";
        private String deepArchiveClass = "DEEP_ARCHIVE";
    }

    @Data
    public static class LifecycleConfig {
        private boolean enabled = false;
        private int transitionToIADays = 30;
        private int transitionToGlacierDays = 90;
        private int transitionToDeepArchiveDays = 180;
        private int deleteAfterDays = 365;
        private boolean deleteIncompleteMultipartUploads = true;
        private int incompleteMultipartDays = 7;
    }

    /**
     * S3 Bucket Manager for bucket operations
     */
    public static class S3BucketManager {

        private final S3Client s3Client;
        private final S3Config config;

        public S3BucketManager(S3Client s3Client, S3Config config) {
            this.s3Client = s3Client;
            this.config = config;
        }

        @PostConstruct
        public void initializeBucket() {
            if (config.isCreateBucketOnStartup()) {
                createBucketIfNotExists();
                configureCors();
                configureLifecycle();
            }
        }

        public void createBucketIfNotExists() {
            if (bucketExists()) {
                log.info("S3 bucket {} already exists", config.getBucket());
                return;
            }

            log.info("Creating S3 bucket: {}", config.getBucket());
            
            try {
                CreateBucketRequest.Builder requestBuilder = CreateBucketRequest.builder()
                        .bucket(config.getBucket());

                // Set bucket location constraint if not in us-east-1
                if (!"us-east-1".equals(config.getRegion())) {
                    requestBuilder.createBucketConfiguration(
                        CreateBucketConfiguration.builder()
                            .locationConstraint(BucketLocationConstraint.fromValue(config.getRegion()))
                            .build()
                    );
                }

                s3Client.createBucket(requestBuilder.build());
                
                // Set bucket versioning
                s3Client.putBucketVersioning(
                    PutBucketVersioningRequest.builder()
                        .bucket(config.getBucket())
                        .versioningConfiguration(
                            VersioningConfiguration.builder()
                                .status(BucketVersioningStatus.ENABLED)
                                .build()
                        )
                        .build()
                );

                // Set bucket encryption
                s3Client.putBucketEncryption(
                    PutBucketEncryptionRequest.builder()
                        .bucket(config.getBucket())
                        .serverSideEncryptionConfiguration(
                            ServerSideEncryptionConfiguration.builder()
                                .rules(
                                    ServerSideEncryptionRule.builder()
                                        .applyServerSideEncryptionByDefault(
                                            ServerSideEncryptionByDefault.builder()
                                                .sseAlgorithm(ServerSideEncryption.AES256)
                                                .build()
                                        )
                                        .build()
                                )
                                .build()
                        )
                        .build()
                );

                log.info("S3 bucket {} created successfully", config.getBucket());
                
            } catch (Exception e) {
                log.error("Error creating S3 bucket {}: {}", config.getBucket(), e.getMessage());
                throw new RuntimeException("Failed to create S3 bucket", e);
            }
        }

        public void configureCors() {
            if (!config.getCors().isEnabled()) {
                return;
            }

            log.info("Configuring CORS for S3 bucket: {}", config.getBucket());
            
            CORSRule corsRule = CORSRule.builder()
                    .allowedOrigins(config.getCors().getAllowedOrigins())
                    .allowedMethods(config.getCors().getAllowedMethods())
                    .allowedHeaders(config.getCors().getAllowedHeaders())
                    .exposeHeaders(config.getCors().getExposeHeaders())
                    .maxAgeSeconds(config.getCors().getMaxAgeSeconds())
                    .build();

            CORSConfiguration corsConfiguration = CORSConfiguration.builder()
                    .corsRules(corsRule)
                    .build();

            s3Client.putBucketCors(
                PutBucketCorsRequest.builder()
                    .bucket(config.getBucket())
                    .corsConfiguration(corsConfiguration)
                    .build()
            );

            log.info("CORS configuration applied to S3 bucket: {}", config.getBucket());
        }

        public void configureLifecycle() {
            if (!config.getLifecycle().isEnabled()) {
                return;
            }

            log.info("Configuring lifecycle policy for S3 bucket: {}", config.getBucket());

            LifecycleRule.Builder ruleBuilder = LifecycleRule.builder()
                    .id("oddiya-lifecycle-rule")
                    .status(ExpirationStatus.ENABLED)
                    .filter(LifecycleRuleFilter.builder().prefix("").build());

            // Add transitions
            if (config.getLifecycle().getTransitionToIADays() > 0) {
                ruleBuilder.transitions(
                    Transition.builder()
                        .days(config.getLifecycle().getTransitionToIADays())
                        .storageClass(TransitionStorageClass.STANDARD_IA)
                        .build()
                );
            }

            if (config.getLifecycle().getTransitionToGlacierDays() > 0) {
                ruleBuilder.transitions(
                    Transition.builder()
                        .days(config.getLifecycle().getTransitionToGlacierDays())
                        .storageClass(TransitionStorageClass.GLACIER)
                        .build()
                );
            }

            if (config.getLifecycle().getTransitionToDeepArchiveDays() > 0) {
                ruleBuilder.transitions(
                    Transition.builder()
                        .days(config.getLifecycle().getTransitionToDeepArchiveDays())
                        .storageClass(TransitionStorageClass.DEEP_ARCHIVE)
                        .build()
                );
            }

            // Add expiration
            if (config.getLifecycle().getDeleteAfterDays() > 0) {
                ruleBuilder.expiration(
                    LifecycleExpiration.builder()
                        .days(config.getLifecycle().getDeleteAfterDays())
                        .build()
                );
            }

            // Add incomplete multipart upload cleanup
            if (config.getLifecycle().isDeleteIncompleteMultipartUploads()) {
                ruleBuilder.abortIncompleteMultipartUpload(
                    AbortIncompleteMultipartUpload.builder()
                        .daysAfterInitiation(config.getLifecycle().getIncompleteMultipartDays())
                        .build()
                );
            }

            BucketLifecycleConfiguration lifecycleConfiguration = BucketLifecycleConfiguration.builder()
                    .rules(ruleBuilder.build())
                    .build();

            s3Client.putBucketLifecycleConfiguration(
                PutBucketLifecycleConfigurationRequest.builder()
                    .bucket(config.getBucket())
                    .lifecycleConfiguration(lifecycleConfiguration)
                    .build()
            );

            log.info("Lifecycle configuration applied to S3 bucket: {}", config.getBucket());
        }

        private boolean bucketExists() {
            try {
                s3Client.headBucket(HeadBucketRequest.builder().bucket(config.getBucket()).build());
                return true;
            } catch (NoSuchBucketException e) {
                return false;
            } catch (Exception e) {
                log.warn("Error checking if bucket {} exists: {}", config.getBucket(), e.getMessage());
                return false;
            }
        }
    }

    /**
     * S3 File Manager for file operations
     */
    public static class S3FileManager {

        private final S3Client s3Client;
        private final S3Presigner s3Presigner;
        private final S3Config config;

        public S3FileManager(S3Client s3Client, S3Presigner s3Presigner, S3Config config) {
            this.s3Client = s3Client;
            this.s3Presigner = s3Presigner;
            this.config = config;
        }

        public String generatePresignedUploadUrl(String key) {
            return generatePresignedUploadUrl(key, config.getPresignedUrlExpiration());
        }

        public String generatePresignedUploadUrl(String key, int expirationSeconds) {
            log.debug("Generating presigned upload URL for key: {}", key);
            
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(config.getBucket())
                    .key(key)
                    .build();

            PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofSeconds(expirationSeconds))
                    .putObjectRequest(putObjectRequest)
                    .build();

            return s3Presigner.presignPutObject(presignRequest).url().toString();
        }

        public String generatePresignedDownloadUrl(String key) {
            return generatePresignedDownloadUrl(key, config.getPresignedUrlExpiration());
        }

        public String generatePresignedDownloadUrl(String key, int expirationSeconds) {
            log.debug("Generating presigned download URL for key: {}", key);
            
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(config.getBucket())
                    .key(key)
                    .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofSeconds(expirationSeconds))
                    .getObjectRequest(getObjectRequest)
                    .build();

            return s3Presigner.presignGetObject(presignRequest).url().toString();
        }

        public void deleteFile(String key) {
            log.info("Deleting file from S3: {}", key);
            
            s3Client.deleteObject(
                DeleteObjectRequest.builder()
                    .bucket(config.getBucket())
                    .key(key)
                    .build()
            );
        }

        public void copyFile(String sourceKey, String destinationKey) {
            log.info("Copying file in S3 from {} to {}", sourceKey, destinationKey);
            
            s3Client.copyObject(
                CopyObjectRequest.builder()
                    .sourceBucket(config.getBucket())
                    .sourceKey(sourceKey)
                    .destinationBucket(config.getBucket())
                    .destinationKey(destinationKey)
                    .build()
            );
        }

        public boolean fileExists(String key) {
            try {
                s3Client.headObject(
                    HeadObjectRequest.builder()
                        .bucket(config.getBucket())
                        .key(key)
                        .build()
                );
                return true;
            } catch (NoSuchKeyException e) {
                return false;
            } catch (Exception e) {
                log.warn("Error checking if file {} exists: {}", key, e.getMessage());
                return false;
            }
        }

        public long getFileSize(String key) {
            try {
                HeadObjectResponse response = s3Client.headObject(
                    HeadObjectRequest.builder()
                        .bucket(config.getBucket())
                        .key(key)
                        .build()
                );
                return response.contentLength();
            } catch (Exception e) {
                log.error("Error getting file size for {}: {}", key, e.getMessage());
                return -1;
            }
        }
    }
}

/**
 * Mock S3 configuration for local development and testing
 */
@Configuration
@Profile({"local", "test", "h2"})
@Slf4j
class MockS3Config {

    @Bean
    @ConditionalOnProperty(name = "app.aws.mock.enabled", havingValue = "true", matchIfMissing = true)
    public MockS3FileManager mockS3FileManager() {
        log.info("Creating mock S3 file manager for local development");
        return new MockS3FileManager();
    }

    public static class MockS3FileManager {

        public String generatePresignedUploadUrl(String key) {
            log.debug("Mock: Generating presigned upload URL for key: {}", key);
            return "http://localhost:9000/mock-bucket/" + key + "?upload=true";
        }

        public String generatePresignedDownloadUrl(String key) {
            log.debug("Mock: Generating presigned download URL for key: {}", key);
            return "http://localhost:9000/mock-bucket/" + key;
        }

        public void deleteFile(String key) {
            log.info("Mock: Deleting file: {}", key);
        }

        public void copyFile(String sourceKey, String destinationKey) {
            log.info("Mock: Copying file from {} to {}", sourceKey, destinationKey);
        }

        public boolean fileExists(String key) {
            log.debug("Mock: Checking if file exists: {}", key);
            return key.contains("existing");
        }

        public long getFileSize(String key) {
            log.debug("Mock: Getting file size for: {}", key);
            return 1024L; // Mock size
        }
    }
}