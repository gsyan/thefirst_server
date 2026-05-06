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
    private int mineral;

    @Column(nullable = false, columnDefinition = "INT DEFAULT 0")
    private int mineralMaxGot = 0;

    // PvP 정산 배치 지급 재화 — 만료 시 소멸
    @Column(nullable = false, columnDefinition = "INT DEFAULT 0")
    private int pvpMineral = 0;

    @Column(nullable = false, columnDefinition = "INT DEFAULT 0")
    private int pvpMineralMaxGot = 0;

    private Instant pvpMineralExpiry;

    // IAP 구매 임시 재화 — 만료 시 소멸
    @Column(nullable = false, columnDefinition = "INT DEFAULT 0")
    private int tempMineral = 0;

    @Column(nullable = false, columnDefinition = "INT DEFAULT 0")
    private int tempMineralMaxGot = 0;

    private Instant tempMineralExpiry;

    // 이름 변경 가능 횟수 (초기값 2, 0이면 변경 불가)
    @Column(nullable = false, columnDefinition = "INT DEFAULT 2")
    private Integer nameChangeCount = 2;

    // 마지막 자원 수집 시간 (zone clear 시 또는 collect 시 갱신, UTC)
    private Instant collectDateTime;

    // 마지막 온라인 시간 (heartbeat로 갱신, 오프라인 보상 계산용, UTC)
    private Instant lastOnlineAt;

    @Column(nullable = false)
    private boolean deleted = false;

    @Column(nullable = false)
    private LocalDateTime dateTime = LocalDateTime.now();
}