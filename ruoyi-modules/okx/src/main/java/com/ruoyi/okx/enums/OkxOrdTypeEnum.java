package com.ruoyi.okx.enums;


import com.google.common.collect.Maps;

import java.util.Map;

public enum OkxOrdTypeEnum {
    LIMIT("limit", "限价"),

    MARKET("market", "市价");

    private String side;

    private String desc;

    private static Map<String, OkxOrdTypeEnum> typeEnumMap;

    static {
        typeEnumMap = Maps.newHashMap();
        for (OkxOrdTypeEnum statusEnum : values())
            typeEnumMap.put(statusEnum.getSide(), statusEnum);
    }

    OkxOrdTypeEnum(String side, String desc) {
        this.side = side;
        this.desc = desc;
    }

    public String getSide() {
        return this.side;
    }

    public String getDesc() {
        return this.desc;
    }
    }
