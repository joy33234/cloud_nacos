package com.seektop.fund.controller.forehead.result;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.math.BigDecimal;

@Setter
@Getter
@NoArgsConstructor
@ToString
public class RechargeDigitalInfo implements Serializable {

    /**
     * 订单号
     */
    private String orderId;

    /**
     * 收款账户人
     */
    private String owner;

    /**
     * 充值金额
     */
    private BigDecimal amount;

    /**
     * 数字货币数量
     */
    private BigDecimal digitalAmount;

    /**
     * 区块协议
     */
    private String protocol;

    /**
     * USDT收币地址
     */
    private String blockAddress;

    /**
     * USDT兑RMB 汇率
     */
    private BigDecimal rate;

    /**
     * 交易汇率
     */
    private BigDecimal realRate;

    private String keyword;


}
