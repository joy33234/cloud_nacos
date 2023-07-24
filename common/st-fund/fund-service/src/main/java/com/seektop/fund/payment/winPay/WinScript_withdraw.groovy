package com.seektop.fund.payment.winPay

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

class WinScript_withdraw {

    private static final Logger log = LoggerFactory.getLogger(WinScript_withdraw.class)

    private OkHttpUtil okHttpUtil

    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness


    WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

        Map<String, String> paramMap = new HashMap<>()
        paramMap.put("method", "topay")
        paramMap.put("user_id", merchantAccount.getMerchantCode())
        paramMap.put("order_number", req.getOrderId())
        paramMap.put("payment_type", "uniondf")
        paramMap.put("card_number", req.getCardNo())
        paramMap.put("bank_user_name", req.getName())
        paramMap.put("notify_url", merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId())
        paramMap.put("order_amount", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString())

        String toSign = MD5.toAscii(paramMap) + "&key=" + merchantAccount.getPrivateKey()
        paramMap.put("sign", MD5.md5(toSign).toUpperCase())

        paramMap.put("client_ip", req.getIp())
        paramMap.put("bank_account", "上海支行")
        paramMap.put("bank_name", req.getBankName())
        log.info("WinScript_Transfer_Params: {}", JSON.toJSONString(paramMap))
        GlRequestHeader requestHeader =
                getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(),merchantAccount.getChannelId(),merchantAccount.getChannelName())
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/dfgateway", paramMap, 30L, requestHeader)
        log.info("WinScript_Transfer_resStr: {}", resStr)

        WithdrawResult result = new WithdrawResult()
        result.setOrderId(req.getOrderId())
        result.setReqData(JSON.toJSONString(paramMap))
        result.setResData(resStr)

        if (StringUtils.isEmpty(resStr)) {
            result.setValid(false)
            result.setMessage("API异常:请联系出款商户确认订单.")
            return result
        }

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || "1" != json.getString("code")) {
            result.setValid(false)
            result.setMessage(json == null ? "API异常:请联系出款商户确认订单." : json.getString("message"))
            return result
        }
        JSONObject dataJSON = json.getJSONObject("data")
        if (dataJSON == null) {
            result.setValid(false)
            result.setMessage(json.getString("message"))
            return result
        }
        result.setValid(true)
        result.setMessage(json.getString("message"))
        result.setThirdOrderId(json.getString("transaction_id"))
        return result
    }

    WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        Map<String, String> resMap = args[2] as Map<String, String>
        log.info("WinScript_Transfer_Notify_resMap:{}", JSON.toJSONString(resMap))
        JSONObject json = JSON.parseObject(resMap.get("reqBody"))
        String orderid = json.getString("order_number")// 商户订单号
        String thirdOrderId = json.getString("trade_no")
        if (StringUtils.isNotEmpty(orderid)) {
            return withdrawQuery(okHttpUtil, merchant, orderid,thirdOrderId, args[3])
        }
        return null
    }

    WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2]
        String thirdOrderId = args[3]
        String keyValue = merchant.getPrivateKey() // 商家密钥
        Map<String, String> paramMap = new HashMap<>()
        paramMap.put("user_id", merchant.getMerchantCode())
        paramMap.put("method", "orderQuery")
        paramMap.put("trade_no", thirdOrderId)

        String signInfo = MD5.toAscii(paramMap) + "&key=" + keyValue
        paramMap.put("sign", MD5.md5(signInfo).toUpperCase())

        log.info("WinScript_Transfer_Query_Params: {}", JSON.toJSONString(paramMap))
        GlRequestHeader requestHeader =
                getRequestHeard(null, null, orderId, GlActionEnum.WITHDRAW_QUERY.getCode(),merchant.getChannelId(),merchant.getChannelName())
        String resStr = okHttpUtil.post(merchant.getPayUrl() + "/dfgateway", paramMap, 30L, requestHeader)
        log.info("WinScript_Transfer_Query_resStr: {}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || json.getString("code") != "1") {
            return null
        }
        JSONObject dataJSON = json.getJSONObject("data")
        if (dataJSON == null) {
            return  null
        }
        //描述SUCCESS成功WAIT待支付FAIL订单过期
        String status = dataJSON.getString("trade_status")
        WithdrawNotify notify = new WithdrawNotify()
        notify.setMerchantCode(merchant.getMerchantCode())
        notify.setMerchantId(merchant.getMerchantId())
        notify.setMerchantName(merchant.getChannelName())
        notify.setOrderId(orderId)
        notify.setRemark("")
        if (status == "SUCCESS") {
            notify.setStatus(0)
        } else if (status == "FAIL" ||  status == "BOHUI") {
            notify.setStatus(1)
        } else {
            notify.setStatus(2)
        }
        notify.setThirdOrderId(thirdOrderId)
        return notify
    }


    BigDecimal balanceQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount

        Map<String, String> paramMap = new HashMap<>()
        paramMap.put("user_id", merchantAccount.getMerchantCode())
        paramMap.put("method", "enquiryBlance")

        String toSign = MD5.toAscii(paramMap) + "&key=" + merchantAccount.getPrivateKey()
        paramMap.put("sign", MD5.md5(toSign.toString()).toUpperCase())

        log.info("WinScript_Query_Balance_Params: {}", JSON.toJSONString(paramMap))
        GlRequestHeader requestHeader =
                getRequestHeard(null, null, null, GlActionEnum.PAYMENT_QUERY_BALANCE.getCode(),merchantAccount.getChannelId(),merchantAccount.getChannelName())
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/dfgateway", paramMap, 30L, requestHeader)
        log.info("WinScript_Query_Balance_resStr: {}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || json.getString("code") != "1") {
            return BigDecimal.ZERO
        }
        JSONObject dataJSON = json.getJSONObject("data")
        if (dataJSON == null) {
            return BigDecimal.ZERO
        }
        BigDecimal balance = dataJSON.getBigDecimal("available_balance")
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