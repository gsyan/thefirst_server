package com.bk.sbs.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

// Fleet 안의 함선 슬롯 하나. hullSubType은 DataTableModule의 body EModuleSubType 이름(함체) 참조.
// 유저가 실제로 장착한 모듈(빔/미사일/격납고 on-off 상태)은 modules에 함선 단위로 별도 저장 — hullSubType은 배치 시 기본값(빔1) 출처일 뿐, 이후엔 modules가 우선
@Entity
@Getter
@Setter
public class Ship {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fleet_id", nullable = false)
    private Fleet fleet;

    @Column(nullable = false)
    private int slotIndex;

    @Column(nullable = false)
    private String hullSubType;

    @Column(nullable = false)
    private boolean isFront;

    @OneToMany(mappedBy = "ship", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Module> modules;
}
