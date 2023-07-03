package com.seektop.fund.controller.forehead.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class RechargeSettingResult {

    /**
     * 普通充值快捷金额
     */
    private List<BigDecimal> common;

    /**
     * 大额充值快捷金额
     */
    private List<BigDecimal> large;

    /**
     * 代理充值快捷金额
     */
    private List<BigDecimal> proxy;

    /**
     * 充值通道显示
     */
    private Boolean showMerchant;

    /**
     * 大额充值渠道显示
     */
    private Boolean largeMerchant;
}
