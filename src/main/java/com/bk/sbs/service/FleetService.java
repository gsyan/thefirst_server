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

    private final FleetRepository fleetRepository;
    private final ShipRepository shipRepository;
    private final ShipModuleRepository shipModuleRepository;
    private final CommanderRepository commanderRepository;
    private final GameDataService gameDataService;
    private final CommanderFleetPresetRepository commanderFleetPresetRepository;

    // 신규 커맨더에게 지급되는 기본 함대 프리셋(presetIndex=0)의 초기 함선 — 바디 body_t1_m111(빔1/미사일1/격납고1) + 기본 빔1 장착
    private static final String DEFAULT_FLEET_PRESET_SHIP_PRESET_ID = "m11100";

    public FleetService(FleetRepository fleetRepository, ShipRepository shipRepository,
                       ShipModuleRepository shipModuleRepository,
                       CommanderRepository commanderRepository,
                       GameDataService gameDataService,
                       CommanderFleetPresetRepository commanderFleetPresetRepository) {
        this.fleetRepository = fleetRepository;
        this.shipRepository = shipRepository;
        this.shipModuleRepository = shipModuleRepository;
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

        List<ShipInfoDto> ships = preset.getSlots().stream()
                .sorted((a, b) -> Integer.compare(a.getSlotIndex(), b.getSlotIndex()))
                .map(slot -> ShipInfoDto.builder()
                        .shipPresetId(slot.getShipPresetId())
                        .isFront(slot.isFront())
                        .bodies(List.of(buildModuleBodyInfoDto(slot)))
                        .build())
                .collect(Collectors.toList());

        return FleetInfoDto.builder()
                .ships(ships)
                .build();
    }

    // 함대편성(FleetComposition) 슬롯에 함선 배치/교체 — 바디(프리셋) 자체를 바꾸는 동작이라 그 슬롯의 장착 모듈은 새 바디의 기본 로드아웃(빔1)으로 초기화됨
    @Transactional
    public void placeFleetPresetShip(Long commanderId, FleetPresetPlaceShipRequest request) {
        CommanderFleetPreset preset = commanderFleetPresetRepository.findByCommanderIdAndPresetIndex(commanderId, 0)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.COMMANDER_CONTROLLER_FAIL_NULL_ACTIVE_FLEET));

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
            case hanger -> EModuleSubType.hanger_t1;
            default -> null;
        };
    }

    private boolean isAttackModuleType(EModuleType moduleType) {
        return moduleType == EModuleType.beam || moduleType == EModuleType.missile || moduleType == EModuleType.hanger;
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
        List<ModuleInfoDto> hangers = new ArrayList<>();

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
                    case hanger -> hangers.add(dto);
                    default -> { }
                }
            }
        }

        return ModuleBodyInfoDto.builder()
                .beams(beams)
                .missiles(missiles)
                .hangers(hangers)
                .build();
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
        appendDesiredModules(desired, EModuleType.hanger, maxSlots[2], requestedModules != null ? requestedModules.getHangers() : null);

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

    // 캐릭터의 모든 함대 조회
    public List<FleetInfoDto> getUserFleets(Long commanderId) {
        List<Fleet> fleets = fleetRepository.findByCommanderIdOrderByActiveAndModified(commanderId);
        return fleets.stream()
                .map(this::convertFleetToFleetInfoDto)
                .collect(Collectors.toList());
    }

    // 특정 함대 상세 조회
    public FleetInfoDto getFleetDetail(Long commanderId, Long fleetId) {
        Fleet fleet = fleetRepository.findByIdAndCommanderIdAndDeletedFalse(fleetId, commanderId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.FLEET_NOT_FOUND));
        
        return convertToDetailDto(fleet);
    }

    // 활성 함대 조회
    public FleetInfoDto getActiveFleet(Long commanderId) {
        Fleet fleet = fleetRepository.findByCommanderIdAndIsActiveTrueAndDeletedFalse(commanderId)
                .orElse(null);
        
        return fleet != null ? convertToDetailDto(fleet) : null;
    }

    // 함대 생성 (기본 함선과 모듈 포함)
    @Transactional
    public FleetInfoDto createFleet(Long commanderId, String fleetName, String description) {
        if (fleetRepository.existsByCommanderIdAndFleetNameAndDeletedFalse(commanderId, fleetName)) {
            throw new BusinessException(ServerErrorCode.FLEET_DUPLICATE_NAME);
        }

        Fleet fleet = new Fleet();
        fleet.setCommanderId(commanderId);
        fleet.setFleetName(fleetName);
        fleet.setDescription(description);
        fleet.setActive(false); // 기본값: 비활성
        fleet.setFormation(EFormationType.linear_horizontal);
        
        fleet = fleetRepository.save(fleet);
        
        // 기본 함선 생성
        createDefaultShipsForFleet(fleet);
        
        return convertToDetailDto(fleet);
    }
    
    // 기본함선, 기본모듈 생성
    private void createDefaultShipsForFleet(Fleet fleet) {
        // 기본 함선 1개 생성
        Ship defaultShip = new Ship();
        defaultShip.setFleet(fleet);
        defaultShip.setShipName("Ship_" + 1);
        defaultShip.setPositionIndex(0); // 첫 번째 위치
        defaultShip.setDescription("Auto-generated default ship.");
        defaultShip = shipRepository.save(defaultShip);

        // GameDataService에서 레벨 1 모듈 데이터 가져오기
        ModuleData bodyData = gameDataService.getFirstModuleByType(EModuleType.body);
        ModuleData beamData = gameDataService.getFirstModuleByType(EModuleType.beam);
        ModuleData missileData = gameDataService.getFirstModuleByType(EModuleType.missile);
        ModuleData hangerData = gameDataService.getFirstModuleByType(EModuleType.hanger);

        // 1. Body
        ShipModule bodyModule = new ShipModule();
        bodyModule.setShip(defaultShip);
        bodyModule.setModuleType(EModuleType.body);
        bodyModule.setModuleSubType(EModuleSubType.body_t1_m111);
        bodyModule.setModuleLevel(1); // 레벨 축 삭제 — 타입당 서브타입 1개뿐이라 고정값
        bodyModule.setBodyIndex(0);
        bodyModule.setSlotIndex(0);
        bodyModule.setCurrentHealth(bodyData.getHealth() != null ? bodyData.getHealth() : 0f);
        shipModuleRepository.save(bodyModule);

        // 2. Beam (addShip과 동일하게 moduleUnlockPrice=1 투입)
        ShipModule beamModule = new ShipModule();
        beamModule.setShip(defaultShip);
        beamModule.setModuleType(EModuleType.beam);
        beamModule.setModuleSubType(EModuleSubType.beam_t1);
        beamModule.setModuleLevel(1); // 레벨 축 삭제 — 타입당 서브타입 1개뿐이라 고정값
        beamModule.setBodyIndex(0);
        beamModule.setSlotIndex(0);
        beamModule.setDeleted(false);
        beamModule.setCreated(Instant.now());
        beamModule.setModified(Instant.now());
        shipModuleRepository.save(beamModule);

//        // Missile
//        ShipModule missileModule = new ShipModule();
//        missileModule.setShip(defaultShip);
//        missileModule.setModuleType(EModuleType.missile);
//        missileModule.setModuleSubType(EModuleSubType.missile_t1_std);
//        missileModule.setModuleLevel(missileData.getModuleLevel());
//        missileModule.setBodyIndex(0);
//        missileModule.setSlotIndex(0);
//        shipModuleRepository.save(missileModule);

//        // Hanger 모듈 (type 4)
//        ShipModule hangerModule = new ShipModule();
//        hangerModule.setShip(defaultShip);
//        hangerModule.setModuleType(EModuleType.hanger);
//        hangerModule.setModuleSubType(EModuleSubType.hanger_t1_std);
//        hangerModule.setModuleLevel(hangerData.getModuleLevel());
//        hangerModule.setBodyIndex(0);
//        hangerModule.setSlotIndex(0);
//        shipModuleRepository.save(hangerModule);


        System.out.println("Default ship and modules created: " + defaultShip.getShipName());
    }

    // 함대 활성화
    @Transactional
    public void activateFleet(Long commanderId, Long fleetId) {
        // 기존 활성 함대 비활성화
        fleetRepository.findByCommanderIdAndIsActiveTrueAndDeletedFalse(commanderId)
                .ifPresent(activeFleet -> {
                    activeFleet.setActive(false);
                    activeFleet.setModified(Instant.now());
                    fleetRepository.save(activeFleet);
                });

        // 새 함대 활성화
        Fleet fleet = fleetRepository.findByIdAndCommanderIdAndDeletedFalse(fleetId, commanderId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.FLEET_NOT_FOUND));
        
        fleet.setActive(true);
        fleet.setModified(Instant.now());
        fleetRepository.save(fleet);
    }

    // 첫 번째 함대를 활성화 (캐릭터 생성 시 사용)
    @Transactional
    public void activateFirstFleet(Long commanderId) {
        List<Fleet> fleets = fleetRepository.findByCommanderIdOrderByActiveAndModified(commanderId);
        if (!fleets.isEmpty()) {
            Fleet firstFleet = fleets.get(0);
            firstFleet.setActive(true);
            firstFleet.setModified(Instant.now());
            fleetRepository.save(firstFleet);
        }
    }

//    // 클라이언트 데이터 가져오기 (Export)
//    @Transactional(readOnly = true)
//    public FleetExportResponse exportFleet(Long commanderId, Long fleetId) {
//        Fleet fleet = fleetRepository.findByIdAndCommanderIdAndDeletedFalse(fleetId, commanderId)
//                .orElseThrow(() -> new BusinessException(ServerErrorCode.FLEET_NOT_FOUND));
//
//        FleetExportResponse response = new FleetExportResponse();
//        response.setFleetName(fleet.getFleetName());
//        response.setDescription(fleet.getDescription());
//        response.setActive(fleet.isActive());
//
//        List<Ship> ships = shipRepository.findByFleetIdAndDeletedFalseOrderByPositionIndex(fleetId);
//        List<FleetExportResponse.ShipExportData> shipData = ships.stream()
//                .map(ship -> {
//                    FleetExportResponse.ShipExportData shipExport = new FleetExportResponse.ShipExportData();
//                    shipExport.setShipName(ship.getShipName());
//                    shipExport.setPositionIndex(ship.getPositionIndex());
//                    shipExport.setDescription(ship.getDescription());
//
//                    List<ShipModule> modules = shipModuleRepository.findByShipIdAndDeletedFalseOrderBySlotIndex(ship.getId());
//                    List<FleetExportResponse.ShipModuleExportData> moduleData = modules.stream()
//                            .map(module -> {
//                                FleetExportResponse.ShipModuleExportData moduleExport = new FleetExportResponse.ShipModuleExportData();
//                                moduleExport.setModuleType(module.getModuleType());
//                                moduleExport.setModuleLevel(module.getModuleLevel());
//                                moduleExport.setSlotIndex(module.getSlotIndex());
//                                return moduleExport;
//                            })
//                            .collect(Collectors.toList());
//                    shipExport.setModules(moduleData);
//                    return shipExport;
//                })
//                .collect(Collectors.toList());
//        response.setShips(shipData);
//
//        return response;
//    }

//    // 클라이언트 데이터 저장 (Import)
//    @Transactional
//    public FleetInfoDto importFleet(Long commanderId, FleetImportRequest request) {
//        // 기존 함대명 중복 체크
//        if (fleetRepository.existsByCommanderIdAndFleetNameAndDeletedFalse(commanderId, request.getFleetName())) {
//            throw new BusinessException(ServerErrorCode.FLEET_DUPLICATE_NAME);
//        }
//
//        // 함대 생성
//        Fleet fleet = new Fleet();
//        fleet.setCommanderId(commanderId);
//        fleet.setFleetName(request.getFleetName());
//        fleet.setDescription(request.getDescription());
//        fleet.setActive(request.isActive());
//
//        // 활성 함대가 이미 있다면 비활성화
//        if (request.isActive()) {
//            fleetRepository.findByCommanderIdAndIsActiveTrueAndDeletedFalse(commanderId)
//                    .ifPresent(activeFleet -> {
//                        activeFleet.setActive(false);
//                        activeFleet.setModified(Instant.now());
//                        fleetRepository.save(activeFleet);
//                    });
//        }
//
//        fleet = fleetRepository.save(fleet);
//
//        // 함선들 생성
//        if (request.getShips() != null) {
//            for (FleetImportRequest.ShipImportData shipData : request.getShips()) {
//                Ship ship = new Ship();
//                ship.setFleet(fleet);
//                ship.setShipName(shipData.getShipName());
//                ship.setPositionIndex(shipData.getPositionIndex());
//                ship.setDescription(shipData.getDescription());
//                ship = shipRepository.save(ship);
//
//                // 모듈들 생성
//                if (shipData.getModules() != null) {
//                    for (FleetImportRequest.ShipModuleImportData moduleData : shipData.getModules()) {
//                        ShipModule module = new ShipModule();
//                        module.setShip(ship);
//                        module.setModuleType(moduleData.getModuleType());
//                        module.setModuleLevel(moduleData.getModuleLevel());
//                        module.setSlotIndex(moduleData.getSlotIndex());
//                        shipModuleRepository.save(module);
//                    }
//                }
//            }
//        }
//
//        return convertToDetailDto(fleet);
//    }

//    // 함대 업데이트
//    @Transactional
//    public FleetInfoDto updateFleet(Long commanderId, Long fleetId, FleetImportRequest request) {
//        Fleet fleet = fleetRepository.findByIdAndCommanderIdAndDeletedFalse(fleetId, commanderId)
//                .orElseThrow(() -> new BusinessException(ServerErrorCode.FLEET_NOT_FOUND));
//
//        // 함대명 변경 시 중복 체크
//        if (!fleet.getFleetName().equals(request.getFleetName()) &&
//            fleetRepository.existsByCommanderIdAndFleetNameAndDeletedFalse(commanderId, request.getFleetName())) {
//            throw new BusinessException(ServerErrorCode.FLEET_DUPLICATE_NAME);
//        }
//
//        // 함대 정보 업데이트
//        fleet.setFleetName(request.getFleetName());
//        fleet.setDescription(request.getDescription());
//        fleet.setModified(Instant.now());
//
//        // 활성 상태 변경
//        if (request.isActive() && !fleet.isActive()) {
//            activateFleet(commanderId, fleetId);
//        } else if (!request.isActive() && fleet.isActive()) {
//            fleet.setActive(false);
//        }
//
//        // 기존 함선과 모듈들 삭제 (soft delete)
//        List<Ship> existingShips = shipRepository.findByFleetIdAndDeletedFalseOrderByPositionIndex(fleetId);
//        for (Ship ship : existingShips) {
//            List<ShipModule> modules = shipModuleRepository.findByShipIdAndDeletedFalseOrderBySlotIndex(ship.getId());
//            for (ShipModule module : modules) {
//                module.setDeleted(true);
//                module.setModified(Instant.now());
//                shipModuleRepository.save(module);
//            }
//            ship.setDeleted(true);
//            ship.setModified(Instant.now());
//            shipRepository.save(ship);
//        }
//
//        // 새로운 함선과 모듈들 생성
//        if (request.getShips() != null) {
//            for (FleetImportRequest.ShipImportData shipData : request.getShips()) {
//                Ship ship = new Ship();
//                ship.setFleet(fleet);
//                ship.setShipName(shipData.getShipName());
//                ship.setPositionIndex(shipData.getPositionIndex());
//                ship.setDescription(shipData.getDescription());
//                ship = shipRepository.save(ship);
//
//                if (shipData.getModules() != null) {
//                    for (FleetImportRequest.ShipModuleImportData moduleData : shipData.getModules()) {
//                        ShipModule module = new ShipModule();
//                        module.setShip(ship);
//                        module.setModuleType(moduleData.getModuleType());
//                        module.setModuleLevel(moduleData.getModuleLevel());
//                        module.setSlotIndex(moduleData.getSlotIndex());
//                        shipModuleRepository.save(module);
//                    }
//                }
//            }
//        }
//
//        fleet = fleetRepository.save(fleet);
//        return convertToDetailDto(fleet);
//    }

    // 함대 삭제 (soft delete)
    @Transactional
    public void deleteFleet(Long commanderId, Long fleetId) {
        Fleet fleet = fleetRepository.findByIdAndCommanderIdAndDeletedFalse(fleetId, commanderId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.FLEET_NOT_FOUND));

        // 함선과 모듈들도 함께 삭제
        List<Ship> ships = shipRepository.findByFleetIdAndDeletedFalseOrderByPositionIndex(fleetId);
        for (Ship ship : ships) {
            List<ShipModule> modules = shipModuleRepository.findByShipIdAndDeletedFalseOrderBySlotIndex(ship.getId());
            for (ShipModule module : modules) {
                module.setDeleted(true);
                module.setModified(Instant.now());
                shipModuleRepository.save(module);
            }
            ship.setDeleted(true);
            ship.setModified(Instant.now());
            shipRepository.save(ship);
        }

        fleet.setDeleted(true);
        fleet.setModified(Instant.now());
        fleetRepository.save(fleet);
    }

    // Entity -> DTO 변환 (기본 정보만)
    private FleetInfoDto convertFleetToFleetInfoDto(Fleet fleet) {
        return FleetInfoDto.builder()
                .id(fleet.getId())
                .fleetName(fleet.getFleetName())
                .description(fleet.getDescription())
                .isActive(fleet.isActive())
                .formation(fleet.getFormation())
                .tacticOptions(fleet.getTacticOptions())
                .build();
    }

    // Entity -> DTO 변환 (상세 정보 포함)
    private FleetInfoDto convertToDetailDto(Fleet fleet) {
        FleetInfoDto dto = convertFleetToFleetInfoDto(fleet);
        
        List<Ship> ships = shipRepository.findByFleetIdAndDeletedFalseOrderByPositionIndex(fleet.getId());
        List<ShipInfoDto> shipDtos = ships.stream()
                .map(this::convertShipToShipInfoDto)
                .collect(Collectors.toList());
        dto.setShips(shipDtos);
        
        return dto;
    }

    private List<ModuleBodyInfoDto> convertToBodyModules(List<ShipModule> modules) {
        return modules.stream()
                .filter(m -> m.getModuleType() == EModuleType.body)
                .map(bodyModule -> {
                    int bodyIndex = bodyModule.getBodyIndex();

                    List<ModuleInfoDto> beams = modules.stream()
                            .filter(m -> m.getModuleType() == EModuleType.beam && m.getBodyIndex() == bodyIndex)
                            .map(beamModule -> ModuleInfoDto.builder()
                                    .moduleType(beamModule.getModuleType())
                                    .moduleSubType(beamModule.getModuleSubType())
                                    .moduleLevel(beamModule.getModuleLevel())
                                    .bodyIndex(beamModule.getBodyIndex())
                                    .slotIndex(beamModule.getSlotIndex())
                                    .build())
                            .collect(Collectors.toList());

                    List<ModuleInfoDto> missiles = modules.stream()
                            .filter(m -> m.getModuleType() == EModuleType.missile && m.getBodyIndex() == bodyIndex)
                            .map(missileModule -> ModuleInfoDto.builder()
                                    .moduleType(missileModule.getModuleType())
                                    .moduleSubType(missileModule.getModuleSubType())
                                    .moduleLevel(missileModule.getModuleLevel())
                                    .bodyIndex(missileModule.getBodyIndex())
                                    .slotIndex(missileModule.getSlotIndex())
                                    .build())
                            .collect(Collectors.toList());

                    List<ModuleInfoDto> hangers = modules.stream()
                            .filter(m -> m.getModuleType() == EModuleType.hanger && m.getBodyIndex() == bodyIndex)
                            .map(hangerModule -> ModuleInfoDto.builder()
                                    .moduleType(hangerModule.getModuleType())
                                    .moduleSubType(hangerModule.getModuleSubType())
                                    .moduleLevel(hangerModule.getModuleLevel())
                                    .bodyIndex(hangerModule.getBodyIndex())
                                    .slotIndex(hangerModule.getSlotIndex())
                                    .build())
                            .collect(Collectors.toList());

                    float maxHealth = gameDataService.getModulesByType(EModuleType.body).stream()
                            .filter(d -> d.getModuleSubType() == bodyModule.getModuleSubType())
                            .findFirst()
                            .map(d -> d.getHealth() != null ? d.getHealth() : 0f)
                            .orElse(0f);
                    float normalizedHealth = maxHealth > 0f
                            ? Math.min(bodyModule.getCurrentHealth(), maxHealth)
                            : bodyModule.getCurrentHealth();

                    return ModuleBodyInfoDto.builder()
                            .moduleType(bodyModule.getModuleType())
                            .moduleSubType(bodyModule.getModuleSubType())
                            .moduleLevel(bodyModule.getModuleLevel())
                            .bodyIndex(bodyIndex)
                            .beams(beams)
                            .missiles(missiles)
                            .hangers(hangers)
                            .currentHealth(normalizedHealth)
                            .build();
                })
                .collect(Collectors.toList());
    }

    private ShipInfoDto convertShipToShipInfoDto(Ship ship) {
        List<ShipModule> modules = shipModuleRepository.findByShipIdAndDeletedFalseOrderBySlotIndex(ship.getId());
        List<ModuleBodyInfoDto> bodyDtos = convertToBodyModules(modules);

        return ShipInfoDto.builder()
                .id(ship.getId())
                .fleetId(ship.getFleet().getId())
                .shipName(ship.getShipName())
                .positionIndex(ship.getPositionIndex())
                .description(ship.getDescription())
                .bodies(bodyDtos)
                .build();
    }

    @Transactional
    public AddShipResponse addShip(Long commanderId, AddShipRequest request) {
        // 캐릭터 조회 (비관적 락)
        Commander commander = commanderRepository.findByIdForUpdate(commanderId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.ADD_SHIP_FAIL_SHIP_NOT_FOUND));

        // 대상 함대 결정 (요청에 fleetId가 없으면 활성 함대 사용)
        Fleet targetFleet;
        if (request.getFleetId() != null) {
            targetFleet = fleetRepository.findByIdAndCommanderIdAndDeletedFalse(request.getFleetId(), commanderId)
                    .orElseThrow(() -> new BusinessException(ServerErrorCode.ADD_SHIP_FAIL_FLEET_NOT_FOUND));
        } else {
            targetFleet = fleetRepository.findByCommanderIdAndIsActiveTrueAndDeletedFalse(commanderId)
                    .orElseThrow(() -> new BusinessException(ServerErrorCode.ADD_SHIP_FAIL_ACTIVE_FLEET_NOT_FOUND));
        }

        int maxShipsPerFleet = gameDataService.getMaxShipsPerFleet();

        List<Ship> currentShips = shipRepository.findByFleetIdAndDeletedFalseOrderByPositionIndex(targetFleet.getId());
        if (currentShips.size() >= maxShipsPerFleet) {
            throw new BusinessException(ServerErrorCode.ADD_SHIP_FAIL_FLEET_MAX_SHIPS_REACHED);
        }

        // 커맨더 레벨 검증 — 현재 레벨에서 허용된 최대 함선 수(ship_count) 초과 여부
        int charCommanderLevel = commander.getCommanderLevel();
        if (currentShips.size() >= gameDataService.getShipCount(charCommanderLevel)) {
            throw new BusinessException(ServerErrorCode.ADD_SHIP_FAIL_INSUFFICIENT_COMMANDER_LEVEL);
        }

        // 빠진 함대 positionIndex(삭제된 슬롯) 중 가장 낮은 값 사용
        java.util.Set<Integer> usedIndexes = currentShips.stream()
                .map(Ship::getPositionIndex)
                .collect(java.util.stream.Collectors.toSet());
        int newPositionIndex = 0;
        while (usedIndexes.contains(newPositionIndex)) newPositionIndex++;

        // 새 함선 생성
        Ship newShip = new Ship();
        newShip.setFleet(targetFleet);
        newShip.setShipName("Ship_" + (newPositionIndex + 1));
        newShip.setPositionIndex(newPositionIndex);
        newShip.setDeleted(false);
        newShip.setCreated(Instant.now());
        newShip.setModified(Instant.now());
        Ship savedShip = shipRepository.save(newShip);

        // 기본 모듈들 생성 (Body, Weapon)
        createDefaultModules(savedShip);

        // 응답 생성
        AddShipResponse response = AddShipResponse.builder()
                .newShipInfo(convertShipToShipInfoDto(savedShip))
                .build();

        return response;
    }

    private void createDefaultModules(Ship ship) {
        ModuleData bodyData = gameDataService.getFirstModuleByType(EModuleType.body);
        // Body 모듈
        ShipModule bodyModule = new ShipModule();
        bodyModule.setShip(ship);
        bodyModule.setModuleType(EModuleType.body);
        bodyModule.setModuleSubType(EModuleSubType.body_t1_m111);
        bodyModule.setModuleLevel(1);
        bodyModule.setBodyIndex(0);
        bodyModule.setSlotIndex(0);
        bodyModule.setCurrentHealth(bodyData != null && bodyData.getHealth() != null ? bodyData.getHealth() : 0f);
        bodyModule.setDeleted(false);
        bodyModule.setCreated(Instant.now());
        bodyModule.setModified(Instant.now());
        shipModuleRepository.save(bodyModule);

        // Beam 모듈
        ShipModule weaponModule = new ShipModule();
        weaponModule.setShip(ship);
        weaponModule.setModuleType(EModuleType.beam);
        weaponModule.setModuleSubType(EModuleSubType.beam_t1);
        weaponModule.setModuleLevel(1);
        weaponModule.setBodyIndex(0);
        weaponModule.setSlotIndex(0);
        weaponModule.setDeleted(false);
        weaponModule.setCreated(Instant.now());
        weaponModule.setModified(Instant.now());
        shipModuleRepository.save(weaponModule);
    }

    @Transactional
    public ChangeFormationResponse changeFormation(Long commanderId, ChangeFormationRequest request) {
        Fleet fleet;

        // fleetId가 null이거나 0이면 활성 함대 사용
        if (request.getFleetId() == null || request.getFleetId() == 0) {
            fleet = fleetRepository.findByCommanderIdAndIsActiveTrueAndDeletedFalse(commanderId)
                    .orElseThrow(() -> new BusinessException(ServerErrorCode.FLEET_NOT_FOUND));
        } else {
            fleet = fleetRepository.findByIdAndCommanderIdAndDeletedFalse(request.getFleetId(), commanderId)
                    .orElseThrow(() -> new BusinessException(ServerErrorCode.FLEET_NOT_FOUND));
        }

        EFormationType formationType = request.getFormationType();

        fleet.setFormation(formationType);
        fleet.setModified(Instant.now());
        fleetRepository.save(fleet);

        return ChangeFormationResponse.builder()
                .formation(formationType)
                .build();
    }

    @Transactional
    public ChangeTacticOptionsResponse changeTacticOptions(Long commanderId, ChangeTacticOptionsRequest request) {
        Fleet fleet;

        if (request.getFleetId() == null || request.getFleetId() == 0) {
            fleet = fleetRepository.findByCommanderIdAndIsActiveTrueAndDeletedFalse(commanderId)
                    .orElseThrow(() -> new BusinessException(ServerErrorCode.FLEET_NOT_FOUND));
        } else {
            fleet = fleetRepository.findByIdAndCommanderIdAndDeletedFalse(request.getFleetId(), commanderId)
                    .orElseThrow(() -> new BusinessException(ServerErrorCode.FLEET_NOT_FOUND));
        }

        fleet.setTacticOptions(request.getTacticOptions());
        fleet.setModified(Instant.now());
        fleetRepository.save(fleet);

        return ChangeTacticOptionsResponse.builder()
                .tacticOptions(request.getTacticOptions())
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 함선 리셋 + 삭제 (전 모듈 투자분 환급 후 함선 soft delete)
    // 기함(positionIndex == 0) 불가
    // ─────────────────────────────────────────────────────────────────────────
    @Transactional
    public ShipResetRemoveResponse resetAndRemoveShip(Long commanderId, ShipResetRemoveRequest request) {
        Ship ship = shipRepository.findById(request.getShipId())
                .orElseThrow(() -> new BusinessException(ServerErrorCode.RESET_SHIP_FAIL_SHIP_NOT_FOUND));

        if (!ship.getFleet().getCommanderId().equals(commanderId))
            throw new BusinessException(ServerErrorCode.RESET_SHIP_FAIL_FLEET_ACCESS_DENIED);

        if (ship.getPositionIndex() == 0)
            throw new BusinessException(ServerErrorCode.RESET_SHIP_FAIL_FLAGSHIP_FORBIDDEN);

        Commander commander = commanderRepository.findByIdForUpdate(commanderId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.RESET_SHIP_FAIL_COMMANDER_NOT_FOUND));

        // 모듈 soft delete
        List<ShipModule> allModules = shipModuleRepository.findByShipIdAndDeletedFalseOrderBySlotIndex(request.getShipId());
        for (ShipModule mod : allModules) {
            mod.setDeleted(true);
            mod.setModified(Instant.now());
            shipModuleRepository.save(mod);
        }

        // 함선 soft delete
        ship.setDeleted(true);
        ship.setModified(Instant.now());
        shipRepository.save(ship);

        return ShipResetRemoveResponse.builder()
                .removedShipId(request.getShipId())
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
                if (body.getHangers() != null) {
                    for (ModuleInfoDto hanger : body.getHangers()) {
                        ModuleData data = findModuleData(EModuleType.hanger, hanger.getModuleSubType());
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

    @Transactional
    public FleetInstantRepairResponse instantRepairFleet(Long commanderId) {
        Commander commander = commanderRepository.findByIdForUpdate(commanderId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.FLEET_INSTANT_REPAIR_FAIL_COMMANDER_NOT_FOUND));

        Fleet fleet = fleetRepository.findByCommanderIdAndIsActiveTrueAndDeletedFalse(commanderId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.FLEET_INSTANT_REPAIR_FAIL_FLEET_NOT_FOUND));

        List<Ship> ships = shipRepository.findByFleetIdAndDeletedFalseOrderByPositionIndex(fleet.getId());
        List<ModuleData> bodyDataList = gameDataService.getModulesByType(EModuleType.body);

        // HP 전체 회복
        for (Ship ship : ships) {
            List<ShipModule> modules = shipModuleRepository.findByShipIdAndDeletedFalseOrderBySlotIndex(ship.getId());
            for (ShipModule m : modules) {
                if (m.getModuleType() != EModuleType.body) continue;
                float maxHealth = bodyDataList.stream()
                        .filter(d -> d.getModuleSubType() == m.getModuleSubType())
                        .findFirst()
                        .map(d -> d.getHealth() != null ? d.getHealth() : 0f)
                        .orElse(0f);
                m.setCurrentHealth(maxHealth);
                shipModuleRepository.save(m);
            }
        }

        return FleetInstantRepairResponse.builder().build();
    }

    @Transactional
    public void saveFleetHealth(Long commanderId, FleetHealthSaveRequest request) {
        if (request.getShips() == null) {
            return;
        }

        for (ShipHealthInfoDto shipHealth : request.getShips()) {
            Ship ship = shipRepository.findById(shipHealth.getShipId()).orElse(null);
            if (ship == null || !ship.getFleet().getCommanderId().equals(commanderId)) continue;
            if (shipHealth.getBodies() == null) continue;

            for (BodyHealthEntryDto entry : shipHealth.getBodies()) {
                Optional<ShipModule> bodyOpt = shipModuleRepository.findByShipIdAndBodyIndexAndModuleTypeAndSlotIndexAndDeletedFalse(
                        shipHealth.getShipId(), entry.getBodyIndex(), EModuleType.body, 0);
                if (bodyOpt.isEmpty()) continue;

                ShipModule body = bodyOpt.get();
                body.setCurrentHealth(entry.getCurrentHealth());
                shipModuleRepository.save(body);
            }
        }
    }
}









