//--------------------------------------------------------------------------------------------------
package com.bk.sbs.repository;

import com.bk.sbs.entity.Progress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProgressRepository extends JpaRepository<Progress, Long> {

    List<Progress> findByCharacterIdAndCategory(Long characterId, String category);

    Optional<Progress> findByCharacterIdAndCategoryAndProgressKey(Long characterId, String category, String progressKey);

    boolean existsByCharacterIdAndCategoryAndProgressKey(Long characterId, String category, String progressKey);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("DELETE FROM Progress p WHERE p.characterId = :characterId")
    void deleteByCharacterId(@org.springframework.data.repository.query.Param("characterId") Long characterId);
}
