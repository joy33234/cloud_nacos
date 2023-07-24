package com.seektop.fund.controller.backend.result;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Set;

@Getter
@Setter
public class USDTConfigResult implements Serializable {

    /**
     * 数字货币提现设置
     *
     * 0 全部用户可提现
     * 1 全部用户不可提现
     * 2 仅充值过的用户可提现
     */
    private int status;

    /**
     * 汇率
     */
    private BigDecimal rate;

    /**
     * USDT提现支持协议
     */
    private Set<String> protocols;

}