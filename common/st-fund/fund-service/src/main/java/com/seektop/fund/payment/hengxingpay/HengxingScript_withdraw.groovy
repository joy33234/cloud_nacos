package com.seektop.fund.payment.hengxingpay

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
 * 恒星支付
 * @auth joy
 * @date 2021-05-14
 */

class HengxingScript_withdraw {

    private static final Logger log = LoggerFactory.getLogger(HengxingScript_withdraw.class)

    private OkHttpUtil okHttpUtil

    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness

    WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        this.glPaymentChannelBankBusiness = com.seektop.fund.payment.groovy.BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw

        Map<String, String> paramMap = new HashMap<>()
        paramMap.put("merchantNum", merchantAccount.getMerchantCode())
        paramMap.put("merchantOrderNum", req.getOrderId())
        paramMap.put("userCardNum", req.getCardNo())
        paramMap.put("bankCode", glPaymentChannelBankBusiness.getBankCode(req.getBankId(), merchantAccount.getChannelId())) //银行Code
        paramMap.put("amount", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString())
        paramMap.put("sign", MD5.md5(MD5.toAscii(paramMap) + "&key=" + merchantAccount.getPrivateKey()))

        paramMap.put("callbackUrl", merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId())
        paramMap.put("userCardName", req.getName())

        log.info("HengxingScript_Transfer_Params: {}", JSON.toJSONString(paramMap))
        GlRequestHeader requestHeader =
                getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(), merchantAccount.getChannelId(),merchantAccount.getChannelName())
        String resStr = okHttpUtil.postJSON(merchantAccount.getPayUrl() + "/api/merchant-api/user-withdraw/request", JSON.toJSONString(paramMap),  requestHeader)
        log.info("HengxingScript_Transfer_resStr: {}", resStr)

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
        if (json == null || !json.getBoolean("success")) {
            result.setValid(false)
            result.setMessage(json == null ? "API异常:请联系出款商户确认订单." : json.getString("message"))
            return result
        }
        result.setValid(true)
        result.setMessage(json.getString("message"))
        return result
    }

    WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        Map<String, String> resMap = args[2] as Map<String, String>
        log.info("HengxingScript_Transfer_Notify_resMap:{}", JSON.toJSONString(resMap))
        JSONObject json = JSON.parseObject(resMap.get("reqBody"))
        String orderid = json.getString("merchantOrderNum")
        if (StringUtils.isNotEmpty(orderid)) {
            return withdrawQuery(okHttpUtil, merchant, orderid)
        }
        return null
    }

    WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2]
        Map<String, String> paramMap = new HashMap<>()
        paramMap.put("merchantNum", merchant.getMerchantCode())
        paramMap.put("merchantOrderNum", orderId)
        paramMap.put("sign", MD5.md5(MD5.toAscii(paramMap) + "&key=" + merchant.getPrivateKey()))

        log.info("HengxingScript_Transfer_Query_Params: {}", JSON.toJSONString(paramMap))
        GlRequestHeader requestHeader =
                getRequestHeard(null, null, orderId, GlActionEnum.WITHDRAW_QUERY.getCode(),merchant.getChannelId(), merchant.getChannelName())
        String resStr = okHttpUtil.postJSON(merchant.getPayUrl() + "/api/merchant-api/user-withdraw/get", JSON.toJSONString(paramMap), requestHeader)
        log.info("HengxingScript_Transfer_Query_resStr: {}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || !json.getBoolean("success")) {
            return null
        }
        JSONObject dataJSON = json.getJSONObject("data")
        if (dataJSON == null) {
            return null
        }
        //1待处理，2进行中，3拒绝，6完成，7失败，8进行中
        Integer status = dataJSON.getInteger("status")
        WithdrawNotify notify = new WithdrawNotify()
        notify.setAmount(dataJSON.getBigDecimal("amount"))
        notify.setMerchantCode(merchant.getMerchantCode())
        notify.setMerchantId(merchant.getMerchantId())
        notify.setMerchantName(merchant.getChannelName())
        notify.setOrderId(orderId)
        if (status == 6) {
            notify.setStatus(0)
            notify.setRsp("SUCCESS")
        } else if (status == 3) {
            notify.setStatus(1)
            notify.setRsp("SUCCESS")
        } else {
            notify.setStatus(2)
        }
        return notify
    }


    BigDecimal balanceQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount

        Map<String, String> paramMap = new HashMap<>()
        paramMap.put("merchantNum", merchantAccount.getMerchantCode())
        paramMap.put("sign", MD5.md5(MD5.toAscii(paramMap) + "&key=" + merchantAccount.getPrivateKey()))

        log.info("HengxingScript_Query_Balance_Params: {}", JSON.toJSONString(paramMap))
        GlRequestHeader requestHeader =
                getRequestHeard(null, null, null, GlActionEnum.PAYMENT_QUERY_BALANCE.getCode(),merchantAccount.getChannelId(),merchantAccount.getChannelName())
        String resStr = okHttpUtil.postJSON(merchantAccount.getPayUrl() + "/api/merchant-api/user-balance/get", JSON.toJSONString(paramMap),  requestHeader)
        log.info("HengxingScript_Query_Balance_resStr: {}", resStr)

        if (StringUtils.isEmpty(resStr)) {
            return BigDecimal.ZERO
        }
        JSONObject json = JSON.parseObject(resStr)
        if (json == null || !json.getBoolean("success")) {
            return BigDecimal.ZERO
        }

        BigDecimal balance = json.getBigDecimal("data")
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