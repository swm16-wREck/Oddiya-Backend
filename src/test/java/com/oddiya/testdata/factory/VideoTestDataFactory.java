package com.oddiya.testdata.factory;

import com.oddiya.entity.TravelPlan;
import com.oddiya.entity.User;
import com.oddiya.entity.Video;
import com.oddiya.entity.VideoStatus;
import net.datafaker.Faker;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Test data factory for Video entities using Builder pattern
 * Generates realistic Korean travel video data
 */
public class VideoTestDataFactory {
    
    private final Faker faker = new Faker(Locale.KOREAN);
    private final Random random = new Random(12345);
    
    /**
     * Create a single video
     */
    public Video createVideo(User user) {
        return createVideoBuilder(user).build();
    }
    
    /**
     * Create video with travel plan
     */
    public Video createVideo(User user, TravelPlan travelPlan) {
        return createVideoBuilder(user)
            .travelPlan(travelPlan)
            .title(generateTravelPlanVideoTitle(travelPlan))
            .description(generateTravelPlanVideoDescription(travelPlan))
            .build();
    }
    
    /**
     * Create multiple videos
     */
    public List<Video> createVideos(int count, List<User> users) {
        List<Video> videos = new ArrayList<>();
        
        for (int i = 0; i < count; i++) {
            User user = users.get(random.nextInt(users.size()));
            videos.add(createVideoBuilder(user).build());
        }
        
        return videos;
    }
    
    /**
     * Create Korean travel videos with authentic content
     */
    public List<Video> createKoreanTravelVideos(List<User> users, int count) {
        List<Video> videos = new ArrayList<>();
        
        for (int i = 0; i < count; i++) {
            User user = users.get(random.nextInt(users.size()));
            videos.add(createKoreanTravelVideo(user));
        }
        
        return videos;
    }
    
    /**
     * Create popular videos (high view count)
     */
    public List<Video> createPopularVideos(List<User> users, int count) {
        List<Video> videos = new ArrayList<>();
        
        for (int i = 0; i < count; i++) {
            User user = users.get(random.nextInt(users.size()));
            
            videos.add(createVideoBuilder(user)
                .viewCount(10000L + random.nextLong(90000)) // 10k-100k views
                .likeCount(500L + random.nextLong(4500)) // 500-5k likes
                .isPublic(true)
                .status(VideoStatus.PUBLISHED)
                .build());
        }
        
        return videos;
    }
    
    /**
     * Create video builder with randomized data
     */
    public Video.VideoBuilder createVideoBuilder(User user) {
        return Video.builder()
            .user(user)
            .title(generateVideoTitle())
            .description(generateVideoDescription())
            .videoUrl(generateVideoUrl())
            .thumbnailUrl(generateThumbnailUrl())
            .duration(generateDuration())
            .status(generateVideoStatus())
            .isPublic(random.nextBoolean())
            .tags(generateVideoTags())
            .viewCount(random.nextLong(10000))
            .likeCount(random.nextLong(500))
            .shareCount(random.nextLong(100))
            .createdAt(generateCreatedDate())
            .updatedAt(LocalDateTime.now());
    }
    
    /**
     * Create Korean travel video with authentic content
     */
    private Video createKoreanTravelVideo(User user) {
        String[] videoTypes = {"vlog", "guide", "review", "culture", "food"};
        String videoType = videoTypes[random.nextInt(videoTypes.length)];
        
        return createVideoBuilder(user)
            .title(generateKoreanVideoTitle(videoType))
            .description(generateKoreanVideoDescription(videoType))
            .tags(generateKoreanVideoTags(videoType))
            .build();
    }
    
    private String generateVideoTitle() {
        return generateKoreanVideoTitle("general");
    }
    
    private String generateKoreanVideoTitle(String videoType) {
        Map<String, String[]> titles = Map.of(
            "vlog", new String[]{
                "서울 첫 여행 브이로그 🇰🇷",
                "부산 2박 3일 혼자 여행 일상",
                "제주도 힐링 여행 브이로그",
                "강릉 바다 여행 일기",
                "전주 한옥마을 체험기"
            },
            "guide", new String[]{
                "서울 필수 관광지 TOP 10",
                "부산 맛집 완전정복 가이드",
                "제주도 숨은 명소 총정리",
                "경주 역사 여행 코스 추천",
                "인천 차이나타운 완벽 가이드"
            },
            "review", new String[]{
                "이 호텔 진짜 추천해요! 솔직 후기",
                "서울 미슐랭 맛집 먹방 후기",
                "부산 펜션 리뷰 (장단점 솔직하게)",
                "제주도 렌터카 후기와 팁",
                "경주 게스트하우스 솔직 리뷰"
            },
            "culture", new String[]{
                "한국 전통문화 체험기",
                "K-pop 성지순례 투어",
                "한복 입고 궁궐 나들이",
                "한국 사찰 템플스테이 체험",
                "전통시장에서 만난 한국의 맛"
            },
            "food", new String[]{
                "서울 길거리 음식 도전기",
                "부산 해산물 먹방 투어",
                "전주 비빔밥의 진짜 맛집",
                "제주 흑돼지 맛집 찾기",
                "한국 치킨 순위 매기기"
            }
        );
        
        String[] typeSpecificTitles = titles.get(videoType);
        if (typeSpecificTitles != null) {
            return typeSpecificTitles[random.nextInt(typeSpecificTitles.length)];
        }
        
        // Default titles
        String[] generalTitles = {
            "한국 여행 이야기",
            "여행 일상 브이로그",
            "한국 문화 체험기",
            "맛집 탐방 기록",
            "여행 꿀팁 공유"
        };
        
        return generalTitles[random.nextInt(generalTitles.length)];
    }
    
    private String generateVideoDescription() {
        return generateKoreanVideoDescription("general");
    }
    
    private String generateKoreanVideoDescription(String videoType) {
        Map<String, String[]> descriptions = Map.of(
            "vlog", new String[]{
                "안녕하세요! 이번에 처음으로 한국 여행을 다녀왔는데, 너무 좋은 경험이었어서 여러분과 공유하고 싶어요. 맛있는 음식도 많이 먹고, 아름다운 곳도 많이 가봤답니다!",
                "혼자 떠난 부산 여행기를 브이로그로 담아봤어요. 바다도 보고, 맛있는 해산물도 먹고, 정말 힐링되는 시간이었습니다. 여러분도 꼭 가보세요!",
                "제주도의 아름다운 자연을 만끽한 여행 기록입니다. 올레길 걷기, 맛집 탐방, 카페 투어까지 알찬 일정이었어요!"
            },
            "guide", new String[]{
                "한국 여행 처음이신가요? 이 영상 하나면 서울의 모든 필수 관광지를 다 알 수 있어요! 교통편부터 입장료, 운영시간까지 상세하게 정리했습니다.",
                "부산 현지인이 추천하는 진짜 맛집들만 골라서 소개해드려요. 관광지 맛집이 아닌, 부산 사람들이 진짜 가는 곳들입니다!",
                "제주도 여행 계획 중이시라면 이 영상 필수 시청! 렌터카 코스부터 숨은 명소까지 제주도 여행의 모든 것을 담았습니다."
            },
            "review", new String[]{
                "솔직한 후기로 유명한 제가 이번에 머물렀던 호텔을 정말 솔직하게 리뷰해드려요. 장점과 단점을 숨김없이 공개합니다!",
                "화제의 미슐랭 맛집에 직접 다녀왔어요! 정말 그 값어치를 하는지, 맛과 서비스는 어땠는지 솔직한 후기입니다.",
                "제주도에서 렌터카 이용해보신 분들 많으실 텐데, 제가 이용했던 업체의 장단점을 솔직하게 말씀드릴게요."
            }
        );
        
        String[] typeDescriptions = descriptions.get(videoType);
        if (typeDescriptions != null) {
            return typeDescriptions[random.nextInt(typeDescriptions.length)];
        }
        
        return "한국 여행의 아름다운 순간들을 기록한 영상입니다. 여러분의 여행 계획에 도움이 되었으면 좋겠어요!";
    }
    
    private String generateVideoUrl() {
        String[] platforms = {"youtube", "vimeo", "dailymotion"};
        String platform = platforms[random.nextInt(platforms.length)];
        String videoId = faker.random().hex(11);
        
        return switch (platform) {
            case "youtube" -> "https://www.youtube.com/watch?v=" + videoId;
            case "vimeo" -> "https://vimeo.com/" + random.nextInt(999999999);
            case "dailymotion" -> "https://www.dailymotion.com/video/" + videoId;
            default -> "https://example.com/videos/" + videoId;
        };
    }
    
    private String generateThumbnailUrl() {
        return "https://example.com/thumbnails/" + faker.random().hex(10) + ".jpg";
    }
    
    private Integer generateDuration() {
        // Duration in seconds: 30 seconds to 30 minutes
        double rand = random.nextDouble();
        if (rand < 0.3) {
            return 30 + random.nextInt(270); // 30s - 5min (short videos)
        } else if (rand < 0.7) {
            return 300 + random.nextInt(600); // 5min - 15min (medium videos)
        } else {
            return 900 + random.nextInt(900); // 15min - 30min (long videos)
        }
    }
    
    private VideoStatus generateVideoStatus() {
        VideoStatus[] statuses = VideoStatus.values();
        double rand = random.nextDouble();
        
        if (rand < 0.05) return VideoStatus.DRAFT;
        else if (rand < 0.15) return VideoStatus.PROCESSING;
        else if (rand < 0.85) return VideoStatus.PUBLISHED;
        else if (rand < 0.95) return VideoStatus.ARCHIVED;
        else return VideoStatus.DELETED;
    }
    
    private List<String> generateVideoTags() {
        return generateKoreanVideoTags("general");
    }
    
    private List<String> generateKoreanVideoTags(String videoType) {
        Map<String, String[]> tagSets = Map.of(
            "vlog", new String[]{"브이로그", "일상", "여행일기", "힐링", "여행브이로그"},
            "guide", new String[]{"가이드", "팁", "정보", "추천", "코스", "여행정보"},
            "review", new String[]{"리뷰", "후기", "솔직후기", "체험", "추천"},
            "culture", new String[]{"문화", "전통", "체험", "한국문화", "역사"},
            "food", new String[]{"먹방", "맛집", "음식", "현지음식", "미식"},
            "general", new String[]{"여행", "한국", "관광", "체험", "문화", "음식"}
        );
        
        String[] availableTags = tagSets.getOrDefault(videoType, tagSets.get("general"));
        
        List<String> tags = new ArrayList<>();
        int tagCount = 3 + random.nextInt(3); // 3-5 tags
        
        for (int i = 0; i < tagCount; i++) {
            String tag = availableTags[random.nextInt(availableTags.length)];
            if (!tags.contains(tag)) {
                tags.add(tag);
            }
        }
        
        return tags;
    }
    
    private String generateTravelPlanVideoTitle(TravelPlan travelPlan) {
        return travelPlan.getTitle() + " - 여행 영상";
    }
    
    private String generateTravelPlanVideoDescription(TravelPlan travelPlan) {
        return "'" + travelPlan.getTitle() + "' 여행의 아름다운 순간들을 영상으로 기록했습니다. " +
               travelPlan.getDescription() + " 함께 떠나보세요!";
    }
    
    private LocalDateTime generateCreatedDate() {
        // Generate dates within the last year
        long minDay = LocalDateTime.now().minusYears(1).toLocalDate().toEpochDay();
        long maxDay = LocalDateTime.now().toLocalDate().toEpochDay();
        long randomDay = ThreadLocalRandom.current().nextLong(minDay, maxDay);
        return java.time.LocalDate.ofEpochDay(randomDay).atTime(
            random.nextInt(24), random.nextInt(60)
        );
    }
}