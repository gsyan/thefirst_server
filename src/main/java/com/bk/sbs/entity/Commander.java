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

    // 커맨더 레벨 (exp 누적 기준 자동 레벨업, 기본값 1)
    @Column(nullable = false, columnDefinition = "INT DEFAULT 1")
    private int commanderLevel = 1;

    // 경험치 (스테이지 클리어마다 매번 지급)
    @Column(nullable = false, columnDefinition = "INT DEFAULT 0")
    private int exp = 0;

    // PvP 시즌 보상 포인트 — 누적만 되며 만료 없음
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

    // 자원 최대치 — 전투 중 전술 토글(체력회복/미사일/함재기) 3종이 공유하는 소모 게이지의 상한.
    // 커맨더 생성 시 고정값, 성장 수단 없음. 존 런 시작 시 이 값으로 ZoneRun.resource가 초기화됨
    @Column(nullable = false, columnDefinition = "INT DEFAULT 1000")
    private int resourceMax = 1000;

    // 역대 누적 획득 광물 — 존런 중 실제로 보유 중인 광물(ZoneRun.mineralAmount, 런 종료 시 소멸)과 달리 절대 줄어들지 않음. 업적(MineralTotal 조건) 판정 전용.
    // 정식 지급 지점(존 셀 클리어, 보상카드 Instant_MineralFlat)에서만 증가
    @Column(nullable = false, columnDefinition = "INT DEFAULT 0")
    private int mineralEarnedTotal = 0;

    // 업적포인트 — 업적 패널에서 유저가 완료된 업적을 직접 수령(AchievementService.claimAchievement)해야만 지급됨(자동 지급 없음), 티어4+ 함체 언락(FleetService.unlockHull)에 소모
    @Column(nullable = false, columnDefinition = "INT DEFAULT 0")
    private int achievementPoint = 0;

    // 존 탈출(ESCAPED)로 확정된 존 번호 중 최댓값 — 다음 존 입장 가능 여부 판정용(ZoneRun 조회 없이 O(1))
    @Column(nullable = false, columnDefinition = "INT DEFAULT 0")
    private int highestClearedZoneNumber = 0;

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

    // claimedDaysMask 기준 주(週) 시작일 — 이번 주 월요일 UTC 날짜, 새 주 판단용
    private LocalDate loginRewardWeekStart;

    // 이번 주 동안 접속한 서로 다른 날짜 수(출석일수) — 수령 여부와 무관하게 접속만 해도 증가, todayDay(열린 칸 개수) 계산 기준
    @Column(nullable = false, columnDefinition = "INT DEFAULT 0")
    private int attendanceDayCount = 0;

    // attendanceDayCount 중복 증가 방지용 — 마지막으로 출석 카운트한 UTC 날짜
    private LocalDate lastAttendanceDate;

    // 보상카드 리롤(광고 시청) 오늘 사용 횟수 — 남은 횟수는 DataTableConfig.exploration.rewardCardRerollLimit에서 이 값을 뺀 값(한도 설정이 바뀌어도 항상 최신 기준으로 계산됨)
    @Column(nullable = false, columnDefinition = "INT DEFAULT 0")
    private int rewardCardRerollCountToday = 0;

    // rewardCardRerollCountToday 리셋 판단용 — 마지막으로 리셋된 UTC 날짜(attendanceDayCount/lastAttendanceDate와 동일한 지연 리셋 패턴)
    private LocalDate rewardCardRerollResetDate;

    @Column(nullable = false)
    private boolean deleted = false;

    @Column(nullable = false)
    private Instant dateTime = Instant.now();
}
