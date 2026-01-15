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

    // 캐릭터의 모든 개발된 모듈 조회
    List<ModuleResearch> findByCharacterIdAndResearchedTrue(Long characterId);

    // 특정 모듈이 개발되었는지 확인
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
}
