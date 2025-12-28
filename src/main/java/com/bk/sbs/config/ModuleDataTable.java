package com.bk.sbs.config;

import com.bk.sbs.dto.ModuleData;
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
}




