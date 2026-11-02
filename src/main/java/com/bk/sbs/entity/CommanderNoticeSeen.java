package com.bk.sbs.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

// 커맨더별 공지사항 열람 기록 — (commanderId, noticeId) 유니크, 재열람 시 seenVersion/seenAt 갱신
@Entity
@Table(name = "commander_notice_seen")
@Getter
@Setter
@NoArgsConstructor
public class CommanderNoticeSeen {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "commander_id", nullable = false)
    private Long commanderId;

    @Column(name = "notice_id", nullable = false)
    private Long noticeId;

    @Column(name = "seen_version", nullable = false)
    private int seenVersion;

    @Column(name = "seen_at", nullable = false)
    private Instant seenAt = Instant.now();

    public CommanderNoticeSeen(Long commanderId, Long noticeId, int seenVersion) {
        this.commanderId = commanderId;
        this.noticeId = noticeId;
        this.seenVersion = seenVersion;
        this.seenAt = Instant.now();
    }
}
