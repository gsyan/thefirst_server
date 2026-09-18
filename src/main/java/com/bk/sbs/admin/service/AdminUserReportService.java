//--------------------------------------------------------------------------------------------------
package com.bk.sbs.admin.service;

import com.bk.sbs.dto.nogenerated.admin.CommanderDetailDto;
import com.bk.sbs.dto.nogenerated.admin.CommanderLoginHistoryDto;
import com.bk.sbs.dto.nogenerated.admin.CommanderSummaryDto;
import com.bk.sbs.dto.nogenerated.admin.DailyPlaytimeDto;
import com.bk.sbs.entity.Commander;
import com.bk.sbs.entity.CommanderDailyPlaytime;
import com.bk.sbs.repository.CommanderDailyPlaytimeRepository;
import com.bk.sbs.repository.CommanderLoginLogRepository;
import com.bk.sbs.repository.CommanderRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class AdminUserReportService {

    private static final int RECENT_DAYS_FOR_LIST = 7;
    private static final int RECENT_DAYS_FOR_DETAIL = 30;
    private static final int LOGIN_HISTORY_LIMIT = 20;

    private final CommanderRepository commanderRepository;
    private final CommanderLoginLogRepository commanderLoginLogRepository;
    private final CommanderDailyPlaytimeRepository commanderDailyPlaytimeRepository;

    public AdminUserReportService(CommanderRepository commanderRepository,
                                   CommanderLoginLogRepository commanderLoginLogRepository,
                                   CommanderDailyPlaytimeRepository commanderDailyPlaytimeRepository) {
        this.commanderRepository = commanderRepository;
        this.commanderLoginLogRepository = commanderLoginLogRepository;
        this.commanderDailyPlaytimeRepository = commanderDailyPlaytimeRepository;
    }

    public Page<CommanderSummaryDto> search(String query, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Commander> commanderPage = query == null || query.isBlank()
                ? commanderRepository.findAll(pageable)
                : commanderRepository.findByCommanderNameContainingIgnoreCase(query.trim(), pageable);

        return commanderPage.map(this::toSummaryDto);
    }

    private CommanderSummaryDto toSummaryDto(Commander commander) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate fromDate = today.minusDays(RECENT_DAYS_FOR_LIST - 1);
        List<CommanderDailyPlaytime> recentPlaytime = commanderDailyPlaytimeRepository
                .findByCommanderIdAndPlayDateBetweenOrderByPlayDateAsc(commander.getId(), fromDate, today);

        int recentSevenDayPlayedSeconds = recentPlaytime.stream().mapToInt(CommanderDailyPlaytime::getPlayedSeconds).sum();

        return new CommanderSummaryDto(
                commander.getId(),
                commander.getCommanderName(),
                commander.getCommanderLevel(),
                commander.getLastOnlineAt(),
                recentSevenDayPlayedSeconds);
    }

    public CommanderDetailDto getDetail(Long commanderId) {
        Commander commander = commanderRepository.findById(commanderId)
                .orElseThrow(() -> new IllegalArgumentException("커맨더를 찾을 수 없습니다: " + commanderId));

        List<CommanderLoginHistoryDto> loginHistory = commanderLoginLogRepository
                .findByCommanderIdOrderByLoginAtDesc(commanderId, PageRequest.of(0, LOGIN_HISTORY_LIMIT))
                .map(log -> new CommanderLoginHistoryDto(log.getLoginAt(), log.getLoginType()))
                .toList();

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate fromDate = today.minusDays(RECENT_DAYS_FOR_DETAIL - 1);
        List<DailyPlaytimeDto> dailyPlaytime = commanderDailyPlaytimeRepository
                .findByCommanderIdAndPlayDateBetweenOrderByPlayDateAsc(commanderId, fromDate, today)
                .stream()
                .map(p -> new DailyPlaytimeDto(p.getPlayDate(), p.getPlayedSeconds()))
                .toList();

        return new CommanderDetailDto(
                commander.getId(),
                commander.getCommanderName(),
                commander.getCommanderLevel(),
                commander.getLastOnlineAt(),
                loginHistory,
                dailyPlaytime);
    }
}
