package com.seektop.fund.controller.backend.param.bankcard;

import com.seektop.common.mvc.ManageParamBaseDO;
import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Getter
@Setter
public class RechargeBankEditParamDO extends ManageParamBaseDO {

    @NotNull(message = "银行ID不能为空")
    private Integer bankId;

    @NotBlank(message = "银行名称不能为空")
    private String bankName;

    @NotBlank(message = "币种不能为空")
    private String coin;

    @NotBlank(message = "银行Logo不能为空")
    private String bankLogo;

}