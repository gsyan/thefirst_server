package com.bk.sbs.service;

import com.bk.sbs.dto.DailyClaimResponse;
import com.bk.sbs.enums.EDailyBonusTier;
import com.bk.sbs.dto.VipPurchaseRequest;
import com.bk.sbs.dto.VipStatusResponse;
import com.bk.sbs.entity.Character;
import com.bk.sbs.entity.VipSubscription;
import com.bk.sbs.repository.CharacterRepository;
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

    @Value("${vip.mineral-reward-multiplier:4}")
    private int mineralRewardMultiplier;

    private final VipSubscriptionRepository vipSubscriptionRepository;
    private final CharacterRepository characterRepository;
    private final ObjectMapper objectMapper;
    private final GameDataService gameDataService;
    private final HttpClient httpClient;

    public IapService(VipSubscriptionRepository vipSubscriptionRepository,
                      CharacterRepository characterRepository,
                      ObjectMapper objectMapper,
                      GameDataService gameDataService) {
        this.vipSubscriptionRepository = vipSubscriptionRepository;
        this.characterRepository = characterRepository;
        this.objectMapper = objectMapper;
        this.gameDataService = gameDataService;
        this.httpClient = HttpClient.newHttpClient();
    }

    // ── 구매 처리 ───────────────────────────────────────────────────────────

    @Transactional
    public VipStatusResponse purchaseVip(Long characterId, VipPurchaseRequest request) {
        String purchaseToken = extractPurchaseToken(request.getReceipt());

        // 동일 토큰 중복 처리 방지
        if (vipSubscriptionRepository.existsByPurchaseToken(purchaseToken) == true) {
            log.warn("[IAP] 이미 처리된 purchaseToken: {}", purchaseToken);
            return getVipStatus(characterId);
        }

        // 활성 VIP 기간 중 재구매 방지
        Optional<VipSubscription> existing = vipSubscriptionRepository.findByCharacterId(characterId);
        if (existing.isPresent() == true) {
            VipSubscription current = existing.get();
            if (current.getVipExpiry() != null && Instant.now().isBefore(current.getVipExpiry())) {
                log.warn("[IAP] 활성 VIP 기간 중 재구매 시도 characterId={}", characterId);
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
                    new VipSubscription(characterId, expiry, purchaseToken, request.getPlatform())
            );
        }

        log.info("[IAP] VIP 구매 완료 characterId={} expiry={}", characterId, expiry);
        return getVipStatus(characterId);
    }

    // ── VIP 상태 조회 ────────────────────────────────────────────────────────

    public VipStatusResponse getVipStatus(Long characterId) {
        Optional<VipSubscription> sub = vipSubscriptionRepository.findByCharacterId(characterId);
        Instant expiry = sub.isPresent() ? sub.get().getVipExpiry() : null;
        return buildStatusResponse(expiry);
    }

    // ── 일일 로그인 보상 지급 (무과금+VIP 통합) ──────────────────────────────

    @Transactional
    public DailyClaimResponse claimDailyReward(Long characterId) {
        Character character = characterRepository.findByIdForUpdate(characterId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.IAP_DAILY_MINERAL_CHARACTER_NOT_FOUND));

        Instant now = Instant.now();
        ZonedDateTime nowUtc = now.atZone(ZoneOffset.UTC);
        int todayInMonth = nowUtc.getDayOfMonth();
        int currentMonth = nowUtc.getYear() * 100 + nowUtc.getMonthValue();

        Integer savedMonth = character.getLoginRewardMonth();
        boolean isNewMonth = savedMonth == null || savedMonth != currentMonth;
        int currentMask = isNewMonth ? 0 : character.getClaimedDaysMask();
        int currentRewardMonth = isNewMonth ? currentMonth : (savedMonth != null ? savedMonth : currentMonth);

        int currentVipMask = isNewMonth ? 0 : character.getVipClaimedDaysMask();

        // 테이블에 오늘 날짜 Normal 보상이 없으면 클레임 불가
        int tableMineral = gameDataService.getDailyMineralForDay(todayInMonth, EDailyBonusTier.Normal);
        if (tableMineral < 0) {
            return DailyClaimResponse.builder()
                    .available(false)
                    .todayDay(todayInMonth)
                    .claimedDaysMask(currentMask)
                    .vipClaimedDaysMask(currentVipMask)
                    .loginRewardMonth(currentRewardMonth)
                    .build();
        }

        // 다음날 UTC 00:00
        Instant nextMidnightUtc = nowUtc.withHour(0).withMinute(0).withSecond(0).withNano(0).plusDays(1).toInstant();

        Instant last = character.getLastLoginRewardAt();
        ZonedDateTime lastUtc = (last != null) ? last.atZone(ZoneOffset.UTC) : null;
        boolean available = (lastUtc == null
                || lastUtc.getYear() != nowUtc.getYear()
                || lastUtc.getMonthValue() != nowUtc.getMonthValue()
                || lastUtc.getDayOfMonth() != nowUtc.getDayOfMonth());

        if (available == false) {
            return DailyClaimResponse.builder()
                    .available(false)
                    .nextAvailableAt(DateTimeFormatter.ISO_INSTANT.format(nextMidnightUtc))
                    .todayDay(todayInMonth)
                    .claimedDaysMask(currentMask)
                    .vipClaimedDaysMask(currentVipMask)
                    .loginRewardMonth(currentRewardMonth)
                    .build();
        }

        // 새 달이면 마스크 초기화
        if (isNewMonth == true) {
            character.setClaimedDaysMask(0);
            character.setVipClaimedDaysMask(0);
            character.setLoginRewardMonth(currentMonth);
        }

        int grantedMineral = tableMineral;

        // VIP 추가 보상: catch-up 적용
        Optional<VipSubscription> sub = vipSubscriptionRepository.findByCharacterId(characterId);
        boolean isVip = sub.isPresent() && sub.get().getVipExpiry() != null && now.isBefore(sub.get().getVipExpiry());
        if (isVip == true) {
            // VIP catch-up: 이번 달 첫 클레임이면 월초부터 오늘까지, 아니면 오늘 1일치
            int vipMineral = isNewMonth
                    ? gameDataService.getVipMineralCatchup(1, todayInMonth)
                    : gameDataService.getDailyMineralForDay(todayInMonth, EDailyBonusTier.VIP);
            grantedMineral += Math.max(0, vipMineral);
        }

        // 오늘 날짜 비트 세팅
        int bit = 1 << (todayInMonth - 1);
        character.setClaimedDaysMask(character.getClaimedDaysMask() | bit);
        if (isVip == true)
            character.setVipClaimedDaysMask(character.getVipClaimedDaysMask() | bit);
        character.setMineral(character.getMineral() + grantedMineral);
        character.setLastLoginRewardAt(now);
        characterRepository.save(character);

        log.info("[IAP] 일일 로그인 보상 characterId={} isVip={} amount={} day={} mask={} vipMask={}",
                characterId, isVip, grantedMineral, todayInMonth, character.getClaimedDaysMask(), character.getVipClaimedDaysMask());
        return DailyClaimResponse.builder()
                .available(true)
                .grantedMineral(grantedMineral)
                .mineralRemain(character.getMineral())
                .nextAvailableAt(DateTimeFormatter.ISO_INSTANT.format(nextMidnightUtc))
                .todayDay(todayInMonth)
                .claimedDaysMask(character.getClaimedDaysMask())
                .vipClaimedDaysMask(character.getVipClaimedDaysMask())
                .loginRewardMonth(currentMonth)
                .build();
    }

    // ── 내부 메서드 ──────────────────────────────────────────────────────────

    private VipStatusResponse buildStatusResponse(Instant expiry) {
        boolean isVip = expiry != null && Instant.now().isBefore(expiry);
        String expiryStr = expiry != null ? DateTimeFormatter.ISO_INSTANT.format(expiry) : null;
        return VipStatusResponse.builder()
                .isVip(isVip)
                .vipExpiry(expiryStr)
                .mineralRewardMultiplier(mineralRewardMultiplier)
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
