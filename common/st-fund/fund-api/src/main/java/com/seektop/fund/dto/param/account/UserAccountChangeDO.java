package com.seektop.fund.dto.param.account;

import lombok.Data;
import lombok.ToString;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Data
@ToString
public class UserAccountChangeDO implements Serializable {
    /**
     * 交易订单号
     */
    private String tradeId;

    /**
     * 用户ID
     */
    private Integer userId;

    /**
     * 帐变金额(充值、上分、红利)
     */
    private BigDecimal amount;

    /**
     * 流水倍数
     */
    private Integer multiple;

    /**
     * 帐变时间
     */
    private Date changeDate;

    /**
     * 操作人
     */
    private String operator = "system";


    private String remark;

    private String isFake;
    /**
     * @link com.seektop.enumerate.fund.BettingBalanceEnum
     */
    private Integer type;

    private Integer subType;
}
