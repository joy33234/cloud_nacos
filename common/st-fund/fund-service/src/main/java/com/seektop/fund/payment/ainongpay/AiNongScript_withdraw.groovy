package com.seektop.fund.payment.ainongpay

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

/**
 * @desc  爱农支付
 * @date 2021-09-08
 * @auth joy
 */
public class AiNongScript_withdraw {


    private static final Logger log = LoggerFactory.getLogger(AiNongScript_withdraw.class)

    private OkHttpUtil okHttpUtil

    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness


    public WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount account = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

        Map<String, String> params = new LinkedHashMap<>()
        //公共参数
        params.put("access_key", account.getMerchantCode())
        params.put("charset", "utf-8")
        String stamptime = DateUtils.format(new Date(), DateUtils.YYYY_MM_DD_HH_MM_SS);
        params.put("timestamp", stamptime)
        params.put("sign_type", "md5")
        params.put("nonce", UUID.randomUUID().toString())

        //业务参数
        Map<String, String> businessParams = new LinkedHashMap<>()
        businessParams.put("order_id", req.getOrderId())
        businessParams.put("order_time", stamptime)
        businessParams.put("total_amount", req.getAmount().subtract(req.getFee()).multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.DOWN).toString())
        businessParams.put("card_no", req.getCardNo())
        businessParams.put("cardholder", req.getName())
        businessParams.put("bank_name", glPaymentChannelBankBusiness.getBankName(req.getBankId(), account.getChannelId()))
        businessParams.put("cnaps_code", "102100099996")
        businessParams.put("phone_number", "13000000001")

        params.put("biz_content", JSON.toJSONString(businessParams))
        params.put("secret",account.getPrivateKey())
        params.put("sign", MD5.md5(MD5.toAscii(params)).toUpperCase())
        params.remove("secret")

        log.info("AiNongScript_doTransfer_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                this.getRequestHeard(req.getUserId() + "", req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(), account.getChannelId(), account.getChannelName())

        String resStr = okHttpUtil.postJSON(account.getPayUrl() + "/api/v1/order/payment/create", JSON.toJSONString(params), requestHeader)
        log.info("AiNongScript_doTransfer_resp:{}", resStr)
        JSONObject json = JSON.parseObject(resStr)

        WithdrawResult result = new WithdrawResult()
        result.setOrderId(req.getOrderId())
        result.setReqData(JSON.toJSONString(params))
        result.setResData(resStr)
        if (StringUtils.isEmpty(resStr)) {
            result.setValid(false)
            result.setMessage("API异常:请联系出款商户确认订单.")
            return result
        }
        if ("0" != (json.getString("code"))) {
            result.setValid(false)
            result.setMessage(json.getString("message"))
            return result
        }
        JSONObject dataJSON = json.getJSONObject("data")
        result.setValid(true)
        result.setMessage("ok")
        return result
    }

    public WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        Map<String, String> resMap = args[2] as Map<String, String>
        log.info("AiNongScript_doTransferNotify_resp:{}", JSON.toJSONString(resMap))
        String orderId = resMap.get("orderId")
        if (StringUtils.isNotEmpty(orderId)) {
            return this.withdrawQuery(okHttpUtil, merchant, orderId, args[3])
        }
        return null
    }

    public WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount account = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2]

        Map<String, String> params = new LinkedHashMap<>()
        //公共参数
        params.put("access_key", account.getMerchantCode())
        params.put("charset", "utf-8")
        String stamptime = DateUtils.format(new Date(), DateUtils.YYYY_MM_DD_HH_MM_SS);
        params.put("timestamp", stamptime)
        params.put("sign_type", "md5")
        params.put("nonce", UUID.randomUUID().toString())

        //业务参数
        Map<String, String> businessParams = new LinkedHashMap<>()
        businessParams.put("order_id", orderId)

        params.put("biz_content", JSON.toJSONString(businessParams))
        params.put("secret",account.getPrivateKey())
        params.put("sign", MD5.md5(MD5.toAscii(params)).toUpperCase())
        params.remove("secret")

        log.info("AiNongScript_doTransferQuery_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                this.getRequestHeard("", "", orderId, GlActionEnum.WITHDRAW_QUERY.getCode(), account.getChannelId(), account.getChannelName())
        String resStr = okHttpUtil.postJSON(account.getPayUrl() + "/api/v1/order/payment/query", JSONObject.toJSONString(params), requestHeader)
        log.info("AiNongScript_doTransferQuery_resp:{}", resStr)
        if (StringUtils.isEmpty(resStr)) {
            return null
        }
        JSONObject json = JSON.parseObject(resStr)
        if ("0" != json.getString("code")) {
            return null
        }
        JSONObject dataJSON = json.getJSONObject("data")
        if (dataJSON == null) {
            return null
        }
        WithdrawNotify notify = new WithdrawNotify()
        notify.setMerchantCode(account.getMerchantCode())
        notify.setMerchantId(account.getMerchantId())
        notify.setMerchantName(account.getChannelName())
        notify.setOrderId(orderId)
        // 支付状态:status  订单状态 0:交易成功  1:交易中   -1:交易失败
        if (dataJSON.getString("status") == ("0")) {
            notify.setStatus(0)
            notify.setRsp("SUCCESS")
        } else if (dataJSON.getString("status") == "-1") {
            notify.setStatus(1)
            notify.setRsp("SUCCESS")
        } else {
            notify.setStatus(2)
        }
        return notify
    }

    public BigDecimal balanceQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount account = args[1] as GlWithdrawMerchantAccount

        Map<String, String> params = new LinkedHashMap<>()
        //公共参数
        params.put("access_key", account.getMerchantCode())
        params.put("charset", "utf-8")
        String stamptime = DateUtils.format(new Date(), DateUtils.YYYY_MM_DD_HH_MM_SS);
        params.put("timestamp", stamptime)
        params.put("sign_type", "md5")
        params.put("nonce", UUID.randomUUID().toString())

        params.put("biz_content","{}")
        params.put("secret",account.getPrivateKey())
        String toSign = MD5.toAscii(params);
        params.put("sign", MD5.md5(toSign).toUpperCase())
        params.remove("secret")

        log.info("AiNongScript_Query_Balance_Params: {}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                getRequestHeard(null, null, null, GlActionEnum.PAYMENT_QUERY_BALANCE.getCode(),account.getChannelId(),account.getChannelName())
        String resStr = okHttpUtil.postJSON(account.getPayUrl() + "/api/v1/wallet/query", JSONObject.toJSONString(params),  requestHeader)
        log.info("AiNongScript_Query_Balance_resStr: {}", resStr)

        if (StringUtils.isEmpty(resStr)) {
            return BigDecimal.ZERO
        }
        JSONObject json = JSON.parseObject(resStr)
        if ("0" != (json.getString("code"))) {
            return null
        }
        JSONObject dataJSON = json.getJSONObject("data");
        if (dataJSON == null) {
            return BigDecimal.ZERO
        }
        BigDecimal balance = dataJSON.getBigDecimal("available_balance")
        return balance == null ? BigDecimal.ZERO : balance.divide(BigDecimal.valueOf(100))
    }



    /**
     * 获取头部信息
     *
     * @param userId
     * @param userName
     * @param orderId
     * @return
     */
    private GlRequestHeader getRequestHeard(String userId, String userName, String orderId, String code, Integer channelId, String channelName) {
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