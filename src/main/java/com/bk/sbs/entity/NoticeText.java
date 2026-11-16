package com.bk.sbs.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 공지 언어별 제목/본문 — (noticeId, locale) 유니크, locale은 클라 로케일 코드(en, ko 등)
@Entity
@Table(name = "notice_text")
@Getter
@Setter
@NoArgsConstructor
public class NoticeText {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "notice_id", nullable = false)
    private Long noticeId;

    @Column(nullable = false, length = 10)
    private String locale;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    public NoticeText(Long noticeId, String locale, String title, String body) {
        this.noticeId = noticeId;
        this.locale = locale;
        this.title = title;
        this.body = body;
    }
}
