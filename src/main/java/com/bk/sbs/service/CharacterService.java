//--------------------------------------------------------------------------------------------------
package com.bk.sbs.service;

import com.bk.sbs.dto.CharacterCreateRequest;
import com.bk.sbs.dto.CharacterResponse;
import com.bk.sbs.dto.CharacterStatusResponse;
import com.bk.sbs.entity.Account;
import com.bk.sbs.entity.Character;
import com.bk.sbs.exception.BusinessException;
import com.bk.sbs.exception.ServerErrorCode;
import com.bk.sbs.repository.AccountRepository;
import com.bk.sbs.repository.CharacterRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;

@Service
public class CharacterService {

    private final CharacterRepository characterRepository;
    private final AccountRepository accountRepository;
    private final FleetService fleetService;

    @Value("${worldid}")
    private int worldId;

    public CharacterService(CharacterRepository characterRepository, AccountRepository accountRepository, FleetService fleetService) {
        this.characterRepository = characterRepository;
        this.accountRepository = accountRepository;
        this.fleetService = fleetService;
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

        return new CharacterResponse(
                savedCharacter.getId(),
                savedCharacter.getCharacterName(),
                savedCharacter.getDateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                worldId
        );
    }

    public CharacterStatusResponse getCharacterStatus(Long characterId) {
        Character character = characterRepository.findById(characterId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.CHARACTER_NOT_FOUND));

        CharacterStatusResponse response = new CharacterStatusResponse();
        response.setCharacterName(character.getCharacterName());
        response.setMoney(character.getMoney());
        response.setMineral(character.getMineral());
        response.setTechnologyLevel(character.getTechLevel());

        return response;
    }

    @Transactional
    public Long updateMoney(Long characterId, Long money) {
        Character character = characterRepository.findByIdForUpdate(characterId).orElseThrow(() -> new BusinessException(ServerErrorCode.CHARACTER_NOT_FOUND));
        //Character character = characterRepository.findById(characterId).orElseThrow(() -> new BusinessException(ServerErrorCode.CHARACTER_NOT_FOUND));        
        character.setMoney(money);
        character = characterRepository.save(character);        
        return money;
    }

    @Transactional
    public Long addMoney(Long characterId, Long amount) {
        Character character = characterRepository.findByIdForUpdate(characterId).orElseThrow(() -> new BusinessException(ServerErrorCode.CHARACTER_NOT_FOUND));
        Long beforeMoney = character.getMoney();
        character.setMoney(beforeMoney + amount);
        character = characterRepository.save(character);
        return character.getMoney();
    }

    @Transactional
    public Long updateMineral(Long characterId, Long mineral) {
        Character character = characterRepository.findByIdForUpdate(characterId).orElseThrow(() -> new BusinessException(ServerErrorCode.CHARACTER_NOT_FOUND));
        //Character character = characterRepository.findById(characterId).orElseThrow(() -> new BusinessException(ServerErrorCode.CHARACTER_NOT_FOUND));        
        character.setMoney(mineral);            
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
    public Integer updateTechLevel(Long characterId, Integer techLevel) {
        //Character character = characterRepository.findByIdForUpdate(characterId).orElseThrow(() -> new BusinessException(ServerErrorCode.CHARACTER_NOT_FOUND));
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