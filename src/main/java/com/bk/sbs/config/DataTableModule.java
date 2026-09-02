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
    private volatile List<ModuleData> hullModules = new ArrayList<>();
    private volatile List<ModuleData> beamModules = new ArrayList<>();
    private volatile List<ModuleData> missileModules = new ArrayList<>();
    private volatile List<ModuleData> hangarModules = new ArrayList<>();
    private volatile List<ModuleData> shieldModules = new ArrayList<>();
    private volatile List<ModuleData> interceptorModules = new ArrayList<>();

    /**
     * JSON의 "modules" 맵을 파싱하여 각 타입별 리스트에 분배
     * Key: EModuleType의 value 값 문자열
     */
    @JsonProperty("modules")
    public void setModules(Map<String, List<ModuleData>> modules) {
        if (modules == null) return;

        String hullKey        = String.valueOf(EModuleType.hull.getValue());
        String beamKey        = String.valueOf(EModuleType.beam.getValue());
        String missileKey     = String.valueOf(EModuleType.missile.getValue());
        String hangarKey      = String.valueOf(EModuleType.hangar.getValue());
        String shieldKey      = String.valueOf(EModuleType.shield.getValue());
        String interceptorKey = String.valueOf(EModuleType.interceptor.getValue());

        if (modules.containsKey(hullKey))        this.hullModules        = new ArrayList<>(modules.get(hullKey));
        if (modules.containsKey(beamKey))        this.beamModules        = new ArrayList<>(modules.get(beamKey));
        if (modules.containsKey(missileKey))     this.missileModules     = new ArrayList<>(modules.get(missileKey));
        if (modules.containsKey(hangarKey))      this.hangarModules      = new ArrayList<>(modules.get(hangarKey));
        if (modules.containsKey(shieldKey))      this.shieldModules      = new ArrayList<>(modules.get(shieldKey));
        if (modules.containsKey(interceptorKey)) this.interceptorModules = new ArrayList<>(modules.get(interceptorKey));
    }

    public List<ModuleData> getHullModules() {
        return Collections.unmodifiableList(hullModules);
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
    public List<ModuleData> getShieldModules() {
        return Collections.unmodifiableList(shieldModules);
    }
    public List<ModuleData> getInterceptorModules() {
        return Collections.unmodifiableList(interceptorModules);
    }

}




