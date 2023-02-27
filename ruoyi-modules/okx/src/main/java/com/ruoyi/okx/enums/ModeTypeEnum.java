package com.ruoyi.okx.enums;


import com.google.common.collect.Maps;

import java.util.Map;

public enum ModeTypeEnum {
    GRID("grid", "网格交易"),

    MARKET("market", "大盘交易");

    private String value;

    private String desc;

    private static Map<String, ModeTypeEnum> typeEnumMap;

    static {
        typeEnumMap = Maps.newHashMap();
        for (ModeTypeEnum statusEnum : values()) {
            typeEnumMap.put(statusEnum.getValue(), statusEnum);
        }
    }

    ModeTypeEnum(String value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    public String getValue() {
        return this.value;
    }

    public String getDesc() {
        return this.desc;
    }

    public static ModeTypeEnum getModeType(String value) {
        return typeEnumMap.get(value);
    }
}
