package com.seektop.fund.dto.result.withdraw;

import com.seektop.enumerate.digital.DigitalCoinEnum;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Getter
@Setter
@NoArgsConstructor
@ToString
public class GlWithdrawEffectBetDO implements Serializable {

    private static final long serialVersionUID = 4376433846259522290L;

    private Integer userId;

    private Boolean isClean;

    private BigDecimal amount;

    //所需流水
    private BigDecimal effectAmount;

    private Date changeDate;
    //已完成流水
    private BigDecimal validBalance;
    //币种
    private String coin = DigitalCoinEnum.CNY.getCode();

    /**
     * 提现所需流水
     */
    private BigDecimal requiredBet;

    /**
     * 上一次完成流水之后，账户累计变动金额
     */
    private BigDecimal grandTotalBalance;

    /**
     * 流水累计开始时间（上一次资金变动，流水已完成时间）
     */
    private Date effectStartTime;

    /**
     * 输光清零标识：输光逻辑上线之后，用户是否已完成所需流水(0：未完成、1：已完成)
     */
    private Integer loseClean;

    /**
     * 上次提款后，账户累计变动金额
     */
    private BigDecimal lastTotalBalance;

    /**
     * 输光逻辑输赢开始统计时间（上次提款后，第一次资金变动完成时间）
     */
    private Date loseStartTime;

    /**
     * 上次提款后开始计算输光：提款后为true, 提款后第一次账变为false
     */
    private Boolean lose;

}
