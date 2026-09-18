//--------------------------------------------------------------------------------------------------
package com.bk.sbs.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "commander_daily_playtime", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"commanderId", "playDate"})
})
@Getter
@Setter
public class CommanderDailyPlaytime {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long commanderId;

    @Column(nullable = false)
    private LocalDate playDate;

    @Column(nullable = false)
    private int playedSeconds;

    @Column(nullable = false)
    private Instant lastHeartbeatAt;

    protected CommanderDailyPlaytime() {
    }
}
