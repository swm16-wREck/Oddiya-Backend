package com.oddiya.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_user_email", columnList = "email", unique = true),
    @Index(name = "idx_user_provider_id", columnList = "provider,provider_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class User extends BaseEntity {
    
    @Column(nullable = false, unique = true)
    private String email;
    
    @Column(unique = true)
    private String username;
    
    @Column
    private String password;
    
    @Column(name = "nickname", nullable = false)
    private String nickname;
    
    @Column(name = "profile_image_url")
    private String profileImageUrl;
    
    @Column(name = "bio", length = 500)
    private String bio;
    
    @Column(name = "provider", nullable = false)
    private String provider; // google, apple
    
    @Column(name = "provider_id", nullable = false)
    private String providerId;
    
    @ElementCollection
    @CollectionTable(name = "user_preferences", joinColumns = @JoinColumn(name = "user_id"))
    @MapKeyColumn(name = "preference_key")
    @Column(name = "preference_value")
    @Builder.Default
    private Map<String, String> preferences = new HashMap<>();
    
    @ElementCollection
    @CollectionTable(name = "user_travel_preferences", joinColumns = @JoinColumn(name = "user_id"))
    @MapKeyColumn(name = "preference_key")
    @Column(name = "preference_value")
    @Builder.Default
    private Map<String, String> travelPreferences = new HashMap<>();
    
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<TravelPlan> travelPlans = new ArrayList<>();
    
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Review> reviews = new ArrayList<>();
    
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Video> videos = new ArrayList<>();
    
    @ManyToMany
    @JoinTable(
        name = "user_followers",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "follower_id")
    )
    @Builder.Default
    private List<User> followers = new ArrayList<>();
    
    @ManyToMany(mappedBy = "followers")
    @Builder.Default
    private List<User> following = new ArrayList<>();
    
    @Column(name = "is_email_verified")
    @Builder.Default
    private boolean isEmailVerified = false;
    
    @Column(name = "is_premium")
    @Builder.Default
    private boolean isPremium = false;
    
    @Column(name = "is_active")
    @Builder.Default
    private boolean isActive = true;
    
    @Column(name = "refresh_token")
    private String refreshToken;
    
    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;
}