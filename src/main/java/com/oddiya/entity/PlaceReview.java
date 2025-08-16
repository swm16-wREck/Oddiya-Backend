package com.oddiya.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "place_reviews", indexes = {
    @Index(name = "idx_place_reviews", columnList = "place_id"),
    @Index(name = "idx_review_rating", columnList = "rating"),
    @Index(name = "idx_review_time", columnList = "review_time")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaceReview extends BaseEntity {
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "place_id", nullable = false)
    private Place place;
    
    @Column(nullable = false)
    private Integer rating;  // 1-5
    
    @Column(name = "review_text", columnDefinition = "TEXT")
    private String reviewText;
    
    @Column(name = "author_name")
    private String authorName;
    
    @Column(name = "author_profile_photo_url", length = 500)
    private String authorProfilePhotoUrl;
    
    @Column(name = "review_time")
    private LocalDateTime reviewTime;
    
    @Column(name = "relative_time_description")
    private String relativeTimeDescription;  // "2 months ago"
    
    @Column(name = "language_code", length = 10)
    @Builder.Default
    private String languageCode = "ko";
    
    @Column(name = "review_source", length = 50)
    @Builder.Default
    private String reviewSource = "GOOGLE_PLACES";
}