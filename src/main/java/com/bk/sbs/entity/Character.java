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

    // 기술레벨 연구 포인트 (스테이지 최초 클리어 보상)
    @Column(nullable = false, columnDefinition = "INT DEFAULT 0")
    private int techPoint = 0;

    // 모듈 레벨업/업그레이드 포인트 (스테이지 최초 클리어 보상)
    @Column(nullable = false, columnDefinition = "INT DEFAULT 0")
    private int modulePoint = 0;

    @Column(nullable = false, columnDefinition = "INT DEFAULT 0")
    private int modulePointMaxGot = 0;

    // PvP 시즌 보상 포인트 — 만료 시 소멸
    @Column(nullable = false, columnDefinition = "INT DEFAULT 0")
    private int pvpPoint = 0;

    @Column(nullable = false, columnDefinition = "INT DEFAULT 0")
    private int pvpPointMaxGot = 0;

    private Instant pvpPointExpiry;

    // 어느 시즌 보상인지 참조 — 다음 시즌 기간 변경 시 만료일 일괄 업데이트에 사용
    @Column(nullable = false, columnDefinition = "INT DEFAULT 0")
    private int pvpPointSeasonRef = 0;

    // 이름 변경 가능 횟수 (초기값 2, 0이면 변경 불가)
    @Column(nullable = false, columnDefinition = "INT DEFAULT 2")
    private Integer nameChangeCount = 2;

    // 마지막 자원 수집 시간 (zone clear 시 또는 collect 시 갱신, UTC)
    private Instant collectDateTime;

    // 마지막 온라인 시간 (heartbeat로 갱신, 오프라인 보상 계산용, UTC)
    private Instant lastOnlineAt;

    // 마지막 일일 로그인 보상 수령 시각 (무과금+VIP 통합, 24h 쿨다운 판단용, UTC)
    private Instant lastLoginRewardAt;

    // 이번 달 수령 현황 비트마스크 (bit0=1일, bit27=28일)
    @Column(nullable = false, columnDefinition = "INT DEFAULT 0")
    private int claimedDaysMask = 0;

    // VIP 보상 수령 현황 비트마스크 (bit0=1일, bit27=28일)
    @Column(nullable = false, columnDefinition = "INT DEFAULT 0")
    private int vipClaimedDaysMask = 0;

    // claimedDaysMask 기준 달 (yyyyMM, 새 달 판단용)
    private Integer loginRewardMonth;

    @Column(nullable = false)
    private boolean deleted = false;

    @Column(nullable = false)
    private LocalDateTime dateTime = LocalDateTime.now();
}