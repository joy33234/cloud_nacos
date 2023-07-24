package com.seektop.fund.payment.longtengpay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
import com.seektop.common.utils.MD5
import com.seektop.enumerate.GlActionEnum
import com.seektop.exception.GlobalException
import com.seektop.fund.business.GlPaymentChannelBankBusiness
import com.seektop.fund.model.GlWithdraw
import com.seektop.fund.model.GlWithdrawMerchantAccount
import com.seektop.fund.payment.WithdrawNotify
import com.seektop.fund.payment.WithdrawResult
import com.seektop.fund.payment.groovy.BaseScript
import com.seektop.fund.payment.groovy.ResourceEnum
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode

/**
 * @desc 龙腾支付
 * @date 2021-06-21
 * @auth joy
 */
public class LongTengScript_withdraw {


    private static final Logger log = LoggerFactory.getLogger(LongTengScript_withdraw.class)

    private OkHttpUtil okHttpUtil

    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness


    public WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount account = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

        Map<String, String> bodyParams = new LinkedHashMap<>()
        bodyParams.put("out_trade_no", req.getOrderId())
        bodyParams.put("bank_account", req.getName())
        bodyParams.put("card_no", req.getCardNo())
        bodyParams.put("bank_name", glPaymentChannelBankBusiness.getBankCode(req.getBankId(), account.getChannelId()))
        bodyParams.put("bank_province", "上海市")
        bodyParams.put("bank_city", "上海市")
        bodyParams.put("sub_bank", "上海支行")
        bodyParams.put("amount", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString())
        bodyParams.put("notify_url", account.getNotifyUrl() + account.getMerchantId())
        bodyParams.put("currency", "CNY")
        bodyParams.put("send_ip", req.getIp())
        bodyParams.put("attach", "")

        Map<String, String> headParams = new LinkedHashMap<>()
        headParams.put("sid", account.getMerchantCode())
        headParams.put("timestamp", System.currentTimeMillis().toString())
        headParams.put("nonce", UUID.randomUUID().toString())
        headParams.put("url", "/payfor/trans")

        String headStr = toAscii(headParams);
        String bodyStr = toAscii(bodyParams);
        headParams.put("sign", MD5.md5(headStr + bodyStr + account.getPrivateKey()).toUpperCase())

        log.info("LongTengScript_doTransfer_headParams：{} bodyParams:{}", JSON.toJSONString(headParams), JSON.toJSONString(bodyParams))
        GlRequestHeader requestHeader =
                this.getRequestHeard(req.getUserId() + "", req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(), account.getChannelId(), account.getChannelName())

        String resStr = okHttpUtil.post(account.getPayUrl() + "/payfor/trans", bodyParams, requestHeader,headParams)
        log.info("LongTengScript_doTransfer_resp:{}", resStr)
        JSONObject json = JSON.parseObject(resStr)

        WithdrawResult result = new WithdrawResult()
        result.setOrderId(req.getOrderId())
        result.setReqData(JSON.toJSONString(headParams) + JSON.toJSONString(bodyParams))
        result.setResData(resStr)
        if (StringUtils.isEmpty(resStr)) {
            result.setValid(false)
            result.setMessage("API异常:请联系出款商户确认订单.")
            return result
        }
        if ("1000" != json.getString("code") || StringUtils.isEmpty(json.getString("trade_no"))) {
            result.setValid(false)
            result.setMessage(json.getString("msg"))
            return result
        }
        result.setValid(true)
        result.setMessage("ok")
        return result
    }

    public WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        Map<String, String> resMap = args[2] as Map<String, String>
        log.info("LongTengScript_doTransferNotify_resp:{}", JSON.toJSONString(resMap))
        String orderId = resMap.get("out_trade_no")
        String amount = resMap.get("amount")
        if (StringUtils.isNotEmpty(orderId) && StringUtils.isNotEmpty(amount)) {
            return this.withdrawQuery(okHttpUtil, merchant, orderId, amount, args[3])
        }
        return null
    }

    public WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2] as String
        String amount = args[3] as String

        Map<String, String> bodyParams = new LinkedHashMap<>()
        bodyParams.put("out_trade_no", orderId)
        bodyParams.put("amount", amount)

        Map<String, String> headParams = new LinkedHashMap<>()
        headParams.put("sid", merchant.getMerchantCode())
        headParams.put("timestamp", System.currentTimeMillis().toString())
        headParams.put("nonce", UUID.randomUUID().toString())
        headParams.put("url", "/payfor/orderquery")

        String headStr = toAscii(headParams);
        String bodyStr = toAscii(bodyParams);
        headParams.put("sign", MD5.md5(headStr + bodyStr + merchant.getPrivateKey()).toUpperCase())


        log.info("LongTengScript_doTransferQuery_headParams:{}, bodyParams:{}", JSON.toJSONString(headParams), JSON.toJSONString(bodyParams))
        GlRequestHeader requestHeader =
                this.getRequestHeard("", "", orderId, GlActionEnum.WITHDRAW_QUERY.getCode(), merchant.getChannelId(), merchant.getChannelName())
        String resStr = okHttpUtil.post(merchant.getPayUrl() + "/payfor/orderquery",bodyParams , requestHeader, headParams)
        log.info("LongTengScript_doTransferQuery_resp:{}", resStr)
        if (StringUtils.isEmpty(resStr)) {
            return null
        }
        JSONObject json = JSON.parseObject(resStr)
        if (json == null || json.getString("code") != "1000") {
            return null
        }

        WithdrawNotify notify = new WithdrawNotify()
        notify.setMerchantCode(merchant.getMerchantCode())
        notify.setMerchantId(merchant.getMerchantId())
        notify.setMerchantName(merchant.getChannelName())
        notify.setOrderId(orderId)
        notify.setThirdOrderId(json.getString("transactionId"))
        // 支付状态:status  FAILURE代付失败   WAIT 等待付款   SUCCESS 代付成功  CLOSE失败关闭  ERROR 订单不存在
        if (json.getString("status") == ("SUCCESS")) {
            notify.setStatus(0)
        } else if (json.getString("status") == ("FAILURE") || json.getString("status") == "CLOSE") {
            notify.setStatus(1)
        } else {
            notify.setStatus(2)
        }
        return notify
    }

    public BigDecimal balanceQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount

        Map<String, String> bodyParams = new LinkedHashMap<>()

        Map<String, String> headParams = new LinkedHashMap<>()
        headParams.put("sid", merchantAccount.getMerchantCode())
        headParams.put("timestamp", System.currentTimeMillis().toString())//
        headParams.put("nonce", UUID.randomUUID().toString())//
        headParams.put("url", "/pay/balancequery")

        String headStr = toAscii(headParams);
        String bodyStr = toAscii(bodyParams);
        log.info("headStr:{}",headStr)
        log.info("bodyStr:{}",bodyStr)
        log.info("toSign:{}",headStr + bodyStr + merchantAccount.getPrivateKey())
        log.info("sign:{}", MD5.md5(headStr + bodyStr + merchantAccount.getPrivateKey()).toUpperCase())

        headParams.put("sign", MD5.md5(headStr + bodyStr + merchantAccount.getPrivateKey()).toUpperCase())


        log.info("LongTengScript_Query_Balance_Params: {}", JSON.toJSONString(headParams))
        GlRequestHeader requestHeader =
                getRequestHeard(null, null, null, GlActionEnum.PAYMENT_QUERY_BALANCE.getCode(),merchantAccount.getChannelId(),merchantAccount.getChannelName())
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/pay/balancequery", bodyParams,  requestHeader, headParams)
        log.info("LongTengScript_Query_Balance_resStr: {}", resStr)

        if (StringUtils.isEmpty(resStr)) {
            return BigDecimal.ZERO
        }
        JSONObject json = JSON.parseObject(resStr)
        if (json == null || "1000" != (json.getString("code"))) {
            return null
        }
        BigDecimal balance = json.getBigDecimal("balance")
        return balance == null ? BigDecimal.ZERO : balance
    }



    /**
     * 获取头部信息
     *
     * @param userId
     * @param userName
     * @param orderId
     * @return
     */
    private GlRequestHeader getRequestHeard(String userId, String userName, String orderId, String code, Integer channelId, String channelName) {
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