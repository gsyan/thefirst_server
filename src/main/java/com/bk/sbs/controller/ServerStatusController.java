//--------------------------------------------------------------------------------------------------
package com.bk.sbs.controller;

import com.bk.sbs.dto.ServerStatusRequest;
import com.bk.sbs.dto.ServerStatusResponse;
import com.bk.sbs.dto.nogenerated.ApiResponse;
import com.bk.sbs.service.ServerStatusService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/status")
public class ServerStatusController {

    private final ServerStatusService serverStatusService;

    public ServerStatusController(ServerStatusService serverStatusService) {
        this.serverStatusService = serverStatusService;
    }

    @PostMapping("")
    public ApiResponse<ServerStatusResponse> getStatus(@RequestBody ServerStatusRequest request) {
        ServerStatusResponse status = serverStatusService.getStatus(request.getVersionCode());
        return ApiResponse.success(status);
    }
}
