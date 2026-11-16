//--------------------------------------------------------------------------------------------------
package com.bk.sbs.controller;

import com.bk.sbs.dto.NoticeReadRequest;
import com.bk.sbs.dto.NoticeReadResponse;
import com.bk.sbs.dto.NoticeResponse;
import com.bk.sbs.dto.nogenerated.ApiResponse;
import com.bk.sbs.security.CommanderId;
import com.bk.sbs.service.NoticeService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notice")
public class NoticeController {

    private final NoticeService noticeService;

    public NoticeController(NoticeService noticeService) {
        this.noticeService = noticeService;
    }

    // 활성 공지 전체 + 항목별 읽음 여부(로그인 시 자동 팝업 판단, 공지 버튼/목록 레드닷)
    @GetMapping("/list")
    public ApiResponse<NoticeResponse> getNoticeList(@CommanderId Long commanderId) {
        NoticeResponse response = noticeService.getNoticeList(commanderId);
        return ApiResponse.success(response);
    }

    // 본문을 열었을 때 읽음 처리
    @PostMapping("/read")
    public ApiResponse<NoticeReadResponse> readNotice(@CommanderId Long commanderId, @RequestBody NoticeReadRequest request) {
        NoticeReadResponse response = noticeService.markRead(commanderId, request);
        return ApiResponse.success(response);
    }
}
