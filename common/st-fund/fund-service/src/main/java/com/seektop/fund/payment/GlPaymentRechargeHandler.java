package com.seektop.fund.payment;

import com.seektop.exception.GlobalException;
import com.seektop.fund.model.GlPaymentMerchantApp;
import com.seektop.fund.model.GlPaymentMerchantaccount;

import java.util.Map;

public interface GlPaymentRechargeHandler {

    RechargeNotify result(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, Map<String, String> resMap) throws GlobalException;

    void prepare(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result) throws GlobalException;

    /**
     * 支付成功返回RechargeNotify、否则返回Null
     *
     * @param merchant
     * @param account
     * @param resMap
     * @return
     * @throws GlobalException
     */
    RechargeNotify notify(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, Map<String, String> resMap) throws GlobalException;

    /**
     * 支付成功返回RechargeNotify、否则返回Null
     *
     * @param account
     * @param orderId
     * @return
     * @throws GlobalException
     */
    RechargeNotify query(GlPaymentMerchantaccount account, String orderId) throws GlobalException;


}
