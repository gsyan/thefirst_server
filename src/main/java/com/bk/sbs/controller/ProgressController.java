//--------------------------------------------------------------------------------------------------
package com.bk.sbs.controller;

import com.bk.sbs.dto.ProgressInfoDto;
import com.bk.sbs.dto.ProgressListResponse;
import com.bk.sbs.dto.ProgressSaveRequest;
import com.bk.sbs.dto.nogenerated.ApiResponse;
import com.bk.sbs.exception.BusinessException;
import com.bk.sbs.exception.ServerErrorCode;
import com.bk.sbs.security.JwtUtil;
import com.bk.sbs.service.ProgressService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/progress")
public class ProgressController {

    private final ProgressService progressService;
    private final JwtUtil jwtUtil;

    public ProgressController(ProgressService progressService, JwtUtil jwtUtil) {
        this.progressService = progressService;
        this.jwtUtil = jwtUtil;
    }

    // 진행도 저장
    @PostMapping("/save")
    public ResponseEntity<ApiResponse<ProgressInfoDto>> saveProgress(
            @RequestBody ProgressSaveRequest request,
            HttpServletRequest httpRequest) {
        Long commanderId = getCommanderIdFromToken(httpRequest);
        ProgressInfoDto response = progressService.saveProgress(commanderId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 카테고리별 진행도 조회
    @GetMapping("/{category}")
    public ResponseEntity<ApiResponse<ProgressListResponse>> getProgressList(
            @PathVariable String category,
            HttpServletRequest httpRequest) {
        Long commanderId = getCommanderIdFromToken(httpRequest);
        ProgressListResponse response = progressService.getProgressList(commanderId, category);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    private Long getCommanderIdFromToken(HttpServletRequest request) {
        String token = jwtUtil.getTokenFromRequest(request);
        if (token == null) throw new BusinessException(ServerErrorCode.PROGRESS_CONTROLLER_FAIL_INVALID_TOKEN);
        if (!jwtUtil.hasCommanderId(token)) throw new BusinessException(ServerErrorCode.PROGRESS_CONTROLLER_FAIL_JWT_HAS_COMMANDERID);

        Long commanderId = jwtUtil.getCommanderIdFromToken(token);
        if (commanderId == null) throw new BusinessException(ServerErrorCode.PROGRESS_CONTROLLER_FAIL_JWT_GET_COMMANDERID);

        return commanderId & 0x00FFFFFFFFFFFFFFL;
    }
}



