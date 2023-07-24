package com.seektop.fund.payment.jiufuUSDT

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
import com.seektop.common.redis.RedisService
import com.seektop.common.utils.BigDecimalUtils
import com.seektop.common.utils.MD5
import com.seektop.enumerate.GlActionEnum
import com.seektop.exception.GlobalException
import com.seektop.fund.business.GlPaymentChannelBankBusiness
import com.seektop.fund.model.GlWithdraw
import com.seektop.fund.model.GlWithdrawMerchantAccount
import com.seektop.fund.payment.PaymentMerchantEnum
import com.seektop.fund.payment.WithdrawNotify
import com.seektop.fund.payment.WithdrawResult
import com.seektop.fund.payment.groovy.ResourceEnum
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode

class JiufuScript_USDT_withdraw {

    private static final Logger log = LoggerFactory.getLogger(JiufuScript_USDT_withdraw.class)

    private OkHttpUtil okHttpUtil

    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness

    private RedisService redisService

    public WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        this.glPaymentChannelBankBusiness = com.seektop.fund.payment.groovy.BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

        this.redisService = com.seektop.fund.payment.groovy.BaseScript.getResource(args[3], ResourceEnum.RedisService) as RedisService

        BigDecimal usdtRate = getRate(merchantAccount)
        BigDecimal amount = req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN);
        BigDecimal usdtAmount = amount.divide(usdtRate, 4, RoundingMode.DOWN)

        Map<String, String> DataContentParms = new HashMap<>()
        DataContentParms.put("partner", merchantAccount.getMerchantCode())
        DataContentParms.put("service", "10202")
        DataContentParms.put("tradeNo", req.getOrderId())
        DataContentParms.put("amount", usdtAmount.toString())
        DataContentParms.put("address", req.getAddress())
        DataContentParms.put("notifyUrl", merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId())


        String toSign = MD5.toAscii(DataContentParms) + "&" + merchantAccount.getPrivateKey()
        DataContentParms.put("sign", MD5.md5(toSign).toLowerCase())
        
        log.info("JiufuUSDTScript_Transfer_params: {}", JSON.toJSONString(DataContentParms))
        GlRequestHeader requestHeader = getRequestHeard(req.getUserId() + "", req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode())
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/usdtAgentPay", DataContentParms, requestHeader)
        log.info("JiufuUSDTScript_Transfer_resStr: {}", resStr)

        WithdrawResult result = new WithdrawResult()
        result.setOrderId(req.getOrderId())
        result.setReqData(JSON.toJSONString(DataContentParms))
        result.setResData(resStr)
        result.setUsdtAmount(usdtAmount)
        result.setRate(usdtRate)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || "T" != json.getString("isSuccess")) {
            result.setValid(false)
            result.setMessage(json == null ? "API异常:请联系出款商户确认订单." : json.getString("msg"))
            return result
        }
        result.setValid(true)
        result.setMessage("")
        result.setThirdOrderId(json.getString("tradeId"))
        return result
    }

    public WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        Map<String, String> resMap = args[2] as Map<String, String>
        log.info("JiufuUSDTScript_Notify_resMap:{}", JSON.toJSONString(resMap))
        String orderId = resMap.get("outTradeNo")
        if (StringUtils.isNotEmpty(orderId)) {
            return withdrawQuery(this.okHttpUtil, merchant, orderId, args[3])
        } else {
            return null
        }
    }

    public WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2]
        Map<String, String> DataContentParams = new HashMap<String, String>()
        DataContentParams.put("partner", merchant.getMerchantCode())
        DataContentParams.put("service", "10303")
        DataContentParams.put("outTradeNo", orderId)

        String toSign = MD5.toAscii(DataContentParams) + "&" + merchant.getPrivateKey()
        DataContentParams.put("Sign", MD5.md5(toSign).toLowerCase())

        log.info("JiufuUSDTScript_TransferQuery_reqMap:{}", JSON.toJSONString(DataContentParams))
        GlRequestHeader requestHeader = getRequestHeard("", "", orderId, GlActionEnum.WITHDRAW_QUERY.getCode())
        String resStr = okHttpUtil.post(merchant.getPayUrl() + "/orderQuery", DataContentParams, requestHeader)
        log.info("JiufuUSDTScript_TransferQuery_resStr:{}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null) {
            return null
        }
        WithdrawNotify notify = new WithdrawNotify()
        if ("T" == json.getString("isSuccess")) {
            notify.setAmount(json.getBigDecimal("amount"))
            notify.setMerchantCode(merchant.getMerchantCode())
            notify.setMerchantId(merchant.getMerchantId())
            notify.setMerchantName(merchant.getChannelName())
            notify.setOrderId(orderId)
            notify.setThirdOrderId("")
            //商户返回出款状态：0 处理理中  1 成功   2 失败   3 处理理中  4 处理理中
            if (json.getString("status") == "1") {
                notify.setStatus(0)
            } else if (json.getString("status") == "2") {
                notify.setStatus(1)
            } else {
                notify.setStatus(2)
            }
        }
        return notify
    }


    public BigDecimal balanceQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        Map<String, String> DataContentParams = new HashMap<>()
        DataContentParams.put("partner", merchantAccount.getMerchantCode())
        DataContentParams.put("service", "10402")

        String toSign = MD5.toAscii(DataContentParams) + "&" + merchantAccount.getPrivateKey()
        DataContentParams.put("sign", MD5.md5(toSign).toLowerCase())

        log.info("JiufuUSDTScript_QueryBalance_reqMap: {}", JSON.toJSONString(DataContentParams))
        GlRequestHeader requestHeader = getRequestHeard("", "", "", GlActionEnum.PAYMENT_QUERY_BALANCE.getCode())
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/balanceQuery", DataContentParams, requestHeader)
        log.info("JiufuUSDTScript_QueryBalance_resStr: {}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if ("T" == json.getString("isSuccess")) {
            BigDecimal usdtRate = getRate(merchantAccount)
            BigDecimal amount = json.getBigDecimal("balance") == null ? BigDecimal.ZERO : json.getBigDecimal("balance");
            if (BigDecimalUtils.moreThanZero(usdtRate) && BigDecimalUtils.moreThanZero(amount)) {
                return amount.multiply(usdtRate).setScale(4,RoundingMode.DOWN)
            }
        }
        return BigDecimal.ZERO
    }


    public BigDecimal getRate(GlWithdrawMerchantAccount account) throws GlobalException {
        Map<String, String> DataContentParams = new HashMap<>()
        DataContentParams.put("partner", account.getMerchantCode())
        DataContentParams.put("service", "10501")

        String toSign = MD5.toAscii(DataContentParams) + "&" + account.getPrivateKey()
        DataContentParams.put("sign", MD5.md5(toSign).toLowerCase())

        log.info("JiufuUSDTScript_QueryRate_reqMap: {}", JSON.toJSONString(DataContentParams))
        GlRequestHeader requestHeader = getRequestHeard("", "", "", GlActionEnum.QUERY_RATE.getCode())
        String resStr = okHttpUtil.post(account.getPayUrl() + "/usdtRateQuery", DataContentParams, requestHeader)
        log.info("JiufuUSDTScript_QueryRate_resStr: {}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if ("T" == json.getString("isSuccess")) {
            return json.getBigDecimal("rate") == null ? BigDecimal.ZERO : json.getBigDecimal("rate")
        }
        return BigDecimal.ZERO
    }




    private static GlRequestHeader getRequestHeard(String userId, String userName, String orderId, String code) {
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(code)
                .channelId(PaymentMerchantEnum.DIOR_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.DIOR_PAY.getPaymentName())
                .userId(userId)
                .userName(userName)
                .tradeId(orderId)
                .build()
        return requestHeader
    }
}
