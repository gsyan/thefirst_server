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
}
