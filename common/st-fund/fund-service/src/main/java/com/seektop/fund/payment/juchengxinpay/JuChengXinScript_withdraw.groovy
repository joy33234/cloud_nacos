package com.seektop.fund.payment.juchengxinpay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
import com.seektop.common.utils.DateUtils
import com.seektop.common.utils.MD5
import com.seektop.enumerate.GlActionEnum
import com.seektop.exception.GlobalException
import com.seektop.fund.model.GlWithdraw
import com.seektop.fund.model.GlWithdrawMerchantAccount
import com.seektop.fund.payment.WithdrawNotify
import com.seektop.fund.payment.WithdrawResult
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode

/**
 * 聚诚信支付
 * @auth joy
 * @date 2021-04-24
 */

class JuChengXinScript_withdraw {

    private static final Logger log = LoggerFactory.getLogger(JuChengXinScript_withdraw.class)

    private OkHttpUtil okHttpUtil


    WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw

        Map<String, String> DataContentParms = new HashMap<>()
        DataContentParms.put("oid_partner", merchantAccount.getMerchantCode())
        DataContentParms.put("no_order", req.getOrderId())
        DataContentParms.put("notify_url", merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId())
        DataContentParms.put("acct_name", req.getName())
        DataContentParms.put("bank_name", req.getBankName())
        DataContentParms.put("card_no", req.getCardNo())
        DataContentParms.put("time_order", DateUtils.format(req.getCreateDate(), DateUtils.YYYYMMDDHHMMSS))
        DataContentParms.put("money_order", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString())
        DataContentParms.put("sign_type", "MD5")

        String toSign = MD5.toAscii(DataContentParms) + merchantAccount.getPrivateKey()
        DataContentParms.put("sign", MD5.md5(toSign))

        log.info("JuChengXinScript_Transfer_params: {}", JSON.toJSONString(DataContentParms))
        GlRequestHeader requestHeader = getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(), merchantAccount.getChannelId(), merchantAccount.getChannelName())
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/gateway/pay", DataContentParms, 10L, requestHeader)
        log.info("JuChengXinScript_Transfer_resStr: {}", resStr)

        WithdrawResult result = new WithdrawResult()
        result.setOrderId(req.getOrderId())
        result.setReqData(JSON.toJSONString(DataContentParms))
        result.setResData(resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || "0000" != json.getString("ret_code")) {
            result.setValid(false)
            result.setMessage(json == null ? "API异常:请联系出款商户确认订单." : json.getString("ret_msg"))
            return result
        }
        result.setValid(true)
        result.setMessage("")
        result.setThirdOrderId(json.getString("oid_paybill"))
        return result
    }

    WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        Map<String, String> resMap = args[2] as Map<String, String>
        log.info("JuChengXinScript_WithdrawNotify_resMap:{}", JSON.toJSONString(resMap))
        JSONObject json = JSONObject.parseObject(resMap.get("reqBody"))
        String orderid = json.getString("no_order")
        if (org.apache.commons.lang.StringUtils.isNotEmpty(orderid)) {
            return withdrawQuery(okHttpUtil, merchant, orderid, args[3])
        } else {
            return null
        }
    }

    WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2]

        Map<String, String> DataContentParms = new HashMap<String, String>()
        DataContentParms.put("oid_partner", merchant.getMerchantCode())
        DataContentParms.put("no_order", orderId)
        DataContentParms.put("sign_type", "MD5")
        DataContentParms.put("time_order", DateUtils.format(new Date(), DateUtils.YYYYMMDDHHMMSS))
        String toSign = MD5.toAscii(DataContentParms) + merchant.getPrivateKey()
        DataContentParms.put("sign", MD5.md5(toSign))

        log.info("JuChengXinScript_TransferQuery_reqMap:{}", JSON.toJSONString(DataContentParms))
        GlRequestHeader requestHeader = getRequestHeard(null, null, orderId, GlActionEnum.WITHDRAW_QUERY.getCode(), merchant.getChannelId(), merchant.getChannelName())
        String resStr = okHttpUtil.post(merchant.getPayUrl() + "/gateway/pay/queryOrder", DataContentParms, 10L, requestHeader)
        log.info("JuChengXinScript_TransferQuery_resStr:{}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null) {
            return null
        }
        WithdrawNotify notify = new WithdrawNotify()
        //SUCCESS 付款成功 PROCESSING 付款处理中 CANCEL 退款
        if ("0000" == json.getString("ret_code")) {
            notify.setMerchantCode(merchant.getMerchantCode())
            notify.setMerchantId(merchant.getMerchantId())
            notify.setMerchantName(merchant.getChannelName())
            notify.setOrderId(orderId)
            notify.setThirdOrderId(json.getString("oid_paybill"))
            if (json.getString("result_pay") == "SUCCESS") {
                notify.setStatus(0)
                notify.setRsp("success")
            } else if (json.getString("result_pay") == "CANCEL") {
                notify.setStatus(1)
            } else {
                notify.setStatus(2)
            }
        }
        return notify
    }


    BigDecimal balanceQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        Map<String, String> DataContentParms = new HashMap<>()
        DataContentParms.put("oid_partner", merchantAccount.getMerchantCode())
        DataContentParms.put("time_order", DateUtils.format(new Date(), DateUtils.YYYYMMDDHHMMSS))
        DataContentParms.put("sign_type", "MD5")

        String toSign = MD5.toAscii(DataContentParms) + merchantAccount.getPrivateKey()
        DataContentParms.put("sign", MD5.md5(toSign))

        log.info("JuChengXinScript_QueryBalance_reqMap: {}", JSON.toJSONString(DataContentParms))
        GlRequestHeader requestHeader =
                getRequestHeard("", "", "", GlActionEnum.PAYMENT_QUERY_BALANCE.getCode(), merchantAccount.getChannelId(), merchantAccount.getChannelName())
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/gateway/pay/queryAmount", DataContentParms, 10L, requestHeader)
        log.info("JuChengXinScript_QueryBalance_resStr: {}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json != null) {
            return json.getBigDecimal("money") == null ? BigDecimal.ZERO : json.getBigDecimal("money")
        }
        return BigDecimal.ZERO
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