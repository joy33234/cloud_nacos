package com.seektop.fund.payment.HaiChuanKejiPay

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

public class HaiChuanKeJiScript_withdraw {

    private static final String SERVER_DF_PAY_URL = "/pay/gateway/withdraw.do"

    private static final String SERVER_DF_QUERY_URL = "/pay/gateway/withdrawQuery.do"

    private static final String SERVER_QUERY_URL = "/pay/gateway/query.do"


    private static final Logger log = LoggerFactory.getLogger(HaiChuanKeJiScript_withdraw.class)

    private OkHttpUtil okHttpUtil

    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness


    public WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

        WithdrawResult result = new WithdrawResult()
        String[] arr = merchantAccount.getMerchantCode().split("\\|\\|")
        if (arr == null || arr.length != 2) {
            result.setOrderId(req.getOrderId())
            result.setReqData(req.getOrderId())
            result.setResData("商户配置错误")
            result.setValid(false)
            result.setMessage("商户配置错误")
            return result
        }

        Map<String, String> params = new HashMap<>()
        params.put("TRDE_CODE", "20003")
        params.put("PRT_CODE", arr[1])
        params.put("VER_NO", "1.0")
        params.put("MERC_ID", arr[0])
        params.put("OUT_WITHDRAW_NO", req.getOrderId())
        params.put("WITHDRAW_MONEY", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString())
        params.put("PERSON_NM", req.getName())
        params.put("CARD_NO", req.getCardNo())
        params.put("BNK_NM", req.getBankName())
        params.put("BNK_NO", glPaymentChannelBankBusiness.getBankCode(req.getBankId(), merchantAccount.getChannelId()))
        params.put("BNK_CD", "0000")
        params.put("CRP_ID_NO", "120103200101017417")
        params.put("PHONE_NO", "13611111148")
        params.put("PAY_ID", "D0")
        params.put("NON_STR", System.currentTimeMillis() + "")
        params.put("TM_SMP", System.currentTimeMillis() + "")
        params.put("NOTIFY_URL", merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId())
        params.put("SIGN_TYP", "MD5")
        String toSign = MD5.toAscii(params)
        toSign += "&KEY=" + merchantAccount.getPrivateKey()
        params.put("SIGN_DAT", MD5.md5(toSign))
        log.info("HaiChuanKejiScript_doTransfer_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.WITHDRAW.getCode())
                .channelId(merchantAccount.getChannelId() + "")
                .channelName(merchantAccount.getChannelName())
                .userId(req.getUserId() + "")
                .userName(req.getUsername())
                .tradeId(req.getOrderId())
                .build()
        String resStr = okHttpUtil.postJSON(merchantAccount.getPayUrl() + SERVER_DF_PAY_URL, JSON.toJSONString(params), requestHeader)
        log.info("HaiChuanKejiScript_doTransfer_resp:{}", resStr)
        JSONObject json = JSON.parseObject(resStr)
        result.setOrderId(req.getOrderId())
        result.setReqData(JSON.toJSONString(params))
        result.setResData(resStr)
        if (StringUtils.isEmpty(resStr)) {
            result.setValid(false)
            result.setMessage("API异常:请联系出款商户确认订单.")
            return result
        }

        if ("0000" != (json.getString("RETURNCODE"))) {
            result.setValid(false)
            result.setMessage(json.getString("RETURNCON"))
            return result
        }
        req.setMerchantId(merchantAccount.getMerchantId())
        result.setValid(true)
        result.setMessage(json.getString("RETURNCON"))
        return result
    }

    public WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        Map<String, String> resMap = args[2] as Map<String, String>
        log.info("HaiChuanKejiScript_doTransferNotify_resp:{}", JSON.toJSONString(resMap))
        JSONObject json = JSONObject.parseObject(resMap.get("reqBody"))
        String orderId
        if (json == null) {
            orderId = resMap.get("TAMT_ORD_NO")
        } else {
            orderId = json.getString("TAMT_ORD_NO")
        }
        if (StringUtils.isNotEmpty(orderId)) {
            return this.withdrawQuery(okHttpUtil, merchant, orderId, args[3])
        }
        return null
    }

    public WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2]

        String[] arr = merchant.getMerchantCode().split("\\|\\|")
        if (arr == null || arr.length != 2) {
            return null
        }
        Map<String, String> params = new HashMap<>()
        params.put("TRDE_CODE", "20004")
        params.put("PRT_CODE", arr[1])
        params.put("VER_NO", "1.0")
        params.put("MERC_ID", arr[0])
        params.put("OUT_WITHDRAW_NO", orderId)
        params.put("NON_STR", System.currentTimeMillis() + "")
        params.put("TM_SMP", System.currentTimeMillis() + "")
        params.put("SIGN_TYP", "MD5")
        String toSign = MD5.toAscii(params)
        toSign += "&KEY=" + merchant.getPrivateKey()
        params.put("SIGN_DAT", MD5.md5(toSign))
        log.info("HaiChuanKejiScript_doTransferQuery_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.WITHDRAW_QUERY.getCode())
                .channelId(merchant.getChannelId() + "")
                .channelName(merchant.getChannelName())
                .userId("")
                .userName("")
                .tradeId(orderId)
                .build()

        String resStr = okHttpUtil.postJSON(merchant.getPayUrl() + SERVER_DF_QUERY_URL, JSONObject.toJSONString(params), requestHeader)
        log.info("HaiChuanKejiScript_doTransferQuery_resp:{}", resStr)
        if (StringUtils.isEmpty(resStr)) {
            return null
        }
        JSONObject json = JSON.parseObject(resStr)
        if ("0000" != (json.getString("RETURNCODE"))) {
            return null
        }
        WithdrawNotify notify = new WithdrawNotify()
        notify.setAmount(json.getBigDecimal("WITHDRAW_MONEY"))
        notify.setMerchantCode(merchant.getMerchantCode())
        notify.setMerchantId(merchant.getMerchantId())
        notify.setMerchantName(merchant.getChannelName())
        notify.setOrderId(json.getString("OUT_WITHDRAW_NO"))
        notify.setThirdOrderId("")
        //00-成功  10-预代付 70-失败
        if (json.getString("BUS_STS") == ("00")) {
            notify.setStatus(0)
            notify.setRsp("SUCCESS")
        } else if (json.getString("BUS_STS") == ("70")) {
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
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[2], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

        String[] arr = merchantAccount.getMerchantCode().split("\\|\\|")
        if (arr == null || arr.length != 2) {
            return null
        }
        Map<String, String> params = new HashMap<>()
        params.put("TRDE_CODE", "20005")
        params.put("PRT_CODE", arr[1])
        params.put("VER_NO", "1.0")
        params.put("MERC_ID", arr[0])
        params.put("NON_STR", System.currentTimeMillis() + "")
        params.put("TM_SMP", System.currentTimeMillis() + "")
        params.put("SIGN_TYP", "MD5")
        String toSign = MD5.toAscii(params)
        toSign += "&KEY=" + merchantAccount.getPrivateKey()
        params.put("SIGN_DAT", MD5.md5(toSign))
        log.info("HaiChuanKejiScript_queryBalance_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.PAYMENT_QUERY_BALANCE.getCode())
                .channelId(merchantAccount.getChannelId() + "")
                .channelName(merchantAccount.getChannelName())
                .userId("")
                .userName("")
                .tradeId("")
                .build()
        String resStr = okHttpUtil.postJSON(merchantAccount.getPayUrl() + SERVER_QUERY_URL, JSONObject.toJSONString(params), requestHeader)
        log.info("HaiChuanKejiScript_queryBalance_resp:{}", resStr)
        JSONObject json = JSONObject.parseObject(resStr)
        if ("0000" == (json.getString("RETURNCODE"))) {
            return json.getBigDecimal("D_AMT")
        }
        return BigDecimal.ZERO
    }
}