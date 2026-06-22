//--------------------------------------------------------------------------------------------------
package com.bk.sbs.service;

import com.bk.sbs.dto.CommanderCreateRequest;
import com.bk.sbs.dto.CommanderRenameRequest;
import com.bk.sbs.dto.CommanderRenameResponse;
import com.bk.sbs.dto.CommanderResponse;
import com.bk.sbs.dto.CommanderInfoDto;
import com.bk.sbs.util.ProfanityFilter;
import com.bk.sbs.dto.ShipInfoDto;
import com.bk.sbs.dto.ZoneConfigData;
import com.bk.sbs.entity.Account;
import com.bk.sbs.entity.Commander;
import com.bk.sbs.enums.*;
import com.bk.sbs.exception.BusinessException;
import com.bk.sbs.exception.ServerErrorCode;
import com.bk.sbs.repository.AccountRepository;
import com.bk.sbs.repository.CommanderRepository;
import com.bk.sbs.repository.ClearedZoneRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class CommanderService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CommanderService.class);
    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9\\uAC00-\\uD7A3]{2,16}$");

    private final CommanderRepository commanderRepository;
    private final AccountRepository accountRepository;
    private final FleetService fleetService;
    private final ClearedZoneRepository clearedZoneRepository;
    private final StringRedisTemplate redisTemplate;
    private final GameDataService gameDataService;

@Value("${worldid}")
    private int worldId;

    public CommanderService(CommanderRepository commanderRepository, AccountRepository accountRepository, FleetService fleetService, ClearedZoneRepository clearedZoneRepository, StringRedisTemplate redisTemplate, GameDataService gameDataService) {
        this.commanderRepository = commanderRepository;
        this.accountRepository = accountRepository;
        this.fleetService = fleetService;
        this.clearedZoneRepository = clearedZoneRepository;
        this.redisTemplate = redisTemplate;
        this.gameDataService = gameDataService;
    }

    @Transactional
    public CommanderResponse createCommander(CommanderCreateRequest request) {
        Long accountId = Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.COMMANDER_CREATE_FAIL_ACCOUNT_NOT_FOUND));

        // commanderName이 null/empty면 자동 이름 모드 — "empty" 고정, 중복 검사 생략
        String requestedName = request.getCommanderName();
        boolean isAutoName = (requestedName == null || requestedName.isEmpty());
        if (isAutoName == false) {
            if (commanderRepository.existsByCommanderName(requestedName)) throw new BusinessException(ServerErrorCode.COMMANDER_CREATE_FAIL_NAME_DUPLICATE);
        }

        Commander commander = new Commander();
        commander.setAccountId(account.getId());
        // 자동 이름 모드: 충돌 없는 UUID 임시 이름으로 저장 → 이후 commander_+id로 교체
        commander.setCommanderName(isAutoName ? UUID.randomUUID().toString() : requestedName);
        commander.setModulePoint(0);        // 기본 beam unlock 1포인트 기함에 투입, 잔여 0
        commander.setModulePointMaxGot(1);  // 모듈 포인트 총 획득량 1 (beam unlock 반영)
        commander.setMineral(100);          // 기본 미네랄 100 지급
        Commander savedCommander = commanderRepository.save(commander);

        // 자동 이름: 저장 후 확정된 id로 commander_+id 설정 (유니크 보장)
        if (isAutoName == true) {
            savedCommander.setCommanderName("commander_" + savedCommander.getId());
            savedCommander = commanderRepository.save(savedCommander);
        }
        log.info("createCommander: accountId={}, commanderId={}, name={}", accountId, savedCommander.getId(), savedCommander.getCommanderName());

        // 커맨더 생성과 동시에 기본 함대 생성 및 활성화
        // 실패 시 전체 트랜잭션 롤백됨
        fleetService.createFleet(savedCommander.getId(), "Default Fleet", "Auto-generated default fleet.");
        fleetService.activateFirstFleet(savedCommander.getId());

        // commanderId = worldId(8비트) + id(56비트)
        return CommanderResponse.builder()
                .commanderId(((long) worldId << 56) | savedCommander.getId())
                .commanderName(savedCommander.getCommanderName())
                .build();
    }

    // 접속 시 lastOnlineAt 갱신 — collectDateTime은 ZoneService에서 캡 적용
    @Transactional
    public void updateLastOnline(Long commanderId) {
        Commander commander = commanderRepository.findByIdForUpdate(commanderId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.GET_COMMANDER_INFO_DTO_FAIL_COMMANDER_NOT_FOUND));

        commander.setLastOnlineAt(Instant.now());
        commanderRepository.save(commander);
    }

    @Transactional
    public CommanderInfoDto getCommanderInfoDto(Long commanderId) {
        Commander commander = commanderRepository.findById(commanderId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.GET_COMMANDER_INFO_DTO_FAIL_COMMANDER_NOT_FOUND));

        return CommanderInfoDto.builder()
                .commanderId(commanderId)
                .commanderName(commander.getCommanderName())
                .mineral(commander.getMineral())
                .techLevel(commander.getTechLevel())
                .techPoint(commander.getTechPoint())
                .modulePoint(commander.getModulePoint())
                .modulePointMaxGot(commander.getModulePointMaxGot())
                .pvpPoint(commander.getPvpPoint())
                .pvpPointMaxGot(commander.getPvpPointMaxGot())
                .pvpPointExpiry(commander.getPvpPointExpiry() != null ? commander.getPvpPointExpiry().toString() : null)
                .clearedZones(clearedZoneRepository.findZoneNamesByCommanderId(commanderId))
                .nameChangeCount(commander.getNameChangeCount())
                .build();
    }

    // 이름 유효성 검사 (형식·중복·비속어) — validate-name 엔드포인트용
    public boolean validateCommanderName(String name) {
        if (name == null || NAME_PATTERN.matcher(name).matches() == false)
            throw new BusinessException(ServerErrorCode.COMMANDER_VALIDATE_NAME_INVALID_FORMAT);
        if (commanderRepository.existsByCommanderName(name))
            throw new BusinessException(ServerErrorCode.COMMANDER_VALIDATE_NAME_DUPLICATE);
        if (ProfanityFilter.containsProfanity(name))
            throw new BusinessException(ServerErrorCode.COMMANDER_VALIDATE_NAME_PROFANITY);
        return true;
    }

    @Transactional
    public CommanderRenameResponse renameCommander(Long commanderId, CommanderRenameRequest request) {
        Commander commander = commanderRepository.findByIdForUpdate(commanderId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.COMMANDER_RENAME_FAIL_COMMANDER_NOT_FOUND));

        if (commander.getNameChangeCount() <= 0)
            throw new BusinessException(ServerErrorCode.COMMANDER_RENAME_FAIL_NO_REMAINING_COUNT);

        if (request.getNewName() == null || NAME_PATTERN.matcher(request.getNewName()).matches() == false)
            throw new BusinessException(ServerErrorCode.COMMANDER_RENAME_FAIL_INVALID_NAME);

        if (commanderRepository.existsByCommanderName(request.getNewName()))
            throw new BusinessException(ServerErrorCode.COMMANDER_RENAME_FAIL_NAME_DUPLICATE);

        if (ProfanityFilter.containsProfanity(request.getNewName()))
            throw new BusinessException(ServerErrorCode.COMMANDER_RENAME_FAIL_PROFANITY);

        commander.setCommanderName(request.getNewName());
        commander.setNameChangeCount(commander.getNameChangeCount() - 1);
        commanderRepository.save(commander);

        // Redis rank:name 해시 동기화 (랭킹 보드에서 이름이 즉시 반영되도록)
        redisTemplate.opsForHash().put("rank:name", commanderId.toString(), request.getNewName());

        return CommanderRenameResponse.builder()
                .commanderName(commander.getCommanderName())
                .nameChangeCount(commander.getNameChangeCount())
                .build();
    }

    @Transactional
    public int updateMineral(Long commanderId, int mineral) {
        Commander commander = commanderRepository.findByIdForUpdate(commanderId).orElseThrow(() -> new BusinessException(ServerErrorCode.UPDATE_MINERAL_FAIL_COMMANDER_NOT_FOUND));
        commander.setMineral(mineral);
        commander = commanderRepository.save(commander);
        return mineral;
    }

    @Transactional
    public int addMineral(Long commanderId, int amount) {
        Commander commander = commanderRepository.findByIdForUpdate(commanderId).orElseThrow(() -> new BusinessException(ServerErrorCode.ADD_MINERAL_FAIL_COMMANDER_NOT_FOUND));
        int before = commander.getMineral();
        commander.setMineral(before + amount);
        commander = commanderRepository.save(commander);
        return commander.getMineral();
    }

    @Transactional
    public int addTechPoint(Long commanderId, int amount) {
        Commander commander = commanderRepository.findByIdForUpdate(commanderId).orElseThrow(() -> new BusinessException(ServerErrorCode.ADD_MINERAL_FAIL_COMMANDER_NOT_FOUND));
        commander.setTechPoint(commander.getTechPoint() + amount);
        commander = commanderRepository.save(commander);
        return commander.getTechPoint();
    }

    @Transactional
    public int addModulePoint(Long commanderId, int amount) {
        Commander commander = commanderRepository.findByIdForUpdate(commanderId).orElseThrow(() -> new BusinessException(ServerErrorCode.ADD_MINERAL_FAIL_COMMANDER_NOT_FOUND));
        commander.setModulePoint(commander.getModulePoint() + amount);
        commander = commanderRepository.save(commander);
        return commander.getModulePoint();
    }

    @Transactional
    public int addModulePointMaxGot(Long commanderId, int amount) {
        Commander commander = commanderRepository.findByIdForUpdate(commanderId).orElseThrow(() -> new BusinessException(ServerErrorCode.ADD_MINERAL_FAIL_COMMANDER_NOT_FOUND));
        commander.setModulePointMaxGot(commander.getModulePointMaxGot() + amount);
        commander = commanderRepository.save(commander);
        return commander.getModulePointMaxGot();
    }

    @Transactional
    public int addPvpPoint(Long commanderId, int amount) {
        Commander commander = commanderRepository.findByIdForUpdate(commanderId).orElseThrow(() -> new BusinessException(ServerErrorCode.ADD_MINERAL_FAIL_COMMANDER_NOT_FOUND));
        commander.setPvpPoint(commander.getPvpPoint() + amount);
        commander = commanderRepository.save(commander);
        return commander.getPvpPoint();
    }

    @Transactional
    public int addPvpPointMaxGot(Long commanderId, int amount) {
        Commander commander = commanderRepository.findByIdForUpdate(commanderId).orElseThrow(() -> new BusinessException(ServerErrorCode.ADD_MINERAL_FAIL_COMMANDER_NOT_FOUND));
        commander.setPvpPointMaxGot(commander.getPvpPointMaxGot() + amount);
        commander = commanderRepository.save(commander);
        return commander.getPvpPointMaxGot();
    }

}


