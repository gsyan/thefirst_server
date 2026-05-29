//--------------------------------------------------------------------------------------------------
package com.bk.sbs.repository;

import com.bk.sbs.entity.VipSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VipSubscriptionRepository extends JpaRepository<VipSubscription, Long> {
    Optional<VipSubscription> findByCharacterId(Long characterId);
    boolean existsByPurchaseToken(String purchaseToken);
}
