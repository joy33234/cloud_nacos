package com.seektop.common.local.constant.enums;


import com.seektop.common.local.base.parse.LocalCommonConfig;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum LocalCommonEnums implements LocalCommonConfig {
    MVC_FAIL("MVC_ERROR"),
    FUND_RECHARGE("FUND_RECHARGE_%s"),
    ;
    private String key;

    @Override
    public String getModule() {
        return "common";
    }

}
