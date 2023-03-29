package com.ruoyi.okx.enums;


import com.google.common.collect.Maps;

import java.util.Map;

public enum MarketStatusEnum {
    RISE(0, "上涨"),
    FALL(1, "下跌"),

    STABLE(2, "平稳"),

    MAX_RISE(4, "大涨");

    private Integer status;

    private String desc;

    private static Map<Integer, MarketStatusEnum> typeEnumMap;

    static {
        typeEnumMap = Maps.newHashMap();
        for (MarketStatusEnum statusEnum : values())
            typeEnumMap.put(statusEnum.getStatus(), statusEnum);
    }

    MarketStatusEnum(Integer status, String desc) {
        this.status = status;
        this.desc = desc;
    }

    public Integer getStatus() {
        return this.status;
    }

    public String getDesc() {
        return this.desc;
    }
    }
