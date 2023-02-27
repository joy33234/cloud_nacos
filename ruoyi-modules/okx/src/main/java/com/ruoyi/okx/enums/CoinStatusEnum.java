package com.ruoyi.okx.enums;


import com.google.common.collect.Maps;
import java.util.Map;

public enum CoinStatusEnum {
    OPEN(Integer.valueOf(0), "开启"),
    CLOSE(Integer.valueOf(1), "关闭"),
    ONYYSELL(Integer.valueOf(2), "只卖");

    private Integer status;

    private String desc;

    private static Map<Integer, CoinStatusEnum> typeEnumMap;

    static {
        typeEnumMap = Maps.newHashMap();
        for (CoinStatusEnum statusEnum : values())
            typeEnumMap.put(statusEnum.getStatus(), statusEnum);
    }

    CoinStatusEnum(Integer status, String desc) {
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
