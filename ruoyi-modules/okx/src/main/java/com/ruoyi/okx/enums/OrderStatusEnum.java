package com.ruoyi.okx.enums;


import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import java.util.List;
import java.util.Map;

public enum OrderStatusEnum {
    CREATED(Integer.valueOf(0), "创建", Boolean.valueOf(false)),
    PENDING(Integer.valueOf(1), "买入中", Boolean.valueOf(false)),
    FAIL(Integer.valueOf(2), "失败", Boolean.valueOf(true)),
    SUCCESS(Integer.valueOf(3), "成功", Boolean.valueOf(false)),
    PARTIALLYFILLED(Integer.valueOf(4), "部分成功", Boolean.valueOf(false)),
    FINISH(Integer.valueOf(5), "卖出结束", Boolean.valueOf(true)),
    CANCEL(Integer.valueOf(6), "撤销", Boolean.valueOf(true)),
    SELLING(Integer.valueOf(7), "卖出中", Boolean.valueOf(false)),

    NEW(Integer.valueOf(8), "新币", Boolean.valueOf(false));

    OrderStatusEnum(Integer status, String desc, Boolean finished) {
        this.status = status;
        this.desc = desc;
        this.finished = finished;
    }

    private Integer status;

    private String desc;

    private Boolean finished;

    private static Map<Integer, OrderStatusEnum> typeEnumMap;

    private static List<Integer> finishList;

    private static List<Integer> unFinishList;

    static {
        typeEnumMap = Maps.newHashMap();
        for (OrderStatusEnum statusEnum : values())
            typeEnumMap.put(statusEnum.getStatus(), statusEnum);
        finishList = Lists.newArrayList();
        unFinishList = Lists.newArrayList();
        for (OrderStatusEnum statusEnum : values()) {
            if (statusEnum.getFinished().booleanValue()) {
                finishList.add(statusEnum.getStatus());
            } else {
                unFinishList.add(statusEnum.getStatus());
            }
        }
    }

    OrderStatusEnum(Integer status, String desc) {
        this.status = status;
        this.desc = desc;
    }

    public Integer getStatus() {
        return this.status;
    }

    public String getDesc() {
        return this.desc;
    }

    public static String getDesc(Integer status) {
        return typeEnumMap.get(status).getDesc();
    }

    public Boolean setFinished() {
        return this.finished;
    }

    public Boolean getFinished() {
        return this.finished;
    }

    public static List<Integer> getFinishList() {
        return finishList;
    }

    public static List<Integer> getUnFinishList() {
        return unFinishList;
    }
}
