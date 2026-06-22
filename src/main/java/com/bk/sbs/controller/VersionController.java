//--------------------------------------------------------------------------------------------------
package com.bk.sbs.controller;

import com.bk.sbs.dto.nogenerated.ApiResponse;
import com.bk.sbs.dto.nogenerated.VersionCheckRequest;
import com.bk.sbs.dto.nogenerated.VersionCheckResponse;
import com.bk.sbs.service.VersionService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/version")
public class VersionController {

    private final VersionService versionService;

    public VersionController(VersionService versionService) {
        this.versionService = versionService;
    }

    @PostMapping("/check")
    public ApiResponse<VersionCheckResponse> checkVersion(@RequestBody VersionCheckRequest request) {
        VersionCheckResponse response = versionService.checkVersion(request.getVersionCode());
        return ApiResponse.success(response);
    }
}
