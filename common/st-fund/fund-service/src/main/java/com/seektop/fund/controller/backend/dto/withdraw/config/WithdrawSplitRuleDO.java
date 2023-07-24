package com.seektop.fund.controller.backend.dto.withdraw.config;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;

@Getter
@Setter
public class WithdrawSplitRuleDO implements Serializable {

    @NotNull(message = "提现金额区间范围最小值不能为空")
    @Min(value = 100, message = "随机范围金额最小值不能小于100")
    private BigDecimal minAmount;

    @NotNull(message = "提现金额区间范围最大值不能为空")
    @Min(value = 100, message = "随机范围金额最大值不能小于100")
    private BigDecimal maxAmount;

    @NotNull(message = "拆单金额不能为空")
    @Min(value = 100, message = "拆单金额不能小于0")
    @Max(value = 49999, message = "拆单金额不能大于49999")
    private BigDecimal splitAmount;

    @NotNull(message = "随机范围金额为空!")
    @Min(value = 0, message = "随机范围金额不能小于0")
    private BigDecimal randomAmount;
}
