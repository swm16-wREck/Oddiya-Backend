package com.oddiya.aws;

import com.oddiya.config.aws.AWSConfig;
import com.oddiya.entity.dynamodb.*;
import com.oddiya.dto.message.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Test configuration for AWS services using LocalStack.
 * Provides mock AWS service setup and test data fixtures.
 */
@TestConfiguration
public class AWSTestConfiguration {

    @Value("${test.localstack.endpoint:http://localhost:4566}")
    private String localstackEndpoint;

    @Value("${aws.region:ap-northeast-2}")
    private String awsRegion;

    // Test credentials for LocalStack
    private static final String TEST_ACCESS_KEY = "test";
    private static final String TEST_SECRET_KEY = "test";

    @Bean
    @Primary
    @Profile({"dynamodb-test", "s3-test", "sqs-test"})
    public AwsCredentialsProvider testAwsCredentialsProvider() {
        return StaticCredentialsProvider.create(
            AwsBasicCredentials.create(TEST_ACCESS_KEY, TEST_SECRET_KEY)
        );
    }

    @Bean
    @Primary
    @Profile({"dynamodb-test", "s3-test", "sqs-test"})
    public Region testAwsRegion() {
        return Region.of(awsRegion);
    }

    @Bean
    @Primary
    @Profile("dynamodb-test")
    public DynamoDbClient testDynamoDbClient(Region region, AwsCredentialsProvider credentialsProvider) {
        return DynamoDbClient.builder()
                .region(region)
                .credentialsProvider(credentialsProvider)
                .endpointOverride(URI.create(localstackEndpoint))
                .build();
    }

    @Bean
    @Primary
    @Profile("dynamodb-test")
    public DynamoDbEnhancedClient testDynamoDbEnhancedClient(DynamoDbClient dynamoDbClient) {
        return DynamoDbEnhancedClient.builder()
                .dynamoDbClient(dynamoDbClient)
                .build();
    }

    @Bean
    @Primary
    @Profile("s3-test")
    public S3Client testS3Client(Region region, AwsCredentialsProvider credentialsProvider) {
        return S3Client.builder()
                .region(region)
                .credentialsProvider(credentialsProvider)
                .endpointOverride(URI.create(localstackEndpoint))
                .forcePathStyle(true) // Required for LocalStack
                .build();
    }

    @Bean
    @Primary
    @Profile("s3-test")
    public S3Presigner testS3Presigner(Region region, AwsCredentialsProvider credentialsProvider) {
        return S3Presigner.builder()
                .region(region)
                .credentialsProvider(credentialsProvider)
                .endpointOverride(URI.create(localstackEndpoint))
                .build();
    }

    @Bean
    @Primary
    @Profile("sqs-test")
    public SqsClient testSqsClient(Region region, AwsCredentialsProvider credentialsProvider) {
        return SqsClient.builder()
                .region(region)
                .credentialsProvider(credentialsProvider)
                .endpointOverride(URI.create(localstackEndpoint))
                .build();
    }

    @Bean
    @Primary
    @Profile({"dynamodb-test", "s3-test", "sqs-test"})
    public CloudWatchClient testCloudWatchClient(Region region, AwsCredentialsProvider credentialsProvider) {
        return CloudWatchClient.builder()
                .region(region)
                .credentialsProvider(credentialsProvider)
                .endpointOverride(URI.create(localstackEndpoint))
                .build();
    }

    /**
     * Test data fixtures for AWS integration tests
     */
    @TestConfiguration
    public static class TestDataFixtures {

        @Bean
        public TestDataFactory testDataFactory() {
            return new TestDataFactory();
        }
    }

    /**
     * Factory for creating test data objects
     */
    public static class TestDataFactory {

        public DynamoDBUser createTestUser(String id) {
            return DynamoDBUser.builder()
                    .id(id)
                    .username("testuser" + id)
                    .email("test" + id + "@example.com")
                    .name("Test User " + id)
                    .phoneNumber("+82-10-1234-567" + id.substring(0, 1))
                    .profileImageUrl("https://example.com/profile" + id + ".jpg")
                    .socialProvider("EMAIL")
                    .isEmailVerified(true)
                    .isActive(true)
                    .createdAt(LocalDateTime.now().minusDays(Integer.parseInt(id)))
                    .updatedAt(LocalDateTime.now())
                    .build();
        }

        public DynamoDBPlace createTestPlace(String id) {
            return DynamoDBPlace.builder()
                    .id(id)
                    .name("Test Place " + id)
                    .description("A test place for integration testing " + id)
                    .address("123 Test Street " + id + ", Seoul")
                    .latitude(37.5665 + Double.parseDouble(id) * 0.001)
                    .longitude(126.9780 + Double.parseDouble(id) * 0.001)
                    .category("restaurant")
                    .phoneNumber("+82-2-123-456" + id)
                    .website("https://testplace" + id + ".com")
                    .openingHours("09:00-22:00")
                    .averageRating(4.0 + Double.parseDouble(id) * 0.1)
                    .reviewCount(Integer.parseInt(id) * 10)
                    .images(Arrays.asList(
                        "https://example.com/image" + id + "_1.jpg",
                        "https://example.com/image" + id + "_2.jpg"
                    ))
                    .tags(Arrays.asList("test", "restaurant", "category" + id))
                    .isVerified(true)
                    .isActive(true)
                    .createdAt(LocalDateTime.now().minusDays(Integer.parseInt(id)))
                    .updatedAt(LocalDateTime.now())
                    .build();
        }

        public DynamoDBTravelPlan createTestTravelPlan(String id, String userId) {
            return DynamoDBTravelPlan.builder()
                    .id(id)
                    .userId(userId)
                    .title("Test Travel Plan " + id)
                    .description("A test travel plan for integration testing " + id)
                    .destination("Seoul, South Korea")
                    .startDate(LocalDateTime.now().plusDays(Integer.parseInt(id)))
                    .endDate(LocalDateTime.now().plusDays(Integer.parseInt(id) + 7))
                    .totalBudget(1000000L + Long.parseLong(id) * 100000)
                    .currency("KRW")
                    .participantCount(Integer.parseInt(id) % 5 + 1)
                    .status("ACTIVE")
                    .visibility("PUBLIC")
                    .tags(Arrays.asList("test", "integration", "travel" + id))
                    .isActive(true)
                    .createdAt(LocalDateTime.now().minusDays(Integer.parseInt(id)))
                    .updatedAt(LocalDateTime.now())
                    .build();
        }

        public DynamoDBReview createTestReview(String id, String userId, String placeId) {
            return DynamoDBReview.builder()
                    .id(id)
                    .userId(userId)
                    .placeId(placeId)
                    .rating(Integer.parseInt(id) % 5 + 1)
                    .title("Test Review " + id)
                    .content("This is a test review for integration testing " + id)
                    .images(Arrays.asList(
                        "https://example.com/review" + id + "_1.jpg",
                        "https://example.com/review" + id + "_2.jpg"
                    ))
                    .tags(Arrays.asList("test", "review", "rating" + (Integer.parseInt(id) % 5 + 1)))
                    .helpfulCount(Integer.parseInt(id) * 2)
                    .visitDate(LocalDateTime.now().minusDays(Integer.parseInt(id) * 2))
                    .isVerified(true)
                    .isActive(true)
                    .createdAt(LocalDateTime.now().minusDays(Integer.parseInt(id)))
                    .updatedAt(LocalDateTime.now())
                    .build();
        }

        public DynamoDBVideo createTestVideo(String id, String userId) {
            return DynamoDBVideo.builder()
                    .id(id)
                    .userId(userId)
                    .title("Test Video " + id)
                    .description("A test video for integration testing " + id)
                    .originalUrl("https://example.com/original/video" + id + ".mp4")
                    .processedUrl("https://example.com/processed/video" + id + ".mp4")
                    .thumbnailUrl("https://example.com/thumbnail/video" + id + ".jpg")
                    .duration(120L + Long.parseLong(id) * 30)
                    .fileSize(10485760L + Long.parseLong(id) * 1048576)
                    .format("mp4")
                    .resolution("1920x1080")
                    .status("COMPLETED")
                    .viewCount(Integer.parseInt(id) * 100)
                    .likeCount(Integer.parseInt(id) * 10)
                    .tags(Arrays.asList("test", "video", "integration" + id))
                    .isPublic(true)
                    .isActive(true)
                    .createdAt(LocalDateTime.now().minusDays(Integer.parseInt(id)))
                    .updatedAt(LocalDateTime.now())
                    .build();
        }

        public EmailMessage createTestEmailMessage(String id) {
            return EmailMessage.builder()
                    .id(id)
                    .to("test" + id + "@example.com")
                    .cc(Arrays.asList("cc" + id + "@example.com"))
                    .subject("Test Email " + id)
                    .body("This is a test email message for integration testing " + id)
                    .htmlBody("<p>This is a <b>test email message</b> for integration testing " + id + "</p>")
                    .attachments(Arrays.asList("attachment" + id + ".pdf"))
                    .priority("NORMAL")
                    .messageType("EMAIL")
                    .timestamp(LocalDateTime.now())
                    .metadata(Map.of(
                        "source", "integration-test",
                        "test-id", id,
                        "priority", "normal"
                    ))
                    .build();
        }

        public AnalyticsMessage createTestAnalyticsMessage(String id, String userId) {
            return AnalyticsMessage.builder()
                    .id(id)
                    .userId(userId)
                    .sessionId("session-" + id)
                    .eventName("test_event_" + id)
                    .eventCategory("integration_test")
                    .eventAction("test_action")
                    .eventLabel("test_label_" + id)
                    .properties(Map.of(
                        "page", "/test/page" + id,
                        "duration", "30",
                        "device", "desktop",
                        "browser", "chrome"
                    ))
                    .userAgent("Mozilla/5.0 (Test Browser)")
                    .ipAddress("192.168.1." + (Integer.parseInt(id) % 255))
                    .messageType("ANALYTICS")
                    .timestamp(LocalDateTime.now())
                    .build();
        }

        public RecommendationMessage createTestRecommendationMessage(String id, String userId) {
            return RecommendationMessage.builder()
                    .id(id)
                    .userId(userId)
                    .requestType("PLACE_RECOMMENDATIONS")
                    .parameters(Map.of(
                        "latitude", "37.5665",
                        "longitude", "126.9780",
                        "radius", "5000",
                        "category", "restaurant",
                        "limit", "10"
                    ))
                    .context(Map.of(
                        "current_location", "Seoul",
                        "time_of_day", "afternoon",
                        "weather", "sunny"
                    ))
                    .preferences(Map.of(
                        "cuisine", "korean",
                        "price_range", "medium",
                        "rating_min", "4.0"
                    ))
                    .messageType("RECOMMENDATION")
                    .timestamp(LocalDateTime.now())
                    .build();
        }

        public VideoProcessingMessage createTestVideoProcessingMessage(String id, String videoId) {
            return VideoProcessingMessage.builder()
                    .id(id)
                    .videoId(videoId)
                    .action("TRANSCODE")
                    .parameters(Map.of(
                        "resolution", "1080p",
                        "format", "mp4",
                        "quality", "high",
                        "bitrate", "5000"
                    ))
                    .inputUrl("https://example.com/input/video" + id + ".mov")
                    .outputUrl("https://example.com/output/video" + id + ".mp4")
                    .callbackUrl("https://api.oddiya.com/webhook/video-processing")
                    .priority("NORMAL")
                    .messageType("VIDEO_PROCESSING")
                    .timestamp(LocalDateTime.now())
                    .metadata(Map.of(
                        "source", "integration-test",
                        "test-id", id,
                        "original_format", "mov"
                    ))
                    .build();
        }

        public ImageProcessingMessage createTestImageProcessingMessage(String id, String imageId) {
            return ImageProcessingMessage.builder()
                    .id(id)
                    .imageId(imageId)
                    .action("RESIZE")
                    .parameters(Map.of(
                        "width", "800",
                        "height", "600",
                        "quality", "85",
                        "format", "jpeg"
                    ))
                    .inputUrl("https://example.com/input/image" + id + ".png")
                    .outputUrl("https://example.com/output/image" + id + ".jpg")
                    .transformations(Arrays.asList("resize", "compress", "optimize"))
                    .messageType("IMAGE_PROCESSING")
                    .timestamp(LocalDateTime.now())
                    .metadata(Map.of(
                        "source", "integration-test",
                        "test-id", id,
                        "original_format", "png"
                    ))
                    .build();
        }

        public List<DynamoDBUser> createTestUsers(int count) {
            List<DynamoDBUser> users = new ArrayList<>();
            for (int i = 1; i <= count; i++) {
                users.add(createTestUser(String.valueOf(i)));
            }
            return users;
        }

        public List<DynamoDBPlace> createTestPlaces(int count) {
            List<DynamoDBPlace> places = new ArrayList<>();
            for (int i = 1; i <= count; i++) {
                places.add(createTestPlace(String.valueOf(i)));
            }
            return places;
        }

        public Map<String, String> createTestS3Metadata(String testId) {
            return Map.of(
                "test-id", testId,
                "source", "integration-test",
                "uploaded-by", "test-user",
                "test-timestamp", LocalDateTime.now().toString(),
                "environment", "test"
            );
        }

        public byte[] createTestFileContent(String content) {
            return (content != null ? content : "Test file content for integration testing").getBytes();
        }

        public byte[] createLargeTestFileContent(int sizeInMB) {
            int size = sizeInMB * 1024 * 1024;
            byte[] content = new byte[size];
            Arrays.fill(content, (byte) 'A');
            return content;
        }
    }

    /**
     * Mock AWS service configurations for testing
     */
    @TestConfiguration
    @Profile("mock-aws")
    public static class MockAWSServiceConfiguration {

        @Bean
        @Primary
        public AWSConfig.S3Properties mockS3Properties() {
            AWSConfig.S3Properties properties = new AWSConfig.S3Properties();
            properties.setEnabled(true);
            properties.setBucket("mock-test-bucket");
            properties.setRegion("ap-northeast-2");
            properties.setPresignedUrlExpiration(3600);
            properties.setMultipartThreshold(16 * 1024 * 1024);
            return properties;
        }

        @Bean
        @Primary
        public AWSConfig.DynamoDBProperties mockDynamoDBProperties() {
            AWSConfig.DynamoDBProperties properties = new AWSConfig.DynamoDBProperties();
            properties.setEnabled(true);
            properties.setEndpoint("http://localhost:4566");
            properties.setTablePrefix("mock_test_");
            properties.setBillingMode("PAY_PER_REQUEST");
            return properties;
        }

        @Bean
        @Primary
        public AWSConfig.SQSProperties mockSQSProperties() {
            AWSConfig.SQSProperties properties = new AWSConfig.SQSProperties();
            properties.setEnabled(true);
            properties.setQueueUrl("http://localhost:4566/000000000000/mock-test-queue");
            properties.setDeadLetterQueueUrl("http://localhost:4566/000000000000/mock-test-dlq");
            properties.setMaxMessages(10);
            properties.setVisibilityTimeoutSeconds(300);
            return properties;
        }

        @Bean
        @Primary
        public AWSConfig.CloudWatchProperties mockCloudWatchProperties() {
            AWSConfig.CloudWatchProperties properties = new AWSConfig.CloudWatchProperties();
            properties.setEnabled(true);
            properties.setNamespace("MockOddiya/Test");
            properties.setBatchSize(20);
            properties.setEnableDetailedMetrics(false);
            return properties;
        }
    }
}