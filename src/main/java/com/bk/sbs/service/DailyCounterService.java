package com.bk.sbs.service;

import com.bk.sbs.entity.CommanderDailyCounter;
import com.bk.sbs.enums.EAchievementConditionType;
import com.bk.sbs.repository.CommanderDailyCounterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

// 일일퀘스트용 오늘(UTC) 카운터 누적/조회 — 이벤트 발생 지점에서 increment 한 줄만 호출
@Service
public class DailyCounterService {

    private final CommanderDailyCounterRepository commanderDailyCounterRepository;

    public DailyCounterService(CommanderDailyCounterRepository commanderDailyCounterRepository) {
        this.commanderDailyCounterRepository = commanderDailyCounterRepository;
    }

    // amount가 0 이하이면 기록하지 않음
    @Transactional
    public void increment(Long commanderId, EAchievementConditionType type, long amount) {
        if (amount <= 0) return;

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        commanderDailyCounterRepository.upsertAdd(commanderId, today, type.name(), amount);
    }

    // 지정 날짜의 전체 카운터(counterType → 값) — 목록 조회처럼 여러 항목을 판정할 때 1회 조회용
    @Transactional(readOnly = true)
    public Map<String, Long> getCounters(Long commanderId, LocalDate date) {
        Map<String, Long> result = new HashMap<>();
        for (CommanderDailyCounter counter : commanderDailyCounterRepository.findByCommanderIdAndCounterDate(commanderId, date)) {
            result.put(counter.getCounterType(), counter.getCounterValue());
        }
        return result;
    }

    @Transactional(readOnly = true)
    public long getCounter(Long commanderId, LocalDate date, EAchievementConditionType type) {
        return commanderDailyCounterRepository.findByCommanderIdAndCounterDateAndCounterType(commanderId, date, type.name())
                .map(CommanderDailyCounter::getCounterValue)
                .orElse(0L);
    }
}
