package com.oddiya.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "sports_details")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SportsDetail extends BaseEntity {
    
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "place_id")
    private Place place;
    
    // Tour API 필드
    @Column
    private String openperiod;  // 개장기간
    
    @Column(columnDefinition = "TEXT")
    private String reservation;  // 예약안내
    
    @Column
    private String infocenterleports;  // 문의및안내
    
    @Column
    private String scaleleports;  // 규모
    
    @Column
    private String accomcountleports;  // 수용인원
    
    @Column
    private String restdateleports;  // 휴무일
    
    @Column(columnDefinition = "TEXT")
    private String usetimeleports;  // 이용시간
    
    @Column(columnDefinition = "TEXT")
    private String usefeeleports;  // 입장료
    
    @Column
    private String expagerangeleports;  // 체험가능연령
    
    @Column(columnDefinition = "TEXT")
    private String parkingleports;  // 주차시설
    
    @Column
    private String parkingfeeleports;  // 주차요금
    
    @Column(length = 50)
    private String chkbabycarriageleports;  // 유모차대여
    
    @Column(length = 50)
    private String chkpetleports;  // 애완동물동반
    
    @Column(length = 50)
    private String chkcreditcardleports;  // 신용카드가능
}