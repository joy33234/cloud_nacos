package com.seektop.fund.payment.henglixingwxpay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
import com.seektop.enumerate.GlActionEnum
import com.seektop.exception.GlobalException
import com.seektop.fund.model.GlPaymentMerchantApp
import com.seektop.fund.model.GlPaymentMerchantaccount
import com.seektop.fund.payment.*
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode

public class HenglixingWXScript {

    private static final Logger log = LoggerFactory.getLogger(HenglixingWXScript.class)

    private OkHttpUtil okHttpUtil

    public RechargeNotify result(Object[] args) throws GlobalException {
        return null
    }


    WithdrawResult withdraw(Object[] args) throws GlobalException {
        return null
    }


    WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        return null
    }


    WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        return null
    }


    BigDecimal balanceQuery(Object[] args) throws GlobalException {
        return null
    }

    public void pay(Object[] args) {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantApp merchant = args[1] as GlPaymentMerchantApp
        GlPaymentMerchantaccount merchantaccount = args[2] as GlPaymentMerchantaccount
        RechargePrepareDO req = args[3] as RechargePrepareDO
        GlRechargeResult result = args[4] as GlRechargeResult
        Map<String, String> params = new HashMap()
        params.put("merchantNo", merchantaccount.getMerchantCode())
        params.put("merchantOrderNo", req.getOrderId())
        params.put("orderAmount", req.getAmount().setScale(0, RoundingMode.DOWN).toString())
        params.put("payWay", "ds_zl_wx_wap")
        params.put("notifyUrl", merchantaccount.getNotifyUrl() + merchant.getId())
        params.put("returnUrl", "")
        params.put("clientIp", req.getIp())
        params.put("timestamp", req.getCreateDate().getTime() + "")
        params.put("sign", ApiSignUtil.buildSignByMd5(params, merchantaccount.getPrivateKey()))
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.RECHARGE.getCode())
                .channelId(PaymentMerchantEnum.HENGLIXINGWX_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.HENGLIXINGWX_PAY.getPaymentName())
                .userId(req.getUserId() + "")
                .userName(req.getUsername())
                .tradeId(req.getOrderId())
                .build()
        try {
            log.info("HenglixingWXScript_prepare_params:{}", JSONObject.toJSONString(params))
            String payUrl = okHttpUtil.postJSON(merchantaccount.getPayUrl() + "/api/unifiedCreateOrder", JSON.toJSONString(params), requestHeader)
            log.info("HenglixingWXScript_prepare_stage_one_resp:{}", payUrl)
            JSONObject payJson = JSONObject.parseObject(payUrl)
            if (payJson != null && "1" == (payJson.getString("status"))) {
                Map<String, String> params2 = new HashMap()
                params2.put("tradeId", payJson.getString("data"))
                params2.put("merchantNo", merchantaccount.getMerchantCode())
                params2.put("timestamp", req.getCreateDate().getTime() + "")
                params2.put("sign", ApiSignUtil.buildSignByMd5(params2, merchantaccount.getPrivateKey()))

                log.info("HenglixingWXScript_prepare_stage_two_params:{}", JSON.toJSONString(params2))
                payUrl = okHttpUtil.postJSON(merchantaccount.getPayUrl() + "/api/getPayLink", JSON.toJSONString(params2), requestHeader)
                log.info("HenglixingWXScript_prepare_stage_two_resp:{}", payUrl)
                payJson = JSONObject.parseObject(payUrl)

                if (payJson != null && payJson.getString("status") == ("1")) {
                    result.setRedirectUrl(payJson.getString("data"))
                } else {
                    result.setErrorCode(1)
                    result.setErrorMsg("创建订单失败，稍后重试")
                }
            }
        } catch (Exception e) {
            result.setErrorCode(1)
            result.setErrorMsg("创建订单失败，请更换充值方式")
        }
    }

    public RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantApp merchant = args[1] as GlPaymentMerchantApp
        GlPaymentMerchantaccount merchantaccount = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>
        log.info("HenglixingWXScript_notify_resp:{}", JSON.toJSONString(resMap))
        try {
            String notifyStr = AESEncryptUtil.decrypt(resMap.get("reqBody"), merchantaccount.getPublicKey())
            JSONObject json = JSONObject.parseObject(notifyStr)
            if (json != null && "1" == (json.getString("status"))) {
                json = json.getJSONObject("data")
                if (null != json && StringUtils.isNotEmpty(json.getString("merchantOrderNo"))) {
                    return this.payQuery(okHttpUtil, merchantaccount, json.getString("merchantOrderNo"))
                }
            }
        } catch (Exception e) {
            e.printStackTrace()
        }
        return null
    }

    public RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String
        Map<String, String> params = new HashMap()
        params.put("merchantNo", account.getMerchantCode())
        params.put("merchantOrderNo", orderId)
        params.put("timestamp", System.currentTimeMillis() + "")
        params.put("sign", ApiSignUtil.buildSignByMd5(params, account.getPrivateKey()))
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.RECHARGE_QUERY.getCode())
                .channelId(PaymentMerchantEnum.HENGLIXINGWX_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.HENGLIXINGWX_PAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId(orderId)
                .build()

        log.info("HenglixingWXScript_query_params:{}", JSON.toJSONString(params))
        String resp = okHttpUtil.postJSON(account.getPayUrl() + "/api/findOrder", JSON.toJSONString(params), requestHeader)
        log.info("HenglixingWXScript_query_resp:{}", resp)

        JSONObject json = JSONObject.parseObject(resp)
        if (json != null && "1" == (json.getString("status"))) {
            json = json.getJSONObject("data")
            RechargeNotify pay = new RechargeNotify()
            pay.setAmount(json.getBigDecimal("orderAmount"))
            pay.setFee(BigDecimal.ZERO)
            pay.setOrderId(orderId)
            pay.setThirdOrderId(json.getString("platformOrderNo"))
            return pay
        }

        return null
    }


}
