package com.oddiya.testdata.data;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Realistic Korean location data for test generation
 * Contains landmarks, restaurants, hotels, and cultural sites
 */
public class KoreanLocationData {
    
    // Seoul Famous Landmarks
    public static final List<LocationData> SEOUL_LANDMARKS = Arrays.asList(
        new LocationData("경복궁", "Gyeongbokgung Palace", "관광지", "서울 종로구 사직로 161", 37.5788, 126.9770, "Korean royal palace from Joseon Dynasty", Arrays.asList("역사", "궁궐", "한국문화")),
        new LocationData("남산타워", "N Seoul Tower", "관광지", "서울 용산구 남산공원길 105", 37.5512, 126.9882, "Iconic Seoul tower with panoramic city views", Arrays.asList("전망", "랜드마크", "로맨틱")),
        new LocationData("명동", "Myeongdong", "쇼핑", "서울 중구 명동", 37.5636, 126.9831, "Famous shopping and cultural district", Arrays.asList("쇼핑", "음식", "관광")),
        new LocationData("홍대", "Hongdae", "유흥", "서울 마포구 홍익로", 37.5563, 126.9236, "University area known for nightlife and indie culture", Arrays.asList("클럽", "술집", "음악")),
        new LocationData("강남", "Gangnam", "쇼핑", "서울 강남구", 37.5173, 127.0473, "Upscale district known for fashion and dining", Arrays.asList("쇼핑몰", "고급", "K-pop")),
        new LocationData("한강공원", "Han River Park", "공원", "서울 용산구 이촌로72길 62", 37.5219, 126.9923, "Beautiful riverside park for recreation", Arrays.asList("피크닉", "자전거", "휴식")),
        new LocationData("동대문디자인플라자", "DDP", "문화시설", "서울 중구 을지로 281", 37.5665, 127.0092, "Futuristic design and cultural complex", Arrays.asList("건축", "전시", "디자인")),
        new LocationData("북촌한옥마을", "Bukchon Hanok Village", "문화지구", "서울 종로구 계동길 37", 37.5814, 126.9849, "Traditional Korean village with hanok houses", Arrays.asList("전통", "한옥", "사진촬영")),
        new LocationData("이태원", "Itaewon", "국제지구", "서울 용산구 이태원로", 37.5349, 126.9947, "International district with diverse restaurants", Arrays.asList("국제음식", "외국인", "다이버시티")),
        new LocationData("청계천", "Cheonggyecheon", "하천", "서울 중구 청계천로", 37.5693, 126.9781, "Restored urban stream in downtown Seoul", Arrays.asList("산책", "도심", "물"))
    );
    
    // Seoul Restaurants
    public static final List<LocationData> SEOUL_RESTAURANTS = Arrays.asList(
        new LocationData("미슐랭 한식당", "Michelin Korean Restaurant", "한식", "서울 강남구 테헤란로", 37.5123, 127.0591, "Premium Korean fine dining experience", Arrays.asList("미슐랭", "한식", "고급")),
        new LocationData("명동교자", "Myeongdong Kyoja", "면요리", "서울 중구 명동10길 29", 37.5652, 126.9810, "Famous kalguksu and mandu restaurant", Arrays.asList("칼국수", "만두", "전통")),
        new LocationData("강남 BBQ", "Gangnam BBQ", "고기구이", "서울 강남구 강남대로", 37.5014, 127.0269, "Premium Korean BBQ with wagyu beef", Arrays.asList("한우", "바베큐", "소고기")),
        new LocationData("홍대 치킨", "Hongdae Chicken", "치킨", "서울 마포구 와우산로", 37.5538, 126.9258, "Popular late-night Korean fried chicken", Arrays.asList("치킨", "맥주", "야식")),
        new LocationData("종로 전통찻집", "Jongro Traditional Tea House", "찻집", "서울 종로구 인사동길", 37.5707, 126.9854, "Traditional Korean tea house in Insadong", Arrays.asList("전통차", "한과", "문화")),
        new LocationData("이태원 파스타", "Itaewon Italian", "이탈리안", "서울 용산구 이태원로27가길", 37.5349, 126.9928, "Authentic Italian restaurant in international district", Arrays.asList("파스타", "피자", "와인")),
        new LocationData("압구정 스시", "Apgujeong Sushi", "일식", "서울 강남구 압구정로", 37.5274, 127.0286, "High-end Japanese sushi restaurant", Arrays.asList("스시", "사시미", "오마카세")),
        new LocationData("신촌 카페", "Sinchon Specialty Coffee", "카페", "서울 서대문구 연세로", 37.5596, 126.9424, "Trendy specialty coffee shop near universities", Arrays.asList("커피", "디저트", "학생")),
        new LocationData("동대문 족발", "Dongdaemun Jokbal", "족발", "서울 중구 장충단로", 37.5638, 127.0074, "Famous pork hocks restaurant", Arrays.asList("족발", "보쌈", "소주")),
        new LocationData("서촌 브런치", "Seochon Brunch Cafe", "브런치", "서울 종로구 자하문로", 37.5858, 126.9681, "Cozy brunch cafe in historic Seochon area", Arrays.asList("브런치", "팬케이크", "에그베네딕트"))
    );
    
    // Seoul Hotels
    public static final List<LocationData> SEOUL_HOTELS = Arrays.asList(
        new LocationData("신라호텔", "The Shilla Seoul", "럭셔리호텔", "서울 중구 동호로 249", 37.5558, 127.0054, "Luxury hotel with traditional Korean hospitality", Arrays.asList("럭셔리", "전통", "서비스")),
        new LocationData("롯데호텔 명동", "Lotte Hotel Seoul", "호텔", "서울 중구 을지로 30", 37.5651, 126.9810, "Premium hotel in the heart of Seoul", Arrays.asList("명동", "쇼핑", "중심가")),
        new LocationData("강남 비즈니스 호텔", "Gangnam Business Hotel", "비즈니스호텔", "서울 강남구 테헤란로", 37.5058, 127.0436, "Modern business hotel in Gangnam district", Arrays.asList("비즈니스", "컨벤션", "강남")),
        new LocationData("홍대 부티크 호텔", "Hongdae Boutique Hotel", "부티크호텔", "서울 마포구 월드컵북로", 37.5566, 126.9235, "Stylish boutique hotel near Hongik University", Arrays.asList("부티크", "스타일", "젊음")),
        new LocationData("한옥 게스트하우스", "Traditional Hanok Guesthouse", "한옥숙박", "서울 종로구 북촌로", 37.5825, 126.9849, "Traditional Korean house accommodation", Arrays.asList("한옥", "전통", "체험")),
        new LocationData("이태원 국제호텔", "Itaewon International Hotel", "호텔", "서울 용산구 이태원로", 37.5342, 126.9953, "International hotel in multicultural district", Arrays.asList("국제", "다문화", "외국인")),
        new LocationData("남산 뷰 호텔", "Namsan View Hotel", "뷰호텔", "서울 중구 소공로", 37.5580, 126.9812, "Hotel with stunning Namsan Tower views", Arrays.asList("뷰", "남산", "전망")),
        new LocationData("동대문 패션호텔", "Dongdaemun Fashion Hotel", "패션호텔", "서울 중구 을지로", 37.5665, 127.0086, "Fashion-themed hotel near shopping districts", Arrays.asList("패션", "쇼핑", "트렌드")),
        new LocationData("압구정 럭셔리 스위트", "Apgujeong Luxury Suite", "스위트", "서울 강남구 압구정로", 37.5269, 127.0276, "Luxury suite hotel in upscale Apgujeong", Arrays.asList("럭셔리", "스위트", "압구정")),
        new LocationData("여의도 비즈니스 센터 호텔", "Yeouido Business Center Hotel", "비즈니스호텔", "서울 영등포구 여의대로", 37.5219, 126.9245, "Business hotel in financial district", Arrays.asList("비즈니스", "금융", "여의도"))
    );
    
    // Busan Attractions
    public static final List<LocationData> BUSAN_ATTRACTIONS = Arrays.asList(
        new LocationData("해운대해수욕장", "Haeundae Beach", "해수욕장", "부산 해운대구 해운대해변로", 35.1585, 129.1603, "Famous beach resort area in Busan", Arrays.asList("바다", "해수욕", "리조트")),
        new LocationData("감천문화마을", "Gamcheon Culture Village", "문화마을", "부산 사하구 감내2로", 35.0977, 129.0108, "Colorful hillside village with art installations", Arrays.asList("예술", "컬러풀", "사진")),
        new LocationData("자갈치시장", "Jagalchi Fish Market", "전통시장", "부산 중구 자갈치해안로", 35.0966, 129.0306, "Korea's largest seafood market", Arrays.asList("해산물", "시장", "신선")),
        new LocationData("부산타워", "Busan Tower", "전망대", "부산 중구 용두산길", 35.1007, 129.0319, "Observation tower with city panorama", Arrays.asList("전망", "야경", "부산")),
        new LocationData("광안리해수욕장", "Gwangalli Beach", "해수욕장", "부산 수영구 광안해변로", 35.1532, 129.1189, "Beach with views of Gwangan Bridge", Arrays.asList("다리", "야경", "카페"))
    );
    
    // Korean Review Comments (Positive)
    public static final List<String> POSITIVE_KOREAN_REVIEWS = Arrays.asList(
        "정말 멋진 곳이에요! 사진 찍기도 좋고 분위기가 최고예요.",
        "음식이 너무 맛있고 서비스도 친절해서 또 오고 싶어요.",
        "가족과 함께 즐거운 시간을 보냈습니다. 강추합니다!",
        "인스타그램에 올리기 딱 좋은 예쁜 장소네요.",
        "전통적인 한국의 멋을 느낄 수 있는 곳입니다.",
        "데이트 코스로 완벽해요. 로맨틱한 분위기가 좋아요.",
        "친구들과 가기 좋은 핫플레이스! 젊은 분위기가 매력적이에요.",
        "한국 문화를 체험할 수 있어서 의미있는 시간이었습니다.",
        "교통이 편리하고 주변에 볼거리도 많아서 좋아요.",
        "깨끗하고 관리가 잘 되어 있어서 만족스러워요."
    );
    
    // Korean Review Comments (Negative)
    public static final List<String> NEGATIVE_KOREAN_REVIEWS = Arrays.asList(
        "기대했던 것보다는 별로였어요. 사람이 너무 많았습니다.",
        "가격 대비 품질이 아쉬워요. 다시 가고 싶지는 않네요.",
        "직원들이 불친절하고 서비스가 좋지 않았습니다.",
        "시설이 노후되어 있고 청결하지 못한 것 같아요.",
        "찾기 어려운 위치에 있고 교통이 불편해요.",
        "예약 시스템이 복잡하고 대기시간이 너무 길어요.",
        "메뉴가 한정적이고 맛도 그냥 그래요.",
        "사진과 실제가 너무 달라서 실망했습니다.",
        "주차하기 어렵고 주변이 시끄러워요.",
        "가격이 너무 비싸서 부담스러워요."
    );
    
    // Korean User Names
    public static final List<String> KOREAN_NAMES = Arrays.asList(
        "김민수", "이지영", "박준호", "최수진", "정대한", "한소영", "임재현", "윤미래",
        "장성호", "오혜진", "송민기", "노예은", "권태우", "문지혜", "백승호", "신유진",
        "조현우", "황미경", "강동희", "도수빈", "남궁민", "서지원", "홍길동", "배수지",
        "신민철", "고은별", "유재석", "천송이", "진수현", "마동석", "설현", "이민호"
    );
    
    // Travel Preferences in Korean
    public static final Map<String, List<String>> TRAVEL_PREFERENCES = Map.of(
        "여행스타일", Arrays.asList("모험적", "여유로운", "문화탐방", "맛집투어", "쇼핑중심", "자연친화"),
        "숙박선호", Arrays.asList("럭셔리호텔", "부티크호텔", "한옥체험", "게스트하우스", "펜션", "리조트"),
        "교통수단", Arrays.asList("대중교통", "렌터카", "택시", "도보", "자전거", "기차"),
        "예산수준", Arrays.asList("프리미엄", "중상급", "중급", "경제적", "백패커", "가성비"),
        "동행타입", Arrays.asList("솔로", "커플", "가족", "친구", "비즈니스", "단체"),
        "관심분야", Arrays.asList("역사문화", "현대문화", "K-pop", "음식", "쇼핑", "자연", "건축", "예술")
    );
    
    public List<LocationData> getSeoulLandmarks() {
        return SEOUL_LANDMARKS;
    }
    
    public List<LocationData> getSeoulRestaurants() {
        return SEOUL_RESTAURANTS;
    }
    
    public List<LocationData> getSeoulHotels() {
        return SEOUL_HOTELS;
    }
    
    public List<LocationData> getBusanAttractions() {
        return BUSAN_ATTRACTIONS;
    }
    
    public List<String> getPositiveReviews() {
        return POSITIVE_KOREAN_REVIEWS;
    }
    
    public List<String> getNegativeReviews() {
        return NEGATIVE_KOREAN_REVIEWS;
    }
    
    public List<String> getKoreanNames() {
        return KOREAN_NAMES;
    }
    
    public Map<String, List<String>> getTravelPreferences() {
        return TRAVEL_PREFERENCES;
    }
    
    /**
     * Location data container
     */
    public static class LocationData {
        public final String koreanName;
        public final String englishName;
        public final String category;
        public final String address;
        public final double latitude;
        public final double longitude;
        public final String description;
        public final List<String> tags;
        
        public LocationData(String koreanName, String englishName, String category, String address, 
                          double latitude, double longitude, String description, List<String> tags) {
            this.koreanName = koreanName;
            this.englishName = englishName;
            this.category = category;
            this.address = address;
            this.latitude = latitude;
            this.longitude = longitude;
            this.description = description;
            this.tags = tags;
        }
    }
}