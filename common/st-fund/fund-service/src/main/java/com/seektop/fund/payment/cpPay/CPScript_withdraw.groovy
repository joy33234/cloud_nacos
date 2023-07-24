package com.seektop.fund.payment.cpPay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONArray
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
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode

/**
 * @desc CP代付
 * @date 2021-09-23
 * @auth Otto
 */
class CPScript_withdraw {

    private static final Logger log = LoggerFactory.getLogger(CPScript_withdraw.class)

    private static final String SERVER_WITHDRAW_URL = "/v4/merchant/withdraw"
    private static final String SERVER_QUERY_URL = "/v4/merchant/withdraw/query"
    private static final String SERVER_BALANCE_URL = "/v4/merchant/balance"
    private OkHttpUtil okHttpUtil

    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness

    WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

        Map<String, String> DataContentParms = new HashMap<>()
        DataContentParms.put("accountnumber", req.getCardNo())
        DataContentParms.put("amount", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString())
        DataContentParms.put("bank", glPaymentChannelBankBusiness.getBankName(req.getBankId(), merchantAccount.getChannelId()))
        DataContentParms.put("branch", "any")
        DataContentParms.put("callback_url", merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId())
        DataContentParms.put("merchant_code", merchantAccount.getMerchantCode())
        DataContentParms.put("name", req.getName())
        DataContentParms.put("order_id", req.getOrderId())
        DataContentParms.put("timestamp", System.currentTimeSeconds()+"")

        String toSign = MD5.toAscii(DataContentParms) +"&"+ merchantAccount.getPrivateKey()
        DataContentParms.put("sign", MD5.md5(toSign))

        log.info("CPScript_Transfer_params: {}", DataContentParms)
        GlRequestHeader requestHeader = getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(), merchantAccount.getChannelId(), merchantAccount.getChannelName())

        String resStr = okHttpUtil.postJSON(merchantAccount.getPayUrl() + SERVER_WITHDRAW_URL, JSONObject.toJSONString(DataContentParms), requestHeader)
        log.info("CPScript_Transfer_resStr: {}", resStr)

        WithdrawResult result = new WithdrawResult()
        result.setOrderId(req.getOrderId())
        result.setReqData(JSON.toJSONString(DataContentParms))
        result.setResData(resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || "true" != json.getString("status")) {
            result.setValid(false)
            result.setMessage(json == null ? "API异常:请联系出款商户确认订单." : json.getString("message"))
            return result
        }
        result.setValid(true)
        result.setMessage("")
        result.setThirdOrderId(json.getString("withdawal_id"))
        return result
    }


    WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        Map<String, String> resMap = args[2] as Map<String, String>
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

        log.info("CPScript_WithdrawNotify_resMap:{}", JSON.toJSONString(resMap))
        JSONObject json = JSONObject.parseObject(resMap.get("reqBody"))
        String orderid = json.getString("order_id")
        String thirdOrderId = json.getString("withdrawal_id")
        if (org.apache.commons.lang.StringUtils.isNotEmpty(orderid)) {
            return withdrawQuery(okHttpUtil, merchant, orderid, thirdOrderId)

        } else {
            return null

        }
    }

    WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2] as String
        String thirdOrderId = args[3] as String

        Map<String, String> DataContentParms = new HashMap<String, String>()
        DataContentParms.put("merchant_code", merchant.getMerchantCode())
        DataContentParms.put("order_id", orderId)
        DataContentParms.put("withdrawal_id", thirdOrderId)
        String toSign = MD5.toAscii(DataContentParms) + "&" +merchant.getPrivateKey()
        DataContentParms.put("sign", MD5.md5(toSign))

        log.info("CPScript_TransferQuery_reqMap:{}", JSON.toJSONString(DataContentParms))
        GlRequestHeader requestHeader = getRequestHeard(null, null, orderId, GlActionEnum.WITHDRAW_QUERY.getCode(), merchant.getChannelId(), merchant.getChannelName())

        String resStr = okHttpUtil.postJSON(merchant.getPayUrl() + SERVER_QUERY_URL, JSONObject.toJSONString(DataContentParms) ,  requestHeader)
        log.info("CPScript_TransferQuery_resStr:{}", resStr)
        JSONObject json = JSON.parseObject(resStr)
        if (json == null) {
            return null
        }
        WithdrawNotify notify = new WithdrawNotify()

        //REJECTED 已拒绝/APPROVED 已经被批准/PENDING 等待处理/DISPENSED 已出款
        if (json.getString("status") == "true") {
            JSONArray dataArray =json.getJSONArray("data")
            String orderFinalStatus = dataArray.get(0).getString("status")
            notify.setMerchantCode(merchant.getMerchantCode())
            notify.setMerchantId(merchant.getMerchantId())
            notify.setMerchantName(merchant.getChannelName())
            notify.setOrderId(orderId)
            notify.setThirdOrderId(json.getString("withdrawal_id"))

            if (orderFinalStatus == "APPROVED" || orderFinalStatus == "DISPENSED" ) {
                notify.setStatus(0)
                notify.setRsp("ok")

            } else if (orderFinalStatus== "REJECTED" ) {
                notify.setStatus(1)
                notify.setRsp("ok")

            } else {
                notify.setStatus(2)
            }
        }
        return notify
    }

    BigDecimal balanceQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[2], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

        Map<String, String> DataContentParms = new HashMap<>()
        DataContentParms.put("merchant_code", merchantAccount.getMerchantCode())
        DataContentParms.put("time",System.currentTimeSeconds()+"")

        String toSign = MD5.toAscii(DataContentParms) + "&"+merchantAccount.getPrivateKey()
        DataContentParms.put("sign", MD5.md5(toSign))

        log.info("CPScript_QueryBalance_reqMap: {}", JSON.toJSONString(DataContentParms))
        GlRequestHeader requestHeader =
                getRequestHeard("", "", "", GlActionEnum.PAYMENT_QUERY_BALANCE.getCode(), merchantAccount.getChannelId(), merchantAccount.getChannelName())

        String resStr = okHttpUtil.postJSON(merchantAccount.getPayUrl() + SERVER_BALANCE_URL, JSONObject.toJSONString(DataContentParms) , requestHeader)
        log.info("CPScript_QueryBalance_resStr: {}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || json.getString("status") != "true") {
            return BigDecimal.ZERO
        }

        JSONArray jsonArray = json.getJSONArray("data")
        BigDecimal balance = jsonArray.getJSONObject(0).getBigDecimal("balance")
        return balance == null ? BigDecimal.ZERO : balance
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