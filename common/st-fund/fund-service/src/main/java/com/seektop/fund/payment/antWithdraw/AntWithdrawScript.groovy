package com.seektop.fund.payment.antWithdraw

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
import com.seektop.common.utils.MD5
import com.seektop.enumerate.GlActionEnum
import com.seektop.exception.GlobalException
import com.seektop.fund.model.GlWithdraw
import com.seektop.fund.model.GlWithdrawMerchantAccount
import com.seektop.fund.payment.PaymentMerchantEnum
import com.seektop.fund.payment.WithdrawNotify
import com.seektop.fund.payment.WithdrawResult
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode

/**
 * 蚂蚁内充代付
 */
class AntWithdrawScript {

    private static final Logger log = LoggerFactory.getLogger(AntWithdrawScript.class)

    private OkHttpUtil okHttpUtil

    WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw

        Map<String, String> DataContentParams = new HashMap<>()
        DataContentParams.put("mch_id", merchantAccount.getMerchantCode())
        DataContentParams.put("out_trade_no", req.getOrderId())
        DataContentParams.put("amount", req.getAmount().subtract(req.getFee()).multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.DOWN).toString())
        DataContentParams.put("name", req.getName())
        DataContentParams.put("bankCardNum", req.getCardNo())
        DataContentParams.put("notify_url", merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId())
        DataContentParams.put("bank_code", req.getCardNo())
        DataContentParams.put("bank_name", req.getBankName())

        String toSign = MD5.toAscii(DataContentParams) + "&key=" + merchantAccount.getPrivateKey()
        DataContentParams.put("sign", MD5.md5(toSign).toLowerCase())
        log.info("AntWithdrawScript_Transfer_params = {}", JSON.toJSONString(DataContentParams))
        GlRequestHeader requestHeader = getRequestHeard(req.getUserId() + "", req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode())

        String url = merchantAccount.getPayUrl() + "/api/gateway/pay"

        String resStr = okHttpUtil.post(url, DataContentParams, requestHeader)
        JSONObject json = JSON.parseObject(resStr)
        log.info("AntWithdrawScript_Transfer_resStr = {}", json)

        WithdrawResult result = new WithdrawResult()
        result.setOrderId(req.getOrderId())
        result.setReqData(JSON.toJSONString(DataContentParams))
        result.setResData(resStr)

        if (json == null) {
            result.setValid(false)
            result.setMessage("API异常:请联系出款商户确认订单.")
            return result
        }

        if (json.getString("status") == "200") {
            result.setValid(true)
            result.setThirdOrderId(json.getString("order_id"))
        } else {
            result.setValid(false)
            result.setMessage(json.getString("msg"))
        }
        return result
    }

    WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        Map<String, String> resMap = args[2] as Map<String, String>
        log.info("AntWithdrawScript_Transfer_notify = {}", JSON.toJSONString(resMap))
        String orderId = resMap.get("out_trade_no");
        if (StringUtils.isEmpty(orderId)) {
            return null;
        }
        return withdrawQuery(this.okHttpUtil, merchant, orderId, args[3])
    }

    WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2]
        Map<String, String> DataContentParams = new HashMap<String, String>()
        DataContentParams.put("merchantNo", merchant.getMerchantCode())
        DataContentParams.put("orderNo", orderId)

        String toSign = MD5.toAscii(DataContentParams) + "&key=" + merchant.getPrivateKey()
        DataContentParams.put("sign", MD5.md5(toSign).toLowerCase())

        log.info("AntWithdrawScript_TransferQuery_reqMap = {}", JSON.toJSONString(DataContentParams))
        GlRequestHeader requestHeader = getRequestHeard("", "", orderId, GlActionEnum.WITHDRAW_QUERY.getCode())
        String url = merchant.getPayUrl() + "/dev/order/query"
        String resStr = okHttpUtil.post(url, DataContentParams, requestHeader)
        JSONObject json = JSON.parseObject(resStr)
        log.info("AntWithdrawScript_TransferQuery_resStr = {}", json)

        if (json == null ) {
            return null
        }
        WithdrawNotify notify = new WithdrawNotify()
        notify.setMerchantCode(merchant.getMerchantCode())
        notify.setMerchantId(merchant.getMerchantId())
        notify.setMerchantName(merchant.getChannelName())
        notify.setOrderId(orderId)
        notify.setThirdOrderId("")
        //200:支付成功   400:下单失败  100:等待出款 403:签名出错  404:查询失败   5000：支付成功    6000 退款成功
        if (json.getInteger("status") == 200 || json.getInteger("status") == 5000) {
            notify.setStatus(0)
        } else if (json.getInteger("status") == 400 || json.getInteger("status") == 403
                || json.getInteger("status") == 404 || json.getInteger("status") == 6000) {
            notify.setStatus(1)
        } else {
            notify.setStatus(2)
        }
        return notify
    }


    BigDecimal balanceQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        Map<String, String> DataContentParams = new HashMap<>()

        DataContentParams.put("merchantNo", merchantAccount.getMerchantCode())

        String toSign = MD5.toAscii(DataContentParams) + "&key=" + merchantAccount.getPrivateKey()
        DataContentParams.put("sign", MD5.md5(toSign).toUpperCase())

        log.info("AntWithdrawScript_QueryBalance_reqMap = {}", JSON.toJSONString(DataContentParams))
        GlRequestHeader requestHeader = getRequestHeard("", "", "", GlActionEnum.PAYMENT_QUERY_BALANCE.getCode())
        String url = merchantAccount.getPayUrl() + "/dev/user/query"
        String resStr = okHttpUtil.post(url, DataContentParams, requestHeader)
        JSONObject json = JSON.parseObject(resStr)
        log.info("AntWithdrawScript_QueryBalance_resStr = {}", json)
        if (null == json) {
            return BigDecimal.ZERO
        }

        if (json.getInteger("status") == 100) {
            return json.getBigDecimal("balance")
        }

        return BigDecimal.ZERO

    }

    private static GlRequestHeader getRequestHeard(String userId, String userName, String orderId, String code) {
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(code)
                .channelId(PaymentMerchantEnum.ANTWITHDRAW_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.ANTWITHDRAW_PAY.getPaymentName())
                .userId(userId)
                .userName(userName)
                .tradeId(orderId)
                .build()
        return requestHeader
    }
}