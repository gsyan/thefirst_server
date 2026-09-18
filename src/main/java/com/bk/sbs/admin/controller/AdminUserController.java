//--------------------------------------------------------------------------------------------------
package com.bk.sbs.admin.controller;

import com.bk.sbs.admin.service.AdminUserReportService;
import com.bk.sbs.dto.nogenerated.admin.CommanderDetailDto;
import com.bk.sbs.dto.nogenerated.admin.CommanderSummaryDto;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/api/commanders")
public class AdminUserController {

    private final AdminUserReportService adminUserReportService;

    public AdminUserController(AdminUserReportService adminUserReportService) {
        this.adminUserReportService = adminUserReportService;
    }

    @GetMapping
    public ResponseEntity<Page<CommanderSummaryDto>> search(
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(adminUserReportService.search(query, page, size));
    }

    @GetMapping("/{commanderId}")
    public ResponseEntity<CommanderDetailDto> getDetail(@PathVariable Long commanderId) {
        return ResponseEntity.ok(adminUserReportService.getDetail(commanderId));
    }
}
