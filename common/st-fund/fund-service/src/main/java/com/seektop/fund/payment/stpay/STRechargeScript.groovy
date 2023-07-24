package com.seektop.fund.payment.stpay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.OkHttpUtil
import com.seektop.constant.FundConstant
import com.seektop.exception.GlobalException
import com.seektop.fund.business.GlFundUserlevelBusiness
import com.seektop.fund.business.GlPaymentChannelBankBusiness
import com.seektop.fund.model.GlFundUserlevel
import com.seektop.fund.model.GlPaymentChannelBank
import com.seektop.fund.model.GlPaymentMerchantApp
import com.seektop.fund.model.GlPaymentMerchantaccount
import com.seektop.fund.payment.*
import com.seektop.fund.payment.groovy.BaseScript
import com.seektop.fund.payment.groovy.ResourceEnum
import org.apache.commons.lang3.StringUtils
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.apache.http.util.EntityUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.spec.InvalidKeySpecException
import java.security.spec.PKCS8EncodedKeySpec

class STRechargeScript {

    private static final Logger log = LoggerFactory.getLogger(STRechargeScript.class)

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
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[5], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        this.glFundUserlevelBusiness = BaseScript.getResource(args[5], ResourceEnum.GlFundUserlevelBusiness) as GlFundUserlevelBusiness

        if (FundConstant.PaymentType.BANKCARD_TRANSFER == merchant.getPaymentId()) {
            prepareTransfer(merchant, payment, req, result)
        } else if (FundConstant.PaymentType.DIGITAL_PAY == merchant.getPaymentId()) {
            prepareDigi(merchant, payment, req, result)
        }


    }

    private void prepareTransfer(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, RechargePrepareDO req, GlRechargeResult result) {
        Map<String, String> params = new HashMap<>()
        params.put("type", "1")
        params.put("user_id", req.getUserId().toString())
        params.put("username", req.getUsername())
        params.put("amount", req.getAmount().toString())
        params.put("order_id", req.getOrderId())
        params.put("pay_bank", glPaymentChannelBankBusiness.getBankCode(req.getBankId(), payment.getChannelId()))
        params.put("pay_user", req.getKeyword().split("\\|\\|")[0])
        params.put("remark", req.getKeyword().split("\\|\\|")[1])
        if (StringUtils.isNotEmpty(req.getUserLevel())) {
            GlFundUserlevel level = glFundUserlevelBusiness.findById(Integer.valueOf(req.getUserLevel()))
            if (level != null) {
                params.put("card_group", level.getName())
            }
        }
        params.put("timestamp", System.currentTimeMillis() + "")
        String key = payment.getPrivateKey()
        String sign = rsaSign(JSONObject.toJSONString(params), key, "UTF-8")
        log.info("STScript_prepare_params = {}", JSON.toJSONString(params))
        String resStr = getPostResult(payment.getPayUrl() + "/api/recharge/submit", sign, params, payment.getMerchantCode())
        log.info("STScript_prepare_resp = {}", resStr)

        JSONObject resp = JSONObject.parseObject(resStr)
        if (null == resp) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("网络请求超时，稍后重试")
            return
        }
        if (1 != resp.getInteger("code")) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg(resp.getString("message"))
            return
        }

        JSONObject data = resp.getJSONObject("data")
        result.setErrorCode(FundConstant.RechargeErrorCode.NORMAL)
        if (data != null) {
            GlPaymentChannelBank bank = glPaymentChannelBankBusiness.getChannelBank(merchant.getChannelId(), data.getString("card_bank"))

            BankInfo bankInfo = new BankInfo();
            bankInfo.setName(data.getString("card_owner"))
            bankInfo.setBankId(bank.getBankId())
            bankInfo.setBankName(bank.getBankName())
            bankInfo.setBankBranchName(data.getString("card_branch"))
            bankInfo.setCardNo(data.getString("card_num"))
            bankInfo.setKeyword(req.getKeyword().split("\\|\\|")[1])
            result.setBankInfo(bankInfo)
        }
    }

    private void prepareDigi(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, RechargePrepareDO req, GlRechargeResult result) {
        Map<String, String> params = new HashMap<>()
        params.put("type", "2")
        params.put("protocol", req.getProtocol())
        params.put("user_id", req.getUserId().toString())
        params.put("username", req.getUsername())
        params.put("amount", req.getAmount().toString())
        params.put("order_id", req.getOrderId())

        if (StringUtils.isNotEmpty(req.getUserLevel())) {
            GlFundUserlevel level = glFundUserlevelBusiness.findById(Integer.valueOf(req.getUserLevel()))
            if (level != null) {
                params.put("card_group", level.getName())
            }
        }
        params.put("timestamp", System.currentTimeMillis() + "")
        String key = payment.getPrivateKey()
        String sign = rsaSign(JSONObject.toJSONString(params), key, "UTF-8")
        log.info("STScript_prepare_digi_params = {}", JSON.toJSONString(params))
        String resStr = getPostResult(payment.getPayUrl() + "/api/recharge/submit", sign, params, payment.getMerchantCode())
        log.info("STScript_prepare_diig_resp = {}", resStr)

        JSONObject resp = JSONObject.parseObject(resStr)
        if (null == resp) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("网络请求超时，稍后重试")
            return
        }
        if (1 != resp.getInteger("code")) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg(resp.getString("message"))
            return
        }

        JSONObject data = resp.getJSONObject("data")
        result.setErrorCode(FundConstant.RechargeErrorCode.NORMAL)
        if (data != null) {
            BlockInfo blockInfo = new BlockInfo()

            blockInfo.setDigitalAmount(data.getBigDecimal("amount_digi"))
            blockInfo.setProtocol(req.getProtocol())
            blockInfo.setBlockAddress(data.getString("card_num"))
            //USDT数量
            BigDecimal rate = req.getAmount().divide(data.getBigDecimal("amount_digi"), 2, RoundingMode.DOWN)
            blockInfo.setRate(rate)
            result.setBlockInfo(blockInfo)
        }
    }

    RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount payment = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[4], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        log.info("STScript_notify_resp:{}", JSON.toJSONString(resMap))
        JSONObject json = JSONObject.parseObject(resMap.get("reqBody"))
        String orderId = json.getString("order_id")
        String payDigitalAmount = json.getBigDecimal("amount_digi")
        if (null != orderId && "" != orderId) {
            return this.payQuery(okHttpUtil, payment, orderId, args[4], payDigitalAmount)
        }
        return null
    }

    RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        BigDecimal payDigitalAmount = args[4] as BigDecimal

        Map<String, String> params = new HashMap<>()
        params.put("order_id", orderId)
        String key = account.getPrivateKey()
        String sign = rsaSign(JSONObject.toJSONString(params), key, "UTF-8")
        log.info("STScript_query_params:{}", JSON.toJSONString(params))
        String resStr = getPostResult(account.getPayUrl() + "/api/recharge/status", sign, params, account.getMerchantCode())
        log.info("STScript_query_resp:{}", resStr)
        if (StringUtils.isEmpty(resStr) || 1 != JSONObject.parseObject(resStr).getInteger("code")) {
            return null
        }
        JSONObject resp = JSONObject.parseObject(resStr)
        JSONObject data = resp.getJSONObject("data")
        if ("20" == data.getString("status")) {
            RechargeNotify pay = new RechargeNotify()
            pay.setOrderId(data.getString("order_id"))
            pay.setAmount(data.getBigDecimal("pay_amount").setScale(2, RoundingMode.DOWN))
            pay.setFee(BigDecimal.ZERO)
            pay.setThirdOrderId("")

            //针对ST补单，调整实际收款账户信息
            if (StringUtils.isNotEmpty(data.getString("to_bank"))) {
                GlPaymentChannelBank bank = glPaymentChannelBankBusiness.getChannelBank(account.getChannelId(), data.getString("to_bank"))
                if (null != bank) {
                    pay.setBankId(bank.getBankId())
                    pay.setBankName(bank.getBankName())
                    pay.setBankCardName(data.getString("to_card_owner"))
                    pay.setBankCardNo(data.getString("to_card_num"))
                }
            }
            pay.setPayDigitalAmount(payDigitalAmount)
            if (StringUtils.isNotEmpty(data.getString("from_card_num"))) {
                pay.setPayAddress(data.getString("from_card_num")+"||")
            }
            return pay
        }
        return null
    }


    void cancel(Object[] args) throws GlobalException {
        GlPaymentMerchantaccount payment = args[2] as GlPaymentMerchantaccount
        RechargePrepareDO req = args[3] as RechargePrepareDO
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[5], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        Map<String, String> params = new HashMap<>()
        params.put("order_id", req.getOrderId())
        String key = payment.getPrivateKey()
        String sign = rsaSign(JSONObject.toJSONString(params), key, "UTF-8")
        log.info("STScript_cancel_params:{}", JSON.toJSONString(params))
        String resp = getPostResult(payment.getPayUrl() + "/api/recharge/cancel", sign, params, payment.getMerchantCode())
        log.info("STScript_cancel_resp:{}", resp)
    }

    /**
     * 是否为内部渠道
     *
     * @param args
     * @return
     */
    public boolean innerpay(Object[] args) {
        return true
    }

    /**
     * 根据支付方式判断-转帐是否需要实名
     *
     * @param args
     * @return
     */
    public boolean needName(Object[] args) {
        Integer paymentId = args[1] as Integer
        if (paymentId == FundConstant.PaymentType.BANKCARD_TRANSFER) {
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
        return FundConstant.ShowType.DETAIL
    }

    /**
     * 充值USDT汇率
     *
     * @param args
     * @return
     */
    public BigDecimal paymentRate(Object[] args) {
        return null
    }

    private String getPostResult(String url, String sign, Map<String, String> map, String merchantCode) {
        try {
            CloseableHttpClient httpclient = HttpClients.createDefault()
            HttpPost httpPost = new HttpPost(url)
            httpPost.addHeader("X-ST-ID", merchantCode)
            httpPost.addHeader("X-ST-SIG", sign)
            httpPost.addHeader("Content-Type", "application/json")
            httpPost.addHeader("Authorization", "Basic c2Vla3RvcDpzdDIwMTk=")
            httpPost.setEntity(new StringEntity(JSONObject.toJSONString(map), "UTF-8"))
            CloseableHttpResponse response = httpclient.execute(httpPost)
            return EntityUtils.toString(response.getEntity(), "UTF-8")
        } catch (IOException e) {
            e.printStackTrace()
        }
        return null
    }

    // 获取私钥
    private static PrivateKey getPrivateKeyFromPKCS8(byte[] data) throws Exception {
        KeyFactory keyFactory = KeyFactory.getInstance("RSA")
        byte[] encodedKey = org.apache.commons.net.util.Base64.decodeBase64(data)
        return keyFactory.generatePrivate(new PKCS8EncodedKeySpec(encodedKey))
    }

    // 生成签名
    static String rsaSign(String content, String privateKey, String charset) {
        try {
            PrivateKey priKey = getPrivateKeyFromPKCS8(privateKey.getBytes())
            java.security.Signature signature = java.security.Signature.getInstance("SHA1WithRSA")
            signature.initSign(priKey)
            signature.update(content.getBytes(charset))
            byte[] signed = signature.sign()
            return new String(org.apache.commons.net.util.Base64.encodeBase64(signed))
        } catch (InvalidKeySpecException ie) {
            log.warn("RSA私钥格式不正确", ie)
        } catch (Exception e) {
            log.warn("RSA签名失败:", e)
        }
        return ""
    }
}