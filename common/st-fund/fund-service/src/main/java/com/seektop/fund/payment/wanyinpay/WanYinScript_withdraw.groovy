package com.seektop.fund.payment.wanyinpay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
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
import java.security.PrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

/**
 * 万银支付
 */

class WanYinScript_withdraw {

    private static final Logger log = LoggerFactory.getLogger(WanYinScript_withdraw.class)

    private OkHttpUtil okHttpUtil

    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness


    WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount account = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

        JSONObject params = new JSONObject()
        params.put("bank_code", glPaymentChannelBankBusiness.getBankCode(req.getBankId(), account.getChannelId()))
        params.put("amount", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString())
        params.put("merchant_user", "st")
        params.put("merchant_order_no", req.getOrderId())
        params.put("platform", "PC")
        params.put("card_num", req.getCardNo())
        params.put("card_name", req.getName())
        params.put("bank_branch", "上海市")
        params.put("bank_province", "上海市")
        params.put("bank_city", "上海市")
        params.put("callback_url", account.getNotifyUrl() + account.getMerchantId())

        String data = encryptByPublicKey(params.toJSONString(), account.getPublicKey());

        String sign = sign(data, account.getPrivateKey());

        Map<String, String> paramsMap = new LinkedHashMap<>()
        paramsMap.put("merchant_code", account.getMerchantCode())
        paramsMap.put("data", data)
        paramsMap.put("sign", sign)

        log.info("WanYinScript_transfer_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                getRequestHeard( req.getUserId() + "", req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(), account.getChannelId(), account.getChannelName())
        String resStr = okHttpUtil.post(account.getPayUrl() + "/rsa/withdraw", paramsMap , requestHeader)
        log.info("WanYinScript_transfer_resp:{}", resStr)
        resStr =resStr.replace("\\", "\\\\")
        WithdrawResult result = new WithdrawResult()
        result.setOrderId(req.getOrderId())
        result.setReqData(JSON.toJSONString(params))
        result.setResData(resStr)

        JSONObject responseJSON = JSONObject.parseObject(resStr)
        if (responseJSON == null || responseJSON.getString("status") != "1" ) {
            result.setValid(false)
            result.setMessage(responseJSON == null ? "API异常:请联系出款商户确认订单." : responseJSON.getString("error_code"))
            return result
        }

        String dataStr = decryptByPrivateKey( responseJSON.getString("data") , account.getPrivateKey())
        log.info("WanYinScript_transfer_dataStr:{}", dataStr)
        JSONObject respDataJSON = JSONObject.parseObject(dataStr)

        if(respDataJSON == null) {
            result.setValid(false)
            result.setMessage(responseJSON == null ? "API异常:请联系出款商户确认订单." : responseJSON.getString("error_code"))
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
        log.info("WanYinScript_withdrawNotify_resp:{}", JSON.toJSONString(resMap))
        String dataStr = decryptByPrivateKey( resMap.get("data") , merchant.getPrivateKey())
        log.info("WanYinScript_withdrawNotify_dataStr:{}", dataStr)
        JSONObject json = JSONObject.parseObject(dataStr)
        String orderId = json.getString("merchant_order_no")
        if (StringUtils.isEmpty(orderId)) {
            return null
        }
        return withdrawQuery(this.okHttpUtil, merchant, orderId , args[3])
    }

    WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount account = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2]

        JSONObject params = new JSONObject()
        params.put("merchant_order_no", orderId)

        String data = encryptByPublicKey(params.toJSONString(), account.getPublicKey());

        String sign = sign(data, account.getPrivateKey());

        Map<String, String> paramsMap = new LinkedHashMap<>()
        paramsMap.put("merchant_code", account.getMerchantCode())
        paramsMap.put("data", data)
        paramsMap.put("sign", sign)

        log.info("WanYinScript_transferQuery_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                getRequestHeard("", "", orderId, GlActionEnum.WITHDRAW_QUERY.getCode(), account.getChannelId(), account.getChannelName())
        String resStr = okHttpUtil.post(account.getPayUrl() + "/rsa/query-order", paramsMap , requestHeader)
        log.info("WanYinScript_transferQuery_resp:{}", resStr)
        resStr =resStr.replace("\\", "\\\\")
        JSONObject responseJSON = JSONObject.parseObject(resStr)


        if (responseJSON == null || responseJSON.getInteger("status") != 1 ) {
            return null
        }
        String dataStr = decryptByPrivateKey( responseJSON.getString("data"), account.getPrivateKey())
        log.info("WanYinScript_transferQuery_dataStr:{}", dataStr)
        JSONObject respDataJSON = JSONObject.parseObject(dataStr)

        WithdrawNotify notify = new WithdrawNotify()
        if (respDataJSON != null) {
            notify.setAmount(respDataJSON.getBigDecimal("amount"))
            notify.setMerchantCode(account.getMerchantCode())
            notify.setMerchantId(account.getMerchantId())
            notify.setMerchantName(account.getChannelName())
            notify.setOrderId(orderId)
            notify.setThirdOrderId("")
            //P = 等待中，F = 失败/拒绝， Y = 已支付， I = 支付中， C = 撤单
            if (respDataJSON.getString("trans_status") == "Y") {
                notify.setStatus(0)
                String rsp = "{\"error_msg\":\"\",\"status\":\"1\"}";
                notify.setRsp(rsp)
            } else if (respDataJSON.getString("trans_status") == "F" || respDataJSON.getString("trans_status") == "C") {
                notify.setStatus(1)
                String rsp = "{\"error_msg\":\"\",\"status\":\"1\"}";
                notify.setRsp(rsp)
            } else {
                notify.setStatus(2)
            }
        }
        return notify
    }


    BigDecimal balanceQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount account = args[1] as GlWithdrawMerchantAccount

        JSONObject params = new JSONObject()
        params.put("merchant_order_no", account.getMerchantCode())
        String data = encryptByPublicKey(params.toJSONString(), account.getPublicKey());

        String sign = sign(data, account.getPrivateKey());

        Map<String, String> paramsMap = new LinkedHashMap<>()
        paramsMap.put("merchant_code", account.getMerchantCode())
        paramsMap.put("data", data)
        paramsMap.put("sign", sign)

        log.info("WanYinScript_QueryBalance_reqMap2: {}", JSON.toJSONString(paramsMap))
        GlRequestHeader requestHeader =
                getRequestHeard("", "", "", GlActionEnum.PAYMENT_QUERY_BALANCE.getCode(),account.getChannelId(),account.getChannelName())
        String resStr = okHttpUtil.post(account.getPayUrl() + "/rsa/balance", paramsMap, requestHeader)
        log.info("WanYinScript_QueryBalance_resStr: {}", resStr)
        resStr =resStr.replace("\\", "\\\\")
        JSONObject responseJSON = JSON.parseObject(resStr)
        if (responseJSON != null && responseJSON.getInteger("status") == 1) {
            String dataStr = responseJSON.getString("data")
            dataStr = decryptByPrivateKey( dataStr, account.getPrivateKey())

            JSONObject respDataJSON = JSONObject.parseObject(dataStr)
            if (respDataJSON != null) {
                return respDataJSON.getBigDecimal("current_balance") == null ? BigDecimal.ZERO : respDataJSON.getBigDecimal("current_balance")
            }
        }
        return BigDecimal.ZERO
    }




    public static final String SIGN_ALGORITHMS = "SHA1WithRSA";
    private static final String KEY_ALGORITHM = "RSA";
    private static final int KEY_SIZE = 2048;
    private static final int DECRYPT_BLOCK_SIZE = KEY_SIZE / 8;


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


    public static String sign(String content, String privateKey) {
        try {
            PKCS8EncodedKeySpec priPKCS8 = new PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKey));
            KeyFactory keyf = KeyFactory.getInstance("RSA");
            PrivateKey priKey = keyf.generatePrivate(priPKCS8);
            java.security.Signature signature = java.security.Signature.getInstance(SIGN_ALGORITHMS);
            signature.initSign(priKey);
            signature.update(content.getBytes());
            byte[] signed = signature.sign();
            return Base64.getEncoder().encodeToString(signed);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }


    public static String decryptByPrivateKey(String content, String privateKey) throws Exception {
        byte[] privateKeyBytes = Base64.getDecoder().decode(privateKey);

        content = content.replaceAll("[\\s*\t\n\r]", "");

        byte[] decryptBytes = Base64.getDecoder().decode(content);

        if (decryptBytes.length <= DECRYPT_BLOCK_SIZE) {
            return new String(decrypt(decryptBytes, privateKeyBytes), "UTF-8");
        } else {
            byte[] buffer = null;

            int index = ((decryptBytes.length - 1) / DECRYPT_BLOCK_SIZE) + 1;
            byte[] blockBytes = new byte[DECRYPT_BLOCK_SIZE];
            for (int i = 0; i < index; i++) {
                if (i == index - 1) {
                    blockBytes = new byte[DECRYPT_BLOCK_SIZE];
                }
                int startIndex = i * DECRYPT_BLOCK_SIZE;
                int endIndex = startIndex + DECRYPT_BLOCK_SIZE;
                blockBytes = Arrays.copyOfRange(decryptBytes, startIndex,
                        endIndex > decryptBytes.length ? decryptBytes.length : endIndex);
                if (buffer == null) {
                    buffer = decrypt(blockBytes, privateKeyBytes);
                } else {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    baos.write(buffer);
                    baos.write(decrypt(blockBytes, privateKeyBytes));
                    buffer = baos.toByteArray();
                    baos.close();
                }
            }
            return new String(buffer, "UTF-8");
        }
    }

    public static byte[] decrypt(byte[] decrypt, byte[] privateKeyBytes) throws Exception {
        PrivateKey privateKey = codeToPrivateKey(privateKeyBytes);

        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.DECRYPT_MODE, privateKey);
        byte[] resultBytes = cipher.doFinal(decrypt);
        return resultBytes;
    }

    public static PrivateKey codeToPrivateKey(byte[] privateKey) throws Exception {
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(privateKey);
        KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM);
        PrivateKey keyPrivate = keyFactory.generatePrivate(keySpec);
        return keyPrivate;
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
