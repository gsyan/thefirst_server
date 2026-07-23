//--------------------------------------------------------------------------------------------------
package com.bk.sbs.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "commander")
@Getter
@Setter
public class Commander {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long accountId;

    @Column(nullable = false, unique = true)
    private String commanderName;

    private Long lastLocation;

    @Column(nullable = false)
    private int mineral;

    // 커맨더 레벨 (exp 누적 기준 자동 레벨업, 기본값 1)
    @Column(nullable = false, columnDefinition = "INT DEFAULT 1")
    private int commanderLevel = 1;

    // 경험치 (스테이지 클리어마다 매번 지급)
    @Column(nullable = false, columnDefinition = "INT DEFAULT 0")
    private int exp = 0;

    // 모듈 레벨업/업그레이드 포인트 (커맨더 레벨업 보상)
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

    // 탐험 함대 편성 지휘력 최대치 — IncreaseCommandPowerMaxRequest(은행 탐험 포인트 소모)로 영구 증가
    // 기본값은 임시 밸런스값(더미 프리셋 commandCost 100~500 기준 1~2척 배치 가능한 수준) — 기획 확정 시 조정
    @Column(nullable = false, columnDefinition = "INT DEFAULT 300")
    private int commandPowerMax = 300;

    // 마지막 자원 수집 시간 (zone clear 시 또는 collect 시 갱신, UTC)
    private Instant collectDateTime;

    // 마지막 온라인 시간 (heartbeat로 갱신, 오프라인 보상 계산용, UTC)
    private Instant lastOnlineAt;

    // 이번 달 수령 현황 비트마스크 (bit0=1일, bit27=28일)
    @Column(nullable = false, columnDefinition = "INT DEFAULT 0")
    private int claimedDaysMask = 0;

    // VIP 보상 수령 현황 비트마스크 (bit0=1일, bit27=28일)
    @Column(nullable = false, columnDefinition = "INT DEFAULT 0")
    private int vipClaimedDaysMask = 0;

    // claimedDaysMask 기준 달 (yyyyMM, 새 달 판단용)
    private Integer loginRewardMonth;

    // 일일 로그인 보상을 실제로 마지막 수령한 UTC 날짜 — todayDay(출석 순번) 계산이 마스크 비트 수 기반이라
    // 같은 날 중복 호출 시 순번이 잘못 증가하는 것을 막기 위한 가드
    private LocalDate lastDailyClaimDate;

    @Column(nullable = false)
    private boolean deleted = false;

    @Column(nullable = false)
    private Instant dateTime = Instant.now();
}
