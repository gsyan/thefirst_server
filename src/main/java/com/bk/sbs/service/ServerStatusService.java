//--------------------------------------------------------------------------------------------------
package com.bk.sbs.service;

import com.bk.sbs.dto.ServerStatusResponse;
import com.bk.sbs.dto.nogenerated.admin.MaintenanceStatusDto;
import com.bk.sbs.entity.AppConfig;
import com.bk.sbs.repository.AppConfigRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.format.DateTimeParseException;

@Service
public class ServerStatusService {

    private static final String KEY_MIN_VERSION_CODE = "android_min_version_code";
    private static final String KEY_MIN_VERSION_NAME = "android_min_version_name";
    private static final String KEY_WORKING = "server_status_working";
    private static final String KEY_END_TIME = "server_status_end_time";

    private final AppConfigRepository appConfigRepository;

    public ServerStatusService(AppConfigRepository appConfigRepository) {
        this.appConfigRepository = appConfigRepository;
    }

    public ServerStatusResponse getStatus(int clientVersionCode) {
        int minVersionCode = getIntConfig(KEY_MIN_VERSION_CODE, 1);
        String minVersionName = getStringConfig(KEY_MIN_VERSION_NAME, "0.1.0");
        boolean updateRequired = clientVersionCode < minVersionCode;
        boolean working = getBooleanConfig(KEY_WORKING, true);
        String endTime = getStringConfig(KEY_END_TIME, "");

        return ServerStatusResponse.builder()
                .updateRequired(updateRequired)
                .minVersionCode(minVersionCode)
                .minVersionName(minVersionName)
                .working(working)
                .endTime(endTime)
                .build();
    }

    public MaintenanceStatusDto getMaintenanceStatus() {
        boolean working = getBooleanConfig(KEY_WORKING, true);
        String endTime = getStringConfig(KEY_END_TIME, "");
        return new MaintenanceStatusDto(working, endTime);
    }

    public void updateMaintenanceStatus(boolean working, String endTime) {
        String normalizedEndTime = endTime == null ? "" : endTime.trim();
        if (normalizedEndTime.isEmpty() == false) {
            try {
                Instant.parse(normalizedEndTime);
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("endTime 형식이 올바르지 않습니다 (ISO-8601 필요): " + endTime);
            }
        }

        upsertConfig(KEY_WORKING, String.valueOf(working));
        upsertConfig(KEY_END_TIME, normalizedEndTime);
    }

    private void upsertConfig(String key, String value) {
        AppConfig config = appConfigRepository.findByConfigKey(key).orElse(null);
        if (config == null) {
            appConfigRepository.save(new AppConfig(key, value, null));
            return;
        }
        config.setConfigValue(value);
        appConfigRepository.save(config);
    }

    private int getIntConfig(String key, int defaultValue) {
        AppConfig config = appConfigRepository.findByConfigKey(key).orElse(null);
        if (config == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(config.getConfigValue());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private boolean getBooleanConfig(String key, boolean defaultValue) {
        AppConfig config = appConfigRepository.findByConfigKey(key).orElse(null);
        if (config == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(config.getConfigValue());
    }

    private String getStringConfig(String key, String defaultValue) {
        AppConfig config = appConfigRepository.findByConfigKey(key).orElse(null);
        if (config == null) {
            return defaultValue;
        }
        return config.getConfigValue();
    }
}
