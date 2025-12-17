package com.bk.sbs.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CostStruct {
    private int techLevel;
    private long mineral;
    private long mineralRare;
    private long mineralExotic;
    private long mineralDark;
}
