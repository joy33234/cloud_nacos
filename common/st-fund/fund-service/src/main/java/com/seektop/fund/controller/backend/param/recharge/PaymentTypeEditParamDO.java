package com.seektop.fund.controller.backend.param.recharge;

import com.seektop.common.mvc.ManageParamBaseDO;
import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Getter
@Setter
public class PaymentTypeEditParamDO extends ManageParamBaseDO {

    @NotNull(message = "支付方式ID不能为空")
    private Integer paymentId;

    @NotBlank(message = "支付方式Logo不能为空")
    private String paymentLogo;

    @NotBlank(message = "币种不能为空")
    private String coin;

}