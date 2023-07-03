package com.seektop.fund.controller.backend.dto.withdraw.condition;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;

@Getter
@Setter
public class WithdrawConditionAddDO implements Serializable {

    /**
     * 条件名称
     */
    @NotNull(message = "参数异常:conditionName Not Null")
    private String conditionName;

    /**
     * 提现单类型（0-人工出款、1-自动出款、2-三方手动出款）
     */
    @NotNull(message = "参数异常:withdrawType Not Null")
    private Integer withdrawType;

    /**
     * 最小金额
     */
    @NotNull(message = "参数异常:minAmount Not Null")
    @Min(value = 1, message = "最小金额不能小于1")
    private BigDecimal minAmount;

    /**
     * 最大金额
     */
    @NotNull(message = "参数异常:maxAmount Not Null")
    @Min(value = 1, message = "最大金额不能小于1")
    private BigDecimal maxAmount;

    /**
     * 会员层级ID，多个逗号拼接
     */
    @NotNull(message = "参数异常:levelId Not Null")
    private String levelId;

    /**
     * 备注
     */
    @NotNull(message = "参数异常:remark Not Null")
    private String remark;

    /**
     * coin
     */
    @NotNull(message = "参数异常:coin Not Null")
    private String coin;

}
