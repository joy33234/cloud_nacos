package com.seektop.fund.payment.applePay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
import com.seektop.common.utils.MD5
import com.seektop.enumerate.GlActionEnum
import com.seektop.exception.GlobalException
import com.seektop.fund.model.GlWithdraw
import com.seektop.fund.model.GlWithdrawMerchantAccount
import com.seektop.fund.payment.WithdrawNotify
import com.seektop.fund.payment.WithdrawResult
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode


/**
 * @desc 苹果支付
 * @auth joy
 * @date 2021-10-01
 */

class AppleScript_withdraw {

    private static final Logger log = LoggerFactory.getLogger(AppleScript_withdraw.class)

    private OkHttpUtil okHttpUtil

    WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw

        Map<String, String> params = new HashMap<>()
        params.put("merchantNum", merchantAccount.getMerchantCode())
        params.put("orderNo", req.getOrderId())
        params.put("amount", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString())
        params.put("notifyUrl", merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId())
        params.put("pay_bankname", merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId())
        params.put("channelCode", "bankCard")//代付通道
        params.put("accountHolder", req.getName())
        params.put("bankCardAccount", req.getCardNo())
        params.put("openAccountBank", req.getBankName())

        StringBuilder toSign = new StringBuilder();
        toSign.append("num").append(params.get("merchantNum"))
        toSign.append("order:").append(params.get("orderNo"))
        toSign.append("amount:").append(params.get("amount"))
        toSign.append("not:").append(params.get("notifyUrl"))
        toSign.append("xiangyunxihongshi").append(merchantAccount.getPrivateKey())

        params.put("sign", MD5.md5(toSign.toString()))
        log.info("AppleScript_Transfer_Params: {}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(), merchantAccount.getChannelId(),merchantAccount.getChannelName())
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/api/startPayForAnotherOrder", params, 30L, requestHeader)
        log.info("AppleScript_Transfer_resStr: {}", resStr)

        WithdrawResult result = new WithdrawResult()
        result.setOrderId(req.getOrderId())
        result.setReqData(JSON.toJSONString(params))
        result.setResData(resStr)

        if (StringUtils.isEmpty(resStr)) {
            result.setValid(false)
            result.setMessage("API异常:请联系出款商户确认订单.")
            return result
        }

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || "200" != json.getString("code")) {
            result.setValid(false)
            result.setMessage(json == null ? "API异常:请联系出款商户确认订单." : json.getString("msg"))
            return result
        }
        result.setValid(true)
        result.setMessage(json.getString("msg"))
        result.setThirdOrderId(json.getString("transaction_id"))
        return result
    }

    WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        Map<String, String> resMap = args[2] as Map<String, String>
        log.info("AppleScript_Transfer_Notify_resMap:{}", JSON.toJSONString(resMap))
        String orderId = resMap.get("orderNo")
        String thirdOrderId = resMap.get("platformOrderNo")
        if (StringUtils.isNotEmpty(orderId)) {
            return withdrawQuery(okHttpUtil, merchant, orderId, thirdOrderId)
        }
        return null
    }

    WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2]
        String thirdOrderId = args[3]
        String keyValue = merchant.getPrivateKey() // 商家密钥
        Map<String, String> params = new HashMap<>()
        params.put("merchantNum", merchant.getMerchantCode())
        params.put("merchantOrderNo", orderId)
        params.put("sign", MD5.md5(merchant.getMerchantCode() + orderId + keyValue))

        log.info("AppleScript_Transfer_Query_Params: {}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                getRequestHeard(null, null, orderId, GlActionEnum.WITHDRAW_QUERY.getCode(),merchant.getChannelId(), merchant.getChannelName())
        String resStr = okHttpUtil.get(merchant.getPayUrl() + "/api/getPayForAnotherOrderInfo", params, requestHeader)
        log.info("AppleScript_Transfer_Query_resStr: {}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || json.getString("code") == null || json.getString("code") != "200") {
            return null
        }
        JSONObject dataJSON = json.getJSONObject("data")
        if (dataJSON == null) {
            return null
        }
        //1：等待接单，2：已接单，3：已完成，4：已取消，5：超时取消，6：异常退回
        Integer orderState = dataJSON.getInteger("orderState")
        WithdrawNotify notify = new WithdrawNotify()
        notify.setAmount(dataJSON.getBigDecimal("amount"))
        notify.setMerchantCode(merchant.getMerchantCode())
        notify.setMerchantId(merchant.getMerchantId())
        notify.setMerchantName(merchant.getChannelName())
        notify.setOrderId(orderId)
        if (orderState == 3) {
            notify.setStatus(0)
        } else if (orderState == 4 || orderState == 6) {
            notify.setStatus(1)
        } else {
            notify.setStatus(2)
        }
        notify.setThirdOrderId(thirdOrderId)
        return notify
    }


    BigDecimal balanceQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        String keyValue = merchantAccount.getPrivateKey() // 商家密钥
        String pay_memberid = merchantAccount.getMerchantCode()

        Map<String, String> params = new HashMap<>()
        params.put("merchantNum", pay_memberid)
        params.put("sign", MD5.md5(pay_memberid + keyValue))

        log.info("AppleScript_Query_Balance_Params: {}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                getRequestHeard(null, null, null, GlActionEnum.PAYMENT_QUERY_BALANCE.getCode(),merchantAccount.getChannelId(),merchantAccount.getChannelName())
        String resStr = okHttpUtil.get(merchantAccount.getPayUrl() + "/api/getBalance", params,  requestHeader)
        log.info("AppleScript_Query_Balance_resStr: {}", resStr)

        if (StringUtils.isEmpty(resStr)) {
            return BigDecimal.ZERO
        }
        JSONObject json = JSON.parseObject(resStr)
        if (json == null || json.getString("code") != "200") {
            return BigDecimal.ZERO
        }
        JSONObject dataJSON = json.getJSONObject("data");
        if (dataJSON == null) {
            return BigDecimal.ZERO
        }
        BigDecimal balance = dataJSON.getBigDecimal("balance")
        return balance == null ? BigDecimal.ZERO : balance
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