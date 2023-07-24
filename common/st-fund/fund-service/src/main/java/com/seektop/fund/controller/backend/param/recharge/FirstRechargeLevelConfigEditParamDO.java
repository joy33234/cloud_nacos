package com.seektop.fund.controller.backend.param.recharge;

import com.seektop.common.mvc.ManageParamBaseDO;
import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotNull;

@Getter
@Setter
public class FirstRechargeLevelConfigEditParamDO extends ManageParamBaseDO {

    @NotNull(message = "检测层级ID不能为空")
    private Integer levelId;

    @NotNull(message = "变更层级ID不能为空")
    private Integer targetLevelId;

    @NotNull(message = "成功次数不能为空")
    private Integer successTimes;

}