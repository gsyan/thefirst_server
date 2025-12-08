package com.bk.sbs.entity;

import com.bk.sbs.enums.*;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
public class ShipModule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ship_id", nullable = false)
    private Ship ship;

    @Enumerated(EnumType.ORDINAL)
    @Column(nullable = false)
    private EModuleType moduleType;

    @Column(nullable = false)
    private int moduleSubTypeValue;

    @Enumerated(EnumType.ORDINAL)
    @Column(nullable = false)
    private EModuleStyle moduleStyle;

    public void setModuleSubType(EModuleBodySubType subType) {
        this.moduleSubTypeValue = subType != null ? subType.getValue() : 0;
    }

    public void setModuleSubType(EModuleEngineSubType subType) {
        this.moduleSubTypeValue = subType != null ? subType.getValue() : 0;
    }

    public void setModuleSubType(EModuleWeaponSubType subType) {
        this.moduleSubTypeValue = subType != null ? subType.getValue() : 0;
    }

    public void setModuleSubType(EModuleHangerSubType subType) {
        this.moduleSubTypeValue = subType != null ? subType.getValue() : 0;
    }


    public EModuleBodySubType getModuleBodySubType() {
        for (EModuleBodySubType type : EModuleBodySubType.values())
            if (type.getValue() == moduleSubTypeValue) return type;
        return EModuleBodySubType.None;
    }

    public EModuleEngineSubType getModuleEngineSubType() {
        for (EModuleEngineSubType type : EModuleEngineSubType.values())
            if (type.getValue() == moduleSubTypeValue) return type;
        return EModuleEngineSubType.None;
    }

    public EModuleWeaponSubType getModuleWeaponSubType() {
        for (EModuleWeaponSubType type : EModuleWeaponSubType.values())
            if (type.getValue() == moduleSubTypeValue) return type;
        return EModuleWeaponSubType.None;
    }

    public EModuleHangerSubType getModuleHangerSubType() {
        for (EModuleHangerSubType type : EModuleHangerSubType.values())
            if (type.getValue() == moduleSubTypeValue) return type;
        return EModuleHangerSubType.None;
    }


    @Column(nullable = false)
    private int moduleLevel;

    @Column(nullable = false)
    private int bodyIndex; // 어떤 body 모듈에 장착될 것인지 (Body 모듈인 경우 자신의 인덱스)

    @Column(nullable = false)
    private int slotIndex; // 함선 내에서 모듈의 슬롯 위치 (Body는 0, Weapon과 Engine은 여러 개 가능)

    @Column(nullable = false)
    private boolean deleted = false;

    @Column(nullable = false)
    private LocalDateTime created = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime modified = LocalDateTime.now();
}
