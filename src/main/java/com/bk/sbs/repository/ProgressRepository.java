//--------------------------------------------------------------------------------------------------
package com.bk.sbs.repository;

import com.bk.sbs.entity.Progress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProgressRepository extends JpaRepository<Progress, Long> {

    List<Progress> findByCommanderIdAndCategory(Long commanderId, String category);

    Optional<Progress> findByCommanderIdAndCategoryAndProgressKey(Long commanderId, String category, String progressKey);

    boolean existsByCommanderIdAndCategoryAndProgressKey(Long commanderId, String category, String progressKey);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("DELETE FROM Progress p WHERE p.commanderId = :commanderId")
    void deleteByCommanderId(@org.springframework.data.repository.query.Param("commanderId") Long commanderId);
}


