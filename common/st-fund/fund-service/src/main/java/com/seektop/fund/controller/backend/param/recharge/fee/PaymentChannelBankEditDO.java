package com.seektop.fund.controller.backend.param.recharge.fee;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PaymentChannelBankEditDO implements Serializable {

    /**
     * 充值银行ID
     */
    @NotNull(message = "参数异常:paybankId Not Null")
    private Integer paybankId;

    /**
     * 银行状态：0正常，1禁用
     */
    @NotNull(message = "参数异常:status Not Null")
    private Integer status;

    /**
     * 最低限额
     */
    @NotNull(message = "参数异常:minAmount Not Null")
    private BigDecimal minAmount;

    /**
     * 最高限额
     */
    @NotNull(message = "参数异常:maxAmount Not Null")
    private BigDecimal maxAmount;

}