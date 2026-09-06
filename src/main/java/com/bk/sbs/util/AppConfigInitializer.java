//--------------------------------------------------------------------------------------------------
package com.bk.sbs.util;

import com.bk.sbs.entity.AppConfig;
import com.bk.sbs.repository.AppConfigRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class AppConfigInitializer {

    @Value("${app.init.android-min-version-code:1}")
    private String androidMinVersionCode;

    @Value("${app.init.android-min-version-name:0.1.0}")
    private String androidMinVersionName;

    private final AppConfigRepository appConfigRepository;

    public AppConfigInitializer(AppConfigRepository appConfigRepository) {
        this.appConfigRepository = appConfigRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(1)
    public void init() {
        upsertVersionCode("android_min_version_code", androidMinVersionCode, "Android 최소 허용 versionCode (Jenkins BUILD_NUMBER)");
        upsertVersionName("android_min_version_name", androidMinVersionName, "Android 최소 허용 versionName (표시용)");
        log.info("[AppConfig] 초기값 반영 완료");
    }

    // versionCode(정수)는 application.properties 값이 DB 값보다 클 때만 덮어씀 — DB 값이 같거나 더 높으면 유지
    // (운영 중 DB를 직접 올려둔 값을 재기동 시 옛 배포 설정이 되돌리지 않도록)
    private void upsertVersionCode(String key, String propertiesValue, String description) {
        AppConfig config = appConfigRepository.findByConfigKey(key).orElse(null);
        if (config == null) {
            appConfigRepository.save(new AppConfig(key, propertiesValue, description));
            log.info("[AppConfig] 삽입: {} = {}", key, propertiesValue);
            return;
        }

        int dbVersionCode;
        int propertiesVersionCode;
        try {
            dbVersionCode = Integer.parseInt(config.getConfigValue());
            propertiesVersionCode = Integer.parseInt(propertiesValue);
        }
        catch (NumberFormatException e) {
            log.error("[AppConfig] {} 버전코드 파싱 실패 — DB 값 유지 (db={}, properties={})", key, config.getConfigValue(), propertiesValue);
            return;
        }

        if (propertiesVersionCode > dbVersionCode) {
            String oldValue = config.getConfigValue();
            config.setConfigValue(propertiesValue);
            appConfigRepository.save(config);
            log.info("[AppConfig] 갱신: {} = {} (기존: {})", key, propertiesValue, oldValue);
        }
    }

    // versionName("0.1.51" 형태)은 점(.) 구분 세그먼트를 정수로 비교 — application.properties 값이 DB 값보다 높을 때만 덮어씀
    private void upsertVersionName(String key, String propertiesValue, String description) {
        AppConfig config = appConfigRepository.findByConfigKey(key).orElse(null);
        if (config == null) {
            appConfigRepository.save(new AppConfig(key, propertiesValue, description));
            log.info("[AppConfig] 삽입: {} = {}", key, propertiesValue);
            return;
        }

        String dbVersionName = config.getConfigValue();
        int compareResult;
        try {
            compareResult = compareVersionName(propertiesValue, dbVersionName);
        }
        catch (NumberFormatException e) {
            log.error("[AppConfig] {} 버전명 파싱 실패 — DB 값 유지 (db={}, properties={})", key, dbVersionName, propertiesValue);
            return;
        }

        if (compareResult > 0) {
            config.setConfigValue(propertiesValue);
            appConfigRepository.save(config);
            log.info("[AppConfig] 갱신: {} = {} (기존: {})", key, propertiesValue, dbVersionName);
        }
    }

    // "0.1.51" 같은 점(.) 구분 버전 문자열을 세그먼트별 정수로 비교. left>right면 양수, 같으면 0, left<right면 음수
    private static int compareVersionName(String left, String right) {
        String[] leftParts = left.split("\\.");
        String[] rightParts = right.split("\\.");
        int maxLength = Math.max(leftParts.length, rightParts.length);

        for (int i = 0; i < maxLength; i++) {
            int leftSegment = i < leftParts.length ? Integer.parseInt(leftParts[i]) : 0;
            int rightSegment = i < rightParts.length ? Integer.parseInt(rightParts[i]) : 0;
            if (leftSegment != rightSegment) return leftSegment - rightSegment;
        }
        return 0;
    }
}
