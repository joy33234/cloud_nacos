package com.seektop.fund.controller.forehead.result;

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
public class WithdrawCalcFreeTimesResult implements Serializable {

    private static final long serialVersionUID = -2482098539851280130L;

    /**
     * 体育有效投注:下个等级赠送所需完成流水
     */
    private BigDecimal sportValidBet;

    /**
     * 娱乐有效投注:下个等级赠送所需完成流水
     */
    private BigDecimal funValidBet;

    /**
     * 体育投注赠送免费次数
     */
    private int sportFreeTims;

    /**
     * 娱乐投注赠送免费次数
     */
    private int funFreeTimes;

}
