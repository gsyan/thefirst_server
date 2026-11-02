//--------------------------------------------------------------------------------------------------
package com.bk.sbs.entity;

import com.bk.sbs.enums.EModuleType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 아키타입 단위 영구 모듈강화(M6) — 함선 슬롯(인스턴스)이 아니라 커맨더-아키타입-moduleType-slotIndex 단위로 귀속.
// beam/missile/hangar는 슬롯마다 레벨이 독립(slotIndex 의미 있음) — 같은 아키타입이라도 슬롯0과 슬롯2가 다른 레벨일 수 있음
// hull/shield/interceptor는 슬롯 개념이 없어 slotIndex는 항상 0으로 고정(함체당 1개, 어느 슬롯에 배치하든 공유)
@Entity
@Table(name = "commander_archetype_module_upgrade",
        uniqueConstraints = @UniqueConstraint(columnNames = {"commander_id", "hull_archetype", "module_type", "slot_index"}))
@Getter
@Setter
@NoArgsConstructor
public class CommanderArchetypeModuleUpgrade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "commander_id", nullable = false)
    private Long commanderId;

    @Column(name = "hull_archetype", nullable = false, length = 50)
    private String hullArchetype;

    @Enumerated(EnumType.STRING)
    @Column(name = "module_type", nullable = false, length = 30)
    private EModuleType moduleType;

    @Column(name = "slot_index", nullable = false)
    private int slotIndex;

    @Column(nullable = false)
    private int level;

    public CommanderArchetypeModuleUpgrade(Long commanderId, String hullArchetype, EModuleType moduleType, int slotIndex, int level) {
        this.commanderId = commanderId;
        this.hullArchetype = hullArchetype;
        this.moduleType = moduleType;
        this.slotIndex = slotIndex;
        this.level = level;
    }
}
