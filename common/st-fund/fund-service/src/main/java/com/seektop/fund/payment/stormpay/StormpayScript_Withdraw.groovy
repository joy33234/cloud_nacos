package com.seektop.fund.payment.stormpay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.OkHttpUtil
import com.seektop.exception.GlobalException
import com.seektop.fund.business.GlPaymentChannelBankBusiness
import com.seektop.fund.model.GlPaymentChannelBank
import com.seektop.fund.model.GlWithdraw
import com.seektop.fund.model.GlWithdrawMerchantAccount
import com.seektop.fund.payment.WithdrawNotify
import com.seektop.fund.payment.WithdrawResult
import com.seektop.fund.payment.groovy.BaseScript
import com.seektop.fund.payment.groovy.ResourceEnum
import org.apache.commons.codec.binary.Base64
import org.apache.commons.lang.StringUtils
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.apache.http.util.EntityUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.math.RoundingMode

class StormpayScript_Withdraw {

    private static final Logger log = LoggerFactory.getLogger(StormpayScript_Withdraw.class)

    private OkHttpUtil okHttpUtil

    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness

    WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

        WithdrawResult result = new WithdrawResult()
        JSONObject data = new JSONObject()
        data.put("cid", merchantAccount.getMerchantCode())
        data.put("uid", req.getUsername())
        data.put("time", req.getCreateDate().getTime() / 1000)
        data.put("amount", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN))
        data.put("order_id", req.getOrderId().substring(2, req.getOrderId().length()))
        data.put("to_bank_flag", glPaymentChannelBankBusiness.getBankCode(req.getBankId(), merchantAccount.getChannelId()))
        data.put("to_cardnumber", req.getCardNo())
        data.put("to_username", req.getName())
        data.put("location", "河南省,郑州")
        data.put("city", "11-13")

        log.info("StormPayScript_Transfer_OrderId = {} data = {}", req.getOrderId(), data.toJSONString())

        String key = merchantAccount.getPrivateKey()

        String HMAC_SHA1_ALGORITHM = "HmacSHA1"
        SecretKeySpec signingKey = new SecretKeySpec(key.getBytes(), HMAC_SHA1_ALGORITHM)
        Mac mac = Mac.getInstance(HMAC_SHA1_ALGORITHM)
        mac.init(signingKey)
        byte[] rawHmac = mac.doFinal(data.toJSONString().getBytes())
        String sign = Base64.encodeBase64String(rawHmac)

        CloseableHttpClient httpclient = HttpClients.createDefault()
        HttpPost httpPost = new HttpPost(merchantAccount.getPayUrl() + "/dsdf/api/outer_withdraw")
        httpPost.addHeader("Content-Hmac", sign)
        httpPost.addHeader("content-type", "application/json")
        httpPost.setEntity(new StringEntity(data.toJSONString(), "UTF-8"))
        CloseableHttpResponse response2 = httpclient.execute(httpPost)
        String resStr = EntityUtils.toString(response2.getEntity(), "UTF-8")
        log.info("StormPayScript_Transfer_OrderId = {}  response = {}", req.getOrderId(), resStr)

        result.setOrderId(req.getOrderId())
        result.setReqData(data.toJSONString())
        result.setResData(resStr)

        if (StringUtils.isEmpty(resStr)) {
            result.setValid(false)
            result.setMessage("API异常:请联系出款商户确认订单.")
            return result
        }
        JSONObject json = JSON.parseObject(resStr)
        if (json == null || !json.getBoolean("success")) {
            result.setValid(false)
            result.setMessage("API异常:请联系出款商户确认订单.")
            return result
        }
        result.setValid(true)
        result.setMessage("Withdraw Pending")
        return result
    }

    WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        Map<String, String> resMap = args[2] as Map<String, String>
        log.info("StormPayScript_TransferNotify_resMap = {}", JSON.toJSONString(resMap))
        String reqBody = resMap.get("reqBody")
        if (StringUtils.isEmpty(reqBody)) {
            return null
        }
        JSONObject jsonObject = JSON.parseObject(reqBody)
        String orderId = jsonObject.getString("order_id")

        if (StringUtils.isNotEmpty(orderId)) {
            return withdrawQuery(okHttpUtil, merchant, orderId, args[3])
        }
        return null
    }

    WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2]
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        try {
            JSONObject data = new JSONObject()
            data.put("cid", merchant.getMerchantCode())
            data.put("order_id", orderId)
            data.put("time", System.currentTimeMillis() / 1000)
            String key = merchant.getPrivateKey()
            String HMAC_SHA1_ALGORITHM = "HmacSHA1"
            SecretKeySpec signingKey = new SecretKeySpec(key.getBytes(), HMAC_SHA1_ALGORITHM)
            Mac mac = Mac.getInstance(HMAC_SHA1_ALGORITHM)
            mac.init(signingKey)
            byte[] rawHmac = mac.doFinal(data.toJSONString().getBytes())
            String sign = Base64.encodeBase64String(rawHmac)

            CloseableHttpClient httpclient = HttpClients.createDefault()
            HttpPost httpPost = new HttpPost(merchant.getPayUrl() + "/dsdf/api/query_withdraw")
            httpPost.addHeader("Content-Hmac", sign)
            httpPost.addHeader("content-type", "application/json")
            httpPost.setEntity(new StringEntity(data.toJSONString(), "UTF-8"))
            CloseableHttpResponse response2 = httpclient.execute(httpPost)
            log.info("StormPayScript_transferQuery_response = {}", JSON.toJSONString(response2))
            String resStr = EntityUtils.toString(response2.getEntity(), "UTF-8")
            log.info("StormPayScript_TransferQuery_OrderId = {} Result = {}", orderId, resStr)

            JSONObject resObj = JSON.parseObject(resStr)
            if (resObj.getBoolean("success") && resObj.getJSONObject("order") != null) {
                JSONObject orderObj = resObj.getJSONObject("order")
                WithdrawNotify notify = new WithdrawNotify()
                notify.setAmount(orderObj.getBigDecimal("amount"))
                notify.setMerchantCode(merchant.getMerchantCode())
                notify.setMerchantId(merchant.getMerchantId())
                notify.setMerchantName(merchant.getChannelName())
                notify.setOrderId("TX" + orderId)
                notify.setThirdOrderId(orderId)
                notify.setRemark(orderObj.getString("out_cardnumber"))//风云聚合出款卡号
                if ("verified" == orderObj.getString("status")) {
                    notify.setStatus(0)
                    notify.setRemark("SUCCESS")

                    //风云聚合出款：出款账户信息
                    notify.setOutCardName(orderObj.getString("out_cardname"))
                    notify.setOutCardNo(orderObj.getString("out_cardnumber"))

                    GlPaymentChannelBank channelBank = glPaymentChannelBankBusiness.getChannelBank(merchant.getChannelId(), orderObj.getString("out_bankflag"))
                    if (null != channelBank) {
                        notify.setOutBankFlag(channelBank.getBankName())
                    }
                } else {
                    notify.setStatus(2)
                }
                notify.setSuccessTime(new Date())
                return notify
            }
        } catch (Exception e) {
            log.error("StormPayScript_TransferQuery_Error", e)
        }
        return null
    }


    BigDecimal balanceQuery(Object[] args) throws GlobalException {
        return BigDecimal.valueOf(-1)
    }
}
