package com.seektop.fund.payment.qingpingguopay

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
import org.apache.commons.lang3.ObjectUtils
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode

/**
 * @desc 青苹果支付
 * @auth joy
 * @date 2021-05-10
 */

class QingPingGuoScript_withdraw {

    private static final Logger log = LoggerFactory.getLogger(QingPingGuoScript_withdraw.class)

    private OkHttpUtil okHttpUtil

    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness


    WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw

        Map<String, String> paramMap = new LinkedHashMap<>()
        paramMap.put("merchantId", merchantAccount.getMerchantCode())
        paramMap.put("merchantOrderId", req.getOrderId())
        paramMap.put("orderAmount", req.getAmount().subtract(req.getFee()).setScale(0, RoundingMode.DOWN).toString())
        paramMap.put("payType", "1")
        paramMap.put("accountHolderName", req.getName())
        paramMap.put("accountNumber", req.getCardNo())
        paramMap.put("bankType", glPaymentChannelBankBusiness.getBankCode(req.getBankId(), merchantAccount.getChannelId())) //银行Code
        paramMap.put("notifyUrl", merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId())
        paramMap.put("reverseUrl", merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId())
        paramMap.put("submitIp", "0.0.0.0")

        String toSign = MD5.toSign(paramMap) + merchantAccount.getPrivateKey()
        paramMap.put("sign", MD5.md5(toSign))
        paramMap.put("subBranch", "上海市")

        log.info("QingPingGuoScript_Transfer_Params: {}", JSON.toJSONString(paramMap))
        GlRequestHeader requestHeader =
                getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(), merchantAccount.getChannelId(),merchantAccount.getChannelName())
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/PaymentGetway/SinglePay", paramMap, 30L, requestHeader)
        log.info("QingPingGuoScript_Transfer_resStr: {}", resStr)

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
        if (json == null ||  ObjectUtils.isNotEmpty(json.get("ErrorCode")) || ObjectUtils.isNotEmpty(json.get("ErrorMessage"))) {
            result.setValid(false)
            result.setMessage(json == null ? "API异常:请联系出款商户确认订单." : json.getString("ErrorMessage"))
            return result
        }
        result.setValid(true)
        result.setMessage(json.getString("ErrorMessage"))
        return result
    }

    WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        Map<String, String> resMap = args[2] as Map<String, String>
        log.info("QingPingGuoScript_Transfer_Notify_resMap:{}", JSON.toJSONString(resMap))
        String orderId = resMap.get("merchantOrderId")
        String thirdOrderId = resMap.get("systemOrderId")
        String orderAmount = resMap.get("orderAmount")
        if (StringUtils.isNotEmpty(orderId) && StringUtils.isNotEmpty(orderAmount)) {
            return withdrawQuery(okHttpUtil, merchant, orderId, thirdOrderId, orderAmount)
        }
        return null
    }

    WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2]
        String thirdOrderId = args[3]
        String orderAmount = args[4]

        Map<String, String> paramMap = new LinkedHashMap<>()
        paramMap.put("merchantId", merchant.getMerchantCode())
        paramMap.put("merchantOrderId", orderId)
        paramMap.put("orderAmount", orderAmount)
        String toSign = MD5.toSign(paramMap) + merchant.getPrivateKey()
        paramMap.put("sign", MD5.md5(toSign))

        log.info("QingPingGuoScript_Transfer_Query_Params: {}", JSON.toJSONString(paramMap))
        GlRequestHeader requestHeader =
                getRequestHeard(null, null, orderId, GlActionEnum.WITHDRAW_QUERY.getCode(),merchant.getChannelId(), merchant.getChannelName())
        String resStr = okHttpUtil.post(merchant.getPayUrl() + "/PaymentGetway/SinglePayOrderQuery", paramMap, requestHeader)
        log.info("QingPingGuoScript_Transfer_Query_resStr: {}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || StringUtils.isNotEmpty(json.getString("ErrorMessage")) || StringUtils.isNotEmpty(json.getString("ErrorCode"))) {
            return null
        }

        //订单处理状态 1:等待处理  2:准备打款  3:支付成功  4:支付失败
        Integer orderState = json.getInteger("Status")
        WithdrawNotify notify = new WithdrawNotify()
        notify.setMerchantCode(merchant.getMerchantCode())
        notify.setMerchantId(merchant.getMerchantId())
        notify.setMerchantName(merchant.getChannelName())
        notify.setOrderId(orderId)
        if (orderState == 3) {
            notify.setStatus(0)
            notify.setRsp("OK")
        } else if (orderState == 4) {
            notify.setStatus(1)
            notify.setRsp("OK")
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
        paramMap.put("merchantId", merchantAccount.getMerchantCode())

        String toSign = MD5.toSign(paramMap) + merchantAccount.getPrivateKey()
        paramMap.put("sign", MD5.md5(toSign))

        log.info("QingPingGuoScript_Query_Balance_Params: {}", JSON.toJSONString(paramMap))
        GlRequestHeader requestHeader =
                getRequestHeard(null, null, null, GlActionEnum.PAYMENT_QUERY_BALANCE.getCode(),merchantAccount.getChannelId(),merchantAccount.getChannelName())
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/PaymentGetway/SinglePayBalanceQuery", paramMap,  requestHeader)
        log.info("QingPingGuoScript_Query_Balance_resStr: {}", resStr)

        if (StringUtils.isEmpty(resStr)) {
            return BigDecimal.ZERO
        }
        JSONObject json = JSON.parseObject(resStr)
        if (json == null || StringUtils.isNotEmpty(json.getString("ErrorCode")) || StringUtils.isNotEmpty(json.getString("ErrorMessage"))) {
            return BigDecimal.ZERO
        }
        return json.getBigDecimal("TopupBalance") == null ? BigDecimal.ZERO : json.getBigDecimal("TopupBalance")
    }

    /**
     * 获取头部信息
     *
     * @param userId
     * @param userName
     * @param orderId
     * @return
     */
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