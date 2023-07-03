package com.seektop.fund.controller.backend.param.monitor;

import com.seektop.common.mvc.ManageParamBaseDO;
import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotBlank;

@Getter
@Setter
public class RechargePayerMonitorUsernameWhiteListSubmitParamDO extends ManageParamBaseDO {

    @NotBlank(message = "要添加的白名单账号不能为空")
    private String username;

}