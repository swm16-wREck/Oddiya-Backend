package com.oddiya.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "place_photos", indexes = {
    @Index(name = "idx_place_photos", columnList = "place_id"),
    @Index(name = "idx_primary_photo", columnList = "place_id, is_primary")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlacePhoto extends BaseEntity {
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "place_id", nullable = false)
    private Place place;
    
    @Column(name = "photo_url", nullable = false, length = 1000)
    private String photoUrl;
    
    @Column(name = "width_px")
    private Integer widthPx;
    
    @Column(name = "height_px")
    private Integer heightPx;
    
    @Column(name = "photo_source", length = 50)
    @Builder.Default
    private String photoSource = "GOOGLE_PLACES";  // 기본값 Google Places
    
    @Column(name = "is_primary")
    @Builder.Default
    private Boolean isPrimary = false;
}