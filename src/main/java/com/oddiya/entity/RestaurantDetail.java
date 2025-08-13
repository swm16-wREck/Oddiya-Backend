package com.oddiya.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "restaurant_details")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RestaurantDetail extends BaseEntity {
    
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "place_id")
    private Place place;
    
    // Tour API 필드
    @Column
    private String firstmenu;  // 대표메뉴
    
    @Column(columnDefinition = "TEXT")
    private String treatmenu;  // 취급메뉴
    
    @Column(length = 50)
    private String smoking;  // 금연/흡연
    
    @Column(length = 50)
    private String seat;  // 좌석수
    
    @Column(length = 50)
    private String kidsfacility;  // 어린이시설
    
    @Column(length = 50)
    private String packing;  // 포장가능여부
    
    @Column
    private String infocenterfood;  // 문의및안내
    
    @Column
    private String scalefood;  // 규모
    
    @Column(columnDefinition = "TEXT")
    private String parkingfood;  // 주차시설
    
    @Column
    private String opendatefood;  // 개업일
    
    @Column(length = 50)
    private String delivery;  // 배달여부
    
    @Column(length = 50)
    private String takeout;  // 테이크아웃
    
    @Column(columnDefinition = "TEXT")
    private String opentimefood;  // 영업시간
    
    @Column
    private String restdatefood;  // 쉬는날
    
    @Column(columnDefinition = "TEXT")
    private String discountinfofood;  // 할인정보
    
    @Column(length = 50)
    private String chkcreditcardfood;  // 신용카드가능
    
    @Column(columnDefinition = "TEXT")
    private String reservationfood;  // 예약안내
    
    @Column(length = 100)
    private String lcnsno;  // 인허가번호
    
    // Google Places 추가 필드
    @Column(name = "serves_breakfast")
    private Boolean servesBreakfast;
    
    @Column(name = "serves_lunch")
    private Boolean servesLunch;
    
    @Column(name = "serves_dinner")
    private Boolean servesDinner;
    
    @Column(name = "serves_vegetarian_food")
    private Boolean servesVegetarianFood;
    
    @Column(name = "menu_for_children")
    private Boolean menuForChildren;
    
    @Column(name = "outdoor_seating")
    private Boolean outdoorSeating;
    
    @Column(name = "dine_in")
    private Boolean dineIn;
}