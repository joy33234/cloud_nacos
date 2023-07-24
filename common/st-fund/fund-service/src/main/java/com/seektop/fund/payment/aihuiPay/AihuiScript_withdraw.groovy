package com.seektop.fund.payment.aihuiPay

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
 * 爱汇支付
 * @auth  joy
 * @date 2021-07-29
 */

class AihuiScript_withdraw {

    private static final Logger log = LoggerFactory.getLogger(AihuiScript_withdraw.class)

    private OkHttpUtil okHttpUtil

    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness


    WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

        Map<String, String> paramMap = new HashMap<>()
        paramMap.put("Client", merchantAccount.getMerchantCode())
        paramMap.put("ReferenceID", req.getOrderId())
        paramMap.put("BankID", glPaymentChannelBankBusiness.getBankCode(req.getBankId(), merchantAccount.getChannelId()));
        paramMap.put("CurrencyCode", "RMB")
        paramMap.put("Amount", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString())
        paramMap.put("NotificationUrl", merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId())

        paramMap.put("City", "上海市")
        paramMap.put("Province", "上海市")
        paramMap.put("BankAccountBranch", "上海支行")

        paramMap.put("BankAccountName", req.getName())
        paramMap.put("BankAccountNumber", req.getCardNo())

        String toSign = MD5.toAscii(paramMap) + "&" + merchantAccount.getPrivateKey()
        paramMap.put("Sign", MD5.md5(toSign))

        log.info("AiHuiScript_Transfer_Params: {}", JSON.toJSONString(paramMap))
        GlRequestHeader requestHeader =
                getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(),merchantAccount.getChannelId(),merchantAccount.getChannelName())
        String resStr = okHttpUtil.postJSON(merchantAccount.getPayUrl() + "/api/payout", JSON.toJSONString(paramMap),  requestHeader)
        log.info("AiHuiScript_Transfer_resStr: {}", resStr)

        WithdrawResult result = new WithdrawResult()
        result.setOrderId(req.getOrderId())
        result.setReqData(JSON.toJSONString(paramMap))
        result.setResData(resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null) {
            result.setValid(false)
            result.setMessage("API异常:请联系出款商户确认订单.")
            return result
        }
        if ("1" != json.getString("Status")) {
            result.setValid(false)
            result.setMessage(json.getString("Message"))
            return result
        }
        result.setValid(true)
        result.setMessage(json.getString("Message"))
        result.setThirdOrderId(json.getString("TransactionID"))
        return result
    }

    WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        Map<String, String> resMap = args[2] as Map<String, String>
        log.info("AiHuiScript_Transfer_Notify_resMap:{}", JSON.toJSONString(resMap))
        JSONObject json = JSON.parseObject(resMap.get("reqBody"));
        String orderid = json.getString("ReferenceID")
        if (StringUtils.isNotEmpty(orderid)) {
            return withdrawQuery(okHttpUtil, merchant, orderid, args[3])
        }
        return null
    }

    WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2]
        String keyValue = merchant.getPrivateKey() // 商家密钥
        Map<String, String> paramMap = new HashMap<>()
        paramMap.put("Client", merchant.getMerchantCode())
        paramMap.put("ReferenceID", orderId)

        String signInfo = MD5.toAscii(paramMap) + "&" + keyValue
        paramMap.put("Sign", MD5.md5(signInfo).toUpperCase())

        log.info("AiHuiScript_Transfer_Query_Params: {}", JSON.toJSONString(paramMap))
        GlRequestHeader requestHeader =
                getRequestHeard(null, null, orderId, GlActionEnum.WITHDRAW_QUERY.getCode(),merchant.getChannelId(),merchant.getChannelName())
        String resStr = okHttpUtil.post(merchant.getPayUrl() + "/api/payoutstatus", paramMap, 30L, requestHeader)
        log.info("AiHuiScript_Transfer_Query_resStr: {}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null ) {
            return null
        }
        //1：代付成功；  2：处理中；  3：失败
        Integer status = json.getInteger("Status")
        WithdrawNotify notify = new WithdrawNotify()
        notify.setMerchantCode(merchant.getMerchantCode())
        notify.setMerchantId(merchant.getMerchantId())
        notify.setMerchantName(merchant.getChannelName())
        notify.setOrderId(orderId)
        notify.setRemark(json.getString("refMsg"))
        if (status == 1) {
            notify.setStatus(0)
            notify.setRsp("SUCCESS")
        } else if (status == 3) {
            notify.setStatus(1)
            notify.setRsp("SUCCESS")
        } else {
            notify.setStatus(2)
        }
        return notify
    }


    BigDecimal balanceQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount

        Map<String, String> paramMap = new HashMap<>()
        paramMap.put("Client", merchantAccount.getMerchantCode())

        String toSign = MD5.toAscii(paramMap) + "&" + merchantAccount.getPrivateKey()
        paramMap.put("Sign", MD5.md5(toSign.toString()).toUpperCase())
        paramMap.put("CurrencyCode", "RMB")


        log.info("AiHuiScript_Query_Balance_Params: {}", JSON.toJSONString(paramMap))
        GlRequestHeader requestHeader =
                getRequestHeard(null, null, null, GlActionEnum.PAYMENT_QUERY_BALANCE.getCode(),merchantAccount.getChannelId(),merchantAccount.getChannelName())
        String resStr = okHttpUtil.postJSON(merchantAccount.getPayUrl() + "/api/getbalance", JSON.toJSONString(paramMap),  requestHeader)
        log.info("AiHuiScript_Query_Balance_resStr: {}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || json.getString("Status") != "1") {
            return BigDecimal.ZERO
        }
        BigDecimal balance = json.getBigDecimal("Balance")
        return balance == null ? BigDecimal.ZERO : balance
    }


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