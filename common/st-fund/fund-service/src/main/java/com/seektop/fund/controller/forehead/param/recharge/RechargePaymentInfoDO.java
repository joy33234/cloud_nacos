package com.seektop.fund.controller.forehead.param.recharge;

import com.seektop.common.mvc.ParamBaseDO;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RechargePaymentInfoDO extends ParamBaseDO implements Serializable {

    private static final long serialVersionUID = -8287681143207544776L;

    /**
     * 充值金额
     */
    @DecimalMin(value = "100", message = "单笔最小充值金额为100.0元")
    private BigDecimal amount;

    /**
     * 是否按照金额过滤(T-过滤、F-不过滤)
     */
    private Boolean amountFilter = false;

    /**
     * 是否过滤极速支付渠道(T-过滤、F-不过滤)
     */
    private Boolean quickFilter = false;

    /**
     * 币种
     */
    @NotNull(message = "币种不能为空")
    private String coin;


}
