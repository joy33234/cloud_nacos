package com.seektop.fund.payment;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 代付API请求Result
 */
@Getter
@Setter
public class WithdrawResult implements Serializable {

    private static final long serialVersionUID = 5975969591519421603L;
    /**
     * 出款订单ID
     */
    private String orderId;
    /**
     * 代付请求数据
     */
    private String reqData;
    /**
     * 代付响应数据
     */
    private String resData;
    /**
     * 代付结果消息
     */
    private String message;

    /**
     * 商户订单号
     */
    private String thirdOrderId;

    /**
     * True:请求成功、False:请求失败或者请求超时
     */
    private boolean valid = false;

    /**
     * 预计到账USDT金额
     */
    private BigDecimal usdtAmount;

    /**
     * USDT汇率
     */
    private BigDecimal rate;

}