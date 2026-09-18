//--------------------------------------------------------------------------------------------------
package com.bk.sbs.service;

import com.bk.sbs.entity.ConcurrentUserSnapshot;
import com.bk.sbs.repository.CommanderRepository;
import com.bk.sbs.repository.ConcurrentUserSnapshotRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;

// 시간대별 동시접속자 그래프용 주기적 스냅샷 — 어드민 대시보드가 이 테이블을 읽어 그래프로 표시
@Service
public class ConcurrentUserSnapshotScheduler {

    @Value("${concurrent.snapshot.online-window-seconds:90}")
    private long onlineWindowSeconds;

    private final CommanderRepository commanderRepository;
    private final ConcurrentUserSnapshotRepository concurrentUserSnapshotRepository;

    public ConcurrentUserSnapshotScheduler(CommanderRepository commanderRepository,
                                            ConcurrentUserSnapshotRepository concurrentUserSnapshotRepository) {
        this.commanderRepository = commanderRepository;
        this.concurrentUserSnapshotRepository = concurrentUserSnapshotRepository;
    }

    @Scheduled(fixedRateString = "#{${concurrent.snapshot.interval-minutes:5} * 60000}")
    public void takeSnapshot() {
        Instant now = Instant.now();
        long onlineCount = commanderRepository.countByLastOnlineAtAfter(now.minusSeconds(onlineWindowSeconds));
        concurrentUserSnapshotRepository.save(new ConcurrentUserSnapshot(now, (int) onlineCount));
    }
}
