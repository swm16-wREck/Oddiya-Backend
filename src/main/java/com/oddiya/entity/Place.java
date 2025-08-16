package com.oddiya.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "places", indexes = {
    @Index(name = "idx_place_category", columnList = "category"),
    @Index(name = "idx_place_location", columnList = "latitude,longitude"),
    @Index(name = "idx_place_area", columnList = "area_code,sigungu_code"),
    @Index(name = "idx_place_content_type", columnList = "content_type_id")
})
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Place extends BaseEntity {
    
    @Column(name = "naver_place_id", unique = true)
    private String naverPlaceId;
    
    @Column(nullable = false)
    private String name;
    
    @Column(nullable = false)
    private String category;
    
    @Column(length = 1000)
    private String description;
    
    @Column(nullable = false)
    private String address;
    
    @Column(name = "road_address")
    private String roadAddress;
    
    @Column(nullable = false)
    private Double latitude;
    
    @Column(nullable = false)
    private Double longitude;
    
    @Column(name = "phone_number")
    private String phoneNumber;
    
    @Column
    private String website;
    
    // ===== 한국관광공사 Tour API 필드 =====
    @Column(name = "content_id", unique = true, length = 50)
    private String contentId;
    
    @Column(name = "content_type_id", length = 10)
    private String contentTypeId;  // 12=관광지, 39=음식점, 32=숙박 등
    
    @Column(name = "area_code", length = 10)
    private String areaCode;  // 지역 코드
    
    @Column(name = "sigungu_code", length = 10)
    private String sigunguCode;  // 시군구 코드
    
    @Column(name = "cat1", length = 10)
    private String cat1;  // 대분류
    
    @Column(name = "cat2", length = 10)
    private String cat2;  // 중분류
    
    @Column(name = "cat3", length = 10)
    private String cat3;  // 소분류
    
    // ===== Google Places API 필드 =====
    @Column(name = "google_place_id", length = 100)
    private String googlePlaceId;
    
    @Column(name = "google_rating")
    private Double googleRating;  // Google 평점 (기존 rating과 별도)
    
    @Column(name = "google_user_ratings_total")
    private Integer googleUserRatingsTotal;
    
    @Column(name = "price_level")
    private Integer priceLevel;  // 0-4 가격대
    
    // ===== 데이터 출처 관리 =====
    @Column(name = "data_source", length = 50)
    private String dataSource;  // 'NAVER', 'TOUR_API', 'GOOGLE', 'USER'
    
    // ===== 정규화된 연관관계 (추가) =====
    @OneToMany(mappedBy = "place", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("isPrimary DESC, id ASC")
    private List<PlacePhoto> photos = new ArrayList<>();
    
    @OneToMany(mappedBy = "place", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("reviewTime DESC")
    private List<PlaceReview> placeReviews = new ArrayList<>();  // Google Places 및 외부 리뷰
    
    @OneToOne(mappedBy = "place", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private RestaurantDetail restaurantDetail;
    
    @OneToOne(mappedBy = "place", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private AccommodationDetail accommodationDetail;
    
    @OneToOne(mappedBy = "place", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private TouristSpotDetail touristSpotDetail;
    
    // ===== 기존 연관관계 유지 =====
    @OneToMany(mappedBy = "place", cascade = CascadeType.ALL)
    @Builder.Default
    private List<Review> reviews = new ArrayList<>();  // 사용자 리뷰 (기존)
    
    @OneToMany(mappedBy = "place")
    @Builder.Default
    private List<ItineraryItem> itineraryItems = new ArrayList<>();
    
    // ===== 기존 필드 유지 =====
    @Column
    private Double rating;
    
    @Column(name = "review_count")
    @Builder.Default
    private Integer reviewCount = 0;
    
    @Column(name = "bookmark_count")
    @Builder.Default
    private Integer bookmarkCount = 0;
    
    @Column(name = "view_count")
    @Builder.Default
    private Long viewCount = 0L;
    
    @Column(name = "is_verified")
    @Builder.Default
    private boolean isVerified = false;
    
    @Column(name = "popularity_score")
    @Builder.Default
    private Double popularityScore = 0.0;
}