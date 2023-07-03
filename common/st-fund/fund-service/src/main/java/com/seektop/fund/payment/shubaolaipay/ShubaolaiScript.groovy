package com.seektop.fund.payment.shubaolaipay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
import com.seektop.common.utils.MD5
import com.seektop.constant.FundConstant
import com.seektop.enumerate.GlActionEnum
import com.seektop.exception.GlobalException
import com.seektop.fund.business.GlPaymentChannelBankBusiness
import com.seektop.fund.model.GlPaymentMerchantApp
import com.seektop.fund.model.GlPaymentMerchantaccount
import com.seektop.fund.payment.*
import com.seektop.fund.payment.groovy.ResourceEnum
import org.apache.commons.lang.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode

public class ShubaolaiScript {

    private static final Logger log = LoggerFactory.getLogger(ShubaolaiScript.class)

    private OkHttpUtil okHttpUtil

    private GlPaymentChannelBankBusiness channelBankBusiness

    public RechargeNotify result(Object[] args) throws GlobalException {
        return notify(args)
    }


    WithdrawResult withdraw(Object[] args) throws GlobalException {
        return null
    }


    WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        return null
    }


    WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        return null
    }


    BigDecimal balanceQuery(Object[] args) throws GlobalException {
        return null
    }

    public void pay(Object[] args) {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantApp merchant = args[1] as GlPaymentMerchantApp
        GlPaymentMerchantaccount account = args[2] as GlPaymentMerchantaccount
        RechargePrepareDO req = args[3] as RechargePrepareDO
        GlRechargeResult result = args[4] as GlRechargeResult
        this.channelBankBusiness = com.seektop.fund.payment.groovy.BaseScript.getResource(args[5], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        if (FundConstant.PaymentType.ALI_PAY == merchant.getPaymentId()) {
            prepareToScan(merchant, account, req, result, "ALIPAY_QRCODE")
        } else if (FundConstant.PaymentType.WECHAT_PAY == merchant.getPaymentId()) {
            prepareToScan(merchant, account, req, result, "WECHAT_QRCODE")
        } else if (FundConstant.PaymentType.ONLINE_PAY == merchant.getPaymentId()) {
            prepareToScan(merchant, account, req, result, "BANK_QRCODE")
        } else if (FundConstant.PaymentType.UNION_PAY == merchant.getPaymentId()) {
            prepareToScan(merchant, account, req, result, "WEB_BANK")
        } else if (FundConstant.PaymentType.QUICK_ALI_PAY == merchant.getPaymentId()) {
            prepareToScan(merchant, account, req, result, "ALIPAY_H5_B")
        } else if (FundConstant.PaymentType.BANKCARD_TRANSFER == merchant.getPaymentId()) {
            prepareToScan(merchant, account, req, result, "WEB_BANK")
        }
    }

    private void prepareToScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result, String channelType) throws GlobalException {
        Map<String, String> params = new HashMap<>()
        params.put("MerchantTicketId", req.getOrderId())
        params.put("Account", account.getMerchantCode())
        params.put("ChannelType", channelType)
        params.put("Amount", req.getAmount().toString())
        params.put("MemberCode", "obama")
        params.put("AccessIP", "0.0.0.0")
        params.put("NotifyUrl", account.getNotifyUrl() + merchant.getId())
        params.put("ResType", "json")
        String toSign = MD5.toAscii(params)
        toSign += "&key=" + account.getPrivateKey()
        params.put("Sign", MD5.md5(toSign).toUpperCase())
        if (StringUtils.equals("BANK_QRCODE", channelType)) {
            params.put("BankCode", channelBankBusiness.getBankCode(req.getBankId(), account.getChannelId()))
            params.put("ResolvedUrl", "https://www.ballbet5.com/")
        }
        log.info("ShubaolaiScript_recharge_prepare_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.RECHARGE.getCode())
                .channelId(PaymentMerchantEnum.SHUBAOLAI_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.SHUBAOLAI_PAY.getPaymentName())
                .userId(req.getUserId() + "")
                .userName(req.getUsername())
                .tradeId(req.getOrderId())
                .build()
        String resStr = okHttpUtil.postJSON(account.getPayUrl() + "/Merchant/Channel?Alg=MD5", JSONObject.toJSONString(params), requestHeader)
        log.info("ShubaolaiScript_recharge_prepare_resp:{}", resStr)
        if (StringUtils.isEmpty(resStr)) {
            result.setErrorCode(1)
            result.setErrorMsg("创建订单失败，稍后重试")
            return
        }
        JSONObject json = JSONObject.parseObject(resStr)
        if ("Success" != (json.getString("State"))) {
            result.setErrorCode(1)
            result.setErrorMsg("创建订单失败，稍后重试")
            return
        }
        String link = json.getJSONObject("Content").getJSONObject("TicketInfo").getString("Link")
        result.setRedirectUrl(link)
    }


    public RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantApp merchant = args[1] as GlPaymentMerchantApp
        GlPaymentMerchantaccount account = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>
        this.channelBankBusiness = com.seektop.fund.payment.groovy.BaseScript.getResource(args[4], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        log.info("ShubaolaiScript_notify_resp:{}", JSON.toJSONString(resMap))
        String reqBody = resMap.get("reqBody")
        String orderId = JSONObject.parseObject(reqBody).getString("CusId")
        if (null != orderId && "" != (orderId)) {
            return this.payQuery(okHttpUtil, account, orderId, args[4])
        }
        return null
    }

    public RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String
        this.channelBankBusiness = com.seektop.fund.payment.groovy.BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        Map<String, String> params = new HashMap<>()
        params.put("Account", account.getMerchantCode())
        params.put("MerchantTicketId", orderId)
        String toSign = MD5.toAscii(params)
        toSign += "&key=" + account.getPrivateKey()
        params.put("Sign", MD5.md5(toSign).toUpperCase())
        log.info("ShubaolaiScript_query_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.RECHARGE_QUERY.getCode())
                .channelId(PaymentMerchantEnum.SHUBAOLAI_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.SHUBAOLAI_PAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId(orderId)
                .build()
        String resp = okHttpUtil.postJSON(account.getPayUrl() + "/Merchant/Fetch/Ticket?Alg=MD5", JSONObject.toJSONString(params), requestHeader)
        log.info("ShubaolaiScript_query_resp:{}", resp)
        if (StringUtils.isEmpty(resp)) {
            return null
        }
        JSONObject json = JSON.parseObject(resp)
        // 请求成功 并且 支付成功
        if ("Success" == (json.getString("State")) && "RESOLVED" == (json.getJSONObject("Content").getString("Status"))) {
            RechargeNotify pay = new RechargeNotify()
            json = json.getJSONObject("Content")
            pay.setAmount(json.getBigDecimal("RealAmount").setScale(2, RoundingMode.DOWN))
            pay.setFee(BigDecimal.ZERO)
            pay.setOrderId(orderId)
            pay.setThirdOrderId(json.getString("SLBTicketId"))
            return pay
        }

        return null
    }
}
