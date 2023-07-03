package com.seektop.fund.enums;

import java.util.ArrayList;
import java.util.List;

public enum UseModeEnum {
    APP(0, "应用渠道"),
    INSTEAD(1, "代客渠道"),
    C2C(2, "极速转卡");

    private int code;
    private String message;

    UseModeEnum(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    // valueList List
    public final static List<Integer> valueList;

    static {
        valueList = new ArrayList<>();
        for (UseModeEnum p : UseModeEnum.values()) {
            valueList.add(p.code);
        }
    }
}
