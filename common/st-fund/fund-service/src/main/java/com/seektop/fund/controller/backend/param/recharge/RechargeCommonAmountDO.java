package com.seektop.fund.controller.backend.param.recharge;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * @desc 全局充值金额区间
 * @author joy
 * @date 2021-02-17
 */

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RechargeCommonAmountDO implements Serializable {

    private static final long serialVersionUID = 7581524522316513191L;

    /**
     * 快捷金额
     */
    @NotNull(message = "amounts is Not Null")
    List<BigDecimal> amounts;


    /**
     * 0：普通充值(默认)，1：大额充值, 2:代理充值
     */
    @NotNull(message = "limitType is Not Null")
    private Integer limitType;

    /**
     * 最低金额
     */
    private BigDecimal minAmount;

    /**
     * 最高金额
     */
    private BigDecimal maxAmount;

    /**
     * 充值USDT汇率
     */
    private BigDecimal rate;

}
