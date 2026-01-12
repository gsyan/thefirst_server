package com.bk.sbs.config;

import com.bk.sbs.dto.ModuleData;
import com.bk.sbs.dto.ModuleResearchData;
import com.bk.sbs.dto.CostStructDto;
import com.bk.sbs.enums.EModuleSubType;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ModuleDataTable {
    private volatile List<ModuleData> bodyModules = new ArrayList<>();
    private volatile List<ModuleData> engineModules = new ArrayList<>();
    private volatile List<ModuleData> weaponModules = new ArrayList<>();
    private volatile List<ModuleData> hangerModules = new ArrayList<>();
    private volatile List<ModuleResearchData> researchDataList = new ArrayList<>();


    public List<ModuleData> getBodyModules() {
        return Collections.unmodifiableList(bodyModules);
    }

    public List<ModuleData> getEngineModules() {
        return Collections.unmodifiableList(engineModules);
    }

    public List<ModuleData> getWeaponModules() {
        return Collections.unmodifiableList(weaponModules);
    }

    public List<ModuleData> getHangerModules() {
        return Collections.unmodifiableList(hangerModules);
    }

    public List<ModuleResearchData> getResearchDataList() {
        return Collections.unmodifiableList(researchDataList);
    }

    @SuppressWarnings("unchecked")
    public void setModules(Map<String, List<?>> modulesMap) {
        ObjectMapper mapper = new ObjectMapper();

        for (Map.Entry<String, List<?>> entry : modulesMap.entrySet()) {
            int moduleType = Integer.parseInt(entry.getKey());
            switch (moduleType) {
                case 1:
                    bodyModules = entry.getValue().stream()
                        .map(obj -> mapper.convertValue(obj, ModuleData.class))
                        .toList();
                    break;
                case 2:
                    engineModules = entry.getValue().stream()
                        .map(obj -> mapper.convertValue(obj, ModuleData.class))
                        .toList();
                    break;
                case 3:
                    weaponModules = entry.getValue().stream()
                        .map(obj -> mapper.convertValue(obj, ModuleData.class))
                        .toList();
                    break;
                case 4:
                    hangerModules = entry.getValue().stream()
                        .map(obj -> mapper.convertValue(obj, ModuleData.class))
                        .toList();
                    break;
            }
        }
    }

    public void setResearchDataList(List<ModuleResearchData> researchDataList) {
        this.researchDataList = researchDataList != null ? new ArrayList<>(researchDataList) : new ArrayList<>();
    }

    /**
     * 특정 모듈 서브타입의 연구 비용 조회
     */
    public CostStructDto getResearchCost(EModuleSubType moduleSubType) {
        if (researchDataList == null || moduleSubType == null) {
            return new CostStructDto(0, 0L, 0L, 0L, 0L);
        }

        for (ModuleResearchData data : researchDataList) {
            if (data.getModuleSubType() != null &&
                data.getModuleSubType().equals(moduleSubType)) {
                return data.getResearchCost() != null ?
                       data.getResearchCost() :
                       new CostStructDto(0, 0L, 0L, 0L, 0L);
            }
        }

        return new CostStructDto(0, 0L, 0L, 0L, 0L);
    }
}




