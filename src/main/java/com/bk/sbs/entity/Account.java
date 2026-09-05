//--------------------------------------------------------------------------------------------------
package com.bk.sbs.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Getter
@Setter
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String email;  // 이메일/비밀번호 로그인 계정만 값을 가짐. 게스트/구글 전용 계정은 null

    @Column(nullable = false)
    private String password;

    @Column(unique = true)
    private String googleId;  // 구글 계정 연동 시 Google UID 저장 (null = 미연동)

    @Column(unique = true)
    private String guestId;  // 게스트 로그인 식별자. 구글 연동 중이면 반드시 null

    @Column(length = 255)
    private String guestSecret;  // 게스트 로그인 자격증명(BCrypt 해시). 구글 연동 중이면 반드시 null

    @Column(nullable = false)
    private boolean deleted = false;

    @Column(nullable = false)
    private Instant dateTime = Instant.now();
}