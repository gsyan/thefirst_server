package com.bk.sbs.service;

import com.bk.sbs.entity.CommanderHullArchetypeProgress;
import com.bk.sbs.repository.CommanderHullArchetypeProgressRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

// 아키타입 패시브 언락 조건 카운터 — 서버가 자연히 셀 수 있는 카운터(존런 완료 등)는 이 서비스에서 직접 증가,
// 전투 중 발생하는 카운터(적 함선 파괴 등)는 클라가 집계해서 런 종료시 일괄 보고(reportCounter)하는 용도로 사용
@Service
public class HullArchetypeProgressService {

    private final CommanderHullArchetypeProgressRepository repository;

    public HullArchetypeProgressService(CommanderHullArchetypeProgressRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<CommanderHullArchetypeProgress> getProgress(Long commanderId) {
        return repository.findByCommanderId(commanderId);
    }

    // delta만큼 누적 — 기록이 없으면 새로 생성
    @Transactional
    public void incrementCounter(Long commanderId, String hullArchetype, String counterType, long delta) {
        if (delta == 0) return;

        Optional<CommanderHullArchetypeProgress> existing =
                repository.findByCommanderIdAndHullArchetypeAndCounterType(commanderId, hullArchetype, counterType);

        if (existing.isPresent()) {
            CommanderHullArchetypeProgress progress = existing.get();
            progress.setCounterValue(progress.getCounterValue() + delta);
            repository.save(progress);
        } else {
            repository.save(new CommanderHullArchetypeProgress(commanderId, hullArchetype, counterType, delta));
        }
    }
}
