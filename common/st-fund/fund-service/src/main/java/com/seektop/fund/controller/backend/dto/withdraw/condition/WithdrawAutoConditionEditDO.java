package com.seektop.fund.controller.backend.dto.withdraw.condition;

import com.seektop.fund.model.WithdrawAutoConditionMerchantAccount;
import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
public class WithdrawAutoConditionEditDO implements Serializable {

    /**
     * 主键ID
     */
    @NotNull(message = "参数异常:id Not Null")
    private Integer id;

    /**
     * 条件名称
     */
    @NotNull(message = "参数异常:conditionName Not Null")
    private String conditionName;

    /**
     * 三方出款商户及相关设置
     */
    @NotNull(message = "参数异常:merchantId Not Null")
    private String merchantId;

    /**
     * 三方出款商户相关设置
     */
    private List<WithdrawAutoConditionMerchantAccount> merchantAccounts;

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
     * 用户层级ID
     */
    @NotNull(message = "参数异常:levelId Not Null")
    private String levelId;

    /**
     * 备注
     */
    @NotNull(message = "参数异常:remark Not Null")
    private String remark;

}
