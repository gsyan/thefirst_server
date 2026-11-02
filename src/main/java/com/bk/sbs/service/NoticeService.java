package com.bk.sbs.service;

import com.bk.sbs.dto.NoticeItemDto;
import com.bk.sbs.dto.NoticeResponse;
import com.bk.sbs.dto.nogenerated.admin.NoticeAdminDto;
import com.bk.sbs.dto.nogenerated.admin.NoticeSaveRequest;
import com.bk.sbs.entity.CommanderNoticeSeen;
import com.bk.sbs.entity.Notice;
import com.bk.sbs.repository.CommanderNoticeSeenRepository;
import com.bk.sbs.repository.NoticeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class NoticeService {

    private final NoticeRepository noticeRepository;
    private final CommanderNoticeSeenRepository commanderNoticeSeenRepository;

    public NoticeService(NoticeRepository noticeRepository, CommanderNoticeSeenRepository commanderNoticeSeenRepository) {
        this.noticeRepository = noticeRepository;
        this.commanderNoticeSeenRepository = commanderNoticeSeenRepository;
    }

    // 관리자 — 전체 목록(비활성 포함)
    public List<NoticeAdminDto> listNotices() {
        List<Notice> notices = noticeRepository.findAllByOrderByDisplayOrderAsc();
        List<NoticeAdminDto> result = new ArrayList<>();
        for (Notice notice : notices) {
            result.add(toAdminDto(notice));
        }
        return result;
    }

    // 관리자 — id가 없으면 생성, 있으면 수정(수정 시 내용이 바뀌면 version 증가 → 이미 본 유저도 다시 노출)
    public NoticeAdminDto saveNotice(NoticeSaveRequest request) {
        Notice notice;
        if (request.getId() == null) {
            notice = new Notice();
            notice.setVersion(1);
        } else {
            notice = noticeRepository.findById(request.getId())
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 공지입니다: " + request.getId()));
            notice.setVersion(notice.getVersion() + 1);
        }

        notice.setTitle(request.getTitle());
        notice.setBody(request.getBody());
        notice.setEnabled(request.isEnabled());
        notice.setDisplayOrder(request.getDisplayOrder());
        notice.setUpdatedAt(Instant.now());

        notice = noticeRepository.save(notice);
        return toAdminDto(notice);
    }

    public void deleteNotice(Long id) {
        noticeRepository.deleteById(id);
    }

    // 클라 — 로그인 시 호출. 안 본(또는 버전이 바뀐) 활성 공지만 displayOrder 순으로 반환하고, 반환과 동시에 열람 처리
    @Transactional
    public NoticeResponse getUnseenNoticesAndMarkSeen(Long commanderId) {
        List<Notice> activeNotices = noticeRepository.findByEnabledTrueOrderByDisplayOrderAsc();
        if (activeNotices.isEmpty()) {
            return NoticeResponse.builder().notices(new ArrayList<>()).build();
        }

        Map<Long, CommanderNoticeSeen> seenByNoticeId = new HashMap<>();
        for (CommanderNoticeSeen seen : commanderNoticeSeenRepository.findByCommanderId(commanderId)) {
            seenByNoticeId.put(seen.getNoticeId(), seen);
        }

        List<NoticeItemDto> unseenItems = new ArrayList<>();
        for (Notice notice : activeNotices) {
            CommanderNoticeSeen seen = seenByNoticeId.get(notice.getId());
            boolean alreadySeenThisVersion = seen != null && seen.getSeenVersion() == notice.getVersion();
            if (alreadySeenThisVersion) continue;

            unseenItems.add(NoticeItemDto.builder()
                    .noticeId(notice.getId())
                    .version(notice.getVersion())
                    .title(notice.getTitle())
                    .body(notice.getBody())
                    .build());

            if (seen == null) {
                seen = new CommanderNoticeSeen(commanderId, notice.getId(), notice.getVersion());
            } else {
                seen.setSeenVersion(notice.getVersion());
                seen.setSeenAt(Instant.now());
            }
            commanderNoticeSeenRepository.save(seen);
        }

        return NoticeResponse.builder().notices(unseenItems).build();
    }

    private NoticeAdminDto toAdminDto(Notice notice) {
        NoticeAdminDto dto = new NoticeAdminDto();
        dto.setId(notice.getId());
        dto.setTitle(notice.getTitle());
        dto.setBody(notice.getBody());
        dto.setEnabled(notice.isEnabled());
        dto.setDisplayOrder(notice.getDisplayOrder());
        dto.setVersion(notice.getVersion());
        return dto;
    }
}
