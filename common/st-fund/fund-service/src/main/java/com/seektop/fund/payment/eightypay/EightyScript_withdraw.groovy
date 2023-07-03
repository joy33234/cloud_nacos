package com.seektop.fund.payment.eightypay

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
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.crypto.Cipher
import java.math.RoundingMode
import java.security.Key
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec

/**
 * @desc 18付
 * @auth joy
 * @date 2021-04-26
 */

class EightyScript_withdraw {

    private static final Logger log = LoggerFactory.getLogger(EightyScript_withdraw.class)

    private OkHttpUtil okHttpUtil

    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness

    WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw

        Map<String, String> paramMap = new HashMap<>()
        paramMap.put("down_sn", req.getOrderId())
        paramMap.put("amount", req.getAmount().subtract(req.getFee()).multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.DOWN).toString())
        paramMap.put("bank_account", req.getName())
        paramMap.put("bank_cardno", req.getCardNo())
        paramMap.put("bank_code", glPaymentChannelBankBusiness.getBankCode(req.getBankId(), merchantAccount.getChannelId()))
        paramMap.put("channel_code", "1050")//代付通道
        paramMap.put("notify_url", merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId())
        String sign = MD5.md5(MD5.toAscii(paramMap) + "&key=" + merchantAccount.getPrivateKey())
        paramMap.put("sign", sign)

        paramMap.put("cipher_data", encryptByPublicKey(JSON.toJSONString(paramMap), merchantAccount.getPublicKey()))

        paramMap.put("merchant_sn", merchantAccount.getMerchantCode())

        log.info("EightyScript_Transfer_Params: {}", JSON.toJSONString(paramMap))
        GlRequestHeader requestHeader =
                getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(), merchantAccount.getChannelId(),merchantAccount.getChannelName())
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/api/settle/pay", paramMap, 30L, requestHeader)
        log.info("EightyScript_Transfer_resStr: {}", resStr)

        WithdrawResult result = new WithdrawResult()
        result.setOrderId(req.getOrderId())
        result.setReqData(JSON.toJSONString(paramMap))
        result.setResData(resStr)

        if (StringUtils.isEmpty(resStr)) {
            result.setValid(false)
            result.setMessage("API异常:请联系出款商户确认订单.")
            return result
        }

        JSONObject resJson = JSON.parseObject(resStr)
        if (resJson == null || "0" != resJson.getString("code")) {
            result.setValid(false)
            result.setMessage(resJson == null ? "API异常:请联系出款商户确认订单." : resJson.getString("msg"))
            return result
        }
        result.setValid(true)
        result.setMessage(resJson.getString("msg"))
        result.setThirdOrderId(resJson.getString("settle_sn"))
        return result
    }

    WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        Map<String, String> resMap = args[2] as Map<String, String>
        log.info("EightyScript_Transfer_Notify_resMap:{}", JSON.toJSONString(resMap))
        String orderId = resMap.get("orderNo")
        String thirdOrderId = resMap.get("platformOrderNo")
        if (StringUtils.isNotEmpty(orderId)) {
            return withdrawQuery(okHttpUtil, merchant, orderId, thirdOrderId)
        }
        return null
    }

    WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2]

        Map<String, String> paramMap = new HashMap<>()
        paramMap.put("merchant_sn", merchant.getMerchantCode())
        paramMap.put("down_sn", orderId)
        paramMap.put("sign", MD5.md5(MD5.toAscii(paramMap) + "&key=" + merchant.getPrivateKey()))

        log.info("EightyScript_Transfer_Query_Params: {}", JSON.toJSONString(paramMap))
        GlRequestHeader requestHeader =
                getRequestHeard(null, null, orderId, GlActionEnum.WITHDRAW_QUERY.getCode(),merchant.getChannelId(), merchant.getChannelName())
        String resStr = okHttpUtil.get(merchant.getPayUrl() + "/api/settle/query", paramMap, requestHeader)
        log.info("EightyScript_Transfer_Query_resStr: {}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || json.getString("code") == null || json.getString("code") != "0") {
            return null
        }
        JSONObject dataJSON = json.getJSONObject("data")
        if (dataJSON == null) {
            return null
        }
        //1：支付成功  2：支付失败    3：未支付   4：支付中
        Integer orderState = dataJSON.getInteger("status")
        WithdrawNotify notify = new WithdrawNotify()
        notify.setAmount(dataJSON.getBigDecimal("amount"))
        notify.setMerchantCode(merchant.getMerchantCode())
        notify.setMerchantId(merchant.getMerchantId())
        notify.setMerchantName(merchant.getChannelName())
        notify.setOrderId(orderId)
        if (orderState == 1) {
            notify.setStatus(0)
        } else if (orderState == 1) {
            notify.setStatus(1)
        } else {
            notify.setStatus(2)
        }
        notify.setThirdOrderId(dataJSON.getString("settle_sn"))
        return notify
    }


    BigDecimal balanceQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount

        Map<String, String> paramMap = new HashMap<>()
        paramMap.put("merchant_sn", merchantAccount.getMerchantCode())
        paramMap.put("channel_code", "1050")
        paramMap.put("sign", MD5.md5(MD5.toAscii(paramMap) + "&key=" + merchantAccount.getPrivateKey()))

        log.info("EightyScript_Query_Balance_Params:{}", JSON.toJSONString(paramMap))
        GlRequestHeader requestHeader =
                getRequestHeard(null, null, null, GlActionEnum.PAYMENT_QUERY_BALANCE.getCode(),merchantAccount.getChannelId(),merchantAccount.getChannelName())
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/api/settle/balance", paramMap,  requestHeader)
        log.info("EightyScript_Query_Balance_resStr: {}", resStr)

        if (StringUtils.isEmpty(resStr)) {
            return BigDecimal.ZERO
        }
        JSONObject json = JSON.parseObject(resStr)
        if (json == null || json.getString("code") != "0") {
            return BigDecimal.ZERO
        }
        JSONObject dataJSON = json.getJSONObject("data");
        if (dataJSON == null) {
            return BigDecimal.ZERO
        }
        BigDecimal balance = dataJSON.getBigDecimal("balance")
        return balance == null ? BigDecimal.ZERO : balance
    }

    public static void main(String[] args) {
        Map<String, String> paramMap = new HashMap<>()
        paramMap.put("merchant_sn", "merchantAccount.getMerchantCode()")
        paramMap.put("channel_code", "1050")
        paramMap.put("sign", MD5.md5(MD5.toAscii(paramMap) + "key=" + "merchantAccount.getPrivateKey()"))

        log.info("EightyScript_Query_Balance_Params:{}", JSON.toJSONString(paramMap))
    }

    /**
     * 获取头部信息
     *
     * @param userId
     * @param userName
     * @param orderId
     * @return
     */
    private GlRequestHeader getRequestHeard(String userId, String userName, String orderId, String code, Integer channelId, String channleName) {
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(code)
                .channelId(channelId + "")
                .channelName(channleName)
                .userId(userId)
                .userName(userName)
                .tradeId(orderId)
                .build()
        return requestHeader
    }

    private String getSign(JSONObject json){

    }


    public static String encryptByPublicKey(String content, String publicKey) throws Exception {

        return Base64.getEncoder().encodeToString(encryptByPublicKey(content.getBytes("UTF-8"), publicKey));
    }

    //2.1 2.3
    public static byte[] encryptByPublicKey(byte[] data, String publicKey) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(publicKey);
        X509EncodedKeySpec x509KeySpec = new X509EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        Key publicK = keyFactory.generatePublic(x509KeySpec);

        Cipher cipher = Cipher.getInstance(keyFactory.getAlgorithm());
        cipher.init(1, publicK);
        int inputLen = data.length;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int offSet = 0;

        int i = 0;

        while (inputLen - offSet > 0) {
            byte[] cache;
            if (inputLen - offSet > 117) {
                cache = cipher.doFinal(data, offSet, 117);
            } else {
                cache = cipher.doFinal(data, offSet, inputLen - offSet);
            }
            out.write(cache, 0, cache.length);
            i++;
            offSet = i * 117;
        }
        byte[] encryptedData = out.toByteArray();
        out.close();
        return encryptedData;
    }

//    public static String sign(String content, String privateKey) {
//        try {
//            PKCS8EncodedKeySpec priPKCS8 = new PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKey));
//            KeyFactory keyf = KeyFactory.getInstance("RSA");
//            PrivateKey priKey = keyf.generatePrivate(priPKCS8);
//            java.security.Signature signature = java.security.Signature.getInstance(SIGN_ALGORITHMS);
//            signature.initSign(priKey);
//            signature.update(content.getBytes());
//            byte[] signed = signature.sign();
//            return Base64.getEncoder().encodeToString(signed);
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//        return null;
//    }

}