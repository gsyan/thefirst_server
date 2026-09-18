//--------------------------------------------------------------------------------------------------
package com.bk.sbs.admin.controller;

import com.bk.sbs.dto.nogenerated.admin.MaintenanceStatusDto;
import com.bk.sbs.dto.nogenerated.admin.MaintenanceUpdateRequest;
import com.bk.sbs.service.ServerStatusService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/api/maintenance")
public class AdminMaintenanceController {

    private final ServerStatusService serverStatusService;

    public AdminMaintenanceController(ServerStatusService serverStatusService) {
        this.serverStatusService = serverStatusService;
    }

    @GetMapping
    public ResponseEntity<MaintenanceStatusDto> getStatus() {
        return ResponseEntity.ok(serverStatusService.getMaintenanceStatus());
    }

    @PostMapping
    public ResponseEntity<?> updateStatus(@RequestBody MaintenanceUpdateRequest request) {
        try {
            serverStatusService.updateMaintenanceStatus(request.isWorking(), request.getEndTime());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
        return ResponseEntity.ok(serverStatusService.getMaintenanceStatus());
    }
}
