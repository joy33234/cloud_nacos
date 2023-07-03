package com.seektop.fund.controller.backend.dto.withdraw.config;

import com.seektop.common.mvc.ManageParamBaseDO;
import com.seektop.fund.business.withdraw.config.dto.GlWithdrawUSDTLimit;
import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
public class ProxyConfigDO extends ManageParamBaseDO implements Serializable {


    @NotNull(message = "参数异常:countLimit Not Null")
    @Min(value = 1, message = "每天提现次数上限不能小于1")
    private int countLimit;

    @NotNull(message = "参数异常:feeType Not Null")
    private String feeType; // 手续费类型：fix固定金额，percent百分比

    @NotNull(message = "参数异常:fee Not Null")
    @Min(value = 0, message = "手续费不能小于0")
    private BigDecimal fee; // 手续费

    @NotNull(message = "每日提现金额上限不能为空")
    @Min(value = 100, message = "每日提现金额上限不能小于100")
    private BigDecimal amountLimit;

    @NotNull(message = "参数异常:minLimit Not Null")
    @Min(value = 100, message = "提现最低限额不能小于100")
    private BigDecimal minLimit;

    @NotNull(message = "参数异常:maxLimit Not Null")
    @Min(value = 100, message = "提现最高限额不能小于100")
    private BigDecimal maxLimit; // 提现最高限额

    @NotNull(message = "参数异常:splitRuleList Not Empty")
    private List<WithdrawSplitRuleDO> splitRuleList;

    @NotNull(message = "参数异常:minUSDTLimit Not Null")
    @Min(value = 1, message = "参数异常:数字货币提现最低限额不能小于1")
    private BigDecimal minUSDTLimit;

    @NotNull(message = "参数异常:maxUSDTLimit Not Null")
    @Min(value = 1, message = "参数异常:数字货币提现最高限额不能小于1")
    private BigDecimal maxUSDTLimit;

    /**
     * USDT协议限额
     */
    private List<GlWithdrawUSDTLimit> usdtLimits;

    private String coin;

}
