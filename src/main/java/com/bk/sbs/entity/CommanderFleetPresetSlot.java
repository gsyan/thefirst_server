package com.bk.sbs.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

// CommanderFleetPreset 안의 함선 슬롯 하나. shipPresetId는 DataTableShipPreset의 preset id 참조.
@Entity
@Getter
@Setter
public class CommanderFleetPresetSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fleet_preset_id", nullable = false)
    private CommanderFleetPreset fleetPreset;

    @Column(nullable = false)
    private int slotIndex;

    @Column(nullable = false)
    private String shipPresetId;

    @Column(nullable = false)
    private boolean isFront;
}
