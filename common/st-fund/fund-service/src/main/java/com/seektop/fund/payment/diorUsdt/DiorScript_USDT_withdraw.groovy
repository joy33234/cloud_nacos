package com.seektop.fund.payment.diorUsdt

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
import com.seektop.common.redis.RedisService
import com.seektop.common.utils.MD5
import com.seektop.constant.RedisKeyHelper
import com.seektop.constant.user.UserConstant
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

class DiorScript_USDT_withdraw {

    private static final Logger log = LoggerFactory.getLogger(DiorScript_USDT_withdraw.class)

    private OkHttpUtil okHttpUtil

    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness

    private RedisService redisService

    public WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        this.glPaymentChannelBankBusiness = com.seektop.fund.payment.groovy.BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

        this.redisService = com.seektop.fund.payment.groovy.BaseScript.getResource(args[3], ResourceEnum.RedisService) as RedisService

        BigDecimal usdtRate = null;
        if (UserConstant.UserType.PLAYER == req.getUserType()) {
            usdtRate  = redisService.get(RedisKeyHelper.WITHDRAW_USDT_RATE, BigDecimal.class);
        } else {
            usdtRate  = redisService.get(RedisKeyHelper.WITHDRAW_USDT_RATE_PROXY, BigDecimal.class);
        }
        BigDecimal amount = req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN);
        BigDecimal usdtAmount = amount.divide(usdtRate, 4, RoundingMode.DOWN)

        Map<String, String> DataContentParams = new HashMap<>()
        DataContentParams.put("MerchantCode", merchantAccount.getMerchantCode())
        DataContentParams.put("OrderId", req.getOrderId())
        DataContentParams.put("BankCardNum", req.getCardNo())
        DataContentParams.put("BankCardName", req.getName())
        DataContentParams.put("Branch", "上海市")
        DataContentParams.put("BankCode", req.getAddress())
        DataContentParams.put("Amount", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString())
        DataContentParams.put("OrderDate", req.getCreateDate().getTime() + "")

        String toSign = MD5.toAscii(DataContentParams) + "&Key=" + merchantAccount.getPrivateKey()
        DataContentParams.put("Sign", MD5.md5(toSign).toLowerCase())

        DataContentParams.put("NotifyUrl", merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId())
        DataContentParams.put("Province", "上海市")
        DataContentParams.put("City", "上海市")
        DataContentParams.put("Area", "上海市")


        log.info("DiorScript_Transfer_params: {}", JSON.toJSONString(DataContentParams))
        GlRequestHeader requestHeader = getRequestHeard(req.getUserId() + "", req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode())
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/api/withdraw", DataContentParams, requestHeader)
        log.info("DiorScript_Transfer_resStr: {}", resStr)


        WithdrawResult result = new WithdrawResult()
        result.setOrderId(req.getOrderId())
        result.setReqData(JSON.toJSONString(DataContentParams))
        result.setResData(resStr)
        result.setUsdtAmount(usdtAmount)
        result.setRate(usdtRate)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || "200" != json.getString("resultCode") || !json.getBoolean("success")) {
            result.setValid(false)
            result.setMessage(json == null ? "API异常:请联系出款商户确认订单." : json.getString("resultMsg"))
            return result
        }
        result.setValid(true)
        result.setMessage("")
        JSONObject dataJSON = json.getJSONObject("data").getJSONObject("data")
        if (dataJSON != null) {
            result.setThirdOrderId(dataJSON.getString("orderId"))
        }
        return result
    }

    public WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        Map<String, String> resMap = args[2] as Map<String, String>
        log.info("DiorScript_Notify_resMap:{}", JSON.toJSONString(resMap))
        String orderId = resMap.get("OrderId")
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
        DataContentParams.put("MerchantCode", merchant.getMerchantCode())
        DataContentParams.put("Time", System.currentTimeMillis() + "")
        DataContentParams.put("OrderId", orderId)

        String toSign = MD5.toAscii(DataContentParams) + "&Key=" + merchant.getPrivateKey()
        DataContentParams.put("Sign", MD5.md5(toSign).toLowerCase())

        log.info("DiorScript_TransferQuery_reqMap:{}", JSON.toJSONString(DataContentParams))
        GlRequestHeader requestHeader = getRequestHeard("", "", orderId, GlActionEnum.WITHDRAW_QUERY.getCode())
        String resStr = okHttpUtil.post(merchant.getPayUrl() + "/api/withdrawquery", DataContentParams, requestHeader)
        log.info("DiorScript_TransferQuery_resStr:{}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null) {
            return null
        }
        WithdrawNotify notify = new WithdrawNotify()
        if ("200" == json.getString("resultCode") && json.getBoolean("success")) {
            JSONObject dataJSON = json.getJSONObject("data").getJSONObject("data")
            notify.setAmount(dataJSON.getBigDecimal("moneyReceived"))
            notify.setMerchantCode(merchant.getMerchantCode())
            notify.setMerchantId(merchant.getMerchantId())
            notify.setMerchantName(merchant.getChannelName())
            notify.setOrderId(orderId)
            notify.setThirdOrderId("")
            if (dataJSON.getString("status") == "2") {//商户返回出款状态：0成功，1失败,2处理中      三方订单状态 0成功，1失败,2处理中
                notify.setStatus(0)
            } else if (dataJSON.getString("status") == "3") {
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
        DataContentParams.put("MerchantCode", merchantAccount.getMerchantCode())
        DataContentParams.put("Time", System.currentTimeMillis() + "")

        String toSign = MD5.toAscii(DataContentParams) + "&Key=" + merchantAccount.getPrivateKey()
        DataContentParams.put("Sign", MD5.md5(toSign).toLowerCase())

        log.info("DiorScript_QueryBalance_reqMap: {}", JSON.toJSONString(DataContentParams))
        GlRequestHeader requestHeader = getRequestHeard("", "", "", GlActionEnum.PAYMENT_QUERY_BALANCE.getCode())
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/api/balancequery", DataContentParams, requestHeader)
        log.info("DiorScript_QueryBalance_resStr: {}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if ("200" == json.getString("resultCode") && json.getBoolean("success")) {
            JSONObject dataJson = json.getJSONObject("data").getJSONObject("data")
            return dataJson.getBigDecimal("dfamount") == null ? BigDecimal.ZERO : dataJson.getBigDecimal("dfamount")
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
