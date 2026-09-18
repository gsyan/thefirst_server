//--------------------------------------------------------------------------------------------------
package com.bk.sbs.repository;

import com.bk.sbs.entity.CommanderLoginLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommanderLoginLogRepository extends JpaRepository<CommanderLoginLog, Long> {
    Page<CommanderLoginLog> findByCommanderIdOrderByLoginAtDesc(Long commanderId, Pageable pageable);
}
