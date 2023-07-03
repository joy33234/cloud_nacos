package com.seektop.fund.controller.backend.param.monitor;

import com.seektop.common.mvc.ManageParamBaseDO;
import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotBlank;

@Getter
@Setter
public class RechargePayerMonitorNameWhiteListSubmitParamDO extends ManageParamBaseDO {

    @NotBlank(message = "要添加的白名单姓名不能为空")
    private String name;

}