package com.seektop.fund.controller.backend.dto.withdraw.config;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;

@Getter
@Setter
public class WithdrawEffectBetRuleDO implements Serializable {

    @NotNull(message = "有效投注额不能为空")
    private BigDecimal betAmount;

    @NotNull(message = "增送免费提现次数不能为空")
    @Min(value = 1, message = "赠送免费提现次数不能小于1")
    private int freeTimes;//免费提现次数

}
