//--------------------------------------------------------------------------------------------------
package com.bk.sbs.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "vip_subscription")
@Getter
@Setter
@NoArgsConstructor
public class VipSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long commanderId;

    @Column(nullable = false)
    private Instant vipExpiry;

    @Column(nullable = false, length = 512)
    private String purchaseToken;

    @Column(nullable = false, length = 32)
    private String platform;

    @Column(nullable = false)
    private Instant updatedAt;

    public VipSubscription(Long commanderId, Instant vipExpiry, String purchaseToken, String platform) {
        this.commanderId = commanderId;
        this.vipExpiry = vipExpiry;
        this.purchaseToken = purchaseToken;
        this.platform = platform;
        this.updatedAt = Instant.now();
    }
}

