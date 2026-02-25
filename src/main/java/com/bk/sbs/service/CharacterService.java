//--------------------------------------------------------------------------------------------------
package com.bk.sbs.service;

import com.bk.sbs.dto.CharacterCreateRequest;
import com.bk.sbs.dto.CharacterResponse;
import com.bk.sbs.dto.CharacterInfoDto;
import com.bk.sbs.dto.ShipInfoDto;
import com.bk.sbs.dto.ZoneConfigData;
import com.bk.sbs.entity.Account;
import com.bk.sbs.entity.Character;
import com.bk.sbs.entity.ModuleResearch;
import com.bk.sbs.enums.*;
import com.bk.sbs.exception.BusinessException;
import com.bk.sbs.exception.ServerErrorCode;
import com.bk.sbs.repository.AccountRepository;
import com.bk.sbs.repository.CharacterRepository;
import com.bk.sbs.repository.ModuleResearchRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

@Service
public class CharacterService {

    private final CharacterRepository characterRepository;
    private final AccountRepository accountRepository;
    private final FleetService fleetService;
    private final ModuleResearchRepository moduleResearchRepository;
    private final StringRedisTemplate redisTemplate;
    private final GameDataService gameDataService;

    // 오프라인 보상 최대 시간 (12시간)
    private static final long MAX_OFFLINE_SECONDS = 43200L;

    @Value("${worldid}")
    private int worldId;

    public CharacterService(CharacterRepository characterRepository, AccountRepository accountRepository, FleetService fleetService, ModuleResearchRepository moduleResearchRepository, StringRedisTemplate redisTemplate, GameDataService gameDataService) {
        this.characterRepository = characterRepository;
        this.accountRepository = accountRepository;
        this.fleetService = fleetService;
        this.moduleResearchRepository = moduleResearchRepository;
        this.redisTemplate = redisTemplate;
        this.gameDataService = gameDataService;
    }

    @Transactional
    public CharacterResponse createCharacter(CharacterCreateRequest request) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Account account = accountRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.CHARACTER_CREATE_FAIL_ACCOUNT_NOT_FOUND));

        if (characterRepository.existsByCharacterName(request.getCharacterName())) throw new BusinessException(ServerErrorCode.CHARACTER_CREATE_FAIL_NAME_DUPLICATE);

        Character character = new Character();
        character.setAccountId(account.getId());
        character.setCharacterName(request.getCharacterName());
        character.setMineral(5100L);  // 기본미네랄 5100 지급
        Character savedCharacter = characterRepository.save(character);

        // 캐릭터 생성과 동시에 기본 함대 생성 및 활성화
        // 실패 시 전체 트랜잭션 롤백됨
        fleetService.createFleet(savedCharacter.getId(), "Default Fleet", "Auto-generated default fleet.");
        fleetService.activateFirstFleet(savedCharacter.getId());

        // 기본 모듈 개발 상태 설정
        initializeDefaultModules(savedCharacter.getId());

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

    // 기본연구 세팅
    private void initializeDefaultModules(Long characterId) {
        LocalDateTime now = LocalDateTime.now();

        // Body - Battle
        ModuleResearch researchBody = new ModuleResearch();
        researchBody.setCharacterId(characterId);
        researchBody.setModuleType(EModuleType.body);
        researchBody.setModuleSubType(EModuleSubType.body_battle);
        researchBody.setResearched(true);
        researchBody.setCreated(now);
        researchBody.setModified(now);
        moduleResearchRepository.save(researchBody);

        // Engine - Standard
        ModuleResearch researchEngine = new ModuleResearch();
        researchEngine.setCharacterId(characterId);
        researchEngine.setModuleType(EModuleType.engine);
        researchEngine.setModuleSubType(EModuleSubType.engine_standard);
        researchEngine.setResearched(true);
        researchEngine.setCreated(now);
        researchEngine.setModified(now);
        moduleResearchRepository.save(researchEngine);

        // Beam
        ModuleResearch researchWeapon = new ModuleResearch();
        researchWeapon.setCharacterId(characterId);
        researchWeapon.setModuleType(EModuleType.beam);
        researchWeapon.setModuleSubType(EModuleSubType.beam_standard);
        researchWeapon.setResearched(true);
        researchWeapon.setCreated(now);
        researchWeapon.setModified(now);
        moduleResearchRepository.save(researchWeapon);

        // Missile
        ModuleResearch researchMissile = new ModuleResearch();
        researchMissile.setCharacterId(characterId);
        researchMissile.setModuleType(EModuleType.missile);
        researchMissile.setModuleSubType(EModuleSubType.missile_standard);
        researchMissile.setResearched(true);
        researchMissile.setCreated(now);
        researchMissile.setModified(now);
        moduleResearchRepository.save(researchMissile);

        // Hanger - Standard
        ModuleResearch researchHanger = new ModuleResearch();
        researchHanger.setCharacterId(characterId);
        researchHanger.setModuleType(EModuleType.hanger);
        researchHanger.setModuleSubType(EModuleSubType.hanger_standard);
        researchHanger.setResearched(true);
        researchHanger.setCreated(now);
        researchHanger.setModified(now);
        moduleResearchRepository.save(researchHanger);

    }

    // 오프라인 보상 지급 + lastOnlineAt 갱신 (selectCharacter 진입 시 호출)
    @Transactional
    public void applyOfflineRewardAndUpdateLastOnline(Long characterId) {
        Character character = characterRepository.findByIdForUpdate(characterId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.GET_CHARACTER_INFO_DTO_FAIL_CHARACTER_NOT_FOUND));

        Instant now = Instant.now();
        String clearedZone = character.getClearedZone();
        Instant lastOnlineAt = character.getLastOnlineAt();

        // clearedZone이 있고 lastOnlineAt이 기록된 경우에만 오프라인 보상 지급
        if (lastOnlineAt != null && clearedZone != null && !clearedZone.isEmpty()) {
            long offlineSec = Math.min(ChronoUnit.SECONDS.between(lastOnlineAt, now), MAX_OFFLINE_SECONDS);

            if (offlineSec > 0) {
                ZoneConfigData zoneConfig = gameDataService.getZoneConfigByName(clearedZone);
                if (zoneConfig != null) {
                    double mineralTotal = (zoneConfig.getMineralPerHour() / 3600.0 * offlineSec);
                    double mineralRareTotal = (zoneConfig.getMineralRarePerHour() / 3600.0 * offlineSec);
                    double mineralExoticTotal = (zoneConfig.getMineralExoticPerHour() / 3600.0 * offlineSec);
                    double mineralDarkTotal = (zoneConfig.getMineralDarkPerHour() / 3600.0 * offlineSec);

                    character.setMineral(character.getMineral() + (long) mineralTotal);
                    character.setMineralRare(character.getMineralRare() + (long) mineralRareTotal);
                    character.setMineralExotic(character.getMineralExotic() + (long) mineralExoticTotal);
                    character.setMineralDark(character.getMineralDark() + (long) mineralDarkTotal);

                    // 오프라인 보상으로 collect 기간 소비 → collectDateTime 리셋
                    character.setCollectDateTime(now);
                    character.setMineralFraction(0.0);
                    character.setMineralRareFraction(0.0);
                    character.setMineralExoticFraction(0.0);
                    character.setMineralDarkFraction(0.0);
                }
            }
        }

        character.setLastOnlineAt(now);
        characterRepository.save(character);
    }

    public CharacterInfoDto getCharacterInfoDto(Long characterId) {
        Character character = characterRepository.findById(characterId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.GET_CHARACTER_INFO_DTO_FAIL_CHARACTER_NOT_FOUND));

        return CharacterInfoDto.builder()
                .characterName(character.getCharacterName())
                .techLevel(character.getTechLevel())
                .mineral(character.getMineral())
                .mineralRare(character.getMineralRare())
                .mineralExotic(character.getMineralExotic())
                .mineralDark(character.getMineralDark())
                .clearedZone(character.getClearedZone())
                .collectDateTime(character.getCollectDateTime() != null ? character.getCollectDateTime().toString() : null)
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

    @Transactional
    public Integer updateTechLevel(Long characterId, Integer techLevel) {
        Character character = characterRepository.findById(characterId).orElseThrow(() -> new BusinessException(ServerErrorCode.UPDATE_TECH_LEVEL_FAIL_CHARACTER_NOT_FOUND));
        character.setTechLevel(techLevel);
        character = characterRepository.save(character);
        return techLevel;
    }

    @Transactional
    public Integer addTechLevel(Long characterId, Integer amount) {
        Character character = characterRepository.findById(characterId).orElseThrow(() -> new BusinessException(ServerErrorCode.ADD_TECH_LEVEL_FAIL_CHARACTER_NOT_FOUND));
        Integer before = character.getTechLevel();
        character.setTechLevel(before + amount);
        character = characterRepository.save(character);
        return character.getTechLevel();
    }

}