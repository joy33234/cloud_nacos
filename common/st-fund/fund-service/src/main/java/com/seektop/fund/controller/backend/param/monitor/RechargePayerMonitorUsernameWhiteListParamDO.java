package com.seektop.fund.controller.backend.param.monitor;

import com.seektop.common.mvc.ManageParamBaseDO;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
public class RechargePayerMonitorUsernameWhiteListParamDO extends ManageParamBaseDO {

    private Date startDate;

    private Date endDate;

    private String username;

    private Integer page = 1;

    private Integer size = 20;

}