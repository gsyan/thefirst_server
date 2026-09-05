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

    // 신규 커맨더에게 지급되는 기본 함대(fleetIndex=0)의 초기 함선 — 함체 hull_3_1_11100(빔1/미사일1/격납고1) + 기본 빔1 장착
    private static final String DEFAULT_FLEET_HULL_SUB_TYPE = "hull_3_1_11100";

    public FleetService(CommanderRepository commanderRepository,
                       GameDataService gameDataService,
                       FleetRepository fleetRepository) {
        this.commanderRepository = commanderRepository;
        this.gameDataService = gameDataService;
        this.fleetRepository = fleetRepository;
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
        replaceShipModules(ship, buildDefaultModules(ship));
        fleet.setShips(new ArrayList<>(List.of(ship)));

        fleetRepository.save(fleet);
    }

    // 로그인 시 내려주는 "내 함대" — fleetIndex=0 함대를 FleetInfoDto로 변환, 함선별 실제 장착 모듈(hulls)까지 포함
    // Fleet.ships가 LAZY라 convertFleetToFleetInfoDto에서 컬렉션을 순회하는 동안 세션이 열려 있어야 함 — @Transactional 필수
    @Transactional(readOnly = true)
    public FleetInfoDto getActiveFleet(Long commanderId) {
        Fleet fleet = fleetRepository.findByCommanderIdAndFleetIndex(commanderId, 0)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.COMMANDER_CONTROLLER_FAIL_NULL_ACTIVE_FLEET));
        return convertFleetToFleetInfoDto(fleet);
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
        int unlockCommanderLevel = hullData.getUnlockCommanderLevel() != null ? hullData.getUnlockCommanderLevel() : 1;
        if (unlockCommanderLevel > commander.getCommanderLevel())
            throw new BusinessException(ServerErrorCode.PLACE_FLEET_SHIP_FAIL_INSUFFICIENT_COMMANDER_LEVEL);

        int openSlotCount = gameDataService.getShipCount(commander.getCommanderLevel());
        if (request.getSlotIndex() < 0 || request.getSlotIndex() >= openSlotCount)
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
        int[] newMaxSlots = GameDataService.parseMaxSlotsFromHullSubType(request.getHullSubType());
        List<Module> keptModules = existingModules.isEmpty() == false
                ? filterModulesForNewHull(existingModules, newMaxSlots, request.getHullSubType(), ship)
                : buildDefaultModules(ship);

        boolean hasAttackModule = keptModules.stream().anyMatch(m -> isAttackModuleType(m.getModuleType()));
        if (hasAttackModule == false)
            throw new BusinessException(ServerErrorCode.PLACE_FLEET_SHIP_FAIL_NO_ATTACK_MODULE_REMAINING);

        int newShipCost = hullData.getStatPoint() != null ? hullData.getStatPoint() : 0;
        for (Module m : keptModules) {
            int installCost = getModuleStatPoint(m.getModuleType(), m.getModuleSubType());
            int reinforceCost = m.getAttackPoints() + m.getAttackToFighterPoints();
            newShipCost += installCost + reinforceCost;
        }

        int usedByOtherShips = 0;
        for (Ship s : fleet.getShips()) {
            if (s.getSlotIndex() == request.getSlotIndex()) continue;
            usedByOtherShips += computeShipCommandCost(s);
        }
        if (usedByOtherShips + newShipCost > commander.getCommandPowerMax())
            throw new BusinessException(ServerErrorCode.PLACE_FLEET_SHIP_FAIL_NOT_ENOUGH_COMMAND_POWER);

        ship.setHullSubType(request.getHullSubType());
        ship.setFront(request.getIsFront());
        replaceShipModules(ship, keptModules);
        fleetRepository.save(fleet);
    }

    // 기존 장착 모듈 중 새 함체(newMaxSlots)에도 같은 카테고리+슬롯 인덱스가 존재하는 것만 유지 — 강화 포인트는 그대로 복사
    // 확정 규칙: 무기 티어는 함체 티어와 독립적인 별도 축이지만 상한은 항상 함체 티어 — 새 함체 티어를 넘는 기존 모듈은 함체 티어로 자동 다운그레이드됨
    // (지휘력 회수는 별도 처리 불필요 — 호출부 placeFleetShip이 이 결과의 statPoint로 비용을 재계산하므로 낮아진 티어가 자동 반영됨)
    private List<Module> filterModulesForNewHull(List<Module> existingModules, int[] newMaxSlots, String newHullSubType, Ship targetShip) {
        int newHullTier = GameDataService.parseTierFromHullSubType(newHullSubType);
        List<Module> kept = new ArrayList<>();
        for (Module old : existingModules) {
            int maxSlotForCategory = getMaxSlotForCategory(newMaxSlots, old.getModuleType());
            if (old.getSlotIndex() >= maxSlotForCategory) continue;

            Module module = new Module();
            module.setShip(targetShip);
            module.setModuleType(old.getModuleType());
            module.setSlotIndex(old.getSlotIndex());
            module.setModuleSubType(clampModuleTierToHull(old.getModuleType(), old.getModuleSubType(), newHullTier));
            module.setAttackPoints(old.getAttackPoints());
            module.setAttackToFighterPoints(old.getAttackToFighterPoints());
            kept.add(module);
        }
        return kept;
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
        return 0;
    }

    // 기본 로드아웃(beam slot0=beam_1_1)을 상수 규칙으로 생성 — 전 함체 공통, 무기 티어는 함체와 독립적인 별도 축. 반영(저장)은 호출부 책임
    private List<Module> buildDefaultModules(Ship ship) {
        Module module = new Module();
        module.setShip(ship);
        module.setModuleType(EModuleType.beam);
        module.setSlotIndex(0);
        module.setModuleSubType("beam_1_1");
        return new ArrayList<>(List.of(module));
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

    private boolean isAttackModuleType(EModuleType moduleType) {
        return moduleType == EModuleType.beam || moduleType == EModuleType.missile || moduleType == EModuleType.hangar;
    }

    private int getModuleStatPoint(EModuleType moduleType, String subType) {
        if (subType == null) return 0;
        List<ModuleData> modules = gameDataService.getModulesByType(moduleType);
        for (ModuleData data : modules) {
            if (subType.equals(data.getModuleSubType()))
                return data.getStatPoint() != null ? data.getStatPoint() : 0;
        }
        return 0;
    }

    private int computeHullCost(String hullSubType) {
        ModuleData hullData = gameDataService.getHullModuleData(hullSubType);
        return hullData != null && hullData.getStatPoint() != null ? hullData.getStatPoint() : 0;
    }

    // 함체 설치비 + 현재 장착된 모든 모듈의 설치비/강화 포인트 합 — 클라 ShipStatAllocation.GetTotalPointsUsed와 동일한 계산
    private int computeShipCommandCost(Ship ship) {
        int hullCost = computeHullCost(ship.getHullSubType());

        int modulesCost = 0;
        if (ship.getModules() != null) {
            for (Module module : ship.getModules()) {
                int installCost = getModuleStatPoint(module.getModuleType(), module.getModuleSubType());
                int reinforceCost = module.getAttackPoints() + module.getAttackToFighterPoints();
                modulesCost += installCost + reinforceCost;
            }
        }
        return hullCost + modulesCost;
    }

    private ModuleHullInfoDto buildModuleHullInfoDto(Ship ship) {
        List<ModuleInfoDto> beams = new ArrayList<>();
        List<ModuleInfoDto> missiles = new ArrayList<>();
        List<ModuleInfoDto> hangars = new ArrayList<>();
        String shieldModuleSubType = "";

        if (ship.getModules() != null) {
            for (Module module : ship.getModules()) {
                ModuleInfoDto dto = ModuleInfoDto.builder()
                        .moduleType(module.getModuleType())
                        .moduleSubType(module.getModuleSubType())
                        .slotIndex(module.getSlotIndex())
                        .attackPoints(module.getAttackPoints())
                        .attackToFighterPoints(module.getAttackToFighterPoints())
                        .build();
                switch (module.getModuleType()) {
                    case beam -> beams.add(dto);
                    case missile -> missiles.add(dto);
                    case hangar -> hangars.add(dto);
                    case shield -> shieldModuleSubType = module.getModuleSubType();
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

        int[] maxSlots = GameDataService.parseMaxSlotsFromHullSubType(ship.getHullSubType());
        int hullTier = GameDataService.parseTierFromHullSubType(ship.getHullSubType());
        ModuleHullInfoDto requestedModules = request.getModules();

        List<DesiredModule> desired = new ArrayList<>();
        appendDesiredModules(desired, EModuleType.beam, maxSlots[0], hullTier, requestedModules != null ? requestedModules.getBeams() : null);
        appendDesiredModules(desired, EModuleType.missile, maxSlots[1], hullTier, requestedModules != null ? requestedModules.getMissiles() : null);
        appendDesiredModules(desired, EModuleType.hangar, maxSlots[2], hullTier, requestedModules != null ? requestedModules.getHangars() : null);
        appendDesiredShield(desired, maxSlots[3], requestedModules != null ? requestedModules.getShieldModuleSubType() : null);

        boolean hasAttackModule = desired.stream().anyMatch(m -> isAttackModuleType(m.moduleType()));
        if (hasAttackModule == false)
            throw new BusinessException(ServerErrorCode.SET_FLEET_MODULE_FAIL_NO_ATTACK_MODULE_REMAINING);

        int newShipCost = computeHullCost(ship.getHullSubType());
        for (DesiredModule m : desired) {
            int installCost = getModuleStatPoint(m.moduleType(), m.moduleSubType());
            int reinforceCost = m.attackPoints() + m.attackToFighterPoints();
            newShipCost += installCost + reinforceCost;
        }

        int usedByOtherShips = 0;
        for (Ship s : fleet.getShips()) {
            if (s.getSlotIndex() == ship.getSlotIndex()) continue;
            usedByOtherShips += computeShipCommandCost(s);
        }
        if (usedByOtherShips + newShipCost > commander.getCommandPowerMax())
            throw new BusinessException(ServerErrorCode.SET_FLEET_MODULE_FAIL_NOT_ENOUGH_COMMAND_POWER);

        List<Module> newModules = new ArrayList<>();
        for (DesiredModule m : desired) {
            Module module = new Module();
            module.setShip(ship);
            module.setModuleType(m.moduleType());
            module.setSlotIndex(m.slotIndex());
            module.setModuleSubType(m.moduleSubType());
            module.setAttackPoints(m.attackPoints());
            module.setAttackToFighterPoints(m.attackToFighterPoints());
            newModules.add(module);
        }
        replaceShipModules(ship, newModules);
        fleetRepository.save(fleet);

        int remainingAfter = commander.getCommandPowerMax() - (usedByOtherShips + newShipCost);

        return SetModuleResponse.builder()
                .hull(buildModuleHullInfoDto(ship))
                .commandCost(newShipCost)
                .remainingCommandPower(remainingAfter)
                .build();
    }

    private record DesiredModule(EModuleType moduleType, int slotIndex, String moduleSubType, int attackPoints, int attackToFighterPoints) { }

    // requested의 각 항목이 유효한 슬롯 인덱스(0 <= idx < maxSlotCount)인지, 중복 슬롯이 없는지 검증하며 desired 목록에 채워 넣음
    // 강화 포인트는 클라 입력을 신뢰하지 않고 서버가 직접 clamp. moduleSubType(티어)도 마찬가지로 클라가 보낸 값을 그대로 믿지 않고
    // 이 카테고리에 실제로 존재하는 데이터인지 + 함체 티어를 넘지 않는지 검증한 뒤에만 사용(티어업/다운) — 없거나 비어있거나 함체 티어 초과면 기본 1티어로 폴백
    private void appendDesiredModules(List<DesiredModule> target, EModuleType moduleType, int maxSlotCount, int hullTier, List<ModuleInfoDto> requested) {
        if (requested == null) return;
        String defaultSubType = getDefaultSubTypeForCategory(moduleType);
        int maxPerSlot = gameDataService.getMaxAttackReinforcePointsPerSlot();
        boolean isHangar = moduleType == EModuleType.hangar;

        java.util.Set<Integer> seenSlotIndexes = new java.util.HashSet<>();
        for (ModuleInfoDto item : requested) {
            Integer slotIndex = item.getSlotIndex();
            if (slotIndex == null || slotIndex < 0 || slotIndex >= maxSlotCount)
                throw new BusinessException(ServerErrorCode.SET_FLEET_MODULE_FAIL_INVALID_SLOT_INDEX);
            if (seenSlotIndexes.add(slotIndex) == false)
                throw new BusinessException(ServerErrorCode.SET_FLEET_MODULE_FAIL_INVALID_SLOT_INDEX); // 같은 슬롯이 중복 요청됨

            int clampedAttackPoints = clampReinforcePoints(item.getAttackPoints(), maxPerSlot);
            int clampedFighterPoints = isHangar ? clampReinforcePoints(item.getAttackToFighterPoints(), maxPerSlot) : 0;

            String requestedSubType = item.getModuleSubType();
            String subType = (requestedSubType != null && isValidSubTypeForCategory(moduleType, requestedSubType, hullTier))
                    ? requestedSubType
                    : defaultSubType;

            target.add(new DesiredModule(moduleType, slotIndex, subType, clampedAttackPoints, clampedFighterPoints));
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

    // 실드는 리스트가 아니라 문자열 하나(장착 여부)뿐 — 슬롯 인덱스는 항상 0, 강화 포인트도 아직 없음(on/off만 지원)
    // maxSlotCount<=0(실드 슬롯 없는 함체)인데 장착 요청이 오면 슬롯 인덱스 검증과 동일하게 거부
    private void appendDesiredShield(List<DesiredModule> target, int maxSlotCount, String requestedShieldSubType) {
        if (requestedShieldSubType == null || requestedShieldSubType.isEmpty()) return;
        if (maxSlotCount <= 0)
            throw new BusinessException(ServerErrorCode.SET_FLEET_MODULE_FAIL_INVALID_SLOT_INDEX);

        target.add(new DesiredModule(EModuleType.shield, 0, "shield_1_1", 0, 0));
    }

    // 클라가 보낸 강화 포인트 값을 0~maxPerSlot 범위로 강제 — null/음수/상한 초과 모두 방어
    private int clampReinforcePoints(Integer requested, int maxPerSlot) {
        int rawValue = requested != null ? requested : 0;
        int nonNegativeValue = Math.max(0, rawValue);
        return Math.min(nonNegativeValue, maxPerSlot);
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
                            statAirAttack += data.getAirAttack() != null ? data.getAirAttack() : 0f;
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
