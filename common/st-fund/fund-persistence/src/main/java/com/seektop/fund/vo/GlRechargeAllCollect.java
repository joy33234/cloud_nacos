package com.seektop.fund.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 三方充值-转账充值记录数据汇总
 */
@Data
public class GlRechargeAllCollect implements Serializable {

    /**
     * 存款金额汇总
     */
    private BigDecimal depositAmountCollect;

    /**
     *  手续费金额汇总
     */
    private BigDecimal handlingFeeAmountCollect;

    /**
     *  到账金额汇总
     */
    private BigDecimal arrivalAmountCollect;

    /**
     * 查询成功总条数
     */
    private Integer count;

    /**
     * 币种
     */
    private String coinCode;


}
