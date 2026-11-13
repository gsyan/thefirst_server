//--------------------------------------------------------------------------------------------------
package com.bk.sbs.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

// 일일퀘스트 판정용 날짜(UTC)별 범용 카운터 — counterType은 EAchievementConditionType 이름
@Entity
@Table(name = "commander_daily_counter", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"commanderId", "counterDate", "counterType"})
})
@Getter
@Setter
public class CommanderDailyCounter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long commanderId;

    @Column(nullable = false)
    private LocalDate counterDate;

    @Column(nullable = false, length = 64)
    private String counterType;

    @Column(nullable = false)
    private long counterValue;

    protected CommanderDailyCounter() {
    }
}
