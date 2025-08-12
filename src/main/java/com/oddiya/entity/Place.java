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
    @Index(name = "idx_place_naver", columnList = "naver_place_id", unique = true)
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
    
    @ElementCollection
    @CollectionTable(name = "place_opening_hours", joinColumns = @JoinColumn(name = "place_id"))
    @MapKeyColumn(name = "day_of_week")
    @Column(name = "hours")
    @Builder.Default
    private Map<String, String> openingHours = new HashMap<>();
    
    @ElementCollection
    @CollectionTable(name = "place_images", joinColumns = @JoinColumn(name = "place_id"))
    @Column(name = "image_url")
    @Builder.Default
    private List<String> images = new ArrayList<>();
    
    @ElementCollection
    @CollectionTable(name = "place_tags", joinColumns = @JoinColumn(name = "place_id"))
    @Column(name = "tag")
    @Builder.Default
    private List<String> tags = new ArrayList<>();
    
    @OneToMany(mappedBy = "place", cascade = CascadeType.ALL)
    @Builder.Default
    private List<Review> reviews = new ArrayList<>();
    
    @OneToMany(mappedBy = "place")
    @Builder.Default
    private List<ItineraryItem> itineraryItems = new ArrayList<>();
    
    @Column
    private Double rating;
    
    @Column(name = "review_count")
    @Builder.Default
    private Integer reviewCount = 0;
    
    @Column(name = "bookmark_count")
    @Builder.Default
    private Integer bookmarkCount = 0;
    
    @Column(name = "is_verified")
    @Builder.Default
    private boolean isVerified = false;
    
    @Column(name = "popularity_score")
    @Builder.Default
    private Double popularityScore = 0.0;
}