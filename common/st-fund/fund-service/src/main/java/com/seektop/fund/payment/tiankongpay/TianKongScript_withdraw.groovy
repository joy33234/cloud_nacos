package com.seektop.fund.payment.tiankongpay

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
import com.seektop.fund.payment.groovy.ResourceEnum
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.lang3.ObjectUtils
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import java.math.RoundingMode
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec

import static com.seektop.fund.payment.groovy.BaseScript.getResource
/**
 * @desc 天空支付
 * @auth Otto
 * @date 2022-03-15
 */

class TianKongScript_withdraw {

    private static final Logger log = LoggerFactory.getLogger(TianKongScript_withdraw.class)

    private static final String SERVER_WITHDRAW_URL = "/merchant/withdraw"
    private static final String SERVER_QUERY_URL = "/merchant/order/withdraw"
    private static final String SERVER_BALANCE_URL = "/merchant/balance"
    private static final String SERVER_TOKEN_URL = "/merchant/token"  //取得token
    private static final String RANDOM_CODE_URL = "/merchant/random_code" //取得 random code

    private OkHttpUtil okHttpUtil
    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness

    WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        this.glPaymentChannelBankBusiness = getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

        //下单需将token 放入 header
        String randomCode  = getRandomCode(merchantAccount.getMerchantCode() ,merchantAccount.getPayUrl());
        String token = getToken(randomCode ,merchantAccount.getMerchantCode(),merchantAccount.getPublicKey() ,merchantAccount.getPayUrl())

        Map<String, String> headerMap = new HashMap<>()
        headerMap.put("Authorization", token)

        Map<String,String> params = new HashMap<>()
        params.put("user_id", merchantAccount.getMerchantCode())
        params.put("order_id", req.getOrderId())
        params.put("amount", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString())
        params.put("currency", "CNY")
        params.put("channel", "bank2bank")
        params.put("card_no", req.getCardNo())
        params.put("card_name", req.getName())
        params.put("card_type", "1")
        params.put("bank_code", glPaymentChannelBankBusiness.getBankCode(req.getBankId(), merchantAccount.getChannelId()))
        params.put("bank_name", glPaymentChannelBankBusiness.getBankName(req.getBankId(), merchantAccount.getChannelId()))
        params.put("bank_branch", "支行")
        params.put("bank_province", "北京")
        params.put("bank_city", "北京")
        params.put("callback_url", merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId())
        params.put("timestamp", System.currentTimeSeconds().toString())
        params.put("sign", genSign(MD5.toAscii(params) + "&" + merchantAccount.getPublicKey(), merchantAccount.getPrivateKey()))

        log.info("TianKongScript_Transfer_params: {} , url:{} ", JSON.toJSONString(params) , merchantAccount.getPayUrl())
        GlRequestHeader requestHeader = getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(), merchantAccount.getChannelId(), merchantAccount.getChannelName())

        String aesString = aes256Encode(new JSONObject(params).toString(),merchantAccount.getPublicKey())
        String resStr = okHttpUtil.postText(merchantAccount.getPayUrl() + SERVER_WITHDRAW_URL, aesString, headerMap,requestHeader)
        log.info("TianKongScript_Transfer_resStr: {}", resStr)

        WithdrawResult result = new WithdrawResult()
        result.setOrderId(req.getOrderId())
        result.setReqData(JSON.toJSONString(params))
        result.setResData(resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || "1000" != json.getString("code")) {
            result.setValid(false)
            result.setMessage(json == null ? "API异常:请联系出款商户确认订单." : json.getString("message"))
            return result
        }
        result.setValid(true)
        result.setMessage("")
        return result
    }

    WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        Map<String, String> resMap = args[2] as Map<String, String>
        this.glPaymentChannelBankBusiness = getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

        log.info("TianKongScript_WithdrawNotify_resMap:{}", JSON.toJSONString(resMap))

        JSONObject resultJson = new JSONObject(resMap)
        String orderId = resultJson.getJSONObject("reqBody").get("order_id")
        if (StringUtils.isNotEmpty(orderId)) {
            return withdrawQuery(okHttpUtil, merchant, orderId)
        }
            return null

    }

    WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2] as String

        String randomCode  = getRandomCode(merchantAccount.getMerchantCode() ,merchantAccount.getPayUrl());
        String token = getToken(randomCode ,merchantAccount.getMerchantCode(),merchantAccount.getPublicKey(),merchantAccount.getPayUrl())
        if ( token =="" || randomCode =="" ) {
            return null
        }

        Map<String,String> params = new HashMap<>()
        params.put("user_id", merchantAccount.getMerchantCode())
        params.put("order_id", orderId)
        params.put("sign", genSign(MD5.toAscii(params) + "&" + merchantAccount.getPublicKey(), merchantAccount.getPrivateKey()))

        log.info("TianKongScript_TransferQuery_reqMap:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = getRequestHeard(null, null, orderId, GlActionEnum.WITHDRAW_QUERY.getCode(), merchantAccount.getChannelId(), merchantAccount.getChannelName())

        Map<String, String> headerMap = new HashMap<>()
        headerMap.put("Authorization", token)
        String aesString = aes256Encode(new JSONObject(params).toString(),merchantAccount.getPublicKey())
        String resStr = okHttpUtil.postText(merchantAccount.getPayUrl() + SERVER_QUERY_URL, aesString, headerMap,requestHeader)
        log.info("TianKongScript_TransferQuery_resStr:{}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || "1000" != json.getString("code") || ObjectUtils.isEmpty(json.getJSONObject("data"))  ) {
            return null
        }
        WithdrawNotify notify = new WithdrawNotify()
        notify.setMerchantCode(merchantAccount.getMerchantCode())
        notify.setMerchantId(merchantAccount.getMerchantId())
        notify.setMerchantName(merchantAccount.getChannelName())
        notify.setOrderId(orderId)
        notify.setThirdOrderId("")

        // 0000 =未处理
        // 1000 =处理中
        // 2000 =成功
        // 3000 =失败
        // 4000 =订单异常
        String payStatus = json.getJSONObject("data").getString("status")
        if (payStatus == "2000") {
            notify.setStatus(0)
            notify.setRsp("SUCCESS")

        } else if (payStatus == "3000") {
            notify.setStatus(1)
            notify.setRsp("SUCCESS")

        } else {
            notify.setStatus(2)
            notify.setRsp("SUCCESS")
        }
        return notify
    }

    BigDecimal balanceQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount

        String randomCode  = getRandomCode(merchantAccount.getMerchantCode() ,merchantAccount.getPayUrl());
        String token = getToken(randomCode ,merchantAccount.getMerchantCode(),merchantAccount.getPublicKey(),merchantAccount.getPayUrl())

        Map<String, String> params = new HashMap<>()
        params.put("user_id", merchantAccount.getMerchantCode())
        params.put("sign", genSign(MD5.toAscii(params) + "&" + merchantAccount.getPublicKey(), merchantAccount.getPrivateKey()))

        log.info("TianKongScript_QueryBalance_reqMap: {}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                getRequestHeard("", "", "", GlActionEnum.PAYMENT_QUERY_BALANCE.getCode(), merchantAccount.getChannelId(), merchantAccount.getChannelName())
        Map<String, String> headerMap = new HashMap<>()

        headerMap.put("Authorization", token)
        String aesString = aes256Encode(new JSONObject(params).toString(),merchantAccount.getPublicKey())
        String resStr = okHttpUtil.postText(merchantAccount.getPayUrl() + SERVER_BALANCE_URL, aesString, headerMap,requestHeader)
        log.info("TianKongScript_QueryBalance_resStr: {}", resStr)

        JSONObject json = JSON.parseObject(resStr)

        if (json == null || json.getString("code") != "1000") {
            return BigDecimal.ZERO
        }
        if (json.getJSONObject("data").getJSONObject("CNY") == null) {
            return BigDecimal.ZERO
        }
        BigDecimal balance = json.getJSONObject("data").getJSONObject("CNY").getBigDecimal("bank2bank")
        return balance == null ? BigDecimal.ZERO : balance
    }



    /**
     * AES256加密
     * @param str 需要加密的字符串
     * @param code 安全码
     * @return 加密后的字符串
     */
    String aes256Encode(String str, String code) {
        byte[] key = code.getBytes()
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        SecretKeySpec keySpec = new SecretKeySpec(key, "AES")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec)
        byte[] result = cipher.doFinal(str.getBytes("UTF-8"))
        return Base64.getEncoder().encodeToString(result)
    }

    /**
     * 获取签名
     * @param beforeSignString 需要签名的字符串
     * @param privateKeyStr 私钥字符串
     * @return 签名
     */
    String genSign(String beforeSignString, String privateKeyStr) {
        Signature privateSignature = Signature.getInstance("SHA256withRSA")
        PrivateKey privateKey = getPrivateKey(privateKeyStr)
        privateSignature.initSign(privateKey)
        privateSignature.update(beforeSignString.getBytes("UTF-8"))
        byte[] sign = privateSignature.sign()
        return Base64.getEncoder().encodeToString(sign)
    }

    /**
     * 获取token
     * @param safeCode 安全码
     * @param merchantId 商户号
     * @param privateKeyStr 私钥字符串
     * @param baseUrl 请求基地址
     * @return token
     */
    String getToken(String randomCode, String user_id, String safecode ,String payUrl) {

        String originalString = randomCode + user_id + safecode;
        String sha256hex = DigestUtils.sha256Hex(originalString);

        Map<String, String> params = new HashMap<>()
        params.put("user_id", user_id)
        params.put("hash", sha256hex)

        String resStr = okHttpUtil.post(payUrl + SERVER_TOKEN_URL, params)
        log.info("TianKongScript_Transfer_getToken: {}", resStr) ;
        JSONObject json = JSON.parseObject(resStr)
        if(json.getString("code") != "1000" || json.getJSONObject("data") == null ) {
            return "";
        }
        JSONObject dataJson = json.getJSONObject("data");
        return dataJson.getString("token_type")+" "+ dataJson.getString("access_token");

    }

    String getRandomCode(String user_id , String payUrl ) {
        Map<String, String> aMap = new HashMap<>();
        aMap.put("user_id", user_id);
        String resStr = okHttpUtil.post(payUrl + RANDOM_CODE_URL, aMap) ;
        log.info("TianKongScript_Transfer_getRandomCode: {}", resStr)
        JSONObject json = JSON.parseObject(resStr)
        if(json.getString("code") != "1000" || json.getJSONObject("data") == null ) {
            return "";
        }
        JSONObject dataJson = json.getJSONObject("data");
        return dataJson.getString("code");
    }


    /**
     * 获取私钥
     * @param privateKeyStr 私钥字符串
     * @return 私钥
     */
    PrivateKey getPrivateKey(String privateKeyStr) {
        privateKeyStr = privateKeyStr.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("\n", "")
                .replace(" ","")
        byte[] decoded = Base64.getDecoder().decode(privateKeyStr)
        PKCS8EncodedKeySpec privateKey = new PKCS8EncodedKeySpec(decoded)
        KeyFactory kf = KeyFactory.getInstance("RSA")

        return kf.generatePrivate(privateKey)
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