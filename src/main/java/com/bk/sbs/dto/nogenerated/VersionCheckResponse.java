//--------------------------------------------------------------------------------------------------
package com.bk.sbs.dto.nogenerated;

import lombok.Getter;

@Getter
public class VersionCheckResponse {
    private final boolean updateRequired;
    private final int minVersionCode;
    private final String minVersionName;

    public VersionCheckResponse(boolean updateRequired, int minVersionCode, String minVersionName) {
        this.updateRequired = updateRequired;
        this.minVersionCode = minVersionCode;
        this.minVersionName = minVersionName;
    }
}
