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
        ModuleData engineData = gameDataService.getFirstModuleByType(EModuleType.engine);
        ModuleData beamData = gameDataService.getFirstModuleByType(EModuleType.beam);
        ModuleData missileData = gameDataService.getFirstModuleByType(EModuleType.missile);
        ModuleData hangerData = gameDataService.getFirstModuleByType(EModuleType.hanger);

        // 1. Body
        ShipModule bodyModule = new ShipModule();
        bodyModule.setShip(defaultShip);
        bodyModule.setModuleType(EModuleType.body);
        bodyModule.setModuleSubType(EModuleSubType.body_battle);
        //bodyModule.setModuleSubType(EModuleSubType.Body_Aircraft);
        bodyModule.setModuleLevel(bodyData.getModuleLevel());
        bodyModule.setBodyIndex(0);
        bodyModule.setSlotIndex(0);
        shipModuleRepository.save(bodyModule);

        // 2. Engine
        ShipModule engineModule = new ShipModule();
        engineModule.setShip(defaultShip);
        engineModule.setModuleType(EModuleType.engine);
        engineModule.setModuleSubType(EModuleSubType.engine_standard);
        engineModule.setModuleLevel(engineData.getModuleLevel());
        engineModule.setBodyIndex(0);
        engineModule.setSlotIndex(0);
        shipModuleRepository.save(engineModule);

//        // Beam
//        ShipModule beamModule = new ShipModule();
//        beamModule.setShip(defaultShip);
//        beamModule.setModuleType(EModuleType.Beam);
//        beamModule.setModuleSubType(EModuleSubType.Beam_Standard);
//        beamModule.setModuleLevel(beamData.getModuleLevel());
//        beamModule.setBodyIndex(0);
//        beamModule.setSlotIndex(0);
//        shipModuleRepository.save(beamModule);

//        // Missile
//        ShipModule missileModule = new ShipModule();
//        missileModule.setShip(defaultShip);
//        missileModule.setModuleType(EModuleType.Missile);
//        missileModule.setModuleSubType(EModuleSubType.Missile_Standard);
//        missileModule.setModuleLevel(missileData.getModuleLevel());
//        missileModule.setBodyIndex(0);
//        missileModule.setSlotIndex(0);
//        shipModuleRepository.save(missileModule);

//        // Hanger 모듈 (type 4)
//        ShipModule hangerModule = new ShipModule();
//        hangerModule.setShip(defaultShip);
//        hangerModule.setModuleType(EModuleType.Hanger);
//        hangerModule.setModuleSubType(EModuleSubType.Hanger_Standard);
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
                .characterId(fleet.getCharacterId())
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

    private List<ModuleBodyInfoDto> convertToBodyModules(List<ShipModule> modules) {
        return modules.stream()
                .filter(m -> m.getModuleType() == EModuleType.body)
                .map(bodyModule -> {
                    ModuleBodyInfoDto bodyDto = ModuleBodyInfoDto.builder()
                            .moduleType(bodyModule.getModuleType())
                            .moduleSubType(bodyModule.getModuleSubType())
                            .moduleLevel(bodyModule.getModuleLevel())
                            .bodyIndex(bodyModule.getBodyIndex())
                            .build();

                    int bodyIndex = bodyModule.getBodyIndex();
                    List<ModuleInfoDto> engines = modules.stream()
                            .filter(m -> m.getModuleType() == EModuleType.engine && m.getBodyIndex() == bodyIndex)
                            .map(engineModule -> {
                                ModuleInfoDto engineDto = ModuleInfoDto.builder()
                                        .moduleType(engineModule.getModuleType())
                                        .moduleSubType(engineModule.getModuleSubType())
                                        .moduleLevel(engineModule.getModuleLevel())
                                        .bodyIndex(engineModule.getBodyIndex())
                                        .slotIndex(engineModule.getSlotIndex())
                                        .build();
                                return engineDto;
                            })
                            .collect(Collectors.toList());

                    List<ModuleInfoDto> beams = modules.stream()
                            .filter(m -> m.getModuleType() == EModuleType.beam && m.getBodyIndex() == bodyIndex)
                            .map(beamModule -> {
                                ModuleInfoDto beamDto = ModuleInfoDto.builder()
                                        .moduleType(beamModule.getModuleType())
                                        .moduleSubType(beamModule.getModuleSubType())
                                        .moduleLevel(beamModule.getModuleLevel())
                                        .bodyIndex(beamModule.getBodyIndex())
                                        .slotIndex(beamModule.getSlotIndex())
                                        .build();
                                return beamDto;
                            })
                            .collect(Collectors.toList());

                    List<ModuleInfoDto> missiles = modules.stream()
                            .filter(m -> m.getModuleType() == EModuleType.missile && m.getBodyIndex() == bodyIndex)
                            .map(missileModule -> {
                                ModuleInfoDto missileDto = ModuleInfoDto.builder()
                                        .moduleType(missileModule.getModuleType())
                                        .moduleSubType(missileModule.getModuleSubType())
                                        .moduleLevel(missileModule.getModuleLevel())
                                        .bodyIndex(missileModule.getBodyIndex())
                                        .slotIndex(missileModule.getSlotIndex())
                                        .build();
                                return missileDto;
                            })
                            .collect(Collectors.toList());

                    List<ModuleInfoDto> hangers = modules.stream()
                            .filter(m -> m.getModuleType() == EModuleType.hanger && m.getBodyIndex() == bodyIndex)
                            .map(hangerModule -> {
                                ModuleInfoDto hangerDto = ModuleInfoDto.builder()
                                        .moduleType(hangerModule.getModuleType())
                                        .moduleSubType(hangerModule.getModuleSubType())
                                        .moduleLevel(hangerModule.getModuleLevel())
                                        .bodyIndex(hangerModule.getBodyIndex())
                                        .slotIndex(hangerModule.getSlotIndex())
                                        .build();
                                return hangerDto;
                            })
                            .collect(Collectors.toList());

                    bodyDto.setEngines(engines);
                    bodyDto.setBeams(beams);
                    bodyDto.setMissiles(missiles);
                    bodyDto.setHangers(hangers);

                    return bodyDto;
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
        CostStructDto shipAddCost = gameDataService.getShipAddCost(currentShips.size());

        // TechLevel 검증
        if (character.getTechLevel() < shipAddCost.getTechLevel()) {
            throw new BusinessException(ServerErrorCode.ADD_SHIP_FAIL_INSUFFICIENT_TECH_LEVEL);
        }

        // 자원 부족 검사
        if (character.getMineral() < shipAddCost.getMineral()) {
            throw new BusinessException(ServerErrorCode.ADD_SHIP_FAIL_INSUFFICIENT_MINERAL);
        }
        if (character.getMineralRare() < shipAddCost.getMineralRare()) {
            throw new BusinessException(ServerErrorCode.ADD_SHIP_FAIL_INSUFFICIENT_MINERAL_RARE);
        }
        if (character.getMineralExotic() < shipAddCost.getMineralExotic()) {
            throw new BusinessException(ServerErrorCode.ADD_SHIP_FAIL_INSUFFICIENT_MINERAL_EXOTIC);
        }
        if (character.getMineralDark() < shipAddCost.getMineralDark()) {
            throw new BusinessException(ServerErrorCode.ADD_SHIP_FAIL_INSUFFICIENT_MINERAL_DARK);
        }

        // 자원 차감
        character.setMineral(character.getMineral() - shipAddCost.getMineral());
        character.setMineralRare(character.getMineralRare() - shipAddCost.getMineralRare());
        character.setMineralExotic(character.getMineralExotic() - shipAddCost.getMineralExotic());
        character.setMineralDark(character.getMineralDark() - shipAddCost.getMineralDark());
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

        // 기본 모듈들 생성 (Body, Weapon, Engine)
        createDefaultModules(savedShip);

        // 비용 정보 (모든 미네랄 타입 포함)
        CostRemainInfoDto costRemainInfo = new CostRemainInfoDto(
                shipAddCost.getMineral(),
                shipAddCost.getMineralRare(),
                shipAddCost.getMineralExotic(),
                shipAddCost.getMineralDark(),
                character.getMineral(),
                character.getMineralRare(),
                character.getMineralExotic(),
                character.getMineralDark()
        );

        // 응답 생성
        AddShipResponse response = AddShipResponse.builder()
                .newShipInfo(convertShipToShipInfoDto(savedShip))
                .costRemainInfo(costRemainInfo)
                .updatedFleetInfo(convertToDetailDto(targetFleet))
                .build();

        return response;
    }

    private void createDefaultModules(Ship ship) {
        // Body 모듈
        ShipModule bodyModule = new ShipModule();
        bodyModule.setShip(ship);
        bodyModule.setModuleType(EModuleType.body);
        bodyModule.setModuleSubType(EModuleSubType.body_battle);
        bodyModule.setModuleLevel(1);
        bodyModule.setBodyIndex(0);
        bodyModule.setSlotIndex(0);
        bodyModule.setDeleted(false);
        bodyModule.setCreated(LocalDateTime.now());
        bodyModule.setModified(LocalDateTime.now());
        shipModuleRepository.save(bodyModule);

        // Engine 모듈
        ShipModule engineModule = new ShipModule();
        engineModule.setShip(ship);
        engineModule.setModuleType(EModuleType.engine);
        engineModule.setModuleSubType(EModuleSubType.engine_standard);
        engineModule.setModuleLevel(1);
        engineModule.setBodyIndex(0);
        engineModule.setSlotIndex(0);
        engineModule.setDeleted(false);
        engineModule.setCreated(LocalDateTime.now());
        engineModule.setModified(LocalDateTime.now());
        shipModuleRepository.save(engineModule);

        // Beam 모듈
        ShipModule weaponModule = new ShipModule();
        weaponModule.setShip(ship);
        weaponModule.setModuleType(EModuleType.beam);
        weaponModule.setModuleSubType(EModuleSubType.beam_standard);
        weaponModule.setModuleLevel(1);
        weaponModule.setBodyIndex(0);
        weaponModule.setSlotIndex(0);
        weaponModule.setDeleted(false);
        weaponModule.setCreated(LocalDateTime.now());
        weaponModule.setModified(LocalDateTime.now());
        shipModuleRepository.save(weaponModule);
    }

    @Transactional
    public ModuleUpgradeResponse upgradeModule(Long characterId, ModuleUpgradeRequest request) {
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

        // 업그레이드 비용 계산 (현재 레벨부터 목표 레벨까지)
        CostStructDto totalCost = new CostStructDto(0, 0L, 0L, 0L, 0L);
        int maxTechLevel = 0;

        List<ModuleData> moduleDataList = gameDataService.getModulesByType(moduleType);
        for (int level = request.getCurrentLevel(); level < request.getTargetLevel(); level++) {
            final int currentLevel = level;
            ModuleData levelData = moduleDataList.stream()
                    .filter(data -> data.getModuleLevel() == currentLevel)
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(ServerErrorCode.UPGRADE_MODULE_FAIL_MODULE_DATA_NOT_FOUND));

            CostStructDto cost = levelData.getUpgradeCost();
            if (cost != null) {
                maxTechLevel = Math.max(maxTechLevel, cost.getTechLevel());
                totalCost.setMineral(totalCost.getMineral() + cost.getMineral());
                totalCost.setMineralRare(totalCost.getMineralRare() + cost.getMineralRare());
                totalCost.setMineralExotic(totalCost.getMineralExotic() + cost.getMineralExotic());
                totalCost.setMineralDark(totalCost.getMineralDark() + cost.getMineralDark());
            }
        }

        // TechLevel 검증
        if (character.getTechLevel() < maxTechLevel) {
            throw new BusinessException(ServerErrorCode.UPGRADE_MODULE_FAIL_INSUFFICIENT_TECH_LEVEL);
        }

        // 자원 부족 검사 (업그레이드 진행 전에 먼저 체크)
        if (character.getMineral() < totalCost.getMineral()) {
            throw new BusinessException(ServerErrorCode.UPGRADE_MODULE_FAIL_INSUFFICIENT_MINERAL);
        }
        if (character.getMineralRare() < totalCost.getMineralRare()) {
            throw new BusinessException(ServerErrorCode.UPGRADE_MODULE_FAIL_INSUFFICIENT_MINERAL_RARE);
        }
        if (character.getMineralExotic() < totalCost.getMineralExotic()) {
            throw new BusinessException(ServerErrorCode.UPGRADE_MODULE_FAIL_INSUFFICIENT_MINERAL_EXOTIC);
        }
        if (character.getMineralDark() < totalCost.getMineralDark()) {
            throw new BusinessException(ServerErrorCode.UPGRADE_MODULE_FAIL_INSUFFICIENT_MINERAL_DARK);
        }

        // 자원 차감 (업그레이드 진행 전에 먼저 차감)
        character.setMineral(character.getMineral() - totalCost.getMineral());
        character.setMineralRare(character.getMineralRare() - totalCost.getMineralRare());
        character.setMineralExotic(character.getMineralExotic() - totalCost.getMineralExotic());
        character.setMineralDark(character.getMineralDark() - totalCost.getMineralDark());
        characterRepository.save(character);

        // 모듈 레벨 업데이트 (능력치는 클라이언트가 DataTable에서 조회)
        module.setModuleLevel(request.getTargetLevel());
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

        // 비용 정보 (모든 미네랄 타입 포함)
        CostRemainInfoDto costRemainInfo = new CostRemainInfoDto(
                totalCost.getMineral(),
                totalCost.getMineralRare(),
                totalCost.getMineralExotic(),
                totalCost.getMineralDark(),
                character.getMineral(),
                character.getMineralRare(),
                character.getMineralExotic(),
                character.getMineralDark()
        );

        // 응답 생성
        ModuleUpgradeResponse response = ModuleUpgradeResponse.builder()
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
        long mineralCost = gameDataService.getDataTableConfig().getModuleUnlockPrice();


        // 자원 부족 검사
        if (character.getMineral() < mineralCost) {
            throw new BusinessException(ServerErrorCode.UNLOCK_MODULE_FAIL_INSUFFICIENT_MINERAL);
        }

        // 자원 차감
        character.setMineral(character.getMineral() - mineralCost);
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
        int defaultSubTypeValue = moduleType.getValue() * 1000 + 1;
        EModuleSubType finalModuleSubType = EModuleSubType.fromValue(defaultSubTypeValue);


        // 새로운 모듈 레코드 생성
        ShipModule newModule = new ShipModule();
        newModule.setShip(ship);
        newModule.setBodyIndex(request.getBodyIndex());
        newModule.setSlotIndex(request.getSlotIndex());
        newModule.setModuleType(moduleType);
        newModule.setModuleSubType(finalModuleSubType);
        newModule.setModuleLevel(1);
        newModule.setDeleted(false);
        newModule.setCreated(LocalDateTime.now());
        newModule.setModified(LocalDateTime.now());
        shipModuleRepository.save(newModule);

        // 비용 정보
        CostRemainInfoDto costRemainInfo = new CostRemainInfoDto(
                mineralCost,
                0L,
                0L,
                0L,
                character.getMineral(),
                character.getMineralRare(),
                character.getMineralExotic(),
                character.getMineralDark()
        );

        // 응답 생성
        return new ModuleUnlockResponse(
                request.getShipId(),
                request.getBodyIndex(),
                moduleType,
                finalModuleSubType,
                request.getSlotIndex(),
                costRemainInfo
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

        // 2. 모듈 타입이 다르면 에러 (Weapon->Weapon, Engine->Engine 만 가능)
        if (currentModuleType != newModuleType) {
            throw new BusinessException(ServerErrorCode.CHANGE_MODULE_FAIL_NOT_MATCH_MODULE_TYPE);
        }

        // 3. 새 모듈이 연구되었는지 확인 (slotType 제외, moduleType + moduleSubType 만으로 체크)
        Optional<ModuleResearch> researchCheck = moduleResearchRepository.findByCharacterIdAndModuleTypeAndModuleSubType(
                characterId,
                newModuleType,
                newModuleSubType
        );

        if (!researchCheck.isPresent() || !researchCheck.get().isResearched()) {
            throw new BusinessException(ServerErrorCode.CHANGE_MODULE_FAIL_NOT_RESEARCHED);
        }

        // 현재 장착된 모듈 찾기
        ShipModule currentModule = shipModuleRepository.findByShipIdAndBodyIndexAndModuleTypeAndSlotIndexAndDeletedFalse(
                request.getShipId(),
                request.getBodyIndex(),
                currentModuleType,
                request.getSlotIndex()
        ).orElseThrow(() -> new BusinessException(ServerErrorCode.CHANGE_MODULE_FAIL_MODULE_NOT_FOUND));

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
        int newModuleLevel = shipModuleLevelRepository.findByShipIdAndBodyIndexAndModuleTypeAndSlotIndexAndModuleSubType(
                request.getShipId(),
                request.getBodyIndex(),
                newModuleType,
                request.getSlotIndex(),
                newModuleSubType
        ).map(ShipModuleLevel::getLevel).orElse(1);

        // 3. 모듈 정보 업데이트 (서브타입 + 레벨 변경)
        currentModule.setModuleSubType(newModuleSubType);
        currentModule.setModuleLevel(newModuleLevel);
        currentModule.setModified(LocalDateTime.now());
        shipModuleRepository.save(currentModule);

        // 응답 생성
        return ModuleChangeResponse.builder()
                .shipId(request.getShipId())
                .bodyIndex(request.getBodyIndex())
                .moduleTypeCurrent(currentModuleType)
                .moduleSubTypeCurrent(currentModuleSubType)
                .moduleTypeNew(newModuleType)
                .moduleSubTypeNew(newModuleSubType)
                .slotIndex(request.getSlotIndex())
                .moduleNewLevel(newModuleLevel)
                .build();
    }

    @Transactional
    public ModuleResearchResponse researchModule(Long characterId, ModuleResearchRequest request) {
        EModuleType moduleType = request.getModuleType();
        EModuleSubType moduleSubType = request.getModuleSubType();

        // 이미 개발되었는지 확인 (moduleType + moduleSubType만으로 체크)
        Optional<ModuleResearch> existing = moduleResearchRepository.findByCharacterIdAndModuleTypeAndModuleSubType(
                characterId,
                moduleType,
                moduleSubType
        );

        if (existing.isPresent() && existing.get().isResearched()) {
            throw new BusinessException(ServerErrorCode.RESEARCH_MODULE_FAIL_ALREADY_RESEARCHED);
        }

        // 캐릭터 자원 조회 (비관적 락)
        com.bk.sbs.entity.Character character = characterRepository.findByIdForUpdate(characterId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.RESEARCH_MODULE_FAIL_CHARACTER_NOT_FOUND));

        // 모듈 개발 비용 가져오기 (DataTableResearch.json에서 로딩)
        CostStructDto researchCost = gameDataService.getModuleResearchCost(moduleSubType);

        // TechLevel 검증
        if (character.getTechLevel() < researchCost.getTechLevel()) {
            throw new BusinessException(ServerErrorCode.RESEARCH_MODULE_FAIL_INSUFFICIENT_TECH_LEVEL);
        }

        // 자원 부족 검사
        if (character.getMineral() < researchCost.getMineral()) {
            throw new BusinessException(ServerErrorCode.RESEARCH_MODULE_FAIL_INSUFFICIENT_MINERAL);
        }
        if (character.getMineralRare() < researchCost.getMineralRare()) {
            throw new BusinessException(ServerErrorCode.RESEARCH_MODULE_FAIL_INSUFFICIENT_MINERAL_RARE);
        }
        if (character.getMineralExotic() < researchCost.getMineralExotic()) {
            throw new BusinessException(ServerErrorCode.RESEARCH_MODULE_FAIL_INSUFFICIENT_MINERAL_EXOTIC);
        }
        if (character.getMineralDark() < researchCost.getMineralDark()) {
            throw new BusinessException(ServerErrorCode.RESEARCH_MODULE_FAIL_INSUFFICIENT_MINERAL_DARK);
        }

        // 자원 차감
        character.setMineral(character.getMineral() - researchCost.getMineral());
        character.setMineralRare(character.getMineralRare() - researchCost.getMineralRare());
        character.setMineralExotic(character.getMineralExotic() - researchCost.getMineralExotic());
        character.setMineralDark(character.getMineralDark() - researchCost.getMineralDark());
        characterRepository.save(character);

        // 모듈 개발 정보 저장 또는 업데이트
        ModuleResearch moduleResearch;
        if (existing.isPresent()) {
            moduleResearch = existing.get();
            moduleResearch.setResearched(true);
            moduleResearch.setModified(LocalDateTime.now());
        } else {
            moduleResearch = new ModuleResearch();
            moduleResearch.setCharacterId(characterId);
            moduleResearch.setModuleType(moduleType);
            moduleResearch.setModuleSubType(moduleSubType);
            moduleResearch.setResearched(true);
        }
        moduleResearchRepository.save(moduleResearch);

        // 개발된 모든 모듈 목록 조회
        List<ModuleResearch> researchedList = moduleResearchRepository.findByCharacterIdAndResearchedTrue(characterId);
        List<List<Integer>> researchedModuleTypes = researchedList.stream()
                .map(r -> List.of(r.getModuleType().getValue(), r.getModuleSubType().getValue()))
                .collect(Collectors.toList());

        // 비용 정보
        CostRemainInfoDto costRemainInfo = new CostRemainInfoDto(
                researchCost.getMineral(),
                researchCost.getMineralRare(),
                researchCost.getMineralExotic(),
                researchCost.getMineralDark(),
                character.getMineral(),
                character.getMineralRare(),
                character.getMineralExotic(),
                character.getMineralDark()
        );

        // 응답 생성
        return new ModuleResearchResponse(
                moduleType,
                moduleSubType,
                costRemainInfo,
                researchedModuleTypes
        );
    }


    //캐릭터가 개발한 모든 모듈 목록 조회
    public List<List<Integer>> getResearchedModuleTypes(Long characterId) {
        List<ModuleResearch> researchedList = moduleResearchRepository.findByCharacterIdAndResearchedTrue(characterId);
        return researchedList.stream()
                .map(r -> List.of(r.getModuleType().getValue(), r.getModuleSubType().getValue()))
                .collect(Collectors.toList());
    }
}
