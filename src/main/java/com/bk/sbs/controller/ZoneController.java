package com.bk.sbs.controller;

import com.bk.sbs.dto.HeartbeatRequest;
import com.bk.sbs.dto.HeartbeatResponse;
import com.bk.sbs.dto.nogenerated.ApiResponse;
import com.bk.sbs.security.CommanderId;
import com.bk.sbs.service.ZoneService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/zone")
public class ZoneController {

    private final ZoneService zoneService;

    public ZoneController(ZoneService zoneService) {
        this.zoneService = zoneService;
    }

    // 하트비트 (온라인 시간 갱신)
    @PostMapping("/heartbeat")
    public ResponseEntity<ApiResponse<HeartbeatResponse>> heartbeat(
            @RequestBody HeartbeatRequest request,
            @CommanderId Long actualCommanderId) {
        HeartbeatResponse response = zoneService.heartbeat(actualCommanderId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}




