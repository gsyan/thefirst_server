//--------------------------------------------------------------------------------------------------
package com.bk.sbs.service;

import com.bk.sbs.dto.CharacterCreateRequest;
import com.bk.sbs.dto.CharacterResponse;
import com.bk.sbs.dto.CharacterStatusResponse;
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
                .orElseThrow(() -> new BusinessException(ServerErrorCode.ACCOUNT_NOT_FOUND));

        if (characterRepository.existsByCharacterName(request.getCharacterName())) {
            throw new BusinessException(ServerErrorCode.CHARACTER_NAME_DUPLICATE);
        }

        Character character = new Character();
        character.setAccountId(account.getId());
        character.setCharacterName(request.getCharacterName());

        Character savedCharacter = characterRepository.save(character);

        // 캐릭터 생성과 동시에 기본 함대 생성 및 활성화
        // 실패 시 전체 트랜잭션 롤백됨
        fleetService.createFleet(savedCharacter.getId(), "Default Fleet", "Auto-generated default fleet.");
        fleetService.activateFirstFleet(savedCharacter.getId());

        // 기본 모듈 개발 상태 설정
        initializeDefaultModules(savedCharacter.getId());

        return new CharacterResponse(
                savedCharacter.getId(),
                savedCharacter.getCharacterName(),
                savedCharacter.getDateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                worldId
        );
    }

    /**
     * 캐릭터 생성 시 기본 모듈을 개발된 상태로 설정
     * - Body: Battle (StyleA)
     * - Weapon: Beam (StyleA)
     * - Engine: Standard (StyleA)
     * - Hanger: 제외
     */
    private void initializeDefaultModules(Long characterId) {
        LocalDateTime now = LocalDateTime.now();

        // Body - Battle (StyleA)
        ModuleResearch bodyModule = new ModuleResearch();
        bodyModule.setCharacterId(characterId);
        bodyModule.setModuleType(EModuleType.Body);
        bodyModule.setModuleSubTypeValue(EModuleBodySubType.Battle.getValue());
        bodyModule.setModuleStyleValue(EModuleStyle.None.getValue());
        bodyModule.setResearched(true);
        bodyModule.setCreated(now);
        bodyModule.setModified(now);
        moduleResearchRepository.save(bodyModule);

        // Weapon - Beam (StyleA)
        ModuleResearch weaponModule = new ModuleResearch();
        weaponModule.setCharacterId(characterId);
        weaponModule.setModuleType(EModuleType.Weapon);
        weaponModule.setModuleSubTypeValue(EModuleWeaponSubType.Beam.getValue());
        weaponModule.setModuleStyleValue(EModuleStyle.None.getValue());
        weaponModule.setResearched(true);
        weaponModule.setCreated(now);
        weaponModule.setModified(now);
        moduleResearchRepository.save(weaponModule);

        // Engine - Standard (StyleA)
        ModuleResearch engineModule = new ModuleResearch();
        engineModule.setCharacterId(characterId);
        engineModule.setModuleType(EModuleType.Engine);
        engineModule.setModuleSubTypeValue(EModuleEngineSubType.Standard.getValue());
        engineModule.setModuleStyleValue(EModuleStyle.None.getValue());
        engineModule.setResearched(true);
        engineModule.setCreated(now);
        engineModule.setModified(now);
        moduleResearchRepository.save(engineModule);
    }

    public CharacterStatusResponse getCharacterStatus(Long characterId) {
        Character character = characterRepository.findById(characterId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.CHARACTER_NOT_FOUND));

        CharacterStatusResponse response = new CharacterStatusResponse();
        response.setCharacterName(character.getCharacterName());
        response.setTechLevel(character.getTechLevel());
        response.setMineral(character.getMineral());
        response.setMineralRare(character.getMineralRare());
        response.setMineralExotic(character.getMineralExotic());
        response.setMineralDark(character.getMineralDark());
        
        return response;
    }

    @Transactional
    public Long updateMineral(Long characterId, Long mineral) {
        Character character = characterRepository.findByIdForUpdate(characterId).orElseThrow(() -> new BusinessException(ServerErrorCode.CHARACTER_NOT_FOUND));
        character.setMineral(mineral);
        character = characterRepository.save(character);
        return mineral;
    }

    @Transactional
    public Long addMineral(Long characterId, Long amount) {
        Character character = characterRepository.findByIdForUpdate(characterId).orElseThrow(() -> new BusinessException(ServerErrorCode.CHARACTER_NOT_FOUND));
        Long before = character.getMineral();
        character.setMineral(before + amount);
        character = characterRepository.save(character);
        return character.getMineral();
    }

    @Transactional
    public Long updateMineralRare(Long characterId, Long mineralRare) {
        Character character = characterRepository.findByIdForUpdate(characterId).orElseThrow(() -> new BusinessException(ServerErrorCode.CHARACTER_NOT_FOUND));
        character.setMineralRare(mineralRare);
        character = characterRepository.save(character);
        return mineralRare;
    }

    @Transactional
    public Long addMineralRare(Long characterId, Long amount) {
        Character character = characterRepository.findByIdForUpdate(characterId).orElseThrow(() -> new BusinessException(ServerErrorCode.CHARACTER_NOT_FOUND));
        Long before = character.getMineralRare();
        character.setMineralRare(before + amount);
        character = characterRepository.save(character);
        return character.getMineralRare();
    }

    @Transactional
    public Long updateMineralExotic(Long characterId, Long mineralExotic) {
        Character character = characterRepository.findByIdForUpdate(characterId).orElseThrow(() -> new BusinessException(ServerErrorCode.CHARACTER_NOT_FOUND));
        character.setMineralExotic(mineralExotic);
        character = characterRepository.save(character);
        return mineralExotic;
    }

    @Transactional
    public Long addMineralExotic(Long characterId, Long amount) {
        Character character = characterRepository.findByIdForUpdate(characterId).orElseThrow(() -> new BusinessException(ServerErrorCode.CHARACTER_NOT_FOUND));
        Long before = character.getMineralExotic();
        character.setMineralExotic(before + amount);
        character = characterRepository.save(character);
        return character.getMineralExotic();
    }

    @Transactional
    public Long updateMineralDark(Long characterId, Long mineralDark) {
        Character character = characterRepository.findByIdForUpdate(characterId).orElseThrow(() -> new BusinessException(ServerErrorCode.CHARACTER_NOT_FOUND));
        character.setMineralDark(mineralDark);
        character = characterRepository.save(character);
        return mineralDark;
    }

    @Transactional
    public Long addMineralDark(Long characterId, Long amount) {
        Character character = characterRepository.findByIdForUpdate(characterId).orElseThrow(() -> new BusinessException(ServerErrorCode.CHARACTER_NOT_FOUND));
        Long before = character.getMineralDark();
        character.setMineralDark(before + amount);
        character = characterRepository.save(character);
        return character.getMineralDark();
    }

    @Transactional
    public Integer updateTechLevel(Long characterId, Integer techLevel) {
        Character character = characterRepository.findById(characterId).orElseThrow(() -> new BusinessException(ServerErrorCode.CHARACTER_NOT_FOUND));
        character.setTechLevel(techLevel);
        character = characterRepository.save(character);
        return techLevel;
    }

    @Transactional
    public Integer addTechLevel(Long characterId, Integer amount) {
        Character character = characterRepository.findById(characterId).orElseThrow(() -> new BusinessException(ServerErrorCode.CHARACTER_NOT_FOUND));
        Integer before = character.getTechLevel();
        character.setTechLevel(before + amount);
        character = characterRepository.save(character);
        return character.getTechLevel();
    }

}