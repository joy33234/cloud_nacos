package com.seektop.fund.payment.longtengpay

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
 * @desc 龙腾支付
 * @date 2021-06-21
 * @auth joy
 */
public class LongTengScript_recharge {


    private static final Logger log = LoggerFactory.getLogger(LongTengScript_recharge.class)

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
            payType = "8"//卡卡
        }
        if (StringUtils.isEmpty(payType)) {
            result.setErrorMsg("支付方式不支持")
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            return
        }
        prepare(merchant, account, req, result, payType)
    }


    private void prepare(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result, String payType) throws GlobalException {

        Map<String, String> bodyParams = new LinkedHashMap<>()
        bodyParams.put("out_trade_no", req.getOrderId())
        bodyParams.put("channel", payType)
        bodyParams.put("amount", req.getAmount().setScale(2, RoundingMode.DOWN).toString())
        bodyParams.put("currency", "CNY")
        bodyParams.put("notify_url", account.getNotifyUrl() + merchant.getId())
        bodyParams.put("return_url", "")
        bodyParams.put("send_ip", req.getIp())
        JSONObject nameJSON = new JSONObject();
        nameJSON.put("real_name",req.getFromCardUserName())
        bodyParams.put("attach", nameJSON.toJSONString())

        Map<String, String> headParams = new LinkedHashMap<>()
        headParams.put("sid", account.getMerchantCode())
        headParams.put("timestamp", System.currentTimeMillis().toString())
        headParams.put("nonce", UUID.randomUUID().toString())
        headParams.put("url", "/pay/qrorder")

        String headStr = toAscii(headParams);
        String bodyStr = toAscii(bodyParams);
        headParams.put("sign", MD5.md5(headStr + bodyStr + account.getPrivateKey()).toUpperCase())


        log.info("LongTengScript_recharge_prepare_headParams:{} bodyParams:{}", JSON.toJSONString(headParams), JSON.toJSONString(bodyParams))
        GlRequestHeader requestHeader =
                getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode(), account.getChannelId(), account.getChannelName())
        String resStr = okHttpUtil.post(account.getPayUrl() + "/pay/qrorder", bodyParams,requestHeader,headParams)
        log.info("LongTengScript_recharge_prepare_resp:{}", resStr)
        if (StringUtils.isEmpty(resStr)) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("网络请求超时，稍后重试")
            return
        }
        JSONObject json = JSONObject.parseObject(resStr)
        if (json == null || "1000" != json.getString("code")) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg(json.getString("msg"))
            return
        }
        JSONObject dataJSON = json.getJSONObject("pay_url");
        if (ObjectUtils.isEmpty(dataJSON) || StringUtils.isEmpty(dataJSON.getString("pay_url"))) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg(json.getString("msg"))
            return
        }
        result.setRedirectUrl(dataJSON.getString("pay_url"))
    }




    public RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>
        log.info("LongTengScript_notify_resp:{}", JSON.toJSONString(resMap))
        String orderId = resMap.get("out_trade_no")
        if (StringUtils.isNotEmpty(orderId)) {
            return this.payQuery(okHttpUtil, account, orderId, args[4])
        }
        return null
    }

    public RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String

        Map<String, String> bodyParams = new LinkedHashMap<>()
        bodyParams.put("out_trade_no", orderId)

        Map<String, String> headParams = new LinkedHashMap<>()
        headParams.put("sid", account.getMerchantCode())
        headParams.put("timestamp", System.currentTimeMillis().toString())
        headParams.put("nonce", UUID.randomUUID().toString())
        headParams.put("url", "/pay/orderquery")

        String headStr = toAscii(headParams);
        String bodyStr = toAscii(bodyParams);
        headParams.put("sign", MD5.md5(headStr + bodyStr + account.getPrivateKey()).toUpperCase())


        log.info("LongTengScript_query_headParams:{}", JSON.toJSONString(headParams))
        GlRequestHeader requestHeader = getRequestHeard(null, null, orderId, GlActionEnum.RECHARGE_QUERY.getCode(), account.getChannelId(), account.getChannelName())
        String resStr = okHttpUtil.post(account.getPayUrl() + "/pay/orderquery", bodyParams, requestHeader,headParams)
        log.info("LongTengScript_query_resp:{}", resStr)
        if (StringUtils.isEmpty(resStr)) {
            return null
        }
        JSONObject json = JSON.parseObject(resStr)
        if (json == null || "1000" != json.getString("code")) {
            return null
        }
        // 支付状态:status WAIT 等待支付   SUCCESS 支付成功    CLOSE订单关闭
        if ("SUCCESS" == (json.getString("status"))) {
            RechargeNotify pay = new RechargeNotify()
            pay.setAmount(json.getBigDecimal("pay_amount").setScale(2, RoundingMode.DOWN))
            pay.setFee(BigDecimal.ZERO)
            pay.setOrderId(orderId)
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

    /**
     * ASCII排序
     *
     * @param parameters
     * @return
     */
    public static String toAscii(Map<String, String> parameters) {
        List<Map.Entry<String, String>> infoIds = new ArrayList<>(parameters.entrySet())
        // 对所有传入参数按照字段名的 ASCII 码从小到大排序（字典序）
        Collections.sort(infoIds, Comparator.comparing({ o -> (o as Map.Entry<String, String>).getKey() }))
        StringBuffer sign = new StringBuffer()
        for (Map.Entry<String, String> item : infoIds) {
            String k = item.getKey()
            if (!org.springframework.util.StringUtils.isEmpty(item.getKey())) {
                Object v = item.getValue()
                sign.append(k  + v )
            }
        }
        return sign.toString()
    }
}