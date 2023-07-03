package com.seektop.fund.controller.backend.param.c2c;

import com.seektop.common.mvc.ManageParamBaseDO;
import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotBlank;

@Getter
@Setter
public class C2CSeoWithdrawConfigParamDO extends ManageParamBaseDO {

    @NotBlank(message = "seo用户提现可用VIP等级不能为空")
    private String withdrawSeoVipLevels;

}