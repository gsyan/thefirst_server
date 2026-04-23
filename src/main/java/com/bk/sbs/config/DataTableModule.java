package com.bk.sbs.config;

import com.bk.sbs.dto.ModuleData;
import com.bk.sbs.dto.ModuleResearchData;
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
    private volatile List<ModuleData> beamModules = new ArrayList<>();
    private volatile List<ModuleData> missileModules = new ArrayList<>();
    private volatile List<ModuleData> hangerModules = new ArrayList<>();
    private volatile List<ModuleResearchData> researchDataList = new ArrayList<>();

    /**
     * JSON의 "modules" 맵을 파싱하여 각 타입별 리스트에 분배
     * Key: EModuleType의 value 값 문자열
     */
    @JsonProperty("modules")
    public void setModules(Map<String, List<ModuleData>> modules) {
        if (modules == null) return;

        String bodyKey    = String.valueOf(EModuleType.body.getValue());
        String beamKey    = String.valueOf(EModuleType.beam.getValue());
        String missileKey = String.valueOf(EModuleType.missile.getValue());
        String hangerKey  = String.valueOf(EModuleType.hanger.getValue());

        if (modules.containsKey(bodyKey))    this.bodyModules    = new ArrayList<>(modules.get(bodyKey));
        if (modules.containsKey(beamKey))    this.beamModules    = new ArrayList<>(modules.get(beamKey));
        if (modules.containsKey(missileKey)) this.missileModules = new ArrayList<>(modules.get(missileKey));
        if (modules.containsKey(hangerKey))  this.hangerModules  = new ArrayList<>(modules.get(hangerKey));
    }

    public List<ModuleData> getBodyModules() {
        return Collections.unmodifiableList(bodyModules);
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
    public int getResearchCost(EModuleSubType moduleSubType) {
        if (researchDataList == null || moduleSubType == null) {
            return 0;
        }

        for (ModuleResearchData data : researchDataList) {
            if (data.getModuleSubType() != null &&
                data.getModuleSubType().equals(moduleSubType)) {
                return data.getMineralCost() != null ?
                       data.getMineralCost() :
                       0;
            }
        }

        return 0;
    }
}




