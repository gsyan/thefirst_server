//--------------------------------------------------------------------------------------------------
package com.bk.sbs.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "concurrent_user_snapshot")
@Getter
@Setter
public class ConcurrentUserSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant snapshotAt;

    @Column(nullable = false)
    private int onlineCount;

    public ConcurrentUserSnapshot(Instant snapshotAt, int onlineCount) {
        this.snapshotAt = snapshotAt;
        this.onlineCount = onlineCount;
    }

    protected ConcurrentUserSnapshot() {
    }
}
