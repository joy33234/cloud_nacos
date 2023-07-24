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
public class GeneralConfigDO extends ManageParamBaseDO implements Serializable {

    @NotNull(message = "参数异常:freeTimes Not Null")
    @Min(value = 1, message = "每日免费提现次数不能小于1")
    private int freeTimes;

    /**
     * 手续费类型：fix固定金额，percent百分比
     */
    @NotNull(message = "参数异常:feeType Not Null")
    private String feeType;

    @NotNull(message = "参数异常:fee Not Null")
    private BigDecimal fee; // 手续费

    @NotNull(message = "参数异常:最高手续费为空!")
    @Min(value = 0, message = "参数异常:最高手续费不能小于0")
    private BigDecimal feeLimit;

    @NotNull(message = "参数异常:countLimit Not Null")
    @Min(value = 1, message = "参数异常:最高手续费不能小于1")
    private int countLimit;

    @NotNull(message = "参数异常:minLimit Not Null")
    @Min(value = 1, message = "参数异常:提现最低限额不能小于1")
    private BigDecimal minLimit;

    @NotNull(message = "参数异常:maxLimit Not Null")
    @Min(value = 1, message = "参数异常:提现最高限额不能小于1")
    private BigDecimal maxLimit;

    @NotNull(message = "参数异常:minUSDTLimit Not Null")
    @Min(value = 1, message = "参数异常:数字货币提现最低限额不能小于1")
    private BigDecimal minUSDTLimit;

    @NotNull(message = "参数异常:maxUSDTLimit Not Null")
    @Min(value = 1, message = "参数异常:数字货币提现最高限额不能小于1")
    private BigDecimal maxUSDTLimit;

    /**
     * 层级限额设置
     */
    private List<GeneralConfigLimitDO> limitList;

    /**
     * USDT协议限额
     */
    private List<GlWithdrawUSDTLimit> usdtLimits;

    @NotNull(message = "参数异常:coin Not Null")
    private String coin;


}
