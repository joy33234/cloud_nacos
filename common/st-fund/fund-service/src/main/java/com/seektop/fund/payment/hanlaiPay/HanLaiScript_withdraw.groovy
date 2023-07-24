package com.seektop.fund.payment.hanlaiPay

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
 * 汉来支付
 * @auth  joy
 * @date 20202-12-29
 */

class HanLaiScript_withdraw {

    private static final Logger log = LoggerFactory.getLogger(HanLaiScript_withdraw.class)

    private OkHttpUtil okHttpUtil

    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness


    WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

        Map<String, String> paramMap = new HashMap<>()
        paramMap.put("version", "1.0")
        paramMap.put("cid", merchantAccount.getMerchantCode())
        paramMap.put("tradeNo", req.getOrderId())
        paramMap.put("amount", req.getAmount().subtract(req.getFee()).multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.DOWN).toString())
        paramMap.put("payType", "1")
        paramMap.put("acctName", req.getName())
        paramMap.put("acctNo", req.getCardNo())
        paramMap.put("bankCode", glPaymentChannelBankBusiness.getBankCode(req.getBankId(), merchantAccount.getChannelId()));
        paramMap.put("notifyUrl", merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId())

        String toSign = MD5.toAscii(paramMap) + "&key=" + merchantAccount.getPrivateKey()
        paramMap.put("sign", MD5.md5(toSign))

        log.info("HanLaiScript_Transfer_Params: {}", JSON.toJSONString(paramMap))
        GlRequestHeader requestHeader =
                getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(),merchantAccount.getChannelId(),merchantAccount.getChannelName())
        String resStr = okHttpUtil.postJSON(merchantAccount.getPayUrl() + "/louis/ap.do", JSON.toJSONString(paramMap),  requestHeader)
        log.info("HanLaiScript_Transfer_resStr: {}", resStr)

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

        if ("0" != json.getString("retcode")) {
            result.setValid(false)
            result.setMessage(json.getString("retmsg"))
            return result
        }
        result.setValid(true)
        result.setMessage(json.getString("retmsg"))
        result.setThirdOrderId(json.getString("rockTradeNo"))
        return result
    }

    WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        Map<String, String> resMap = args[2] as Map<String, String>
        log.info("HanLaiScript_Transfer_Notify_resMap:{}", JSON.toJSONString(resMap))
        JSONObject json = JSON.parseObject(resMap.get("reqBody"));
        String orderid = json.getString("tradeNo")
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
        paramMap.put("cid", merchant.getMerchantCode())
        paramMap.put("tradeNo", orderId)
        paramMap.put("type", "001")

        String signInfo = MD5.toAscii(paramMap) + "&key=" + keyValue
        paramMap.put("sign", MD5.md5(signInfo).toUpperCase())

        log.info("HanLaiScript_Transfer_Query_Params: {}", JSON.toJSONString(paramMap))
        GlRequestHeader requestHeader =
                getRequestHeard(null, null, orderId, GlActionEnum.WITHDRAW_QUERY.getCode(),merchant.getChannelId(),merchant.getChannelName())
        String resStr = okHttpUtil.post(merchant.getPayUrl() + "/louis/query.do", paramMap, 10L, requestHeader)
        log.info("HanLaiScript_Transfer_Query_resStr: {}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null ||  json.getString("retcode") != "0") {
            return null
        }
        //1：代付成功；  2：处理中；  3：失败
        Integer status = json.getInteger("status")
        WithdrawNotify notify = new WithdrawNotify()
        notify.setAmount(json.getBigDecimal("amount").divide(BigDecimal.valueOf(10)).setScale(2,RoundingMode.DOWN))
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
        notify.setThirdOrderId(json.getString("rockTradeNo"))
        return notify
    }


    BigDecimal balanceQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount

        Map<String, String> paramMap = new HashMap<>()
        paramMap.put("cid", merchantAccount.getMerchantCode())

        String toSign = MD5.toAscii(paramMap) + "&key=" + merchantAccount.getPrivateKey()
        paramMap.put("sign", MD5.md5(toSign.toString()).toUpperCase())

        log.info("HanLaiScript_Query_Balance_Params: {}", JSON.toJSONString(paramMap))
        GlRequestHeader requestHeader =
                getRequestHeard(null, null, null, GlActionEnum.PAYMENT_QUERY_BALANCE.getCode(),merchantAccount.getChannelId(),merchantAccount.getChannelName())
        String resStr = okHttpUtil.postJSON(merchantAccount.getPayUrl() + "/louis/spQuery.do", JSON.toJSONString(paramMap),  requestHeader)
        log.info("HanLaiScript_Query_Balance_resStr: {}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || json.getString("retcode") != "0") {
            return BigDecimal.ZERO
        }
        BigDecimal balance = json.getBigDecimal("balancePay")
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