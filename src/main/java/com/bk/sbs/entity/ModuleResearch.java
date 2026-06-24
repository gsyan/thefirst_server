package com.bk.sbs.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

// 캐릭터별 문자열 기반 연구 상태 저장 (예: tech_level_N)
@Entity
@Getter
@Setter
public class ModuleResearch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long commanderId;

    @Column(nullable = true)
    private String researchId; // 문자열 기반 연구 ID (예: tech_level_2)

    @Column(nullable = false)
    private boolean researched = false;

    @Column(nullable = false)
    private Instant created = Instant.now();

    @Column(nullable = false)
    private Instant modified = Instant.now();
}

