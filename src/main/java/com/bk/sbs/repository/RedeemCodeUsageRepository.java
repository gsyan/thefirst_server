//--------------------------------------------------------------------------------------------------
package com.bk.sbs.repository;

import com.bk.sbs.entity.RedeemCodeUsage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RedeemCodeUsageRepository extends JpaRepository<RedeemCodeUsage, Long> {
    boolean existsByCommanderIdAndCode(Long commanderId, String code);
}
