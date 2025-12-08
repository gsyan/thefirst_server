//--------------------------------------------------------------------------------------------------
package com.bk.sbs.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "`character`")    // character 는 마리아 DB 의 예약어라 이렇게 처리
@Getter
@Setter
public class Character {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long accountId;

    @Column(nullable = false, unique = true)
    private String characterName;

    private Long lastLocation;

    @Column(nullable = false)
    private Long money = 0L;

    @Column(nullable = false)
    private Long mineral = 0L;

    @Column(nullable = false)
    private Integer techLevel = 1;

    @Column(nullable = false)
    private boolean deleted = false;

    @Column(nullable = false)
    private LocalDateTime dateTime = LocalDateTime.now();
}