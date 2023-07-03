package com.seektop.fund.dto.result.c2c;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Getter
@Setter
@NoArgsConstructor
public class C2COrderDetail implements Serializable {

    /**
     * 提现单号
     */
    private String withdrawOrderId;

    /**
     * 充值单号
     */
    private String rechargeOrderId;

    /**
     * 撮合金额
     */
    private BigDecimal matchAmount;

    /**
     * 撮合状态
     *
     * @see com.seektop.enumerate.fund.C2CWithdrawOrderStatusEnum
     */
    private Short status;

    /**
     * 创建时间
     */
    private Date createDate;

    /**
     * 撮合时间
     */
    private Date matchedDate;

    /**
     * 付款时间
     */
    private Date paymentDate;

    /**
     * 收款时间
     */
    private Date receiveDate;

}
