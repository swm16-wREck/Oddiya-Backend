package com.oddiya.testdata.factory;

import com.oddiya.entity.Place;
import com.oddiya.entity.TravelPlan;
import com.oddiya.entity.TravelPlanStatus;
import com.oddiya.entity.User;
import com.oddiya.testdata.data.KoreanLocationData;
import net.datafaker.Faker;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Test data factory for TravelPlan entities using Builder pattern
 * Generates realistic Korean travel scenarios including solo, family, and business trips
 */
public class TravelPlanTestDataFactory {
    
    private final Faker faker = new Faker(Locale.KOREAN);
    private final KoreanLocationData locationData = new KoreanLocationData();
    private final Random random = new Random(12345);
    
    /**
     * Create a single travel plan with default settings
     */
    public TravelPlan createTravelPlan(User user, List<Place> places) {
        return createTravelPlanBuilder(user, places).build();
    }
    
    /**
     * Create multiple travel plans
     */
    public List<TravelPlan> createTravelPlans(int count, List<User> users, List<Place> places) {
        List<TravelPlan> travelPlans = new ArrayList<>();
        
        for (int i = 0; i < count; i++) {
            User user = users.get(random.nextInt(users.size()));
            travelPlans.add(createTravelPlanBuilder(user, places).build());
        }
        
        return travelPlans;
    }
    
    /**
     * Create specific Seoul travel scenarios
     */
    public List<TravelPlan> createSeoulTravelScenarios(List<User> users, List<KoreanLocationData.LocationData> seoulPlaces) {
        List<TravelPlan> scenarios = new ArrayList<>();
        
        // Seoul Weekend Getaway (Solo)
        scenarios.add(TravelPlan.builder()
            .user(users.get(0))
            .title("서울 주말 힐링 여행")
            .description("혼자만의 시간을 즐기는 서울 시내 힐링 여행 코스입니다.")
            .destination("서울")
            .startDate(LocalDate.now().plusDays(14))
            .endDate(LocalDate.now().plusDays(16))
            .numberOfPeople(1)
            .budget(BigDecimal.valueOf(300000))
            .status(TravelPlanStatus.CONFIRMED)
            .isPublic(true)
            .preferences(Map.of(
                "여행스타일", "여유로운",
                "교통수단", "대중교통",
                "관심분야", "문화"
            ))
            .tags(Arrays.asList("솔로여행", "힐링", "서울", "주말"))
            .createdAt(LocalDateTime.now().minusDays(7))
            .updatedAt(LocalDateTime.now())
            .build());
        
        // Seoul Family Trip (3 days)
        scenarios.add(TravelPlan.builder()
            .user(users.get(1))
            .title("서울 가족여행 3박 4일")
            .description("아이들과 함께하는 서울의 주요 관광지 탐방과 체험 활동")
            .destination("서울")
            .startDate(LocalDate.now().plusDays(30))
            .endDate(LocalDate.now().plusDays(33))
            .numberOfPeople(4)
            .budget(BigDecimal.valueOf(800000))
            .status(TravelPlanStatus.DRAFT)
            .isPublic(false)
            .preferences(Map.of(
                "여행스타일", "가족친화",
                "교통수단", "택시",
                "관심분야", "체험"
            ))
            .tags(Arrays.asList("가족여행", "아이들", "체험", "관광"))
            .createdAt(LocalDateTime.now().minusDays(3))
            .updatedAt(LocalDateTime.now())
            .build());
        
        // Seoul Business Trip Extended
        scenarios.add(TravelPlan.builder()
            .user(users.get(2))
            .title("서울 출장 + 개인여행")
            .description("출장 일정을 마친 후 서울의 문화와 맛집을 탐방하는 여행")
            .destination("서울")
            .startDate(LocalDate.now().plusDays(21))
            .endDate(LocalDate.now().plusDays(25))
            .numberOfPeople(1)
            .budget(BigDecimal.valueOf(500000))
            .status(TravelPlanStatus.CONFIRMED)
            .isPublic(true)
            .preferences(Map.of(
                "여행스타일", "비즈니스",
                "교통수단", "대중교통",
                "관심분야", "음식"
            ))
            .tags(Arrays.asList("출장", "비즈니스", "맛집", "문화"))
            .createdAt(LocalDateTime.now().minusDays(10))
            .updatedAt(LocalDateTime.now())
            .build());
        
        // Seoul K-pop Tour
        scenarios.add(TravelPlan.builder()
            .user(users.get(3))
            .title("서울 K-pop 성지순례")
            .description("K-pop 팬들을 위한 서울의 K-pop 관련 명소와 카페 투어")
            .destination("서울")
            .startDate(LocalDate.now().plusDays(45))
            .endDate(LocalDate.now().plusDays(47))
            .numberOfPeople(3)
            .budget(BigDecimal.valueOf(450000))
            .status(TravelPlanStatus.PLANNING)
            .isPublic(true)
            .preferences(Map.of(
                "여행스타일", "문화탐방",
                "교통수단", "대중교통",
                "관심분야", "K-pop"
            ))
            .tags(Arrays.asList("K-pop", "한류", "성지순례", "친구"))
            .createdAt(LocalDateTime.now().minusDays(5))
            .updatedAt(LocalDateTime.now())
            .build());
        
        // Seoul Traditional Culture Tour
        scenarios.add(TravelPlan.builder()
            .user(users.get(4))
            .title("서울 전통문화 체험")
            .description("궁궐과 한옥마을을 중심으로 한 전통 문화 체험 여행")
            .destination("서울")
            .startDate(LocalDate.now().plusDays(60))
            .endDate(LocalDate.now().plusDays(62))
            .numberOfPeople(2)
            .budget(BigDecimal.valueOf(350000))
            .status(TravelPlanStatus.DRAFT)
            .isPublic(false)
            .preferences(Map.of(
                "여행스타일", "문화탐방",
                "교통수단", "대중교통",
                "관심분야", "역사문화"
            ))
            .tags(Arrays.asList("전통문화", "궁궐", "한옥", "체험"))
            .createdAt(LocalDateTime.now().minusDays(2))
            .updatedAt(LocalDateTime.now())
            .build());
        
        return scenarios;
    }
    
    /**
     * Create travel plans for different personas
     */
    public List<TravelPlan> createPersonaTravelPlans(List<User> users, List<Place> places) {
        List<TravelPlan> plans = new ArrayList<>();
        
        for (User user : users) {
            String travelStyle = user.getTravelPreferences().get("여행스타일");
            String companionType = user.getTravelPreferences().get("동행타입");
            
            plans.add(createTravelPlanForPersona(user, places, travelStyle, companionType));
        }
        
        return plans;
    }
    
    /**
     * Create travel plan builder with randomized Korean data
     */
    public TravelPlan.TravelPlanBuilder<?, ?> createTravelPlanBuilder(User user, List<Place> places) {
        LocalDate startDate = generateRandomFutureDate();
        LocalDate endDate = startDate.plusDays(generateTripDuration());
        
        return TravelPlan.builder()
            .user(user)
            .title(generateTravelTitle())
            .description(generateTravelDescription())
            .destination(generateDestination())
            .startDate(startDate)
            .endDate(endDate)
            .numberOfPeople(generateNumberOfPeople())
            .budget(generateBudget())
            .status(generateTravelPlanStatus())
            .isPublic(random.nextBoolean())
            .isAiGenerated(random.nextDouble() < 0.3) // 30% AI generated
            .preferences(generateTravelPlanPreferences())
            .tags(generateTravelTags())
            .viewCount(random.nextLong(1000))
            .likeCount(random.nextLong(100))
            .shareCount(random.nextLong(50))
            .saveCount(random.nextLong(200))
            .coverImageUrl(faker.internet().url())
            .createdAt(generateCreatedDate())
            .updatedAt(LocalDateTime.now());
    }
    
    /**
     * Create budget-conscious travel plan
     */
    public TravelPlan createBudgetTravelPlan(User user, List<Place> places) {
        return createTravelPlanBuilder(user, places)
            .budget(BigDecimal.valueOf(100000 + random.nextInt(200000))) // 100k-300k budget
            .preferences(Map.of(
                "예산수준", "경제적",
                "숙박선호", "게스트하우스",
                "교통수단", "대중교통"
            ))
            .tags(Arrays.asList("가성비", "백패커", "경제여행"))
            .build();
    }
    
    /**
     * Create luxury travel plan
     */
    public TravelPlan createLuxuryTravelPlan(User user, List<Place> places) {
        return createTravelPlanBuilder(user, places)
            .budget(BigDecimal.valueOf(1000000 + random.nextInt(2000000))) // 1M-3M budget
            .preferences(Map.of(
                "예산수준", "프리미엄",
                "숙박선호", "럭셔리호텔",
                "교통수단", "택시"
            ))
            .tags(Arrays.asList("럭셔리", "프리미엄", "고급"))
            .build();
    }
    
    private TravelPlan createTravelPlanForPersona(User user, List<Place> places, String travelStyle, String companionType) {
        TravelPlan.TravelPlanBuilder<?, ?> builder = createTravelPlanBuilder(user, places);
        
        // Adjust based on travel style
        switch (travelStyle) {
            case "모험적":
                builder.title("모험과 스릴이 가득한 여행")
                    .tags(Arrays.asList("모험", "액티비티", "스릴", "체험"));
                break;
            case "여유로운":
                builder.title("여유롭고 편안한 힐링 여행")
                    .tags(Arrays.asList("힐링", "여유", "휴식", "평화"));
                break;
            case "문화탐방":
                builder.title("문화와 역사를 탐방하는 여행")
                    .tags(Arrays.asList("문화", "역사", "박물관", "전통"));
                break;
            case "맛집투어":
                builder.title("현지 맛집을 찾아가는 미식 여행")
                    .tags(Arrays.asList("맛집", "미식", "현지음식", "요리"));
                break;
        }
        
        // Adjust based on companion type
        switch (companionType) {
            case "솔로":
                builder.numberOfPeople(1)
                    .tags(Arrays.asList("솔로여행", "혼자", "자유", "힐링"));
                break;
            case "커플":
                builder.numberOfPeople(2)
                    .tags(Arrays.asList("커플여행", "로맨틱", "데이트", "추억"));
                break;
            case "가족":
                builder.numberOfPeople(3 + random.nextInt(3))
                    .tags(Arrays.asList("가족여행", "아이들", "체험", "안전"));
                break;
            case "친구":
                builder.numberOfPeople(2 + random.nextInt(4))
                    .tags(Arrays.asList("친구여행", "우정", "재미", "모험"));
                break;
        }
        
        return builder.build();
    }
    
    private String generateTravelTitle() {
        String[] prefixes = {"설레는", "완벽한", "특별한", "잊지못할", "환상적인", "감동적인", "로맨틱한", "평화로운"};
        String[] destinations = {"서울", "부산", "제주도", "경주", "전주", "강릉", "속초", "여수"};
        String[] suffixes = {"여행", "여행기", "투어", "나들이", "힐링", "체험", "모험", "탐방"};
        
        return prefixes[random.nextInt(prefixes.length)] + " " + 
               destinations[random.nextInt(destinations.length)] + " " +
               suffixes[random.nextInt(suffixes.length)];
    }
    
    private String generateTravelDescription() {
        String[] templates = {
            "현지인이 추천하는 숨겨진 명소들을 탐방하는 특별한 여행입니다.",
            "가족 또는 친구와 함께 즐길 수 있는 다양한 체험 활동이 포함되어 있습니다.",
            "전통과 현대가 어우러진 한국의 아름다운 모습을 만날 수 있는 코스입니다.",
            "맛있는 현지 음식과 아름다운 풍경을 함께 즐길 수 있는 완벽한 일정입니다.",
            "편안한 휴식과 힐링을 위한 여유로운 일정으로 구성되어 있습니다.",
            "사진 촬영하기 좋은 인스타그램 감성 명소들을 모은 여행 코스입니다.",
            "한국의 독특한 문화와 역사를 체험할 수 있는 교육적인 여행입니다.",
            "예산을 고려한 가성비 좋은 여행 코스로 부담없이 즐길 수 있습니다."
        };
        
        return templates[random.nextInt(templates.length)];
    }
    
    private String generateDestination() {
        String[] destinations = {
            "서울", "부산", "제주도", "인천", "대전", "대구", "울산", "세종",
            "경기도", "강원도", "충청북도", "충청남도", "전라북도", "전라남도",
            "경상북도", "경상남도", "경주", "전주", "강릉", "속초", "여수", "통영"
        };
        
        return destinations[random.nextInt(destinations.length)];
    }
    
    private LocalDate generateRandomFutureDate() {
        // Generate dates within the next 6 months
        long minDay = LocalDate.now().toEpochDay();
        long maxDay = LocalDate.now().plusMonths(6).toEpochDay();
        long randomDay = ThreadLocalRandom.current().nextLong(minDay, maxDay);
        return LocalDate.ofEpochDay(randomDay);
    }
    
    private int generateTripDuration() {
        // Most trips are 2-7 days, with bias towards weekend trips
        double rand = random.nextDouble();
        if (rand < 0.3) return 1; // Day trip
        else if (rand < 0.6) return 2; // Weekend
        else if (rand < 0.8) return 3; // Long weekend
        else if (rand < 0.95) return 4 + random.nextInt(3); // 4-6 days
        else return 7 + random.nextInt(7); // 7-13 days (longer trips)
    }
    
    private Integer generateNumberOfPeople() {
        double rand = random.nextDouble();
        if (rand < 0.25) return 1; // Solo
        else if (rand < 0.50) return 2; // Couple
        else if (rand < 0.75) return 3 + random.nextInt(2); // Small family/friends
        else return 5 + random.nextInt(10); // Large group
    }
    
    private BigDecimal generateBudget() {
        // Budget ranges from 50k to 3M KRW
        double budgetType = random.nextDouble();
        if (budgetType < 0.3) {
            // Budget travel: 50k-300k
            return BigDecimal.valueOf(50000 + random.nextInt(250000));
        } else if (budgetType < 0.7) {
            // Mid-range: 300k-800k
            return BigDecimal.valueOf(300000 + random.nextInt(500000));
        } else if (budgetType < 0.9) {
            // High-end: 800k-1.5M
            return BigDecimal.valueOf(800000 + random.nextInt(700000));
        } else {
            // Luxury: 1.5M-3M
            return BigDecimal.valueOf(1500000 + random.nextInt(1500000));
        }
    }
    
    private TravelPlanStatus generateTravelPlanStatus() {
        TravelPlanStatus[] statuses = TravelPlanStatus.values();
        double rand = random.nextDouble();
        
        if (rand < 0.4) return TravelPlanStatus.DRAFT;
        else if (rand < 0.6) return TravelPlanStatus.PLANNING;
        else if (rand < 0.85) return TravelPlanStatus.CONFIRMED;
        else if (rand < 0.95) return TravelPlanStatus.COMPLETED;
        else return TravelPlanStatus.CANCELLED;
    }
    
    private Map<String, String> generateTravelPlanPreferences() {
        Map<String, String> prefs = new HashMap<>();
        Map<String, List<String>> preferences = locationData.getTravelPreferences();
        
        // Select random preferences
        for (Map.Entry<String, List<String>> entry : preferences.entrySet()) {
            if (random.nextDouble() < 0.7) { // 70% chance to have each preference
                List<String> options = entry.getValue();
                prefs.put(entry.getKey(), options.get(random.nextInt(options.size())));
            }
        }
        
        return prefs;
    }
    
    private List<String> generateTravelTags() {
        String[] allTags = {
            "가족여행", "솔로여행", "커플여행", "친구여행", "맛집", "힐링", "문화",
            "자연", "모험", "여유", "사진", "체험", "전통", "현대", "쇼핑", "휴식",
            "가성비", "럭셔리", "로맨틱", "액티비티", "관광", "탐방", "투어"
        };
        
        List<String> tags = new ArrayList<>();
        int tagCount = 2 + random.nextInt(4); // 2-5 tags
        
        for (int i = 0; i < tagCount; i++) {
            String tag = allTags[random.nextInt(allTags.length)];
            if (!tags.contains(tag)) {
                tags.add(tag);
            }
        }
        
        return tags;
    }
    
    private LocalDateTime generateCreatedDate() {
        // Generate dates within the last 6 months
        long minDay = LocalDateTime.now().minusMonths(6).toLocalDate().toEpochDay();
        long maxDay = LocalDateTime.now().toLocalDate().toEpochDay();
        long randomDay = ThreadLocalRandom.current().nextLong(minDay, maxDay);
        return java.time.LocalDate.ofEpochDay(randomDay).atStartOfDay();
    }
}