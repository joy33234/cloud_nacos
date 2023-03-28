package com.ruoyi.okx.enums;


import com.google.common.collect.Maps;
import java.util.Map;

public enum CoinStatusEnum {
    OPEN(0, "开启"),
    CLOSE(1, "关闭"),
    ONYYSELL(2, "只卖");

    private int status;

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

    public int getStatus() {
        return this.status;
    }

    public String getDesc() {
        return this.desc;
    }
    }
