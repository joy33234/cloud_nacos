package com.seektop.fund.business.withdraw.config.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@ToString
public class GlWithdrawPolicyAmountConfig implements Serializable {

    private int dailyAmount; // 单日提现金额
    private int amount; // 单次提现金额
    private int weeklyAmount; // 7天累计提现金额
    private int dailyTimes; // 单日提现次数
    private int firstAmount; // 首次提现金额 --> 当日首提金额
    private int dailyProfit; // 当日盈利
    private int firstWithdrawAmount; //首次提现金额
    private String coinCode;//币种

}
