package com.oddiya.testdata.factory;

import com.oddiya.entity.User;
import com.oddiya.testdata.data.KoreanLocationData;
import net.datafaker.Faker;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Test data factory for User entities using Builder pattern
 * Generates realistic Korean user data with diverse personas
 */
public class UserTestDataFactory {
    
    private final Faker faker = new Faker(Locale.KOREAN);
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final KoreanLocationData locationData = new KoreanLocationData();
    private final Random random = new Random(12345);
    
    /**
     * Create a single user with default settings
     */
    public User createUser() {
        return createUserBuilder().build();
    }
    
    /**
     * Create multiple users
     */
    public List<User> createUsers(int count) {
        List<User> users = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            users.add(createUserBuilder().build());
        }
        return users;
    }
    
    /**
     * Create diverse user personas with different travel preferences
     */
    public List<User> createUserPersonas() {
        List<User> personas = new ArrayList<>();
        
        // Solo Traveler - Adventure seeking young professional
        personas.add(createUserBuilder()
            .nickname("모험가민수")
            .email("adventure.minsu@email.com")
            .bio("혼자 떠나는 여행을 좋아하는 모험가입니다. 새로운 경험을 추구합니다.")
            .travelPreferences(Map.of(
                "여행스타일", "모험적",
                "숙박선호", "게스트하우스",
                "예산수준", "경제적",
                "동행타입", "솔로",
                "관심분야", "자연"
            ))
            .build());
        
        // Family Traveler - Parent with kids
        personas.add(createUserBuilder()
            .nickname("가족여행맘")
            .email("family.travel@email.com")
            .bio("아이들과 함께하는 안전하고 교육적인 여행을 계획합니다.")
            .travelPreferences(Map.of(
                "여행스타일", "여유로운",
                "숙박선호", "리조트",
                "예산수준", "중상급",
                "동행타입", "가족",
                "관심분야", "역사문화"
            ))
            .build());
        
        // Luxury Traveler - High-end experiences
        personas.add(createUserBuilder()
            .nickname("럭셔리지영")
            .email("luxury.jiyoung@email.com")
            .bio("프리미엄 서비스와 고급 여행을 선호합니다. 품격있는 여행을 추구해요.")
            .isPremium(true)
            .travelPreferences(Map.of(
                "여행스타일", "문화탐방",
                "숙박선호", "럭셔리호텔",
                "예산수준", "프리미엄",
                "동행타입", "커플",
                "관심분야", "예술"
            ))
            .build());
        
        // Foodie Traveler - Culinary focused
        personas.add(createUserBuilder()
            .nickname("맛집헌터")
            .email("foodie.hunter@email.com")
            .bio("전국 맛집을 찾아다니는 미식가입니다. 숨은 맛집 정보를 공유합니다.")
            .travelPreferences(Map.of(
                "여행스타일", "맛집투어",
                "숙박선호", "부티크호텔",
                "예산수준", "중급",
                "동행타입", "친구",
                "관심분야", "음식"
            ))
            .build());
        
        // Young Couple - Romantic getaways
        personas.add(createUserBuilder()
            .nickname("로맨틱커플")
            .email("romantic.couple@email.com")
            .bio("연인과 함께하는 로맨틱한 여행을 좋아합니다. 인스타 감성 여행지 추천해주세요!")
            .travelPreferences(Map.of(
                "여행스타일", "여유로운",
                "숙박선호", "부티크호텔",
                "예산수준", "중급",
                "동행타입", "커플",
                "관심분야", "현대문화"
            ))
            .build());
        
        // Business Traveler - Work and leisure
        personas.add(createUserBuilder()
            .nickname("출장족워리어")
            .email("business.warrior@email.com")
            .bio("출장과 여행을 결합한 효율적인 스케줄을 선호합니다.")
            .travelPreferences(Map.of(
                "여행스타일", "여유로운",
                "숙박선호", "비즈니스호텔",
                "교통수단", "대중교통",
                "예산수준", "중상급",
                "동행타입", "비즈니스"
            ))
            .build());
        
        // K-pop Fan - Cultural enthusiast
        personas.add(createUserBuilder()
            .nickname("케이팝러버")
            .email("kpop.lover@email.com")
            .bio("K-pop과 한류 문화를 사랑하는 팬입니다. 성지순례 여행을 자주 갑니다.")
            .travelPreferences(Map.of(
                "여행스타일", "문화탐방",
                "숙박선호", "부티크호텔",
                "예산수준", "중급",
                "동행타입", "친구",
                "관심분야", "K-pop"
            ))
            .build());
        
        // Traditional Culture Lover
        personas.add(createUserBuilder()
            .nickname("전통문화애호가")
            .email("traditional.culture@email.com")
            .bio("한국의 전통 문화와 역사를 체험하는 여행을 좋아합니다.")
            .travelPreferences(Map.of(
                "여행스타일", "문화탐방",
                "숙박선호", "한옥체험",
                "예산수준", "중급",
                "동행타입", "가족",
                "관심분야", "역사문화"
            ))
            .build());
        
        return personas;
    }
    
    /**
     * Create user builder with randomized Korean data
     */
    public User.UserBuilder<?, ?> createUserBuilder() {
        String koreanName = getRandomKoreanName();
        String email = generateEmail(koreanName);
        
        return User.builder()
            .email(email)
            .username(generateUsername(koreanName))
            .password(passwordEncoder.encode("password123"))
            .nickname(generateNickname())
            .profileImageUrl(faker.internet().avatar())
            .bio(generateBio())
            .provider(randomProvider())
            .providerId(faker.random().hex(16))
            .preferences(generateUserPreferences())
            .travelPreferences(generateTravelPreferences())
            .isEmailVerified(random.nextBoolean())
            .isPremium(random.nextDouble() < 0.15) // 15% premium users
            .isActive(random.nextDouble() < 0.95) // 95% active users
            .createdAt(generateCreatedDate())
            .updatedAt(LocalDateTime.now());
    }
    
    /**
     * Create user with specific travel preferences
     */
    public User createUserWithPreferences(Map<String, String> travelPrefs) {
        return createUserBuilder()
            .travelPreferences(new HashMap<>(travelPrefs))
            .build();
    }
    
    /**
     * Create premium user
     */
    public User createPremiumUser() {
        return createUserBuilder()
            .isPremium(true)
            .preferences(Map.of(
                "notifications", "premium",
                "privacy", "high",
                "recommendations", "personalized"
            ))
            .build();
    }
    
    private String getRandomKoreanName() {
        return locationData.getKoreanNames().get(random.nextInt(locationData.getKoreanNames().size()));
    }
    
    private String generateEmail(String name) {
        String[] domains = {"gmail.com", "naver.com", "kakao.com", "daum.net", "hotmail.com"};
        String username = name.replaceAll("[^a-zA-Z0-9가-힣]", "") + random.nextInt(1000);
        return username.toLowerCase() + "@" + domains[random.nextInt(domains.length)];
    }
    
    private String generateUsername(String name) {
        return name.replaceAll("[^a-zA-Z0-9가-힣]", "") + "_" + random.nextInt(10000);
    }
    
    private String generateNickname() {
        String[] prefixes = {"여행", "모험", "탐험", "문화", "맛집", "힐링", "로맨틱", "활동", "휴식", "체험"};
        String[] suffixes = {"러버", "매니아", "헌터", "마스터", "전문가", "탐험가", "애호가", "수집가", "전령", "왕"};
        return prefixes[random.nextInt(prefixes.length)] + suffixes[random.nextInt(suffixes.length)];
    }
    
    private String generateBio() {
        String[] templates = {
            "여행을 통해 새로운 경험과 추억을 만드는 것을 좋아합니다.",
            "맛집과 카페를 찾아다니며 소소한 일상의 행복을 추구합니다.",
            "한국의 아름다운 곳곳을 탐험하고 사진으로 기록합니다.",
            "가족과 함께하는 따뜻한 여행을 계획하고 있습니다.",
            "친구들과 떠나는 재미있는 여행 이야기를 나누고 싶어요.",
            "혼자만의 시간을 즐기며 힐링 여행을 추구합니다.",
            "전통 문화와 현대 문화가 어우러진 한국을 사랑합니다.",
            "새로운 사람들과의 만남을 통해 세상을 배워갑니다."
        };
        return templates[random.nextInt(templates.length)];
    }
    
    private String randomProvider() {
        String[] providers = {"google", "apple", "naver", "kakao", "local"};
        return providers[random.nextInt(providers.length)];
    }
    
    private Map<String, String> generateUserPreferences() {
        Map<String, String> prefs = new HashMap<>();
        prefs.put("language", random.nextBoolean() ? "ko" : "en");
        prefs.put("notifications", random.nextBoolean() ? "enabled" : "disabled");
        prefs.put("privacy", random.nextBoolean() ? "public" : "private");
        prefs.put("marketing", random.nextBoolean() ? "allowed" : "blocked");
        prefs.put("theme", random.nextBoolean() ? "light" : "dark");
        return prefs;
    }
    
    private Map<String, String> generateTravelPreferences() {
        Map<String, String> travelPrefs = new HashMap<>();
        Map<String, List<String>> preferences = locationData.getTravelPreferences();
        
        for (Map.Entry<String, List<String>> entry : preferences.entrySet()) {
            List<String> options = entry.getValue();
            travelPrefs.put(entry.getKey(), options.get(random.nextInt(options.size())));
        }
        
        return travelPrefs;
    }
    
    private LocalDateTime generateCreatedDate() {
        // Generate dates within the last 2 years
        long minDay = LocalDateTime.now().minusYears(2).toLocalDate().toEpochDay();
        long maxDay = LocalDateTime.now().toLocalDate().toEpochDay();
        long randomDay = ThreadLocalRandom.current().nextLong(minDay, maxDay);
        return java.time.LocalDate.ofEpochDay(randomDay).atStartOfDay();
    }
}