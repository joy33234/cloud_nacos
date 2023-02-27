package com.ruoyi.okx.enums;


import com.google.common.collect.Maps;
import java.util.Map;

public enum OkxSideEnum {
    BUY("buy", "买"),
    SELL("sell", "卖");

    private String side;

    private String desc;

    private static Map<String, OkxSideEnum> typeEnumMap;

    static {
        typeEnumMap = Maps.newHashMap();
        for (OkxSideEnum statusEnum : values())
            typeEnumMap.put(statusEnum.getSide(), statusEnum);
    }

    OkxSideEnum(String side, String desc) {
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
