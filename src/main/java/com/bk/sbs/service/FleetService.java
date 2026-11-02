package com.bk.sbs.service;

import com.bk.sbs.config.DataTableModule;
import com.bk.sbs.dto.*;
import com.bk.sbs.entity.*;
import com.bk.sbs.entity.Commander;
import com.bk.sbs.entity.Module;
import com.bk.sbs.enums.*;
import com.bk.sbs.exception.BusinessException;
import com.bk.sbs.exception.ServerErrorCode;
import com.bk.sbs.repository.*;
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
    private final FleetRepository fleetRepository;
    private final CommanderUnlockedHullRepository commanderUnlockedHullRepository;
    private final AchievementService achievementService;

    // 신규 커맨더에게 지급되는 기본 함대(fleetIndex=0)의 초기 함선 — 티어1 아키타입(centurion) 레벨1 함체 + 기본 빔1 장착
    private static final String DEFAULT_FLEET_HULL_SUB_TYPE = "hull_1_centurion_1";

    public FleetService(CommanderRepository commanderRepository,
                       GameDataService gameDataService,
                       FleetRepository fleetRepository,
                       CommanderUnlockedHullRepository commanderUnlockedHullRepository,
                       AchievementService achievementService) {
        this.commanderRepository = commanderRepository;
        this.gameDataService = gameDataService;
        this.fleetRepository = fleetRepository;
        this.commanderUnlockedHullRepository = commanderUnlockedHullRepository;
        this.achievementService = achievementService;
    }

    // 신규 커맨더 생성 시 기본 함대(fleetIndex=0) 생성
    @Transactional
    public void createDefaultFleet(Long commanderId) {
        Fleet fleet = new Fleet();
        fleet.setCommanderId(commanderId);
        fleet.setFleetIndex(0);
        fleet = fleetRepository.save(fleet);

        Ship ship = new Ship();
        ship.setFleet(fleet);
        ship.setSlotIndex(0);
        ship.setHullSubType(DEFAULT_FLEET_HULL_SUB_TYPE);
        ship.setFront(true);
        int[] initialMaxSlots = gameDataService.getMaxSlotsForHull(DEFAULT_FLEET_HULL_SUB_TYPE);
        replaceShipModules(ship, buildDefaultModules(ship, initialMaxSlots));
        fleet.setShips(new ArrayList<>(List.of(ship)));

        fleetRepository.save(fleet);
    }

    // 로그인 시 내려주는 "내 함대" — fleetIndex=0 함대를 FleetInfoDto로 변환, 함선별 실제 장착 모듈(hulls)까지 포함
    // Fleet.ships가 LAZY라 convertFleetToFleetInfoDto에서 컬렉션을 순회하는 동안 세션이 열려 있어야 함 — @Transactional 필수
    // healInvalidHullSubTypes가 DB를 고칠 수도 있어 readOnly 아님(일반 @Transactional)
    @Transactional
    public FleetInfoDto getActiveFleet(Long commanderId) {
        Fleet fleet = fleetRepository.findByCommanderIdAndFleetIndex(commanderId, 0)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.COMMANDER_CONTROLLER_FAIL_NULL_ACTIVE_FLEET));
        healInvalidHullSubTypes(fleet);
        healEmptyModules(fleet);
        return convertFleetToFleetInfoDto(fleet);
    }

    // 모듈은 항상 설치된 상태가 기본값이 된 이후(설치/해제 개념 폐지)에도, 그 변경 이전에 생성돼 모듈이 빈 채로 저장된 기존 함선은
    // 조회 시점에 자가치유 — 현재 hullSubType 기준으로 빈 슬롯을 기본 레벨1 모듈로 채우고 DB에도 반영(dirty checking, @Transactional)
    private void healEmptyModules(Fleet fleet) {
        if (fleet.getShips() == null) return;

        for (Ship ship : fleet.getShips()) {
            if (ship.getModules() != null && ship.getModules().isEmpty() == false) continue;

            int[] maxSlots = gameDataService.getMaxSlotsForHull(ship.getHullSubType());
            List<Module> defaultModules = buildDefaultModules(ship, maxSlots);
            if (defaultModules.isEmpty()) continue;

            log.info("Ship {} (hullSubType={}) had empty modules — backfilling with default level-1 modules",
                    ship.getId(), ship.getHullSubType());
            replaceShipModules(ship, defaultModules);
        }
    }

    // 콘텐츠 개편으로 datatable_module.csv에서 사라진 hullSubType을 들고 있는 함선을 조회 시점에 기본 함체로 자가치유 — 실서버 패치 후 과거 데이터 보정용
    // hull만 처리(장착 모듈 유효성은 이번 범위 밖) — 기본 함체로 바뀌어도 기존 모듈은 그대로 유지, 슬롯 범위를 벗어나면 다음 setFleetSlotModules 때 자연히 걸러짐
    // 무효 함선이 여러 척이면 전부 같은 기본 함체(DEFAULT_FLEET_HULL_SUB_TYPE)로 바뀌어 같은 아키타입 중복이 생기므로(M7: 함대 내 아키타입 1척 제한),
    // 첫 번째 한 척만 교체하고 나머지는 슬롯 자체를 비움(제거). 이미 유효한 다른 슬롯이 같은 아키타입을 쓰고 있는 경우까지는 검사하지 않음 — M7 구현 시 함께 다룰 범위
    private void healInvalidHullSubTypes(Fleet fleet) {
        if (fleet.getShips() == null) return;

        List<Ship> invalidShips = new ArrayList<>();
        for (Ship ship : fleet.getShips()) {
            if (gameDataService.getHullModuleData(ship.getHullSubType()) == null)
                invalidShips.add(ship);
        }
        if (invalidShips.isEmpty()) return;

        Ship firstInvalid = invalidShips.get(0);
        log.warn("Ship {} had an unknown hullSubType '{}' — resetting to default {}",
                firstInvalid.getId(), firstInvalid.getHullSubType(), DEFAULT_FLEET_HULL_SUB_TYPE);
        firstInvalid.setHullSubType(DEFAULT_FLEET_HULL_SUB_TYPE);

        for (int i = 1; i < invalidShips.size(); i++) {
            Ship extraInvalid = invalidShips.get(i);
            log.warn("Ship {} had an unknown hullSubType '{}' — removing this slot instead of duplicating the default hull",
                    extraInvalid.getId(), extraInvalid.getHullSubType());
            fleet.getShips().remove(extraInvalid);
        }

        fleetRepository.save(fleet);
    }

    // PvP 상대 함대 조회/랭킹 함대 능력치 계산용 — 함대가 없으면(탈퇴 등) null 반환, getActiveFleet과 달리 예외를 던지지 않음
    @Transactional(readOnly = true)
    public FleetInfoDto getActiveFleetOrNull(Long commanderId) {
        return fleetRepository.findByCommanderIdAndFleetIndex(commanderId, 0)
                .map(this::convertFleetToFleetInfoDto)
                .orElse(null);
    }

    private FleetInfoDto convertFleetToFleetInfoDto(Fleet fleet) {
        List<ShipInfoDto> ships = fleet.getShips().stream()
                .sorted((a, b) -> Integer.compare(a.getSlotIndex(), b.getSlotIndex()))
                .map(ship -> ShipInfoDto.builder()
                        .id(ship.getId())
                        .positionIndex(ship.getSlotIndex())
                        .hullSubType(ship.getHullSubType())
                        .isFront(ship.isFront())
                        .hulls(List.of(buildModuleHullInfoDto(ship)))
                        .build())
                .collect(Collectors.toList());

        return FleetInfoDto.builder()
                .id(fleet.getId())
                .tacticOptions(fleet.getTacticOptions())
                .ships(ships)
                .build();
    }

    // 함대편성(FleetComposition) 슬롯에 함선 배치/교체 — 함체를 바꿔도 새 함체에 같은 카테고리+슬롯 인덱스가 남아있는
    // 기존 모듈(서브타입/강화 포인트 포함)은 그대로 유지하고, 슬롯 자체가 사라진 모듈만 소실된다(자동으로 일부만 끄는 로직은 없음 —
    // 유지 결과 지휘력이 초과되면 배치 자체를 거부). 클라(FleetComposition.TryPlaceShipAt)가 동일 로직으로 미리보기를 계산하지만,
    // 조작된 요청을 막기 위해 서버가 DB의 기존 모듈을 직접 기준으로 재계산한다 — setFleetSlotModules와 동일한 신뢰 경계 원칙
    @Transactional
    public void placeFleetShip(Long commanderId, FleetPlaceShipRequest request) {
        Commander commander = commanderRepository.findByIdForUpdate(commanderId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.PLACE_FLEET_SHIP_FAIL_COMMANDER_NOT_FOUND));

        ModuleData hullData = gameDataService.getHullModuleData(request.getHullSubType());
        if (hullData == null)
            throw new BusinessException(ServerErrorCode.PLACE_FLEET_SHIP_FAIL_HULL_NOT_FOUND);
        // 티어1은 기본 지급이라 DB 기록 없이 항상 사용 가능, 티어2+는 비용(ap_cost)과 무관하게 commander_unlocked_hull 기록 존재 여부로만 판정
        int placeTier = GameDataService.parseTierFromHullSubType(request.getHullSubType());
        if (placeTier > 1
                && commanderUnlockedHullRepository.existsByCommanderIdAndHullSubType(commanderId, request.getHullSubType()) == false)
            throw new BusinessException(ServerErrorCode.PLACE_FLEET_SHIP_FAIL_HULL_NOT_UNLOCKED);

        // 함대 슬롯 칸 수는 고정 상한(GeneralSettings.maxFleetSlotCount) — 지휘관 레벨과 무관, 실제 배치 가능 여부는 지휘력(캐파) 체크가 담당
        int maxSlotCount = gameDataService.getMaxShipsPerFleet();
        if (request.getSlotIndex() < 0 || request.getSlotIndex() >= maxSlotCount)
            throw new BusinessException(ServerErrorCode.PLACE_FLEET_SHIP_FAIL_SLOT_LOCKED);

        Fleet fleet = fleetRepository.findByCommanderIdAndFleetIndex(commanderId, 0)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.COMMANDER_CONTROLLER_FAIL_NULL_ACTIVE_FLEET));

        Ship ship = fleet.getShips().stream()
                .filter(s -> s.getSlotIndex() == request.getSlotIndex())
                .findFirst()
                .orElseGet(() -> {
                    Ship newShip = new Ship();
                    newShip.setFleet(fleet);
                    newShip.setSlotIndex(request.getSlotIndex());
                    fleet.getShips().add(newShip);
                    return newShip;
                });

        // 새 함체 반영 전에 기존 모듈을 스냅샷 — replaceShipModules가 이 컬렉션을 곧 비우므로 미리 복사해둬야 함
        List<Module> existingModules = ship.getModules() != null ? new ArrayList<>(ship.getModules()) : new ArrayList<>();
        int[] newMaxSlots = gameDataService.getMaxSlotsForHull(request.getHullSubType());
        List<Module> keptModules = existingModules.isEmpty() == false
                ? filterModulesForNewHull(existingModules, newMaxSlots, request.getHullSubType(), ship)
                : buildDefaultModules(ship, newMaxSlots);

        // 캐파(지휘력)는 함체(hull) 자신의 점유값만 반영 — 모듈 설치/해제는 캐파와 무관(M13)
        int newShipCost = hullData.getStatPoint() != null ? hullData.getStatPoint() : 0;

        int usedByOtherShips = 0;
        for (Ship s : fleet.getShips()) {
            if (s.getSlotIndex() == request.getSlotIndex()) continue;
            usedByOtherShips += computeShipCommandCost(s);
        }
        int commandPowerMax = gameDataService.getCommandPowerMax(commander.getCommanderLevel());
        if (usedByOtherShips + newShipCost > commandPowerMax)
            throw new BusinessException(ServerErrorCode.PLACE_FLEET_SHIP_FAIL_NOT_ENOUGH_COMMAND_POWER);

        ship.setHullSubType(request.getHullSubType());
        ship.setFront(request.getIsFront());
        replaceShipModules(ship, keptModules);
        fleetRepository.save(fleet);
        achievementService.recordReachedTierAchievements(commander, fleet);
    }

    // 아직 언락 기록이 없는 함체(티어2+)를 업적포인트로 언락(구매) — 언락 완료 후에도 배치는 placeFleetShip을 통해 별도로 해야 함(여기선 구매만 처리)
    @Transactional
    public UnlockHullResponse unlockHull(Long commanderId, UnlockHullRequest request) {
        Commander commander = commanderRepository.findByIdForUpdate(commanderId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.UNLOCK_HULL_FAIL_COMMANDER_NOT_FOUND));

        ModuleData hullData = gameDataService.getHullModuleData(request.getHullSubType());
        if (hullData == null)
            throw new BusinessException(ServerErrorCode.UNLOCK_HULL_FAIL_HULL_NOT_FOUND);

        int unlockCost = hullData.getUnlockAchievementPointCost() != null ? hullData.getUnlockAchievementPointCost() : 0;

        if (commanderUnlockedHullRepository.existsByCommanderIdAndHullSubType(commanderId, request.getHullSubType()) == true)
            throw new BusinessException(ServerErrorCode.UNLOCK_HULL_FAIL_ALREADY_UNLOCKED);
        if (commander.getAchievementPoint() < unlockCost)
            throw new BusinessException(ServerErrorCode.UNLOCK_HULL_FAIL_INSUFFICIENT_ACHIEVEMENT_POINT);

        commander.setAchievementPoint(commander.getAchievementPoint() - unlockCost);
        commanderRepository.save(commander);
        commanderUnlockedHullRepository.save(new CommanderUnlockedHull(commanderId, request.getHullSubType()));

        List<String> unlockedHulls = commanderUnlockedHullRepository.findByCommanderId(commanderId).stream()
                .map(CommanderUnlockedHull::getHullSubType)
                .collect(Collectors.toList());

        return UnlockHullResponse.builder()
                .hullSubType(request.getHullSubType())
                .achievementPointRemain(commander.getAchievementPoint())
                .unlockedHulls(unlockedHulls)
                .build();
    }

    // 기존 장착 모듈 중 새 함체(newMaxSlots)에도 같은 카테고리+슬롯 인덱스가 존재하는 것만 유지 — 강화 포인트는 그대로 복사
    // 확정 규칙: 무기 티어는 함체 티어와 독립적인 별도 축이지만 상한은 항상 함체 티어 — 새 함체 티어를 넘는 기존 모듈은 함체 티어로 자동 다운그레이드됨
    // (지휘력 회수는 별도 처리 불필요 — 호출부 placeFleetShip이 이 결과의 statPoint로 비용을 재계산하므로 낮아진 티어가 자동 반영됨)
    private List<Module> filterModulesForNewHull(List<Module> existingModules, int[] newMaxSlots, String newHullSubType, Ship targetShip) {
        int newHullTier = GameDataService.parseTierFromHullSubType(newHullSubType);
        List<Module> kept = new ArrayList<>();
        java.util.Set<String> filledKeys = new java.util.HashSet<>(); // "moduleType:slotIndex" — 아래에서 빈 슬롯을 기본 모듈로 채울 때 중복 방지
        for (Module old : existingModules) {
            int maxSlotForCategory = getMaxSlotForCategory(newMaxSlots, old.getModuleType());
            if (old.getSlotIndex() >= maxSlotForCategory) continue;

            Module module = new Module();
            module.setShip(targetShip);
            module.setModuleType(old.getModuleType());
            module.setSlotIndex(old.getSlotIndex());
            module.setModuleSubType(clampModuleTierToHull(old.getModuleType(), old.getModuleSubType(), newHullTier));
            kept.add(module);
            filledKeys.add(old.getModuleType() + ":" + old.getSlotIndex());
        }

        // 새 함체로 바뀌면서 슬롯이 늘어나 비게 된 자리(실드/요격체가 원래 없었던 경우 포함)는 기본 레벨1 모듈로 채움 — 모듈은 항상 설치된 상태(설치/해제 개념 폐지)
        appendMissingDefaultCategoryModules(kept, targetShip, EModuleType.beam, newMaxSlots[0], filledKeys);
        appendMissingDefaultCategoryModules(kept, targetShip, EModuleType.missile, newMaxSlots[1], filledKeys);
        appendMissingDefaultCategoryModules(kept, targetShip, EModuleType.hangar, newMaxSlots[2], filledKeys);
        if (newMaxSlots[3] > 0 && filledKeys.contains(EModuleType.shield + ":0") == false)
            kept.add(buildModule(targetShip, EModuleType.shield, 0, "shield_1_1"));
        if (newMaxSlots[4] > 0 && filledKeys.contains(EModuleType.interceptor + ":0") == false)
            kept.add(buildModule(targetShip, EModuleType.interceptor, 0, "interceptor_1_1"));

        return kept;
    }

    private void appendMissingDefaultCategoryModules(List<Module> target, Ship ship, EModuleType moduleType, int maxSlotCount, java.util.Set<String> filledKeys) {
        String defaultSubType = getDefaultSubTypeForCategory(moduleType);
        for (int i = 0; i < maxSlotCount; i++) {
            if (filledKeys.contains(moduleType + ":" + i)) continue;
            target.add(buildModule(ship, moduleType, i, defaultSubType));
        }
    }

    // 모듈 서브타입의 티어가 함체 티어를 넘으면 {category}_{hullTier}_1로 다운그레이드 — 해당 티어 데이터가 없으면(비정상 데이터) 원본 유지
    private String clampModuleTierToHull(EModuleType moduleType, String subType, int hullTier) {
        if (GameDataService.parseTierFromHullSubType(subType) <= hullTier) return subType;

        String downgradedSubType = moduleType + "_" + hullTier + "_1";
        if (isValidSubTypeForCategory(moduleType, downgradedSubType) == false) return subType;

        return downgradedSubType;
    }

    // newMaxSlots = [beam, missile, hangar, shield, interceptor] — moduleType과 배열 인덱스 매핑
    private int getMaxSlotForCategory(int[] maxSlots, EModuleType moduleType) {
        if (moduleType == EModuleType.beam) return maxSlots[0];
        if (moduleType == EModuleType.missile) return maxSlots[1];
        if (moduleType == EModuleType.hangar) return maxSlots[2];
        if (moduleType == EModuleType.shield) return maxSlots[3];
        if (moduleType == EModuleType.interceptor) return maxSlots[4];
        return 0;
    }

    // 빈 슬롯(새로 배치되는 함선)의 기본 로드아웃 — 모든 구조 슬롯에 레벨1 모듈을 기본 장착(설치/해제 개념 폐지, 항상 설치된 상태).
    // maxSlots = [beam, missile, hangar, shield, interceptor]. 반영(저장)은 호출부 책임
    private List<Module> buildDefaultModules(Ship ship, int[] maxSlots) {
        List<Module> modules = new ArrayList<>();
        appendDefaultCategoryModules(modules, ship, EModuleType.beam, maxSlots[0]);
        appendDefaultCategoryModules(modules, ship, EModuleType.missile, maxSlots[1]);
        appendDefaultCategoryModules(modules, ship, EModuleType.hangar, maxSlots[2]);
        if (maxSlots[3] > 0) modules.add(buildModule(ship, EModuleType.shield, 0, "shield_1_1"));
        if (maxSlots[4] > 0) modules.add(buildModule(ship, EModuleType.interceptor, 0, "interceptor_1_1"));
        return modules;
    }

    private void appendDefaultCategoryModules(List<Module> target, Ship ship, EModuleType moduleType, int maxSlotCount) {
        String defaultSubType = getDefaultSubTypeForCategory(moduleType);
        for (int i = 0; i < maxSlotCount; i++)
            target.add(buildModule(ship, moduleType, i, defaultSubType));
    }

    private Module buildModule(Ship ship, EModuleType moduleType, int slotIndex, String subType) {
        Module module = new Module();
        module.setShip(ship);
        module.setModuleType(moduleType);
        module.setSlotIndex(slotIndex);
        module.setModuleSubType(subType);
        return module;
    }

    // Hibernate orphanRemoval 컬렉션은 필드 참조 자체를 새 List로 갈아끼우면 안 됨(기존에 관리되던 컬렉션이 고아가 되어
    // "A collection with orphan deletion was no longer referenced" 예외 발생) — 기존 컬렉션이 있으면 clear() 후 채우고,
    // 아직 없으면(신규 함선 등 영속화 전) 그대로 세팅
    private void replaceShipModules(Ship ship, List<Module> newModules) {
        List<Module> current = ship.getModules();
        if (current == null) {
            ship.setModules(newModules);
            return;
        }
        current.clear();
        current.addAll(newModules);
    }

    // ── 함선 모듈 편집 ────────────────────────────────────────────────

    // on/off만 지원하므로 카테고리당 서브타입은 항상 이 값 하나 — 무기 티어는 함체와 독립적인 별도 축이라 기본값은 항상 1티어
    private String getDefaultSubTypeForCategory(EModuleType moduleType) {
        return switch (moduleType) {
            case beam -> "beam_1_1";
            case missile -> "missile_1_1";
            case hangar -> "hangar_1_1";
            case shield -> "shield_1_1";
            default -> null;
        };
    }

    private int computeHullCost(String hullSubType) {
        ModuleData hullData = gameDataService.getHullModuleData(hullSubType);
        return hullData != null && hullData.getStatPoint() != null ? hullData.getStatPoint() : 0;
    }

    // 함선의 캐파(지휘력) 점유값 — 함체(hull) 자신의 점유값만, 모듈은 캐파와 무관(M13)
    private int computeShipCommandCost(Ship ship) {
        return computeHullCost(ship.getHullSubType());
    }

    private ModuleHullInfoDto buildModuleHullInfoDto(Ship ship) {
        List<ModuleInfoDto> beams = new ArrayList<>();
        List<ModuleInfoDto> missiles = new ArrayList<>();
        List<ModuleInfoDto> hangars = new ArrayList<>();
        String shieldModuleSubType = "";
        String interceptorModuleSubType = "";

        if (ship.getModules() != null) {
            for (Module module : ship.getModules()) {
                ModuleInfoDto dto = ModuleInfoDto.builder()
                        .moduleType(module.getModuleType())
                        .moduleSubType(module.getModuleSubType())
                        .slotIndex(module.getSlotIndex())
                        .build();
                switch (module.getModuleType()) {
                    case beam -> beams.add(dto);
                    case missile -> missiles.add(dto);
                    case hangar -> hangars.add(dto);
                    case shield -> shieldModuleSubType = module.getModuleSubType();
                    case interceptor -> interceptorModuleSubType = module.getModuleSubType();
                    default -> { }
                }
            }
        }

        String hullSubType = ship.getHullSubType();
        Float maxHealth = getModuleMaxHealth(hullSubType);

        return ModuleHullInfoDto.builder()
                .moduleType(EModuleType.hull)
                .moduleSubType(hullSubType)
                .beams(beams)
                .missiles(missiles)
                .hangars(hangars)
                .shieldModuleSubType(shieldModuleSubType)
                .interceptorModuleSubType(interceptorModuleSubType)
                .currentHealth(maxHealth)
                .build();
    }

    private Float getModuleMaxHealth(String hullSubType) {
        if (hullSubType == null) return null;
        List<ModuleData> hullDataList = gameDataService.getModulesByType(EModuleType.hull);
        return hullDataList.stream()
                .filter(d -> hullSubType.equals(d.getModuleSubType()))
                .findFirst()
                .map(d -> d.getHealth() != null ? d.getHealth() : 0f)
                .orElse(null);
    }

    // 함선 하나의 장착 모듈 "전체"를 최종 상태로 한 번에 교체 — 낱개 토글을 순서대로 여러 번 보내면 중간 상태에서
    // 예산/공격모듈 0개 검증에 걸릴 수 있어(예: 빔→미사일 교체 시 어느 순서로 보내도 중간엔 항상 실패), 요청받은 최종 구성만 검증한다
    @Transactional
    public SetModuleResponse setFleetSlotModules(Long commanderId, SetModuleRequest request) {
        Commander commander = commanderRepository.findByIdForUpdate(commanderId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.SET_FLEET_MODULE_FAIL_COMMANDER_NOT_FOUND));

        Fleet fleet = fleetRepository.findByCommanderIdAndFleetIndex(commanderId, 0)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.SET_FLEET_MODULE_FAIL_FLEET_NOT_FOUND));

        Ship ship = fleet.getShips().stream()
                .filter(s -> s.getSlotIndex() == request.getSlotIndex())
                .findFirst()
                .orElseThrow(() -> new BusinessException(ServerErrorCode.SET_FLEET_MODULE_FAIL_SLOT_NOT_FOUND));

        int[] maxSlots = gameDataService.getMaxSlotsForHull(ship.getHullSubType());
        int hullTier = GameDataService.parseTierFromHullSubType(ship.getHullSubType());
        ModuleHullInfoDto requestedModules = request.getModules();

        List<DesiredModule> desired = new ArrayList<>();
        appendDesiredModules(desired, EModuleType.beam, maxSlots[0], hullTier, requestedModules != null ? requestedModules.getBeams() : null);
        appendDesiredModules(desired, EModuleType.missile, maxSlots[1], hullTier, requestedModules != null ? requestedModules.getMissiles() : null);
        appendDesiredModules(desired, EModuleType.hangar, maxSlots[2], hullTier, requestedModules != null ? requestedModules.getHangars() : null);
        appendDesiredShield(desired, maxSlots[3], hullTier,
                requestedModules != null ? requestedModules.getShieldModuleSubType() : null);
        appendDesiredInterceptor(desired, maxSlots[4], hullTier,
                requestedModules != null ? requestedModules.getInterceptorModuleSubType() : null);

        // 캐파(지휘력)는 함체(hull) 자신의 점유값만 반영 — 모듈 설치/해제는 캐파와 무관(M13)
        int newShipCost = computeHullCost(ship.getHullSubType());

        int usedByOtherShips = 0;
        for (Ship s : fleet.getShips()) {
            if (s.getSlotIndex() == ship.getSlotIndex()) continue;
            usedByOtherShips += computeShipCommandCost(s);
        }
        int commandPowerMax = gameDataService.getCommandPowerMax(commander.getCommanderLevel());
        if (usedByOtherShips + newShipCost > commandPowerMax)
            throw new BusinessException(ServerErrorCode.SET_FLEET_MODULE_FAIL_NOT_ENOUGH_COMMAND_POWER);

        List<Module> newModules = new ArrayList<>();
        for (DesiredModule m : desired) {
            Module module = new Module();
            module.setShip(ship);
            module.setModuleType(m.moduleType());
            module.setSlotIndex(m.slotIndex());
            module.setModuleSubType(m.moduleSubType());
            newModules.add(module);
        }
        replaceShipModules(ship, newModules);
        fleetRepository.save(fleet);
        achievementService.recordReachedTierAchievements(commander, fleet);

        int remainingAfter = commandPowerMax - (usedByOtherShips + newShipCost);

        return SetModuleResponse.builder()
                .hull(buildModuleHullInfoDto(ship))
                .commandCost(newShipCost)
                .remainingCommandPower(remainingAfter)
                .build();
    }

    private record DesiredModule(EModuleType moduleType, int slotIndex, String moduleSubType) {
    }

    // requested의 각 항목이 유효한 슬롯 인덱스(0 <= idx < maxSlotCount)인지, 중복 슬롯이 없는지 검증하며 desired 목록에 채워 넣음
    // moduleSubType(티어)은 클라가 보낸 값을 그대로 믿지 않고 이 카테고리에 실제로 존재하는 데이터인지 + 함체 티어를 넘지 않는지 검증한 뒤에만
    // 사용(티어업/다운) — 없거나 비어있거나 함체 티어 초과면 기본 1티어로 폴백
    private void appendDesiredModules(List<DesiredModule> target, EModuleType moduleType, int maxSlotCount, int hullTier, List<ModuleInfoDto> requested) {
        if (requested == null) return;
        String defaultSubType = getDefaultSubTypeForCategory(moduleType);

        java.util.Set<Integer> seenSlotIndexes = new java.util.HashSet<>();
        for (ModuleInfoDto item : requested) {
            Integer slotIndex = item.getSlotIndex();
            if (slotIndex == null || slotIndex < 0 || slotIndex >= maxSlotCount)
                throw new BusinessException(ServerErrorCode.SET_FLEET_MODULE_FAIL_INVALID_SLOT_INDEX);
            if (seenSlotIndexes.add(slotIndex) == false)
                throw new BusinessException(ServerErrorCode.SET_FLEET_MODULE_FAIL_INVALID_SLOT_INDEX); // 같은 슬롯이 중복 요청됨

            String requestedSubType = item.getModuleSubType();
            String subType = (requestedSubType != null && isValidSubTypeForCategory(moduleType, requestedSubType, hullTier))
                    ? requestedSubType
                    : defaultSubType;

            target.add(new DesiredModule(moduleType, slotIndex, subType));
        }
    }

    // requestedSubType이 이 카테고리(moduleType)의 실제 데이터에 존재하는지 확인 — 다른 카테고리 문자열이나 존재하지 않는 티어를 그대로 믿지 않기 위함
    private boolean isValidSubTypeForCategory(EModuleType moduleType, String requestedSubType) {
        List<ModuleData> modules = gameDataService.getModulesByType(moduleType);
        for (ModuleData data : modules) {
            if (requestedSubType.equals(data.getModuleSubType())) return true;
        }
        return false;
    }

    // 강화(SetModule) 요청 전용 — 데이터 존재 여부에 더해, 확정 규칙(무기 티어 상한은 항상 함체 티어)까지 검증
    private boolean isValidSubTypeForCategory(EModuleType moduleType, String requestedSubType, int hullTier) {
        if (isValidSubTypeForCategory(moduleType, requestedSubType) == false) return false;
        return GameDataService.parseTierFromHullSubType(requestedSubType) <= hullTier;
    }

    // 실드는 리스트가 아니라 문자열 하나(장착 여부)뿐 — 슬롯 인덱스는 항상 0. 티어 선택 가능(datatable_module 티어1~14)
    // maxSlotCount<=0(실드 슬롯 없는 함체)인데 장착 요청이 오면 슬롯 인덱스 검증과 동일하게 거부
    // 요청된 티어는 클라를 신뢰하지 않고 무기 카테고리와 동일하게 검증(존재 여부 + 함체 티어 이하) 후 사용, 아니면 기본 티어1로 폴백
    private void appendDesiredShield(List<DesiredModule> target, int maxSlotCount, int hullTier, String requestedShieldSubType) {
        if (requestedShieldSubType == null || requestedShieldSubType.isEmpty()) return;
        if (maxSlotCount <= 0)
            throw new BusinessException(ServerErrorCode.SET_FLEET_MODULE_FAIL_INVALID_SLOT_INDEX);

        String subType = isValidSubTypeForCategory(EModuleType.shield, requestedShieldSubType, hullTier)
                ? requestedShieldSubType
                : "shield_1_1";
        target.add(new DesiredModule(EModuleType.shield, 0, subType));
    }

    // 요격체도 실드와 동일하게 문자열 하나(장착 여부) — 슬롯 인덱스는 항상 0
    private void appendDesiredInterceptor(List<DesiredModule> target, int maxSlotCount, int hullTier, String requestedInterceptorSubType) {
        if (requestedInterceptorSubType == null || requestedInterceptorSubType.isEmpty()) return;
        if (maxSlotCount <= 0)
            throw new BusinessException(ServerErrorCode.SET_FLEET_MODULE_FAIL_INVALID_SLOT_INDEX);

        String subType = isValidSubTypeForCategory(EModuleType.interceptor, requestedInterceptorSubType, hullTier)
                ? requestedInterceptorSubType
                : "interceptor_1_1";
        target.add(new DesiredModule(EModuleType.interceptor, 0, subType));
    }

    // 함대편성 슬롯 전/후방 토글 저장
    @Transactional
    public void setFleetShipFront(Long commanderId, FleetSetFrontRequest request) {
        Fleet fleet = fleetRepository.findByCommanderIdAndFleetIndex(commanderId, 0)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.COMMANDER_CONTROLLER_FAIL_NULL_ACTIVE_FLEET));

        fleet.getShips().stream()
                .filter(s -> s.getSlotIndex() == request.getSlotIndex())
                .findFirst()
                .ifPresent(ship -> ship.setFront(request.getIsFront()));
    }

    // 함대편성 슬롯의 함선을 배치 해제 — orphanRemoval로 Ship(+그 modules)이 DB에서 완전히 삭제됨.
    // 컬렉션 참조 자체를 새 List로 갈아끼우면 orphanRemoval이 깨지므로 removeIf로 기존 리스트를 직접 수정.
    // 클라 FleetComposition.RemoveShip(List.RemoveAt)이 뒤 슬롯들을 한 칸씩 당겨 압축하는 것과 동일하게,
    // 삭제된 슬롯보다 큰 slotIndex를 전부 -1 해야 "빈 슬롯은 항상 꼬리에만" 불변식이 서버-클라 양쪽에서 일치함
    @Transactional
    public void removeFleetShip(Long commanderId, FleetRemoveShipRequest request) {
        Commander commander = commanderRepository.findByIdForUpdate(commanderId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.REMOVE_FLEET_SHIP_FAIL_COMMANDER_NOT_FOUND));

        Fleet fleet = fleetRepository.findByCommanderIdAndFleetIndex(commanderId, 0)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.COMMANDER_CONTROLLER_FAIL_NULL_ACTIVE_FLEET));

        int removedSlotIndex = request.getSlotIndex();
        fleet.getShips().removeIf(s -> s.getSlotIndex() == removedSlotIndex);
        for (Ship ship : fleet.getShips()) {
            if (ship.getSlotIndex() > removedSlotIndex)
                ship.setSlotIndex(ship.getSlotIndex() - 1);
        }
        fleetRepository.save(fleet);
    }

    // fleetId는 Fleet.id(FleetInfoDto.id로 클라에 내려준 값) — 미지정(0/null)이면 활성 함대(fleetIndex=0)로 폴백
    @Transactional
    public ChangeTacticOptionsResponse changeTacticOptions(Long commanderId, ChangeTacticOptionsRequest request) {
        Fleet fleet;

        if (request.getFleetId() == null || request.getFleetId() == 0) {
            fleet = fleetRepository.findByCommanderIdAndFleetIndex(commanderId, 0)
                    .orElseThrow(() -> new BusinessException(ServerErrorCode.FLEET_NOT_FOUND));
        } else {
            fleet = fleetRepository.findByIdAndCommanderId(request.getFleetId(), commanderId)
                    .orElseThrow(() -> new BusinessException(ServerErrorCode.FLEET_NOT_FOUND));
        }

        fleet.setTacticOptions(request.getTacticOptions());
        fleet.setModified(Instant.now());
        fleetRepository.save(fleet);

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
            if (ship.getHulls() == null) continue;
            for (ModuleHullInfoDto hull : ship.getHulls()) {
                ModuleData hullData = findModuleData(EModuleType.hull, hull.getModuleSubType());
                if (hullData != null) statHealth += hullData.getHealth() != null ? hullData.getHealth() : 0f;

                if (hull.getBeams() != null) {
                    for (ModuleInfoDto beam : hull.getBeams()) {
                        ModuleData data = findModuleData(EModuleType.beam, beam.getModuleSubType());
                        if (data != null) {
                            statAttack += data.getAttack() != null ? data.getAttack() : 0f;
                        }
                    }
                }
                if (hull.getMissiles() != null) {
                    for (ModuleInfoDto missile : hull.getMissiles()) {
                        ModuleData data = findModuleData(EModuleType.missile, missile.getModuleSubType());
                        if (data != null) {
                            statAttack += data.getAttack() != null ? data.getAttack() : 0f;
                        }
                    }
                }
                if (hull.getHangars() != null) {
                    for (ModuleInfoDto hangar : hull.getHangars()) {
                        ModuleData data = findModuleData(EModuleType.hangar, hangar.getModuleSubType());
                        if (data != null) {
                            statAirCount  += data.getAirCount() != null ? data.getAirCount() : 0;
                            statAirAttack += data.getAirAttackToShip() != null ? data.getAirAttackToShip() : 0f;
                        }
                    }
                }
            }
        }

        return String.format("{\"shipCount\":%d,\"statHealth\":%.4f,\"statAttack\":%.4f,\"statAirCount\":%d,\"statAirAttack\":%.4f}",
                shipCount, statHealth, statAttack, statAirCount, statAirAttack);
    }

    private ModuleData findModuleData(EModuleType type, String subType) {
        if (subType == null) return null;
        List<ModuleData> list = gameDataService.getModulesByType(type);
        for (ModuleData data : list) {
            if (subType.equals(data.getModuleSubType())) return data;
        }
        return null;
    }
}
