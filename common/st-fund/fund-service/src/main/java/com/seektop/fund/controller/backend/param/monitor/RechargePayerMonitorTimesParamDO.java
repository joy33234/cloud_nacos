package com.seektop.fund.controller.backend.param.monitor;

import com.seektop.common.mvc.ManageParamBaseDO;
import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotNull;

@Getter
@Setter
public class RechargePayerMonitorTimesParamDO extends ManageParamBaseDO {

    @NotNull(message = "次数不能为空")
    private Integer times;

}