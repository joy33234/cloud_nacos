package com.seektop.fund.business.withdraw.config.dto;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;

@Getter
@Setter
public class GlWithdrawUSDTLimit implements Serializable {

    /**
     * 协议
     */
    private String protocol;

    /**
     * 最大提现金额
     */
    private BigDecimal maxAmount;

    /**
     * 最低提现金额
     */
    private BigDecimal minAmount;
}
