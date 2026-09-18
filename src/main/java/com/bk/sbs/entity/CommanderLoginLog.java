//--------------------------------------------------------------------------------------------------
package com.bk.sbs.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "commander_login_log")
@Getter
@Setter
public class CommanderLoginLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long accountId;

    private Long commanderId;  // 로그인 시점에 커맨더가 없으면 null

    @Column(nullable = false, length = 20)
    private String loginType;  // EMAIL / GOOGLE / GUEST

    @Column(nullable = false)
    private Instant loginAt;

    public CommanderLoginLog(Long accountId, Long commanderId, String loginType) {
        this.accountId = accountId;
        this.commanderId = commanderId;
        this.loginType = loginType;
        this.loginAt = Instant.now();
    }

    protected CommanderLoginLog() {
    }
}
