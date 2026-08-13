package com.bk.sbs.config;

import com.bk.sbs.dto.ModuleData;
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
    private volatile List<ModuleData> hangarModules = new ArrayList<>();

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
        String hangarKey  = String.valueOf(EModuleType.hangar.getValue());

        if (modules.containsKey(bodyKey))    this.bodyModules    = new ArrayList<>(modules.get(bodyKey));
        if (modules.containsKey(beamKey))    this.beamModules    = new ArrayList<>(modules.get(beamKey));
        if (modules.containsKey(missileKey)) this.missileModules = new ArrayList<>(modules.get(missileKey));
        if (modules.containsKey(hangarKey))  this.hangarModules  = new ArrayList<>(modules.get(hangarKey));
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
    public List<ModuleData> getHangarModules() {
        return Collections.unmodifiableList(hangarModules);
    }

}




