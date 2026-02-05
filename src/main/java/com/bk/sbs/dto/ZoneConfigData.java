package com.bk.sbs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ZoneConfigData
 * Auto-generated from Unity C# ZoneConfig class (server-required fields only)
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class ZoneConfigData {
    private String zoneName;
    private Float mineralPerHour;
    private Float mineralRarePerHour;
    private Float mineralExoticPerHour;
    private Float mineralDarkPerHour;
}
