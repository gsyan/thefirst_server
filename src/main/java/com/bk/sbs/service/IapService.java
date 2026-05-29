package com.bk.sbs.service;

import com.bk.sbs.dto.VipDailyMineralResponse;
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
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Optional;

@Service
@Slf4j
public class IapService {

    private static final String PLAY_API_SCOPE = "https://www.googleapis.com/auth/androidpublisher";
    private static final String SUBSCRIPTIONS_V2_URL =
            "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/%s/purchases/subscriptionsv2/tokens/%s";

    @Value("${google.play.package-name}")
    private String packageName;

    @Value("${google.play.vip-product-id}")
    private String vipProductId;

    @Value("${vip.daily-mineral:10000}")
    private int dailyMineralAmount;

    private final VipSubscriptionRepository vipSubscriptionRepository;
    private final CharacterRepository characterRepository;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public IapService(VipSubscriptionRepository vipSubscriptionRepository,
                      CharacterRepository characterRepository,
                      ObjectMapper objectMapper) {
        this.vipSubscriptionRepository = vipSubscriptionRepository;
        this.characterRepository = characterRepository;
        this.objectMapper = objectMapper;
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

        Instant expiry = verifyGooglePlaySubscription(purchaseToken);

        Optional<VipSubscription> existing = vipSubscriptionRepository.findByCharacterId(characterId);
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
        return buildStatusResponse(expiry);
    }

    // ── VIP 상태 조회 ────────────────────────────────────────────────────────

    public VipStatusResponse getVipStatus(Long characterId) {
        Optional<VipSubscription> sub = vipSubscriptionRepository.findByCharacterId(characterId);
        if (sub.isPresent() == false) {
            return VipStatusResponse.builder().isVip(false).vipExpiry(null).build();
        }
        return buildStatusResponse(sub.get().getVipExpiry());
    }

    // ── 일일 미네랄 지급 ─────────────────────────────────────────────────────

    @Transactional
    public VipDailyMineralResponse claimDailyMineral(Long characterId) {
        VipSubscription sub = vipSubscriptionRepository.findByCharacterId(characterId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.IAP_DAILY_MINERAL_NOT_VIP));

        boolean isVip = sub.getVipExpiry() != null && Instant.now().isBefore(sub.getVipExpiry());
        if (isVip == false)
            throw new BusinessException(ServerErrorCode.IAP_DAILY_MINERAL_NOT_VIP);

        Instant now = Instant.now();
        Instant last = sub.getLastDailyMineralAt();
        boolean available = (last == null || now.isAfter(last.plusSeconds(86400)));

        if (available == false) {
            return VipDailyMineralResponse.builder()
                    .available(false)
                    .mineralRemain(null)
                    .nextAvailableAt(DateTimeFormatter.ISO_INSTANT.format(last.plusSeconds(86400)))
                    .build();
        }

        Character character = characterRepository.findByIdForUpdate(characterId)
                .orElseThrow(() -> new BusinessException(ServerErrorCode.IAP_DAILY_MINERAL_CHARACTER_NOT_FOUND));
        character.setMineral(character.getMineral() + dailyMineralAmount);
        characterRepository.save(character);

        sub.setLastDailyMineralAt(now);
        vipSubscriptionRepository.save(sub);

        log.info("[IAP] VIP 일일 미네랄 지급 characterId={} amount={}", characterId, dailyMineralAmount);
        return VipDailyMineralResponse.builder()
                .available(true)
                .grantedMineral(dailyMineralAmount)
                .mineralRemain(character.getMineral())
                .nextAvailableAt(DateTimeFormatter.ISO_INSTANT.format(now.plusSeconds(86400)))
                .build();
    }

    // ── 내부 메서드 ──────────────────────────────────────────────────────────

    private VipStatusResponse buildStatusResponse(Instant expiry) {
        boolean isVip = expiry != null && Instant.now().isBefore(expiry);
        String expiryStr = expiry != null ? DateTimeFormatter.ISO_INSTANT.format(expiry) : null;
        return VipStatusResponse.builder().isVip(isVip).vipExpiry(expiryStr).build();
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

    // Google Play Developer API (subscriptionsv2) 호출 → 만료 시각 반환
    private Instant verifyGooglePlaySubscription(String purchaseToken) {
        try {
            String accessToken = getGoogleAccessToken();
            String url = String.format(SUBSCRIPTIONS_V2_URL, packageName, purchaseToken);

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
            String state = root.path("subscriptionState").asText();
            if ("SUBSCRIPTION_STATE_ACTIVE".equals(state) == false && "SUBSCRIPTION_STATE_IN_GRACE_PERIOD".equals(state) == false) {
                log.warn("[IAP] 구독 비활성 상태: {}", state);
                throw new BusinessException(ServerErrorCode.IAP_PURCHASE_FAIL_SUBSCRIPTION_NOT_ACTIVE);
            }

            // lineItems[0].expiryTime (RFC 3339)
            String expiryTimeStr = root.path("lineItems").path(0).path("expiryTime").asText();
            return Instant.parse(expiryTimeStr);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[IAP] Google Play 검증 중 예외", e);
            throw new BusinessException(ServerErrorCode.IAP_PURCHASE_FAIL_GOOGLE_VERIFY);
        }
    }

    private String getGoogleAccessToken() throws IOException {
        ClassPathResource resource = new ClassPathResource("firebase-service-account.json");
        GoogleCredentials credentials = GoogleCredentials
                .fromStream(resource.getInputStream())
                .createScoped(Collections.singleton(PLAY_API_SCOPE));
        credentials.refreshIfExpired();
        return credentials.getAccessToken().getTokenValue();
    }
}
