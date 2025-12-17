package com.bk.sbs.service;

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
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class FleetService {

    private final FleetRepository fleetRepository;
    private final ShipRepository shipRepository;
    private final ShipModuleRepository shipModuleRepository;
    private final CharacterRepository characterRepository;
    private final GameDataService gameDataService;

    public FleetService(FleetRepository fleetRepository, ShipRepository shipRepository,
                       ShipModuleRepository shipModuleRepository, CharacterRepository characterRepository,
                       GameDataService gameDataService) {
        this.fleetRepository = fleetRepository;
        this.shipRepository = shipRepository;
        this.shipModuleRepository = shipModuleRepository;
        this.characterRepository = characterRepository;
        this.gameDataService = gameDataService;
    }

    // 캐릭터의 모든 함대 조회
    public List<FleetDto> getUserFleets(Long characterId) {
        List<Fleet> fleets = fleetRepository.findByCharacterIdOrderByActiveAndModified(characterId);
        return fleets.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    // 특정 함대 상세 조회
    public FleetDto getFleetDetail(Long characterId, Long fleetId) {
        Fleet fleet = fleetRepository.findByIdAndCharacterIdAndDeletedFalse(fleetId, characterId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.FLEET_NOT_FOUND));
        
        return convertToDetailDto(fleet);
    }

    // 활성 함대 조회
    public FleetDto getActiveFleet(Long characterId) {
        Fleet fleet = fleetRepository.findByCharacterIdAndIsActiveTrueAndDeletedFalse(characterId)
                .orElse(null);
        
        return fleet != null ? convertToDetailDto(fleet) : null;
    }

    // 함대 생성 (기본 함선과 모듈 포함)
    @Transactional
    public FleetDto createFleet(Long characterId, String fleetName, String description) {
        if (fleetRepository.existsByCharacterIdAndFleetNameAndDeletedFalse(characterId, fleetName)) {
            throw new BusinessException(ServerErrorCode.FLEET_DUPLICATE_NAME);
        }

        Fleet fleet = new Fleet();
        fleet.setCharacterId(characterId);
        fleet.setFleetName(fleetName);
        fleet.setDescription(description);
        fleet.setActive(false); // 기본값: 비활성
        fleet.setFormation(EFormationType.LinearHorizontal);
        
        fleet = fleetRepository.save(fleet);
        
        // 기본 함선 생성
        createDefaultShipsForFleet(fleet);
        
        return convertToDetailDto(fleet);
    }
    
    // 기본 함선과 모듈들을 생성하는 메서드
    private void createDefaultShipsForFleet(Fleet fleet) {
        // 기본 함선 1개 생성
        Ship defaultShip = new Ship();
        defaultShip.setFleet(fleet);
        defaultShip.setShipName("Ship_" + 1);
        defaultShip.setPositionIndex(0); // 첫 번째 위치
        defaultShip.setDescription("Auto-generated default ship.");
        defaultShip = shipRepository.save(defaultShip);

        // GameDataService에서 레벨 1 모듈 데이터 가져오기
        ModuleBodyDataDto bodyData = gameDataService.getFirstBodyModule();
        ModuleWeaponDataDto weaponData = gameDataService.getFirstWeaponModule();
        ModuleEngineDataDto engineData = gameDataService.getFirstEngineModule();
        ModuleHangerDataDto hangerData = gameDataService.getFirstHangerModule();

        // 1. Body 모듈 (타입 1)
        ShipModule bodyModule = new ShipModule();
        bodyModule.setShip(defaultShip);
        bodyModule.setModuleType(EModuleType.Body);
        bodyModule.setModuleSubType(EModuleBodySubType.Battle);
        //bodyModule.setModuleSubType(EModuleBodySubType.Aircraft);
        bodyModule.setModuleStyle(EModuleStyle.StyleA);
        bodyModule.setModuleLevel(bodyData.getLevel());
        bodyModule.setBodyIndex(0);
        bodyModule.setSlotIndex(0);
        shipModuleRepository.save(bodyModule);

        // 2. Engine 모듈 (타입 2)
        ShipModule engineModule = new ShipModule();
        engineModule.setShip(defaultShip);
        engineModule.setModuleType(EModuleType.Engine);
        engineModule.setModuleSubType(EModuleEngineSubType.Standard);
        engineModule.setModuleStyle(EModuleStyle.StyleA);
        engineModule.setModuleLevel(engineData.getLevel());
        engineModule.setBodyIndex(0);
        engineModule.setSlotIndex(0);
        shipModuleRepository.save(engineModule);

        // 3. Weapon 모듈 (타입 3)
        ShipModule weaponModule = new ShipModule();
        weaponModule.setShip(defaultShip);
        weaponModule.setModuleType(EModuleType.Weapon);
        //weaponModule.setModuleSubType(EModuleWeaponSubType.Beam);
        weaponModule.setModuleSubType(EModuleWeaponSubType.Missile);
        weaponModule.setModuleStyle(EModuleStyle.StyleA);
        weaponModule.setModuleLevel(weaponData.getLevel());
        weaponModule.setBodyIndex(0);
        weaponModule.setSlotIndex(0);
        shipModuleRepository.save(weaponModule);

        // 4. Hanger 모듈 (타입 4)
        ShipModule hangerModule = new ShipModule();
        hangerModule.setShip(defaultShip);
        hangerModule.setModuleType(EModuleType.Hanger);
        hangerModule.setModuleSubType(EModuleHangerSubType.Standard);
        hangerModule.setModuleStyle(EModuleStyle.StyleA);
        hangerModule.setModuleLevel(hangerData.getLevel());
        hangerModule.setBodyIndex(0);
        hangerModule.setSlotIndex(0);
        shipModuleRepository.save(hangerModule);


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

    // 클라이언트 데이터 가져오기 (Export)
    @Transactional(readOnly = true)
    public FleetExportResponse exportFleet(Long characterId, Long fleetId) {
        Fleet fleet = fleetRepository.findByIdAndCharacterIdAndDeletedFalse(fleetId, characterId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.FLEET_NOT_FOUND));

        FleetExportResponse response = new FleetExportResponse();
        response.setFleetName(fleet.getFleetName());
        response.setDescription(fleet.getDescription());
        response.setActive(fleet.isActive());

        List<Ship> ships = shipRepository.findByFleetIdAndDeletedFalseOrderByPositionIndex(fleetId);
        List<FleetExportResponse.ShipExportData> shipData = ships.stream()
                .map(ship -> {
                    FleetExportResponse.ShipExportData shipExport = new FleetExportResponse.ShipExportData();
                    shipExport.setShipName(ship.getShipName());
                    shipExport.setPositionIndex(ship.getPositionIndex());
                    shipExport.setDescription(ship.getDescription());

                    List<ShipModule> modules = shipModuleRepository.findByShipIdAndDeletedFalseOrderBySlotIndex(ship.getId());
                    List<FleetExportResponse.ShipModuleExportData> moduleData = modules.stream()
                            .map(module -> {
                                FleetExportResponse.ShipModuleExportData moduleExport = new FleetExportResponse.ShipModuleExportData();
                                moduleExport.setModuleType(module.getModuleType());
                                moduleExport.setModuleLevel(module.getModuleLevel());
                                moduleExport.setSlotIndex(module.getSlotIndex());
                                return moduleExport;
                            })
                            .collect(Collectors.toList());
                    shipExport.setModules(moduleData);
                    return shipExport;
                })
                .collect(Collectors.toList());
        response.setShips(shipData);

        return response;
    }

    // 클라이언트 데이터 저장 (Import)
    @Transactional
    public FleetDto importFleet(Long characterId, FleetImportRequest request) {
        // 기존 함대명 중복 체크
        if (fleetRepository.existsByCharacterIdAndFleetNameAndDeletedFalse(characterId, request.getFleetName())) {
            throw new BusinessException(ServerErrorCode.FLEET_DUPLICATE_NAME);
        }

        // 함대 생성
        Fleet fleet = new Fleet();
        fleet.setCharacterId(characterId);
        fleet.setFleetName(request.getFleetName());
        fleet.setDescription(request.getDescription());
        fleet.setActive(request.isActive());
        
        // 활성 함대가 이미 있다면 비활성화
        if (request.isActive()) {
            fleetRepository.findByCharacterIdAndIsActiveTrueAndDeletedFalse(characterId)
                    .ifPresent(activeFleet -> {
                        activeFleet.setActive(false);
                        activeFleet.setModified(LocalDateTime.now());
                        fleetRepository.save(activeFleet);
                    });
        }
        
        fleet = fleetRepository.save(fleet);

        // 함선들 생성
        if (request.getShips() != null) {
            for (FleetImportRequest.ShipImportData shipData : request.getShips()) {
                Ship ship = new Ship();
                ship.setFleet(fleet);
                ship.setShipName(shipData.getShipName());
                ship.setPositionIndex(shipData.getPositionIndex());
                ship.setDescription(shipData.getDescription());
                ship = shipRepository.save(ship);

                // 모듈들 생성
                if (shipData.getModules() != null) {
                    for (FleetImportRequest.ShipModuleImportData moduleData : shipData.getModules()) {
                        ShipModule module = new ShipModule();
                        module.setShip(ship);
                        module.setModuleType(moduleData.getModuleType());
                        module.setModuleLevel(moduleData.getModuleLevel());
                        module.setSlotIndex(moduleData.getSlotIndex());
                        shipModuleRepository.save(module);
                    }
                }
            }
        }

        return convertToDetailDto(fleet);
    }

    // 함대 업데이트
    @Transactional
    public FleetDto updateFleet(Long characterId, Long fleetId, FleetImportRequest request) {
        Fleet fleet = fleetRepository.findByIdAndCharacterIdAndDeletedFalse(fleetId, characterId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.FLEET_NOT_FOUND));

        // 함대명 변경 시 중복 체크
        if (!fleet.getFleetName().equals(request.getFleetName()) && 
            fleetRepository.existsByCharacterIdAndFleetNameAndDeletedFalse(characterId, request.getFleetName())) {
            throw new BusinessException(ServerErrorCode.FLEET_DUPLICATE_NAME);
        }

        // 함대 정보 업데이트
        fleet.setFleetName(request.getFleetName());
        fleet.setDescription(request.getDescription());
        fleet.setModified(LocalDateTime.now());

        // 활성 상태 변경
        if (request.isActive() && !fleet.isActive()) {
            activateFleet(characterId, fleetId);
        } else if (!request.isActive() && fleet.isActive()) {
            fleet.setActive(false);
        }

        // 기존 함선과 모듈들 삭제 (soft delete)
        List<Ship> existingShips = shipRepository.findByFleetIdAndDeletedFalseOrderByPositionIndex(fleetId);
        for (Ship ship : existingShips) {
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

        // 새로운 함선과 모듈들 생성
        if (request.getShips() != null) {
            for (FleetImportRequest.ShipImportData shipData : request.getShips()) {
                Ship ship = new Ship();
                ship.setFleet(fleet);
                ship.setShipName(shipData.getShipName());
                ship.setPositionIndex(shipData.getPositionIndex());
                ship.setDescription(shipData.getDescription());
                ship = shipRepository.save(ship);

                if (shipData.getModules() != null) {
                    for (FleetImportRequest.ShipModuleImportData moduleData : shipData.getModules()) {
                        ShipModule module = new ShipModule();
                        module.setShip(ship);
                        module.setModuleType(moduleData.getModuleType());
                        module.setModuleLevel(moduleData.getModuleLevel());
                        module.setSlotIndex(moduleData.getSlotIndex());
                        shipModuleRepository.save(module);
                    }
                }
            }
        }

        fleet = fleetRepository.save(fleet);
        return convertToDetailDto(fleet);
    }

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
    private FleetDto convertToDto(Fleet fleet) {
        FleetDto dto = new FleetDto();
        dto.setId(fleet.getId());
        dto.setCharacterId(fleet.getCharacterId());
        dto.setFleetName(fleet.getFleetName());
        dto.setDescription(fleet.getDescription());
        dto.setActive(fleet.isActive());
        dto.setFormation(fleet.getFormation());
        dto.setCreated(fleet.getCreated());
        dto.setModified(fleet.getModified());
        return dto;
    }

    // Entity -> DTO 변환 (상세 정보 포함)
    private FleetDto convertToDetailDto(Fleet fleet) {
        FleetDto dto = convertToDto(fleet);
        
        List<Ship> ships = shipRepository.findByFleetIdAndDeletedFalseOrderByPositionIndex(fleet.getId());
        List<ShipDto> shipDtos = ships.stream()
                .map(this::convertToShipDto)
                .collect(Collectors.toList());
        dto.setShips(shipDtos);
        
        return dto;
    }

    private ShipDto convertToShipDto(Ship ship) {
        ShipDto dto = new ShipDto();
        dto.setId(ship.getId());
        dto.setFleetId(ship.getFleet().getId());
        dto.setShipName(ship.getShipName());
        dto.setPositionIndex(ship.getPositionIndex());
        dto.setDescription(ship.getDescription());
        dto.setCreated(ship.getCreated());
        dto.setModified(ship.getModified());

        List<ShipModule> modules = shipModuleRepository.findByShipIdAndDeletedFalseOrderBySlotIndex(ship.getId());
        List<BodyModuleDto> bodyDtos = convertToBodyModules(modules);
        dto.setBodies(bodyDtos);

        return dto;
    }

    private List<BodyModuleDto> convertToBodyModules(List<ShipModule> modules) {
        return modules.stream()
                .filter(m -> m.getModuleType() == EModuleType.Body)
                .map(bodyModule -> {
                    BodyModuleDto bodyDto = new BodyModuleDto();
                    bodyDto.setModuleType(ModuleTypeConverter.packBody(
                        bodyModule.getModuleBodySubType(),
                        bodyModule.getModuleStyle()
                    ));
                    bodyDto.setModuleLevel(bodyModule.getModuleLevel());
                    bodyDto.setBodyIndex(bodyModule.getBodyIndex());
                    bodyDto.setCreated(bodyModule.getCreated());

                    int bodyIndex = bodyModule.getBodyIndex();

                    List<EngineModuleDto> engines = modules.stream()
                            .filter(m -> m.getModuleType() == EModuleType.Engine && m.getBodyIndex() == bodyIndex)
                            .map(engineModule -> {
                                EngineModuleDto engineDto = new EngineModuleDto();
                                engineDto.setModuleType(ModuleTypeConverter.packEngine(
                                        engineModule.getModuleEngineSubType(),
                                        engineModule.getModuleStyle()
                                ));
                                engineDto.setModuleLevel(engineModule.getModuleLevel());
                                engineDto.setBodyIndex(engineModule.getBodyIndex());
                                engineDto.setSlotIndex(engineModule.getSlotIndex());
                                engineDto.setCreated(engineModule.getCreated());
                                return engineDto;
                            })
                            .collect(Collectors.toList());

                    List<WeaponModuleDto> weapons = modules.stream()
                            .filter(m -> m.getModuleType() == EModuleType.Weapon && m.getBodyIndex() == bodyIndex)
                            .map(weaponModule -> {
                                WeaponModuleDto weaponDto = new WeaponModuleDto();
                                weaponDto.setModuleType(ModuleTypeConverter.packWeapon(
                                    weaponModule.getModuleWeaponSubType(),
                                    weaponModule.getModuleStyle()
                                ));
                                weaponDto.setModuleLevel(weaponModule.getModuleLevel());
                                weaponDto.setBodyIndex(weaponModule.getBodyIndex());
                                weaponDto.setSlotIndex(weaponModule.getSlotIndex());
                                weaponDto.setCreated(weaponModule.getCreated());
                                return weaponDto;
                            })
                            .collect(Collectors.toList());

                    List<HangerModuleDto> hangers = modules.stream()
                            .filter(m -> m.getModuleType() == EModuleType.Hanger && m.getBodyIndex() == bodyIndex)
                            .map(hangerModule -> {
                                HangerModuleDto hangerDto = new HangerModuleDto();
                                hangerDto.setModuleType(ModuleTypeConverter.packHanger(
                                    hangerModule.getModuleHangerSubType(),
                                    hangerModule.getModuleStyle()
                                ));
                                hangerDto.setModuleLevel(hangerModule.getModuleLevel());
                                hangerDto.setBodyIndex(hangerModule.getBodyIndex());
                                hangerDto.setSlotIndex(hangerModule.getSlotIndex());
                                hangerDto.setCreated(hangerModule.getCreated());
                                return hangerDto;
                            })
                            .collect(Collectors.toList());

                    bodyDto.setWeapons(weapons);
                    bodyDto.setEngines(engines);
                    bodyDto.setHangers(hangers);

                    return bodyDto;
                })
                .collect(Collectors.toList());
    }

    private ShipDto convertShipToDto(Ship ship) {
        ShipDto dto = new ShipDto();
        dto.setId(ship.getId());
        dto.setFleetId(ship.getFleet().getId());
        dto.setShipName(ship.getShipName());
        dto.setPositionIndex(ship.getPositionIndex());
        dto.setDescription(ship.getDescription());
        dto.setCreated(ship.getCreated());
        dto.setModified(ship.getModified());

        List<ShipModule> modules = shipModuleRepository.findByShipIdAndDeletedFalseOrderBySlotIndex(ship.getId());
        List<BodyModuleDto> bodyDtos = convertToBodyModules(modules);
        dto.setBodies(bodyDtos);

        return dto;
    }

    @Transactional
    public AddShipResponse addShip(Long characterId, AddShipRequest request) {
        // 캐릭터 조회
        com.bk.sbs.entity.Character character = characterRepository.findById(characterId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.CHARACTER_NOT_FOUND));

        // 대상 함대 결정 (요청에 fleetId가 없으면 활성 함대 사용)
        Fleet targetFleet;
        if (request.getFleetId() != null) {
            targetFleet = fleetRepository.findByIdAndCharacterIdAndDeletedFalse(request.getFleetId(), characterId)
                    .orElseThrow(() -> new BusinessException(ServerErrorCode.FLEET_NOT_FOUND));
        } else {
            targetFleet = fleetRepository.findByCharacterIdAndIsActiveTrueAndDeletedFalse(characterId)
                    .orElseThrow(() -> new BusinessException(ServerErrorCode.ACTIVE_FLEET_NOT_FOUND));
        }

        // 함선 추가 제한 검사 (GameSettings에서 가져오기)
        int maxShipsPerFleet = gameDataService.getMaxShipsPerFleet();

        List<Ship> currentShips = shipRepository.findByFleetIdAndDeletedFalseOrderByPositionIndex(targetFleet.getId());
        if (currentShips.size() >= maxShipsPerFleet) {
            throw new BusinessException(ServerErrorCode.FLEET_MAX_SHIPS_REACHED);
        }

        // 현재 함선 수에 따른 추가 비용 가져오기
        CostStruct shipAddCost = gameDataService.getShipAddCost(currentShips.size());

        // TechLevel 검증
        if (character.getTechLevel() < shipAddCost.getTechLevel()) {
            throw new BusinessException(ServerErrorCode.INSUFFICIENT_TECH_LEVEL);
        }

        // 자원 부족 검사
        if (character.getMineral() < shipAddCost.getMineral()) {
            throw new BusinessException(ServerErrorCode.INSUFFICIENT_MINERAL);
        }
        if (character.getMineralRare() < shipAddCost.getMineralRare()) {
            throw new BusinessException(ServerErrorCode.INSUFFICIENT_MINERAL_RARE);
        }
        if (character.getMineralExotic() < shipAddCost.getMineralExotic()) {
            throw new BusinessException(ServerErrorCode.INSUFFICIENT_MINERAL_EXOTIC);
        }
        if (character.getMineralDark() < shipAddCost.getMineralDark()) {
            throw new BusinessException(ServerErrorCode.INSUFFICIENT_MINERAL_DARK);
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

        // 응답 생성
        AddShipResponse response = new AddShipResponse(true, "함선이 성공적으로 추가되었습니다.");
        response.setNewShipInfo(convertShipToDto(savedShip));

        // 비용 정보 (모든 미네랄 타입 포함)
        CostRemainInfo costRemainInfo = new CostRemainInfo(
                shipAddCost.getMineral(),
                shipAddCost.getMineralRare(),
                shipAddCost.getMineralExotic(),
                shipAddCost.getMineralDark(),
                character.getMineral(),
                character.getMineralRare(),
                character.getMineralExotic(),
                character.getMineralDark()
        );
        response.setCostRemainInfo(costRemainInfo);
        response.setUpdatedFleetInfo(convertToDetailDto(targetFleet));

        log.info("AddShip Response - success: {}, message: {}, costRemainInfo: {}",
                response.isSuccess(), response.getMessage(), response.getCostRemainInfo());

        return response;
    }

    private void createDefaultModules(Ship ship) {
        // Body 모듈
        ShipModule bodyModule = new ShipModule();
        bodyModule.setShip(ship);
        bodyModule.setModuleType(EModuleType.Body);
        bodyModule.setModuleSubType(EModuleBodySubType.Battle);
        bodyModule.setModuleStyle(EModuleStyle.StyleA);
        bodyModule.setModuleLevel(1);
        bodyModule.setBodyIndex(0);
        bodyModule.setSlotIndex(0);
        bodyModule.setDeleted(false);
        bodyModule.setCreated(LocalDateTime.now());
        bodyModule.setModified(LocalDateTime.now());
        shipModuleRepository.save(bodyModule);

        // Weapon 모듈
        ShipModule weaponModule = new ShipModule();
        weaponModule.setShip(ship);
        weaponModule.setModuleType(EModuleType.Weapon);
        weaponModule.setModuleSubType(EModuleWeaponSubType.Beam);
        weaponModule.setModuleStyle(EModuleStyle.StyleA);
        weaponModule.setModuleLevel(1);
        weaponModule.setBodyIndex(0);
        weaponModule.setSlotIndex(0);
        weaponModule.setDeleted(false);
        weaponModule.setCreated(LocalDateTime.now());
        weaponModule.setModified(LocalDateTime.now());
        shipModuleRepository.save(weaponModule);

        // Engine 모듈
        ShipModule engineModule = new ShipModule();
        engineModule.setShip(ship);
        engineModule.setModuleType(EModuleType.Engine);
        engineModule.setModuleSubType(EModuleEngineSubType.Standard);
        engineModule.setModuleStyle(EModuleStyle.StyleA);
        engineModule.setModuleLevel(1);
        engineModule.setBodyIndex(0);
        engineModule.setSlotIndex(0);
        engineModule.setDeleted(false);
        engineModule.setCreated(LocalDateTime.now());
        engineModule.setModified(LocalDateTime.now());
        shipModuleRepository.save(engineModule);
    }

    @Transactional
    public ModuleUpgradeResponse upgradeModule(Long characterId, ModuleUpgradeRequest request) {
        // 함선 소유권 확인
        Ship ship = shipRepository.findById(request.getShipId())
                .orElseThrow(() -> new BusinessException(ServerErrorCode.SHIP_NOT_FOUND));

        if (!ship.getFleet().getCharacterId().equals(characterId)) {
            throw new BusinessException(ServerErrorCode.FLEET_ACCESS_DENIED);
        }

        // 모듈 찾기
        ShipModule module = shipModuleRepository.findByShipIdAndBodyIndexAndModuleTypeAndDeletedFalse(
                request.getShipId(),
                request.getBodyIndex(),
                EModuleType.valueOf(request.getModuleType())
        ).orElseThrow(() -> new BusinessException(ServerErrorCode.MODULE_NOT_FOUND));

        // 현재 레벨 확인
        if (module.getModuleLevel() != request.getCurrentLevel()) {
            throw new BusinessException(ServerErrorCode.MODULE_LEVEL_MISMATCH);
        }

        // 캐릭터 자원 조회
        com.bk.sbs.entity.Character character = characterRepository.findById(characterId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.CHARACTER_NOT_FOUND));

        // 업그레이드 비용 계산 (현재 레벨부터 목표 레벨까지)
        CostStruct totalCost = new CostStruct(0, 0L, 0L, 0L, 0L);
        int maxTechLevel = 0;

        if (module.getModuleType() == EModuleType.Body) {
            List<ModuleBodyDataDto> moduleDataList = gameDataService.getBodyModules();
            for (int level = request.getCurrentLevel(); level < request.getTargetLevel(); level++) {
                final int currentLevel = level;
                ModuleBodyDataDto levelData = moduleDataList.stream()
                        .filter(data -> data.getLevel() == currentLevel)
                        .findFirst()
                        .orElseThrow(() -> new BusinessException(ServerErrorCode.MODULE_DATA_NOT_FOUND));

                CostStruct cost = levelData.getUpgradeCost();
                if (cost != null) {
                    maxTechLevel = Math.max(maxTechLevel, cost.getTechLevel());
                    totalCost.setMineral(totalCost.getMineral() + cost.getMineral());
                    totalCost.setMineralRare(totalCost.getMineralRare() + cost.getMineralRare());
                    totalCost.setMineralExotic(totalCost.getMineralExotic() + cost.getMineralExotic());
                    totalCost.setMineralDark(totalCost.getMineralDark() + cost.getMineralDark());
                }
            }
        } else if (module.getModuleType() == EModuleType.Weapon) {
            List<ModuleWeaponDataDto> moduleDataList = gameDataService.getWeaponModules();
            for (int level = request.getCurrentLevel(); level < request.getTargetLevel(); level++) {
                final int currentLevel = level;
                ModuleWeaponDataDto levelData = moduleDataList.stream()
                        .filter(data -> data.getLevel() == currentLevel)
                        .findFirst()
                        .orElseThrow(() -> new BusinessException(ServerErrorCode.MODULE_DATA_NOT_FOUND));

                CostStruct cost = levelData.getUpgradeCost();
                if (cost != null) {
                    maxTechLevel = Math.max(maxTechLevel, cost.getTechLevel());
                    totalCost.setMineral(totalCost.getMineral() + cost.getMineral());
                    totalCost.setMineralRare(totalCost.getMineralRare() + cost.getMineralRare());
                    totalCost.setMineralExotic(totalCost.getMineralExotic() + cost.getMineralExotic());
                    totalCost.setMineralDark(totalCost.getMineralDark() + cost.getMineralDark());
                }
            }
        } else if (module.getModuleType() == EModuleType.Engine) {
            List<ModuleEngineDataDto> moduleDataList = gameDataService.getEngineModules();
            for (int level = request.getCurrentLevel(); level < request.getTargetLevel(); level++) {
                final int currentLevel = level;
                ModuleEngineDataDto levelData = moduleDataList.stream()
                        .filter(data -> data.getLevel() == currentLevel)
                        .findFirst()
                        .orElseThrow(() -> new BusinessException(ServerErrorCode.MODULE_DATA_NOT_FOUND));

                CostStruct cost = levelData.getUpgradeCost();
                if (cost != null) {
                    maxTechLevel = Math.max(maxTechLevel, cost.getTechLevel());
                    totalCost.setMineral(totalCost.getMineral() + cost.getMineral());
                    totalCost.setMineralRare(totalCost.getMineralRare() + cost.getMineralRare());
                    totalCost.setMineralExotic(totalCost.getMineralExotic() + cost.getMineralExotic());
                    totalCost.setMineralDark(totalCost.getMineralDark() + cost.getMineralDark());
                }
            }
        } else if (module.getModuleType() == EModuleType.Hanger) {
            List<ModuleHangerDataDto> moduleDataList = gameDataService.getHangerModules();
            for (int level = request.getCurrentLevel(); level < request.getTargetLevel(); level++) {
                final int currentLevel = level;
                ModuleHangerDataDto levelData = moduleDataList.stream()
                        .filter(data -> data.getLevel() == currentLevel)
                        .findFirst()
                        .orElseThrow(() -> new BusinessException(ServerErrorCode.MODULE_DATA_NOT_FOUND));

                CostStruct cost = levelData.getUpgradeCost();
                if (cost != null) {
                    maxTechLevel = Math.max(maxTechLevel, cost.getTechLevel());
                    totalCost.setMineral(totalCost.getMineral() + cost.getMineral());
                    totalCost.setMineralRare(totalCost.getMineralRare() + cost.getMineralRare());
                    totalCost.setMineralExotic(totalCost.getMineralExotic() + cost.getMineralExotic());
                    totalCost.setMineralDark(totalCost.getMineralDark() + cost.getMineralDark());
                }
            }
        }

        // TechLevel 검증
        if (character.getTechLevel() < maxTechLevel) {
            throw new BusinessException(ServerErrorCode.INSUFFICIENT_TECH_LEVEL);
        }

        // 자원 부족 검사 (업그레이드 진행 전에 먼저 체크)
        if (character.getMineral() < totalCost.getMineral()) {
            throw new BusinessException(ServerErrorCode.INSUFFICIENT_MINERAL);
        }
        if (character.getMineralRare() < totalCost.getMineralRare()) {
            throw new BusinessException(ServerErrorCode.INSUFFICIENT_MINERAL_RARE);
        }
        if (character.getMineralExotic() < totalCost.getMineralExotic()) {
            throw new BusinessException(ServerErrorCode.INSUFFICIENT_MINERAL_EXOTIC);
        }
        if (character.getMineralDark() < totalCost.getMineralDark()) {
            throw new BusinessException(ServerErrorCode.INSUFFICIENT_MINERAL_DARK);
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

        // 응답 생성
        ModuleUpgradeResponse response = new ModuleUpgradeResponse();
        response.setSuccess(true);
        response.setNewLevel(module.getModuleLevel());
        response.setMessage("Module upgrade completed successfully.");

        // 비용 정보 (모든 미네랄 타입 포함)
        CostRemainInfo costRemainInfo = new CostRemainInfo(
                totalCost.getMineral(),
                totalCost.getMineralRare(),
                totalCost.getMineralExotic(),
                totalCost.getMineralDark(),
                character.getMineral(),
                character.getMineralRare(),
                character.getMineralExotic(),
                character.getMineralDark()
        );
        response.setCostRemainInfo(costRemainInfo);

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

        // 편대 타입 유효성 검사
        EFormationType formationType = request.getFormationType();
        if (formationType == null) {
            return ChangeFormationResponse.failure("Formation type is required");
        }

        // 편대 정보 업데이트
        fleet.setFormation(formationType);
        fleet.setModified(LocalDateTime.now());
        fleetRepository.save(fleet);

        // 업데이트된 함대 정보 반환
        FleetDto updatedFleet = convertToDetailDto(fleet);
        return ChangeFormationResponse.success(updatedFleet);
    }
}
