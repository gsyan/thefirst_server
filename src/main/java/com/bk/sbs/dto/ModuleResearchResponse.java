package com.bk.sbs.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModuleResearchResponse {
    private boolean success;
    private int moduleTypePacked; // 압축된 모듈 타입 정보 (Type + SubType + Style)
    private CostRemainInfo costRemainInfo;
    private List<Integer> researchedModuleTypePacked; // 개발된 모든 모듈의 압축된 타입 정보 목록
    private String message;
}
