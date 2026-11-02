//--------------------------------------------------------------------------------------------------
package com.bk.sbs.admin.controller;

import com.bk.sbs.dto.nogenerated.admin.NoticeAdminDto;
import com.bk.sbs.dto.nogenerated.admin.NoticeSaveRequest;
import com.bk.sbs.service.NoticeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/api/notice")
public class AdminNoticeController {

    private final NoticeService noticeService;

    public AdminNoticeController(NoticeService noticeService) {
        this.noticeService = noticeService;
    }

    @GetMapping
    public ResponseEntity<List<NoticeAdminDto>> listNotices() {
        return ResponseEntity.ok(noticeService.listNotices());
    }

    @PostMapping
    public ResponseEntity<?> saveNotice(@RequestBody NoticeSaveRequest request) {
        try {
            return ResponseEntity.ok(noticeService.saveNotice(request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteNotice(@PathVariable Long id) {
        noticeService.deleteNotice(id);
        return ResponseEntity.ok().build();
    }
}
