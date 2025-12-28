package com.bk.sbs.dto;

import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DevCommandRequest
 * Auto-generated from Unity C# DevCommandRequest class
 */
@Data
@NoArgsConstructor
public class DevCommandRequest {
    private String command;
    private List<String> params;
}
