package com.oddiya.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "shopping_details")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShoppingDetail extends BaseEntity {
    
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "place_id")
    private Place place;
    
    // Tour API 필드
    @Column
    private String saleitem;  // 판매품목
    
    @Column
    private String saleitemcost;  // 판매품목별가격
    
    @Column
    private String fairday;  // 장서는날
    
    @Column
    private String opendateshopping;  // 개업일
    
    @Column
    private String opentime;  // 영업시간
    
    @Column
    private String restdateshopping;  // 휴무일
    
    @Column
    private String restroom;  // 화장실설명
    
    @Column(columnDefinition = "TEXT")
    private String parkingshopping;  // 주차시설
    
    @Column
    private String infocentershopping;  // 문의및안내
    
    @Column
    private String scaleshopping;  // 규모
    
    @Column
    private String shopguide;  // 매장안내
    
    @Column(length = 50)
    private String chkbabycarriageshopping;  // 유모차대여
    
    @Column(length = 50)
    private String chkpetshopping;  // 애완동물동반
    
    @Column(length = 50)
    private String chkcreditcardshopping;  // 신용카드가능
    
    @Column
    private String culturecenter;  // 문화센터바로가기
}