package com.seektop.fund.controller.backend.param.c2c;

import com.seektop.common.mvc.ManageParamBaseDO;
import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Getter
@Setter
public class C2CRechargeConfigParamDO extends ManageParamBaseDO {

    @NotBlank(message = "充值可用用户层级不能为空")
    private String rechargeUserLevels;

    @NotBlank(message = "充值可用VIP等级不能为空")
    private String rechargeVipLevels;

    @NotNull(message = "充值每日使用次数上限不能为空")
    private Integer rechargeDailyUseLimit;

    @NotNull(message = "充值每日取消次数上限不能为空")
    private Integer rechargeDailyCancelLimit;

    @NotNull(message = "充值付款提醒时间不能为空")
    private Integer rechargeAlertTime;

    @NotNull(message = "充值付款超时时间不能为空")
    private Integer rechargePaymentTimeout;

}