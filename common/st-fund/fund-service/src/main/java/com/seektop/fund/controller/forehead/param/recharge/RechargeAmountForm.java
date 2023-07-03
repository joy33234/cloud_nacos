package com.seektop.fund.controller.forehead.param.recharge;

import com.seektop.common.mvc.ParamBaseDO;
import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class RechargeAmountForm extends ParamBaseDO implements Serializable {
    private static final long serialVersionUID = -5972389423204498824L;

    /**
     * 充值商户应用ID
     */
    @NotNull(message = "参数异常:merchantAppId Not Null")
    private Integer merchantAppId;
    /**
     * 充值方式ID
     */
    @NotNull(message = "参数异常:paymentId Not Null")
    private Integer paymentId;
    /**
     * 充值金额
     */
    @DecimalMin(value = "100", message = "amount minValue 100")
    private BigDecimal amount;
}
