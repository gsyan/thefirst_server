package com.bk.sbs.config;

import com.bk.sbs.dto.ModuleData;
import com.bk.sbs.dto.ModuleResearchData;
import com.bk.sbs.dto.CostStructDto;
import com.bk.sbs.enums.EModuleSubType;
import com.bk.sbs.enums.EModuleType;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class DataTableModule {
    private volatile List<ModuleData> bodyModules = new ArrayList<>();
    private volatile List<ModuleData> engineModules = new ArrayList<>();
    private volatile List<ModuleData> beamModules = new ArrayList<>();
    private volatile List<ModuleData> missileModules = new ArrayList<>();
    private volatile List<ModuleData> hangerModules = new ArrayList<>();
    private volatile List<ModuleResearchData> researchDataList = new ArrayList<>();

    /**
     * JSON의 "modules" 맵을 파싱하여 각 타입별 리스트에 분배
     * Key: EModuleType의 ordinal 값 (1=Body, 2=Engine, 3=Beam, 4=Missile, 5=Hanger)
     */
    @JsonProperty("modules")
    public void setModules(Map<String, List<ModuleData>> modules) {
        if (modules == null) return;

        // EModuleType.Body = 1
        if (modules.containsKey("1")) {
            this.bodyModules = new ArrayList<>(modules.get("1"));
        }
        // EModuleType.Engine = 2
        if (modules.containsKey("2")) {
            this.engineModules = new ArrayList<>(modules.get("2"));
        }
        // EModuleType.Beam = 3
        if (modules.containsKey("3")) {
            this.beamModules = new ArrayList<>(modules.get("3"));
        }
        // EModuleType.Missile = 4
        if (modules.containsKey("4")) {
            this.missileModules = new ArrayList<>(modules.get("4"));
        }
        // EModuleType.Hanger = 5
        if (modules.containsKey("5")) {
            this.hangerModules = new ArrayList<>(modules.get("5"));
        }
    }

    public List<ModuleData> getBodyModules() {
        return Collections.unmodifiableList(bodyModules);
    }
    public List<ModuleData> getEngineModules() {
        return Collections.unmodifiableList(engineModules);
    }
    public List<ModuleData> getBeamModules() {
        return Collections.unmodifiableList(beamModules);
    }
    public List<ModuleData> getMissileModules() {
        return Collections.unmodifiableList(missileModules);
    }
    public List<ModuleData> getHangerModules() {
        return Collections.unmodifiableList(hangerModules);
    }

    public List<ModuleResearchData> getResearchDataList() {
        return Collections.unmodifiableList(researchDataList);
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




