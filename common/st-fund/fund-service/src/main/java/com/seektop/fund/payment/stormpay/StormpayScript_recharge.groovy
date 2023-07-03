package com.seektop.fund.payment.stormpay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.OkHttpUtil
import com.seektop.constant.FundConstant
import com.seektop.exception.GlobalException
import com.seektop.fund.business.GlFundUserlevelBusiness
import com.seektop.fund.business.GlPaymentChannelBankBusiness
import com.seektop.fund.model.*
import com.seektop.fund.payment.*
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

class StormpayScript_recharge {

    private static final Logger log = LoggerFactory.getLogger(StormpayScript_recharge.class)

    private OkHttpUtil okHttpUtil

    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness

    private GlFundUserlevelBusiness glFundUserlevelBusiness

    RechargeNotify result(Object[] args) throws GlobalException {
        return notify(args)
    }

    void pay(Object[] args) {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantApp merchant = args[1] as GlPaymentMerchantApp
        GlPaymentMerchantaccount payment = args[2] as GlPaymentMerchantaccount
        RechargePrepareDO req = args[3] as RechargePrepareDO
        GlRechargeResult result = args[4] as GlRechargeResult
        this.glPaymentChannelBankBusiness = com.seektop.fund.payment.groovy.BaseScript.getResource(args[5], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        this.glFundUserlevelBusiness = com.seektop.fund.payment.groovy.BaseScript.getResource(args[5], ResourceEnum.GlFundUserlevelBusiness) as GlFundUserlevelBusiness
        if (FundConstant.PaymentType.BANKCARD_TRANSFER == merchant.getPaymentId()
                || FundConstant.PaymentType.ALI_TRANSFER == merchant.getPaymentId()) {
            prepareScan(merchant, payment, req, result)
        }

    }

    private void prepareScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, RechargePrepareDO req, GlRechargeResult result) {
        try {
            JSONObject data = new JSONObject()
            data.put("cid", payment.getMerchantCode())
            data.put("uid", req.getUsername())
            data.put("time", req.getCreateDate().getTime() / 1000)
            data.put("amount", req.getAmount().setScale(2, RoundingMode.DOWN))
            data.put("order_id", req.getOrderId())
            data.put("category", "remit")

            //分组标识
            if (StringUtils.isNotEmpty(req.getUserLevel())) {
                GlFundUserlevel level = glFundUserlevelBusiness.findById(Integer.valueOf(req.getUserLevel()))
                if (level != null) {
                    data.put("gflag", level.getName())
                }
            }
            if (FundConstant.PaymentType.ALI_TRANSFER == merchant.getPaymentId()) {
                //支付宝转银行卡
                data.put("from_bank_flag", "ALIPAY")
            } else {
                if (null == req.getBankId()) {
                    result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
                    result.setErrorMsg("请选择付款银行名称")
                    return
                }
                String bankCode = glPaymentChannelBankBusiness.getBankCode(req.getBankId(), payment.getChannelId())
                // 银行卡转账
                data.put("from_bank_flag", bankCode)
            }

            data.put("from_username", req.getKeyword().split("\\|\\|")[0])
            data.put("comment", req.getKeyword().split("\\|\\|")[1])

            String key = payment.getPrivateKey()
            String HMAC_SHA1_ALGORITHM = "HmacSHA1"
            SecretKeySpec signingKey = new SecretKeySpec(key.getBytes(), HMAC_SHA1_ALGORITHM)
            Mac mac = Mac.getInstance(HMAC_SHA1_ALGORITHM)
            mac.init(signingKey)
            byte[] rawHmac = mac.doFinal(data.toJSONString().getBytes())
            String sign = Base64.encodeBase64String(rawHmac)
            log.info("StormPayScript_Prepare_data:{}", data.toJSONString())

            CloseableHttpClient httpclient = HttpClients.createDefault()
            HttpPost httpPost = new HttpPost(payment.getPayUrl() + "/dsdf/api/place_order")
            httpPost.addHeader("Content-Hmac", sign)
            httpPost.addHeader("content-type", "application/json")
            httpPost.setEntity(new StringEntity(data.toJSONString(), "UTF-8"))
            CloseableHttpResponse response2 = httpclient.execute(httpPost)
            String resStr = EntityUtils.toString(response2.getEntity(), "UTF-8")
            log.info("StormPayScript_Response_resStr:{},{}", req.getOrderId(), resStr)
            JSONObject resObj = JSON.parseObject(resStr);
            if (null == resObj) {
                result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
                result.setErrorMsg("网络请求超时，稍后重试")
                return
            }

            if (resObj.getBoolean("success") == false) {
                result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
                result.setErrorMsg(resObj.getString("msg"))
                return
            }

            JSONObject dataObj = resObj.getJSONObject("data")
            if (null == dataObj) {
                result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
                result.setErrorMsg("请尝试其他金额或其他支付方式")
                return
            }

            JSONObject cardObj = dataObj.getJSONObject("card")
            if (cardObj == null) {
                result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
                result.setErrorMsg("请尝试其他金额或其他支付方式")
                return
            }

            String bankflag = cardObj.getString("bankflag")
            GlPaymentChannelBank bank = glPaymentChannelBankBusiness.getChannelBank(payment.getChannelId(), bankflag)
            result.setErrorCode(FundConstant.RechargeErrorCode.NORMAL)
            BankInfo bankInfo = new BankInfo()
            bankInfo.setName(cardObj.getString("cardname"))
            if (null != bank) {
                bankInfo.setBankId(bank.getBankId())
                bankInfo.setBankName(bank.getBankName())
            }
            bankInfo.setBankBranchName(cardObj.getString("location"))
            bankInfo.setCardNo(cardObj.getString("cardnumber"))
            bankInfo.setKeyword(req.getKeyword().split("\\|\\|")[1])
            result.setBankInfo(bankInfo)
        } catch (Exception e) {
            e.printStackTrace()
            log.error("StormPayScript_Prepare_error", e)
        }
    }

    RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount payment = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>
        this.glPaymentChannelBankBusiness = com.seektop.fund.payment.groovy.BaseScript.getResource(args[4], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

        String reqBody = resMap.get("reqBody")
        if (StringUtils.isEmpty(reqBody)) {
            return null
        }
        log.info("StormPayScript_Notify_resMap_reqBody: {}", reqBody)
        JSONObject jsonObject = JSON.parseObject(reqBody)
        String orderId = jsonObject.getString("order_id")
        String status = jsonObject.getString("status")
        if (StringUtils.isEmpty(orderId) || StringUtils.isEmpty(status) || status != "verified") {
            return null
        }
        return payQuery(okHttpUtil, payment, orderId, args[4])
    }

    RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount payment = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String

        try {
            JSONObject data = new JSONObject()
            data.put("cid", payment.getMerchantCode())
            data.put("order_id", orderId)
            data.put("time", System.currentTimeMillis() / 1000)

            String key = payment.getPrivateKey()
            String HMAC_SHA1_ALGORITHM = "HmacSHA1"
            SecretKeySpec signingKey = new SecretKeySpec(key.getBytes(), HMAC_SHA1_ALGORITHM)
            Mac mac = Mac.getInstance(HMAC_SHA1_ALGORITHM)
            mac.init(signingKey)
            byte[] rawHmac = mac.doFinal(data.toJSONString().getBytes())
            String sign = Base64.encodeBase64String(rawHmac)

            CloseableHttpClient httpclient = HttpClients.createDefault()
            HttpPost httpPost = new HttpPost(payment.getPayUrl() + "/dsdf/api/query_order")
            httpPost.addHeader("Content-Hmac", sign)
            httpPost.addHeader("content-type", "application/json")
            httpPost.setEntity(new StringEntity(data.toJSONString(), "UTF-8"))
            CloseableHttpResponse response2 = httpclient.execute(httpPost)
            String resStr = EntityUtils.toString(response2.getEntity(), "UTF-8")
            log.info("StormPayScript_Query_resStr: {},:{}", orderId, resStr)

            JSONObject resObj = JSON.parseObject(resStr)
            if (resObj.getBoolean("success") && resObj.getJSONObject("order") != null) {
                JSONObject orderObj = resObj.getJSONObject("order")
                if ("verified" != orderObj.getString("status")) {
                    return null
                }
                RechargeNotify pay = new RechargeNotify()
                pay.setOrderId(orderObj.getString("order_id"))
                pay.setAmount(orderObj.getBigDecimal("amount"))
                pay.setFee(BigDecimal.ZERO)
                pay.setThirdOrderId(orderObj.getString("mer_order_id"))
                return pay
            }
        } catch (Exception e) {
            log.error("StormPayScript payer error", e)
        }
        return null
    }

    /**
     * 是否为内部渠道
     *
     * @param args
     * @return
     */
    public boolean innerpay(Object[] args) {
        Integer paymentId = args[1] as Integer
        if (paymentId == FundConstant.PaymentType.BANKCARD_TRANSFER
                || paymentId == FundConstant.PaymentType.ALI_TRANSFER) {
            return true
        }
        return false
    }

    /**
     * 根据支付方式判断-转帐是否需要实名
     *
     * @param args
     * @return
     */
    public boolean needName(Object[] args) {
        Integer paymentId = args[1] as Integer
        if (paymentId == FundConstant.PaymentType.BANKCARD_TRANSFER
                || paymentId == FundConstant.PaymentType.ALI_TRANSFER) {
            return true
        }
        return false
    }

    /**
     * 充值是否需要卡号
     *
     * @param args
     * @return
     */
    public boolean needCard(Object[] args) {
        return false
    }

    /**
     * 是否显示充值订单祥情
     *
     * @param args
     * @return
     */
    public Integer showType(Object[] args) {
        Integer paymentId = args[1] as Integer
        if (paymentId == FundConstant.PaymentType.BANKCARD_TRANSFER
                || paymentId == FundConstant.PaymentType.ALI_TRANSFER) {
            return FundConstant.ShowType.DETAIL
        }
        return FundConstant.ShowType.NORMAL
    }

    void cancel(Object[] args) throws GlobalException {
        GlPaymentMerchantaccount payment = args[1] as GlPaymentMerchantaccount
        GlRecharge req = args[2] as GlRecharge
        try {
            JSONObject data = new JSONObject()
            data.put("cid", payment.getMerchantCode())
            data.put("order_id", req.getOrderId())
            data.put("time", System.currentTimeMillis() / 1000)
            String key = payment.getPrivateKey()
            String HMAC_SHA1_ALGORITHM = "HmacSHA1"
            SecretKeySpec signingKey = new SecretKeySpec(key.getBytes(), HMAC_SHA1_ALGORITHM)
            Mac mac = Mac.getInstance(HMAC_SHA1_ALGORITHM)
            mac.init(signingKey)
            byte[] rawHmac = mac.doFinal(data.toJSONString().getBytes())
            String sign = Base64.encodeBase64String(rawHmac)

            CloseableHttpClient httpclient = HttpClients.createDefault()
            HttpPost httpPost = new HttpPost(payment.getPayUrl() + "/dsdf/api/revoke_order")
            httpPost.addHeader("Content-Hmac", sign)
            httpPost.addHeader("content-type", "application/json")
            httpPost.setEntity(new StringEntity(data.toJSONString(), "UTF-8"))
            CloseableHttpResponse response2 = httpclient.execute(httpPost)
            String resStr = EntityUtils.toString(response2.getEntity(), "UTF-8")
            log.info("StormPayScript_Cancel_resStr: {},{}", req.getOrderId(), resStr)

        } catch (Exception e) {
            log.error("StormPayScript_Cancel_Error", e)
        }
    }

    WithdrawResult withdraw(Object[] args) throws GlobalException {
        return null
    }

    WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        return null
    }

    WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        return null
    }


    BigDecimal balanceQuery(Object[] args) throws GlobalException {
        return BigDecimal.ZERO
    }
}
