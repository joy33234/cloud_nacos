package com.seektop.fund.controller.backend.dto.withdraw.config;

import com.seektop.fund.business.withdraw.config.dto.GlWithdrawUSDTLimit;
import com.seektop.fund.vo.ManageParamBase;
import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
public class QuickConfigDO extends ManageParamBase implements Serializable {

    @NotNull(message = "体育流水赠送设置不能为空")
    private List<WithdrawEffectBetRuleDO> sportRuleList;

    @NotNull(message = "娱乐类流水赠送设置不能为空")
    private List<WithdrawEffectBetRuleDO> funGameRuleList;

    /**
     * 手续费类型：fix固定金额，percent百分比
     */
    @NotNull(message = "参数异常:feeType Not Null")
    private String feeType;

    @NotNull(message = "参数异常:fee Not Null")
    @Min(value = 0, message = "手续费不能小于0")
    private BigDecimal fee;

    @NotNull(message = "参数异常:countLimit Not Null")
    @Min(value = 0, message = "每天提现次数上限不能小于0")
    private int countLimit;

    @NotNull(message = "参数异常:minLimit Not Null")
    @Min(value = 0, message = "单笔提现最低金额不能小于0")
    private BigDecimal minLimit;

    @NotNull(message = "参数异常:maxLimit Not Null")
    @Min(value = 0, message = "单笔提现最高金额不能小于0")
    private BigDecimal maxLimit; // 提现最高限额

    @NotNull(message = "提现拆单设置不能为空")
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
