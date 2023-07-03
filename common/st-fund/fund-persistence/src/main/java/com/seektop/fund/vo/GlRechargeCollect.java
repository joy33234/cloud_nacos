package com.seektop.fund.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 三方充值-转账充值记录数据汇总
 */
@Data
public class GlRechargeCollect implements Serializable {

    /**
     * 存款金额汇总
     */
    private BigDecimal depositAmount;

    /**
     *  手续费金额汇总
     */
    private BigDecimal handlingFeeAmount;

    /**
     *  到账金额汇总
     */
    private BigDecimal arrivalAmount;


    /**
     * 币种
     */
    private String coinCode;


}
