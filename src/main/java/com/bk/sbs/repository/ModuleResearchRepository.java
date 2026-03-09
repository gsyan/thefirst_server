package com.bk.sbs.repository;

import com.bk.sbs.entity.ModuleResearch;
import com.bk.sbs.enums.EModuleType;
import com.bk.sbs.enums.EModuleSubType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ModuleResearchRepository extends JpaRepository<ModuleResearch, Long> {

    // 캐릭터의 모든 완료된 연구 조회 (모듈 + 문자열 기반 포함)
    List<ModuleResearch> findByCharacterIdAndResearchedTrue(Long characterId);

    // 특정 모듈이 개발되었는지 확인 (moduleType + moduleSubType 기반)
    Optional<ModuleResearch> findByCharacterIdAndModuleTypeAndModuleSubType(
            Long characterId,
            EModuleType moduleType,
            EModuleSubType moduleSubType
    );

    // 특정 모듈이 개발되었는지 여부만 확인
    boolean existsByCharacterIdAndModuleTypeAndModuleSubTypeAndResearchedTrue(
            Long characterId,
            EModuleType moduleType,
            EModuleSubType moduleSubType
    );

    // 문자열 기반 연구 ID 조회 (tech_level_N 등)
    Optional<ModuleResearch> findByCharacterIdAndResearchId(Long characterId, String researchId);

    // 특정 접두사로 시작하는 완료된 연구 목록 (예: "tech_level_" 접두사로 기술레벨 조회)
    List<ModuleResearch> findByCharacterIdAndResearchIdStartingWithAndResearchedTrue(Long characterId, String prefix);

    // researchId가 null이 아닌 완료된 연구 목록
    List<ModuleResearch> findByCharacterIdAndResearchIdIsNotNullAndResearchedTrue(Long characterId);
}
