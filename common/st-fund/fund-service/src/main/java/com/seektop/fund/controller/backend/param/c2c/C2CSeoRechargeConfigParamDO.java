package com.seektop.fund.controller.backend.param.c2c;

import com.seektop.common.mvc.ManageParamBaseDO;
import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotBlank;

@Getter
@Setter
public class C2CSeoRechargeConfigParamDO extends ManageParamBaseDO {

    @NotBlank(message = "充值可用SEO-VIP等级不能为空")
    private String rechargeSeoVipLevels;

}