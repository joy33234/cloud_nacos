package com.seektop.fund.controller.backend.dto.withdraw.config;

import com.seektop.common.mvc.ManageParamBaseDO;
import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;

@Getter
@Setter
public class CommonConfigDO extends ManageParamBaseDO implements Serializable {

    @NotNull(message = "参数异常:multiple Not Null")
    private int multiple; // 提现流水倍数

    @NotNull(message = "参数异常:amountLimit Not Null")
    @Min(value = 1, message = "每天提现金额上线不能小于1")
    private BigDecimal amountLimit; //每天提现金额上限

    /**
     * tipStatus = 0-开启、1-关闭
     */
    @NotNull(message = "参数异常:tipStatus Not Null")
    private String tipStatus; //提示说明开关

    /**
     * 币种
     */
    @NotNull(message = "参数异常:coin Not Null")
    private String coin;

}
