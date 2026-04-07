// 캐릭터별 존 탐험 메타데이터 — Character 테이블 오염 방지를 위한 별도 테이블
package com.bk.sbs.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "zone_meta")
@Getter
@Setter
@NoArgsConstructor
public class ZoneMeta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "character_id", nullable = false, unique = true)
    private Long characterId;

    // 수복 타이머 기준점 — zone 2+ 최초 클리어 시 세팅, 수복 발생마다 갱신
    private Instant enemyRestoreTime;

    public ZoneMeta(Long characterId, Instant enemyRestoreTime) {
        this.characterId = characterId;
        this.enemyRestoreTime = enemyRestoreTime;
    }
}
