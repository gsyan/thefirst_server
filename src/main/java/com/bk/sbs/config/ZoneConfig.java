package com.bk.sbs.config;

import com.bk.sbs.dto.ZoneConfigData;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
public class ZoneConfig {
    private List<ZoneConfigData> zoneStages = new ArrayList<>();

    public ZoneConfigData getZoneByName(String zoneName) {
        if (zoneName == null || zoneName.isEmpty()) return null;
        return zoneStages.stream()
                .filter(z -> zoneName.equals(z.getZoneName()))
                .findFirst()
                .orElse(null);
    }

    public int getMaxStageInGroup(int group) {
        int max = 0;
        for (ZoneConfigData z : zoneStages) {
            String name = z.getZoneName();
            if (name == null) continue;
            String[] parts = name.split("-");
            if (parts.length != 2) continue;
            try {
                if (Integer.parseInt(parts[0]) == group) {
                    int stage = Integer.parseInt(parts[1]);
                    if (stage > max) max = stage;
                }
            } catch (NumberFormatException ignored) {}
        }
        return max;
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
