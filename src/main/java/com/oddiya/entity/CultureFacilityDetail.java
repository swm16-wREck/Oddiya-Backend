package com.oddiya.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "culture_facility_details")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CultureFacilityDetail extends BaseEntity {
    
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "place_id")
    private Place place;
    
    // Tour API 필드
    @Column
    private String scale;  // 규모
    
    @Column(columnDefinition = "TEXT")
    private String usefee;  // 이용요금
    
    @Column(columnDefinition = "TEXT")
    private String discountinfo;  // 할인정보
    
    @Column(columnDefinition = "TEXT")
    private String spendtime;  // 관람소요시간
    
    @Column(columnDefinition = "TEXT")
    private String parkingfee;  // 주차요금
    
    @Column(columnDefinition = "TEXT")
    private String infocenterculture;  // 문의및안내
    
    @Column
    private String accomcountculture;  // 수용인원
    
    @Column
    private String usetimeculture;  // 이용시간
    
    @Column
    private String restdateculture;  // 쉬는날
    
    @Column
    private String parkingculture;  // 주차시설
    
    @Column(length = 50)
    private String chkbabycarriageculture;  // 유모차대여
    
    @Column(length = 50)
    private String chkpetculture;  // 애완동물동반
    
    @Column(length = 50)
    private String chkcreditcardculture;  // 신용카드가능
}