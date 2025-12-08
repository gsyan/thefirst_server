package com.bk.sbs.config;

import com.bk.sbs.dto.ModuleBodyDataDto;
import com.bk.sbs.dto.ModuleEngineDataDto;
import com.bk.sbs.dto.ModuleWeaponDataDto;
import com.bk.sbs.dto.ModuleHangerDataDto;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ModuleDataTable {
    private volatile List<ModuleBodyDataDto> bodyModules = new ArrayList<>();
    private volatile List<ModuleEngineDataDto> engineModules = new ArrayList<>();
    private volatile List<ModuleWeaponDataDto> weaponModules = new ArrayList<>();
    private volatile List<ModuleHangerDataDto> hangerModules = new ArrayList<>();


    public List<ModuleBodyDataDto> getBodyModules() {
        return Collections.unmodifiableList(bodyModules);
    }

    public List<ModuleEngineDataDto> getEngineModules() {
        return Collections.unmodifiableList(engineModules);
    }

    public List<ModuleWeaponDataDto> getWeaponModules() {
        return Collections.unmodifiableList(weaponModules);
    }

    public List<ModuleHangerDataDto> getHangerModules() {
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
                        .map(obj -> mapper.convertValue(obj, ModuleBodyDataDto.class))
                        .toList();
                    break;
                case 2:
                    engineModules = entry.getValue().stream()
                        .map(obj -> mapper.convertValue(obj, ModuleEngineDataDto.class))
                        .toList();
                    break;
                case 3:
                    weaponModules = entry.getValue().stream()
                        .map(obj -> mapper.convertValue(obj, ModuleWeaponDataDto.class))
                        .toList();
                    break;
                case 4:
                    hangerModules = entry.getValue().stream()
                        .map(obj -> mapper.convertValue(obj, ModuleHangerDataDto.class))
                        .toList();
                    break;
            }
        }
    }
}




