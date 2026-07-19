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
        upsert("android_min_version_code", androidMinVersionCode, "Android 최소 허용 versionCode (Jenkins BUILD_NUMBER)");
        upsert("android_min_version_name", androidMinVersionName, "Android 최소 허용 versionName (표시용)");
        log.info("[AppConfig] 초기값 반영 완료");
    }

    // 서버 기동 시 application.properties 값으로 항상 덮어씀 — 배포 후 값 반영을 위해 매번 오버라이트,
    // 실행 중 값 조정은 DB를 직접 수정하면 재기동 전까지 그대로 유지됨 (ServerStatusService가 매 요청 DB 조회)
    private void upsert(String key, String value, String description) {
        AppConfig config = appConfigRepository.findByConfigKey(key).orElse(null);
        if (config == null) {
            appConfigRepository.save(new AppConfig(key, value, description));
            log.info("[AppConfig] 삽입: {} = {}", key, value);
        }
        else if (config.getConfigValue().equals(value) == false) {
            String oldValue = config.getConfigValue();
            config.setConfigValue(value);
            appConfigRepository.save(config);
            log.info("[AppConfig] 갱신: {} = {} (기존: {})", key, value, oldValue);
        }
    }
}
