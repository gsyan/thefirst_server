//--------------------------------------------------------------------------------------------------
package com.bk.sbs.service;

import com.bk.sbs.dto.CharacterCreateRequest;
import com.bk.sbs.dto.CharacterRenameRequest;
import com.bk.sbs.dto.CharacterRenameResponse;
import com.bk.sbs.dto.CharacterResponse;
import com.bk.sbs.dto.CharacterInfoDto;
import com.bk.sbs.util.ProfanityFilter;
import com.bk.sbs.dto.ShipInfoDto;
import com.bk.sbs.dto.ZoneConfigData;
import com.bk.sbs.entity.Account;
import com.bk.sbs.entity.Character;
import com.bk.sbs.entity.ModuleResearch;
import com.bk.sbs.enums.*;
import com.bk.sbs.exception.BusinessException;
import com.bk.sbs.exception.ServerErrorCode;
import com.bk.sbs.entity.ClearedZone;
import com.bk.sbs.entity.ZoneMeta;
import com.bk.sbs.repository.AccountRepository;
import com.bk.sbs.repository.CharacterRepository;
import com.bk.sbs.repository.ClearedZoneRepository;
import com.bk.sbs.repository.ModuleResearchRepository;
import com.bk.sbs.repository.ZoneMetaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class CharacterService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CharacterService.class);

    private final CharacterRepository characterRepository;
    private final AccountRepository accountRepository;
    private final FleetService fleetService;
    private final ModuleResearchRepository moduleResearchRepository;
    private final ClearedZoneRepository clearedZoneRepository;
    private final ZoneMetaRepository zoneMetaRepository;
    private final StringRedisTemplate redisTemplate;
    private final GameDataService gameDataService;

@Value("${worldid}")
    private int worldId;

    public CharacterService(CharacterRepository characterRepository, AccountRepository accountRepository, FleetService fleetService, ModuleResearchRepository moduleResearchRepository, ClearedZoneRepository clearedZoneRepository, ZoneMetaRepository zoneMetaRepository, StringRedisTemplate redisTemplate, GameDataService gameDataService) {
        this.characterRepository = characterRepository;
        this.accountRepository = accountRepository;
        this.fleetService = fleetService;
        this.moduleResearchRepository = moduleResearchRepository;
        this.clearedZoneRepository = clearedZoneRepository;
        this.zoneMetaRepository = zoneMetaRepository;
        this.redisTemplate = redisTemplate;
        this.gameDataService = gameDataService;
    }

    @Transactional
    public CharacterResponse createCharacter(CharacterCreateRequest request) {
        Long accountId = Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.CHARACTER_CREATE_FAIL_ACCOUNT_NOT_FOUND));

        // characterName이 null/empty면 자동 이름 모드 — "empty" 고정, 중복 검사 생략
        String requestedName = request.getCharacterName();
        boolean isAutoName = (requestedName == null || requestedName.isEmpty());
        if (isAutoName == false) {
            if (characterRepository.existsByCharacterName(requestedName)) throw new BusinessException(ServerErrorCode.CHARACTER_CREATE_FAIL_NAME_DUPLICATE);
        }

        Character character = new Character();
        character.setAccountId(account.getId());
        // 자동 이름 모드: 충돌 없는 UUID 임시 이름으로 저장 → 이후 empty_+id로 교체
        character.setCharacterName(isAutoName ? UUID.randomUUID().toString() : requestedName);
        character.setMineral(5100L);  // 기본미네랄 5100 지급
        Character savedCharacter = characterRepository.save(character);

        // 자동 이름: 저장 후 확정된 id로 empty_+id 설정 (유니크 보장)
        if (isAutoName == true) {
            savedCharacter.setCharacterName("empty_" + savedCharacter.getId());
            savedCharacter = characterRepository.save(savedCharacter);
        }
        log.info("createCharacter: accountId={}, characterId={}, name={}", accountId, savedCharacter.getId(), savedCharacter.getCharacterName());

        // 캐릭터 생성과 동시에 기본 함대 생성 및 활성화
        // 실패 시 전체 트랜잭션 롤백됨
        fleetService.createFleet(savedCharacter.getId(), "Default Fleet", "Auto-generated default fleet.");
        fleetService.activateFirstFleet(savedCharacter.getId());

        // 기본 기술레벨 1 초기화
        initializeDefaultTechLevel(savedCharacter.getId());

//        // Redis에 캐릭터 생성 로그 남기기 (테스트용)
//        try {
//            String logKey = "log:character:create:" + savedCharacter.getId();
//            String logValue = String.format("Character created: id=%d, name=%s, time=%s",
//                    savedCharacter.getId(), savedCharacter.getCharacterName(), LocalDateTime.now());
//            redisTemplate.opsForValue().set(logKey, logValue);
//        } catch (Exception e) {
//            // Redis 로그 실패해도 트랜잭션은 계속 진행
//            System.err.println("Failed to log character creation to Redis: " + e.getMessage());
//        }

        // characterId = worldId(8비트) + id(56비트)
        return CharacterResponse.builder()
                .characterId(((long) worldId << 56) | savedCharacter.getId())
                .characterName(savedCharacter.getCharacterName())
                .build();
    }

    // 신규 캐릭터 기본 기술레벨 1 완료 처리
    private void initializeDefaultTechLevel(Long characterId) {
        LocalDateTime now = LocalDateTime.now();
        ModuleResearch techLevel1 = new ModuleResearch();
        techLevel1.setCharacterId(characterId);
        techLevel1.setResearchId("tech_level_1");
        techLevel1.setResearched(true);
        techLevel1.setCreated(now);
        techLevel1.setModified(now);
        moduleResearchRepository.save(techLevel1);
    }

    // 접속 시 lastOnlineAt 갱신 — collectDateTime은 ZoneService에서 캡 적용
    @Transactional
    public void updateLastOnline(Long characterId) {
        Character character = characterRepository.findByIdForUpdate(characterId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.GET_CHARACTER_INFO_DTO_FAIL_CHARACTER_NOT_FOUND));

        character.setLastOnlineAt(Instant.now());
        characterRepository.save(character);
    }

    @Transactional
    public CharacterInfoDto getCharacterInfoDto(Long characterId) {
        Character character = characterRepository.findById(characterId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.GET_CHARACTER_INFO_DTO_FAIL_CHARACTER_NOT_FOUND));

        ZoneMeta zoneMeta = zoneMetaRepository.findByCharacterId(characterId).orElse(null);
        processEnemyRestore(characterId, zoneMeta);

        return CharacterInfoDto.builder()
                .characterId(characterId)
                .characterName(character.getCharacterName())
                .mineral(character.getMineral())
                .mineralRare(character.getMineralRare())
                .mineralExotic(character.getMineralExotic())
                .mineralDark(character.getMineralDark())
                .clearedZones(clearedZoneRepository.findZoneNamesByCharacterId(characterId))
                .collectDateTime(character.getCollectDateTime() != null ? character.getCollectDateTime().toString() : null)
                .nameChangeCount(character.getNameChangeCount())
                .build();
    }

    // 접속 시 24h 경과 여부 체크 — zone 2+ 활성 클리어 존 중 랜덤 하나를 수복 상태로 전환
    private void processEnemyRestore(Long characterId, ZoneMeta zoneMeta) {
        if (zoneMeta == null || zoneMeta.getEnemyRestoreTime() == null) return;
        if (ChronoUnit.HOURS.between(zoneMeta.getEnemyRestoreTime(), Instant.now()) < 24) return;

        List<ClearedZone> allActive = clearedZoneRepository.findActiveByCharacterId(characterId);

        // 클리어된 최고 그룹 번호 산출
        int maxGroup = allActive.stream()
                .mapToInt(cz -> parseZoneGroup(cz.getZoneName()))
                .max().orElse(0);

        if (maxGroup < 2) return;

        // 최고 그룹 존만 대상으로 한정
        List<ClearedZone> candidates = allActive.stream()
                .filter(cz -> parseZoneGroup(cz.getZoneName()) == maxGroup)
                .collect(java.util.stream.Collectors.toList());

        if (candidates.isEmpty()) return;

        ClearedZone target = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        target.setRestored(true);
        target.setRestoredAt(Instant.now());
        clearedZoneRepository.save(target);

        zoneMeta.setEnemyRestoreTime(Instant.now());
        zoneMetaRepository.save(zoneMeta);
    }

    private int parseZoneGroup(String zoneName) {
        if (zoneName == null || zoneName.isEmpty()) return 0;
        int idx = zoneName.indexOf('-');
        if (idx <= 0) return 0;
        try { return Integer.parseInt(zoneName.substring(0, idx)); } catch (NumberFormatException e) { return 0; }
    }

    // 이름 유효성 검사 (중복·비속어) — validate-name 엔드포인트용
    public boolean validateCharacterName(String name) {
        if (characterRepository.existsByCharacterName(name))
            throw new BusinessException(ServerErrorCode.CHARACTER_VALIDATE_NAME_DUPLICATE);
        if (ProfanityFilter.containsProfanity(name))
            throw new BusinessException(ServerErrorCode.CHARACTER_VALIDATE_NAME_PROFANITY);
        return true;
    }

    @Transactional
    public CharacterRenameResponse renameCharacter(Long characterId, CharacterRenameRequest request) {
        Character character = characterRepository.findByIdForUpdate(characterId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.CHARACTER_RENAME_FAIL_CHARACTER_NOT_FOUND));

        if (character.getNameChangeCount() <= 0)
            throw new BusinessException(ServerErrorCode.CHARACTER_RENAME_FAIL_NO_REMAINING_COUNT);

        if (characterRepository.existsByCharacterName(request.getNewName()))
            throw new BusinessException(ServerErrorCode.CHARACTER_RENAME_FAIL_NAME_DUPLICATE);

        if (ProfanityFilter.containsProfanity(request.getNewName()))
            throw new BusinessException(ServerErrorCode.CHARACTER_RENAME_FAIL_PROFANITY);

        character.setCharacterName(request.getNewName());
        character.setNameChangeCount(character.getNameChangeCount() - 1);
        characterRepository.save(character);

        // Redis rank:name 해시 동기화 (랭킹 보드에서 이름이 즉시 반영되도록)
        redisTemplate.opsForHash().put("rank:name", characterId.toString(), request.getNewName());

        return CharacterRenameResponse.builder()
                .characterName(character.getCharacterName())
                .nameChangeCount(character.getNameChangeCount())
                .build();
    }

    @Transactional
    public Long updateMineral(Long characterId, Long mineral) {
        Character character = characterRepository.findByIdForUpdate(characterId).orElseThrow(() -> new BusinessException(ServerErrorCode.UPDATE_MINERAL_FAIL_CHARACTER_NOT_FOUND));
        character.setMineral(mineral);
        character = characterRepository.save(character);
        return mineral;
    }

    @Transactional
    public Long addMineral(Long characterId, Long amount) {
        Character character = characterRepository.findByIdForUpdate(characterId).orElseThrow(() -> new BusinessException(ServerErrorCode.ADD_MINERAL_FAIL_CHARACTER_NOT_FOUND));
        Long before = character.getMineral();
        character.setMineral(before + amount);
        character = characterRepository.save(character);
        return character.getMineral();
    }

    @Transactional
    public Long updateMineralRare(Long characterId, Long mineralRare) {
        Character character = characterRepository.findByIdForUpdate(characterId).orElseThrow(() -> new BusinessException(ServerErrorCode.UPDATE_MINERAL_RARE_FAIL_CHARACTER_NOT_FOUND));
        character.setMineralRare(mineralRare);
        character = characterRepository.save(character);
        return mineralRare;
    }

    @Transactional
    public Long addMineralRare(Long characterId, Long amount) {
        Character character = characterRepository.findByIdForUpdate(characterId).orElseThrow(() -> new BusinessException(ServerErrorCode.ADD_MINERAL_RARE_FAIL_CHARACTER_NOT_FOUND));
        Long before = character.getMineralRare();
        character.setMineralRare(before + amount);
        character = characterRepository.save(character);
        return character.getMineralRare();
    }

    @Transactional
    public Long updateMineralExotic(Long characterId, Long mineralExotic) {
        Character character = characterRepository.findByIdForUpdate(characterId).orElseThrow(() -> new BusinessException(ServerErrorCode.UPDATE_MINERAL_EXOTIC_FAIL_CHARACTER_NOT_FOUND));
        character.setMineralExotic(mineralExotic);
        character = characterRepository.save(character);
        return mineralExotic;
    }

    @Transactional
    public Long addMineralExotic(Long characterId, Long amount) {
        Character character = characterRepository.findByIdForUpdate(characterId).orElseThrow(() -> new BusinessException(ServerErrorCode.ADD_MINERAL_EXOTIC_FAIL_CHARACTER_NOT_FOUND));
        Long before = character.getMineralExotic();
        character.setMineralExotic(before + amount);
        character = characterRepository.save(character);
        return character.getMineralExotic();
    }

    @Transactional
    public Long updateMineralDark(Long characterId, Long mineralDark) {
        Character character = characterRepository.findByIdForUpdate(characterId).orElseThrow(() -> new BusinessException(ServerErrorCode.UPDATE_MINERAL_DARK_FAIL_CHARACTER_NOT_FOUND));
        character.setMineralDark(mineralDark);
        character = characterRepository.save(character);
        return mineralDark;
    }

    @Transactional
    public Long addMineralDark(Long characterId, Long amount) {
        Character character = characterRepository.findByIdForUpdate(characterId).orElseThrow(() -> new BusinessException(ServerErrorCode.ADD_MINERAL_DARK_FAIL_CHARACTER_NOT_FOUND));
        Long before = character.getMineralDark();
        character.setMineralDark(before + amount);
        character = characterRepository.save(character);
        return character.getMineralDark();
    }

    // 기술레벨 업그레이드: module_research에 tech_level_N 행 삽입 후 현재 기술레벨 반환
    @Transactional
    public Integer addTechLevelResearch(Long characterId, Integer targetLevel) {
        String researchId = "tech_level_" + targetLevel;
        ModuleResearch existing = moduleResearchRepository.findByCharacterIdAndResearchId(characterId, researchId)
                .orElse(null);
        if (existing != null && existing.isResearched()) return targetLevel;

        ModuleResearch research = existing != null ? existing : new ModuleResearch();
        research.setCharacterId(characterId);
        research.setResearchId(researchId);
        research.setResearched(true);
        research.setModified(LocalDateTime.now());
        moduleResearchRepository.save(research);
        return targetLevel;
    }

}