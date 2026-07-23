package com.bk.sbs.config;

import com.bk.sbs.dto.ZoneConfigData;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

// 함선 시스템 대격변으로 zoneName/스테이지 기준(zoneStages)에서 zoneIndex 기준(zoneConfigs)으로 재구성됨 —
// 그리드 셀 적함대를 클라(ExplorationEnemyFleetGenerator)와 동일하게 재계산하는 데 필요한 필드만 담음
@Data
@NoArgsConstructor
public class ZoneConfig {
    private List<ZoneConfigData> zoneConfigs = new ArrayList<>();

    public ZoneConfigData getZoneByIndex(int zoneIndex) {
        return zoneConfigs.stream()
                .filter(z -> z.getZoneIndex() != null && z.getZoneIndex() == zoneIndex)
                .findFirst()
                .orElse(null);
    }
}
