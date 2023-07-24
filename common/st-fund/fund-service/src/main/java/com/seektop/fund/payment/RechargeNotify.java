package com.seektop.fund.payment;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.math.BigDecimal;

@Setter
@Getter
@NoArgsConstructor
@ToString
public class RechargeNotify implements Serializable {


    private static final long serialVersionUID = -6658305276392412548L;

    /**
     * 充值订单号
     */
    private String orderId;

    /**
     * 支付金额
     */
    private BigDecimal amount;

    /**
     * 支付USDT数量
     */
    private BigDecimal payDigitalAmount;

    /**
     * 0:待支付、1:成功、2:撤销
     */
    private Integer status;

    /**
     * 手续费
     */
    private BigDecimal fee = BigDecimal.ZERO;

    /**
     * 第三方订单号
     */
    private String thirdOrderId;

    /**
     * 收款银行ID
     */
    private Integer bankId;

    /**
     * 收款银行
     */
    private String bankName;

    /**
     * 收款人
     */
    private String bankCardName;

    /**
     * 收款卡号
     */
    private String bankCardNo;

    /**
     * 接到回调的响应信息
     */
    private String rsp;

    /**
     * USDT对RMB——交易时汇率
     */
    private BigDecimal realRate;

    /**
     * 付款卡号/USDT地址
     */
    private String payAddress;

}