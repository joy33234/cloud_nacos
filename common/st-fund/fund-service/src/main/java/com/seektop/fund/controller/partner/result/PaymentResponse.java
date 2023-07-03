package com.seektop.fund.controller.partner.result;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

@Data
public class PaymentResponse implements Serializable {

    private static final long serialVersionUID = -1650765458400443008L;

    /**
     * 支付方式ID
     */
    private Integer paymentId;

    /**
     * 支付方式名称
     */
    private String paymentName;

    /**
     * 最低金额
     */
    private BigDecimal minAmount;

    /**
     * 最高金额
     */
    private BigDecimal maxAmount;

    /**
     * 充值通道
     */
    private List<PaymentMerchantResponse> merchants;
}
