//--------------------------------------------------------------------------------------------------
package com.bk.sbs.repository;

import com.bk.sbs.entity.VipSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VipSubscriptionRepository extends JpaRepository<VipSubscription, Long> {
    Optional<VipSubscription> findByCommanderId(Long commanderId);
    boolean existsByPurchaseToken(String purchaseToken);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("DELETE FROM VipSubscription v WHERE v.commanderId = :commanderId")
    void deleteByCommanderId(@org.springframework.data.repository.query.Param("commanderId") Long commanderId);
}


