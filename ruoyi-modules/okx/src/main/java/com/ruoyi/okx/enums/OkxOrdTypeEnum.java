package com.ruoyi.okx.enums;


import com.google.common.collect.Maps;

import java.util.Map;

public enum OkxOrdTypeEnum {
    LIMIT("limit", "限价"),

    MARKET("market", "市价");

    private String value;

    private String desc;

    private static Map<String, OkxOrdTypeEnum> typeEnumMap;

    static {
        typeEnumMap = Maps.newHashMap();
        for (OkxOrdTypeEnum statusEnum : values())
            typeEnumMap.put(statusEnum.getValue(), statusEnum);
    }

    OkxOrdTypeEnum(String value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    public String getValue() {
        return this.value;
    }

    public String getDesc() {
        return this.desc;
    }
    }
