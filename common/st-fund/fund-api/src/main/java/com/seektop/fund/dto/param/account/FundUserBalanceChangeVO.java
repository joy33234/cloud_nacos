package com.seektop.fund.dto.param.account;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 用户帐变&流水帐变VO
 */
@Getter
@Setter
@NoArgsConstructor
@ToString
public class FundUserBalanceChangeVO implements Serializable {

    private static final long seralVersionUID = -9201104287378471341L;

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
     * 帐变所需流水金额
     */
    private BigDecimal freezeAmount;

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
    private String operator;


    private String remark;

    private String coinCode;
}
