package com.seektop.fund.controller.forehead.param.recharge;

import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class RechargePaymentDo implements Serializable {

    /**
     * 充值商户ID
     */
    @NotNull(message = "参数异常:merchantId Not Null")
    private Integer merchantId;

    /**
     * 充值金额
     */
    @DecimalMin(value = "100", message = "amount minValue 100")
    private BigDecimal amount;
}
