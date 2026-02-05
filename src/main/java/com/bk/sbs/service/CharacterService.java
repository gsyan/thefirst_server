//--------------------------------------------------------------------------------------------------
package com.bk.sbs.service;

import com.bk.sbs.dto.CharacterCreateRequest;
import com.bk.sbs.dto.CharacterResponse;
import com.bk.sbs.dto.CharacterInfoDto;
import com.bk.sbs.dto.ShipInfoDto;
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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class CharacterService {

    private final CharacterRepository characterRepository;
    private final AccountRepository accountRepository;
    private final FleetService fleetService;
    private final ModuleResearchRepository moduleResearchRepository;

    @Value("${worldid}")
    private int worldId;

    public CharacterService(CharacterRepository characterRepository, AccountRepository accountRepository, FleetService fleetService, ModuleResearchRepository moduleResearchRepository) {
        this.characterRepository = characterRepository;
        this.accountRepository = accountRepository;
        this.fleetService = fleetService;
        this.moduleResearchRepository = moduleResearchRepository;
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
        researchBody.setModuleType(EModuleType.Body);
        researchBody.setModuleSubType(EModuleSubType.Body_Battle);
        researchBody.setResearched(true);
        researchBody.setCreated(now);
        researchBody.setModified(now);
        moduleResearchRepository.save(researchBody);

        // Engine - Standard
        ModuleResearch researchEngine = new ModuleResearch();
        researchEngine.setCharacterId(characterId);
        researchEngine.setModuleType(EModuleType.Engine);
        researchEngine.setModuleSubType(EModuleSubType.Engine_Standard);
        researchEngine.setResearched(true);
        researchEngine.setCreated(now);
        researchEngine.setModified(now);
        moduleResearchRepository.save(researchEngine);

        // Beam
        ModuleResearch researchWeapon = new ModuleResearch();
        researchWeapon.setCharacterId(characterId);
        researchWeapon.setModuleType(EModuleType.Beam);
        researchWeapon.setModuleSubType(EModuleSubType.Beam_Standard);
        researchWeapon.setResearched(true);
        researchWeapon.setCreated(now);
        researchWeapon.setModified(now);
        moduleResearchRepository.save(researchWeapon);

        // Missile
        ModuleResearch researchMissile = new ModuleResearch();
        researchMissile.setCharacterId(characterId);
        researchMissile.setModuleType(EModuleType.Missile);
        researchMissile.setModuleSubType(EModuleSubType.Missile_Standard);
        researchMissile.setResearched(true);
        researchMissile.setCreated(now);
        researchMissile.setModified(now);
        moduleResearchRepository.save(researchMissile);

        // Hanger - Standard
        ModuleResearch researchHanger = new ModuleResearch();
        researchHanger.setCharacterId(characterId);
        researchHanger.setModuleType(EModuleType.Hanger);
        researchHanger.setModuleSubType(EModuleSubType.Hanger_Standard);
        researchHanger.setResearched(true);
        researchHanger.setCreated(now);
        researchHanger.setModified(now);
        moduleResearchRepository.save(researchHanger);

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