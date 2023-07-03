package com.seektop.fund.controller.backend.param.recharge;

import com.seektop.common.mvc.ManageParamBaseDO;
import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

@Getter
@Setter
public class RechargeBettingLevelConfigCreateParamDO extends ManageParamBaseDO {

    @NotNull(message = "检测层级ID不能为空")
    private Integer levelId;

    @NotNull(message = "天数不能为空")
    private Integer days;

    @NotNull(message = "充值金额不能为空")
    private BigDecimal rechargeAmount;

    @NotNull(message = "流水倍数不能为空")
    private BigDecimal bettingMultiple;

    @NotNull(message = "变更层级ID不能为空")
    private Integer targetLevelId;

}