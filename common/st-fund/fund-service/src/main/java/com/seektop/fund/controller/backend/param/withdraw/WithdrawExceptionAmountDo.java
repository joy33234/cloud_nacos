package com.seektop.fund.controller.backend.param.withdraw;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WithdrawExceptionAmountDo implements Serializable {

    /**
     * 主键ID
     */
    private Integer id;

    /**
     * 币种
     */
    private String coinCode;

    /**
     * 单日提现金额
     */
    private Integer dailyAmount;

    /**
     * 单次提现金额
     */
    private Integer amount;

    /**
     * 7天累计提现金额
     */
    private Integer weeklyAmount;

    /**
     * 单日提现次数
     */
    private Integer dailyTimes;

    /**
     * 首次提现金额 -->  当日首提金额
     */
    private Integer firstAmount;

    /**
     * 当日盈利
     */
    private Integer dailyProfit;

    /**
     * 用户首次提现金额
     */
    private Integer firstWithdrawAmount;

}
