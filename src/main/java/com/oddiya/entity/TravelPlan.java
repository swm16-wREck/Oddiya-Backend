package com.oddiya.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "travel_plans", indexes = {
    @Index(name = "idx_travel_plan_user", columnList = "user_id"),
    @Index(name = "idx_travel_plan_status", columnList = "status")
})
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class TravelPlan extends BaseEntity {
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Column(nullable = false)
    private String title;
    
    @Column(length = 1000)
    private String description;
    
    @Column(nullable = false)
    private String destination;
    
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;
    
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;
    
    @Column(name = "number_of_people")
    private Integer numberOfPeople;
    
    @Column(precision = 12, scale = 2)
    private BigDecimal budget;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TravelPlanStatus status = TravelPlanStatus.DRAFT;
    
    @Column(name = "is_public")
    private boolean isPublic = false;
    
    @Column(name = "is_ai_generated")
    private boolean isAiGenerated = false;
    
    @ElementCollection
    @CollectionTable(name = "travel_plan_preferences", joinColumns = @JoinColumn(name = "travel_plan_id"))
    @MapKeyColumn(name = "preference_key")
    @Column(name = "preference_value")
    private Map<String, String> preferences = new HashMap<>();
    
    @OneToMany(mappedBy = "travelPlan", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("dayNumber ASC, sequence ASC")
    private List<ItineraryItem> itineraryItems = new ArrayList<>();
    
    @ManyToMany
    @JoinTable(
        name = "travel_plan_collaborators",
        joinColumns = @JoinColumn(name = "travel_plan_id"),
        inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    private List<User> collaborators = new ArrayList<>();
    
    @OneToMany(mappedBy = "travelPlan", cascade = CascadeType.ALL)
    private List<Video> videos = new ArrayList<>();
    
    @Column(name = "view_count")
    private Long viewCount = 0L;
    
    @Column(name = "like_count")
    private Long likeCount = 0L;
    
    @Column(name = "share_count")
    private Long shareCount = 0L;
    
    @Column(name = "save_count")
    private Long saveCount = 0L;
    
    @ElementCollection
    @CollectionTable(name = "travel_plan_tags", joinColumns = @JoinColumn(name = "travel_plan_id"))
    @Column(name = "tag")
    @Builder.Default
    private List<String> tags = new ArrayList<>();
    
    @Column(name = "cover_image_url")
    private String coverImageUrl;
}