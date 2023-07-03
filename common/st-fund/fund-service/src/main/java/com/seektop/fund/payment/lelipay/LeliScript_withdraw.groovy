package com.seektop.fund.payment.lelipay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
import com.seektop.common.utils.DateUtils
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

class LeliScript_withdraw {

    private static final Logger log = LoggerFactory.getLogger(LeliScript_withdraw.class)

    private OkHttpUtil okHttpUtil

    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness

    WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount account = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        String keyValue = account.getPrivateKey()
        Date now = new Date();
        Map<String, String> paramMap = new HashMap<>()
        paramMap.put("txnType", "52")
        paramMap.put("txnSubType", "10")
        paramMap.put("secpVer", "icp3-1.1")
        paramMap.put("secpMode", "perm")
        paramMap.put("macKeyId", account.getMerchantCode())
        paramMap.put("orderDate", DateUtils.format(now, DateUtils.YYYYMMDD))
        paramMap.put("orderTime", DateUtils.format(now, DateUtils.HHMMSS))
        paramMap.put("merId", account.getMerchantCode())
        paramMap.put("orderId", req.getOrderId())
        paramMap.put("txnAmt", req.getAmount().subtract(req.getFee()).multiply(BigDecimal.valueOf(100)).setScale(0).toString())
        paramMap.put("currencyCode", "156")
        paramMap.put("accName", req.getName())
        paramMap.put("accNum", req.getCardNo())
        paramMap.put("bankNum", glPaymentChannelBankBusiness.getBankCode(req.getBankId(), account.getChannelId())) //银行Code
        paramMap.put("bankName", req.getBankName())
        paramMap.put("notifyUrl", account.getNotifyUrl() + account.getMerchantId())
        paramMap.put("timeStamp", DateUtils.format(now, DateUtils.YYYYMMDDHHMMSS))
        String toSign = MD5.toAscii(paramMap) + "&k=" + keyValue
        String sign = MD5.md5(toSign).toLowerCase()
        paramMap.put("mac", sign)

        log.info("LeliScript_Transfer_paramMap: {}", JSON.toJSONString(paramMap))
        GlRequestHeader requestHeader =
                getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(), account.getChannelId(), account.getChannelName())
        String resStr = okHttpUtil.post(account.getPayUrl() + "/lelipay-gateway-onl/txn", paramMap, requestHeader)
        log.info("LeliScript_Transfer_resStr: {}", resStr)

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
        if (json == null || "0000" != json.getString("respCode")) {
            result.setValid(false)
            result.setMessage(json == null ? "API异常:请联系出款商户确认订单." : json.getString("respMsg"))
            return result
        }
        String txnStatus = json.getString("txnStatus")
        result.setValid("01" == txnStatus || "10" == txnStatus)
        result.setMessage(json.getString("respMsg"))
        return result
    }

    WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        Map<String, String> resMap = args[2] as Map<String, String>
        log.info("LeliScript_Transfer_notify_resp:{}", JSON.toJSONString(resMap))
        String merOrderNo = resMap.get("orderId")// 商户订单号
        String orderDate = resMap.get("orderDate")

        if (StringUtils.isNotEmpty(merOrderNo) && StringUtils.isNotEmpty(orderDate) && StringUtils.isNotEmpty(resMap.get("respCode"))) {
            return withdrawQuery(okHttpUtil, merchant, merOrderNo + "-" + orderDate, args[3])
        }
        return null
    }

    WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2]
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        String keyValue = merchant.getPrivateKey()
        String[] orderParam = orderId.split("-")
        Map<String, String> paramMap = new HashMap<>()
        paramMap.put("txnType", "00")
        paramMap.put("txnSubType", "50")
        paramMap.put("secpVer", "icp3-1.1")
        paramMap.put("secpMode", "perm")
        paramMap.put("macKeyId", merchant.getMerchantCode())
        paramMap.put("merId", merchant.getMerchantCode())
        paramMap.put("orderId", orderParam[0])
        paramMap.put("orderDate", orderParam[1])
        paramMap.put("timeStamp", DateUtils.format(new Date(), DateUtils.YYYYMMDDHHMMSS))
        String toSign = MD5.toAscii(paramMap)
        toSign = toSign + "&k=" + keyValue
        String sign = MD5.md5(toSign).toLowerCase()
        paramMap.put("mac", sign)

        log.info("LeliScript_TransferQuery_paramMap: {}", JSON.toJSONString(paramMap))
        GlRequestHeader requestHeader = getRequestHeard(null, null, orderId, GlActionEnum.WITHDRAW_QUERY.getCode(), merchant.getChannelId(), merchant.getChannelName())
        String resStr = okHttpUtil.post(merchant.getPayUrl() + "/lelipay-gateway-onl/txn", paramMap, requestHeader)
        log.info("LeliScript_TransferQuery_resStr: {}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || json.getString("respCode") == null) {
            return null
        }
        String TradeStatus = json.getString("txnStatus")

        WithdrawNotify notify = new WithdrawNotify()
        notify.setAmount(json.getBigDecimal("txnAmt"))
        notify.setMerchantCode(merchant.getMerchantCode())
        notify.setMerchantId(merchant.getMerchantId())
        notify.setMerchantName(merchant.getChannelName())
        notify.setOrderId(orderParam[0])
        notify.setRemark(json.getString("extInfo"))
        if (TradeStatus == "10") {//01---处理中  10---交易成功 20---交易失败
            notify.setStatus(0)
        } else if (TradeStatus == "20") {
            notify.setStatus(1)
        } else {
            notify.setStatus(2)
        }
        return notify
    }


    BigDecimal balanceQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount account = args[1] as GlWithdrawMerchantAccount
        String keyValue = account.getPrivateKey()
        Map<String, String> paramMap = new HashMap<>()
        paramMap.put("txnType", "00")
        paramMap.put("txnSubType", "90")
        paramMap.put("secpVer", "icp3-1.1")
        paramMap.put("secpMode", "perm")
        paramMap.put("macKeyId", account.getMerchantCode())
        paramMap.put("merId", account.getMerchantCode())
        paramMap.put("accCat", "00")
        paramMap.put("timeStamp", DateUtils.format(new Date(), DateUtils.YYYYMMDDHHMMSS))
        String toSign = MD5.toAscii(paramMap)
        toSign = toSign + "&k=" + keyValue
        String sign = MD5.md5(toSign).toLowerCase()
        paramMap.put("mac", sign)

        log.info("LeliScript_query_balance_param: {}", JSON.toJSONString(paramMap))
        GlRequestHeader requestHeader =
                getRequestHeard("", "", "", GlActionEnum.PAYMENT_QUERY_BALANCE.getCode(), account.getChannelId(), account.getChannelName())
        String resStr = okHttpUtil.post(account.getPayUrl() + "/lelipay-gateway-onl/txn", paramMap, requestHeader)
        log.info("LeliScript_query_balance_resStr: {}", resStr)
        if (StringUtils.isEmpty(resStr)) {
            return BigDecimal.ZERO
        }
        JSONObject json = JSON.parseObject(resStr)
        if (json == null || json.getString("respCode") == null || "0000" != json.getString("respCode")) {
            return BigDecimal.ZERO
        }
        BigDecimal Balance = json.getBigDecimal("balance").divide(BigDecimal.valueOf(100)).setScale(2, RoundingMode.DOWN)
        return Balance == null ? BigDecimal.ZERO : Balance
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