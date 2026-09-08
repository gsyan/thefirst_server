package com.bk.sbs.service;

import com.bk.sbs.config.DataTableDailyBonus;
import com.bk.sbs.dto.DailyBonusStatusResponse;
import com.bk.sbs.dto.DailyClaimResponse;
import com.bk.sbs.dto.VipPurchaseRequest;
import com.bk.sbs.dto.VipStatusResponse;
import com.bk.sbs.entity.Commander;
import com.bk.sbs.entity.VipSubscription;
import com.bk.sbs.enums.EDailyBonusRewardType;
import com.bk.sbs.enums.EDailyBonusTier;
import com.bk.sbs.repository.CommanderRepository;
import com.bk.sbs.exception.BusinessException;
import com.bk.sbs.exception.ServerErrorCode;
import com.bk.sbs.repository.VipSubscriptionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class IapService {

    private static final String PLAY_API_SCOPE = "https://www.googleapis.com/auth/androidpublisher";
    private static final String PRODUCTS_URL =
            "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/%s/purchases/products/%s/tokens/%s";

    @Value("${google.play.package-name}")
    private String packageName;

    @Value("${google.play.vip-product-id}")
    private String vipProductId;

    private final VipSubscriptionRepository vipSubscriptionRepository;
    private final CommanderRepository commanderRepository;
    private final ObjectMapper objectMapper;
    private final GameDataService gameDataService;
    private final HttpClient httpClient;

    public IapService(VipSubscriptionRepository vipSubscriptionRepository,
                      CommanderRepository commanderRepository,
                      ObjectMapper objectMapper,
                      GameDataService gameDataService) {
        this.vipSubscriptionRepository = vipSubscriptionRepository;
        this.commanderRepository = commanderRepository;
        this.objectMapper = objectMapper;
        this.gameDataService = gameDataService;
        this.httpClient = HttpClient.newHttpClient();
    }

    // ── 구매 처리 ───────────────────────────────────────────────────────────

    @Transactional
    public VipStatusResponse purchaseVip(Long commanderId, VipPurchaseRequest request) {
        String purchaseToken = extractPurchaseToken(request.getReceipt());

        // 동일 토큰 중복 처리 방지
        if (vipSubscriptionRepository.existsByPurchaseToken(purchaseToken) == true) {
            log.warn("[IAP] 이미 처리된 purchaseToken: {}", purchaseToken);
            return getVipStatus(commanderId);
        }

        // 활성 VIP 기간 중 재구매 방지
        Optional<VipSubscription> existing = vipSubscriptionRepository.findByCommanderId(commanderId);
        if (existing.isPresent() == true) {
            VipSubscription current = existing.get();
            if (current.getVipExpiry() != null && Instant.now().isBefore(current.getVipExpiry())) {
                log.warn("[IAP] 활성 VIP 기간 중 재구매 시도 commanderId={}", commanderId);
                throw new BusinessException(ServerErrorCode.IAP_PURCHASE_FAIL_ALREADY_ACTIVE);
            }
        }

        Instant purchaseTime = verifyGooglePlayConsumable(purchaseToken);

        // 구매 월 말일 23:59:59 UTC를 만료 시각으로 설정
        ZonedDateTime purchaseZdt = purchaseTime.atZone(ZoneOffset.UTC);
        Instant expiry = purchaseZdt.with(TemporalAdjusters.lastDayOfMonth())
                .withHour(23).withMinute(59).withSecond(59).withNano(0).toInstant();

        if (existing.isPresent() == true) {
            VipSubscription sub = existing.get();
            sub.setPurchaseToken(purchaseToken);
            sub.setPlatform(request.getPlatform());
            sub.setVipExpiry(expiry);
            sub.setUpdatedAt(Instant.now());
            vipSubscriptionRepository.save(sub);
        } else {
            vipSubscriptionRepository.save(
                    new VipSubscription(commanderId, expiry, purchaseToken, request.getPlatform())
            );
        }

        log.info("[IAP] VIP 구매 완료 commanderId={} expiry={}", commanderId, expiry);
        return getVipStatus(commanderId);
    }

    // ── 에디터 전용: VIP 강제 세팅 (영수증 검증 없이 이번 달 말일로 세팅) ──────

    @Transactional
    public VipStatusResponse debugForceVip(Long commanderId) {
        Instant now = Instant.now();
        ZonedDateTime nowZdt = now.atZone(ZoneOffset.UTC);
        Instant expiry = nowZdt.with(TemporalAdjusters.lastDayOfMonth())
                .withHour(23).withMinute(59).withSecond(59).withNano(0).toInstant();

        Optional<VipSubscription> existing = vipSubscriptionRepository.findByCommanderId(commanderId);
        if (existing.isPresent() == true) {
            VipSubscription sub = existing.get();
            sub.setVipExpiry(expiry);
            sub.setUpdatedAt(now);
            vipSubscriptionRepository.save(sub);
        } else {
            vipSubscriptionRepository.save(
                    new VipSubscription(commanderId, expiry, "debug-editor", "Editor")
            );
        }

        log.info("[IAP][DEBUG] 에디터 VIP 강제 세팅 commanderId={} expiry={}", commanderId, expiry);
        return getVipStatus(commanderId);
    }

    // ── VIP 상태 조회 ────────────────────────────────────────────────────────

    public VipStatusResponse getVipStatus(Long commanderId) {
        Optional<VipSubscription> sub = vipSubscriptionRepository.findByCommanderId(commanderId);
        Instant expiry = sub.isPresent() ? sub.get().getVipExpiry() : null;
        return buildStatusResponse(expiry);
    }

    // ── 일일 로그인 보상 (6일 주기, 매주 월요일 UTC 0시 리셋) ──────────────

    private static final int DAILY_BONUS_CYCLE_DAYS = 6;

    // 조회(getDailyBonusStatus)/수령(claimDailyReward) 공용 — DB에 쓰지 않고 "지금 이 순간"의 상태만 계산
    private DailyBonusState computeDailyBonusState(Commander commander, LocalDate today) {
        LocalDate currentWeekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate savedWeekStart = commander.getLoginRewardWeekStart();
        boolean isNewWeek = savedWeekStart == null || savedWeekStart.isEqual(currentWeekStart) == false;

        int effectiveMask    = isNewWeek ? 0 : commander.getClaimedDaysMask();
        int effectiveVipMask = isNewWeek ? 0 : commander.getVipClaimedDaysMask();
        boolean claimedToday = isNewWeek ? false : today.equals(commander.getLastDailyClaimDate());
        int todayInWeek = Integer.bitCount(effectiveMask) + (claimedToday ? 0 : 1);

        return new DailyBonusState(currentWeekStart, isNewWeek, effectiveMask, effectiveVipMask, claimedToday, todayInWeek);
    }

    private static class DailyBonusState {
        final LocalDate currentWeekStart;
        final boolean isNewWeek;
        final int effectiveMask;
        final int effectiveVipMask;
        final boolean claimedToday;
        final int todayInWeek;

        DailyBonusState(LocalDate currentWeekStart, boolean isNewWeek, int effectiveMask, int effectiveVipMask,
                        boolean claimedToday, int todayInWeek) {
            this.currentWeekStart = currentWeekStart;
            this.isNewWeek = isNewWeek;
            this.effectiveMask = effectiveMask;
            this.effectiveVipMask = effectiveVipMask;
            this.claimedToday = claimedToday;
            this.todayInWeek = todayInWeek;
        }
    }

    // 조회 전용 — 지급 없이 오늘 수령 가능 여부만 확인(로그인 시점 레드닷 갱신용)
    public DailyBonusStatusResponse getDailyBonusStatus(Long commanderId) {
        Commander commander = commanderRepository.findById(commanderId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.IAP_DAILY_CLAIM_FAIL_COMMANDER_NOT_FOUND));

        ZonedDateTime nowUtc = Instant.now().atZone(ZoneOffset.UTC);
        LocalDate today = nowUtc.toLocalDate();
        DailyBonusState state = computeDailyBonusState(commander, today);

        boolean available = state.todayInWeek <= DAILY_BONUS_CYCLE_DAYS && state.claimedToday == false;
        Instant nextMidnightUtc = nowUtc.withHour(0).withMinute(0).withSecond(0).withNano(0).plusDays(1).toInstant();

        return DailyBonusStatusResponse.builder()
                .available(available)
                .todayDay(state.todayInWeek)
                .claimedDaysMask(state.effectiveMask)
                .vipClaimedDaysMask(state.effectiveVipMask)
                .loginRewardWeekStart(state.currentWeekStart.toString())
                .nextAvailableAt(DateTimeFormatter.ISO_INSTANT.format(nextMidnightUtc))
                .build();
    }

    // 수령 전용 — 달력 팝업의 오늘 칸 Claim 버튼에서만 호출됨
    @Transactional
    public DailyClaimResponse claimDailyReward(Long commanderId) {
        Commander commander = commanderRepository.findByIdForUpdate(commanderId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.IAP_DAILY_CLAIM_FAIL_COMMANDER_NOT_FOUND));

        ZonedDateTime nowUtc = Instant.now().atZone(ZoneOffset.UTC);
        LocalDate today = nowUtc.toLocalDate();
        DailyBonusState state = computeDailyBonusState(commander, today);

        if (state.isNewWeek == true) {
            commander.setClaimedDaysMask(0);
            commander.setVipClaimedDaysMask(0);
            commander.setLoginRewardWeekStart(state.currentWeekStart);
            commander.setLastDailyClaimDate(null);
        }

        Instant nextMidnightUtc = nowUtc.withHour(0).withMinute(0).withSecond(0).withNano(0).plusDays(1).toInstant();
        String nextAvailableAt = DateTimeFormatter.ISO_INSTANT.format(nextMidnightUtc);

        boolean available = false;
        int grantedExplorationPoint = 0;
        int grantedAchievementPoint = 0;

        if (state.todayInWeek <= DAILY_BONUS_CYCLE_DAYS && state.claimedToday == false) {
            boolean isVip = isVipActive(commanderId);
            int todayBit = 1 << (state.todayInWeek - 1);

            DataTableDailyBonus dataTableDailyBonus = gameDataService.getDataTableDailyBonus();
            List<DataTableDailyBonus.RewardEntry> normalRewards = dataTableDailyBonus.getRewards(state.todayInWeek, EDailyBonusTier.Normal);
            for (DataTableDailyBonus.RewardEntry reward : normalRewards) {
                if (reward.getRewardType() == EDailyBonusRewardType.ExplorationPoint)
                    grantedExplorationPoint += reward.getAmount();
                else if (reward.getRewardType() == EDailyBonusRewardType.AchievementPoint)
                    grantedAchievementPoint += reward.getAmount();
            }

            boolean vipGranted = false;
            if (isVip == true) {
                List<DataTableDailyBonus.RewardEntry> vipRewards = dataTableDailyBonus.getRewards(state.todayInWeek, EDailyBonusTier.VIP);
                for (DataTableDailyBonus.RewardEntry reward : vipRewards) {
                    if (reward.getAmount() <= 0) continue;
                    vipGranted = true;
                    if (reward.getRewardType() == EDailyBonusRewardType.ExplorationPoint)
                        grantedExplorationPoint += reward.getAmount();
                    else if (reward.getRewardType() == EDailyBonusRewardType.AchievementPoint)
                        grantedAchievementPoint += reward.getAmount();
                }
            }

            commander.setClaimedDaysMask(state.effectiveMask | todayBit);
            if (vipGranted == true)
                commander.setVipClaimedDaysMask(state.effectiveVipMask | todayBit);
            commander.setLastDailyClaimDate(today);

            if (grantedExplorationPoint > 0) {
                commander.setExplorationPoint(commander.getExplorationPoint() + grantedExplorationPoint);
                commander.setExplorationPointEarnedTotal(commander.getExplorationPointEarnedTotal() + grantedExplorationPoint);
            }
            if (grantedAchievementPoint > 0)
                commander.setAchievementPoint(commander.getAchievementPoint() + grantedAchievementPoint);

            available = grantedExplorationPoint > 0 || grantedAchievementPoint > 0;

            log.info("[IAP] 일일 로그인 보상 commanderId={} day={} exploration={} achievement={} vip={} mask={} vipMask={}",
                    commanderId, state.todayInWeek, grantedExplorationPoint, grantedAchievementPoint, isVip,
                    commander.getClaimedDaysMask(), commander.getVipClaimedDaysMask());
        }

        commanderRepository.save(commander);

        return DailyClaimResponse.builder()
                .available(available)
                .grantedExplorationPoint(grantedExplorationPoint)
                .grantedAchievementPoint(grantedAchievementPoint)
                .explorationPointRemain(commander.getExplorationPoint())
                .achievementPointRemain(commander.getAchievementPoint())
                .nextAvailableAt(state.todayInWeek <= DAILY_BONUS_CYCLE_DAYS ? nextAvailableAt : null)
                .todayDay(state.todayInWeek)
                .claimedDaysMask(commander.getClaimedDaysMask())
                .vipClaimedDaysMask(commander.getVipClaimedDaysMask())
                .loginRewardWeekStart(commander.getLoginRewardWeekStart().toString())
                .build();
    }

    // 활성 VIP 여부 — getVipStatus()의 만료시각 판정과 동일한 기준
    private boolean isVipActive(Long commanderId) {
        Optional<VipSubscription> sub = vipSubscriptionRepository.findByCommanderId(commanderId);
        Instant expiry = sub.isPresent() ? sub.get().getVipExpiry() : null;
        return expiry != null && Instant.now().isBefore(expiry);
    }

    // ── 내부 메서드 ──────────────────────────────────────────────────────────

    private VipStatusResponse buildStatusResponse(Instant expiry) {
        boolean isVip = expiry != null && Instant.now().isBefore(expiry);
        String expiryStr = expiry != null ? DateTimeFormatter.ISO_INSTANT.format(expiry) : null;
        return VipStatusResponse.builder()
                .isVip(isVip)
                .vipExpiry(expiryStr)
                .build();
    }

    // Unity IAP 영수증 JSON → purchaseToken 추출
    // 영수증 형태: {"Store":"GooglePlay","Payload":"{\"json\":\"{...purchaseToken...}\"}"}
    private String extractPurchaseToken(String receipt) {
        try {
            JsonNode root = objectMapper.readTree(receipt);
            String payloadStr = root.path("Payload").asText();
            JsonNode payload = objectMapper.readTree(payloadStr);
            String jsonStr = payload.path("json").asText();
            JsonNode purchaseJson = objectMapper.readTree(jsonStr);
            String token = purchaseJson.path("purchaseToken").asText();
            if (token == null || token.isEmpty() == true)
                throw new BusinessException(ServerErrorCode.IAP_PURCHASE_FAIL_INVALID_RECEIPT);
            return token;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[IAP] 영수증 파싱 실패", e);
            throw new BusinessException(ServerErrorCode.IAP_PURCHASE_FAIL_INVALID_RECEIPT);
        }
    }

    // Google Play Developer API (purchases.products) 호출 → 구매 시각 반환
    private Instant verifyGooglePlayConsumable(String purchaseToken) {
        try {
            String accessToken = getGoogleAccessToken();
            String url = String.format(PRODUCTS_URL, packageName, vipProductId, purchaseToken);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + accessToken)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("[IAP] Google Play API 오류 status={} body={}", response.statusCode(), response.body());
                throw new BusinessException(ServerErrorCode.IAP_PURCHASE_FAIL_GOOGLE_VERIFY);
            }

            JsonNode root = objectMapper.readTree(response.body());
            int purchaseState = root.path("purchaseState").asInt(-1);
            if (purchaseState != 0) {
                log.warn("[IAP] 구매 비정상 상태: {}", purchaseState);
                throw new BusinessException(ServerErrorCode.IAP_PURCHASE_FAIL_SUBSCRIPTION_NOT_ACTIVE);
            }

            long purchaseTimeMillis = Long.parseLong(root.path("purchaseTimeMillis").asText("0"));
            return Instant.ofEpochMilli(purchaseTimeMillis);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[IAP] Google Play 검증 중 예외", e);
            throw new BusinessException(ServerErrorCode.IAP_PURCHASE_FAIL_GOOGLE_VERIFY);
        }
    }

    private String getGoogleAccessToken() throws IOException {
        ClassPathResource resource = new ClassPathResource("google-play-service-account.json");
        GoogleCredentials credentials = GoogleCredentials
                .fromStream(resource.getInputStream())
                .createScoped(Collections.singleton(PLAY_API_SCOPE));
        credentials.refreshIfExpired();
        return credentials.getAccessToken().getTokenValue();
    }
}






