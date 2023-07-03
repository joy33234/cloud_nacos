package com.seektop.fund.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 三方充值-转账提现记录数据汇总
 */
@Data
public class GlWithdrawAllCollect implements Serializable {

    /**
     * 提现金额汇总
     */
    private BigDecimal withdrawAmountCollect;

    /**
     *  手续费金额汇总
     */
    private BigDecimal handlingFeeAmountCollect;

    /**
     *  到账金额汇总
     */
    private BigDecimal arrivalAmountCollect;

    /**
     * 查询总条数
     */
    private Integer count;

    /**
     * 币种
     */
    private String coinCode;


}
