//--------------------------------------------------------------------------------------------------
package com.bk.sbs.admin.controller;

import com.bk.sbs.dto.nogenerated.admin.ConcurrentUserPointDto;
import com.bk.sbs.repository.ConcurrentUserSnapshotRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@RestController
@RequestMapping("/admin/api")
public class AdminDashboardController {

    private final ConcurrentUserSnapshotRepository concurrentUserSnapshotRepository;

    public AdminDashboardController(ConcurrentUserSnapshotRepository concurrentUserSnapshotRepository) {
        this.concurrentUserSnapshotRepository = concurrentUserSnapshotRepository;
    }

    @GetMapping("/concurrent-users")
    public ResponseEntity<List<ConcurrentUserPointDto>> getConcurrentUsers(
            @RequestParam(defaultValue = "24") int rangeHours) {
        Instant now = Instant.now();
        Instant fromTime = now.minus(rangeHours, ChronoUnit.HOURS);

        List<ConcurrentUserPointDto> points = concurrentUserSnapshotRepository
                .findBySnapshotAtBetweenOrderBySnapshotAtAsc(fromTime, now)
                .stream()
                .map(s -> new ConcurrentUserPointDto(s.getSnapshotAt(), s.getOnlineCount()))
                .toList();

        return ResponseEntity.ok(points);
    }
}
