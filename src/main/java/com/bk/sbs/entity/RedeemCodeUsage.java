//--------------------------------------------------------------------------------------------------
package com.bk.sbs.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "redeem_code_usage", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"commanderId", "code"})
})
@Getter
@Setter
public class RedeemCodeUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long commanderId;

    // trim + 소문자로 정규화된 코드 원문
    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false)
    private Instant usedAt;

    public RedeemCodeUsage(Long commanderId, String code) {
        this.commanderId = commanderId;
        this.code = code;
        this.usedAt = Instant.now();
    }

    protected RedeemCodeUsage() {
    }
}
