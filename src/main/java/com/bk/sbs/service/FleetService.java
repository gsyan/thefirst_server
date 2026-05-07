package com.bk.sbs.service;

import com.bk.sbs.config.DataTableModule;
import com.bk.sbs.dto.*;
import com.bk.sbs.entity.*;
import com.bk.sbs.enums.*;
import com.bk.sbs.exception.BusinessException;
import com.bk.sbs.exception.ServerErrorCode;
import com.bk.sbs.repository.*;
import com.bk.sbs.util.ModuleTypeConverter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
    private final ShipModuleLevelRepository shipModuleLevelRepository;
    private final CharacterRepository characterRepository;
    private final ModuleResearchRepository moduleResearchRepository;
    private final GameDataService gameDataService;

    public FleetService(FleetRepository fleetRepository, ShipRepository shipRepository,
                       ShipModuleRepository shipModuleRepository, ShipModuleLevelRepository shipModuleLevelRepository,
                       CharacterRepository characterRepository,
                       ModuleResearchRepository moduleResearchRepository, GameDataService gameDataService) {
        this.fleetRepository = fleetRepository;
        this.shipRepository = shipRepository;
        this.shipModuleRepository = shipModuleRepository;
        this.shipModuleLevelRepository = shipModuleLevelRepository;
        this.characterRepository = characterRepository;
        this.moduleResearchRepository = moduleResearchRepository;
        this.gameDataService = gameDataService;
    }

    // 캐릭터의 모든 함대 조회
    public List<FleetInfoDto> getUserFleets(Long characterId) {
        List<Fleet> fleets = fleetRepository.findByCharacterIdOrderByActiveAndModified(characterId);
        return fleets.stream()
                .map(this::convertFleetToFleetInfoDto)
                .collect(Collectors.toList());
    }

    // 특정 함대 상세 조회
    public FleetInfoDto getFleetDetail(Long characterId, Long fleetId) {
        Fleet fleet = fleetRepository.findByIdAndCharacterIdAndDeletedFalse(fleetId, characterId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.FLEET_NOT_FOUND));
        
        return convertToDetailDto(fleet);
    }

    // 활성 함대 조회
    public FleetInfoDto getActiveFleet(Long characterId) {
        Fleet fleet = fleetRepository.findByCharacterIdAndIsActiveTrueAndDeletedFalse(characterId)
                .orElse(null);
        
        return fleet != null ? convertToDetailDto(fleet) : null;
    }

    // 함대 생성 (기본 함선과 모듈 포함)
    @Transactional
    public FleetInfoDto createFleet(Long characterId, String fleetName, String description) {
        if (fleetRepository.existsByCharacterIdAndFleetNameAndDeletedFalse(characterId, fleetName)) {
            throw new BusinessException(ServerErrorCode.FLEET_DUPLICATE_NAME);
        }

        Fleet fleet = new Fleet();
        fleet.setCharacterId(characterId);
        fleet.setFleetName(fleetName);
        fleet.setDescription(description);
        fleet.setActive(false); // 기본값: 비활성
        fleet.setFormation(EFormationType.formation_type_linear_horizontal);
        
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
        bodyModule.setModuleSubType(EModuleSubType.body_t1_m1);
        bodyModule.setModuleLevel(bodyData.getModuleLevel());
        bodyModule.setBodyIndex(0);
        bodyModule.setSlotIndex(0);
        bodyModule.setCurrentHealth(bodyData.getHealth() != null ? bodyData.getHealth() : 0f);
        shipModuleRepository.save(bodyModule);
        saveInitialModuleLevel(defaultShip, EModuleType.body, EModuleSubType.body_t1_m1, bodyData.getModuleLevel(), 0, 0);

        // 2. Beam
//        ShipModule beamModule = new ShipModule();
//        beamModule.setShip(defaultShip);
//        beamModule.setModuleType(EModuleType.beam);
//        beamModule.setModuleSubType(EModuleSubType.beam_t1_std);
//        beamModule.setModuleLevel(beamData.getModuleLevel());
//        beamModule.setBodyIndex(0);
//        beamModule.setSlotIndex(0);
//        shipModuleRepository.save(beamModule);

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

    // 초기 모듈 생성 시 ship_module_level에 ver1 서브타입을 무료 이력으로 등록
    private void saveInitialModuleLevel(Ship ship, EModuleType moduleType, EModuleSubType moduleSubType, int level, int bodyIndex, int slotIndex) {
        ShipModuleLevel record = new ShipModuleLevel();
        record.setShip(ship);
        record.setBodyIndex(bodyIndex);
        record.setModuleType(moduleType);
        record.setSlotIndex(slotIndex);
        record.setModuleSubType(moduleSubType);
        record.setLevel(level);
        shipModuleLevelRepository.save(record);
    }

    // 함대 활성화
    @Transactional
    public void activateFleet(Long characterId, Long fleetId) {
        // 기존 활성 함대 비활성화
        fleetRepository.findByCharacterIdAndIsActiveTrueAndDeletedFalse(characterId)
                .ifPresent(activeFleet -> {
                    activeFleet.setActive(false);
                    activeFleet.setModified(LocalDateTime.now());
                    fleetRepository.save(activeFleet);
                });

        // 새 함대 활성화
        Fleet fleet = fleetRepository.findByIdAndCharacterIdAndDeletedFalse(fleetId, characterId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.FLEET_NOT_FOUND));
        
        fleet.setActive(true);
        fleet.setModified(LocalDateTime.now());
        fleetRepository.save(fleet);
    }

    // 첫 번째 함대를 활성화 (캐릭터 생성 시 사용)
    @Transactional
    public void activateFirstFleet(Long characterId) {
        List<Fleet> fleets = fleetRepository.findByCharacterIdOrderByActiveAndModified(characterId);
        if (!fleets.isEmpty()) {
            Fleet firstFleet = fleets.get(0);
            firstFleet.setActive(true);
            firstFleet.setModified(LocalDateTime.now());
            fleetRepository.save(firstFleet);
        }
    }

//    // 클라이언트 데이터 가져오기 (Export)
//    @Transactional(readOnly = true)
//    public FleetExportResponse exportFleet(Long characterId, Long fleetId) {
//        Fleet fleet = fleetRepository.findByIdAndCharacterIdAndDeletedFalse(fleetId, characterId)
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
//    public FleetInfoDto importFleet(Long characterId, FleetImportRequest request) {
//        // 기존 함대명 중복 체크
//        if (fleetRepository.existsByCharacterIdAndFleetNameAndDeletedFalse(characterId, request.getFleetName())) {
//            throw new BusinessException(ServerErrorCode.FLEET_DUPLICATE_NAME);
//        }
//
//        // 함대 생성
//        Fleet fleet = new Fleet();
//        fleet.setCharacterId(characterId);
//        fleet.setFleetName(request.getFleetName());
//        fleet.setDescription(request.getDescription());
//        fleet.setActive(request.isActive());
//
//        // 활성 함대가 이미 있다면 비활성화
//        if (request.isActive()) {
//            fleetRepository.findByCharacterIdAndIsActiveTrueAndDeletedFalse(characterId)
//                    .ifPresent(activeFleet -> {
//                        activeFleet.setActive(false);
//                        activeFleet.setModified(LocalDateTime.now());
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
//    public FleetInfoDto updateFleet(Long characterId, Long fleetId, FleetImportRequest request) {
//        Fleet fleet = fleetRepository.findByIdAndCharacterIdAndDeletedFalse(fleetId, characterId)
//                .orElseThrow(() -> new BusinessException(ServerErrorCode.FLEET_NOT_FOUND));
//
//        // 함대명 변경 시 중복 체크
//        if (!fleet.getFleetName().equals(request.getFleetName()) &&
//            fleetRepository.existsByCharacterIdAndFleetNameAndDeletedFalse(characterId, request.getFleetName())) {
//            throw new BusinessException(ServerErrorCode.FLEET_DUPLICATE_NAME);
//        }
//
//        // 함대 정보 업데이트
//        fleet.setFleetName(request.getFleetName());
//        fleet.setDescription(request.getDescription());
//        fleet.setModified(LocalDateTime.now());
//
//        // 활성 상태 변경
//        if (request.isActive() && !fleet.isActive()) {
//            activateFleet(characterId, fleetId);
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
//                module.setModified(LocalDateTime.now());
//                shipModuleRepository.save(module);
//            }
//            ship.setDeleted(true);
//            ship.setModified(LocalDateTime.now());
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
    public void deleteFleet(Long characterId, Long fleetId) {
        Fleet fleet = fleetRepository.findByIdAndCharacterIdAndDeletedFalse(fleetId, characterId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.FLEET_NOT_FOUND));

        // 함선과 모듈들도 함께 삭제
        List<Ship> ships = shipRepository.findByFleetIdAndDeletedFalseOrderByPositionIndex(fleetId);
        for (Ship ship : ships) {
            List<ShipModule> modules = shipModuleRepository.findByShipIdAndDeletedFalseOrderBySlotIndex(ship.getId());
            for (ShipModule module : modules) {
                module.setDeleted(true);
                module.setModified(LocalDateTime.now());
                shipModuleRepository.save(module);
            }
            ship.setDeleted(true);
            ship.setModified(LocalDateTime.now());
            shipRepository.save(ship);
        }

        fleet.setDeleted(true);
        fleet.setModified(LocalDateTime.now());
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

    private List<ModuleBodyInfoDto> convertToBodyModules(List<ShipModule> modules, List<ShipModuleLevel> allLevels) {
        // bodyIndex+moduleType+slotIndex → unlockedSubTypes 맵 (한 번만 빌드)
        java.util.Map<String, List<EModuleSubType>> unlockedMap = new java.util.HashMap<>();
        for (ShipModuleLevel lvl : allLevels) {
            String key = lvl.getBodyIndex() + "_" + lvl.getModuleType() + "_" + lvl.getSlotIndex();
            unlockedMap.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(lvl.getModuleSubType());
        }

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
                                    .unlockedSubTypes(unlockedMap.getOrDefault(
                                            bodyIndex + "_" + EModuleType.beam + "_" + beamModule.getSlotIndex(),
                                            java.util.Collections.emptyList()))
                                    .investedMineral(beamModule.getInvestedMineral())
                                    .investedPvpMineral(beamModule.getInvestedPvpMineral())
                                    .investedTempMineral(beamModule.getInvestedTempMineral())
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
                                    .unlockedSubTypes(unlockedMap.getOrDefault(
                                            bodyIndex + "_" + EModuleType.missile + "_" + missileModule.getSlotIndex(),
                                            java.util.Collections.emptyList()))
                                    .investedMineral(missileModule.getInvestedMineral())
                                    .investedPvpMineral(missileModule.getInvestedPvpMineral())
                                    .investedTempMineral(missileModule.getInvestedTempMineral())
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
                                    .unlockedSubTypes(unlockedMap.getOrDefault(
                                            bodyIndex + "_" + EModuleType.hanger + "_" + hangerModule.getSlotIndex(),
                                            java.util.Collections.emptyList()))
                                    .investedMineral(hangerModule.getInvestedMineral())
                                    .investedPvpMineral(hangerModule.getInvestedPvpMineral())
                                    .investedTempMineral(hangerModule.getInvestedTempMineral())
                                    .build())
                            .collect(Collectors.toList());

                    // body 자체의 unlockedSubTypes (slotIndex=0 고정)
                    List<EModuleSubType> bodyUnlocked = unlockedMap.getOrDefault(
                            bodyIndex + "_" + EModuleType.body + "_0", java.util.Collections.emptyList());

                    float maxHealth = gameDataService.getModulesByType(EModuleType.body).stream()
                            .filter(d -> d.getModuleSubType() == bodyModule.getModuleSubType()
                                    && d.getModuleLevel().equals(bodyModule.getModuleLevel()))
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
                            .unlockedSubTypes(bodyUnlocked)
                            .investedMineral(bodyModule.getInvestedMineral())
                            .investedPvpMineral(bodyModule.getInvestedPvpMineral())
                            .investedTempMineral(bodyModule.getInvestedTempMineral())
                            .currentHealth(normalizedHealth)
                            .build();
                })
                .collect(Collectors.toList());
    }

    private ShipInfoDto convertShipToShipInfoDto(Ship ship) {
        List<ShipModule> modules = shipModuleRepository.findByShipIdAndDeletedFalseOrderBySlotIndex(ship.getId());
        List<ShipModuleLevel> allLevels = shipModuleLevelRepository.findAllByShipId(ship.getId());
        List<ModuleBodyInfoDto> bodyDtos = convertToBodyModules(modules, allLevels);

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
    public AddShipResponse addShip(Long characterId, AddShipRequest request) {
        // 캐릭터 조회 (비관적 락)
        com.bk.sbs.entity.Character character = characterRepository.findByIdForUpdate(characterId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.ADD_SHIP_FAIL_SHIP_NOT_FOUND));

        // 대상 함대 결정 (요청에 fleetId가 없으면 활성 함대 사용)
        Fleet targetFleet;
        if (request.getFleetId() != null) {
            targetFleet = fleetRepository.findByIdAndCharacterIdAndDeletedFalse(request.getFleetId(), characterId)
                    .orElseThrow(() -> new BusinessException(ServerErrorCode.ADD_SHIP_FAIL_FLEET_NOT_FOUND));
        } else {
            targetFleet = fleetRepository.findByCharacterIdAndIsActiveTrueAndDeletedFalse(characterId)
                    .orElseThrow(() -> new BusinessException(ServerErrorCode.ADD_SHIP_FAIL_ACTIVE_FLEET_NOT_FOUND));
        }

        int maxShipsPerFleet = gameDataService.getMaxShipsPerFleet();

        List<Ship> currentShips = shipRepository.findByFleetIdAndDeletedFalseOrderByPositionIndex(targetFleet.getId());
        if (currentShips.size() >= maxShipsPerFleet) {
            throw new BusinessException(ServerErrorCode.ADD_SHIP_FAIL_FLEET_MAX_SHIPS_REACHED);
        }

        // 현재 함선 수에 따른 추가 비용 가져오기
        int shipAddCost = gameDataService.getShipAddCost();

        // 기술레벨 검증 — 현재 기술레벨에서 허용된 최대 함선 수(ship_count) 초과 여부
        int charTechLevel = getCharacterTechLevel(characterId);
        if (currentShips.size() >= gameDataService.getShipCount(charTechLevel)) {
            throw new BusinessException(ServerErrorCode.ADD_SHIP_FAIL_INSUFFICIENT_TECH_LEVEL);
        }

        // 자원 부족 검사
        if (character.getMineral() < shipAddCost) {
            throw new BusinessException(ServerErrorCode.ADD_SHIP_FAIL_INSUFFICIENT_MINERAL);
        }

        // 자원 차감
        character.setMineral(character.getMineral() - shipAddCost);
        characterRepository.save(character);

        // 새 함선 생성
        Ship newShip = new Ship();
        newShip.setFleet(targetFleet);
        newShip.setShipName("Ship_" + (currentShips.size() + 1));
        newShip.setPositionIndex(currentShips.size());
        newShip.setDeleted(false);
        newShip.setCreated(LocalDateTime.now());
        newShip.setModified(LocalDateTime.now());
        Ship savedShip = shipRepository.save(newShip);

        // 기본 모듈들 생성 (Body, Weapon) — 비용 분배: beam = moduleUnlockPrice, body = 나머지
        int moduleUnlockPrice = gameDataService.getModuleUnlockPrice();
        int bodyInvested = shipAddCost - moduleUnlockPrice;
        createDefaultModules(savedShip, bodyInvested, moduleUnlockPrice);

        // 비용 정보 (모든 미네랄 타입 포함)
        CostRemainInfoDto costRemainInfo = buildCostRemainInfo(shipAddCost, 0, 0, character);

        // 응답 생성
        AddShipResponse response = AddShipResponse.builder()
                .newShipInfo(convertShipToShipInfoDto(savedShip))
                .costRemainInfo(costRemainInfo)
                .updatedFleetInfo(convertToDetailDto(targetFleet))
                .build();

        return response;
    }

    private void createDefaultModules(Ship ship, int bodyInvestedMineral, int beamInvestedMineral) {
        ModuleData bodyData = gameDataService.getFirstModuleByType(EModuleType.body);
        // Body 모듈
        ShipModule bodyModule = new ShipModule();
        bodyModule.setShip(ship);
        bodyModule.setModuleType(EModuleType.body);
        bodyModule.setModuleSubType(EModuleSubType.body_t1_m1);
        bodyModule.setModuleLevel(1);
        bodyModule.setBodyIndex(0);
        bodyModule.setSlotIndex(0);
        bodyModule.setInvestedMineral(bodyInvestedMineral);
        bodyModule.setCurrentHealth(bodyData != null && bodyData.getHealth() != null ? bodyData.getHealth() : 0f);
        bodyModule.setDeleted(false);
        bodyModule.setCreated(LocalDateTime.now());
        bodyModule.setModified(LocalDateTime.now());
        shipModuleRepository.save(bodyModule);

        // Beam 모듈
        ShipModule weaponModule = new ShipModule();
        weaponModule.setShip(ship);
        weaponModule.setModuleType(EModuleType.beam);
        weaponModule.setModuleSubType(EModuleSubType.beam_t1_m1);
        weaponModule.setModuleLevel(1);
        weaponModule.setBodyIndex(0);
        weaponModule.setSlotIndex(0);
        weaponModule.setInvestedMineral(beamInvestedMineral);
        weaponModule.setDeleted(false);
        weaponModule.setCreated(LocalDateTime.now());
        weaponModule.setModified(LocalDateTime.now());
        shipModuleRepository.save(weaponModule);
    }

    @Transactional
    public ModuleLevelUpResponse levelUpModule(Long characterId, ModuleLevelUpRequest request) {
        // 함선 소유권 확인
        Ship ship = shipRepository.findById(request.getShipId())
                .orElseThrow(() -> new BusinessException(ServerErrorCode.UPGRADE_MODULE_FAIL_SHIP_NOT_FOUND));

        if (!ship.getFleet().getCharacterId().equals(characterId)) {
            throw new BusinessException(ServerErrorCode.UPGRADE_MODULE_FAIL_FLEET_ACCESS_DENIED);
        }

        EModuleType moduleType = request.getModuleType();
        EModuleSubType moduleSubType = request.getModuleSubType();

        // 모듈 찾기
        ShipModule module = shipModuleRepository.findByShipIdAndBodyIndexAndModuleTypeAndSlotIndexAndDeletedFalse(
                request.getShipId(),
                request.getBodyIndex(),
                moduleType,
                request.getSlotIndex()
        ).orElseThrow(() -> new BusinessException(ServerErrorCode.UPGRADE_MODULE_FAIL_MODULE_NOT_FOUND));

        // 현재 레벨 확인
        if (module.getModuleLevel() != request.getCurrentLevel()) {
            throw new BusinessException(ServerErrorCode.UPGRADE_MODULE_FAIL_MODULE_LEVEL_MISMATCH);
        }

        // 캐릭터 자원 조회 (비관적 락)
        com.bk.sbs.entity.Character character = characterRepository.findByIdForUpdate(characterId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.UPGRADE_MODULE_FAIL_CHARACTER_NOT_FOUND));

        // 기술레벨 검증 — 서브타입 인코딩에서 파싱: (value/100)%100
        int requiredTechTier = (moduleSubType.getValue() / 100) % 100;
        if (getCharacterTechLevel(characterId) < requiredTechTier) {
            throw new BusinessException(ServerErrorCode.UPGRADE_MODULE_FAIL_INSUFFICIENT_TECH_LEVEL);
        }

        // 업그레이드 비용 계산 (현재 레벨부터 목표 레벨까지)
        int totalCost = 0;

        List<ModuleData> moduleDataList = gameDataService.getModulesByType(moduleType);
        for (int level = request.getCurrentLevel(); level < request.getTargetLevel(); level++) {
            final int currentLevel = level;
            ModuleData levelData = moduleDataList.stream()
                    .filter(data -> data.getModuleLevel() == currentLevel)
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(ServerErrorCode.UPGRADE_MODULE_FAIL_MODULE_DATA_NOT_FOUND));

            totalCost = totalCost + levelData.getMineralCost();
        }

        // 자원 부족 검사 (업그레이드 진행 전에 먼저 체크)
        if (character.getMineral() < totalCost) {
            throw new BusinessException(ServerErrorCode.UPGRADE_MODULE_FAIL_INSUFFICIENT_MINERAL);
        }

        // 자원 차감 (소비 우선순위: M → PM → TM)
        int[] deducted = deductMinerals(character, totalCost);
        characterRepository.save(character);

        // 모듈 레벨 업데이트 (능력치는 클라이언트가 DataTable에서 조회)
        module.setModuleLevel(request.getTargetLevel());
        module.setInvestedMineral(module.getInvestedMineral() + deducted[0]);
        module.setInvestedPvpMineral(module.getInvestedPvpMineral() + deducted[1]);
        module.setInvestedTempMineral(module.getInvestedTempMineral() + deducted[2]);
        module.setModified(LocalDateTime.now());
        shipModuleRepository.save(module);

        // ShipModuleLevel에도 레벨 저장
        ShipModuleLevel levelRecord = shipModuleLevelRepository.findByShipIdAndBodyIndexAndModuleTypeAndSlotIndexAndModuleSubType(
                request.getShipId(),
                request.getBodyIndex(),
                moduleType,
                request.getSlotIndex(),
                moduleSubType
        ).orElseGet(() -> {
            ShipModuleLevel newRecord = new ShipModuleLevel();
            newRecord.setShip(ship);
            newRecord.setBodyIndex(request.getBodyIndex());
            newRecord.setModuleType(moduleType);
            newRecord.setSlotIndex(request.getSlotIndex());
            newRecord.setModuleSubType(moduleSubType);
            return newRecord;
        });
        levelRecord.setLevel(request.getTargetLevel());
        levelRecord.setModified(LocalDateTime.now());
        shipModuleLevelRepository.save(levelRecord);

        // 비용 정보
        CostRemainInfoDto costRemainInfo = buildCostRemainInfo(deducted[0], deducted[1], deducted[2], character);

        // 응답 생성
        ModuleLevelUpResponse response = ModuleLevelUpResponse.builder()
                .shipId(request.getShipId())
                .bodyIndex(request.getBodyIndex())
                .moduleType(moduleType)
                .moduleSubType(moduleSubType)
                .slotIndex(module.getSlotIndex())
                .newLevel(module.getModuleLevel())
                .costRemainInfo(costRemainInfo)
                .build();

        return response;
    }

    @Transactional
    public ChangeFormationResponse changeFormation(Long characterId, ChangeFormationRequest request) {
        Fleet fleet;

        // fleetId가 null이거나 0이면 활성 함대 사용
        if (request.getFleetId() == null || request.getFleetId() == 0) {
            fleet = fleetRepository.findByCharacterIdAndIsActiveTrueAndDeletedFalse(characterId)
                    .orElseThrow(() -> new BusinessException(ServerErrorCode.FLEET_NOT_FOUND));
        } else {
            fleet = fleetRepository.findByIdAndCharacterIdAndDeletedFalse(request.getFleetId(), characterId)
                    .orElseThrow(() -> new BusinessException(ServerErrorCode.FLEET_NOT_FOUND));
        }

        EFormationType formationType = request.getFormationType();

        // 편대 정보 업데이트
        fleet.setFormation(formationType);
        fleet.setModified(LocalDateTime.now());
        fleetRepository.save(fleet);

        // 업데이트된 함대 정보 반환
        FleetInfoDto updatedFleet = convertToDetailDto(fleet);
        return ChangeFormationResponse.builder()
                .updatedFleetInfo(updatedFleet)
                .build();
    }

    @Transactional
    public ModuleUnlockResponse unlockModule(Long characterId, ModuleUnlockRequest request) {
        // 함선 소유권 확인
        Ship ship = shipRepository.findById(request.getShipId())
                .orElseThrow(() -> new BusinessException(ServerErrorCode.UNLOCK_MODULE_FAIL_SHIP_NOT_FOUND));

        if (!ship.getFleet().getCharacterId().equals(characterId)) {
            throw new BusinessException(ServerErrorCode.UNLOCK_MODULE_FAIL_FLEET_ACCESS_DENIED);
        }

        // 요청에서 모듈 타입 정보 추출
        EModuleType moduleType = request.getModuleType();

        // 현재 슬롯 확인
        Optional<ShipModule> existingModule = shipModuleRepository.findByShipIdAndBodyIndexAndModuleTypeAndSlotIndexAndDeletedFalse(
                request.getShipId(),
                request.getBodyIndex(),
                moduleType,
                request.getSlotIndex()
        );

        // 이미 모듈이 존재하면 Placeholder가 아님
        if (existingModule.isPresent()) {
            throw new BusinessException(ServerErrorCode.UNLOCK_MODULE_FAIL_ALREADY_UNLOCKED); // 이미 해금된 모듈
        }

        // 캐릭터 자원 조회 (비관적 락)
        com.bk.sbs.entity.Character character = characterRepository.findByIdForUpdate(characterId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.UNLOCK_MODULE_FAIL_CHARACTER_NOT_FOUND));

        // 모듈 해금 비용
        int mineralCost = gameDataService.getDataTableConfig().getModuleUnlockPrice();


        // 자원 부족 검사
        if (character.getMineral() < mineralCost) {
            throw new BusinessException(ServerErrorCode.UNLOCK_MODULE_FAIL_INSUFFICIENT_MINERAL);
        }

        // 자원 차감 (소비 우선순위: M → PM → TM)
        int[] unlockDeducted = deductMinerals(character, mineralCost);
        characterRepository.save(character);

        // 1. 현재 함선의 Body 모듈 찾기
        ShipModule bodyModule = shipModuleRepository.findByShipIdAndBodyIndexAndModuleTypeAndSlotIndexAndDeletedFalse(
                request.getShipId(),
                request.getBodyIndex(),
                EModuleType.body,
                0 // Body는 항상 slotIndex 0
        ).orElseThrow(() -> new BusinessException(ServerErrorCode.UNLOCK_MODULE_FAIL_BODY_MODULE_NOT_FOUND));

        // 2. Body 모듈의 데이터 가져오기
        List<ModuleData> bodyModuleDataList = gameDataService.getModulesByType(EModuleType.body);
        ModuleData bodyData = bodyModuleDataList.stream()
                .filter(data -> data.getModuleLevel() == bodyModule.getModuleLevel() &&
                        data.getModuleSubType() == bodyModule.getModuleSubType())
                .findFirst()
                .orElseThrow(() -> new BusinessException(ServerErrorCode.UNLOCK_MODULE_FAIL_BODY_DATA_NOT_FOUND));

        // 3. 요청된 슬롯 인덱스의 유효성 검사 및 슬롯 정보 확인
        ModuleSlotInfoDto slotInfo = bodyData.getModuleSlots().stream()
                .filter(s -> s.getModuleType() == moduleType && s.getSlotIndex().equals(request.getSlotIndex()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ServerErrorCode.UNLOCK_MODULE_FAIL_SLOT_INDEX_OUT_OF_BOUNDS));

        // 4. 모듈 타입 검증
        if (slotInfo.getModuleType() != moduleType) {
            throw new BusinessException(ServerErrorCode.UNLOCK_MODULE_FAIL_INVALID_MODULE_TYPE);
        }

        // 기본 subType 결정
        int defaultSubTypeValue = moduleType.getValue() * 10000 + 101; // 7자리 인코딩: t1(01) m1(01)
        EModuleSubType finalModuleSubType = EModuleSubType.fromValue(defaultSubTypeValue);


        // 새로운 모듈 레코드 생성
        ShipModule newModule = new ShipModule();
        newModule.setShip(ship);
        newModule.setBodyIndex(request.getBodyIndex());
        newModule.setSlotIndex(request.getSlotIndex());
        newModule.setModuleType(moduleType);
        newModule.setModuleSubType(finalModuleSubType);
        newModule.setModuleLevel(1);
        newModule.setInvestedMineral(unlockDeducted[0]);
        newModule.setInvestedPvpMineral(unlockDeducted[1]);
        newModule.setInvestedTempMineral(unlockDeducted[2]);
        newModule.setDeleted(false);
        newModule.setCreated(LocalDateTime.now());
        newModule.setModified(LocalDateTime.now());
        shipModuleRepository.save(newModule);
        saveInitialModuleLevel(ship, moduleType, finalModuleSubType, 1, request.getBodyIndex(), request.getSlotIndex());

        // 비용 정보
        CostRemainInfoDto costRemainInfo = buildCostRemainInfo(unlockDeducted[0], unlockDeducted[1], unlockDeducted[2], character);

        // 응답 생성
        return new ModuleUnlockResponse(
                request.getShipId(),
                request.getBodyIndex(),
                moduleType,
                finalModuleSubType,
                request.getSlotIndex(),
                costRemainInfo,
                unlockDeducted[0],
                unlockDeducted[1],
                unlockDeducted[2]
        );
    }

    @Transactional
    public ModuleChangeResponse changeModule(Long characterId, ModuleChangeRequest request) {
        // 함선 소유권 확인
        Ship ship = shipRepository.findById(request.getShipId())
                .orElseThrow(() -> new BusinessException(ServerErrorCode.CHANGE_MODULE_FAIL_SHIP_NOT_FOUND));

        if (!ship.getFleet().getCharacterId().equals(characterId)) {
            throw new BusinessException(ServerErrorCode.CHANGE_MODULE_FAIL_FLEET_ACCESS_DENIED);
        }

        // 현재 모듈 타입 정보 추출
        EModuleType currentModuleType = request.getModuleType();
        EModuleSubType currentModuleSubType = request.getModuleSubTypeCurrent();

        // 새 모듈 타입 정보 추출
        EModuleType newModuleType = request.getModuleType();
        EModuleSubType newModuleSubType = request.getModuleSubTypeNew();

        // 1. 같은 모듈인지 확인 (완전히 동일한 모듈로 변경 불가)
        if (currentModuleType == newModuleType &&  currentModuleSubType == newModuleSubType) {
            throw new BusinessException(ServerErrorCode.CHANGE_MODULE_FAIL_SAME_MODULE);
        }

        // 2. 모듈 타입이 다르면 에러
        if (currentModuleType != newModuleType) {
            throw new BusinessException(ServerErrorCode.CHANGE_MODULE_FAIL_NOT_MATCH_MODULE_TYPE);
        }

        // 현재 장착된 모듈 찾기
        ShipModule currentModule = shipModuleRepository.findByShipIdAndBodyIndexAndModuleTypeAndSlotIndexAndDeletedFalse(
                request.getShipId(),
                request.getBodyIndex(),
                currentModuleType,
                request.getSlotIndex()
        ).orElseThrow(() -> new BusinessException(ServerErrorCode.CHANGE_MODULE_FAIL_MODULE_NOT_FOUND));

        // 기술레벨 검증 — 서브타입 인코딩에서 파싱: (value/10000)%100
        int requiredTechTier = (newModuleSubType.getValue() / 100) % 100;
        if (getCharacterTechLevel(characterId) < requiredTechTier) {
            throw new BusinessException(ServerErrorCode.CHANGE_MODULE_FAIL_INSUFFICIENT_TECH_LEVEL);
        }

        // 3. 슬롯 단위 추가 이력 확인 — ShipModuleLevel 레코드 존재 = 이미 추가됨(무료)
        Optional<ShipModuleLevel> newModuleLevelRecord = shipModuleLevelRepository.findByShipIdAndBodyIndexAndModuleTypeAndSlotIndexAndModuleSubType(
                request.getShipId(),
                request.getBodyIndex(),
                newModuleType,
                request.getSlotIndex(),
                newModuleSubType
        );
        boolean alreadyAdded = newModuleLevelRecord.isPresent();

        // 4. 신규 잠금해제 시에만 max level + 직접 다음 단계 요구 (이미 보유한 서브타입은 레벨 무관하게 교체 가능)
        if (!alreadyAdded) {
            if (!gameDataService.isDirectNextStep(currentModuleSubType, newModuleSubType)) {
                throw new BusinessException(ServerErrorCode.CHANGE_MODULE_FAIL_NOT_DIRECT_NEXT_STEP);
            }
            int maxLevel = gameDataService.getMaxModuleLevel(currentModuleType, currentModuleSubType);
            if (currentModule.getModuleLevel() < maxLevel) {
                throw new BusinessException(ServerErrorCode.CHANGE_MODULE_FAIL_CURRENT_MODULE_NOT_MAX_LEVEL);
            }
        }

        // 5. 최초 추가 시에만 비용 차감 (비관적 락)
        com.bk.sbs.entity.Character character = characterRepository.findByIdForUpdate(characterId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.CHANGE_MODULE_FAIL_SHIP_NOT_FOUND));

        int addCost = gameDataService.getModuleResearchCost(newModuleSubType);
        int[] changeDeducted = new int[]{0, 0, 0};
        if (!alreadyAdded) {
            int totalMineral = character.getMineral() + character.getPvpMineral() + character.getTempMineral();
            if (totalMineral < addCost) {
                throw new BusinessException(ServerErrorCode.CHANGE_MODULE_FAIL_INSUFFICIENT_MINERAL_RARE);
            }
            changeDeducted = deductMinerals(character, addCost);
            characterRepository.save(character);
        }

        // 1. 현재 모듈의 레벨을 ShipModuleLevel에 저장
        ShipModuleLevel currentLevelRecord = shipModuleLevelRepository.findByShipIdAndBodyIndexAndModuleTypeAndSlotIndexAndModuleSubType(
                request.getShipId(),
                request.getBodyIndex(),
                currentModuleType,
                request.getSlotIndex(),
                currentModuleSubType
        ).orElseGet(() -> {
            ShipModuleLevel newRecord = new ShipModuleLevel();
            newRecord.setShip(ship);
            newRecord.setBodyIndex(request.getBodyIndex());
            newRecord.setModuleType(currentModuleType);
            newRecord.setSlotIndex(request.getSlotIndex());
            newRecord.setModuleSubType(currentModuleSubType);
            return newRecord;
        });
        currentLevelRecord.setLevel(currentModule.getModuleLevel());
        currentLevelRecord.setModified(LocalDateTime.now());
        shipModuleLevelRepository.save(currentLevelRecord);

        // 2. 새 모듈의 레벨을 ShipModuleLevel에서 조회 (없으면 1)
        int newModuleLevel = newModuleLevelRecord.map(ShipModuleLevel::getLevel).orElse(1);

        // 3. 모듈 정보 업데이트 (서브타입 + 레벨 변경, 투자 이력은 교체 시 새 슬롯 기준으로 누적)
        currentModule.setModuleSubType(newModuleSubType);
        currentModule.setModuleLevel(newModuleLevel);
        if (!alreadyAdded) {
            currentModule.setInvestedMineral(currentModule.getInvestedMineral() + changeDeducted[0]);
            currentModule.setInvestedPvpMineral(currentModule.getInvestedPvpMineral() + changeDeducted[1]);
            currentModule.setInvestedTempMineral(currentModule.getInvestedTempMineral() + changeDeducted[2]);
        }
        currentModule.setModified(LocalDateTime.now());
        shipModuleRepository.save(currentModule);

        // 응답 생성 (actualCost: 최초 추가 시 실차감액, 재추가 시 0)
        int actualCost = alreadyAdded ? 0 : addCost;
        CostRemainInfoDto costRemainInfo = buildCostRemainInfo(changeDeducted[0], changeDeducted[1], changeDeducted[2], character);

        // 해당 슬롯에 이력이 있는 모든 서브타입 = 비용 없이 교체 가능한 목록
        List<EModuleSubType> unlockedSubTypes = shipModuleLevelRepository
                .findAllByShipIdAndBodyIndexAndModuleTypeAndSlotIndex(
                        request.getShipId(), request.getBodyIndex(), newModuleType, request.getSlotIndex())
                .stream()
                .map(ShipModuleLevel::getModuleSubType)
                .collect(java.util.stream.Collectors.toList());

        return ModuleChangeResponse.builder()
                .shipId(request.getShipId())
                .bodyIndex(request.getBodyIndex())
                .moduleTypeCurrent(currentModuleType)
                .moduleSubTypeCurrent(currentModuleSubType)
                .moduleTypeNew(newModuleType)
                .moduleSubTypeNew(newModuleSubType)
                .slotIndex(request.getSlotIndex())
                .moduleNewLevel(newModuleLevel)
                .costRemainInfo(costRemainInfo)
                .newUnlockedSubTypes(unlockedSubTypes)
                .build();
    }

    // tech_level_N 문자열 기반 연구 처리: 비용 차감 후 DB 저장, researchedIds 반환
    @Transactional
    public TechLevelResearchResponse researchTechLevel(Long characterId, TechLevelResearchRequest request) {
        String researchId = request.getResearchId();
        // 이미 연구 완료 체크
        ModuleResearch existing = moduleResearchRepository.findByCharacterIdAndResearchId(characterId, researchId).orElse(null);
        if (existing != null && existing.isResearched()) {
            throw new BusinessException(ServerErrorCode.RESEARCH_MODULE_FAIL_ALREADY_RESEARCHED);
        }

        // 캐릭터 자원 조회 (비관적 락)
        com.bk.sbs.entity.Character character = characterRepository.findByIdForUpdate(characterId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.RESEARCH_MODULE_FAIL_CHARACTER_NOT_FOUND));

        // 비용 조회 및 선행 기술레벨 검증 (순차 업그레이드만 허용)
        int researchCost = gameDataService.getTechLevelResearchCost(researchId);
        int targetLevel = Integer.parseInt(researchId.substring("tech_level_".length()));
        if (getCharacterTechLevel(characterId) < targetLevel - 1) {
            throw new BusinessException(ServerErrorCode.RESEARCH_MODULE_FAIL_INSUFFICIENT_TECH_LEVEL);
        }

        // 자원 부족 검사
        int totalResearchMineral = character.getMineral() + character.getPvpMineral() + character.getTempMineral();
        if (totalResearchMineral < researchCost)
            throw new BusinessException(ServerErrorCode.RESEARCH_MODULE_FAIL_INSUFFICIENT_MINERAL);

        // 자원 차감 (소비 우선순위: M → PM → TM)
        int[] researchDeducted = deductMinerals(character, researchCost);
        characterRepository.save(character);

        // 기술레벨 연구 저장
        ModuleResearch research = existing != null ? existing : new ModuleResearch();
        research.setCharacterId(characterId);
        research.setResearchId(researchId);
        research.setResearched(true);
        research.setModified(LocalDateTime.now());
        moduleResearchRepository.save(research);

        // 완료된 researchedIds 반환
        List<String> researchedIds = getResearchedIds(characterId);
        CostRemainInfoDto costRemainInfo = buildCostRemainInfo(researchDeducted[0], researchDeducted[1], researchDeducted[2], character);
        return new TechLevelResearchResponse(costRemainInfo, researchedIds);
    }

    // 캐릭터가 개발한 모든 모듈 목록 조회 (moduleType+subType 쌍)
    public List<List<Integer>> getResearchedModuleTypes(Long characterId) {
        List<ModuleResearch> researchedList = moduleResearchRepository.findByCharacterIdAndResearchedTrue(characterId);
        return researchedList.stream()
                .filter(r -> r.getModuleType() != null && r.getModuleSubType() != null)
                .map(r -> List.of(r.getModuleType().getValue(), r.getModuleSubType().getValue()))
                .collect(Collectors.toList());
    }

    // 문자열 기반 완료 연구 ID 목록 조회 (tech_level_N 등)
    public List<String> getResearchedIds(Long characterId) {
        return moduleResearchRepository.findByCharacterIdAndResearchIdIsNotNullAndResearchedTrue(characterId)
                .stream()
                .map(ModuleResearch::getResearchId)
                .collect(Collectors.toList());
    }

    // module_research에서 기술레벨 파생 (tech_level_N 중 최댓값, 기본값 1)
    private int getCharacterTechLevel(Long characterId) {
        return moduleResearchRepository
                .findByCharacterIdAndResearchIdStartingWithAndResearchedTrue(characterId, "tech_level_")
                .stream()
                .map(r -> r.getResearchId().substring("tech_level_".length()))
                .mapToInt(s -> { try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 0; } })
                .max()
                .orElse(1);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 모듈 리셋 (레벨 1 + unlockedSubTypes 초기화 + 투자분 100% 환급)
    // ─────────────────────────────────────────────────────────────────────────
    @Transactional
    public ModuleResetResponse resetModule(Long characterId, ModuleResetRequest request) {
        Ship ship = shipRepository.findById(request.getShipId())
                .orElseThrow(() -> new BusinessException(ServerErrorCode.RESET_MODULE_FAIL_SHIP_NOT_FOUND));

        if (!ship.getFleet().getCharacterId().equals(characterId))
            throw new BusinessException(ServerErrorCode.RESET_MODULE_FAIL_FLEET_ACCESS_DENIED);

        if (request.getModuleType() == EModuleType.body) {
            // 기함(positionIndex==0) body 리셋만 허용 — T1 레벨1로 되돌리기
            if (ship.getPositionIndex() != 0)
                throw new BusinessException(ServerErrorCode.RESET_MODULE_FAIL_BODY_FORBIDDEN);
            return resetFlagshipBody(ship, characterId, request);
        }

        ShipModule module = shipModuleRepository.findByShipIdAndBodyIndexAndModuleTypeAndSlotIndexAndDeletedFalse(
                request.getShipId(), request.getBodyIndex(), request.getModuleType(), request.getSlotIndex()
        ).orElseThrow(() -> new BusinessException(ServerErrorCode.RESET_MODULE_FAIL_MODULE_NOT_FOUND));

        com.bk.sbs.entity.Character character = characterRepository.findByIdForUpdate(characterId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.RESET_MODULE_FAIL_CHARACTER_NOT_FOUND));

        // 투자 이력 환급
        character.setMineral(character.getMineral() + module.getInvestedMineral());
        character.setPvpMineral(character.getPvpMineral() + module.getInvestedPvpMineral());
        character.setTempMineral(character.getTempMineral() + module.getInvestedTempMineral());
        characterRepository.save(character);

        // 모듈 soft-delete (언락된 슬롯을 플레이스홀더 상태로 복귀)
        module.setDeleted(true);
        module.setModified(LocalDateTime.now());
        shipModuleRepository.save(module);

        // ShipModuleLevel 이력 전체 삭제 (모듈 자체가 soft-delete되므로 초기 레코드도 불필요)
        shipModuleLevelRepository.deleteBySlot(
                request.getShipId(), request.getBodyIndex(), request.getModuleType(), request.getSlotIndex());

        CostRemainInfoDto costRemainInfo = CostRemainInfoDto.builder()
                .mineralCost(0)
                .mineralRemain(character.getMineral())
                .pvpMineralCost(0)
                .pvpMineralRemain(character.getPvpMineral())
                .tempMineralCost(0)
                .tempMineralRemain(character.getTempMineral())
                .build();

        return ModuleResetResponse.builder()
                .shipId(request.getShipId())
                .bodyIndex(request.getBodyIndex())
                .moduleType(request.getModuleType())
                .slotIndex(request.getSlotIndex())
                .costRemainInfo(costRemainInfo)
                .build();
    }

    // 기함 body 리셋 — T1 레벨1로 되돌리고 투자 이력 환급 (soft-delete 없이 업데이트)
    private ModuleResetResponse resetFlagshipBody(Ship ship, Long characterId, ModuleResetRequest request) {
        ShipModule body = shipModuleRepository.findByShipIdAndBodyIndexAndModuleTypeAndSlotIndexAndDeletedFalse(
                ship.getId(), request.getBodyIndex(), EModuleType.body, 0
        ).orElseThrow(() -> new BusinessException(ServerErrorCode.RESET_MODULE_FAIL_MODULE_NOT_FOUND));

        com.bk.sbs.entity.Character character = characterRepository.findByIdForUpdate(characterId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.RESET_MODULE_FAIL_CHARACTER_NOT_FOUND));

        // 투자 이력 환급
        character.setMineral(character.getMineral() + body.getInvestedMineral());
        character.setPvpMineral(character.getPvpMineral() + body.getInvestedPvpMineral());
        character.setTempMineral(character.getTempMineral() + body.getInvestedTempMineral());
        characterRepository.save(character);

        // T1 레벨1로 복귀 (삭제 없이 값만 초기화)
        body.setModuleSubType(EModuleSubType.body_t1_m1);
        body.setModuleLevel(1);
        body.setInvestedMineral(0);
        body.setInvestedPvpMineral(0);
        body.setInvestedTempMineral(0);
        body.setModified(LocalDateTime.now());
        shipModuleRepository.save(body);

        // 레벨업 이력 전체 삭제
        shipModuleLevelRepository.deleteBySlot(ship.getId(), request.getBodyIndex(), EModuleType.body, 0);

        CostRemainInfoDto costRemainInfo = CostRemainInfoDto.builder()
                .mineralCost(0).mineralRemain(character.getMineral())
                .pvpMineralCost(0).pvpMineralRemain(character.getPvpMineral())
                .tempMineralCost(0).tempMineralRemain(character.getTempMineral())
                .build();

        return ModuleResetResponse.builder()
                .shipId(ship.getId())
                .bodyIndex(request.getBodyIndex())
                .moduleType(EModuleType.body)
                .slotIndex(0)
                .costRemainInfo(costRemainInfo)
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 함선 리셋 + 삭제 (전 슬롯 resetModule 처리 후 함선 soft delete)
    // 기함(positionIndex == 0) 불가
    // ─────────────────────────────────────────────────────────────────────────
    @Transactional
    public ShipResetRemoveResponse resetAndRemoveShip(Long characterId, ShipResetRemoveRequest request) {
        Ship ship = shipRepository.findById(request.getShipId())
                .orElseThrow(() -> new BusinessException(ServerErrorCode.RESET_SHIP_FAIL_SHIP_NOT_FOUND));

        if (!ship.getFleet().getCharacterId().equals(characterId))
            throw new BusinessException(ServerErrorCode.RESET_SHIP_FAIL_FLEET_ACCESS_DENIED);

        if (ship.getPositionIndex() == 0)
            throw new BusinessException(ServerErrorCode.RESET_SHIP_FAIL_FLAGSHIP_FORBIDDEN);

        com.bk.sbs.entity.Character character = characterRepository.findByIdForUpdate(characterId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.RESET_SHIP_FAIL_CHARACTER_NOT_FOUND));

        // 전 모듈 투자 이력 합산 후 환급
        List<ShipModule> allModules = shipModuleRepository.findByShipIdAndDeletedFalseOrderBySlotIndex(request.getShipId());
        int refundM = 0, refundPm = 0, refundTm = 0;
        for (ShipModule mod : allModules) {
            refundM  += mod.getInvestedMineral();
            refundPm += mod.getInvestedPvpMineral();
            refundTm += mod.getInvestedTempMineral();
        }
        character.setMineral(character.getMineral() + refundM);
        character.setPvpMineral(character.getPvpMineral() + refundPm);
        character.setTempMineral(character.getTempMineral() + refundTm);
        characterRepository.save(character);

        // 모듈 + ShipModuleLevel soft delete
        for (ShipModule mod : allModules) {
            mod.setDeleted(true);
            mod.setModified(LocalDateTime.now());
            shipModuleRepository.save(mod);
        }
        shipModuleLevelRepository.deleteByShipId(request.getShipId());

        // 함선 soft delete
        ship.setDeleted(true);
        ship.setModified(LocalDateTime.now());
        shipRepository.save(ship);

        // 남은 함선 positionIndex 재정렬
        Fleet fleet = ship.getFleet();
        List<Ship> remaining = shipRepository.findByFleetIdAndDeletedFalseOrderByPositionIndex(fleet.getId());
        for (int i = 0; i < remaining.size(); i++) {
            Ship s = remaining.get(i);
            s.setPositionIndex(i);
            s.setModified(LocalDateTime.now());
            shipRepository.save(s);
        }

        Fleet updatedFleet = fleetRepository.findByIdAndCharacterIdAndDeletedFalse(fleet.getId(), characterId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.RESET_SHIP_FAIL_FLEET_NOT_FOUND));

        CostRemainInfoDto costRemainInfo = CostRemainInfoDto.builder()
                .mineralCost(0)
                .mineralRemain(character.getMineral())
                .pvpMineralCost(0)
                .pvpMineralRemain(character.getPvpMineral())
                .tempMineralCost(0)
                .tempMineralRemain(character.getTempMineral())
                .build();

        return ShipResetRemoveResponse.builder()
                .removedShipId(request.getShipId())
                .costRemainInfo(costRemainInfo)
                .updatedFleetInfo(convertToDetailDto(updatedFleet))
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
                ModuleData bodyData = findModuleData(EModuleType.body, body.getModuleSubType(), body.getModuleLevel());
                if (bodyData != null) statHealth += bodyData.getHealth() != null ? bodyData.getHealth() : 0f;

                if (body.getBeams() != null) {
                    for (ModuleInfoDto beam : body.getBeams()) {
                        ModuleData data = findModuleData(EModuleType.beam, beam.getModuleSubType(), beam.getModuleLevel());
                        if (data != null) {
                            float atk = data.getAttack() != null ? data.getAttack() : 0f;
                            int cnt = data.getAttackFireCount() != null ? data.getAttackFireCount() : 1;
                            statAttack += atk * cnt;
                        }
                    }
                }
                if (body.getMissiles() != null) {
                    for (ModuleInfoDto missile : body.getMissiles()) {
                        ModuleData data = findModuleData(EModuleType.missile, missile.getModuleSubType(), missile.getModuleLevel());
                        if (data != null) {
                            float atk = data.getAttack() != null ? data.getAttack() : 0f;
                            int cnt = data.getAttackFireCount() != null ? data.getAttackFireCount() : 1;
                            statAttack += atk * cnt;
                        }
                    }
                }
                if (body.getHangers() != null) {
                    for (ModuleInfoDto hanger : body.getHangers()) {
                        ModuleData data = findModuleData(EModuleType.hanger, hanger.getModuleSubType(), hanger.getModuleLevel());
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

    private ModuleData findModuleData(EModuleType type, EModuleSubType subType, Integer level) {
        if (subType == null || level == null) return null;
        List<ModuleData> list = gameDataService.getModulesByType(type);
        for (ModuleData data : list) {
            if (subType.equals(data.getModuleSubType()) && level.equals(data.getModuleLevel())) return data;
        }
        return null;
    }

    // 소비 우선순위: M → PM → TM. 반환값: [차감M, 차감PM, 차감TM]
    private int[] deductMinerals(com.bk.sbs.entity.Character character, int cost) {
        int[] deducted = new int[]{0, 0, 0};
        int remaining = cost;

        int fromM = Math.min(remaining, character.getMineral());
        character.setMineral(character.getMineral() - fromM);
        deducted[0] = fromM;
        remaining -= fromM;

        if (remaining > 0) {
            int fromPm = Math.min(remaining, character.getPvpMineral());
            character.setPvpMineral(character.getPvpMineral() - fromPm);
            deducted[1] = fromPm;
            remaining -= fromPm;
        }

        if (remaining > 0) {
            int fromTm = Math.min(remaining, character.getTempMineral());
            character.setTempMineral(character.getTempMineral() - fromTm);
            deducted[2] = fromTm;
        }

        return deducted;
    }

    // CostRemainInfoDto 빌더 헬퍼 (3종 차감량 + 잔액)
    private CostRemainInfoDto buildCostRemainInfo(int costM, int costPm, int costTm, com.bk.sbs.entity.Character character) {
        return CostRemainInfoDto.builder()
                .mineralCost(costM)
                .mineralRemain(character.getMineral())
                .pvpMineralCost(costPm)
                .pvpMineralRemain(character.getPvpMineral())
                .tempMineralCost(costTm)
                .tempMineralRemain(character.getTempMineral())
                .build();
    }

    @Transactional
    public void saveFleetHealth(Long characterId, FleetHealthSaveRequest request) {
        if (request.getShips() == null) return;

        for (ShipHealthInfoDto shipHealth : request.getShips()) {
            Ship ship = shipRepository.findById(shipHealth.getShipId()).orElse(null);
            if (ship == null || !ship.getFleet().getCharacterId().equals(characterId)) continue;
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
