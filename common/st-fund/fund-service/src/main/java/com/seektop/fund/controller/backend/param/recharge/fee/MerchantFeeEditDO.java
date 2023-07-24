package com.seektop.fund.controller.backend.param.recharge.fee;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.Max;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MerchantFeeEditDO implements Serializable {


    /**
     * 主键ID
     */
    private Integer feeId;

    /**
     * 额度分类（0-普通充值，1-大额充值）
     */
    @NotNull(message = "参数异常:limitType Not Null")
    private Integer limitType;


    /**
     * 三方商户ID
     */
    @NotNull(message = "参数异常:merchantId Not Null")
    private Integer merchantId;

    /**
     * 支付方式ID
     */
    @NotNull(message = "参数异常:paymentId Not Null")
    private Integer paymentId;


    /**
     * 手续费比例
     */
    @Max(value = 100, message = "参数异常:feeRate maxValue 100")
    private BigDecimal feeRate;

    /**
     * 最大手续费金额
     */
    @NotNull(message = "参数异常:maxFee Not Null")
    private BigDecimal maxFee;

    /**
     * 最低充值金额
     */
    @NotNull(message = "参数异常:minAmount Not Null")
    private BigDecimal minAmount;

    /**
     * 最高充值金额
     */
    @NotNull(message = "参数异常:maxAmount Not Null")
    private BigDecimal maxAmount;

}
