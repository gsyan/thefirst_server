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

    public FleetService(FleetRepository fleetRepository, ShipRepository shipRepository,
                       ShipModuleRepository shipModuleRepository,
                       CommanderRepository commanderRepository,
                       GameDataService gameDataService) {
        this.fleetRepository = fleetRepository;
        this.shipRepository = shipRepository;
        this.shipModuleRepository = shipModuleRepository;
        this.commanderRepository = commanderRepository;
        this.gameDataService = gameDataService;
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
        bodyModule.setModuleSubType(EModuleSubType.body_t1_m1);
        bodyModule.setModuleLevel(bodyData.getModuleLevel());
        bodyModule.setBodyIndex(0);
        bodyModule.setSlotIndex(0);
        bodyModule.setCurrentHealth(bodyData.getHealth() != null ? bodyData.getHealth() : 0f);
        shipModuleRepository.save(bodyModule);

        // 2. Beam (addShip과 동일하게 moduleUnlockPrice=1 투입)
        ShipModule beamModule = new ShipModule();
        beamModule.setShip(defaultShip);
        beamModule.setModuleType(EModuleType.beam);
        beamModule.setModuleSubType(EModuleSubType.beam_t1_m1);
        beamModule.setModuleLevel(beamData.getModuleLevel());
        beamModule.setBodyIndex(0);
        beamModule.setSlotIndex(0);
        beamModule.setInvestedModulePoint(1);
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
                                    .investedModulePoint(beamModule.getInvestedModulePoint())
                                    .addShipModulePoint(beamModule.getAddShipModulePoint())
                                    .investedMineral(beamModule.getInvestedMineral())
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
                                    .investedModulePoint(missileModule.getInvestedModulePoint())
                                    .addShipModulePoint(missileModule.getAddShipModulePoint())
                                    .investedMineral(missileModule.getInvestedMineral())
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
                                    .investedModulePoint(hangerModule.getInvestedModulePoint())
                                    .addShipModulePoint(hangerModule.getAddShipModulePoint())
                                    .investedMineral(hangerModule.getInvestedMineral())
                                    .build())
                            .collect(Collectors.toList());

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
                            .investedModulePoint(bodyModule.getInvestedModulePoint())
                            .addShipModulePoint(bodyModule.getAddShipModulePoint())
                            .investedMineral(bodyModule.getInvestedMineral())
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

        // 현재 함선 수에 따른 추가 비용 가져오기
        int shipAddCost = gameDataService.getShipAddCost();

        // 커맨더 레벨 검증 — 현재 레벨에서 허용된 최대 함선 수(ship_count) 초과 여부
        int charCommanderLevel = commander.getCommanderLevel();
        if (currentShips.size() >= gameDataService.getShipCount(charCommanderLevel)) {
            throw new BusinessException(ServerErrorCode.ADD_SHIP_FAIL_INSUFFICIENT_COMMANDER_LEVEL);
        }

        // 자원 부족 검사
        if (commander.getModulePoint() < shipAddCost) {
            throw new BusinessException(ServerErrorCode.ADD_SHIP_FAIL_INSUFFICIENT_MODULE_POINT);
        }

        // 자원 차감
        int addShipDeducted = deductModulePoint(commander, shipAddCost);
        commanderRepository.save(commander);

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

        // 기본 모듈들 생성 (Body, Weapon) — 비용 분배: beam = moduleUnlockPrice, body = 나머지
        int moduleUnlockPrice = gameDataService.getModuleUnlockPrice();
        int bodyInvested = shipAddCost - moduleUnlockPrice;
        createDefaultModules(savedShip, bodyInvested, moduleUnlockPrice);

        // 응답 생성
        AddShipResponse response = AddShipResponse.builder()
                .newShipInfo(convertShipToShipInfoDto(savedShip))
                .modulePointRemain(commander.getModulePoint())
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
        bodyModule.setInvestedModulePoint(bodyInvestedMineral);
        bodyModule.setAddShipModulePoint(bodyInvestedMineral);
        bodyModule.setCurrentHealth(bodyData != null && bodyData.getHealth() != null ? bodyData.getHealth() : 0f);
        bodyModule.setDeleted(false);
        bodyModule.setCreated(Instant.now());
        bodyModule.setModified(Instant.now());
        shipModuleRepository.save(bodyModule);

        // Beam 모듈
        ShipModule weaponModule = new ShipModule();
        weaponModule.setShip(ship);
        weaponModule.setModuleType(EModuleType.beam);
        weaponModule.setModuleSubType(EModuleSubType.beam_t1_m1);
        weaponModule.setModuleLevel(1);
        weaponModule.setBodyIndex(0);
        weaponModule.setSlotIndex(0);
        weaponModule.setInvestedModulePoint(beamInvestedMineral);
        weaponModule.setAddShipModulePoint(beamInvestedMineral);
        weaponModule.setDeleted(false);
        weaponModule.setCreated(Instant.now());
        weaponModule.setModified(Instant.now());
        shipModuleRepository.save(weaponModule);
    }

    @Transactional
    public ModuleUnlockResponse moduleUnlock(Long commanderId, ModuleUnlockRequest request) {
        Ship ship = shipRepository.findById(request.getShipId())
                .orElseThrow(() -> new BusinessException(ServerErrorCode.MODULE_UNLOCK_FAIL_SHIP_NOT_FOUND));
        if (ship.getFleet().getCommanderId().equals(commanderId) == false) {
            throw new BusinessException(ServerErrorCode.MODULE_UNLOCK_FAIL_FLEET_ACCESS_DENIED);
        }

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
            throw new BusinessException(ServerErrorCode.MODULE_UNLOCK_FAIL_ALREADY_UNLOCKED); // 이미 해금된 모듈
        }

        Commander commander = commanderRepository.findByIdForUpdate(commanderId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.MODULE_UNLOCK_FAIL_COMMANDER_NOT_FOUND));

        // 모듈 해금 비용
        int modulePointCost = gameDataService.getDataTableConfig().getModuleUnlockPrice();

        // 자원 부족 검사
        if (commander.getModulePoint() < modulePointCost) {
            throw new BusinessException(ServerErrorCode.MODULE_UNLOCK_FAIL_INSUFFICIENT_MINERAL);
        }

        // modulePoint 차감
        int unlockDeducted = deductModulePoint(commander, modulePointCost);
        commanderRepository.save(commander);

        // 1. 현재 함선의 Body 모듈 찾기
        ShipModule bodyModule = shipModuleRepository.findByShipIdAndBodyIndexAndModuleTypeAndSlotIndexAndDeletedFalse(
                request.getShipId(),
                request.getBodyIndex(),
                EModuleType.body,
                0 // Body는 항상 slotIndex 0
        ).orElseThrow(() -> new BusinessException(ServerErrorCode.MODULE_UNLOCK_FAIL_BODY_MODULE_NOT_FOUND));

        // 2. Body 모듈의 데이터 가져오기
        List<ModuleData> bodyModuleDataList = gameDataService.getModulesByType(EModuleType.body);
        ModuleData bodyData = bodyModuleDataList.stream()
                .filter(data -> data.getModuleLevel() == bodyModule.getModuleLevel() &&
                        data.getModuleSubType() == bodyModule.getModuleSubType())
                .findFirst()
                .orElseThrow(() -> new BusinessException(ServerErrorCode.MODULE_UNLOCK_FAIL_BODY_DATA_NOT_FOUND));

        // 3. 요청된 슬롯 인덱스의 유효성 검사 및 슬롯 정보 확인
        ModuleSlotInfoDto slotInfo = bodyData.getModuleSlots().stream()
                .filter(s -> s.getModuleType() == moduleType && s.getSlotIndex().equals(request.getSlotIndex()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ServerErrorCode.MODULE_UNLOCK_FAIL_SLOT_INDEX_OUT_OF_BOUNDS));

        // 4. 모듈 타입 검증
        if (slotInfo.getModuleType() != moduleType) {
            throw new BusinessException(ServerErrorCode.MODULE_UNLOCK_FAIL_INVALID_MODULE_TYPE);
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
        newModule.setInvestedModulePoint(unlockDeducted);
        newModule.setDeleted(false);
        newModule.setCreated(Instant.now());
        newModule.setModified(Instant.now());
        shipModuleRepository.save(newModule);

        // 응답 생성
        return new ModuleUnlockResponse(
                request.getShipId(),
                request.getBodyIndex(),
                moduleType,
                finalModuleSubType,
                request.getSlotIndex(),
                commander.getModulePoint(),
                unlockDeducted
        );
    }

    @Transactional
    public ModuleLevelChangeResponse moduleLevelUp(Long commanderId, ModuleLevelChangeRequest request) {
        Ship ship = shipRepository.findById(request.getShipId())
                .orElseThrow(() -> new BusinessException(ServerErrorCode.MODULE_LEVELUP_FAIL_SHIP_NOT_FOUND));
        if (ship.getFleet().getCommanderId().equals(commanderId) == false) {
            throw new BusinessException(ServerErrorCode.MODULE_LEVELUP_FAIL_FLEET_ACCESS_DENIED);
        }

        EModuleType moduleType = request.getModuleType();
        EModuleSubType moduleSubType = request.getModuleSubType();

        ShipModule module = shipModuleRepository.findByShipIdAndBodyIndexAndModuleTypeAndSlotIndexAndDeletedFalse(
                request.getShipId(), request.getBodyIndex(), moduleType, request.getSlotIndex()
        ).orElseThrow(() -> new BusinessException(ServerErrorCode.MODULE_LEVELUP_FAIL_MODULE_NOT_FOUND));

        if (module.getModuleLevel() != request.getCurrentLevel()) {
            throw new BusinessException(ServerErrorCode.MODULE_LEVELUP_FAIL_LEVEL_MISMATCH);
        }

        // 미네랄 투입 시 모듈포인트 추가 투자 불가
        if (module.getInvestedMineral() > 0) {
            throw new BusinessException(ServerErrorCode.MODULE_LEVELUP_FAIL_MINERAL_INVESTED);
        }

        Commander commander = commanderRepository.findByIdForUpdate(commanderId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.MODULE_LEVELUP_FAIL_COMMANDER_NOT_FOUND));

        // 업그레이드 비용 계산 (현재 레벨부터 목표 레벨까지)
        int totalCost = 0;

        List<ModuleData> moduleDataList = gameDataService.getModulesByType(moduleType);
        for (int level = request.getCurrentLevel(); level < request.getTargetLevel(); level++) {
            final int nextLevel = level + 1;
            ModuleData levelData = moduleDataList.stream()
                    .filter(data -> data.getModuleLevel() == nextLevel)
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(ServerErrorCode.MODULE_LEVELUP_FAIL_MODULE_DATA_NOT_FOUND));

            totalCost = totalCost + levelData.getModulePointCost();
        }

        // 자원 부족 검사 (업그레이드 진행 전에 먼저 체크)
        if (commander.getModulePoint() < totalCost) {
            throw new BusinessException(ServerErrorCode.MODULE_LEVELUP_FAIL_INSUFFICIENT_MINERAL);
        }

        // modulePoint 차감
        int deducted = deductModulePoint(commander, totalCost);
        commanderRepository.save(commander);

        // 모듈 레벨 업데이트 (능력치는 클라이언트가 DataTable에서 조회)
        module.setModuleLevel(request.getTargetLevel());
        module.setInvestedModulePoint(module.getInvestedModulePoint() + deducted);
        module.setModified(Instant.now());
        shipModuleRepository.save(module);

        // 응답 생성
        ModuleLevelChangeResponse response = ModuleLevelChangeResponse.builder()
                .shipId(request.getShipId())
                .bodyIndex(request.getBodyIndex())
                .moduleType(moduleType)
                .moduleSubType(moduleSubType)
                .slotIndex(module.getSlotIndex())
                .newLevel(module.getModuleLevel())
                .pointRemain(commander.getModulePoint())
                .build();

        return response;
    }

    @Transactional
    public ModuleLevelChangeResponse moduleLevelDown(Long commanderId, ModuleLevelChangeRequest request) {
        Ship ship = shipRepository.findById(request.getShipId())
                .orElseThrow(() -> new BusinessException(ServerErrorCode.MODULE_LEVELDOWN_FAIL_SHIP_NOT_FOUND));
        if (ship.getFleet().getCommanderId().equals(commanderId) == false) {
            throw new BusinessException(ServerErrorCode.MODULE_LEVELDOWN_FAIL_FLEET_ACCESS_DENIED);
        }

        EModuleType moduleType = request.getModuleType();
        EModuleSubType moduleSubType = request.getModuleSubType();

        ShipModule module = shipModuleRepository.findByShipIdAndBodyIndexAndModuleTypeAndSlotIndexAndDeletedFalse(
                request.getShipId(), request.getBodyIndex(), moduleType, request.getSlotIndex()
        ).orElseThrow(() -> new BusinessException(ServerErrorCode.MODULE_LEVELDOWN_FAIL_MODULE_NOT_FOUND));

        if (module.getModuleLevel() != request.getCurrentLevel()) {
            throw new BusinessException(ServerErrorCode.MODULE_LEVELDOWN_FAIL_LEVEL_MISMATCH);
        }

        // 미네랄 투입 시 모듈포인트 기준 조정 불가
        if (module.getInvestedMineral() > 0) {
            throw new BusinessException(ServerErrorCode.MODULE_LEVELDOWN_FAIL_MINERAL_INVESTED);
        }

        Commander commander = commanderRepository.findByIdForUpdate(commanderId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.MODULE_LEVELDOWN_FAIL_COMMANDER_NOT_FOUND));

        // targetLevel == 0: Lv.1에서 이전 단계 맥스레벨로 강등
        if (request.getTargetLevel() == 0) {
            if (request.getCurrentLevel() != 1) {
                throw new BusinessException(ServerErrorCode.MODULE_LEVELDOWN_FAIL_LEVEL_MISMATCH);
            }

            EModuleSubType prevSubType = EModuleSubType.fromValue(moduleSubType.getValue() - 100);
            if (prevSubType == null) {
                throw new BusinessException(ServerErrorCode.MODULE_LEVELDOWN_FAIL_LEVEL_MISMATCH);
            }

            int maxLevel = gameDataService.getMaxModuleLevel(moduleType, prevSubType);
            // T1 레벨업 비용은 investedModulePoint에 이미 포함 → 리서치 비용만 환급
            int totalRefund = gameDataService.getModuleResearchCost(moduleSubType);
            commander.setModulePoint(commander.getModulePoint() + totalRefund);
            commanderRepository.save(commander);

            module.setModuleSubType(prevSubType);
            module.setModuleLevel(maxLevel);
            module.setInvestedModulePoint(Math.max(0, module.getInvestedModulePoint() - totalRefund));
            module.setModified(Instant.now());
            shipModuleRepository.save(module);

            return ModuleLevelChangeResponse.builder()
                    .shipId(request.getShipId())
                    .bodyIndex(request.getBodyIndex())
                    .moduleType(moduleType)
                    .moduleSubType(prevSubType)
                    .slotIndex(module.getSlotIndex())
                    .newLevel(maxLevel)
                    .pointRemain(commander.getModulePoint())
                    .investedPoint(module.getInvestedModulePoint())
                    .build();
        }

        // 일반 레벨다운 검증
        if (request.getTargetLevel() < 1 || request.getTargetLevel() >= request.getCurrentLevel()) {
            throw new BusinessException(ServerErrorCode.MODULE_LEVELDOWN_FAIL_LEVEL_MISMATCH);
        }

        // 환급 금액 계산 (targetLevel ~ currentLevel-1 구간 합산)
        int totalRefund = 0;
        List<ModuleData> moduleDataList = gameDataService.getModulesByType(moduleType);
        for (int level = request.getTargetLevel(); level < request.getCurrentLevel(); level++) {
            final int nextLevel = level + 1;
            ModuleData levelData = moduleDataList.stream()
                    .filter(data -> data.getModuleLevel() == nextLevel)
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(ServerErrorCode.MODULE_LEVELDOWN_FAIL_MODULE_DATA_NOT_FOUND));
            totalRefund = totalRefund + levelData.getModulePointCost();
        }

        commander.setModulePoint(commander.getModulePoint() + totalRefund);
        commanderRepository.save(commander);

        module.setModuleLevel(request.getTargetLevel());
        module.setInvestedModulePoint(Math.max(0, module.getInvestedModulePoint() - totalRefund));
        module.setModified(Instant.now());
        shipModuleRepository.save(module);

        return ModuleLevelChangeResponse.builder()
                .shipId(request.getShipId())
                .bodyIndex(request.getBodyIndex())
                .moduleType(moduleType)
                .moduleSubType(moduleSubType)
                .slotIndex(module.getSlotIndex())
                .newLevel(module.getModuleLevel())
                .pointRemain(commander.getModulePoint())
                .investedPoint(module.getInvestedModulePoint())
                .build();
    }

    @Transactional
    public ModuleGradeChangeResponse moduleGradeUp(Long commanderId, ModuleGradeChangeRequest request) {
        Ship gradeUpShip = shipRepository.findById(request.getShipId())
                .orElseThrow(() -> new BusinessException(ServerErrorCode.MODULE_GRADEUP_FAIL_SHIP_NOT_FOUND));
        if (gradeUpShip.getFleet().getCommanderId().equals(commanderId) == false) {
            throw new BusinessException(ServerErrorCode.MODULE_GRADEUP_FAIL_FLEET_ACCESS_DENIED);
        }

        EModuleType currentModuleType = request.getModuleType();
        EModuleSubType currentModuleSubType = request.getModuleSubTypeCurrent();

        EModuleType newModuleType = request.getModuleType();
        EModuleSubType newModuleSubType = gameDataService.getNextSubType(currentModuleSubType);
        if (newModuleSubType == EModuleSubType.none) {
            throw new BusinessException(ServerErrorCode.MODULE_GRADEUP_FAIL_NOT_DIRECT_NEXT_STEP);
        }

        ShipModule currentModule = shipModuleRepository.findByShipIdAndBodyIndexAndModuleTypeAndSlotIndexAndDeletedFalse(
                request.getShipId(), request.getBodyIndex(), currentModuleType, request.getSlotIndex()
        ).orElseThrow(() -> new BusinessException(ServerErrorCode.MODULE_GRADEUP_FAIL_MODULE_NOT_FOUND));

        // 미네랄 투입 시 모듈포인트 기준 등급업 불가
        if (currentModule.getInvestedMineral() > 0) {
            throw new BusinessException(ServerErrorCode.MODULE_GRADEUP_FAIL_MINERAL_INVESTED);
        }

        Commander commander = commanderRepository.findByIdForUpdate(commanderId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.MODULE_GRADEUP_FAIL_COMMANDER_NOT_FOUND));

        // 서브타입 등급 검증 — 서브타입 인코딩에서 파싱: (value/100)%100, 커맨더 레벨이 아닌 subtypeLevel 기준
        int requiredTechTier = (newModuleSubType.getValue() / 100) % 100;
        int subtypeLevel = gameDataService.getSubtypeLevel(commander.getCommanderLevel());
        if (subtypeLevel < requiredTechTier) {
            throw new BusinessException(ServerErrorCode.MODULE_GRADEUP_FAIL_INSUFFICIENT_COMMANDER_LEVEL);
        }

        // 3. 직접 다음 단계 검증
        if (!gameDataService.isDirectNextStep(currentModuleSubType, newModuleSubType)) {
            throw new BusinessException(ServerErrorCode.MODULE_GRADEUP_FAIL_NOT_DIRECT_NEXT_STEP);
        }

        // 레벨업 비용 계산 (현재 레벨 → 맥스레벨, 이미 맥스레벨이면 0)
        int maxLevel = gameDataService.getMaxModuleLevel(currentModuleType, currentModuleSubType);
        List<ModuleData> moduleDataList = gameDataService.getModulesByType(currentModuleType);
        int levelUpCost = 0;
        for (int lv = currentModule.getModuleLevel(); lv < maxLevel; lv++) {
            final int nextLv = lv + 1;
            ModuleData levelData = moduleDataList.stream()
                    .filter(d -> d.getModuleSubType() == currentModuleSubType && d.getModuleLevel() == nextLv)
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(ServerErrorCode.MODULE_GRADEUP_FAIL_MODULE_NOT_FOUND));
            levelUpCost += levelData.getModulePointCost();
        }

        int gradeUpCost = gameDataService.getModuleResearchCost(newModuleSubType);
        int totalCost = levelUpCost + gradeUpCost;
        if (commander.getModulePoint() < totalCost) {
            throw new BusinessException(ServerErrorCode.MODULE_GRADEUP_FAIL_INSUFFICIENT_MINERAL);
        }
        int changeDeducted = deductModulePoint(commander, totalCost);
        commanderRepository.save(commander);

        // 5. 모듈 정보 업데이트 (서브타입 레벨 1로 초기화, 투자 이력 누적)
        currentModule.setModuleSubType(newModuleSubType);
        currentModule.setModuleLevel(1);
        currentModule.setInvestedModulePoint(currentModule.getInvestedModulePoint() + changeDeducted);
        currentModule.setModified(Instant.now());
        shipModuleRepository.save(currentModule);

        return ModuleGradeChangeResponse.builder()
                .shipId(request.getShipId())
                .bodyIndex(request.getBodyIndex())
                .moduleTypeCurrent(currentModuleType)
                .moduleSubTypeCurrent(currentModuleSubType)
                .moduleTypeNew(newModuleType)
                .moduleSubTypeNew(newModuleSubType)
                .slotIndex(request.getSlotIndex())
                .moduleNewLevel(1)
                .pointRemain(commander.getModulePoint())
                .investedPoint(currentModule.getInvestedModulePoint())
                .build();
    }

    @Transactional
    public ModuleGradeChangeResponse moduleGradeUpMineral(Long commanderId, ModuleGradeChangeRequest request) {
        Ship mineralGradeUpShip = shipRepository.findById(request.getShipId())
                .orElseThrow(() -> new BusinessException(ServerErrorCode.MODULE_GRADEUP_MINERAL_FAIL_SHIP_NOT_FOUND));
        if (mineralGradeUpShip.getFleet().getCommanderId().equals(commanderId) == false) {
            throw new BusinessException(ServerErrorCode.MODULE_GRADEUP_MINERAL_FAIL_FLEET_ACCESS_DENIED);
        }

        EModuleType moduleType = request.getModuleType();
        EModuleSubType currentSubType = request.getModuleSubTypeCurrent();
        EModuleSubType newSubType = gameDataService.getNextSubType(currentSubType);
        if (newSubType == EModuleSubType.none) {
            throw new BusinessException(ServerErrorCode.MODULE_GRADEUP_MINERAL_FAIL_NOT_DIRECT_NEXT_STEP);
        }

        ShipModule currentModule = shipModuleRepository.findByShipIdAndBodyIndexAndModuleTypeAndSlotIndexAndDeletedFalse(
                request.getShipId(), request.getBodyIndex(), moduleType, request.getSlotIndex()
        ).orElseThrow(() -> new BusinessException(ServerErrorCode.MODULE_GRADEUP_MINERAL_FAIL_MODULE_NOT_FOUND));

        Commander commander = commanderRepository.findByIdForUpdate(commanderId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.MODULE_GRADEUP_MINERAL_FAIL_COMMANDER_NOT_FOUND));

        // 서브타입 등급 검증 — 커맨더 레벨이 아닌 subtypeLevel 기준
        int requiredTechTier = (newSubType.getValue() / 100) % 100;
        int subtypeLevel = gameDataService.getSubtypeLevel(commander.getCommanderLevel());
        if (subtypeLevel < requiredTechTier) {
            throw new BusinessException(ServerErrorCode.MODULE_GRADEUP_MINERAL_FAIL_INSUFFICIENT_COMMANDER_LEVEL);
        }

        // 비용: 현재 레벨→맥스레벨 mineralCost 합산 + 연구비(pointCost)를 미네랄로
        int maxLevel = gameDataService.getMaxModuleLevel(moduleType, currentSubType);
        List<ModuleData> moduleDataList = gameDataService.getModulesByType(moduleType);
        int levelUpCost = 0;
        for (int lv = currentModule.getModuleLevel(); lv < maxLevel; lv++) {
            final int nextLv = lv + 1;
            ModuleData levelData = moduleDataList.stream()
                    .filter(d -> d.getModuleSubType() == currentSubType && d.getModuleLevel() == nextLv)
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(ServerErrorCode.MODULE_GRADEUP_MINERAL_FAIL_MODULE_DATA_NOT_FOUND));
            int stepCost = levelData.getMineralCost() != null ? levelData.getMineralCost() : 0;
            levelUpCost = levelUpCost + stepCost;
        }

        // 연구비는 pointCost와 동일한 값을 미네랄로 소모
        int gradeUpCost = gameDataService.getModuleResearchCost(newSubType);
        int totalCost = levelUpCost + gradeUpCost;

        if (commander.getMineral() < totalCost) {
            throw new BusinessException(ServerErrorCode.MODULE_GRADEUP_MINERAL_FAIL_INSUFFICIENT_MINERAL);
        }

        commander.setMineral(commander.getMineral() - totalCost);
        commanderRepository.save(commander);

        currentModule.setModuleSubType(newSubType);
        currentModule.setModuleLevel(1);
        currentModule.setInvestedMineral(currentModule.getInvestedMineral() + totalCost);
        currentModule.setModified(Instant.now());
        shipModuleRepository.save(currentModule);

        return ModuleGradeChangeResponse.builder()
                .shipId(request.getShipId())
                .bodyIndex(request.getBodyIndex())
                .moduleTypeCurrent(moduleType)
                .moduleSubTypeCurrent(currentSubType)
                .moduleTypeNew(moduleType)
                .moduleSubTypeNew(newSubType)
                .slotIndex(request.getSlotIndex())
                .moduleNewLevel(1)
                .pointRemain(commander.getMineral())
                .investedPoint(currentModule.getInvestedMineral())
                .build();
    }


    @Transactional
    public ModuleGradeChangeResponse moduleGradeDown(Long commanderId, ModuleGradeChangeRequest request) {
        Ship gradeDownShip = shipRepository.findById(request.getShipId())
                .orElseThrow(() -> new BusinessException(ServerErrorCode.MODULE_GRADEDOWN_FAIL_SHIP_NOT_FOUND));
        if (gradeDownShip.getFleet().getCommanderId().equals(commanderId) == false) {
            throw new BusinessException(ServerErrorCode.MODULE_GRADEDOWN_FAIL_FLEET_ACCESS_DENIED);
        }

        EModuleType moduleType = request.getModuleType();
        EModuleSubType currentSubType = request.getModuleSubTypeCurrent();
        EModuleSubType newSubType = gameDataService.getPrevSubType(currentSubType);

        ShipModule currentModule = shipModuleRepository.findByShipIdAndBodyIndexAndModuleTypeAndSlotIndexAndDeletedFalse(
                request.getShipId(), request.getBodyIndex(), moduleType, request.getSlotIndex()
        ).orElseThrow(() -> new BusinessException(ServerErrorCode.MODULE_GRADEDOWN_FAIL_MODULE_NOT_FOUND));

        // 미네랄 투입 시 모듈포인트 기준 등급다운 불가
        if (currentModule.getInvestedMineral() > 0) {
            throw new BusinessException(ServerErrorCode.MODULE_GRADEDOWN_FAIL_MINERAL_INVESTED);
        }

        Commander commander = commanderRepository.findByIdForUpdate(commanderId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.MODULE_GRADEDOWN_FAIL_COMMANDER_NOT_FOUND));

        // T1 최저등급에서 다운 → 리셋 처리
        if (newSubType == EModuleSubType.none) {
            return moduleGradeDownToReset(gradeDownShip, currentModule, moduleType, currentSubType, request, commander);
        }

        // 현재 서브타입 연구 비용 + 현재 레벨업 비용 + 이전 서브타입 레벨업 비용 전체 환급
        int currentLevel = currentModule.getModuleLevel();
        int currentGradeUpCost = gameDataService.getModuleResearchCost(currentSubType);
        int currentGradeLevelupCost = calcLevelRefundUpTo(moduleType, currentSubType, currentLevel);
        int targetGradeLevelupCost = calcLevelRefund(moduleType, newSubType);
        int moduleOnlyRefund = currentGradeUpCost + currentGradeLevelupCost + targetGradeLevelupCost;

        // body 다운그레이드 시 사라지는 슬롯(빔/미사일/격납고)의 포인트+미네랄 환급 + 초기화
        if (moduleType == EModuleType.body) {
            refundAndResetLostSlots(request.getShipId(), request.getBodyIndex(), newSubType, commander);
        }
        commander.setModulePoint(commander.getModulePoint() + moduleOnlyRefund);
        commanderRepository.save(commander);

        // 이전 서브타입으로 복귀, 레벨 1로 초기화
        currentModule.setModuleSubType(newSubType);
        currentModule.setModuleLevel(1);
        currentModule.setInvestedModulePoint(Math.max(0, currentModule.getInvestedModulePoint() - moduleOnlyRefund));
        currentModule.setModified(Instant.now());
        shipModuleRepository.save(currentModule);

        return ModuleGradeChangeResponse.builder()
                .shipId(request.getShipId())
                .bodyIndex(request.getBodyIndex())
                .moduleTypeCurrent(moduleType)
                .moduleSubTypeCurrent(currentSubType)
                .moduleTypeNew(moduleType)
                .moduleSubTypeNew(newSubType)
                .slotIndex(request.getSlotIndex())
                .moduleNewLevel(1)
                .pointRemain(commander.getModulePoint())
                .investedPoint(currentModule.getInvestedModulePoint())
                .build();
    }

    @Transactional
    public ModuleGradeChangeResponse moduleGradeDownMineral(Long commanderId, ModuleGradeChangeRequest request) {
        Ship mineralGradeDownShip = shipRepository.findById(request.getShipId())
                .orElseThrow(() -> new BusinessException(ServerErrorCode.MODULE_GRADEDOWN_MINERAL_FAIL_SHIP_NOT_FOUND));
        if (mineralGradeDownShip.getFleet().getCommanderId().equals(commanderId) == false) {
            throw new BusinessException(ServerErrorCode.MODULE_GRADEDOWN_MINERAL_FAIL_FLEET_ACCESS_DENIED);
        }

        EModuleType moduleType = request.getModuleType();
        EModuleSubType currentSubType = request.getModuleSubTypeCurrent();

        ShipModule currentModule = shipModuleRepository.findByShipIdAndBodyIndexAndModuleTypeAndSlotIndexAndDeletedFalse(
                request.getShipId(), request.getBodyIndex(), moduleType, request.getSlotIndex()
        ).orElseThrow(() -> new BusinessException(ServerErrorCode.MODULE_GRADEDOWN_MINERAL_FAIL_MODULE_NOT_FOUND));

        // 한 단계 아래 서브타입 계산 (없으면 none 반환)
        int prevSubTypeValue = currentSubType.getValue() - 100;
        EModuleSubType prevSubType = EModuleSubType.fromValue(prevSubTypeValue);

        // baseline 계산 (addShip 배분분은 baseline에서 제외)
        int investedModulePoint = currentModule.getInvestedModulePoint() - currentModule.getAddShipModulePoint();
        int[] baseline = calcModulePointBaseline(moduleType, investedModulePoint);
        int baselineSubTypeVal = (baseline != null) ? baseline[0] : 0;
        boolean isAtBaseline = currentSubType.getValue() <= baselineSubTypeVal;

        // reset 케이스: 최하위 등급이거나 baseline에 도달한 경우 → mineralResetModule에 위임
        if (prevSubType == EModuleSubType.none || isAtBaseline == true) {
            ModuleResetRequest resetReq = new ModuleResetRequest();
            resetReq.setShipId(request.getShipId());
            resetReq.setBodyIndex(request.getBodyIndex());
            resetReq.setModuleType(moduleType);
            resetReq.setSlotIndex(request.getSlotIndex());
            ModuleResetResponse resetRes = mineralResetModule(commanderId, resetReq);
            return ModuleGradeChangeResponse.builder()
                    .shipId(resetRes.getShipId())
                    .bodyIndex(resetRes.getBodyIndex())
                    .moduleTypeCurrent(moduleType)
                    .moduleSubTypeCurrent(currentSubType)
                    .moduleTypeNew(moduleType)
                    .moduleSubTypeNew(resetRes.getModuleSubType())
                    .slotIndex(resetRes.getSlotIndex())
                    .moduleNewLevel(resetRes.getModuleNewLevel())
                    .pointRemain(resetRes.getPointRemain())
                    .investedPoint(resetRes.getInvestedPoint())
                    .isModuleRemoved(resetRes.getIsModuleRemoved())
                    .isShipRemoved(resetRes.getIsShipRemoved())
                    .removedShipId(resetRes.getRemovedShipId())
                    .build();
        }

        // grade down 케이스
        Commander commander = commanderRepository.findByIdForUpdate(commanderId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.MODULE_GRADEDOWN_MINERAL_FAIL_COMMANDER_NOT_FOUND));

        // 환급: 현재 등급 연구비 + 현재 레벨업 비용 + 이전 등급에서 미네랄로 투입된 레벨업 비용
        // prevSubType이 baseline(모듈포인트로 도달한 등급)과 같다면, baseline 레벨까지는 미네랄 투입분이 아니므로 환급 대상에서 제외
        int currentGradeUpCost = gameDataService.getModuleResearchCost(currentSubType);
        int currentGradeLevelupCost = calcMineralLevelRefundUpTo(moduleType, currentSubType, currentModule.getModuleLevel());
        int targetGradeLevelupCost = calcMineralLevelRefund(moduleType, prevSubType);
        if (baseline != null && baseline[0] == prevSubType.getValue()) {
            int baselineLevelupCost = calcMineralLevelRefundUpTo(moduleType, prevSubType, baseline[1]);
            targetGradeLevelupCost = targetGradeLevelupCost - baselineLevelupCost;
        }
        int moduleOnlyRefund = currentGradeUpCost + currentGradeLevelupCost + targetGradeLevelupCost;

        // body 다운그레이드 시 사라지는 슬롯의 미네랄+모듈포인트 환급
        if (moduleType == EModuleType.body) {
            refundAndResetLostSlots(request.getShipId(), request.getBodyIndex(), prevSubType, commander);
        }

        commander.setMineral(commander.getMineral() + moduleOnlyRefund);
        commanderRepository.save(commander);

        // baseline과 prevSubType이 같으면 모듈포인트가 이미 그 서브타입까지 투자됨 → baseline 레벨로 복귀
        int resultLevel = 1;
        if (baseline != null && baseline[0] == prevSubType.getValue()) {
            resultLevel = baseline[1];
        }
        currentModule.setModuleSubType(prevSubType);
        currentModule.setModuleLevel(resultLevel);
        currentModule.setInvestedMineral(Math.max(0, currentModule.getInvestedMineral() - moduleOnlyRefund));
        currentModule.setModified(Instant.now());
        shipModuleRepository.save(currentModule);

        return ModuleGradeChangeResponse.builder()
                .shipId(request.getShipId())
                .bodyIndex(request.getBodyIndex())
                .moduleTypeCurrent(moduleType)
                .moduleSubTypeCurrent(currentSubType)
                .moduleTypeNew(moduleType)
                .moduleSubTypeNew(prevSubType)
                .slotIndex(request.getSlotIndex())
                .moduleNewLevel(resultLevel)
                .pointRemain(commander.getMineral())
                .investedPoint(currentModule.getInvestedMineral())
                .isModuleRemoved(false)
                .isShipRemoved(false)
                .build();
    }





    // T1 최저등급에서 다운 요청 시 리셋 처리
    // - body + 비기함: 함선 전체 삭제 (isShipRemoved)
    // - 그 외: 모듈 soft-delete (isModuleRemoved)
    private ModuleGradeChangeResponse moduleGradeDownToReset(
            Ship ship, ShipModule currentModule, EModuleType moduleType,
            EModuleSubType currentSubType, ModuleGradeChangeRequest request,
            Commander commander) {

        boolean isBodyModule = moduleType == EModuleType.body;
        boolean isNonFlagship = ship.getPositionIndex() != 0;

        if (isBodyModule && isNonFlagship) {
            // 함선 전체 삭제: 전 모듈 환급 후 함선 soft-delete
            List<ShipModule> allModules = shipModuleRepository.findByShipIdAndDeletedFalseOrderBySlotIndex(ship.getId());
            int refundMp = 0;
            for (ShipModule mod : allModules) {
                refundMp += mod.getInvestedModulePoint();
            }
            commander.setModulePoint(commander.getModulePoint() + refundMp);
            commanderRepository.save(commander);

            for (ShipModule mod : allModules) {
                mod.setDeleted(true);
                mod.setModified(Instant.now());
                shipModuleRepository.save(mod);
            }
            ship.setDeleted(true);
            ship.setModified(Instant.now());
            shipRepository.save(ship);

            return ModuleGradeChangeResponse.builder()
                    .shipId(ship.getId())
                    .bodyIndex(request.getBodyIndex())
                    .moduleTypeCurrent(moduleType)
                    .moduleSubTypeCurrent(currentSubType)
                    .slotIndex(request.getSlotIndex())
                    .pointRemain(commander.getModulePoint())
                    .isShipRemoved(true)
                    .removedShipId(ship.getId())
                    .build();
        }

        // 기함 body or 일반 슬롯 모듈: 모듈 soft-delete (기함 body는 T1 레벨1 복귀)
        if (isBodyModule) {
            // 기함 body: refundAndResetLostSlots 후 T1 레벨1로 복귀
            int bodyRefund = currentModule.getInvestedModulePoint();
            refundAndResetLostSlots(ship.getId(), request.getBodyIndex(), EModuleSubType.body_t1_m1, commander);
            commander.setModulePoint(commander.getModulePoint() + bodyRefund);
            commanderRepository.save(commander);

            currentModule.setModuleSubType(EModuleSubType.body_t1_m1);
            currentModule.setModuleLevel(1);
            currentModule.setInvestedModulePoint(0);
            currentModule.setModified(Instant.now());
            shipModuleRepository.save(currentModule);

            return ModuleGradeChangeResponse.builder()
                    .shipId(ship.getId())
                    .bodyIndex(request.getBodyIndex())
                    .moduleTypeCurrent(moduleType)
                    .moduleSubTypeCurrent(currentSubType)
                    .moduleTypeNew(moduleType)
                    .moduleSubTypeNew(EModuleSubType.body_t1_m1)
                    .slotIndex(request.getSlotIndex())
                    .moduleNewLevel(1)
                    .pointRemain(commander.getModulePoint())
                    .investedPoint(0)
                    .isModuleRemoved(false)
                    .build();
        }

        // 일반 슬롯 모듈: soft-delete
        commander.setModulePoint(commander.getModulePoint() + currentModule.getInvestedModulePoint());
        commanderRepository.save(commander);

        currentModule.setDeleted(true);
        currentModule.setModified(Instant.now());
        shipModuleRepository.save(currentModule);

        return ModuleGradeChangeResponse.builder()
                .shipId(ship.getId())
                .bodyIndex(request.getBodyIndex())
                .moduleTypeCurrent(moduleType)
                .moduleSubTypeCurrent(currentSubType)
                .slotIndex(request.getSlotIndex())
                .pointRemain(commander.getModulePoint())
                .isModuleRemoved(true)
                .build();
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

    // body 다운그레이드 시 새 body가 지원하지 않는 슬롯의 포인트 환급 + 초기화 (미네랄도 환급, 기존 호출부 호환용)
    private void refundAndResetLostSlots(Long shipId, int bodyIndex,
                                         EModuleSubType newBodySubType,
                                         Commander commander) {
        refundAndResetLostSlots(shipId, bodyIndex, newBodySubType, commander, true);
    }

    // body 등급 하락 시 사라지는 슬롯의 modulePoint(+선택적 mineral) 환급 및 soft-delete (두 계열 공통)
    // refundMineral=false: resetMineralModules(존 보상 유지비 부족 시 강제초기화) 전용 — modulePoint는 환급하되 기존 정책대로 미네랄은 환급하지 않음
    // 반환값: 실제로 환급된 미네랄 총액 (호출부 totalRefund 집계용)
    private int refundAndResetLostSlots(Long shipId, int bodyIndex,
                                         EModuleSubType newBodySubType,
                                         Commander commander,
                                         boolean refundMineral) {
        List<ModuleSlotInfoDto> newBodySlots = gameDataService.getBodyModuleSlots(newBodySubType);

        java.util.Set<String> supportedKeys = new java.util.HashSet<>();
        if (newBodySlots != null) {
            for (ModuleSlotInfoDto slot : newBodySlots) {
                supportedKeys.add(slot.getModuleType().name() + "_" + slot.getSlotIndex());
            }
        }

        int mineralRefunded = 0;
        List<ShipModule> allModules = shipModuleRepository.findByShipIdAndBodyIndexAndDeletedFalse(shipId, bodyIndex);
        for (ShipModule module : allModules) {
            if (module.getModuleType() == EModuleType.body) continue;

            String key = module.getModuleType().name() + "_" + module.getSlotIndex();
            if (!supportedKeys.contains(key)) {
                commander.setModulePoint(commander.getModulePoint() + module.getInvestedModulePoint());
                if (refundMineral == true) {
                    commander.setMineral(commander.getMineral() + module.getInvestedMineral());
                    mineralRefunded += module.getInvestedMineral();
                }
                module.setDeleted(true);
                module.setInvestedModulePoint(0);
                module.setInvestedMineral(0);
                module.setModified(Instant.now());
                shipModuleRepository.save(module);
            }
        }
        return mineralRefunded;
    }


    // ─────────────────────────────────────────────────────────────────────────
    // 모듈 리셋 (레벨 1 + unlockedSubTypes 초기화 + 투자분 100% 환급)
    // ─────────────────────────────────────────────────────────────────────────
    @Transactional
    public ModuleResetResponse resetModule(Long commanderId, ModuleResetRequest request) {
        Ship ship = shipRepository.findById(request.getShipId())
                .orElseThrow(() -> new BusinessException(ServerErrorCode.MODULE_RESET_FAIL_SHIP_NOT_FOUND));

        if (ship.getFleet().getCommanderId().equals(commanderId) == false)
            throw new BusinessException(ServerErrorCode.MODULE_RESET_FAIL_FLEET_ACCESS_DENIED);

        if (request.getModuleType() == EModuleType.body) {
            // 기함(positionIndex==0) body 리셋만 허용 — T1 레벨1로 되돌리기
            if (ship.getPositionIndex() != 0)
                throw new BusinessException(ServerErrorCode.MODULE_RESET_FAIL_BODY_FORBIDDEN);
            return resetFlagshipBody(ship, commanderId, request);
        }

        ShipModule module = shipModuleRepository.findByShipIdAndBodyIndexAndModuleTypeAndSlotIndexAndDeletedFalse(
                request.getShipId(), request.getBodyIndex(), request.getModuleType(), request.getSlotIndex()
        ).orElseThrow(() -> new BusinessException(ServerErrorCode.MODULE_RESET_FAIL_MODULE_NOT_FOUND));

        Commander commander = commanderRepository.findByIdForUpdate(commanderId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.MODULE_RESET_FAIL_COMMANDER_NOT_FOUND));

        // 투자 이력 환급
        commander.setModulePoint(commander.getModulePoint() + module.getInvestedModulePoint());
        commanderRepository.save(commander);

        // 모듈 soft-delete (언락된 슬롯을 플레이스홀더 상태로 복귀)
        module.setDeleted(true);
        module.setModified(Instant.now());
        shipModuleRepository.save(module);

        return ModuleResetResponse.builder()
                .shipId(request.getShipId())
                .bodyIndex(request.getBodyIndex())
                .moduleType(request.getModuleType())
                .slotIndex(request.getSlotIndex())
                .pointRemain(commander.getModulePoint())
                .build();
    }

    // 기함 body 리셋 — T1 레벨1로 되돌리고 투자 이력 환급 (soft-delete 없이 업데이트)
    private ModuleResetResponse resetFlagshipBody(Ship ship, Long commanderId, ModuleResetRequest request) {
        ShipModule body = shipModuleRepository.findByShipIdAndBodyIndexAndModuleTypeAndSlotIndexAndDeletedFalse(
                ship.getId(), request.getBodyIndex(), EModuleType.body, 0
        ).orElseThrow(() -> new BusinessException(ServerErrorCode.MODULE_RESET_FAIL_MODULE_NOT_FOUND));

        Commander commander = commanderRepository.findByIdForUpdate(commanderId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.MODULE_RESET_FAIL_COMMANDER_NOT_FOUND));

        int bodyModulePointRefund = body.getInvestedModulePoint();
        refundAndResetLostSlots(ship.getId(), request.getBodyIndex(), EModuleSubType.body_t1_m1, commander);
        commander.setModulePoint(commander.getModulePoint() + bodyModulePointRefund);
        commanderRepository.save(commander);

        // body T1 레벨1로 복귀 (삭제 없이 값만 초기화)
        body.setModuleSubType(EModuleSubType.body_t1_m1);
        body.setModuleLevel(1);
        body.setInvestedModulePoint(0);
        body.setModified(Instant.now());
        shipModuleRepository.save(body);

        return ModuleResetResponse.builder()
                .shipId(ship.getId())
                .bodyIndex(request.getBodyIndex())
                .moduleType(EModuleType.body)
                .slotIndex(0)
                .pointRemain(commander.getModulePoint())
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 함선 리셋 + 삭제 (전 슬롯 resetModule 처리 후 함선 soft delete)
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

        // 전 모듈 투자 이력 합산 후 환급
        List<ShipModule> allModules = shipModuleRepository.findByShipIdAndDeletedFalseOrderBySlotIndex(request.getShipId());
        int refundMp = 0;
        for (ShipModule mod : allModules) {
            refundMp += mod.getInvestedModulePoint();
        }
        commander.setModulePoint(commander.getModulePoint() + refundMp);
        commanderRepository.save(commander);

        // 모듈 soft delete
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
                .modulePointRemain(commander.getModulePoint())
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

    private int deductModulePoint(Commander commander, int cost) {
        commander.setModulePoint(commander.getModulePoint() - cost);
        return cost;
    }

    // 서브타입의 Lv.1~targetLevel-1 레벨업 비용 합산 (moduleLevel=n은 Lv.n→n+1 비용)
    private int calcLevelRefundUpTo(EModuleType moduleType, EModuleSubType subType, int targetLevel) {
        List<ModuleData> dataList = gameDataService.getModulesByType(moduleType);
        int refund = 0;
        for (int lv = 1; lv < targetLevel; lv++) {
            final int nextLevel = lv + 1;
            Optional<ModuleData> entry = dataList.stream()
                    .filter(d -> subType.equals(d.getModuleSubType()) && d.getModuleLevel() == nextLevel)
                    .findFirst();
            if (entry.isPresent()) refund += entry.get().getModulePointCost();
        }
        return refund;
    }

    // 서브타입의 Lv.1~maxLevel-1 레벨업 비용 합산 (그레이드/레벨 다운 시 환급 계산용)
    private int calcLevelRefund(EModuleType moduleType, EModuleSubType subType) {
        int maxLevel = gameDataService.getMaxModuleLevel(moduleType, subType);
        List<ModuleData> dataList = gameDataService.getModulesByType(moduleType);
        int refund = 0;
        for (int lv = 1; lv < maxLevel; lv++) {
            final int nextLevel = lv + 1;
            Optional<ModuleData> entry = dataList.stream()
                    .filter(d -> subType.equals(d.getModuleSubType()) && d.getModuleLevel() == nextLevel)
                    .findFirst();
            if (entry.isPresent()) refund += entry.get().getModulePointCost();
        }
        return refund;
    }

    // investedModulePoint → (subTypeValue, level) 역산
    // unlock(1) → T1 레벨업 → T2 그레이드업 → T2 레벨업 → ... 순서로 차감
    // 반환: int[]{subTypeValue, level}, null이면 모듈포인트 투자 없음
    private int[] calcModulePointBaseline(EModuleType moduleType, int investedModulePoint) {
        if (investedModulePoint <= 0) { return null; }
        int unlockCost = 0;
        // 함체는 언락의 대상이 아님, 언락 비용 없음
        if( moduleType != EModuleType.body) {
            unlockCost = gameDataService.getModuleUnlockPrice();
        }
        int remaining  = investedModulePoint - unlockCost;

        List<ModuleData> dataList     = gameDataService.getModulesByType(moduleType);
        EModuleSubType currentSubType = gameDataService.getFirstModuleByType(moduleType).getModuleSubType();

        while (currentSubType != EModuleSubType.none) {
            int maxLevel = gameDataService.getMaxModuleLevel(moduleType, currentSubType);
            if (maxLevel <= 0) break;

            final EModuleSubType subForFilter = currentSubType;
            for (int lv = 1; lv < maxLevel; lv++) {
                final int nextLv    = lv + 1;
                int       levelCost = dataList.stream()
                        .filter(d -> subForFilter.equals(d.getModuleSubType()) && d.getModuleLevel() == nextLv)
                        .mapToInt(d -> d.getModulePointCost() != null ? d.getModulePointCost() : 0)
                        .findFirst()
                        .orElse(0);

                if (remaining < levelCost) {
                    return new int[]{currentSubType.getValue(), lv};
                }
                remaining -= levelCost;
            }

            // 최대레벨 도달 → 다음 그레이드
            EModuleSubType nextSubType = EModuleSubType.fromValue(currentSubType.getValue() + 100);
            if (nextSubType == EModuleSubType.none) {
                return new int[]{currentSubType.getValue(), maxLevel};
            }

            int gradeUpCost = gameDataService.getModuleResearchCost(nextSubType);
            if (remaining < gradeUpCost) {
                return new int[]{currentSubType.getValue(), maxLevel};
            }
            remaining    -= gradeUpCost;
            currentSubType = nextSubType;
        }

        return null;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 플리트 전체 investedMineral 합계 반환
    // ─────────────────────────────────────────────────────────────────────────
    public int getTotalInvestedMineral(Long commanderId) {
        Fleet fleet = fleetRepository.findByCommanderIdAndIsActiveTrueAndDeletedFalse(commanderId)
                .orElse(null);
        if (fleet == null) return 0;

        List<Ship> ships = shipRepository.findByFleetIdAndDeletedFalseOrderByPositionIndex(fleet.getId());
        int total = 0;
        for (Ship ship : ships) {
            List<ShipModule> modules = shipModuleRepository.findByShipIdAndDeletedFalseOrderBySlotIndex(ship.getId());
            for (ShipModule module : modules) {
                total = total + module.getInvestedMineral();
            }
        }
        return total;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 함대 전체 투자 미네랄 한방 환급 — 모든 함선의 모든 모듈을 baseline으로 복원하고 investedMineral 전액 환급
    // ─────────────────────────────────────────────────────────────────────────
    @Transactional
    public FleetResetAllInvestedMineralResponse resetAllInvestedMineral(Long commanderId, FleetResetAllInvestedMineralRequest request) {
        Fleet fleet = fleetRepository.findByCommanderIdAndIsActiveTrueAndDeletedFalse(commanderId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.FLEET_RESET_ALL_INVESTED_MINERAL_FAIL_FLEET_NOT_FOUND));

        if (fleet.getId().equals(request.getFleetId()) == false) {
            throw new BusinessException(ServerErrorCode.FLEET_RESET_ALL_INVESTED_MINERAL_FAIL_FLEET_ACCESS_DENIED);
        }

        Commander commander = commanderRepository.findByIdForUpdate(commanderId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.FLEET_RESET_ALL_INVESTED_MINERAL_FAIL_COMMANDER_NOT_FOUND));

        List<Ship> ships = shipRepository.findByFleetIdAndDeletedFalseOrderByPositionIndex(fleet.getId());
        int directRefund = 0;    // module.investedMineral 합산분 — 아래서 commander에 일괄 반영
        int lostSlotRefund = 0;  // refundAndResetLostSlots가 이미 commander에 직접 반영한 분 — 리포트 집계only
        for (Ship ship : ships) {
            List<ShipModule> modules = shipModuleRepository.findByShipIdAndDeletedFalseOrderBySlotIndex(ship.getId());
            for (ShipModule module : modules) {
                if (module.getInvestedMineral() <= 0) continue;

                directRefund = directRefund + module.getInvestedMineral();

                int[] baselineCalc = calcModulePointBaseline(module.getModuleType(), module.getInvestedModulePoint() - module.getAddShipModulePoint());
                boolean mineralOnlyUnlocked = (baselineCalc == null);
                EModuleSubType resultSubType;
                if (mineralOnlyUnlocked) {
                    if (module.getModuleType() == EModuleType.body) {
                        // body는 investedModulePoint=0이 기함의 정상 초기값 — 삭제 대신 T1 Lv1로 복원
                        resultSubType = EModuleSubType.body_t1_m1;
                        module.setModuleSubType(resultSubType);
                        module.setModuleLevel(1);
                    } else {
                        // 미네랄로만 언락된 슬롯 — soft-delete
                        resultSubType = null;
                        module.setDeleted(true);
                    }
                } else {
                    resultSubType = EModuleSubType.fromValue(baselineCalc[0]);
                    int baselineLevel = baselineCalc[1];
                    module.setModuleSubType(resultSubType);
                    module.setModuleLevel(baselineLevel);
                }
                module.setInvestedMineral(0);
                module.setModified(Instant.now());
                shipModuleRepository.save(module);

                // body 등급이 내려가 더 이상 지원하지 않게 된 하위 슬롯(모듈포인트로만 언락된 것 포함) 정리
                // refundAndResetLostSlots는 commander.mineral/modulePoint를 직접 반영하므로 directRefund에는 합산하지 않음
                if (module.getModuleType() == EModuleType.body && resultSubType != null) {
                    lostSlotRefund += refundAndResetLostSlots(ship.getId(), module.getBodyIndex(), resultSubType, commander, true);
                }
            }
        }

        int totalRefund = directRefund + lostSlotRefund;
        if (totalRefund <= 0) {
            throw new BusinessException(ServerErrorCode.FLEET_RESET_ALL_INVESTED_MINERAL_FAIL_NO_MINERAL_INVESTED);
        }

        commander.setMineral(commander.getMineral() + directRefund);
        commanderRepository.save(commander);

        return FleetResetAllInvestedMineralResponse.builder()
                .mineralRemain(commander.getMineral())
                .totalRefundedMineral(totalRefund)
                .updatedFleetInfo(getActiveFleet(commanderId))
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 미네랄만 차감 (서브타입/레벨/investedMineral 유지) — 세팅 재투입용
    // ─────────────────────────────────────────────────────────────────────────
    @Transactional
    public void deductMineralOnly(Commander commander, int amount) {
        int deducted = Math.max(0, commander.getMineral() - amount);
        commander.setMineral(deducted);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 전투 승리 시 미네랄 초기화 — investedModulePoint 역산으로 복원, 미네랄 소멸
    // ─────────────────────────────────────────────────────────────────────────
    @Transactional
    public int resetMineralModules(Long commanderId) {
        Fleet fleet = fleetRepository.findByCommanderIdAndIsActiveTrueAndDeletedFalse(commanderId)
                .orElse(null);
        if (fleet == null) return 0;

        Commander commander = commanderRepository.findByIdForUpdate(commanderId).orElse(null);

        List<Ship> ships = shipRepository.findByFleetIdAndDeletedFalseOrderByPositionIndex(fleet.getId());
        int totalRefund = 0;
        for (Ship ship : ships) {
            List<ShipModule> modules = shipModuleRepository.findByShipIdAndDeletedFalseOrderBySlotIndex(ship.getId());
            for (ShipModule module : modules) {
                if (module.getInvestedMineral() <= 0) continue;

                totalRefund = totalRefund + module.getInvestedMineral();

                int[] baselineCalc = calcModulePointBaseline(module.getModuleType(), module.getInvestedModulePoint() - module.getAddShipModulePoint());
                boolean mineralOnlyUnlocked = (baselineCalc == null);
                EModuleSubType resultSubType;
                if (mineralOnlyUnlocked) {
                    if (module.getModuleType() == EModuleType.body) {
                        // body는 investedModulePoint=0이 기함의 정상 초기값 — 삭제 대신 T1 Lv1로 복원
                        resultSubType = EModuleSubType.body_t1_m1;
                        module.setModuleSubType(resultSubType);
                        module.setModuleLevel(1);
                    } else {
                        // 미네랄로만 언락된 슬롯 — soft-delete
                        resultSubType = null;
                        module.setDeleted(true);
                    }
                } else {
                    // 역산 기준값으로 복원
                    resultSubType = EModuleSubType.fromValue(baselineCalc[0]);
                    int baselineLevel = baselineCalc[1];
                    module.setModuleSubType(resultSubType);
                    module.setModuleLevel(baselineLevel);
                }
                module.setInvestedMineral(0);
                module.setModified(Instant.now());
                shipModuleRepository.save(module);

                // body 등급이 내려가 더 이상 지원하지 않게 된 하위 슬롯 정리
                // 미네랄은 이 경로의 기존 정책대로 소멸(환급 없음), modulePoint만 정당하게 환급
                if (module.getModuleType() == EModuleType.body && resultSubType != null && commander != null) {
                    refundAndResetLostSlots(ship.getId(), module.getBodyIndex(), resultSubType, commander, false);
                }
            }
        }

        if (commander != null) {
            commanderRepository.save(commander);
        }
        return totalRefund;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 미네랄 모듈 강화 API (미네랄 소모, 전투 승리 시 자동 초기화)
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public ModuleUnlockResponse moduleUnlockMineral(Long commanderId, ModuleUnlockRequest request) {
        Ship ship = shipRepository.findById(request.getShipId())
                .orElseThrow(() -> new BusinessException(ServerErrorCode.MODULE_UNLOCK_MINERAL_FAIL_SHIP_NOT_FOUND));
        if (ship.getFleet().getCommanderId().equals(commanderId) == false) {
            throw new BusinessException(ServerErrorCode.MODULE_UNLOCK_MINERAL_FAIL_FLEET_ACCESS_DENIED);
        }

        EModuleType moduleType = request.getModuleType();

        Optional<ShipModule> existingModule = shipModuleRepository.findByShipIdAndBodyIndexAndModuleTypeAndSlotIndexAndDeletedFalse(
                request.getShipId(), request.getBodyIndex(), moduleType, request.getSlotIndex()
        );
        if (existingModule.isPresent()) {
            throw new BusinessException(ServerErrorCode.MODULE_UNLOCK_MINERAL_FAIL_ALREADY_UNLOCKED);
        }

        // body 모듈 찾기 + 슬롯 유효성 확인
        ShipModule bodyModule = shipModuleRepository.findByShipIdAndBodyIndexAndModuleTypeAndSlotIndexAndDeletedFalse(
                request.getShipId(), request.getBodyIndex(), EModuleType.body, 0
        ).orElseThrow(() -> new BusinessException(ServerErrorCode.MODULE_UNLOCK_MINERAL_FAIL_BODY_MODULE_NOT_FOUND));

        List<ModuleData> bodyDataList = gameDataService.getModulesByType(EModuleType.body);
        ModuleData bodyData = bodyDataList.stream()
                .filter(d -> d.getModuleSubType() == bodyModule.getModuleSubType()
                        && d.getModuleLevel().equals(bodyModule.getModuleLevel()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ServerErrorCode.MODULE_UNLOCK_MINERAL_FAIL_BODY_DATA_NOT_FOUND));

        ModuleSlotInfoDto slotInfo = bodyData.getModuleSlots().stream()
                .filter(s -> s.getModuleType() == moduleType && s.getSlotIndex().equals(request.getSlotIndex()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ServerErrorCode.MODULE_UNLOCK_MINERAL_FAIL_SLOT_INDEX_OUT_OF_BOUNDS));

        if (slotInfo.getModuleType() != moduleType) {
            throw new BusinessException(ServerErrorCode.MODULE_UNLOCK_MINERAL_FAIL_INVALID_MODULE_TYPE);
        }

        Commander commander = commanderRepository.findByIdForUpdate(commanderId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.MODULE_UNLOCK_MINERAL_FAIL_COMMANDER_NOT_FOUND));

        int unlockCost = gameDataService.getDataTableConfig().getModuleUnlockPrice();
        if (commander.getMineral() < unlockCost) {
            throw new BusinessException(ServerErrorCode.MODULE_UNLOCK_MINERAL_FAIL_INSUFFICIENT_MINERAL);
        }

        commander.setMineral(commander.getMineral() - unlockCost);
        commanderRepository.save(commander);

        int defaultSubTypeValue = moduleType.getValue() * 10000 + 101;
        EModuleSubType finalModuleSubType = EModuleSubType.fromValue(defaultSubTypeValue);

        ShipModule newModule = new ShipModule();
        newModule.setShip(ship);
        newModule.setBodyIndex(request.getBodyIndex());
        newModule.setSlotIndex(request.getSlotIndex());
        newModule.setModuleType(moduleType);
        newModule.setModuleSubType(finalModuleSubType);
        newModule.setModuleLevel(1);
        newModule.setInvestedMineral(unlockCost);
        newModule.setDeleted(false);
        newModule.setCreated(Instant.now());
        newModule.setModified(Instant.now());
        shipModuleRepository.save(newModule);

        return ModuleUnlockResponse.builder()
                .shipId(request.getShipId())
                .bodyIndex(request.getBodyIndex())
                .moduleType(moduleType)
                .moduleSubType(finalModuleSubType)
                .slotIndex(request.getSlotIndex())
                .pointRemain(commander.getMineral())
                .investedPoint(newModule.getInvestedMineral())
                .build();
    }

    @Transactional
    public ModuleLevelChangeResponse moduleLevelUpMineral(Long commanderId, ModuleLevelChangeRequest request) {
        Ship mineralLvUpShip = shipRepository.findById(request.getShipId())
                .orElseThrow(() -> new BusinessException(ServerErrorCode.MODULE_LEVELUP_MINERAL_FAIL_SHIP_NOT_FOUND));
        if (mineralLvUpShip.getFleet().getCommanderId().equals(commanderId) == false) {
            throw new BusinessException(ServerErrorCode.MODULE_LEVELUP_MINERAL_FAIL_FLEET_ACCESS_DENIED);
        }

        EModuleType moduleType = request.getModuleType();
        EModuleSubType moduleSubType = request.getModuleSubType();

        ShipModule module = shipModuleRepository.findByShipIdAndBodyIndexAndModuleTypeAndSlotIndexAndDeletedFalse(
                request.getShipId(), request.getBodyIndex(), moduleType, request.getSlotIndex()
        ).orElseThrow(() -> new BusinessException(ServerErrorCode.MODULE_LEVELUP_MINERAL_FAIL_MODULE_NOT_FOUND));

        if (module.getModuleLevel() != request.getCurrentLevel()) {
            throw new BusinessException(ServerErrorCode.MODULE_LEVELUP_MINERAL_FAIL_LEVEL_MISMATCH);
        }

        // 미네랄 비용 계산 (mineralCost 사용)
        int totalCost = 0;
        List<ModuleData> moduleDataList = gameDataService.getModulesByType(moduleType);
        for (int level = request.getCurrentLevel(); level < request.getTargetLevel(); level++) {
            final int nextLevel = level + 1;
            ModuleData levelData = moduleDataList.stream()
                    .filter(d -> d.getModuleSubType() == moduleSubType && d.getModuleLevel() == nextLevel)
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(ServerErrorCode.MODULE_LEVELUP_MINERAL_FAIL_MODULE_DATA_NOT_FOUND));
            int stepCost = levelData.getMineralCost() != null ? levelData.getMineralCost() : 0;
            totalCost = totalCost + stepCost;
        }

        Commander commander = commanderRepository.findByIdForUpdate(commanderId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.MODULE_LEVELUP_MINERAL_FAIL_COMMANDER_NOT_FOUND));

        if (commander.getMineral() < totalCost) {
            throw new BusinessException(ServerErrorCode.MODULE_LEVELUP_MINERAL_FAIL_INSUFFICIENT_MINERAL);
        }

        commander.setMineral(commander.getMineral() - totalCost);
        commanderRepository.save(commander);

        module.setModuleLevel(request.getTargetLevel());
        module.setInvestedMineral(module.getInvestedMineral() + totalCost);
        module.setModified(Instant.now());
        shipModuleRepository.save(module);

        return ModuleLevelChangeResponse.builder()
                .shipId(request.getShipId())
                .bodyIndex(request.getBodyIndex())
                .moduleType(moduleType)
                .moduleSubType(moduleSubType)
                .slotIndex(module.getSlotIndex())
                .newLevel(module.getModuleLevel())
                .pointRemain(commander.getMineral())
                .investedPoint(module.getInvestedMineral())
                .build();
    }

    @Transactional
    public ModuleLevelChangeResponse moduleLevelDownMineral(Long commanderId, ModuleLevelChangeRequest request) {
        Ship mineralLvDownShip = shipRepository.findById(request.getShipId())
                .orElseThrow(() -> new BusinessException(ServerErrorCode.MODULE_LEVELDOWN_MINERAL_FAIL_SHIP_NOT_FOUND));
        if (mineralLvDownShip.getFleet().getCommanderId().equals(commanderId) == false) {
            throw new BusinessException(ServerErrorCode.MODULE_LEVELDOWN_MINERAL_FAIL_FLEET_ACCESS_DENIED);
        }

        EModuleType moduleType = request.getModuleType();
        EModuleSubType moduleSubType = request.getModuleSubType();

        ShipModule module = shipModuleRepository.findByShipIdAndBodyIndexAndModuleTypeAndSlotIndexAndDeletedFalse(
                request.getShipId(), request.getBodyIndex(), moduleType, request.getSlotIndex()
        ).orElseThrow(() -> new BusinessException(ServerErrorCode.MODULE_LEVELDOWN_MINERAL_FAIL_MODULE_NOT_FOUND));

        if (module.getModuleLevel() != request.getCurrentLevel()) {
            throw new BusinessException(ServerErrorCode.MODULE_LEVELDOWN_MINERAL_FAIL_LEVEL_MISMATCH);
        }

        // targetLevel == 0: Lv.1에서 이전 등급 맥스레벨로 강등 (그레이드업만 취소, 이전 등급 투자금은 유지)
        if (request.getTargetLevel() == 0) {
            if (request.getCurrentLevel() != 1) {
                throw new BusinessException(ServerErrorCode.MODULE_LEVELDOWN_MINERAL_FAIL_LEVEL_MISMATCH);
            }

            EModuleSubType prevSubType = EModuleSubType.fromValue(moduleSubType.getValue() - 100);
            if (prevSubType == null) {
                throw new BusinessException(ServerErrorCode.MODULE_LEVELDOWN_MINERAL_FAIL_LEVEL_MISMATCH);
            }

            int maxLevel = gameDataService.getMaxModuleLevel(moduleType, prevSubType);
            // 이전 등급 레벨업 비용은 investedMineral에 이미 포함 → 그레이드업 비용만 환급
            int totalRefund = gameDataService.getModuleResearchCost(moduleSubType);

            Commander commander = commanderRepository.findByIdForUpdate(commanderId)
                    .orElseThrow(() -> new BusinessException(ServerErrorCode.MODULE_LEVELDOWN_MINERAL_FAIL_COMMANDER_NOT_FOUND));

            if (moduleType == EModuleType.body) {
                refundAndResetLostSlots(request.getShipId(), request.getBodyIndex(), prevSubType, commander);
            }

            commander.setMineral(commander.getMineral() + totalRefund);
            commanderRepository.save(commander);

            module.setModuleSubType(prevSubType);
            module.setModuleLevel(maxLevel);
            module.setInvestedMineral(Math.max(0, module.getInvestedMineral() - totalRefund));
            module.setModified(Instant.now());
            shipModuleRepository.save(module);

            return ModuleLevelChangeResponse.builder()
                    .shipId(request.getShipId())
                    .bodyIndex(request.getBodyIndex())
                    .moduleType(moduleType)
                    .moduleSubType(prevSubType)
                    .slotIndex(module.getSlotIndex())
                    .newLevel(maxLevel)
                    .pointRemain(commander.getMineral())
                    .investedPoint(module.getInvestedMineral())
                    .build();
        }

        if (request.getTargetLevel() < 1 || request.getTargetLevel() >= request.getCurrentLevel()) {
            throw new BusinessException(ServerErrorCode.MODULE_LEVELDOWN_MINERAL_FAIL_LEVEL_MISMATCH);
        }

        // 모듈포인트 기준 레벨 아래로 다운 불가 (역산) — baseline의 subType이 현재 subType과 같을 때만 적용
        int[] baselineCalc = calcModulePointBaseline(moduleType, module.getInvestedModulePoint() - module.getAddShipModulePoint());
        int baselineLevel = 1;
        if (baselineCalc != null && baselineCalc[0] == moduleSubType.getValue()) {
            baselineLevel = baselineCalc[1];
        }
        if (request.getTargetLevel() < baselineLevel) {
            throw new BusinessException(ServerErrorCode.MODULE_LEVELDOWN_MINERAL_FAIL_BELOW_POINT_BASELINE);
        }

        // 환급 계산 (mineralCost 사용)
        int totalRefund = 0;
        List<ModuleData> moduleDataList = gameDataService.getModulesByType(moduleType);
        for (int level = request.getTargetLevel(); level < request.getCurrentLevel(); level++) {
            final int nextLevel = level + 1;
            ModuleData levelData = moduleDataList.stream()
                    .filter(d -> d.getModuleSubType() == moduleSubType && d.getModuleLevel() == nextLevel)
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(ServerErrorCode.MODULE_LEVELDOWN_MINERAL_FAIL_MODULE_DATA_NOT_FOUND));
            int stepRefund = levelData.getMineralCost() != null ? levelData.getMineralCost() : 0;
            totalRefund = totalRefund + stepRefund;
        }

        Commander commander = commanderRepository.findByIdForUpdate(commanderId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.MODULE_LEVELDOWN_MINERAL_FAIL_COMMANDER_NOT_FOUND));

        commander.setMineral(commander.getMineral() + totalRefund);
        commanderRepository.save(commander);

        module.setModuleLevel(request.getTargetLevel());
        module.setInvestedMineral(Math.max(0, module.getInvestedMineral() - totalRefund));
        module.setModified(Instant.now());
        shipModuleRepository.save(module);

        return ModuleLevelChangeResponse.builder()
                .shipId(request.getShipId())
                .bodyIndex(request.getBodyIndex())
                .moduleType(moduleType)
                .moduleSubType(moduleSubType)
                .slotIndex(module.getSlotIndex())
                .newLevel(module.getModuleLevel())
                .pointRemain(commander.getMineral())
                .investedPoint(module.getInvestedMineral())
                .build();
    }





    @Transactional
    public ModuleResetResponse mineralResetModule(Long commanderId, ModuleResetRequest request) {
        Ship ship = shipRepository.findById(request.getShipId())
                .orElseThrow(() -> new BusinessException(ServerErrorCode.MODULE_RESET_MINERAL_FAIL_SHIP_NOT_FOUND));

        if (ship.getFleet().getCommanderId().equals(commanderId) == false) {
            throw new BusinessException(ServerErrorCode.MODULE_RESET_MINERAL_FAIL_FLEET_ACCESS_DENIED);
        }

        // body 타입은 기함/비기함에 따라 별도 처리
        if (request.getModuleType() == EModuleType.body) {
            return mineralResetBodyModule(ship, commanderId, request);
        }

        ShipModule currentModule = shipModuleRepository.findByShipIdAndBodyIndexAndModuleTypeAndSlotIndexAndDeletedFalse(
                request.getShipId(), request.getBodyIndex(), request.getModuleType(), request.getSlotIndex()
        ).orElseThrow(() -> new BusinessException(ServerErrorCode.MODULE_RESET_MINERAL_FAIL_MODULE_NOT_FOUND));

        if (currentModule.getInvestedMineral() <= 0) {
            throw new BusinessException(ServerErrorCode.MODULE_RESET_MINERAL_FAIL_NO_MINERAL_INVESTED);
        }

        Commander commander = commanderRepository.findByIdForUpdate(commanderId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.MODULE_RESET_MINERAL_FAIL_COMMANDER_NOT_FOUND));

        int refund = currentModule.getInvestedMineral();
        commander.setMineral(commander.getMineral() + refund);
        commanderRepository.save(commander);

        // investedModulePoint 역산으로 기준 서브타입/레벨 결정
        int[] baseline      = calcModulePointBaseline(request.getModuleType(), currentModule.getInvestedModulePoint() - currentModule.getAddShipModulePoint());
        boolean isModuleRemoved = (baseline == null);

        EModuleSubType resultSubType;
        int resultLevel;
        if (isModuleRemoved == true) {
            // 모듈포인트 투자 없음(미네랄로만 언락) → 완전 삭제
            currentModule.setDeleted(true);
            currentModule.setInvestedMineral(0);
            currentModule.setModified(Instant.now());
            shipModuleRepository.save(currentModule);
            resultSubType = EModuleSubType.none;
            resultLevel   = 0;
        } else {
            // 모듈포인트 기준값으로 복원
            EModuleSubType baselineSubType = EModuleSubType.fromValue(baseline[0]);
            int            baselineLevel   = baseline[1];
            currentModule.setModuleSubType(baselineSubType);
            currentModule.setModuleLevel(baselineLevel);
            currentModule.setInvestedMineral(0);
            currentModule.setModified(Instant.now());
            shipModuleRepository.save(currentModule);
            resultSubType = baselineSubType;
            resultLevel   = baselineLevel;
        }

        return ModuleResetResponse.builder()
                .shipId(request.getShipId())
                .bodyIndex(request.getBodyIndex())
                .moduleType(request.getModuleType())
                .moduleSubType(resultSubType)
                .slotIndex(request.getSlotIndex())
                .moduleNewLevel(resultLevel)
                .isModuleRemoved(isModuleRemoved)
                .pointRemain(commander.getMineral())
                .investedPoint(0)
                .isShipRemoved(false)
                .build();
    }

    // body 미네랄 리셋 — 기함/비기함 공통: 미네랄 투자분만 환급하고 baseline으로 복귀
    // add ship은 모듈포인트 영역이므로 미네랄 리셋으로 함선을 삭제하지 않음
    private ModuleResetResponse mineralResetBodyModule(Ship ship, Long commanderId, ModuleResetRequest request) {
        Commander commander = commanderRepository.findByIdForUpdate(commanderId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.MODULE_RESET_MINERAL_FAIL_COMMANDER_NOT_FOUND));

        // 미네랄 환급, 기준값으로 복귀 (함체는 삭제하지 않음)
        ShipModule bodyModule = shipModuleRepository.findByShipIdAndBodyIndexAndModuleTypeAndSlotIndexAndDeletedFalse(
                ship.getId(), request.getBodyIndex(), EModuleType.body, 0
        ).orElseThrow(() -> new BusinessException(ServerErrorCode.MODULE_RESET_MINERAL_FAIL_MODULE_NOT_FOUND));

        if (bodyModule.getInvestedMineral() <= 0) {
            throw new BusinessException(ServerErrorCode.MODULE_RESET_FAIL_NO_MINERAL_INVESTED);
        }

        int refund = bodyModule.getInvestedMineral();
        commander.setMineral(commander.getMineral() + refund);

        int[] baseline = calcModulePointBaseline(EModuleType.body, bodyModule.getInvestedModulePoint() - bodyModule.getAddShipModulePoint());
        EModuleSubType resultSubType;
        int resultLevel;
        if (baseline != null) {
            resultSubType = EModuleSubType.fromValue(baseline[0]);
            resultLevel   = baseline[1];
        } else {
            // 모듈포인트 투자 없음 → 최소 등급/레벨로 복귀
            resultSubType = EModuleSubType.body_t1_m1;
            resultLevel   = 1;
        }

        // body 등급 하락 시 사라지는 슬롯의 미네랄/모듈포인트 환급
        refundAndResetLostSlots(ship.getId(), request.getBodyIndex(), resultSubType, commander);
        commanderRepository.save(commander);

        bodyModule.setModuleSubType(resultSubType);
        bodyModule.setModuleLevel(resultLevel);
        bodyModule.setInvestedMineral(0);
        bodyModule.setModified(Instant.now());
        shipModuleRepository.save(bodyModule);

        return ModuleResetResponse.builder()
                .shipId(ship.getId())
                .bodyIndex(request.getBodyIndex())
                .moduleType(EModuleType.body)
                .moduleSubType(resultSubType)
                .slotIndex(0)
                .moduleNewLevel(resultLevel)
                .isModuleRemoved(false)
                .pointRemain(commander.getMineral())
                .investedPoint(0)
                .isShipRemoved(false)
                .build();
    }


    // 함선 전체 모듈의 미네랄 합산 반환 (모듈은 삭제하지 않음 — 호출부에서 mineralRemoveShip과 함께 사용)
    private int mineralRefundAllModules(Long shipId, Commander commander) {
        List<ShipModule> allModules = shipModuleRepository.findByShipIdAndDeletedFalseOrderBySlotIndex(shipId);
        int mineralRefund = 0;
        int modulePointRefund = 0;
        for (ShipModule mod : allModules) {
            mineralRefund = mineralRefund + mod.getInvestedMineral();
            modulePointRefund = modulePointRefund + mod.getInvestedModulePoint();
        }
        if (modulePointRefund > 0) {
            commander.setModulePoint(commander.getModulePoint() + modulePointRefund);
        }
        return mineralRefund;
    }

    // 함선 soft-delete (모든 모듈 포함)
    private void mineralRemoveShip(Ship ship) {
        List<ShipModule> allModules = shipModuleRepository.findByShipIdAndDeletedFalseOrderBySlotIndex(ship.getId());
        for (ShipModule mod : allModules) {
            mod.setDeleted(true);
            mod.setInvestedMineral(0);
            mod.setInvestedModulePoint(0);
            mod.setModified(Instant.now());
            shipModuleRepository.save(mod);
        }
        ship.setDeleted(true);
        ship.setModified(Instant.now());
        shipRepository.save(ship);
    }

    // 미네랄 비용 기준: Lv.1~targetLevel-1 구간 mineralCost 합산
    private int calcMineralLevelRefundUpTo(EModuleType moduleType, EModuleSubType subType, int targetLevel) {
        List<ModuleData> dataList = gameDataService.getModulesByType(moduleType);
        int refund = 0;
        for (int lv = 1; lv < targetLevel; lv++) {
            final int nextLevel = lv + 1;
            Optional<ModuleData> entry = dataList.stream()
                    .filter(d -> subType.equals(d.getModuleSubType()) && d.getModuleLevel() == nextLevel)
                    .findFirst();
            if (entry.isPresent()) {
                int cost = entry.get().getMineralCost() != null ? entry.get().getMineralCost() : 0;
                refund = refund + cost;
            }
        }
        return refund;
    }

    // 미네랄 비용 기준: Lv.1~maxLevel-1 구간 mineralCost 합산
    private int calcMineralLevelRefund(EModuleType moduleType, EModuleSubType subType) {
        int maxLevel = gameDataService.getMaxModuleLevel(moduleType, subType);
        return calcMineralLevelRefundUpTo(moduleType, subType, maxLevel);
    }

    // investedMineral 역산: 미네랄 투입이 없었을 때의 기준 레벨 반환
    private int calcMineralBaselineLevel(EModuleType moduleType, EModuleSubType subType, int investedMineral, int currentLevel) {
        if (investedMineral <= 0) return currentLevel;
        List<ModuleData> dataList = gameDataService.getModulesByType(moduleType);
        int remaining = investedMineral;
        int baselineLevel = currentLevel;
        for (int lv = currentLevel; lv > 1; lv--) {
            final int thisLevel = lv;
            int stepCost = dataList.stream()
                    .filter(d -> subType.equals(d.getModuleSubType()) && d.getModuleLevel() == thisLevel)
                    .mapToInt(d -> d.getMineralCost() != null ? d.getMineralCost() : 0)
                    .findFirst()
                    .orElse(0);
            if (stepCost <= 0) break;
            remaining = remaining - stepCost;
            baselineLevel = lv - 1;
            if (remaining <= 0) break;
        }
        return baselineLevel;
    }

    // PVP 상대 함대 전달 시 미네랄 레벨업 분을 제거한 FleetInfoDto 반환
    public FleetInfoDto stripMineralLevels(FleetInfoDto fleet) {
        if (fleet == null || fleet.getShips() == null) return fleet;
        for (ShipInfoDto ship : fleet.getShips()) {
            if (ship.getBodies() == null) continue;
            for (ModuleBodyInfoDto body : ship.getBodies()) {
                int bodyInvestedMineral = body.getInvestedMineral() != null ? body.getInvestedMineral() : 0;
                int bodyBaselineLevel = calcMineralBaselineLevel(EModuleType.body, body.getModuleSubType(), bodyInvestedMineral, body.getModuleLevel());
                body.setModuleLevel(bodyBaselineLevel);
                body.setInvestedMineral(0);

                stripMineralLevelsFromModuleList(body.getBeams(),    EModuleType.beam);
                stripMineralLevelsFromModuleList(body.getMissiles(), EModuleType.missile);
                stripMineralLevelsFromModuleList(body.getHangers(),  EModuleType.hanger);
            }
        }
        return fleet;
    }

    private void stripMineralLevelsFromModuleList(List<ModuleInfoDto> modules, EModuleType moduleType) {
        if (modules == null) return;
        for (ModuleInfoDto module : modules) {
            int investedMineral = module.getInvestedMineral() != null ? module.getInvestedMineral() : 0;
            int baselineLevel = calcMineralBaselineLevel(moduleType, module.getModuleSubType(), investedMineral, module.getModuleLevel());
            module.setModuleLevel(baselineLevel);
            module.setInvestedMineral(0);
        }
    }

    @Transactional
    public FleetInstantRepairResponse instantRepairFleet(Long commanderId) {
        Commander commander = commanderRepository.findByIdForUpdate(commanderId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.FLEET_INSTANT_REPAIR_FAIL_COMMANDER_NOT_FOUND));

        Fleet fleet = fleetRepository.findByCommanderIdAndIsActiveTrueAndDeletedFalse(commanderId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.FLEET_INSTANT_REPAIR_FAIL_FLEET_NOT_FOUND));

        List<Ship> ships = shipRepository.findByFleetIdAndDeletedFalseOrderByPositionIndex(fleet.getId());
        List<ModuleData> bodyDataList = gameDataService.getModulesByType(EModuleType.body);

        int cost = gameDataService.getBattleRepairMineralPerSec() * gameDataService.getInstantRepairBaseSecs();
        if (commander.getMineral() < cost)
            throw new BusinessException(ServerErrorCode.FLEET_INSTANT_REPAIR_FAIL_INSUFFICIENT_MINERAL);

        commander.setMineral(commander.getMineral() - cost);
        commanderRepository.save(commander);

        // HP 전체 회복
        for (Ship ship : ships) {
            List<ShipModule> modules = shipModuleRepository.findByShipIdAndDeletedFalseOrderBySlotIndex(ship.getId());
            for (ShipModule m : modules) {
                if (m.getModuleType() != EModuleType.body) continue;
                float maxHealth = bodyDataList.stream()
                        .filter(d -> d.getModuleSubType() == m.getModuleSubType()
                                && d.getModuleLevel().equals(m.getModuleLevel()))
                        .findFirst()
                        .map(d -> d.getHealth() != null ? d.getHealth() : 0f)
                        .orElse(0f);
                m.setCurrentHealth(maxHealth);
                shipModuleRepository.save(m);
            }
        }

        FleetInstantRepairResponse response = new FleetInstantRepairResponse();
        response.setMineralRemain(commander.getMineral());
        return response;
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









