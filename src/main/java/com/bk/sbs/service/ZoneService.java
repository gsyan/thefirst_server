package com.bk.sbs.service;

import com.bk.sbs.dto.CostRemainInfoDto;
import com.bk.sbs.dto.ZoneClearRequest;
import com.bk.sbs.dto.ZoneClearResponse;
import com.bk.sbs.entity.Character;
import com.bk.sbs.exception.BusinessException;
import com.bk.sbs.exception.ServerErrorCode;
import com.bk.sbs.repository.CharacterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ZoneService {

    private final CharacterRepository characterRepository;

    public ZoneService(CharacterRepository characterRepository) {
        this.characterRepository = characterRepository;
    }

    @Transactional
    public ZoneClearResponse clearZone(Long characterId, ZoneClearRequest request) {
        Character character = characterRepository.findByIdForUpdate(characterId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.ZONE_CLEAR_FAIL_CHARACTER_NOT_FOUND));

        String newZoneName = request.getZoneName();
        String currentClearedZone = character.getClearedZone();

        // 새로운 zone이 현재 클리어 zone보다 높은 난이도인지 확인
        if (!isHigherZone(newZoneName, currentClearedZone)) {
            // 이미 클리어한 zone이면 보상 없이 현재 상태 반환
            return ZoneClearResponse.builder()
                    .clearedZone(currentClearedZone)
                    .rewardInfo(null)
                    .build();
        }

        // clearedZone 업데이트
        character.setClearedZone(newZoneName);

        // 보상 계산 및 지급
        long mineralReward = calculateMineralReward(newZoneName);
        character.setMineral(character.getMineral() + mineralReward);

        characterRepository.save(character);

        CostRemainInfoDto rewardInfo = CostRemainInfoDto.builder()
                .mineralCost(-mineralReward)  // 음수로 표시 (획득)
                .mineralRareCost(0L)
                .mineralExoticCost(0L)
                .mineralDarkCost(0L)
                .remainMineral(character.getMineral())
                .remainMineralRare(character.getMineralRare())
                .remainMineralExotic(character.getMineralExotic())
                .remainMineralDark(character.getMineralDark())
                .build();

        return ZoneClearResponse.builder()
                .clearedZone(newZoneName)
                .rewardInfo(rewardInfo)
                .build();
    }

    // zone 난이도 비교 (x-y 형식)
    private boolean isHigherZone(String newZone, String currentZone) {
        if (currentZone == null || currentZone.isEmpty()) return true;

        int[] newParts = parseZoneName(newZone);
        int[] currentParts = parseZoneName(currentZone);

        // 함선 개수(x)가 더 크면 높은 난이도
        if (newParts[0] > currentParts[0]) return true;
        // 함선 개수가 같고 모듈 레벨(y)이 더 크면 높은 난이도
        if (newParts[0] == currentParts[0] && newParts[1] > currentParts[1]) return true;

        return false;
    }

    // "x-y" 형식 파싱
    private int[] parseZoneName(String zoneName) {
        if (zoneName == null || zoneName.isEmpty()) return new int[]{0, 0};

        String[] parts = zoneName.split("-");
        if (parts.length != 2) return new int[]{0, 0};

        try {
            return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1])};
        } catch (NumberFormatException e) {
            return new int[]{0, 0};
        }
    }

    // 보상 계산 (zone 난이도에 따라)
    private long calculateMineralReward(String zoneName) {
        int[] parts = parseZoneName(zoneName);
        int shipCount = parts[0];  // 함선 개수
        int moduleLevel = parts[1];  // 모듈 레벨

        // 기본 보상: 함선 개수 * 모듈 레벨 * 100
        return (long) shipCount * moduleLevel * 100;
    }
}
