package com.oddiya.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "accommodation_details")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccommodationDetail extends BaseEntity {
    
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "place_id")
    private Place place;
    
    // Tour API 필드
    @Column(length = 50)
    private String roomcount;  // 객실수
    
    @Column
    private String roomtype;  // 객실유형
    
    @Column(length = 100)
    private String checkintime;  // 체크인시간
    
    @Column(length = 100)
    private String checkouttime;  // 체크아웃시간
    
    @Column(length = 50)
    private String chkcooking;  // 객실내취사
    
    @Column(length = 50)
    private String seminar;  // 세미나실
    
    @Column(length = 50)
    private String sports;  // 스포츠시설
    
    @Column(length = 50)
    private String sauna;  // 사우나실
    
    @Column(length = 50)
    private String beauty;  // 뷰티시설
    
    @Column(length = 50)
    private String beverage;  // 식음료장
    
    @Column(length = 50)
    private String karaoke;  // 노래방
    
    @Column(length = 50)
    private String barbecue;  // 바비큐장
    
    @Column(length = 50)
    private String campfire;  // 캠프파이어
    
    @Column(length = 50)
    private String bicycle;  // 자전거대여
    
    @Column(length = 50)
    private String fitness;  // 피트니스센터
    
    @Column(length = 50)
    private String publicpc;  // 공용PC
    
    @Column(length = 50)
    private String publicbath;  // 공용샤워실
    
    @Column(columnDefinition = "TEXT")
    private String subfacility;  // 부대시설
    
    @Column(columnDefinition = "TEXT")
    private String foodplace;  // 식음료장
    
    @Column
    private String reservationurl;  // 예약안내홈페이지
    
    @Column
    private String pickup;  // 픽업서비스
    
    @Column
    private String infocenterlodging;  // 문의및안내
    
    @Column(columnDefinition = "TEXT")
    private String parkinglodging;  // 주차시설
    
    @Column
    private String reservationlodging;  // 예약안내
    
    @Column
    private String scalelodging;  // 규모
    
    @Column
    private String accomcountlodging;  // 수용가능인원
    
    @Column(columnDefinition = "TEXT")
    private String refundregulation;  // 환불규정
}