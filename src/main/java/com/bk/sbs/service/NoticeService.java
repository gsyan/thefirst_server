package com.bk.sbs.service;

import com.bk.sbs.dto.NoticeItemDto;
import com.bk.sbs.dto.NoticeReadRequest;
import com.bk.sbs.dto.NoticeReadResponse;
import com.bk.sbs.dto.NoticeResponse;
import com.bk.sbs.dto.NoticeTextDto;
import com.bk.sbs.dto.nogenerated.admin.NoticeAdminDto;
import com.bk.sbs.dto.nogenerated.admin.NoticeSaveRequest;
import com.bk.sbs.entity.CommanderNoticeSeen;
import com.bk.sbs.entity.Notice;
import com.bk.sbs.entity.NoticeText;
import com.bk.sbs.repository.CommanderNoticeSeenRepository;
import com.bk.sbs.repository.NoticeRepository;
import com.bk.sbs.repository.NoticeTextRepository;
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
    private final NoticeTextRepository noticeTextRepository;
    private final CommanderNoticeSeenRepository commanderNoticeSeenRepository;

    public NoticeService(NoticeRepository noticeRepository, NoticeTextRepository noticeTextRepository,
                         CommanderNoticeSeenRepository commanderNoticeSeenRepository) {
        this.noticeRepository = noticeRepository;
        this.noticeTextRepository = noticeTextRepository;
        this.commanderNoticeSeenRepository = commanderNoticeSeenRepository;
    }

    // 관리자 — 전체 목록(비활성 포함)
    public List<NoticeAdminDto> listNotices() {
        List<Notice> notices = noticeRepository.findAllByOrderByDisplayOrderAsc();
        Map<Long, List<NoticeTextDto>> textsByNoticeId = loadTextsByNoticeId(notices);

        List<NoticeAdminDto> result = new ArrayList<>();
        for (Notice notice : notices) {
            result.add(toAdminDto(notice, textsByNoticeId.getOrDefault(notice.getId(), new ArrayList<>())));
        }
        return result;
    }

    // 관리자 — id가 없으면 생성, 있으면 수정(수정 시 version 증가 → 이미 본 유저도 다시 노출). 언어별 본문은 통째로 교체
    @Transactional
    public NoticeAdminDto saveNotice(NoticeSaveRequest request) {
        List<NoticeTextDto> validTexts = filterValidTexts(request.getTexts());
        if (validTexts.isEmpty()) {
            throw new IllegalArgumentException("제목이 입력된 언어가 하나 이상 있어야 합니다.");
        }

        Notice notice;
        if (request.getId() == null) {
            notice = new Notice();
            notice.setVersion(1);
        } else {
            notice = noticeRepository.findById(request.getId())
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 공지입니다: " + request.getId()));
            notice.setVersion(notice.getVersion() + 1);
        }

        notice.setEnabled(request.isEnabled());
        notice.setDisplayOrder(request.getDisplayOrder());
        notice.setUpdatedAt(Instant.now());
        notice = noticeRepository.save(notice);

        noticeTextRepository.deleteAllByNoticeId(notice.getId());
        for (NoticeTextDto text : validTexts) {
            noticeTextRepository.save(new NoticeText(notice.getId(), text.getLocale(), text.getTitle(), text.getBody()));
        }

        return toAdminDto(notice, validTexts);
    }

    @Transactional
    public void deleteNotice(Long id) {
        noticeTextRepository.deleteAllByNoticeId(id);
        commanderNoticeSeenRepository.deleteAllByNoticeId(id);
        noticeRepository.deleteById(id);
    }

    // 클라 — 활성 공지 전체를 displayOrder 순으로, 항목마다 이 지휘관의 읽음 여부(현재 버전 본문을 열었는지)와 함께 반환
    public NoticeResponse getNoticeList(Long commanderId) {
        List<Notice> activeNotices = noticeRepository.findByEnabledTrueOrderByDisplayOrderAsc();
        Map<Long, CommanderNoticeSeen> seenByNoticeId = loadSeenByNoticeId(commanderId);
        Map<Long, List<NoticeTextDto>> textsByNoticeId = loadTextsByNoticeId(activeNotices);

        List<NoticeItemDto> items = new ArrayList<>();
        for (Notice notice : activeNotices) {
            boolean isRead = isReadCurrentVersion(notice, seenByNoticeId.get(notice.getId()));
            items.add(toItemDto(notice, textsByNoticeId.getOrDefault(notice.getId(), new ArrayList<>()), isRead));
        }
        return NoticeResponse.builder().notices(items).build();
    }

    // 클라 — 본문을 열었을 때 호출. 클라가 본 버전이 현재 버전과 같을 때만 읽음 처리(열람 중 공지가 수정됐으면 안 읽은 상태 유지)
    @Transactional
    public NoticeReadResponse markRead(Long commanderId, NoticeReadRequest request) {
        Notice notice = request.getNoticeId() != null ? noticeRepository.findById(request.getNoticeId()).orElse(null) : null;
        boolean isReadableNotice = notice != null && notice.isEnabled() == true;
        boolean isCurrentVersion = isReadableNotice == true && request.getVersion() != null && request.getVersion() == notice.getVersion();
        if (isCurrentVersion == true) {
            CommanderNoticeSeen seen = commanderNoticeSeenRepository.findByCommanderIdAndNoticeId(commanderId, notice.getId()).orElse(null);
            if (seen == null) {
                seen = new CommanderNoticeSeen(commanderId, notice.getId(), notice.getVersion());
            } else {
                seen.setSeenVersion(notice.getVersion());
                seen.setSeenAt(Instant.now());
            }
            commanderNoticeSeenRepository.save(seen);
        }

        boolean hasUnread = hasUnreadNotice(commanderId);
        return NoticeReadResponse.builder().hasUnread(hasUnread).build();
    }

    private boolean hasUnreadNotice(Long commanderId) {
        List<Notice> activeNotices = noticeRepository.findByEnabledTrueOrderByDisplayOrderAsc();
        Map<Long, CommanderNoticeSeen> seenByNoticeId = loadSeenByNoticeId(commanderId);
        for (Notice notice : activeNotices) {
            boolean isRead = isReadCurrentVersion(notice, seenByNoticeId.get(notice.getId()));
            if (isRead == false) return true;
        }
        return false;
    }

    // 이 지휘관의 열람 기록 전체를 한 번에 조회해 공지 id별로 묶음(활성 공지 목록과 메모리상 대조, N+1 방지)
    private Map<Long, CommanderNoticeSeen> loadSeenByNoticeId(Long commanderId) {
        Map<Long, CommanderNoticeSeen> result = new HashMap<>();
        for (CommanderNoticeSeen seen : commanderNoticeSeenRepository.findByCommanderId(commanderId)) {
            result.put(seen.getNoticeId(), seen);
        }
        return result;
    }

    private boolean isReadCurrentVersion(Notice notice, CommanderNoticeSeen seen) {
        return seen != null && seen.getSeenVersion() == notice.getVersion();
    }

    // 공지 여러 개의 언어별 본문을 한 번에 조회해 공지 id별로 묶음(N+1 방지)
    private Map<Long, List<NoticeTextDto>> loadTextsByNoticeId(List<Notice> notices) {
        Map<Long, List<NoticeTextDto>> result = new HashMap<>();
        if (notices.isEmpty()) return result;

        List<Long> noticeIds = new ArrayList<>();
        for (Notice notice : notices) {
            noticeIds.add(notice.getId());
        }
        for (NoticeText text : noticeTextRepository.findByNoticeIdIn(noticeIds)) {
            result.computeIfAbsent(text.getNoticeId(), key -> new ArrayList<>()).add(toTextDto(text));
        }
        return result;
    }

    // locale과 제목이 비어있는 언어는 저장하지 않음(운영툴에서 일부 언어만 입력하는 경우)
    private List<NoticeTextDto> filterValidTexts(List<NoticeTextDto> texts) {
        List<NoticeTextDto> result = new ArrayList<>();
        if (texts == null) return result;

        for (NoticeTextDto text : texts) {
            if (text == null) continue;
            boolean hasLocale = text.getLocale() != null && text.getLocale().isBlank() == false;
            boolean hasTitle = text.getTitle() != null && text.getTitle().isBlank() == false;
            if (hasLocale == false || hasTitle == false) continue;

            String body = text.getBody() != null ? text.getBody() : "";
            result.add(NoticeTextDto.builder()
                    .locale(text.getLocale().trim())
                    .title(text.getTitle().trim())
                    .body(body)
                    .build());
        }
        return result;
    }

    private NoticeTextDto toTextDto(NoticeText text) {
        return NoticeTextDto.builder()
                .locale(text.getLocale())
                .title(text.getTitle())
                .body(text.getBody())
                .build();
    }

    private NoticeItemDto toItemDto(Notice notice, List<NoticeTextDto> texts, boolean isRead) {
        return NoticeItemDto.builder()
                .noticeId(notice.getId())
                .version(notice.getVersion())
                .texts(texts)
                .isRead(isRead)
                .build();
    }

    private NoticeAdminDto toAdminDto(Notice notice, List<NoticeTextDto> texts) {
        NoticeAdminDto dto = new NoticeAdminDto();
        dto.setId(notice.getId());
        dto.setTexts(texts);
        dto.setEnabled(notice.isEnabled());
        dto.setDisplayOrder(notice.getDisplayOrder());
        dto.setVersion(notice.getVersion());
        return dto;
    }
}
