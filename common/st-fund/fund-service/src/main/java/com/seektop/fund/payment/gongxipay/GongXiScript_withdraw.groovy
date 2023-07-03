package com.seektop.fund.payment.gongxipay

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
import com.seektop.fund.payment.henglixingwxpay.Base64Util
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode

/**
 * @desc 恭喜支付
 * @date 2021-09-15
 * @auth joy
 */
public class GongXiScript_withdraw {


    private static final Logger log = LoggerFactory.getLogger(GongXiScript_withdraw.class)

    private OkHttpUtil okHttpUtil

    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness


    public WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount account = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

        Map<String, String> params = new LinkedHashMap<>()
        params.put("mch_id", account.getMerchantCode())
        params.put("order_sn", req.getOrderId())
        params.put("money", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString())
        params.put("bank_realname", req.getName())
        params.put("bank_account", req.getCardNo())
        params.put("branch_name", glPaymentChannelBankBusiness.getBankName(req.getBankId(), account.getChannelId()))
        params.put("goods_desc", "withdraw")
        params.put("time", System.currentTimeMillis().toString())
        params.put("user_ip", req.getIp())
        params.put("user_id", req.getUserId().toString())
        params.put("sign", MD5.md5(MD5.toAscii(params) + "&key=" + account.getPrivateKey()))

        String crypted = getCrypted(JSON.toJSONString(params), account.getPublicKey().replaceAll(" ",""));

        log.info("GongXiScript_doTransfer_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                this.getRequestHeard(req.getUserId() + "", req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(), account.getChannelId(), account.getChannelName())

        String resStr = okHttpUtil.postJSON(account.getPayUrl() + ":7899/?c=Pay&a=payment&crypted=" + crypted, JSON.toJSONString(params), requestHeader)
        log.info("GongXiScript_doTransfer_resp:{}", resStr)
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

        if ("1" != (json.getString("code"))) {
            result.setValid(false)
            result.setMessage(json.getString("msg"))
            return result
        }
        req.setMerchantId(account.getMerchantId())
        result.setValid(true)
        result.setMessage("ok")
        return result
    }

    public WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        Map<String, String> resMap = args[2] as Map<String, String>
        log.info("GongXiScript_doTransferNotify_resp:{}", JSON.toJSONString(resMap))
        String orderId = resMap.get("orderId")
        if (StringUtils.isEmpty(orderId)) {
            JSONObject json = JSON.parseObject(resMap.get("reqBody"));
            orderId = json.getString("orderId")
        }
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
        params.put("mch_id", merchant.getMerchantCode())
        params.put("out_order_sn", orderId)
        params.put("time", System.currentTimeMillis().toString())
        params.put("sign", MD5.md5(MD5.toAscii(params) + "&key=" + merchant.getPrivateKey()))

        String crypted = getCrypted(JSON.toJSONString(params), merchant.getPublicKey().replaceAll(" ",""));

        log.info("GongXiScript_doTransferQuery_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                this.getRequestHeard("", "", orderId, GlActionEnum.WITHDRAW_QUERY.getCode(), merchant.getChannelId(), merchant.getChannelName())
        String resStr = okHttpUtil.postJSON(merchant.getPayUrl() + ":7899/?c=Pay&a=paymentquery&crypted=" + crypted, JSONObject.toJSONString(params), requestHeader)
        log.info("GongXiScript_doTransferQuery_resp:{}", resStr)
        if (StringUtils.isEmpty(resStr)) {
            return null
        }
        JSONObject json = JSON.parseObject(resStr)
        if ("1" != (json.getString("code"))) {
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
        // 支付状态: order_state= 3 为交易成功  order_state= -1 为交易失败    其余为交易处理中
        if (dataJSON.getString("order_state") == ("3")) {
            notify.setStatus(0)
        } else if (dataJSON.getString("order_state") == ("-1")) {
            notify.setStatus(1)
        } else {
            notify.setStatus(2)
        }
        return notify
    }

    public BigDecimal balanceQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount

        Map<String, String> params = new LinkedHashMap<>()
        params.put("mch_id", merchantAccount.getMerchantCode())
        params.put("time", System.currentTimeMillis().toString())
        params.put("sign", MD5.md5(MD5.toAscii(params) + "&key=" + merchantAccount.getPrivateKey()))

        String crypted = getCrypted(JSON.toJSONString(params), merchantAccount.getPublicKey().replaceAll(" ",""));


        log.info("GongXiScript_Query_Balance_Params: {}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                getRequestHeard(null, null, null, GlActionEnum.PAYMENT_QUERY_BALANCE.getCode(),merchantAccount.getChannelId(),merchantAccount.getChannelName())
        String resStr = okHttpUtil.postJSON(merchantAccount.getPayUrl() + ":7899/?c=Pay&a=merchantquery&crypted=" + crypted, JSONObject.toJSONString(params),  requestHeader)
        log.info("GongXiScript_Query_Balance_resStr: {}", resStr)

        if (StringUtils.isEmpty(resStr)) {
            return BigDecimal.ZERO
        }
        JSONObject json = JSON.parseObject(resStr)
        if ("1" != (json.getString("code"))) {
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

    private static String getCrypted(String jsonStr, String publickey) {
        String data = Base64Util.encode(jsonStr.getBytes())
        byte[] arr = com.seektop.fund.payment.lefupay.RSAUtils.encryptByPublicKey(data.getBytes(), publickey)
        return Base64Util.encode(arr)
    }
}