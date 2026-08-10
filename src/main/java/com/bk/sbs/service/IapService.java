package com.bk.sbs.service;

import com.bk.sbs.dto.DailyClaimResponse;
import com.bk.sbs.dto.VipPurchaseRequest;
import com.bk.sbs.dto.VipStatusResponse;
import com.bk.sbs.entity.Commander;
import com.bk.sbs.entity.VipSubscription;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.Collections;
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

    // ── 일일 로그인 보상 지급 (고정 10 exploration point) ──────────────

    private static final int DAILY_EXPLORATION_POINT_REWARD = 10;

    @Transactional
    public DailyClaimResponse claimDailyReward(Long commanderId) {
        // 1) 케릭터 확보
        Commander commander = commanderRepository.findByIdForUpdate(commanderId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.IAP_DAILY_CLAIM_FAIL_COMMANDER_NOT_FOUND));

        // 2) 오늘 날짜 계산, UTC 날짜, 월
        Instant now = Instant.now();
        ZonedDateTime nowUtc = now.atZone(ZoneOffset.UTC);
        LocalDate today = nowUtc.toLocalDate();
        int currentMonth = nowUtc.getYear() * 100 + nowUtc.getMonthValue();

        // 3) 새로운 달(month) 되었다면 마스크 클리어, loginRewardMonth 업데이트
        Integer savedMonth = commander.getLoginRewardMonth();
        boolean isNewMonth = savedMonth == null || savedMonth != currentMonth;
        boolean needsSave  = false;
        if (isNewMonth == true) {
            commander.setClaimedDaysMask(0);
            commander.setVipClaimedDaysMask(0);
            commander.setLoginRewardMonth(currentMonth);
            commander.setLastDailyClaimDate(null);
            needsSave = true;
        }
        // 4) 정보 취합
        int currentMask      = commander.getClaimedDaysMask();
        int currentVipMask   = commander.getVipClaimedDaysMask();
        int loginRewardMonth = commander.getLoginRewardMonth();
        // 다음 받을 시간
        Instant nextMidnightUtc = nowUtc.withHour(0).withMinute(0).withSecond(0).withNano(0).plusDays(1).toInstant();
        String nextAvailableAt = DateTimeFormatter.ISO_INSTANT.format(nextMidnightUtc);

        // 5) 출석 순번(todayInMonth) — 같은 날 중복 호출 시 순번이 잘못 증가하지 않도록 lastDailyClaimDate로 가드
        boolean normalAlreadyClaimed = today.equals(commander.getLastDailyClaimDate());
        int todayInMonth = Integer.bitCount(currentMask) + (normalAlreadyClaimed ? 0 : 1);
        int todayBit = 1 << (todayInMonth - 1);
        // 응답 기본값
        boolean available                 = false;
        int grantedExplorationPoint       = 0;
        Integer explorationPointRemain    = null;

        // 6) 28일 이내이면 보상 지급 가능 (todayInMonth는 1부터 28까지)
        if (todayInMonth <= 28) {
            // 일반 보상 처리
            if (normalAlreadyClaimed == false) {
                grantedExplorationPoint += DAILY_EXPLORATION_POINT_REWARD;
                commander.setClaimedDaysMask(currentMask | todayBit);
                commander.setLastDailyClaimDate(today);
                needsSave = true;
            }

            // 실제로 지급된 exploration point가 있을 때만 available 처리
            if (grantedExplorationPoint > 0) {
                commander.setExplorationPoint(commander.getExplorationPoint() + grantedExplorationPoint);

                available               = true;
                explorationPointRemain  = commander.getExplorationPoint();

                log.info("[IAP] 일일 로그인 보상 commanderId={} amount={} day={} mask={} vipMask={}",
                        commanderId, grantedExplorationPoint, todayInMonth, commander.getClaimedDaysMask(), commander.getVipClaimedDaysMask());
            }
        }

        if (needsSave == true)
            commanderRepository.save(commander);

        return DailyClaimResponse.builder()
                .available(available)
                .grantedExplorationPoint(grantedExplorationPoint)
                .explorationPointRemain(explorationPointRemain)
                .nextAvailableAt(todayInMonth <= 28 ? nextAvailableAt : null)
                .todayDay(todayInMonth)
                .claimedDaysMask(commander.getClaimedDaysMask())
                .vipClaimedDaysMask(commander.getVipClaimedDaysMask())
                .loginRewardMonth(loginRewardMonth)
                .build();
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






