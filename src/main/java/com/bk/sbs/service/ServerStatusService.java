//--------------------------------------------------------------------------------------------------
package com.bk.sbs.service;

import com.bk.sbs.dto.ServerStatusResponse;
import com.bk.sbs.entity.AppConfig;
import com.bk.sbs.repository.AppConfigRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ServerStatusService {

    private static final String KEY_MIN_VERSION_CODE = "android_min_version_code";
    private static final String KEY_MIN_VERSION_NAME = "android_min_version_name";

    @Value("${server.status.working:true}")
    private boolean working;

    @Value("${server.status.endTime:}")
    private String endTime;

    private final AppConfigRepository appConfigRepository;

    public ServerStatusService(AppConfigRepository appConfigRepository) {
        this.appConfigRepository = appConfigRepository;
    }

    public ServerStatusResponse getStatus(int clientVersionCode) {
        int minVersionCode = getIntConfig(KEY_MIN_VERSION_CODE, 1);
        String minVersionName = getStringConfig(KEY_MIN_VERSION_NAME, "0.1.0");
        boolean updateRequired = clientVersionCode < minVersionCode;

        return ServerStatusResponse.builder()
                .updateRequired(updateRequired)
                .minVersionCode(minVersionCode)
                .minVersionName(minVersionName)
                .working(working)
                .endTime(endTime)
                .build();
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

    private String getStringConfig(String key, String defaultValue) {
        AppConfig config = appConfigRepository.findByConfigKey(key).orElse(null);
        if (config == null) {
            return defaultValue;
        }
        return config.getConfigValue();
    }
}
