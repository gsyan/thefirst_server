//--------------------------------------------------------------------------------------------------
package com.bk.sbs.controller;

import com.bk.sbs.dto.ProgressInfoDto;
import com.bk.sbs.dto.ProgressListResponse;
import com.bk.sbs.dto.ProgressSaveRequest;
import com.bk.sbs.dto.nogenerated.ApiResponse;
import com.bk.sbs.security.CommanderId;
import com.bk.sbs.service.ProgressService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/progress")
public class ProgressController {

    private final ProgressService progressService;

    public ProgressController(ProgressService progressService) {
        this.progressService = progressService;
    }

    // 진행도 저장
    @PostMapping("/save")
    public ResponseEntity<ApiResponse<ProgressInfoDto>> saveProgress(
            @RequestBody ProgressSaveRequest request,
            @CommanderId Long commanderId) {
        ProgressInfoDto response = progressService.saveProgress(commanderId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 카테고리별 진행도 조회
    @GetMapping("/{category}")
    public ResponseEntity<ApiResponse<ProgressListResponse>> getProgressList(
            @PathVariable String category,
            @CommanderId Long commanderId) {
        ProgressListResponse response = progressService.getProgressList(commanderId, category);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}



