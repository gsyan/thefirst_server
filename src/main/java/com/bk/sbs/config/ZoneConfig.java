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

    // 이름 목록으로 ZoneConfigData 반환 (순서 무관)
    public List<ZoneConfigData> getZonesByNames(List<String> zoneNames) {
        List<ZoneConfigData> result = new ArrayList<>();
        if (zoneNames == null) return result;
        for (String name : zoneNames) {
            ZoneConfigData z = getZoneByName(name);
            if (z != null) result.add(z);
        }
        return result;
    }
}
