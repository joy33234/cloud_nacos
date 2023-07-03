package com.seektop.fund.payment;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
public class BlockInfo {

    /**
     * 收款账户人
     */
    private String owner;

    /**
     * 本币数量
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
     * USDT兑RMB——订单发起时汇率
     */
    private BigDecimal rate;

    /**
     * 订单过期时间
     */
    private Date expiredDate;

}
