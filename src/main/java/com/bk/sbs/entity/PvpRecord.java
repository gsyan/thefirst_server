package com.bk.sbs.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

// PvP 전적 DB 백업 (Redis와 동기화, Redis 장애 시 복원용)
@Entity
@Table(name = "pvp_record")
@Getter
@Setter
public class PvpRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long characterId;

    @Column(nullable = false)
    private Integer score = 1000;

    @Column(nullable = false)
    private Integer wins = 0;

    @Column(nullable = false)
    private Integer losses = 0;

    @Column(nullable = false)
    private LocalDateTime lastUpdated = LocalDateTime.now();
}
