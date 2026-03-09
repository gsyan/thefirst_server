package com.bk.sbs.entity;

import com.bk.sbs.enums.EModuleSubType;
import com.bk.sbs.enums.EModuleType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 캐릭터별 연구 상태 저장 엔티티 - 모듈 연구(moduleType/SubType) 및 문자열 기반 연구(researchId, 예: tech_level_N) 통합 관리
 */
@Entity
@Getter
@Setter
public class ModuleResearch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long characterId; // 캐릭터 ID

    @Enumerated(EnumType.STRING)
    @Column(nullable = true)
    private EModuleType moduleType; // 모듈 타입 (tech_level 연구 시 null)

    @Enumerated(EnumType.STRING)
    @Column(nullable = true)
    private EModuleSubType moduleSubType; // 서브타입 (tech_level 연구 시 null)

    @Column(nullable = true)
    private String researchId; // 문자열 기반 연구 ID (예: tech_level_2), 모듈 연구 시 null

    @Column(nullable = false)
    private boolean researched = false; // 개발 완료 여부

    @Column(nullable = false)
    private LocalDateTime created = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime modified = LocalDateTime.now();
}
