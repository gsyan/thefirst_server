//--------------------------------------------------------------------------------------------------
package com.bk.sbs.service;

import com.bk.sbs.repository.CommanderDailyPlaytimeRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

@Service
public class PlaytimeService {

    // 하트비트 간격(30s)의 3배 — 이보다 gap이 크면 오프라인 구간으로 간주해 누적하지 않음
    @Value("${playtime.session-gap-seconds:90}")
    private long sessionGapSeconds;

    private final CommanderDailyPlaytimeRepository commanderDailyPlaytimeRepository;

    public PlaytimeService(CommanderDailyPlaytimeRepository commanderDailyPlaytimeRepository) {
        this.commanderDailyPlaytimeRepository = commanderDailyPlaytimeRepository;
    }

    // 하트비트마다 호출 — 직전 lastOnlineAt과 이번 하트비트 사이 간격을 플레이타임으로 누적
    public void accumulate(Long commanderId, Instant previousLastOnlineAt, Instant now) {
        if (previousLastOnlineAt == null) return;

        long gapSeconds = Duration.between(previousLastOnlineAt, now).getSeconds();
        if (gapSeconds <= 0) return;
        if (gapSeconds > sessionGapSeconds) return;

        LocalDate playDate = now.atZone(ZoneOffset.UTC).toLocalDate();
        commanderDailyPlaytimeRepository.upsertAddSeconds(commanderId, playDate, gapSeconds, now);
    }
}
