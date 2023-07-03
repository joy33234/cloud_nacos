package com.seektop.fund.controller.forehead.param.withdraw;

import com.seektop.common.mvc.ParamBaseDO;
import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotBlank;

@Getter
@Setter
public class WithdrawBankListForeheadParamDO extends ParamBaseDO {

    @NotBlank(message = "币种不能为空")
    private String coin;

}