package com.seektop.fund.controller.backend.param.recharge;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serializable;

@Getter
@Setter
public class RechargeFailureLevelConfigParamDO implements Serializable {

    @NotBlank(message = "要变更的层级ID不能为空")
    private String levelIds;

    @NotNull(message = "新用户连续充值失败次数不能为空")
    private Integer newUserTimes;

    @NotNull(message = "旧用户连续充值失败次数不能为空")
    private Integer oldUserTimes;

    @NotNull(message = "目标层级ID不能为空")
    private Integer targetLevelId;

    /**
     * 生效的VIP等级
     */
    private String vips;

}