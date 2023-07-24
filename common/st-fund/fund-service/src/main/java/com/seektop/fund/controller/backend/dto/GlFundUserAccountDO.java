package com.seektop.fund.controller.backend.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Setter
@Getter
@NoArgsConstructor
@ToString
public class GlFundUserAccountDO implements Serializable {

    private static final long serialVersionUID = -4172071578313458037L;
    /**
     * 用户ID
     */
    private Integer userId;

    /**
     * 余额
     */
    private BigDecimal balance;

    /**
     * 可用流水
     */
    private BigDecimal validBalance;

    /**
     * 冻结金额
     */
    private BigDecimal freezeBalance;

    /**
     * 更新
     */
    private Date lastUpdate;
    /**
     * 上一次充值成功时间/上次流水调整后，剩余流水为0的时间
     */
    private Date lastRecharge;

    /**
     * 输光逻辑输赢开始统计时间（上次提款后，第一次资金变动完成时间）
     */
    private Date loseStartTime;

}