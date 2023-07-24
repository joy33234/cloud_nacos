package com.seektop.common.local.constant.enums;


import com.seektop.common.local.base.parse.LocalCommonConfig;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ResultCodeEnums implements LocalCommonConfig {
    MVC_RESULT_CODE("MVC_RESULT_CODE_%s")
    ;

    private String key;
    @Override
    public String getModule() {
        return "result-code-dic";
    }

}
