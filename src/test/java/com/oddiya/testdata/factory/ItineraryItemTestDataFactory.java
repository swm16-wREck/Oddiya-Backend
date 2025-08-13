package com.oddiya.testdata.factory;

import com.oddiya.entity.ItineraryItem;
import com.oddiya.entity.Place;
import com.oddiya.entity.TravelPlan;
import net.datafaker.Faker;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Test data factory for ItineraryItem entities using Builder pattern
 * Generates realistic Korean itinerary items with proper sequencing
 */
public class ItineraryItemTestDataFactory {
    
    private final Faker faker = new Faker(Locale.KOREAN);
    private final Random random = new Random(12345);
    
    /**
     * Create a single itinerary item
     */
    public ItineraryItem createItineraryItem(TravelPlan travelPlan, Place place) {
        return createItineraryItemBuilder(travelPlan, place).build();
    }
    
    /**
     * Create multiple itinerary items for a travel plan
     */
    public List<ItineraryItem> createItineraryItems(TravelPlan travelPlan, List<Place> places, int itemsPerDay) {
        List<ItineraryItem> items = new ArrayList<>();
        
        long durationDays = travelPlan.getEndDate().toEpochDay() - travelPlan.getStartDate().toEpochDay() + 1;
        
        for (int day = 1; day <= durationDays; day++) {
            List<ItineraryItem> dayItems = createDayItinerary(travelPlan, places, day, itemsPerDay);
            items.addAll(dayItems);
        }
        
        return items;
    }
    
    /**
     * Create realistic day itinerary with proper timing
     */
    public List<ItineraryItem> createDayItinerary(TravelPlan travelPlan, List<Place> places, int dayNumber, int itemCount) {
        List<ItineraryItem> dayItems = new ArrayList<>();
        
        // Define typical daily schedule
        LocalTime[] startTimes = {
            LocalTime.of(9, 0),   // Morning activity
            LocalTime.of(12, 0),  // Lunch
            LocalTime.of(14, 30), // Afternoon activity
            LocalTime.of(17, 0),  // Evening activity
            LocalTime.of(19, 30)  // Dinner
        };
        
        int actualItemCount = Math.min(itemCount, startTimes.length);
        
        for (int sequence = 1; sequence <= actualItemCount; sequence++) {
            Place place = selectPlaceForTimeSlot(places, sequence);
            LocalTime startTime = startTimes[sequence - 1];
            LocalDateTime startDateTime = LocalDateTime.now().with(startTime);
            
            ItineraryItem item = createItineraryItemBuilder(travelPlan, place)
                .dayNumber(dayNumber)
                .sequence(sequence)
                .startTime(startDateTime)
                .endTime(startDateTime.plusHours(1 + random.nextInt(3))) // 1-3 hours duration
                .notes(generateTimeSlotNotes(sequence))
                .build();
                
            dayItems.add(item);
        }
        
        return dayItems;
    }
    
    /**
     * Create Seoul-specific itinerary
     */
    public List<ItineraryItem> createSeoulItinerary(TravelPlan travelPlan, List<Place> seoulPlaces) {
        List<ItineraryItem> items = new ArrayList<>();
        
        // Day 1: Traditional Seoul
        items.addAll(createSeoulDay1(travelPlan, seoulPlaces));
        
        // Day 2: Modern Seoul (if multi-day trip)
        if (travelPlan.getEndDate().isAfter(travelPlan.getStartDate())) {
            items.addAll(createSeoulDay2(travelPlan, seoulPlaces));
        }
        
        return items;
    }
    
    /**
     * Create itinerary item builder with randomized data
     */
    public ItineraryItem.ItineraryItemBuilder createItineraryItemBuilder(TravelPlan travelPlan, Place place) {
        return ItineraryItem.builder()
            .travelPlan(travelPlan)
            .place(place)
            .dayNumber(1 + random.nextInt(7)) // 1-7 days
            .sequence(1 + random.nextInt(10)) // 1-10 activities per day
            .startTime(generateRandomTime())
            .endTime(generateRandomTime())
            .notes(generateItineraryNotes());
    }
    
    private List<ItineraryItem> createSeoulDay1(TravelPlan travelPlan, List<Place> places) {
        List<ItineraryItem> day1Items = new ArrayList<>();
        
        // Morning: Gyeongbokgung Palace
        Place palace = findPlaceByName(places, "경복궁");
        if (palace != null) {
            day1Items.add(ItineraryItem.builder()
                .travelPlan(travelPlan)
                .place(palace)
                .dayNumber(1)
                .sequence(1)
                .startTime(LocalDateTime.now().with(LocalTime.of(9, 0)))
                .endTime(LocalDateTime.now().with(LocalTime.of(11, 30)))
                .notes("조선왕조의 대표 궁궐. 수문장 교대식 관람 (10:00, 11:00)")
                .build());
        }
        
        // Lunch: Traditional restaurant
        Place restaurant = findPlaceByCategory(places, "한식");
        if (restaurant != null) {
            day1Items.add(ItineraryItem.builder()
                .travelPlan(travelPlan)
                .place(restaurant)
                .dayNumber(1)
                .sequence(2)
                .startTime(LocalDateTime.now().with(LocalTime.of(12, 0)))
                .endTime(LocalDateTime.now().with(LocalTime.of(13, 30)))
                .notes("전통 한식으로 점심. 궁중요리 체험 추천")
                .build());
        }
        
        // Afternoon: Bukchon Hanok Village
        Place hanokVillage = findPlaceByName(places, "북촌한옥마을");
        if (hanokVillage != null) {
            day1Items.add(ItineraryItem.builder()
                .travelPlan(travelPlan)
                .place(hanokVillage)
                .dayNumber(1)
                .sequence(3)
                .startTime(LocalDateTime.now().with(LocalTime.of(14, 0)))
                .endTime(LocalDateTime.now().with(LocalTime.of(16, 0)))
                .notes("전통 한옥마을 투어. 사진 촬영 명소")
                .build());
        }
        
        return day1Items;
    }
    
    private List<ItineraryItem> createSeoulDay2(TravelPlan travelPlan, List<Place> places) {
        List<ItineraryItem> day2Items = new ArrayList<>();
        
        // Morning: Gangnam
        Place gangnam = findPlaceByName(places, "강남");
        if (gangnam != null) {
            day2Items.add(ItineraryItem.builder()
                .travelPlan(travelPlan)
                .place(gangnam)
                .dayNumber(2)
                .sequence(1)
                .startTime(LocalDateTime.now().with(LocalTime.of(10, 0)))
                .endTime(LocalDateTime.now().with(LocalTime.of(12, 0)))
                .notes("현대 서울의 중심지. 쇼핑과 K-pop 문화 체험")
                .build());
        }
        
        // Evening: Han River
        Place hanRiver = findPlaceByName(places, "한강공원");
        if (hanRiver != null) {
            day2Items.add(ItineraryItem.builder()
                .travelPlan(travelPlan)
                .place(hanRiver)
                .dayNumber(2)
                .sequence(2)
                .startTime(LocalDateTime.now().with(LocalTime.of(17, 0)))
                .endTime(LocalDateTime.now().with(LocalTime.of(19, 0)))
                .notes("한강 공원에서 피크닉. 치킨과 맥주 추천")
                .build());
        }
        
        return day2Items;
    }
    
    private Place selectPlaceForTimeSlot(List<Place> places, int sequence) {
        // Select appropriate place based on time slot
        switch (sequence) {
            case 1: // Morning - Tourist attractions
                return findPlaceByCategory(places, "관광지");
            case 2: // Lunch - Restaurants
                return findPlaceByCategory(places, "음식점");
            case 3: // Afternoon - Cultural sites or shopping
                return findPlaceByCategory(places, "문화시설");
            case 4: // Late afternoon - Parks or leisure
                return findPlaceByCategory(places, "공원");
            case 5: // Dinner - Restaurants or cafes
                return findPlaceByCategory(places, "음식점");
            default:
                return places.get(random.nextInt(places.size()));
        }
    }
    
    private Place findPlaceByName(List<Place> places, String name) {
        return places.stream()
            .filter(place -> place.getName().contains(name))
            .findFirst()
            .orElse(places.get(random.nextInt(places.size())));
    }
    
    private Place findPlaceByCategory(List<Place> places, String category) {
        List<Place> categoryPlaces = places.stream()
            .filter(place -> place.getCategory().equals(category))
            .toList();
            
        if (categoryPlaces.isEmpty()) {
            return places.get(random.nextInt(places.size()));
        }
        
        return categoryPlaces.get(random.nextInt(categoryPlaces.size()));
    }
    
    private String generateTimeSlotNotes(int sequence) {
        Map<Integer, String[]> timeSlotNotes = Map.of(
            1, new String[]{"오전 관광 시작. 사람이 적어 사진 촬영하기 좋음", "이른 시간 방문으로 여유롭게 관람", "오전 일찍 도착해서 대기시간 단축"},
            2, new String[]{"점심 시간. 현지 맛집에서 한식 체험", "오전 활동 후 에너지 충전 시간", "현지인 추천 맛집에서 식사"},
            3, new String[]{"오후 주요 일정. 충분한 시간 확보", "날씨가 좋을 때 야외 활동 추천", "오후 시간대 가장 활발한 활동"},
            4, new String[]{"해질녘 방문으로 아름다운 석양 감상", "오후 늦은 시간 여유로운 관람", "저녁 식사 전 마지막 관광"},
            5, new String[]{"하루 마무리 저녁 식사", "현지 야경과 함께 즐기는 저녁", "하루 여행의 마무리 시간"}
        );
        
        String[] notes = timeSlotNotes.get(sequence);
        if (notes != null) {
            return notes[random.nextInt(notes.length)];
        }
        
        return "여행 일정의 중요한 코스입니다.";
    }
    
    private String generateItineraryNotes() {
        String[] notes = {
            "미리 예약하시는 것을 추천드립니다.",
            "현금 결제만 가능한 곳이니 준비해 주세요.",
            "사진 촬영이 제한될 수 있으니 미리 확인하세요.",
            "주말에는 사람이 많으니 평일 방문을 권합니다.",
            "근처에 주차장이 있어서 편리합니다.",
            "대중교통으로 접근하기 좋은 위치입니다.",
            "영업시간을 미리 확인하고 방문하세요.",
            "날씨에 따라 일정 변경이 필요할 수 있습니다.",
            "현지 가이드 투어를 이용하면 더 좋습니다.",
            "충분한 시간을 확보하고 여유롭게 즐기세요."
        };
        
        return notes[random.nextInt(notes.length)];
    }
    
    private LocalDateTime generateRandomTime() {
        int hour = 8 + random.nextInt(14); // 8 AM to 9 PM
        int minute = random.nextInt(4) * 15; // 0, 15, 30, 45 minutes
        return LocalDateTime.now().with(LocalTime.of(hour, minute));
    }
}