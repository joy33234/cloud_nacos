package com.seektop.fund.payment.cpPay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
import com.seektop.common.utils.MD5
import com.seektop.constant.FundConstant
import com.seektop.enumerate.GlActionEnum
import com.seektop.exception.GlobalException
import com.seektop.fund.model.GlPaymentMerchantApp
import com.seektop.fund.model.GlPaymentMerchantaccount
import com.seektop.fund.payment.GlRechargeResult
import com.seektop.fund.payment.RechargeNotify
import com.seektop.fund.payment.RechargePrepareDO
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode

/**
 * @desc CP支付
 * @date 2021-09-23
 * @auth Otto
 */
public class CPScript_recharge {

    private static final String SERVER_PAY_URL = "/v4/pay"
    private static final String SERVER_QUERY_URL = "/v4/query"
    private static final Logger log = LoggerFactory.getLogger(CPScript_recharge.class)
    private OkHttpUtil okHttpUtil

    public RechargeNotify result(Object[] args) throws GlobalException {
        return null
    }

    public void pay(Object[] args) {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantApp merchant = args[1] as GlPaymentMerchantApp
        GlPaymentMerchantaccount account = args[2] as GlPaymentMerchantaccount
        RechargePrepareDO req = args[3] as RechargePrepareDO
        GlRechargeResult result = args[4] as GlRechargeResult
        String payType = ""

        if (FundConstant.PaymentType.BANKCARD_TRANSFER == merchant.getPaymentId()) {
            payType = "11"
        }
        if (StringUtils.isEmpty(payType)) {
            result.setErrorMsg("支付方式不支持")
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            return
        }
        prepareToScan(merchant, account, req, result, payType)
    }


    private void prepareToScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result, String payType) throws GlobalException {

        Map<String, String> params = new HashMap<>()
        params.put("amount", req.getAmount().setScale(2, RoundingMode.DOWN).toString())
        params.put("channel", payType)
        params.put("httpurl", account.getNotifyUrl() + merchant.getId())
        params.put("merchant_code",account.getMerchantCode())
        params.put("notifyurl", account.getNotifyUrl() + merchant.getId())
        params.put("orderid", StringUtils.lowerCase(req.getOrderId()))
        params.put("reference", "referenece")
        params.put("timestamp", System.currentTimeSeconds().toString() )

        String toSign = MD5.toAscii(params) + "&" + account.getPrivateKey()
        params.put("sign", MD5.md5(toSign))
        log.info("CPScript_recharge_prepare_params:{}", JSON.toJSONString(params))

        GlRequestHeader requestHeader =
                getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode(), account.getChannelId(), account.getChannelName())
        String resStr = okHttpUtil.postJSON(account.getPayUrl() + SERVER_PAY_URL, JSON.toJSONString(params), requestHeader)

        log.info("CPScript_recharge_prepare_resp:{}", resStr)
        if (StringUtils.isEmpty(resStr)) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("网络请求超时，稍后重试")
            return
        }
        JSONObject json = JSONObject.parseObject(resStr)
        if ("true" != (json.getString("status"))) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg(json.getString("message"))
            return
        }

        JSONObject dataJSON = json.getJSONObject("data");
        result.setThirdOrderId(dataJSON.getString("transaction_id"))
        String url = dataJSON.getString("return")
        if (StringUtils.isNotEmpty(url)) {
            result.setRedirectUrl(url)
        }

    }

    public RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>
        log.info("CPScript_notify_resp:{}", JSON.toJSONString(resMap))

        JSONObject json = JSONObject.parseObject(resMap.get("reqBody"))
        String amount =json.getString("amount");
        String orderId ="";
        if (json == null) {
            orderId = resMap.get("order_id")

        } else {
            orderId = json.getString("order_id")

        }
        if (StringUtils.isNotEmpty(orderId)) {
            return this.payQuery(okHttpUtil, account, orderId, args[4] , amount)
        }
        return null
    }

    public RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String
        String amount = args[4] as String //查单用

        Map<String, String> params = new HashMap<>()
        params.put("amount", amount)
        params.put("merchant_code", account.getMerchantCode())
        params.put("orderid", orderId)
        params.put("timestamp", System.currentTimeMillis().toString())
        String toSign = MD5.toAscii(params) + "&" + account.getPrivateKey()
        params.put("sign", MD5.md5(toSign))

        log.info("CPScript_query_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = getRequestHeard(null, null, orderId, GlActionEnum.RECHARGE_QUERY.getCode(), account.getChannelId(), account.getChannelName())
        String resStr = okHttpUtil.postJSON(account.getPayUrl() + SERVER_QUERY_URL, JSON.toJSONString(params), requestHeader)
        log.info("CPScript_query_resp:{}", resStr)

        if (StringUtils.isEmpty(resStr)) {
            return null
        }

        JSONObject json = JSON.parseObject(resStr)
        if ("SUCCESS" == (json.getString("status")) || "PAID" == (json.getString("status")) ) {
            RechargeNotify pay = new RechargeNotify()
            pay.setAmount(json.getBigDecimal("real_amount").setScale(2, RoundingMode.DOWN))
            pay.setFee(BigDecimal.ZERO)
            pay.setOrderId(StringUtils.upperCase(orderId))
            pay.setThirdOrderId(json.getString("transid"))
            pay.setRsp("ok")
            return pay

        }
        return null
    }

    void cancel(Object[] args) throws GlobalException {

    }

    /**
     * 是否为内部渠道
     *
     * @param args
     * @return
     */
    public boolean innerpay(Object[] args) {
        return false
    }

    /**
     * 根据支付方式判断-转帐是否需要实名
     *
     * @param args
     * @return
     */
    public boolean needName(Object[] args) {
        Integer paymentId = args[1] as Integer
        if (paymentId == FundConstant.PaymentType.BANKCARD_TRANSFER
                || paymentId == FundConstant.PaymentType.ALI_TRANSFER
                || paymentId == FundConstant.PaymentType.UNION_TRANSFER) {
            return true
        }
        return false
    }

    /**
     * 充值是否需要卡号
     *
     * @param args
     * @return
     */
    public boolean needCard(Object[] args) {
        return false
    }

    /**
     * 是否显示充值订单祥情
     *
     * @param args
     * @return
     */
    public Integer showType(Object[] args) {
        return FundConstant.ShowType.NORMAL
    }

    private static GlRequestHeader getRequestHeard(String userId, String userName, String orderId, String code, Integer channelId, String channelName) {
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(code)
                .channelId(channelId + "")
                .channelName(channelName)
                .userId(userId)
                .userName(userName)
                .tradeId(orderId)
                .build()
        return requestHeader
    }



}