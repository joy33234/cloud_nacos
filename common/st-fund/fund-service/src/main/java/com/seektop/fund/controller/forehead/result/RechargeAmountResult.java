package com.seektop.fund.controller.forehead.result;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class RechargeAmountResult implements Serializable {
    private static final long serialVersionUID = -2454444396990942918L;

    /**
     * 是否存在相同金额：true 存在，false 不存在
     */
    private Boolean exist;
    /**
     * 建议金额
     */
    private BigDecimal amount;
}
