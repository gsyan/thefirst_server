package com.bk.sbs.service;

import com.bk.sbs.config.DataTableModule;
import com.bk.sbs.dto.*;
import com.bk.sbs.entity.*;
import com.bk.sbs.entity.Character;
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
    private final CharacterRepository characterRepository;
    private final ModuleResearchRepository moduleResearchRepository;
    private final GameDataService gameDataService;

    public FleetService(FleetRepository fleetRepository, ShipRepository shipRepository,
                       ShipModuleRepository shipModuleRepository,
                       CharacterRepository characterRepository,
                       ModuleResearchRepository moduleResearchRepository, GameDataService gameDataService) {
        this.fleetRepository = fleetRepository;
        this.shipRepository = shipRepository;
        this.shipModuleRepository = shipModuleRepository;
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
        beamModule.setCreated(LocalDateTime.now());
        beamModule.setModified(LocalDateTime.now());
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
        if (character.getModulePoint() < shipAddCost) {
            throw new BusinessException(ServerErrorCode.ADD_SHIP_FAIL_INSUFFICIENT_MODULE_POINT);
        }

        // 자원 차감
        int addShipDeducted = deductModulePoint(character, shipAddCost);
        characterRepository.save(character);

        // 빠진 positionIndex(삭제된 슬롯) 중 가장 낮은 값 사용
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
        newShip.setCreated(LocalDateTime.now());
        newShip.setModified(LocalDateTime.now());
        Ship savedShip = shipRepository.save(newShip);

        // 기본 모듈들 생성 (Body, Weapon) — 비용 분배: beam = moduleUnlockPrice, body = 나머지
        int moduleUnlockPrice = gameDataService.getModuleUnlockPrice();
        int bodyInvested = shipAddCost - moduleUnlockPrice;
        createDefaultModules(savedShip, bodyInvested, moduleUnlockPrice);

        // 응답 생성
        AddShipResponse response = AddShipResponse.builder()
                .newShipInfo(convertShipToShipInfoDto(savedShip))
                .modulePointRemain(character.getModulePoint())
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
        weaponModule.setInvestedModulePoint(beamInvestedMineral);
        weaponModule.setDeleted(false);
        weaponModule.setCreated(LocalDateTime.now());
        weaponModule.setModified(LocalDateTime.now());
        shipModuleRepository.save(weaponModule);
    }

    @Transactional
    public ModuleLevelChangeResponse levelUpModule(Long characterId, ModuleLevelChangeRequest request) {
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
            final int nextLevel = level + 1;
            ModuleData levelData = moduleDataList.stream()
                    .filter(data -> data.getModuleLevel() == nextLevel)
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(ServerErrorCode.UPGRADE_MODULE_FAIL_MODULE_DATA_NOT_FOUND));

            totalCost = totalCost + levelData.getModulePointCost();
        }

        // 자원 부족 검사 (업그레이드 진행 전에 먼저 체크)
        if (character.getModulePoint() < totalCost) {
            throw new BusinessException(ServerErrorCode.UPGRADE_MODULE_FAIL_INSUFFICIENT_MINERAL);
        }

        // modulePoint 차감
        int deducted = deductModulePoint(character, totalCost);
        characterRepository.save(character);

        // 모듈 레벨 업데이트 (능력치는 클라이언트가 DataTable에서 조회)
        module.setModuleLevel(request.getTargetLevel());
        module.setInvestedModulePoint(module.getInvestedModulePoint() + deducted);
        module.setModified(LocalDateTime.now());
        shipModuleRepository.save(module);

        // 응답 생성
        ModuleLevelChangeResponse response = ModuleLevelChangeResponse.builder()
                .shipId(request.getShipId())
                .bodyIndex(request.getBodyIndex())
                .moduleType(moduleType)
                .moduleSubType(moduleSubType)
                .slotIndex(module.getSlotIndex())
                .newLevel(module.getModuleLevel())
                .modulePointRemain(character.getModulePoint())
                .build();

        return response;
    }

    @Transactional
    public ModuleLevelChangeResponse levelDownModule(Long characterId, ModuleLevelChangeRequest request) {
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

        // targetLevel == 0: Lv.1에서 이전 단계 맥스레벨로 강등
        if (request.getTargetLevel() == 0) {
            if (request.getCurrentLevel() != 1) {
                throw new BusinessException(ServerErrorCode.UPGRADE_MODULE_FAIL_MODULE_LEVEL_MISMATCH);
            }

            EModuleSubType prevSubType = EModuleSubType.fromValue(moduleSubType.getValue() - 100);
            if (prevSubType == null) {
                throw new BusinessException(ServerErrorCode.UPGRADE_MODULE_FAIL_MODULE_LEVEL_MISMATCH);
            }

            int maxLevel = gameDataService.getMaxModuleLevel(moduleType, prevSubType);
            // T1 레벨업 비용은 investedModulePoint에 이미 포함 → 리서치 비용만 환급
            int totalRefund = gameDataService.getModuleResearchCost(moduleSubType);
            character.setModulePoint(character.getModulePoint() + totalRefund);
            characterRepository.save(character);

            module.setModuleSubType(prevSubType);
            module.setModuleLevel(maxLevel);
            module.setInvestedModulePoint(Math.max(0, module.getInvestedModulePoint() - totalRefund));
            module.setModified(LocalDateTime.now());
            shipModuleRepository.save(module);

            return ModuleLevelChangeResponse.builder()
                    .shipId(request.getShipId())
                    .bodyIndex(request.getBodyIndex())
                    .moduleType(moduleType)
                    .moduleSubType(prevSubType)
                    .slotIndex(module.getSlotIndex())
                    .newLevel(maxLevel)
                    .modulePointRemain(character.getModulePoint())
                    .investedModulePoint(module.getInvestedModulePoint())
                    .build();
        }

        // 일반 레벨다운 검증
        if (request.getTargetLevel() < 1 || request.getTargetLevel() >= request.getCurrentLevel()) {
            throw new BusinessException(ServerErrorCode.UPGRADE_MODULE_FAIL_MODULE_LEVEL_MISMATCH);
        }

        // 환급 금액 계산 (targetLevel ~ currentLevel-1 구간 합산)
        int totalRefund = 0;
        List<ModuleData> moduleDataList = gameDataService.getModulesByType(moduleType);
        for (int level = request.getTargetLevel(); level < request.getCurrentLevel(); level++) {
            final int nextLevel = level + 1;
            ModuleData levelData = moduleDataList.stream()
                    .filter(data -> data.getModuleLevel() == nextLevel)
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(ServerErrorCode.UPGRADE_MODULE_FAIL_MODULE_DATA_NOT_FOUND));
            totalRefund = totalRefund + levelData.getModulePointCost();
        }

        character.setModulePoint(character.getModulePoint() + totalRefund);
        characterRepository.save(character);

        module.setModuleLevel(request.getTargetLevel());
        module.setInvestedModulePoint(Math.max(0, module.getInvestedModulePoint() - totalRefund));
        module.setModified(LocalDateTime.now());
        shipModuleRepository.save(module);

        return ModuleLevelChangeResponse.builder()
                .shipId(request.getShipId())
                .bodyIndex(request.getBodyIndex())
                .moduleType(moduleType)
                .moduleSubType(moduleSubType)
                .slotIndex(module.getSlotIndex())
                .newLevel(module.getModuleLevel())
                .modulePointRemain(character.getModulePoint())
                .investedModulePoint(module.getInvestedModulePoint())
                .build();
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

        fleet.setFormation(formationType);
        fleet.setModified(LocalDateTime.now());
        fleetRepository.save(fleet);

        return ChangeFormationResponse.builder()
                .formation(formationType)
                .build();
    }

    @Transactional
    public ChangeTacticOptionsResponse changeTacticOptions(Long characterId, ChangeTacticOptionsRequest request) {
        Fleet fleet;

        if (request.getFleetId() == null || request.getFleetId() == 0) {
            fleet = fleetRepository.findByCharacterIdAndIsActiveTrueAndDeletedFalse(characterId)
                    .orElseThrow(() -> new BusinessException(ServerErrorCode.FLEET_NOT_FOUND));
        } else {
            fleet = fleetRepository.findByIdAndCharacterIdAndDeletedFalse(request.getFleetId(), characterId)
                    .orElseThrow(() -> new BusinessException(ServerErrorCode.FLEET_NOT_FOUND));
        }

        fleet.setTacticOptions(request.getTacticOptions());
        fleet.setModified(LocalDateTime.now());
        fleetRepository.save(fleet);

        return ChangeTacticOptionsResponse.builder()
                .tacticOptions(request.getTacticOptions())
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
        int modulePointCost = gameDataService.getDataTableConfig().getModuleUnlockPrice();

        // 자원 부족 검사
        if (character.getModulePoint() < modulePointCost) {
            throw new BusinessException(ServerErrorCode.UNLOCK_MODULE_FAIL_INSUFFICIENT_MINERAL);
        }

        // modulePoint 차감
        int unlockDeducted = deductModulePoint(character, modulePointCost);
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
        newModule.setInvestedModulePoint(unlockDeducted);
        newModule.setDeleted(false);
        newModule.setCreated(LocalDateTime.now());
        newModule.setModified(LocalDateTime.now());
        shipModuleRepository.save(newModule);

        // 응답 생성
        return new ModuleUnlockResponse(
                request.getShipId(),
                request.getBodyIndex(),
                moduleType,
                finalModuleSubType,
                request.getSlotIndex(),
                character.getModulePoint(),
                unlockDeducted
        );
    }

    @Transactional
    public ModuleGradeChangeResponse gradeUpModule(Long characterId, ModuleGradeChangeRequest request) {
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

        // 3. 직접 다음 단계 검증
        if (!gameDataService.isDirectNextStep(currentModuleSubType, newModuleSubType)) {
            throw new BusinessException(ServerErrorCode.CHANGE_MODULE_FAIL_NOT_DIRECT_NEXT_STEP);
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
                    .orElseThrow(() -> new BusinessException(ServerErrorCode.CHANGE_MODULE_FAIL_MODULE_NOT_FOUND));
            levelUpCost += levelData.getModulePointCost();
        }

        // 4. 비용 차감 (레벨업 비용 + 그레이드 업 비용, 비관적 락)
        com.bk.sbs.entity.Character character = characterRepository.findByIdForUpdate(characterId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.CHANGE_MODULE_FAIL_SHIP_NOT_FOUND));

        int gradeUpCost = gameDataService.getModuleResearchCost(newModuleSubType);
        int totalCost = levelUpCost + gradeUpCost;
        if (character.getModulePoint() < totalCost) {
            throw new BusinessException(ServerErrorCode.CHANGE_MODULE_FAIL_INSUFFICIENT_MINERAL_RARE);
        }
        int changeDeducted = deductModulePoint(character, totalCost);
        characterRepository.save(character);

        // 5. 모듈 정보 업데이트 (서브타입 레벨 1로 초기화, 투자 이력 누적)
        currentModule.setModuleSubType(newModuleSubType);
        currentModule.setModuleLevel(1);
        currentModule.setInvestedModulePoint(currentModule.getInvestedModulePoint() + changeDeducted);
        currentModule.setModified(LocalDateTime.now());
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
                .modulePointRemain(character.getModulePoint())
                .investedModulePoint(currentModule.getInvestedModulePoint())
                .build();
    }

    @Transactional
    public ModuleGradeChangeResponse gradeDownModule(Long characterId, ModuleGradeChangeRequest request) {
        Ship ship = shipRepository.findById(request.getShipId())
                .orElseThrow(() -> new BusinessException(ServerErrorCode.CHANGE_MODULE_FAIL_SHIP_NOT_FOUND));
        if (!ship.getFleet().getCharacterId().equals(characterId)) {
            throw new BusinessException(ServerErrorCode.CHANGE_MODULE_FAIL_FLEET_ACCESS_DENIED);
        }

        EModuleType moduleType    = request.getModuleType();
        EModuleSubType currentSubType = request.getModuleSubTypeCurrent();
        EModuleSubType newSubType     = request.getModuleSubTypeNew();

        if (newSubType.getValue() >= currentSubType.getValue()) {
            throw new BusinessException(ServerErrorCode.CHANGE_MODULE_FAIL_SAME_MODULE);
        }

        ShipModule currentModule = shipModuleRepository.findByShipIdAndBodyIndexAndModuleTypeAndSlotIndexAndDeletedFalse(
                request.getShipId(), request.getBodyIndex(), moduleType, request.getSlotIndex()
        ).orElseThrow(() -> new BusinessException(ServerErrorCode.CHANGE_MODULE_FAIL_MODULE_NOT_FOUND));

        com.bk.sbs.entity.Character character = characterRepository.findByIdForUpdate(characterId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.CHANGE_MODULE_FAIL_SHIP_NOT_FOUND));

        // 현재 서브타입 연구 비용 + 현재 레벨업 비용 + 이전 서브타입 레벨업 비용 전체 환급
        int currentLevel = currentModule.getModuleLevel();
        int totalRefund = gameDataService.getModuleResearchCost(currentSubType)
                + calcLevelRefundUpTo(moduleType, currentSubType, currentLevel)
                + calcLevelRefund(moduleType, newSubType);

        // body 다운그레이드 시 사라지는 슬롯(빔/미사일/격납고)의 포인트 환급 + 초기화
        if (moduleType == EModuleType.body) {
            totalRefund += refundAndResetLostSlots(request.getShipId(), request.getBodyIndex(), newSubType);
        }

        character.setModulePoint(character.getModulePoint() + totalRefund);
        characterRepository.save(character);

        // 이전 서브타입으로 복귀, 레벨 1로 초기화
        currentModule.setModuleSubType(newSubType);
        currentModule.setModuleLevel(1);
        currentModule.setInvestedModulePoint(Math.max(0, currentModule.getInvestedModulePoint() - totalRefund));
        currentModule.setModified(LocalDateTime.now());
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
                .modulePointRemain(character.getModulePoint())
                .investedModulePoint(currentModule.getInvestedModulePoint())
                .build();
    }

    // body 다운그레이드 시 새 body가 지원하지 않는 슬롯의 포인트 환급 + 초기화
    private int refundAndResetLostSlots(Long shipId, int bodyIndex, EModuleSubType newBodySubType) {
        List<ModuleSlotInfoDto> newBodySlots = gameDataService.getBodyModuleSlots(newBodySubType);

        // 새 body가 지원하는 슬롯 키 집합 (moduleType_slotIndex)
        java.util.Set<String> supportedKeys = new java.util.HashSet<>();
        if (newBodySlots != null) {
            for (ModuleSlotInfoDto slot : newBodySlots) {
                supportedKeys.add(slot.getModuleType().name() + "_" + slot.getSlotIndex());
            }
        }

        List<ShipModule> allModules = shipModuleRepository.findByShipIdAndBodyIndexAndDeletedFalse(shipId, bodyIndex);
        int refund = 0;
        for (ShipModule module : allModules) {
            if (module.getModuleType() == EModuleType.body) continue;
            if (module.getModuleSubType() == EModuleSubType.none) continue; // 이미 placeholder

            String key = module.getModuleType().name() + "_" + module.getSlotIndex();
            if (!supportedKeys.contains(key)) {
                refund += module.getInvestedModulePoint();
                module.setDeleted(true);
                module.setInvestedModulePoint(0);
                module.setModified(LocalDateTime.now());
                shipModuleRepository.save(module);
            }
        }
        return refund;
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
        if (character.getTechPoint() < researchCost)
            throw new BusinessException(ServerErrorCode.RESEARCH_MODULE_FAIL_INSUFFICIENT_MINERAL);

        // techPoint 차감
        int researchDeducted = deductTechPoint(character, researchCost);
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
        return TechLevelResearchResponse.builder()
                .techPointRemain(character.getTechPoint())
                .researchedIds(researchedIds)
                .build();
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
        character.setModulePoint(character.getModulePoint() + module.getInvestedModulePoint());
        characterRepository.save(character);

        // 모듈 soft-delete (언락된 슬롯을 플레이스홀더 상태로 복귀)
        module.setDeleted(true);
        module.setModified(LocalDateTime.now());
        shipModuleRepository.save(module);

        return ModuleResetResponse.builder()
                .shipId(request.getShipId())
                .bodyIndex(request.getBodyIndex())
                .moduleType(request.getModuleType())
                .slotIndex(request.getSlotIndex())
                .modulePointRemain(character.getModulePoint())
                .build();
    }

    // 기함 body 리셋 — T1 레벨1로 되돌리고 투자 이력 환급 (soft-delete 없이 업데이트)
    private ModuleResetResponse resetFlagshipBody(Ship ship, Long characterId, ModuleResetRequest request) {
        ShipModule body = shipModuleRepository.findByShipIdAndBodyIndexAndModuleTypeAndSlotIndexAndDeletedFalse(
                ship.getId(), request.getBodyIndex(), EModuleType.body, 0
        ).orElseThrow(() -> new BusinessException(ServerErrorCode.RESET_MODULE_FAIL_MODULE_NOT_FOUND));

        com.bk.sbs.entity.Character character = characterRepository.findByIdForUpdate(characterId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.RESET_MODULE_FAIL_CHARACTER_NOT_FOUND));

        // T1 레벨1 body가 지원하는 슬롯 목록 조회
        List<ModuleData> bodyModules = gameDataService.getModulesByType(EModuleType.body);
        List<ModuleSlotInfoDto> t1Slots = bodyModules.stream()
                .filter(m -> EModuleSubType.body_t1_m1.equals(m.getModuleSubType()) && Integer.valueOf(1).equals(m.getModuleLevel()))
                .findFirst()
                .map(ModuleData::getModuleSlots)
                .orElse(java.util.Collections.emptyList());

        // body에 속한 자식 모듈 전체 조회 (beam/missile/hanger)
        List<ShipModule> childModules = shipModuleRepository.findByShipIdAndBodyIndexAndDeletedFalse(
                ship.getId(), request.getBodyIndex());

        // T1에서 지원되지 않는 슬롯의 자식 모듈: 환급 + soft-delete
        int totalRefund = body.getInvestedModulePoint();
        for (ShipModule child : childModules) {
            if (child.getModuleType() == EModuleType.body) continue;
            boolean supported = t1Slots.stream().anyMatch(s ->
                    s.getModuleType() == child.getModuleType() && s.getSlotIndex().equals(child.getSlotIndex()));
            if (!supported) {
                totalRefund += child.getInvestedModulePoint();
                child.setDeleted(true);
                child.setModified(LocalDateTime.now());
                shipModuleRepository.save(child);
            }
        }
        character.setModulePoint(character.getModulePoint() + totalRefund);
        characterRepository.save(character);

        // body T1 레벨1로 복귀 (삭제 없이 값만 초기화)
        body.setModuleSubType(EModuleSubType.body_t1_m1);
        body.setModuleLevel(1);
        body.setInvestedModulePoint(0);
        body.setModified(LocalDateTime.now());
        shipModuleRepository.save(body);

        return ModuleResetResponse.builder()
                .shipId(ship.getId())
                .bodyIndex(request.getBodyIndex())
                .moduleType(EModuleType.body)
                .slotIndex(0)
                .modulePointRemain(character.getModulePoint())
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
        int refundMp = 0;
        for (ShipModule mod : allModules) {
            refundMp += mod.getInvestedModulePoint();
        }
        character.setModulePoint(character.getModulePoint() + refundMp);
        characterRepository.save(character);

        // 모듈 soft delete
        for (ShipModule mod : allModules) {
            mod.setDeleted(true);
            mod.setModified(LocalDateTime.now());
            shipModuleRepository.save(mod);
        }

        // 함선 soft delete
        ship.setDeleted(true);
        ship.setModified(LocalDateTime.now());
        shipRepository.save(ship);

        return ShipResetRemoveResponse.builder()
                .removedShipId(request.getShipId())
                .modulePointRemain(character.getModulePoint())
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

    private int deductModulePoint(com.bk.sbs.entity.Character character, int cost) {
        character.setModulePoint(character.getModulePoint() - cost);
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

    private int deductTechPoint(com.bk.sbs.entity.Character character, int cost) {
        character.setTechPoint(character.getTechPoint() - cost);
        return cost;
    }


    @Transactional
    public FleetInstantRepairResponse instantRepairFleet(Long characterId) {
        Character character = characterRepository.findByIdForUpdate(characterId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.FLEET_INSTANT_REPAIR_FAIL_CHARACTER_NOT_FOUND));

        Fleet fleet = fleetRepository.findByCharacterIdAndIsActiveTrueAndDeletedFalse(characterId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.FLEET_INSTANT_REPAIR_FAIL_FLEET_NOT_FOUND));

        List<Ship> ships = shipRepository.findByFleetIdAndDeletedFalseOrderByPositionIndex(fleet.getId());
        List<ModuleData> bodyDataList = gameDataService.getModulesByType(EModuleType.body);

        int cost = gameDataService.getBattleRepairMineralPerSec() * gameDataService.getInstantRepairBaseSecs();
        if (character.getMineral() < cost)
            throw new BusinessException(ServerErrorCode.FLEET_INSTANT_REPAIR_FAIL_INSUFFICIENT_MINERAL);

        character.setMineral(character.getMineral() - cost);
        characterRepository.save(character);

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
        response.setMineralRemain(character.getMineral());
        return response;
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
