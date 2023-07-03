package com.seektop.fund.payment.giglobalpay

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
import com.seektop.fund.payment.groovy.ResourceEnum
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode

/**
 * @desc  gi-global支付
 * @date 2021-03-30
 * @auth joy
 */
public class GiGlobalScript_withdraw {


    private static final Logger log = LoggerFactory.getLogger(GiGlobalScript_withdraw.class)

    private OkHttpUtil okHttpUtil

    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness


    public WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount account = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        this.glPaymentChannelBankBusiness = com.seektop.fund.payment.groovy.BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

        Map<String, String> params = new LinkedHashMap<>()
        params.put("version", "1.0")
        params.put("custid", account.getMerchantCode())
        params.put("ordercode", req.getOrderId())
        params.put("amount", req.getAmount().subtract(req.getFee()).multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.DOWN).toString())
        params.put("cardno", req.getCardNo())
        params.put("accountname", req.getName())
        String toSign = MD5.toSign(params) + "&key=" + account.getPrivateKey()
        params.put("sign", MD5.md5(toSign).toLowerCase())
        
        params.put("backurl", account.getNotifyUrl() + account.getMerchantId())
        params.put("bankname", glPaymentChannelBankBusiness.getBankName(req.getBankId(), account.getChannelId()))

        log.info("GiGlobalScript_doTransfer_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                this.getRequestHeard(req.getUserId() + "", req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(), account.getChannelId(), account.getChannelName())

        String resStr = okHttpUtil.postJSON(account.getPayUrl() + "/cashier/initpay.aspx", JSON.toJSONString(params), requestHeader)
        log.info("GiGlobalScript_doTransfer_resp:{}", resStr)
        JSONObject json = JSON.parseObject(resStr)

        WithdrawResult result = new WithdrawResult()
        result.setOrderId(req.getOrderId())
        result.setReqData(JSON.toJSONString(params))
        result.setResData(resStr)
        if (StringUtils.isEmpty(resStr)) {
            result.setValid(false)
            result.setMessage("API异常:请联系出款商户确认订单.")
            return result
        }

        if ("SUCCESS" != (json.getString("code"))) {
            result.setValid(false)
            result.setMessage(json.getString("MSG"))
            return result
        }
        req.setMerchantId(account.getMerchantId())
        result.setValid(true)
        result.setMessage(json.getString("MSG"))
        return result
    }

    public WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        Map<String, String> resMap = args[2] as Map<String, String>
        log.info("GiGlobalScript_doTransferNotify_resp:{}", JSON.toJSONString(resMap))
        JSONObject json = JSONObject.parseObject(resMap.get("reqBody"))
        String orderId = json.getString("ordercode")
        if (StringUtils.isNotEmpty(orderId)) {
            return this.withdrawQuery(okHttpUtil, merchant, orderId, args[3])
        }
        return null
    }

    public WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2]

        Map<String, String> params = new LinkedHashMap<>()
        params.put("version", "1.0")
        params.put("custid", merchant.getMerchantCode())
        params.put("ordercode", orderId)
        String toSign = MD5.toSign(params) + "&key=" + merchant.getPrivateKey()
        params.put("sign", MD5.md5(toSign).toLowerCase())

        log.info("GiGlobalScript_doTransferQuery_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                this.getRequestHeard("", "", orderId, GlActionEnum.WITHDRAW_QUERY.getCode(), merchant.getChannelId(), merchant.getChannelName())
        String resStr = okHttpUtil.postJSON(merchant.getPayUrl() + "/cashier/queryPay.aspx", JSONObject.toJSONString(params), requestHeader)
        log.info("GiGlobalScript_doTransferQuery_resp:{}", resStr)
        if (StringUtils.isEmpty(resStr)) {
            return null
        }
        JSONObject json = JSON.parseObject(resStr)
        if ("SUCCESS" != (json.getString("code"))) {
            return null
        }
        WithdrawNotify notify = new WithdrawNotify()
        notify.setAmount(json.getBigDecimal("amount").divide(BigDecimal.valueOf(100)))
        notify.setMerchantCode(merchant.getMerchantCode())
        notify.setMerchantId(merchant.getMerchantId())
        notify.setMerchantName(merchant.getChannelName())
        notify.setOrderId(json.getString("ordercode"))
        notify.setThirdOrderId("")
        //订单状态 1:未处理 2:已完成 6: 关闭，3:回调中 4 回调挂起
        if (json.getString("status") == ("2") || json.getString("status") == ("3")
            || json.getString("status") == ("4")) {
            notify.setStatus(0)
            notify.setRsp("SUCCESS")
        } else if (json.getString("status") == ("5") || json.getString("status") == ("6")) {
            notify.setStatus(1)
            notify.setRsp("SUCCESS")
        } else {
            notify.setStatus(2)
        }
        return notify
    }

    public BigDecimal balanceQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount

        Map<String, String> params = new LinkedHashMap<>()
        params.put("version", "1.0")
        params.put("custid", merchantAccount.getMerchantCode())
        String toSign = MD5.toSign(params) + "&key=" + merchantAccount.getPrivateKey()
        params.put("sign", MD5.md5(toSign).toLowerCase())
        log.info("GiGlobalScript_queryBalance_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                getRequestHeard("", "", "", GlActionEnum.PAYMENT_QUERY_BALANCE.getCode(), merchantAccount.getChannelId(), merchantAccount.getChannelName())

        String resStr = okHttpUtil.postJSON(merchantAccount.getPayUrl() + "/balance/query.aspx", JSONObject.toJSONString(params), requestHeader)
        log.info("GiGlobalScript_queryBalance_resp:{}", resStr)
        JSONObject json = JSONObject.parseObject(resStr)
        if ("SUCCESS" == (json.getString("code"))) {
            return json.getBigDecimal("balance").divide(BigDecimal.valueOf(100))
        }
        return BigDecimal.ZERO
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
}