//--------------------------------------------------------------------------------------------------
package com.bk.sbs.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Table(name = "`character`")    // character 는 마리아 DB 의 예약어라 이렇게 처리
@Getter
@Setter
public class Character {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long accountId;

    @Column(nullable = false, unique = true)
    private String characterName;

    private Long lastLocation;

    @Column(nullable = false)
    private Integer techLevel = 1;

    @Column(nullable = false)
    private Long mineral = 0L;

    @Column(nullable = false)
    private Long mineralRare = 0L;

    @Column(nullable = false)
    private Long mineralExotic = 0L;
    
    @Column(nullable = false)
    private Long mineralDark = 0L;

    // 소수점 자원 누적분 (0.0 ~ 0.999...)
    @Column(nullable = false)
    private Double mineralFraction = 0.0;

    @Column(nullable = false)
    private Double mineralRareFraction = 0.0;

    @Column(nullable = false)
    private Double mineralExoticFraction = 0.0;

    @Column(nullable = false)
    private Double mineralDarkFraction = 0.0;

    // 클리어한 최고 zone (예: "3-5"), 신규는 빈 문자열
    @Column(nullable = false)
    private String clearedZone = "";

    // 마지막 자원 수집 시간 (zone clear 시 또는 collect 시 갱신, UTC)
    private Instant collectDateTime;

    @Column(nullable = false)
    private boolean deleted = false;

    @Column(nullable = false)
    private LocalDateTime dateTime = LocalDateTime.now();
}