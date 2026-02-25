package com.bk.sbs.service;

import com.bk.sbs.dto.ZoneConfigData;
import com.bk.sbs.dto.CostRemainInfoDto;
import com.bk.sbs.dto.ZoneClearRequest;
import com.bk.sbs.dto.ZoneClearResponse;
import com.bk.sbs.dto.ZoneCollectRequest;
import com.bk.sbs.dto.ZoneCollectResponse;
import com.bk.sbs.dto.ZoneKillRequest;
import com.bk.sbs.dto.ZoneKillResponse;
import com.bk.sbs.dto.HeartbeatRequest;
import com.bk.sbs.dto.HeartbeatResponse;
import com.bk.sbs.entity.Character;
import com.bk.sbs.exception.BusinessException;
import com.bk.sbs.exception.ServerErrorCode;
import com.bk.sbs.repository.CharacterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class ZoneService {

    private final CharacterRepository characterRepository;
    private final GameDataService gameDataService;

    public ZoneService(CharacterRepository characterRepository, GameDataService gameDataService) {
        this.characterRepository = characterRepository;
        this.gameDataService = gameDataService;
    }

    @Transactional
    public ZoneClearResponse clearZone(Long characterId, ZoneClearRequest request) {
        Character character = characterRepository.findByIdForUpdate(characterId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.ZONE_CLEAR_FAIL_CHARACTER_NOT_FOUND));

        String newZoneName = request.getZoneName();
        String currentClearedZone = character.getClearedZone();

        // 새로운 zone이 현재 클리어 zone보다 높은 난이도인지 확인
        if (!isHigherZone(newZoneName, currentClearedZone)) {
            return ZoneClearResponse.builder()
                    .clearedZone(currentClearedZone)
                    .rewardInfo(null)
                    .collectDateTime(character.getCollectDateTime() != null ? character.getCollectDateTime().toString() : null)
                    .build();
        }

        Instant now = Instant.now();

        // 이전 존의 미수집 자원 먼저 collect
        long[] rewards = collectZoneResources(character, currentClearedZone, now, true);

        // clearedZone 업데이트 및 collectDateTime 설정
        character.setClearedZone(newZoneName);
        character.setCollectDateTime(now);

        characterRepository.save(character);

        CostRemainInfoDto rewardInfo = CostRemainInfoDto.builder()
                .mineralCost(-rewards[0])
                .mineralRareCost(-rewards[1])
                .mineralExoticCost(-rewards[2])
                .mineralDarkCost(-rewards[3])
                .remainMineral(character.getMineral())
                .remainMineralRare(character.getMineralRare())
                .remainMineralExotic(character.getMineralExotic())
                .remainMineralDark(character.getMineralDark())
                .build();

        return ZoneClearResponse.builder()
                .clearedZone(newZoneName)
                .rewardInfo(rewardInfo)
                .collectDateTime(now.toString())
                .build();
    }

    @Transactional
    public ZoneCollectResponse collectZone(Long characterId, ZoneCollectRequest request) {
        Character character = characterRepository.findByIdForUpdate(characterId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.ZONE_CLEAR_FAIL_CHARACTER_NOT_FOUND));

        String clearedZone = character.getClearedZone();
        if (clearedZone == null || clearedZone.isEmpty()) {
            throw new BusinessException(ServerErrorCode.ZONE_COLLECT_FAIL_NO_CLEARED_ZONE);
        }

        Instant lastCollectTime = character.getCollectDateTime();
        Instant now = Instant.now();

        if (lastCollectTime == null) {
            lastCollectTime = now.minusSeconds(1);
        }

        long elapsedSeconds = ChronoUnit.SECONDS.between(lastCollectTime, now);
        if (elapsedSeconds <= 0) {
            return ZoneCollectResponse.builder()
                    .collectDateTime(lastCollectTime.toString())
                    .rewardInfo(CostRemainInfoDto.builder()
                            .mineralCost(0L)
                            .mineralRareCost(0L)
                            .mineralExoticCost(0L)
                            .mineralDarkCost(0L)
                            .remainMineral(character.getMineral())
                            .remainMineralRare(character.getMineralRare())
                            .remainMineralExotic(character.getMineralExotic())
                            .remainMineralDark(character.getMineralDark())
                            .build())
                    .build();
        }

        // 자원 수집 (fraction 유지)
        long[] rewards = collectZoneResources(character, clearedZone, now, false);

        character.setCollectDateTime(now);
        characterRepository.save(character);

        CostRemainInfoDto rewardInfo = CostRemainInfoDto.builder()
                .mineralCost(-rewards[0])
                .mineralRareCost(-rewards[1])
                .mineralExoticCost(-rewards[2])
                .mineralDarkCost(-rewards[3])
                .remainMineral(character.getMineral())
                .remainMineralRare(character.getMineralRare())
                .remainMineralExotic(character.getMineralExotic())
                .remainMineralDark(character.getMineralDark())
                .build();

        return ZoneCollectResponse.builder()
                .collectDateTime(now.toString())
                .rewardInfo(rewardInfo)
                .build();
    }

    // 공통: 자원 수집 로직 (반환: [mineral, mineralRare, mineralExotic, mineralDark])
    private long[] collectZoneResources(Character character, String zoneName, Instant now, boolean resetFraction) {
        long[] rewards = {0L, 0L, 0L, 0L};

        if (zoneName == null || zoneName.isEmpty() || character.getCollectDateTime() == null) {
            if (resetFraction) {
                character.setMineralFraction(0.0);
                character.setMineralRareFraction(0.0);
                character.setMineralExoticFraction(0.0);
                character.setMineralDarkFraction(0.0);
            }
            return rewards;
        }

        long elapsedSeconds = ChronoUnit.SECONDS.between(character.getCollectDateTime(), now);
        if (elapsedSeconds <= 0) {
            if (resetFraction) {
                character.setMineralFraction(0.0);
                character.setMineralRareFraction(0.0);
                character.setMineralExoticFraction(0.0);
                character.setMineralDarkFraction(0.0);
            }
            return rewards;
        }

        ZoneConfigData zoneConfig = gameDataService.getZoneConfigByName(zoneName);
        if (zoneConfig == null) {
            if (resetFraction) {
                character.setMineralFraction(0.0);
                character.setMineralRareFraction(0.0);
                character.setMineralExoticFraction(0.0);
                character.setMineralDarkFraction(0.0);
            }
            return rewards;
        }

        // 소수점까지 계산 후 기존 fraction과 합산
        double mineralTotal = character.getMineralFraction() + (zoneConfig.getMineralPerHour() / 3600.0 * elapsedSeconds);
        double mineralRareTotal = character.getMineralRareFraction() + (zoneConfig.getMineralRarePerHour() / 3600.0 * elapsedSeconds);
        double mineralExoticTotal = character.getMineralExoticFraction() + (zoneConfig.getMineralExoticPerHour() / 3600.0 * elapsedSeconds);
        double mineralDarkTotal = character.getMineralDarkFraction() + (zoneConfig.getMineralDarkPerHour() / 3600.0 * elapsedSeconds);

        // 정수부만 지급
        rewards[0] = (long) mineralTotal;
        rewards[1] = (long) mineralRareTotal;
        rewards[2] = (long) mineralExoticTotal;
        rewards[3] = (long) mineralDarkTotal;

        // 자원 지급
        character.setMineral(character.getMineral() + rewards[0]);
        character.setMineralRare(character.getMineralRare() + rewards[1]);
        character.setMineralExotic(character.getMineralExotic() + rewards[2]);
        character.setMineralDark(character.getMineralDark() + rewards[3]);

        // fraction 처리
        if (resetFraction) {
            character.setMineralFraction(0.0);
            character.setMineralRareFraction(0.0);
            character.setMineralExoticFraction(0.0);
            character.setMineralDarkFraction(0.0);
        } else {
            character.setMineralFraction(mineralTotal - rewards[0]);
            character.setMineralRareFraction(mineralRareTotal - rewards[1]);
            character.setMineralExoticFraction(mineralExoticTotal - rewards[2]);
            character.setMineralDarkFraction(mineralDarkTotal - rewards[3]);
        }

        return rewards;
    }

    // zone 난이도 비교 (x-y 형식)
    private boolean isHigherZone(String newZone, String currentZone) {
        if (currentZone == null || currentZone.isEmpty()) return true;

        int[] newParts = parseZoneName(newZone);
        int[] currentParts = parseZoneName(currentZone);

        if (newParts[0] > currentParts[0]) return true;
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

    @Transactional
    public ZoneKillResponse killZone(Long characterId, ZoneKillRequest request) {
        Character character = characterRepository.findByIdForUpdate(characterId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.ZONE_KILL_FAIL_CHARACTER_NOT_FOUND));

        long[] rewards = calculateKillRewards(request.getZoneName());
        character.setMineral(character.getMineral() + rewards[0]);
        character.setMineralRare(character.getMineralRare() + rewards[1]);
        character.setMineralExotic(character.getMineralExotic() + rewards[2]);
        character.setMineralDark(character.getMineralDark() + rewards[3]);
        characterRepository.save(character);

        CostRemainInfoDto rewardInfo = CostRemainInfoDto.builder()
                .mineralCost(-rewards[0])
                .mineralRareCost(-rewards[1])
                .mineralExoticCost(-rewards[2])
                .mineralDarkCost(-rewards[3])
                .remainMineral(character.getMineral())
                .remainMineralRare(character.getMineralRare())
                .remainMineralExotic(character.getMineralExotic())
                .remainMineralDark(character.getMineralDark())
                .build();

        return ZoneKillResponse.builder().rewardInfo(rewardInfo).build();
    }

    @Transactional
    public HeartbeatResponse heartbeat(Long characterId) {
        Character character = characterRepository.findByIdForUpdate(characterId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.HEARTBEAT_FAIL_CHARACTER_NOT_FOUND));
        character.setLastOnlineAt(Instant.now());
        characterRepository.save(character);
        return new HeartbeatResponse();
    }

    // 킬 보상 계산 (ZoneConfigData에서 가져옴)
    private long[] calculateKillRewards(String zoneName) {
        long[] rewards = {0L, 0L, 0L, 0L};

        ZoneConfigData zoneConfig = gameDataService.getZoneConfigByName(zoneName);
        if (zoneConfig == null) return rewards;

        rewards[0] = zoneConfig.getKillRewardMineral() != null ? zoneConfig.getKillRewardMineral().longValue() : 0L;
        rewards[1] = zoneConfig.getKillRewardMineralRare() != null ? zoneConfig.getKillRewardMineralRare().longValue() : 0L;
        rewards[2] = zoneConfig.getKillRewardMineralExotic() != null ? zoneConfig.getKillRewardMineralExotic().longValue() : 0L;
        rewards[3] = zoneConfig.getKillRewardMineralDark() != null ? zoneConfig.getKillRewardMineralDark().longValue() : 0L;

        return rewards;
    }
}
