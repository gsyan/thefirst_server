package com.bk.sbs.service;

import com.bk.sbs.config.DataTableModule;
import com.bk.sbs.dto.*;
import com.bk.sbs.entity.*;
import com.bk.sbs.entity.Commander;
import com.bk.sbs.enums.*;
import com.bk.sbs.exception.BusinessException;
import com.bk.sbs.exception.ServerErrorCode;
import com.bk.sbs.repository.*;
import com.bk.sbs.util.ModuleTypeConverter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
public class FleetService {

    private final CommanderRepository commanderRepository;
    private final GameDataService gameDataService;
    private final CommanderFleetPresetRepository commanderFleetPresetRepository;

    // 신규 커맨더에게 지급되는 기본 함대 프리셋(presetIndex=0)의 초기 함선 — 바디 body_t1_m111(빔1/미사일1/격납고1) + 기본 빔1 장착
    private static final String DEFAULT_FLEET_PRESET_SHIP_PRESET_ID = "m11100";

    public FleetService(CommanderRepository commanderRepository,
                       GameDataService gameDataService,
                       CommanderFleetPresetRepository commanderFleetPresetRepository) {
        this.commanderRepository = commanderRepository;
        this.gameDataService = gameDataService;
        this.commanderFleetPresetRepository = commanderFleetPresetRepository;
    }

    // 신규 커맨더 생성 시 기본 함대 프리셋(presetIndex=0) 생성 — ExplorationShipSlot 기반, 구식 Fleet/Ship 엔티티 사용 안 함
    @Transactional
    public void createDefaultFleetPreset(Long commanderId) {
        CommanderFleetPreset preset = new CommanderFleetPreset();
        preset.setCommanderId(commanderId);
        preset.setPresetIndex(0);
        preset = commanderFleetPresetRepository.save(preset);

        CommanderFleetPresetSlot slot = new CommanderFleetPresetSlot();
        slot.setFleetPreset(preset);
        slot.setSlotIndex(0);
        slot.setShipPresetId(DEFAULT_FLEET_PRESET_SHIP_PRESET_ID);
        slot.setFront(true);
        seedDefaultModules(slot, DEFAULT_FLEET_PRESET_SHIP_PRESET_ID);
        preset.setSlots(new ArrayList<>(List.of(slot)));

        commanderFleetPresetRepository.save(preset);
    }

    // 로그인 시 내려주는 "내 함대" — presetIndex=0 프리셋을 FleetInfoDto로 변환, 슬롯별 실제 장착 모듈(bodies)까지 포함
    public FleetInfoDto getActiveFleetPreset(Long commanderId) {
        CommanderFleetPreset preset = commanderFleetPresetRepository.findByCommanderIdAndPresetIndex(commanderId, 0)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.COMMANDER_CONTROLLER_FAIL_NULL_ACTIVE_FLEET));
        return convertPresetToFleetInfoDto(preset);
    }

    // PvP 상대 함대 조회/랭킹 함대 능력치 계산용 — 프리셋이 없으면(탈퇴 등) null 반환, getActiveFleetPreset과 달리 예외를 던지지 않음
    public FleetInfoDto getActiveFleetPresetOrNull(Long commanderId) {
        return commanderFleetPresetRepository.findByCommanderIdAndPresetIndex(commanderId, 0)
                .map(this::convertPresetToFleetInfoDto)
                .orElse(null);
    }

    private FleetInfoDto convertPresetToFleetInfoDto(CommanderFleetPreset preset) {
        List<ShipInfoDto> ships = preset.getSlots().stream()
                .sorted((a, b) -> Integer.compare(a.getSlotIndex(), b.getSlotIndex()))
                .map(slot -> ShipInfoDto.builder()
                        .shipPresetId(slot.getShipPresetId())
                        .isFront(slot.isFront())
                        .bodies(List.of(buildModuleBodyInfoDto(slot)))
                        .build())
                .collect(Collectors.toList());

        return FleetInfoDto.builder()
                .id(preset.getId())
                .tacticOptions(preset.getTacticOptions())
                .ships(ships)
                .build();
    }

    // 함대편성(FleetComposition) 슬롯에 함선 배치/교체 — 바디(프리셋) 자체를 바꾸는 동작이라 그 슬롯의 장착 모듈은 새 바디의 기본 로드아웃(빔1)으로 초기화됨
    // 클라이언트(FleetComposition.TryPlaceShipAt/ComputeUnlockedPresets)가 이미 동일한 조건(레벨/슬롯범위/지휘력)을 검증하지만,
    // 조작된 요청을 막기 위해 서버에서도 동일 조건을 재검증한다 — setFleetPresetSlotModules(라인 218)와 동일한 검증 패턴
    @Transactional
    public void placeFleetPresetShip(Long commanderId, FleetPresetPlaceShipRequest request) {
        Commander commander = commanderRepository.findByIdForUpdate(commanderId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.PLACE_FLEET_PRESET_SHIP_FAIL_COMMANDER_NOT_FOUND));

        GameDataService.ShipPresetSummary summary = gameDataService.getShipPresetSummary(request.getShipPresetId());
        if (summary == null)
            throw new BusinessException(ServerErrorCode.PLACE_FLEET_PRESET_SHIP_FAIL_PRESET_NOT_FOUND);
        if (summary.unlockCommanderLevel > commander.getCommanderLevel())
            throw new BusinessException(ServerErrorCode.PLACE_FLEET_PRESET_SHIP_FAIL_INSUFFICIENT_COMMANDER_LEVEL);

        int openSlotCount = gameDataService.getShipCount(commander.getCommanderLevel());
        if (request.getSlotIndex() < 0 || request.getSlotIndex() >= openSlotCount)
            throw new BusinessException(ServerErrorCode.PLACE_FLEET_PRESET_SHIP_FAIL_SLOT_LOCKED);

        CommanderFleetPreset preset = commanderFleetPresetRepository.findByCommanderIdAndPresetIndex(commanderId, 0)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.COMMANDER_CONTROLLER_FAIL_NULL_ACTIVE_FLEET));

        // summary.commandCost는 기본 로드아웃(빔1) 기준 지휘력 총합 — 이 슬롯이 배치 후 실제로 점유할 비용과 동일
        int usedByOtherSlots = 0;
        for (CommanderFleetPresetSlot s : preset.getSlots()) {
            if (s.getSlotIndex() == request.getSlotIndex()) continue;
            usedByOtherSlots += computeSlotCommandCost(s);
        }
        if (usedByOtherSlots + summary.commandCost > commander.getCommandPowerMax())
            throw new BusinessException(ServerErrorCode.PLACE_FLEET_PRESET_SHIP_FAIL_NOT_ENOUGH_COMMAND_POWER);

        CommanderFleetPresetSlot slot = preset.getSlots().stream()
                .filter(s -> s.getSlotIndex() == request.getSlotIndex())
                .findFirst()
                .orElseGet(() -> {
                    CommanderFleetPresetSlot newSlot = new CommanderFleetPresetSlot();
                    newSlot.setFleetPreset(preset);
                    newSlot.setSlotIndex(request.getSlotIndex());
                    preset.getSlots().add(newSlot);
                    return newSlot;
                });
        slot.setShipPresetId(request.getShipPresetId());
        slot.setFront(request.getIsFront());
        seedDefaultModules(slot, request.getShipPresetId());
        commanderFleetPresetRepository.save(preset);
    }

    // presetId(바디)의 기본 로드아웃(현재는 beam slot0=beam_t1)을 슬롯의 장착 모듈로 초기화 — orphanRemoval로 기존 모듈은 자동 삭제됨
    private void seedDefaultModules(CommanderFleetPresetSlot slot, String presetId) {
        List<CommanderFleetPresetSlotModule> modules = new ArrayList<>();
        GameDataService.ShipPresetSummary summary = gameDataService.getShipPresetSummary(presetId);
        if (summary != null && summary.defaultModules != null) {
            for (GameDataService.DefaultModuleEntry entry : summary.defaultModules) {
                CommanderFleetPresetSlotModule module = new CommanderFleetPresetSlotModule();
                module.setPresetSlot(slot);
                module.setModuleType(entry.moduleType);
                module.setSlotIndex(entry.slotIndex);
                module.setModuleSubType(entry.moduleSubType);
                modules.add(module);
            }
        }
        replaceSlotModules(slot, modules);
    }

    // Hibernate orphanRemoval 컬렉션은 필드 참조 자체를 새 List로 갈아끼우면 안 됨(기존에 관리되던 컬렉션이 고아가 되어
    // "A collection with orphan deletion was no longer referenced" 예외 발생) — 기존 컬렉션이 있으면 clear() 후 채우고,
    // 아직 없으면(신규 슬롯 등 영속화 전) 그대로 세팅
    private void replaceSlotModules(CommanderFleetPresetSlot slot, List<CommanderFleetPresetSlotModule> newModules) {
        List<CommanderFleetPresetSlotModule> current = slot.getModules();
        if (current == null) {
            slot.setModules(newModules);
            return;
        }
        current.clear();
        current.addAll(newModules);
    }

    // ── 슬롯 모듈 편집 ────────────────────────────────────────────────

    // on/off만 지원하므로 카테고리당 서브타입은 항상 이 값 하나 — 티어 선택이 생기면 이 지점부터 확장
    private EModuleSubType getDefaultSubTypeForCategory(EModuleType moduleType) {
        return switch (moduleType) {
            case beam -> EModuleSubType.beam_t1;
            case missile -> EModuleSubType.missile_t1;
            case hangar -> EModuleSubType.hangar_t1;
            default -> null;
        };
    }

    private boolean isAttackModuleType(EModuleType moduleType) {
        return moduleType == EModuleType.beam || moduleType == EModuleType.missile || moduleType == EModuleType.hangar;
    }

    private int getModuleStatPoint(EModuleType moduleType, EModuleSubType subType) {
        if (subType == null) return 0;
        List<ModuleData> modules = gameDataService.getModulesByType(moduleType);
        for (ModuleData data : modules) {
            if (subType.equals(data.getModuleSubType()))
                return data.getStatPoint() != null ? data.getStatPoint() : 0;
        }
        return 0;
    }

    private int computeBodyCost(String presetId) {
        GameDataService.ShipPresetSummary summary = gameDataService.getShipPresetSummary(presetId);
        if (summary == null || summary.prefabName == null) return 0;
        try {
            return getModuleStatPoint(EModuleType.body, EModuleSubType.valueOf(summary.prefabName));
        } catch (IllegalArgumentException ignored) {
            return 0; // prefabName이 EModuleSubType에 없는 경우 — bodyCost 0으로 취급
        }
    }

    // 바디 설치비 + 현재 장착된 모든 모듈의 설치비 합 — 클라 ShipStatAllocation.GetTotalPointsUsed와 동일한 계산
    private int computeSlotCommandCost(CommanderFleetPresetSlot slot) {
        int bodyCost = computeBodyCost(slot.getShipPresetId());

        int modulesCost = 0;
        if (slot.getModules() != null) {
            for (CommanderFleetPresetSlotModule module : slot.getModules()) {
                modulesCost += getModuleStatPoint(module.getModuleType(), module.getModuleSubType());
            }
        }
        return bodyCost + modulesCost;
    }

    private ModuleBodyInfoDto buildModuleBodyInfoDto(CommanderFleetPresetSlot slot) {
        List<ModuleInfoDto> beams = new ArrayList<>();
        List<ModuleInfoDto> missiles = new ArrayList<>();
        List<ModuleInfoDto> hangars = new ArrayList<>();

        if (slot.getModules() != null) {
            for (CommanderFleetPresetSlotModule module : slot.getModules()) {
                ModuleInfoDto dto = ModuleInfoDto.builder()
                        .moduleType(module.getModuleType())
                        .moduleSubType(module.getModuleSubType())
                        .slotIndex(module.getSlotIndex())
                        .build();
                switch (module.getModuleType()) {
                    case beam -> beams.add(dto);
                    case missile -> missiles.add(dto);
                    case hangar -> hangars.add(dto);
                    default -> { }
                }
            }
        }

        EModuleSubType bodySubType = getBodySubType(slot.getShipPresetId());
        Float maxHealth = getModuleMaxHealth(bodySubType);

        return ModuleBodyInfoDto.builder()
                .moduleType(EModuleType.body)
                .moduleSubType(bodySubType)
                .beams(beams)
                .missiles(missiles)
                .hangars(hangars)
                .currentHealth(maxHealth)
                .build();
    }

    // shipPresetId(예: "m11100")의 prefabName을 EModuleSubType(body)으로 변환 — computeBodyCost와 동일 패턴
    private EModuleSubType getBodySubType(String presetId) {
        GameDataService.ShipPresetSummary summary = gameDataService.getShipPresetSummary(presetId);
        if (summary == null || summary.prefabName == null) return null;
        try {
            return EModuleSubType.valueOf(summary.prefabName);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private Float getModuleMaxHealth(EModuleSubType bodySubType) {
        if (bodySubType == null) return null;
        List<ModuleData> bodyDataList = gameDataService.getModulesByType(EModuleType.body);
        return bodyDataList.stream()
                .filter(d -> d.getModuleSubType() == bodySubType)
                .findFirst()
                .map(d -> d.getHealth() != null ? d.getHealth() : 0f)
                .orElse(null);
    }

    // 슬롯 하나의 장착 모듈 "전체"를 최종 상태로 한 번에 교체 — 낱개 토글을 순서대로 여러 번 보내면 중간 상태에서
    // 예산/공격모듈 0개 검증에 걸릴 수 있어(예: 빔→미사일 교체 시 어느 순서로 보내도 중간엔 항상 실패), 요청받은 최종 구성만 검증한다
    @Transactional
    public SetFleetPresetSlotModulesResponse setFleetPresetSlotModules(Long commanderId, SetFleetPresetSlotModulesRequest request) {
        Commander commander = commanderRepository.findByIdForUpdate(commanderId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.TOGGLE_FLEET_PRESET_MODULE_FAIL_COMMANDER_NOT_FOUND));

        CommanderFleetPreset preset = commanderFleetPresetRepository.findByCommanderIdAndPresetIndex(commanderId, 0)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.TOGGLE_FLEET_PRESET_MODULE_FAIL_PRESET_NOT_FOUND));

        CommanderFleetPresetSlot slot = preset.getSlots().stream()
                .filter(s -> s.getSlotIndex() == request.getSlotIndex())
                .findFirst()
                .orElseThrow(() -> new BusinessException(ServerErrorCode.TOGGLE_FLEET_PRESET_MODULE_FAIL_SLOT_NOT_FOUND));

        int[] maxSlots = GameDataService.parseMaxSlotsFromPresetId(slot.getShipPresetId());
        ModuleBodyInfoDto requestedModules = request.getModules();

        List<DesiredModule> desired = new ArrayList<>();
        appendDesiredModules(desired, EModuleType.beam, maxSlots[0], requestedModules != null ? requestedModules.getBeams() : null);
        appendDesiredModules(desired, EModuleType.missile, maxSlots[1], requestedModules != null ? requestedModules.getMissiles() : null);
        appendDesiredModules(desired, EModuleType.hangar, maxSlots[2], requestedModules != null ? requestedModules.getHangars() : null);

        boolean hasAttackModule = desired.stream().anyMatch(m -> isAttackModuleType(m.moduleType));
        if (hasAttackModule == false)
            throw new BusinessException(ServerErrorCode.TOGGLE_FLEET_PRESET_MODULE_FAIL_NO_ATTACK_MODULE_REMAINING);

        int newSlotCost = computeBodyCost(slot.getShipPresetId());
        for (DesiredModule m : desired) newSlotCost += getModuleStatPoint(m.moduleType, m.moduleSubType);

        int usedByOtherSlots = 0;
        for (CommanderFleetPresetSlot s : preset.getSlots()) {
            if (s.getSlotIndex() == slot.getSlotIndex()) continue;
            usedByOtherSlots += computeSlotCommandCost(s);
        }
        if (usedByOtherSlots + newSlotCost > commander.getCommandPowerMax())
            throw new BusinessException(ServerErrorCode.TOGGLE_FLEET_PRESET_MODULE_FAIL_NOT_ENOUGH_COMMAND_POWER);

        List<CommanderFleetPresetSlotModule> newModules = new ArrayList<>();
        for (DesiredModule m : desired) {
            CommanderFleetPresetSlotModule module = new CommanderFleetPresetSlotModule();
            module.setPresetSlot(slot);
            module.setModuleType(m.moduleType);
            module.setSlotIndex(m.slotIndex);
            module.setModuleSubType(m.moduleSubType);
            newModules.add(module);
        }
        replaceSlotModules(slot, newModules);
        commanderFleetPresetRepository.save(preset);

        int remainingAfter = commander.getCommandPowerMax() - (usedByOtherSlots + newSlotCost);

        return SetFleetPresetSlotModulesResponse.builder()
                .body(buildModuleBodyInfoDto(slot))
                .commandCost(newSlotCost)
                .remainingCommandPower(remainingAfter)
                .build();
    }

    private record DesiredModule(EModuleType moduleType, int slotIndex, EModuleSubType moduleSubType) { }

    // requested의 각 항목이 유효한 슬롯 인덱스(0 <= idx < maxSlotCount)인지, 중복 슬롯이 없는지 검증하며 desired 목록에 채워 넣음
    private void appendDesiredModules(List<DesiredModule> target, EModuleType moduleType, int maxSlotCount, List<ModuleInfoDto> requested) {
        if (requested == null) return;
        EModuleSubType defaultSubType = getDefaultSubTypeForCategory(moduleType);

        java.util.Set<Integer> seenSlotIndexes = new java.util.HashSet<>();
        for (ModuleInfoDto item : requested) {
            Integer slotIndex = item.getSlotIndex();
            if (slotIndex == null || slotIndex < 0 || slotIndex >= maxSlotCount)
                throw new BusinessException(ServerErrorCode.TOGGLE_FLEET_PRESET_MODULE_FAIL_INVALID_SLOT_INDEX);
            if (seenSlotIndexes.add(slotIndex) == false)
                throw new BusinessException(ServerErrorCode.TOGGLE_FLEET_PRESET_MODULE_FAIL_INVALID_SLOT_INDEX); // 같은 슬롯이 중복 요청됨

            target.add(new DesiredModule(moduleType, slotIndex, defaultSubType));
        }
    }

    // 함대편성 슬롯 전/후방 토글 저장
    @Transactional
    public void setFleetPresetShipFront(Long commanderId, FleetPresetSetFrontRequest request) {
        CommanderFleetPreset preset = commanderFleetPresetRepository.findByCommanderIdAndPresetIndex(commanderId, 0)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.COMMANDER_CONTROLLER_FAIL_NULL_ACTIVE_FLEET));

        preset.getSlots().stream()
                .filter(s -> s.getSlotIndex() == request.getSlotIndex())
                .findFirst()
                .ifPresent(slot -> slot.setFront(request.getIsFront()));
    }

    // 프리셋 기반 함대 시스템(CommanderFleetPreset)으로 전환 — 구식 Fleet 엔티티는 더 이상 참조하지 않음.
    // fleetId는 CommanderFleetPreset.id(FleetInfoDto.id로 클라에 내려준 값) — 미지정(0/null)이면 활성 프리셋(presetIndex=0)으로 폴백
    @Transactional
    public ChangeTacticOptionsResponse changeTacticOptions(Long commanderId, ChangeTacticOptionsRequest request) {
        CommanderFleetPreset preset;

        if (request.getFleetId() == null || request.getFleetId() == 0) {
            preset = commanderFleetPresetRepository.findByCommanderIdAndPresetIndex(commanderId, 0)
                    .orElseThrow(() -> new BusinessException(ServerErrorCode.FLEET_NOT_FOUND));
        } else {
            preset = commanderFleetPresetRepository.findByIdAndCommanderId(request.getFleetId(), commanderId)
                    .orElseThrow(() -> new BusinessException(ServerErrorCode.FLEET_NOT_FOUND));
        }

        preset.setTacticOptions(request.getTacticOptions());
        preset.setModified(Instant.now());
        commanderFleetPresetRepository.save(preset);

        return ChangeTacticOptionsResponse.builder()
                .tacticOptions(request.getTacticOptions())
                .build();
    }

    // ── 함대 능력치 요약 (Redis 저장용) ─────────────────────────────────────

    /** FleetInfoDto → Redis 저장용 능력치 요약 JSON (클라의 GetFleetCapabilityProfile에 대응) */
    public String computeFleetRankStatJson(FleetInfoDto fleetInfo) {
        if (fleetInfo == null || fleetInfo.getShips() == null) return null;

        int shipCount = fleetInfo.getShips().size();
        float statHealth = 0f, statAttack = 0f, statAirAttack = 0f;
        int statAirCount = 0;

        for (ShipInfoDto ship : fleetInfo.getShips()) {
            if (ship.getBodies() == null) continue;
            for (ModuleBodyInfoDto body : ship.getBodies()) {
                ModuleData bodyData = findModuleData(EModuleType.body, body.getModuleSubType());
                if (bodyData != null) statHealth += bodyData.getHealth() != null ? bodyData.getHealth() : 0f;

                if (body.getBeams() != null) {
                    for (ModuleInfoDto beam : body.getBeams()) {
                        ModuleData data = findModuleData(EModuleType.beam, beam.getModuleSubType());
                        if (data != null) {
                            statAttack += data.getAttack() != null ? data.getAttack() : 0f;
                        }
                    }
                }
                if (body.getMissiles() != null) {
                    for (ModuleInfoDto missile : body.getMissiles()) {
                        ModuleData data = findModuleData(EModuleType.missile, missile.getModuleSubType());
                        if (data != null) {
                            statAttack += data.getAttack() != null ? data.getAttack() : 0f;
                        }
                    }
                }
                if (body.getHangars() != null) {
                    for (ModuleInfoDto hangar : body.getHangars()) {
                        ModuleData data = findModuleData(EModuleType.hangar, hangar.getModuleSubType());
                        if (data != null) {
                            statAirCount  += data.getAirCount() != null ? data.getAirCount() : 0;
                            statAirAttack += data.getAirAttack() != null ? data.getAirAttack() : 0f;
                        }
                    }
                }
            }
        }

        return String.format("{\"shipCount\":%d,\"statHealth\":%.4f,\"statAttack\":%.4f,\"statAirCount\":%d,\"statAirAttack\":%.4f}",
                shipCount, statHealth, statAttack, statAirCount, statAirAttack);
    }

    private ModuleData findModuleData(EModuleType type, EModuleSubType subType) {
        if (subType == null) return null;
        List<ModuleData> list = gameDataService.getModulesByType(type);
        for (ModuleData data : list) {
            if (subType.equals(data.getModuleSubType())) return data;
        }
        return null;
    }
}









