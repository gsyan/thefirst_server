package com.bk.sbs.repository;

import com.bk.sbs.entity.ModuleResearch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ModuleResearchRepository extends JpaRepository<ModuleResearch, Long> {

    // 문자열 기반 연구 ID 조회 (tech_level_N 등)
    Optional<ModuleResearch> findByCharacterIdAndResearchId(Long characterId, String researchId);

    // 특정 접두사로 시작하는 완료된 연구 목록
    List<ModuleResearch> findByCharacterIdAndResearchIdStartingWithAndResearchedTrue(Long characterId, String prefix);

    // researchId가 null이 아닌 완료된 연구 목록
    List<ModuleResearch> findByCharacterIdAndResearchIdIsNotNullAndResearchedTrue(Long characterId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("DELETE FROM ModuleResearch m WHERE m.characterId = :characterId")
    void deleteByCharacterId(@org.springframework.data.repository.query.Param("characterId") Long characterId);
}
