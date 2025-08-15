package com.oddiya.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Entity
@Table(name = "saved_plans", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "travel_plan_id"})
})
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class SavedPlan extends BaseEntity {
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "travel_plan_id", nullable = false)
    private TravelPlan travelPlan;
    
    @Column(name = "saved_at", nullable = false)
    private LocalDateTime savedAt;
    
    // Helper methods for entity access
    public String getUserId() {
        return user != null ? user.getId() : null;
    }
    
    public String getTravelPlanId() {
        return travelPlan != null ? travelPlan.getId() : null;
    }
}