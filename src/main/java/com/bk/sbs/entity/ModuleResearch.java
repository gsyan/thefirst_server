package com.bk.sbs.entity;

import com.bk.sbs.enums.EModuleSlotType;
import com.bk.sbs.enums.EModuleSubType;
import com.bk.sbs.enums.EModuleType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 캐릭터별 모듈 개발(연구) 상태를 저장하는 엔티티
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
    @Column(nullable = false)
    private EModuleType moduleType; // 모듈 타입 (Body, Weapon, Engine, Hanger)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EModuleSubType moduleSubType; // 서브타입 (Body_Battle, Engine_Standard 등)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EModuleSlotType moduleSlotType; // 모듈 슬롯 타입 (비트연산 All, Front, Top, Bottom, Left. Right, Rear)

    @Column(nullable = false)
    private boolean researched = false; // 개발 완료 여부

    @Column(nullable = false)
    private LocalDateTime created = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime modified = LocalDateTime.now();

    // 복합 인덱스: characterId + moduleType + moduleSubTypeValue + moduleSlotTypeValue 조합으로 유니크
    // (한 캐릭터가 같은 모듈을 중복으로 개발할 수 없음)
}
