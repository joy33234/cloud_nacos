package com.seektop.fund.controller.backend.result.recharge;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@ToString
public class RechargeDigitalResult implements Serializable {

    /**
     * USDT数量
     */
    private BigDecimal usdtAmount;

    /**
     * USDT支付数量
     */
    private BigDecimal usdtPayAmount;

    /**
     * 下单汇率
     */
    private BigDecimal rate;

    /**
     * 实际汇率
     */
    private BigDecimal realRate;

    /**
     * 区块协议
     */
    private String protocol;

    /**
     * USDT收币地址
     */
    private String blockAddress;

    /**
     * 交易哈希值
     */
    private String txHash;

}
