package com.oddiya.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalTime;

@Entity
@Table(name = "place_opening_hours", indexes = {
    @Index(name = "idx_opening_hours_place", columnList = "place_id"),
    @Index(name = "idx_opening_hours_day", columnList = "day_of_week")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaceOpeningHours extends BaseEntity {
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "place_id", nullable = false)
    private Place place;
    
    @Column(name = "day_of_week", nullable = false)
    private Integer dayOfWeek;  // 0=일요일, 1=월요일, ..., 6=토요일
    
    @Column(name = "open_time")
    private LocalTime openTime;
    
    @Column(name = "close_time")
    private LocalTime closeTime;
    
    @Column(name = "is_closed")
    @Builder.Default
    private Boolean isClosed = false;
    
    @Column(name = "break_start_time")
    private LocalTime breakStartTime;  // 휴게시간 시작
    
    @Column(name = "break_end_time")
    private LocalTime breakEndTime;    // 휴게시간 종료
    
    @Column(name = "last_order_time")
    private LocalTime lastOrderTime;   // 라스트오더 (음식점)
    
    // Helper methods
    public String getDayName() {
        String[] days = {"일요일", "월요일", "화요일", "수요일", "목요일", "금요일", "토요일"};
        return days[dayOfWeek];
    }
    
    public boolean isOpenNow(LocalTime currentTime) {
        if (isClosed) return false;
        if (openTime == null || closeTime == null) return false;
        
        // 휴게시간 체크
        if (breakStartTime != null && breakEndTime != null) {
            if (currentTime.isAfter(breakStartTime) && currentTime.isBefore(breakEndTime)) {
                return false;
            }
        }
        
        // 자정을 넘어가는 경우 처리
        if (closeTime.isBefore(openTime)) {
            return currentTime.isAfter(openTime) || currentTime.isBefore(closeTime);
        }
        
        return currentTime.isAfter(openTime) && currentTime.isBefore(closeTime);
    }
}