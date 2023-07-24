package com.seektop.fund.payment.baifupay

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
import org.springframework.util.ObjectUtils

import java.math.RoundingMode

/**
 * 百富支付
 * @auth joy
 * @date 2021-05-18
 */

class BaifuScript_recharge {

    private static final Logger log = LoggerFactory.getLogger(BaifuScript_recharge.class)

    private OkHttpUtil okHttpUtil

    RechargeNotify result(Object[] args) throws GlobalException {
        return notify(args)
    }

    void pay(Object[] args) {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantApp merchant = args[1] as GlPaymentMerchantApp
        GlPaymentMerchantaccount payment = args[2] as GlPaymentMerchantaccount
        RechargePrepareDO req = args[3] as RechargePrepareDO
        GlRechargeResult result = args[4] as GlRechargeResult

        String channel = ""
        if (merchant.getPaymentId() == FundConstant.PaymentType.BANKCARD_TRANSFER) {
            channel = "11"
        }
        if (StringUtils.isNotEmpty(channel)) {
            prepareScan(merchant, payment, req, result, channel)
        } else {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("该充值方式暂不支持")
            return
        }
    }

    private void prepareScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, RechargePrepareDO req, GlRechargeResult result, String channel) {
        Map<String, String> DataContentParms = new HashMap<String, String>()
        DataContentParms.put("merchant_code", payment.getMerchantCode())
        DataContentParms.put("orderid", req.getOrderId().toLowerCase())
        DataContentParms.put("channel", channel)
        DataContentParms.put("amount", req.getAmount().setScale(2, RoundingMode.DOWN) + "")
        DataContentParms.put("timestamp", (int)(System.currentTimeMillis() / 1000) + "")
        DataContentParms.put("reference", "reference/attach")
        DataContentParms.put("notifyurl", payment.getNotifyUrl() + merchant.getId())
        DataContentParms.put("httpurl", payment.getNotifyUrl() + merchant.getId())

        String toSign = MD5.toAscii(DataContentParms) + "&" + payment.getPrivateKey()
        log.info("toSign:{}",toSign)
        DataContentParms.put("sign", MD5.md5(toSign))

        log.info("BaifuScript_Prepare_Params:{}", JSON.toJSONString(DataContentParms))
        GlRequestHeader requestHeader =
                getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode(), payment.getChannelId(), payment.getChannelName())
        String restr = okHttpUtil.postJSON(payment.getPayUrl() + "/v4/pay", JSON.toJSONString(DataContentParms),  requestHeader)
        log.info("BaifuScript_Prepare_resStr:{}", restr)

        JSONObject json = JSON.parseObject(restr);
        if (json == null) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("网络请求超时，稍后重试")
            return
        }
        JSONObject dataJSON = json.getJSONObject("data")
        if (!json.getBoolean("status") || ObjectUtils.isEmpty(dataJSON)) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg("请求失败:" + json.getString("message"))
            return
        }

        if (StringUtils.isEmpty(dataJSON.getString("return"))) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg("商户异常" + json.getString("message"))
            return
        }
        result.setRedirectUrl(dataJSON.getString("return"))
    }

    RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount payment = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>

        log.info("BaifuScript_Notify_resMap:{}", JSON.toJSONString(resMap))
        JSONObject json = JSON.parseObject(resMap.get("reqBody"))
        String orderid = json.getString("order_id")
        String thirdOrderId = json.getString("transaction_id")
        String amount = json.getString("amount")
        if (StringUtils.isNotEmpty(orderid)) {
            return payQuery(okHttpUtil, payment, orderid, thirdOrderId, amount)
        }
        return null
    }

    RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String
        String thirdOrderId = args[3] as String
        String amount = args[4] as String

        Map<String, String> DataContentParms = new HashMap<String, String>()
        DataContentParms.put("merchant_code", account.getMerchantCode())
        DataContentParms.put("orderid", orderId)
        DataContentParms.put("transid", thirdOrderId)
        DataContentParms.put("amount", amount)
        DataContentParms.put("timestamp", (int)(System.currentTimeMillis() / 1000) + "")

        String toSign = MD5.toAscii(DataContentParms) + "&" + account.getPrivateKey()
        DataContentParms.put("sign", MD5.md5(toSign))

        log.info("BaifuScript_Query_reqMap:{}", JSON.toJSONString(DataContentParms))
        GlRequestHeader requestHeader = getRequestHeard(null, null, orderId, GlActionEnum.RECHARGE_QUERY.getCode(), account.getChannelId(), account.getChannelName())
        String resStr = okHttpUtil.postJSON(account.getPayUrl() + "/v4/query", JSON.toJSONString(DataContentParms),  requestHeader)
        log.info("BaifuScript_Query_resStr:{}", resStr)

        JSONObject json = JSON.parseObject(resStr)

        //支付状态 VALIDATED/PENDING/PAID/SUCCESS /false
        if (json != null && ("SUCCESS" == json.getString("status") || "PAID" == json.getString("status"))) {
            RechargeNotify pay = new RechargeNotify()
            pay.setAmount(json.getBigDecimal("real_amount").setScale(2, RoundingMode.DOWN))
            pay.setFee(BigDecimal.ZERO)
            pay.setOrderId(orderId.toUpperCase())
            pay.setThirdOrderId(thirdOrderId)
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

    /**
     * 充值USDT汇率
     *
     * @param args
     * @return
     */
    public BigDecimal paymentRate(Object[] args) {
        return null
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