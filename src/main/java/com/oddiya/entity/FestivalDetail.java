package com.oddiya.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "festival_details")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FestivalDetail extends BaseEntity {
    
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "place_id")
    private Place place;
    
    // Tour API 필드
    @Column
    private String sponsor1;  // 주최자정보
    
    @Column
    private String sponsor1tel;  // 주최자연락처
    
    @Column
    private String sponsor2;  // 주관사정보
    
    @Column
    private String sponsor2tel;  // 주관사연락처
    
    @Column
    private LocalDate eventstartdate;  // 행사시작일
    
    @Column
    private LocalDate eventenddate;  // 행사종료일
    
    @Column
    private String playtime;  // 공연시간
    
    @Column
    private String eventplace;  // 행사장소
    
    @Column
    private String eventhomepage;  // 행사홈페이지
    
    @Column
    private String agelimit;  // 관람가능연령
    
    @Column
    private String bookingplace;  // 예매처
    
    @Column
    private String placeinfo;  // 행사장위치안내
    
    @Column
    private String subevent;  // 부대행사
    
    @Column(columnDefinition = "TEXT")
    private String program;  // 행사프로그램
    
    @Column(columnDefinition = "TEXT")
    private String usetimefestival;  // 이용요금
    
    @Column(columnDefinition = "TEXT")
    private String discountinfofestival;  // 할인정보
    
    @Column
    private String spendtimefestival;  // 관람소요시간
    
    @Column
    private String festivalgrade;  // 축제등급
}