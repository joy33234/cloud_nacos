package com.seektop.fund.controller.backend.param.c2c;

import com.seektop.common.mvc.ManageParamBaseDO;
import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

@Getter
@Setter
public class C2CWithdrawConfigParamDO extends ManageParamBaseDO {

    @NotBlank(message = "提现可用VIP等级不能为空")
    private String withdrawVipLevels;

    @NotNull(message = "提现每日使用次数上限不能为空")
    private Integer withdrawDailyUseLimit;

    @NotNull(message = "提现手续费类型不能为空")
    private Integer withdrawHandlingFeeType;

    @NotNull(message = "提现手续费值不能为空")
    private BigDecimal withdrawHandlingFeeValue;

    @NotNull(message = "提现手续费上限不能为空")
    private BigDecimal withdrawHandlingFeeMax;

    @NotNull(message = "提现确认到账提醒时间不能为空")
    private Integer withdrawReceiveConfirmAlertTime;

    @NotNull(message = "提现确认到账超时时间不能为空")
    private Integer withdrawReceiveConfirmAlertTimeout;

    @NotNull(message = "撮合等待时间不能为空")
    private Integer matchWaitTime;

    @NotNull(message = "人工成功次数（月）不能为空")
    private Integer withdrawForceSuccessTime;

}