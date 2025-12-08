package com.bk.sbs.entity;

import com.bk.sbs.enums.EFormationType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Getter
@Setter
public class Fleet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long characterId;

    @Column(nullable = false)
    private String fleetName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private boolean isActive = true; // 현재 활성 함대인지

    @Column(nullable = false)
    private boolean deleted = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EFormationType formation = EFormationType.LinearHorizontal;

    @Column(nullable = false)
    private LocalDateTime created = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime modified = LocalDateTime.now();

    @OneToMany(mappedBy = "fleet", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Ship> ships;
}
