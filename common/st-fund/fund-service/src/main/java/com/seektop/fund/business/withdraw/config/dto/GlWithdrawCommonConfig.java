package com.seektop.fund.business.withdraw.config.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@ToString
public class GlWithdrawCommonConfig implements Serializable {

    private int multiple; // 提现流水倍数
    private BigDecimal amountLimit; //每天提现金额上限
    private String tipStatus; //提示说明开关 1-开启、0-关闭
    private String coin; //币种

}