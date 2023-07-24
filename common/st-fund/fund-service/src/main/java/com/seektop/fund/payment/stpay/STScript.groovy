package com.seektop.fund.payment.stpay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.OkHttpUtil
import com.seektop.constant.FundConstant
import com.seektop.exception.GlobalException
import com.seektop.fund.business.GlFundUserlevelBusiness
import com.seektop.fund.business.GlPaymentChannelBankBusiness
import com.seektop.fund.model.*
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

class STScript {

    private static final Logger log = LoggerFactory.getLogger(STScript.class)

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
            prepareScan(merchant, payment, req, result)
        }
    }

    private void prepareScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, RechargePrepareDO req, GlRechargeResult result) {
        Map<String, String> params = new HashMap<>()
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
        log.info("STScript_prepare_params:{}", JSON.toJSONString(params))
        String resStr = getPostResult(payment.getPayUrl() + "/api/recharge/submit", sign, params, payment.getMerchantCode())
        log.info("STScript_prepare_resp:{}", resStr)

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

    RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount payment = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[4], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        log.info("STScript_notify_resp:{}", JSON.toJSONString(resMap))
        JSONObject json = JSONObject.parseObject(resMap.get("reqBody"))
        String orderId = json.getString("order_id")
        if (StringUtils.isNotEmpty(orderId)) {
            return this.payQuery(okHttpUtil, payment, orderId, args[4])
        }
        return null
    }

    RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        Map<String, String> params = new HashMap<>()
        params.put("order_id", orderId)
        String key = account.getPrivateKey()
        String sign = rsaSign(JSONObject.toJSONString(params), key, "UTF-8")
        log.info("STScript_query_params:{}", JSON.toJSONString(params))
        String resStr = getPostResult(account.getPayUrl() + "/api/recharge/status", sign, params, account.getMerchantCode())
        log.info("STScript_query_resp:{}", resStr)

        JSONObject respJson = JSONObject.parseObject(resStr)

        if (null == respJson || 1 != respJson.getInteger("code")) {
            return null
        }

        JSONObject data = respJson.getJSONObject("data")
        if (20 == data.getInteger("status")) {
            RechargeNotify pay = new RechargeNotify()
            pay.setOrderId(data.getString("order_id"))
            pay.setAmount(data.getBigDecimal("pay_amount").setScale(2, RoundingMode.DOWN))
            pay.setFee(BigDecimal.ZERO)
            pay.setThirdOrderId("")

            //针对ST补单，调整实际收款账户信息
            GlPaymentChannelBank bank = glPaymentChannelBankBusiness.getChannelBank(account.getChannelId(), data.getString("to_bank"))
            if (null != bank) {
                pay.setBankId(bank.getBankId())
                pay.setBankName(bank.getBankName())
                pay.setBankCardName(data.getString("to_card_owner"))
                pay.setBankCardNo(data.getString("to_card_num"))
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
        return true
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


    WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount account = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        this.glFundUserlevelBusiness = BaseScript.getResource(args[3], ResourceEnum.GlFundUserlevelBusiness) as GlFundUserlevelBusiness

        Map<String, String> params = new HashMap<>()
        params.put("user_id", req.getUserId().toString())
        params.put("username", req.getUsername())
        params.put("amount", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString())
        params.put("order_id", req.getOrderId())
        params.put("to_card_bank", glPaymentChannelBankBusiness.getBankCode(req.getBankId(), account.getChannelId()))
        params.put("to_card_owner", req.getName())
        params.put("to_card_num", req.getCardNo())
        if (StringUtils.isNotEmpty(req.getUserLevel())) {
            GlFundUserlevel level = glFundUserlevelBusiness.findById(Integer.valueOf(req.getUserLevel()))
            if (level != null) {
                params.put("card_group", level.getName())
            }
        }
        params.put("timestamp", System.currentTimeMillis() + "")

        String sig = rsaSign(JSONObject.toJSONString(params), account.getPrivateKey(), "UTF-8")
        log.info("STScript_transfer_params:{}", JSON.toJSONString(params))
        String resStr = this.getPostResult(account.getPayUrl() + "/api/withdraw/submit", sig, params, account.getMerchantCode())
        log.info("STScript_Transfer_resStr: {}", resStr)

        WithdrawResult result = new WithdrawResult()
        result.setOrderId(req.getOrderId())
        result.setReqData(JSON.toJSONString(params))
        result.setResData(resStr)

        JSONObject resp = JSONObject.parseObject(resStr)
        JSONObject json = resp.getJSONObject("data")
        if (json == null) {
            result.setValid(false)
            result.setMessage("API异常:请联系出款商户确认订单.")
            return result
        }
        if (resp.getInteger("code") != 1) {
            result.setValid(false)
            result.setMessage(json.getString("message"))
            return result
        }
        result.setValid(true)
        return result
    }

    WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        Map<String, String> resMap = args[2] as Map<String, String>
        JSONObject json = JSONObject.parseObject(resMap.get("reqBody"))
        String orderId = json.getString("order_id")
        if (StringUtils.isEmpty(orderId)) {
            return null
        }
        return withdrawQuery(this.okHttpUtil, merchant, orderId, args[3])
    }

    WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2]
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        Map<String, String> params = new HashMap<String, String>()
        params.put("order_id", orderId)

        String sig = rsaSign(JSONObject.toJSONString(params), merchant.getPrivateKey(), "UTF-8")
        log.info("STScript_TransferQuery_params:{}", JSON.toJSONString(params))
        String resStr = this.getPostResult(merchant.getPayUrl() + "/api/withdraw/status", sig, params, merchant.getMerchantCode())
        log.info("STScript_TransferQuery_resStr:{}", resStr)

        JSONObject resp = JSONObject.parseObject(resStr)
        if (resp == null) {
            return null
        }

        WithdrawNotify notify = new WithdrawNotify()
        if (resp.getInteger("code") == 1) {

            JSONObject dataJson = resp.getJSONObject("data")
            log.info("STScript_TransferQuery_dataJson:{}", dataJson)
            notify.setAmount(dataJson.getBigDecimal("amount"))
            notify.setMerchantCode(merchant.getMerchantCode())
            notify.setMerchantId(merchant.getMerchantId())
            notify.setMerchantName(merchant.getChannelName())
            notify.setOrderId(dataJson.getString("order_id"))
            notify.setThirdOrderId("")
            if (dataJson.getInteger("status") == 15) {
//订单状态判断标准：10:待分单  11:待执行 12:执行中 13:回执验证  14:闲置挂起 15:成功 16:失败 17:已撤销
                notify.setStatus(0)
                notify.setRemark("SUCCESS")
                notify.setOutCardName(dataJson.getString("from_user"))
                notify.setOutCardNo(dataJson.getString("from_card_num"))
                GlPaymentChannelBank channelBank = glPaymentChannelBankBusiness.getChannelBank(merchant.getChannelId(), dataJson.getString("from_bank"))
                if (null != channelBank) {
                    notify.setOutBankFlag(channelBank.getBankName())
                }
            } else if (dataJson.getInteger("status") == 16 || dataJson.getInteger("status") == 17) {
                notify.setStatus(1)
            } else {
                notify.setStatus(2)
            }
        }
        return notify
    }


    BigDecimal balanceQuery(Object[] args) throws GlobalException {
        return new BigDecimal(-1)
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