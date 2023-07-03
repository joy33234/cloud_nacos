package com.seektop.fund.controller.backend.param.monitor;

import com.seektop.common.mvc.ManageParamBaseDO;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RechargePayerMonitorNameWhiteListParamDO extends ManageParamBaseDO {

    private Integer page = 1;

    private Integer size = 20;

}