package com.oddiya.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "tourist_spot_details")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TouristSpotDetail extends BaseEntity {
    
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "place_id")
    private Place place;
    
    // Tour API 필드
    @Column(length = 50)
    private String heritage1;  // 세계문화유산
    
    @Column(length = 50)
    private String heritage2;  // 세계자연유산
    
    @Column(length = 50)
    private String heritage3;  // 세계기록유산
    
    @Column(columnDefinition = "TEXT")
    private String infocenter;  // 문의및안내
    
    @Column
    private String opendate;  // 개장일
    
    @Column
    private String restdate;  // 쉬는날
    
    @Column
    private String usetime;  // 이용시간
    
    @Column(columnDefinition = "TEXT")
    private String parking;  // 주차시설
    
    @Column(length = 50)
    private String chkbabycarriage;  // 유모차대여
    
    @Column(length = 50)
    private String chkpet;  // 애완동물동반
    
    @Column(length = 50)
    private String chkcreditcard;  // 신용카드가능
    
    @Column
    private String expguide;  // 체험안내
    
    @Column
    private String expagerange;  // 체험가능연령
    
    @Column
    private String accomcount;  // 수용인원
    
    @Column
    private String useseason;  // 이용시기
    
    @Column
    private String usefee;  // 이용요금
}