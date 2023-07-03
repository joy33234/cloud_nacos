package com.seektop.fund.payment.stpay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.OkHttpUtil
import com.seektop.constant.FundConstant
import com.seektop.exception.GlobalException
import com.seektop.fund.business.GlFundUserlevelBusiness
import com.seektop.fund.business.GlPaymentChannelBankBusiness
import com.seektop.fund.business.withdraw.GlWithdrawReceiveInfoBusiness
import com.seektop.fund.model.*
import com.seektop.fund.payment.WithdrawNotify
import com.seektop.fund.payment.WithdrawResult
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

class STWithdrawScript {

    private static final Logger log = LoggerFactory.getLogger(STWithdrawScript.class)

    private OkHttpUtil okHttpUtil

    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness

    private GlFundUserlevelBusiness glFundUserlevelBusiness

    private GlWithdrawReceiveInfoBusiness glWithdrawReceiveInfoBusiness


    WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount account = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        this.glFundUserlevelBusiness = BaseScript.getResource(args[3], ResourceEnum.GlFundUserlevelBusiness) as GlFundUserlevelBusiness

        BigDecimal amount = req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN);
        Map<String, String> params = new HashMap<>()

        if (req.getBankId() == FundConstant.PaymentType.DIGITAL_PAY && req.getBankName().contains("USDT")) {
            //USDT 提现
            params.put("type", "2")
            //钱包地址
            params.put("to_card_num", req.getAddress())
            //链类型
            params.put("to_card_owner", req.getCardNo())
            //币种编码
            params.put("to_card_bank", "USDT")
        } else {
            //银行卡提现
            params.put("type", "1")
            //收款卡号
            params.put("to_card_num", req.getCardNo())
            //收款人姓名
            params.put("to_card_owner", req.getName())
            //收款银行卡编码
            params.put("to_card_bank", glPaymentChannelBankBusiness.getBankCode(req.getBankId(), account.getChannelId()))

        }

        params.put("user_id", req.getUserId().toString())
        params.put("username", req.getUsername())
        params.put("user_type", req.getUserType().toString())
        params.put("amount", amount.toString())
        params.put("order_id", req.getOrderId())


        if (StringUtils.isNotEmpty(req.getUserLevel())) {
            GlFundUserlevel level = glFundUserlevelBusiness.findById(Integer.valueOf(req.getUserLevel()))
            if (level != null) {
                params.put("card_group", level.getName())
            }
        }
        params.put("timestamp", System.currentTimeMillis() + "")

        String sig = rsaSign(JSONObject.toJSONString(params), account.getPrivateKey(), "UTF-8")
        log.info("STScript_transfer_params = {}", JSON.toJSONString(params))
        String resStr = this.getPostResult(account.getPayUrl() + "/api/withdraw/submit", sig, params, account.getMerchantCode())

        JSONObject resp = JSONObject.parseObject(resStr)
        log.info("STScript_Transfer_resStr = {}", resStr)

        WithdrawResult result = new WithdrawResult()
        result.setOrderId(req.getOrderId())
        result.setReqData(JSON.toJSONString(params))
        result.setResData(resStr)

        BigDecimal rate = resp.getJSONObject("data").getBigDecimal("rate")
        BigDecimal usdtAmount = resp.getJSONObject("data").getBigDecimal("amount_digi")
        if (rate != null && usdtAmount != null) {
            result.setRate(rate)
            result.setUsdtAmount(usdtAmount.setScale(4,RoundingMode.DOWN))
        }

        if (resp == null) {
            result.setValid(false)
            result.setMessage("API异常:请联系出款商户确认订单.")
            return result
        }

        if (resp.getInteger("code") != 1) {
            result.setValid(false)
            result.setMessage(resp.getString("message"))
            return result
        }
        result.setValid(true)
        log.info(JSON.toJSONString(result))
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
        this.glWithdrawReceiveInfoBusiness = BaseScript.getResource(args[3], ResourceEnum.GlWithdrawReceiveInfoBusiness) as GlWithdrawReceiveInfoBusiness
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
                GlWithdrawReceiveInfo receiveInfo = glWithdrawReceiveInfoBusiness.findById(orderId)
                if (receiveInfo != null) {
                    notify.setActualRate(receiveInfo.getRate())
                    notify.setActualAmount(receiveInfo.getUsdtAmount())
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
        return new BigDecimal(9999999)
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