package com.seektop.fund.controller.forehead.param.withdraw;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class WithdrawConfirmDto implements Serializable {

    private static final long serialVersionUID = 1505357366366285367L;

    /**
     * 提现订单ID
     */
    @NotNull(message = "orderId is Not Null")
    private String orderId;

    /**
     * 提现金额
     */
    @NotNull(message = "amount is Not Null")
    private BigDecimal amount;

    /**
     * 姓名
     */
    @NotNull(message = "name is Not Null")
    private String name;

    /**
     * 收款卡号（后四位）
     */
    @NotNull(message = "cardNo is Not Null")
    private String cardNo;


}
