package com.seektop.fund.payment.aihuiPay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
import com.seektop.common.utils.DateUtils
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
 * 爱汇支付
 * @auth  joy
 * @date 2021-07-29
 */

class AiHuiScript_recharge {

    private static final Logger log = LoggerFactory.getLogger(AiHuiScript_recharge.class)

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

        prepare(merchant, payment, req, result)
    }

    private void prepare(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, RechargePrepareDO req, GlRechargeResult result) {

        Map<String, String> paramMap = new HashMap<>()
        paramMap.put("Client", payment.getMerchantCode())
        paramMap.put("ReferenceID", req.getOrderId())
        paramMap.put("PlatformID", "1")
        paramMap.put("BankID", "0")
        paramMap.put("TypeID", "2")
        paramMap.put("CurrencyCode", "RMB")
        paramMap.put("Amount", req.getAmount().setScale(2, RoundingMode.DOWN).toString())
        paramMap.put("SubmitDateTime", DateUtils.format(req.getCreateDate(), DateUtils.YYYY_MM_DD_HH_MM_SS))
        paramMap.put("RedirectUrl", payment.getNotifyUrl() + merchant.getId())
        paramMap.put("NotificationUrl", payment.getNotifyUrl() + merchant.getId())
        paramMap.put("IP", req.getIp())
        paramMap.put("PlayerID", req.getUserId().toString())

        String toSign = MD5.toAscii(paramMap) + "&" + payment.getPrivateKey()
        paramMap.put("Sign", MD5.md5(toSign))
        paramMap.put("PlayerRealName", req.getFromCardUserName())

        log.info("AiHuiScript_Prepare_Params: {}", JSON.toJSONString(paramMap))
        GlRequestHeader requestHeader =
                getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode(), payment.getChannelId(), payment.getChannelName())
        String restr = okHttpUtil.post(payment.getPayUrl() + "/api/deposit", paramMap, 30L, requestHeader)
        log.info("AiHuiScript_Prepare_Resp: {}", restr)
        JSONObject json = JSONObject.parseObject(restr);
        if (json == null) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("网络请求超时，稍后重试")
            return
        }
        if (json.getString("Status") != "1" || StringUtils.isEmpty(json.getString("Content"))) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg(json.getString("Message"))
            return
        }
       result.setRedirectUrl(json.getString("Content"))
    }

    RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount payment = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>
        log.info("AiHuiScript_Notify_resMap:{}", JSON.toJSONString(resMap))
        JSONObject json = JSON.parseObject(resMap.get("reqBody"));
        String orderid = json.getString("ReferenceID")
        String amount = json.getString("SubmitAmount")
        if (StringUtils.isNotEmpty(orderid) && StringUtils.isNotEmpty(amount)) {
            return payQuery(okHttpUtil, payment, orderid, amount, args[4])
        }
        return null
    }

    RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount payment = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String
        String amount = args[3] as String

        Map<String, String> paramMap = new HashMap<>()
        paramMap.put("Client", payment.getMerchantCode())
        paramMap.put("ReferenceID", orderId)

        String toSign = MD5.toAscii(paramMap) + "&" + payment.getPrivateKey()
        paramMap.put("Sign", MD5.md5(toSign))
        paramMap.put("Amount", amount)


        log.info("AiHuiScript_Query_Params: {}", JSON.toJSONString(paramMap))
        GlRequestHeader requestHeader =
                getRequestHeard(null, null, orderId, GlActionEnum.RECHARGE_QUERY.getCode(), payment.getChannelId(), payment.getChannelName())
        String result = okHttpUtil.post(payment.getPayUrl() + "/api/depositstatus", paramMap, 30L, requestHeader)
        log.info("AiHuiScript_Query_resStr: {}", result)

        JSONObject json = JSON.parseObject(result)
        if (json == null) {
            return null
        }
        //1：成功；2：Pending；3：失败
        if ("1" != json.getString("Status")) {
            RechargeNotify pay = new RechargeNotify()
            pay.setAmount(json.getBigDecimal("ActualAmount").setScale(2, RoundingMode.DOWN))
            pay.setFee(BigDecimal.ZERO)
            pay.setOrderId(orderId)
            pay.setThirdOrderId(json.getString("TransactionID"))
            pay.setRsp("SUCCESS")
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
     * 获取头部信息
     *
     * @param userId
     * @param userName
     * @param orderId
     * @return
     */
    private GlRequestHeader getRequestHeard(String userId, String userName, String orderId, String code, Integer channelId, String channleName) {
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(code)
                .channelId(channelId + "")
                .channelName(channleName)
                .userId(userId)
                .userName(userName)
                .tradeId(orderId)
                .build()
        return requestHeader
    }
}