package com.bk.sbs.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

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
    private String shipName;

    @Column(nullable = false)
    private int positionIndex; // 함대 내에서의 위치 순서

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private boolean deleted = false;

    @Column(nullable = false)
    private LocalDateTime created = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime modified = LocalDateTime.now();

    @OneToMany(mappedBy = "ship", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ShipModule> modules;
}
