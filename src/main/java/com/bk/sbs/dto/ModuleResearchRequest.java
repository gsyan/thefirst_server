package com.bk.sbs.dto;

public class ModuleResearchRequest {
    private int moduleTypePacked; // 압축된 모듈 타입 정보 (Type + SubType + Style)

    public int getModuleTypePacked() {
        return moduleTypePacked;
    }

    public void setModuleTypePacked(int moduleTypePacked) {
        this.moduleTypePacked = moduleTypePacked;
    }
}
