//--------------------------------------------------------------------------------------------------
package com.bk.sbs.repository;

import com.bk.sbs.entity.ConcurrentUserSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface ConcurrentUserSnapshotRepository extends JpaRepository<ConcurrentUserSnapshot, Long> {
    List<ConcurrentUserSnapshot> findBySnapshotAtBetweenOrderBySnapshotAtAsc(Instant from, Instant to);
}
