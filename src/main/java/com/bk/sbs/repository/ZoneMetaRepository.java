// ZoneMeta 테이블 접근 — 캐릭터별 존 탐험 메타데이터 (enemyRestoreTime 등)
package com.bk.sbs.repository;

import com.bk.sbs.entity.ZoneMeta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ZoneMetaRepository extends JpaRepository<ZoneMeta, Long> {

    Optional<ZoneMeta> findByCharacterId(Long characterId);
}
