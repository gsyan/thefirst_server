package com.bk.sbs.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "pvp_season")
@Getter
@Setter
public class PvpSeason {

    @Id
    @Column(nullable = false)
    private Integer seasonNumber;

    @Column(nullable = false)
    private Instant startTime;

    @Column(nullable = false)
    private Instant endTime;

    // 시즌 종료 시 보상 지급 완료 여부
    @Column(nullable = false)
    private boolean rewardDistributed = false;
}
