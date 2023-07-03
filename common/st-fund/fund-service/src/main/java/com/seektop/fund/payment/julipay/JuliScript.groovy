package com.seektop.fund.payment.julipay

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
import com.seektop.fund.model.GlWithdraw
import com.seektop.fund.model.GlWithdrawMerchantAccount
import com.seektop.fund.payment.*
import com.seektop.fund.payment.groovy.BaseScript
import com.seektop.fund.payment.groovy.ResourceEnum
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode

public class JuliScript {

    private static final Logger log = LoggerFactory.getLogger(JuliScript.class)

    private OkHttpUtil okHttpUtil

    private GlPaymentChannelBankBusiness paymentChannelBankService

    public RechargeNotify result(Object[] args) throws GlobalException {
        return null
    }

    public void pay(Object[] args) {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantApp merchant = args[1] as GlPaymentMerchantApp
        GlPaymentMerchantaccount account = args[2] as GlPaymentMerchantaccount
        RechargePrepareDO req = args[3] as RechargePrepareDO
        GlRechargeResult result = args[4] as GlRechargeResult
        this.paymentChannelBankService = BaseScript.getResource(args[5], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        Integer payMethod = null
        if (Arrays.asList(FundConstant.PaymentType.WECHAT_PAY, FundConstant.PaymentType.ALI_PAY,
                FundConstant.PaymentType.QQ_PAY, FundConstant.PaymentType.ALI_TRANSFER).contains(merchant.getPaymentId())) {
            if (FundConstant.PaymentType.WECHAT_PAY == merchant.getPaymentId()) {
                payMethod = 0
            } else if (FundConstant.PaymentType.ALI_PAY == merchant.getPaymentId()) {
                payMethod = 1
            } else if (FundConstant.PaymentType.QQ_PAY == merchant.getPaymentId()) {
                payMethod = 5
            } else if (FundConstant.PaymentType.ALI_TRANSFER == merchant.getPaymentId()) {
                payMethod = 14
            }
            prepareToScan(merchant, account, req, result, payMethod)
        }
    }

    private void prepareToScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result, Integer payMethod) throws GlobalException {
        Map<String, String> paramsMap = new HashMap<>()
        paramsMap.put("businessId", account.getMerchantCode())
        paramsMap.put("signType", "MD5")
        paramsMap.put("uid", req.getUsername())
        paramsMap.put("amount", req.getAmount().toString())
        paramsMap.put("outTradeNo", req.getOrderId())
        paramsMap.put("random", System.currentTimeMillis() + "")
        paramsMap.put("payMethod", payMethod + "")
        paramsMap.put("dataType", "0") // 返回json数据格式
        paramsMap.put("notifyUrl", account.getNotifyUrl() + merchant.getId())
        paramsMap.put("returnUrl", account.getNotifyUrl() + merchant.getId())
        paramsMap.put("secret", account.getPrivateKey())
        String signStrTemp = MD5.toAscii(paramsMap)
        paramsMap.put("sign", MD5.md5(signStrTemp))
        log.info("JuliScript_recharge_prepare_params:{}", JSON.toJSONString(paramsMap))
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.RECHARGE.getCode())
                .channelId(PaymentMerchantEnum.JULI_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.JULI_PAY.getPaymentName())
                .userId(req.getUserId().toString())
                .userName(req.getUsername())
                .tradeId(req.getOrderId())
                .build()
        String resStr = okHttpUtil.post(account.getPayUrl() + "/pay/createPayOrder", paramsMap, requestHeader)
        log.info("JuliScript_recharge_prepare_result:{}", resStr)
        JSONObject json = JSONObject.parseObject(resStr)
        if (StringUtils.isEmpty(resStr) || !json.getBoolean("successed")) {
            result.setErrorCode(1)
            result.setErrorMsg("订单创建失败，稍后重试")
            return
        }
        result.setRedirectUrl(json.getString("returnValue"))
    }

    public RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantApp merchant = args[1] as GlPaymentMerchantApp
        GlPaymentMerchantaccount account = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>
        this.paymentChannelBankService = BaseScript.getResource(args[4], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        log.info("JuliScript_notify_resp:{}", JSON.toJSONString(resMap))
        String orderId = resMap.get("outTradeNo")
        if (null != orderId && "" != (orderId)) {
            return this.payQuery(okHttpUtil, account, orderId, args[4])
        }
        return null
    }

    public RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String
        this.paymentChannelBankService = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        Map<String, String> paramsMap = new HashMap<>()
        paramsMap.put("businessId", account.getMerchantCode())
        paramsMap.put("signType", "MD5")
        paramsMap.put("outTradeNo", orderId)
        paramsMap.put("random", System.currentTimeMillis() + "")
        paramsMap.put("secret", account.getPrivateKey())
        String signStrTemp = MD5.toAscii(paramsMap)
        paramsMap.put("sign", MD5.md5(signStrTemp))
        log.info("JuliScript_query_params:{}", JSON.toJSONString(paramsMap))
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.RECHARGE_QUERY.getCode())
                .channelId(PaymentMerchantEnum.JULI_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.JULI_PAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId(orderId)
                .build()
        String resStr = okHttpUtil.post(account.getPayUrl() + "/pay/queryPayOrder", paramsMap, requestHeader)
        log.info("JuliScript_query_result:{}", resStr)
        JSONObject json = JSONObject.parseObject(resStr)
        // 查询成功 并且支付成功
        if (json.getBoolean("successed") && "2" == (json.getJSONObject("returnValue").getString("orderState"))) {
            JSONObject data = json.getJSONObject("returnValue")
            RechargeNotify pay = new RechargeNotify()
            pay.setAmount(data.getBigDecimal("amount").setScale(2, RoundingMode.DOWN))
            pay.setFee(BigDecimal.ZERO)
            pay.setOrderId(orderId)
            pay.setThirdOrderId(data.getString("oid"))
            return pay
        }
        return null
    }


    public WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        this.paymentChannelBankService = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        Map<String, String> paramsMap = new HashMap<>()
        paramsMap.put("businessId", merchantAccount.getMerchantCode())
        paramsMap.put("signType", "MD5")
        paramsMap.put("amount", req.getAmount().subtract(req.getFee()).toString())
        paramsMap.put("outTradeNo", req.getOrderId())
        paramsMap.put("cardNumber", req.getCardNo())
        paramsMap.put("cardholder", req.getName())
        paramsMap.put("openBank", paymentChannelBankService.getBankName(req.getBankId(), merchantAccount.getChannelId()))
        paramsMap.put("branchBank", "上海市")
        paramsMap.put("random", System.currentTimeMillis() + "")
        paramsMap.put("secret", merchantAccount.getPrivateKey())
        paramsMap.put("notifyUrl", merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId())
        String signStrTemp = MD5.toAscii(paramsMap)
        paramsMap.put("sign", MD5.md5(signStrTemp))
        log.info("JuliScript_doTransfer_params:{}", JSON.toJSONString(paramsMap))
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.WITHDRAW.getCode())
                .channelId(PaymentMerchantEnum.JULI_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.JULI_PAY.getPaymentName())
                .userId(req.getUserId().toString())
                .userName(req.getUsername())
                .tradeId(req.getOrderId())
                .build()
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/pay/applyCashWithdrawal", paramsMap, requestHeader)
        log.info("JuliScript_doTransfer_result:{}", resStr)
        WithdrawResult result = new WithdrawResult()
        result.setOrderId(req.getOrderId())
        result.setReqData(JSON.toJSONString(paramsMap))
        result.setResData(resStr)
        if (StringUtils.isEmpty(resStr)) {
            result.setValid(false)
            result.setMessage("API异常:请联系出款商户确认订单.")
            return result
        }
        JSONObject json = JSON.parseObject(resStr)
        if (!json.getBoolean("successed")) {
            result.setValid(false)
            result.setMessage(json.getString("errorDesc"))
            return result
        }
        req.setMerchantId(merchantAccount.getMerchantId())
        result.setValid(true)
        result.setMessage(json.getString("errorDesc"))
        return result
    }


    public WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        Map<String, String> resMap = args[2] as Map<String, String>
        this.paymentChannelBankService = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        log.info("JuliScript_doTransferNotify_params:{}", resMap)
        String orderId = resMap.get("outTradeNo")
        if (!StringUtils.isEmpty(orderId)) {
            return this.withdrawQuery(okHttpUtil, merchant, orderId, args[3])
        }
        return null
    }

    public WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2]
        this.paymentChannelBankService = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        Map<String, String> paramsMap = new HashMap<>()
        paramsMap.put("businessId", merchant.getMerchantCode())
        paramsMap.put("signType", "MD5")
        paramsMap.put("outTradeNo", orderId)
        paramsMap.put("secret", merchant.getPrivateKey())
        paramsMap.put("random", System.currentTimeMillis() + "")
        String signStrTemp = MD5.toAscii(paramsMap)
        paramsMap.put("sign", MD5.md5(signStrTemp))
        log.info("JuliScript_doTransferQuery_params:{}", JSON.toJSONString(paramsMap))
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.WITHDRAW_QUERY.getCode())
                .channelId(PaymentMerchantEnum.JULI_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.JULI_PAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId(orderId)
                .build()
        String resStr = okHttpUtil.post(merchant.getPayUrl() + "/pay/queryCashWithdrawalOrder", paramsMap, requestHeader)
        log.info("JuliScript_doTransferQuery_result:{}", resStr)
        JSONObject json = JSON.parseObject(resStr)
        if (!json.getBoolean("successed")) {
            return null
        }
        JSONObject returnValue = json.getJSONObject("returnValue")
        WithdrawNotify notify = new WithdrawNotify()
        notify.setAmount(returnValue.getBigDecimal("amount"))
        notify.setMerchantCode(merchant.getMerchantCode())
        notify.setMerchantId(merchant.getMerchantId())
        notify.setMerchantName(merchant.getChannelName())
        notify.setOrderId(orderId)
        notify.setThirdOrderId(returnValue.getString("oid"))
        if (returnValue.getString("orderState") == ("2")) {
            notify.setStatus(0)
        } else if (returnValue.getString("orderState") == ("4")) {
            notify.setStatus(1)
        } else {
            notify.setStatus(2)
        }
        return notify
    }


    public BigDecimal balanceQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        this.paymentChannelBankService = BaseScript.getResource(args[2], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        Map<String, String> paramsMap = new HashMap<>()
        paramsMap.put("businessId", merchantAccount.getMerchantCode())
        paramsMap.put("signType", "MD5")
        paramsMap.put("secret", merchantAccount.getPrivateKey())
        paramsMap.put("random", System.currentTimeMillis() + "")
        String signStrTemp = MD5.toAscii(paramsMap)
        paramsMap.put("sign", MD5.md5(signStrTemp))
        log.info("JuliScript_queryBalance_params:{}", JSON.toJSONString(paramsMap))
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.PAYMENT_QUERY_BALANCE.getCode())
                .channelId(PaymentMerchantEnum.JULI_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.JULI_PAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId("")
                .build()
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/pay/queryFinanceInfo", paramsMap, requestHeader)
        log.info("JuliScript_queryBalance_result:{}", resStr)
        JSONObject json = JSON.parseObject(resStr)
        if (json.getBoolean("successed")) {
            return json.getJSONObject("returnValue").getBigDecimal("availableMoney")
        }
        return BigDecimal.ZERO
    }
}
