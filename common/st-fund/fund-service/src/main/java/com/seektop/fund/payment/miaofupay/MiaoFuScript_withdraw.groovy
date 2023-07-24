package com.seektop.fund.payment.miaofupay

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
import org.apache.commons.codec.digest.HmacUtils
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode

/**
 * @desc 淼富支付
 * @date 2021-09-12
 * @auth joy
 */
public class MiaoFuScript_withdraw {


    private static final Logger log = LoggerFactory.getLogger(MiaoFuScript_withdraw.class)

    private OkHttpUtil okHttpUtil

    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness


    public WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount account = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

        Map<String, String> params = new LinkedHashMap<>()
        params.put("mid", account.getMerchantCode())
        params.put("time", System.currentTimeSeconds().toString())
        params.put("amount", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString())
        params.put("currency", "CNY")
        params.put("type", "transfer")
        params.put("order_no", req.getOrderId())
        params.put("ip", req.getIp())
        params.put("bank_code", glPaymentChannelBankBusiness.getBankCode(req.getBankId(), account.getChannelId()))
        params.put("card_no", req.getCardNo())
        params.put("holder_name", req.getName())
        params.put("bank_province", "上海市")
        params.put("bank_city", "上海市")
        params.put("bank_branch", "上海市")
        params.put("notify_url", account.getNotifyUrl() + account.getMerchantId())

        params.put("sign", sign(account.getPrivateKey(), MD5.toAscii(params)))

        Map<String, String> headParams = new HashMap<String, String>()
        headParams.put("Authorization", "api-key " + account.getPublicKey())
        headParams.put("content-type", "application/json")


        log.info("MiaoFuScript_doTransfer_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                this.getRequestHeard(req.getUserId() + "", req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(), account.getChannelId(), account.getChannelName())

        String resStr = okHttpUtil.postJSON(account.getPayUrl() + "/api/v1/transfers", JSON.toJSONString(params), headParams, requestHeader)
        log.info("MiaoFuScript_doTransfer_resp:{}", resStr)
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

        if ("200" != (json.getString("code"))) {
            result.setValid(false)
            result.setMessage(json.getString("message"))
            return result
        }
        result.setValid(true)
        result.setMessage("ok")
        return result
    }

    public WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        Map<String, String> resMap = args[2] as Map<String, String>
        log.info("MiaoFuScript_doTransferNotify_resp:{}", JSON.toJSONString(resMap))
        JSONObject json = JSON.parseObject(resMap.get("reqBody"));
        String orderId = json.getJSONObject("data").getString("order_no")
        if (StringUtils.isNotEmpty(orderId)) {
            return this.withdrawQuery(okHttpUtil, merchant, orderId, args[3])
        }
        return null
    }

    public WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2]


        Map<String, String> params = new LinkedHashMap<>()
        params.put("mid", merchant.getMerchantCode())
        params.put("time", System.currentTimeSeconds().toString())
        params.put("order_no",orderId)
        params.put("sign", sign(merchant.getPrivateKey(), MD5.toAscii(params)))

        Map<String, String> headParams = new HashMap<String, String>()
        headParams.put("Authorization", "api-key " + merchant.getPublicKey())
        headParams.put("content-type", "application/json")

        log.info("MiaoFuScript_doTransferQuery_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                this.getRequestHeard("", "", orderId, GlActionEnum.WITHDRAW_QUERY.getCode(), merchant.getChannelId(), merchant.getChannelName())
        String resStr = okHttpUtil.postJSON(merchant.getPayUrl() + "/api/v1/transfers/query", JSONObject.toJSONString(params), headParams, requestHeader)
        log.info("MiaoFuScript_doTransferQuery_resp:{}", resStr)
        if (StringUtils.isEmpty(resStr)) {
            return null
        }
        JSONObject json = JSON.parseObject(resStr)
        if ("200" != (json.getString("code"))) {
            return null
        }
        JSONObject dataJSON = json.getJSONObject("data")
        if (dataJSON == null) {
            return null
        }
        WithdrawNotify notify = new WithdrawNotify()
        notify.setMerchantCode(merchant.getMerchantCode())
        notify.setMerchantId(merchant.getMerchantId())
        notify.setMerchantName(merchant.getChannelName())
        notify.setOrderId(orderId)
        notify.setThirdOrderId(dataJSON.getString("no"))
        // 支付状态:status  支付中:inprogress  成功:succeeded  失败:failed  超时过期:expired
        if (dataJSON.getString("status") == ("succeeded")) {
            notify.setStatus(0)
            notify.setRsp("ok")
        } else if (dataJSON.getString("status") == ("failed")) {
            notify.setStatus(1)
            notify.setRsp("ok")
        } else {
            notify.setStatus(2)
        }
        return notify
    }

    public BigDecimal balanceQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount

        Map<String, String> params = new LinkedHashMap<>()
        params.put("mid", merchant.getMerchantCode())
        params.put("time", System.currentTimeSeconds().toString())
        params.put("sign", sign(merchant.getPrivateKey(), MD5.toAscii(params)))

        Map<String, String> headParams = new HashMap<String, String>()
        headParams.put("Authorization", "api-key " + merchant.getPublicKey())
        headParams.put("content-type", "application/json")

        log.info("MiaoFuScript_Query_Balance_Params: {}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                getRequestHeard(null, null, null, GlActionEnum.PAYMENT_QUERY_BALANCE.getCode(),merchant.getChannelId(),merchant.getChannelName())
        String resStr = okHttpUtil.postJSON(merchant.getPayUrl() + "/api/v1/balance", JSONObject.toJSONString(params), headParams, requestHeader)
        log.info("MiaoFuScript_Query_Balance_resStr: {}", resStr)

        if (StringUtils.isEmpty(resStr)) {
            return BigDecimal.ZERO
        }
        JSONObject json = JSON.parseObject(resStr)
        if ("200" != (json.getString("code"))) {
            return null
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

    public String sign (String secretKey, String data) {
        // 利用 apache 工具类 HmacUtils
        byte[] bytes = HmacUtils.hmacSha1(secretKey, data);
        return Base64.getEncoder().encodeToString(bytes);
    }
}