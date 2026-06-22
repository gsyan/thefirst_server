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
        insertIfAbsent("android_min_version_code", androidMinVersionCode, "Android 최소 허용 versionCode (Jenkins BUILD_NUMBER)");
        insertIfAbsent("android_min_version_name", androidMinVersionName, "Android 최소 허용 versionName (표시용)");
        log.info("[AppConfig] 초기값 확인 완료");
    }

    private void insertIfAbsent(String key, String value, String description) {
        boolean exists = appConfigRepository.findByConfigKey(key).isPresent();
        if (exists == false) {
            appConfigRepository.save(new AppConfig(key, value, description));
            log.info("[AppConfig] 삽입: {} = {}", key, value);
        }
    }
}
