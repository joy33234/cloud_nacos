package com.seektop.fund.payment;

import com.seektop.exception.GlobalException;
import com.seektop.fund.model.GlPaymentMerchantaccount;
import com.seektop.fund.payment.niubipay.PaymentInfo;

import java.math.BigDecimal;

/**
 * 充值渠道 innerpay,showType,needName..字段配置
 */

public interface GlPaymentHandler {


    boolean innerPay(GlPaymentMerchantaccount account, Integer paymentId);

    Integer showType(GlPaymentMerchantaccount account, Integer paymentId);

    boolean needName(GlPaymentMerchantaccount account, Integer paymentId);

    boolean needCard(GlPaymentMerchantaccount account, Integer paymentId);

    BigDecimal paymentRate(GlPaymentMerchantaccount account, Integer paymentId);

    BigDecimal withdrawRate(GlPaymentMerchantaccount account, Integer paymentId);

    PaymentInfo payments(GlPaymentMerchantaccount account, BigDecimal amount) throws GlobalException;
}
