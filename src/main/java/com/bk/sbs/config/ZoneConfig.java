package com.bk.sbs.config;

import com.bk.sbs.dto.ZoneConfigData;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
public class ZoneConfig {
    private List<ZoneConfigData> zones = new ArrayList<>();

    public ZoneConfigData getZoneByName(String zoneName) {
        if (zoneName == null || zoneName.isEmpty()) return null;
        return zones.stream()
                .filter(z -> zoneName.equals(z.getZoneName()))
                .findFirst()
                .orElse(null);
    }

    // clearedZoneName 포함, 그 이전 모든 존 반환 (index 0 = Zone-0 안전지역 제외)
    public List<ZoneConfigData> getAllZonesUpTo(String zoneName) {
        List<ZoneConfigData> result = new ArrayList<>();
        if (zoneName == null || zoneName.isEmpty()) return result;
        boolean skipFirst = true;
        for (ZoneConfigData z : zones) {
            if (skipFirst) { skipFirst = false; continue; } // Zone-0 제외
            result.add(z);
            if (zoneName.equals(z.getZoneName())) break;
        }
        return result;
    }
}
