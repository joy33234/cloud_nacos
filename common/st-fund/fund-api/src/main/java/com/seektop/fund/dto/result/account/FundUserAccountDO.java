package com.seektop.fund.dto.result.account;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FundUserAccountDO implements Serializable {

    private static final long serialVersionUID = -7706741511345374418L;

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
     * 更新时间
     */
    private Date lastUpdate;

    /**
     * 上一次充值成功时间/上次流水调整后，剩余流水为0的时间
     */
    private Date lastRecharge;

}