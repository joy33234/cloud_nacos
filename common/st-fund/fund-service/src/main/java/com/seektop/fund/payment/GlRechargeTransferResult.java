package com.seektop.fund.payment;


import com.seektop.base.LocalKey;
import com.seektop.constant.FundConstant;
import com.seektop.fund.controller.forehead.result.RechargeDigitalInfo;
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
public class GlRechargeTransferResult implements Serializable {

    private static final long serialVersionUID = 5583058606097241685L;

    /**
     * 订单ID
     */
    private String tradeNo;

    /**
     * 订单金额
     */
    private BigDecimal amount;

    /**
     * 充值异常码  0：系统异常   1 - 三方异常   2:成功
     */
    private int errorCode = FundConstant.RechargeErrorCode.NORMAL;
    /**
     * 异常信息
     */
    private String errorMsg;


    /**
     * 附言
     */
    private String keyword;

    /**
     * 银行ID
     */
    private Integer bankId;
    /**
     * 银行名称
     */
    private String bankName;
    /**
     * 开户支行
     */
    private String bankBranchName;
    /**
     * 银行卡号
     */
    private String cardNo;
    /**
     * 开户人姓名
     */
    private String name;


    /**
     * 数字货币信息
     */
    private RechargeDigitalInfo digitalInfo;

    private String paymentName;

    private LocalKey keyConfig;
}