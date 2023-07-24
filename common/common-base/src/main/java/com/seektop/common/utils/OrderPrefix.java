package com.seektop.common.utils;

/**
 * Created by ken on 2018/7/25.
 * 平台游戏订单前缀定义
 */
public enum OrderPrefix {
    ZZ("ZZ"),
    CZ("CZ"),
    YJ("YJ"),
    DC("DC"),// 代充(代理给会员上分)
    SX("SX"),// 信用额度调整
    WJ("WJ"),// 提现额度调整
    RR("RR"),// 代充返利
    WA("WA");// 错误代充扣回添加

    private String code;

    OrderPrefix(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
