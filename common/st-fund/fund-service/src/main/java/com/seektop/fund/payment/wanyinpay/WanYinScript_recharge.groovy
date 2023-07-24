package com.seektop.fund.payment.wanyinpay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
import com.seektop.constant.FundConstant
import com.seektop.enumerate.GlActionEnum
import com.seektop.exception.GlobalException
import com.seektop.fund.model.GlPaymentMerchantApp
import com.seektop.fund.model.GlPaymentMerchantaccount
import com.seektop.fund.payment.GlRechargeResult
import com.seektop.fund.payment.RechargeNotify
import com.seektop.fund.payment.RechargePrepareDO
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.crypto.Cipher
import java.security.Key
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

/**
 * 万银支付
 */

class WanYinScript_recharge {

    private static final Logger log = LoggerFactory.getLogger(WanYinScript_recharge.class)

    private OkHttpUtil okHttpUtil

    RechargeNotify result(Object[] args) throws GlobalException {
        return notify(args)
    }

    void pay(Object[] args) {
        log.info("万银支付")
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantApp merchant = args[1] as GlPaymentMerchantApp
        GlPaymentMerchantaccount payment = args[2] as GlPaymentMerchantaccount
        RechargePrepareDO req = args[3] as RechargePrepareDO
        GlRechargeResult result = args[4] as GlRechargeResult
        String serviceType = ""
        log.info("merchant：{}", JSON.toJSONString(merchant))
        if (FundConstant.PaymentType.BANKCARD_TRANSFER == merchant.getPaymentId()) {
            serviceType = "22"
        }
        if(StringUtils.isNotEmpty(serviceType)) {
            log.info("serviceType：{}",serviceType)
            prepareScan(merchant, payment, req, result, serviceType ,args[5])
        } else {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg("支付方式暂不支持，请联系技术人员")
            return
        }

    }

    private void prepareScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, RechargePrepareDO req, GlRechargeResult result, String serviceType, Object[] args) {

        log.info("recharge-1")

        JSONObject params = new JSONObject()
        params.put("bank_code", "")
        params.put("service_type", serviceType)
        params.put("merchant_user", "st")
        params.put("amount", req.getAmount())
        params.put("risk_level", "1")
        params.put("merchant_order_no", req.getOrderId())
        params.put("platform", "PC")
        params.put("callback_url", payment.getNotifyUrl() + merchant.getId())

        log.info("params:{}", JSON.toJSONString(params))

        String data = encryptByPublicKey(params.toJSONString(), payment.getPublicKey());

        String sign = sign(data, payment.getPrivateKey());

        Map<String, String> paramsMap = new LinkedHashMap<>()
        paramsMap.put("merchant_code", payment.getMerchantCode())
        paramsMap.put("data", data)
        paramsMap.put("sign", sign)

        log.info("WanYinScript_prepare_params:{}", JSON.toJSONString(paramsMap))
        GlRequestHeader requestHeader =
                getRequestHeard(req.getUserId() + "", req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode(), payment.getChannelId(), payment.getChannelName())
        String resStr = okHttpUtil.post(payment.getPayUrl() + "/rsa/deposit", paramsMap , requestHeader)
        log.info("WanYinScript_prepare_resp:{}", resStr)

        if (StringUtils.isEmpty(resStr)) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("网络连接超时")
            return
        }
        resStr =resStr.replace("\\", "\\\\")
        JSONObject responseJSON = JSONObject.parseObject(resStr)


        if (responseJSON == null || responseJSON.getString("status") != "1" ) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg(responseJSON == null ? "创建订单失败" : responseJSON.getString("error_msg"))
            return
        }

        String dataStr = decryptByPrivateKey( responseJSON.getString("data"), payment.getPrivateKey())
        log.info("WanYinScript_prepare_resp_dataStr:{}", dataStr)
        JSONObject respDataJSON = JSONObject.parseObject(dataStr)

        if(respDataJSON == null || StringUtils.isEmpty(respDataJSON.getString("transaction_url"))) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg("创建订单失败")
            return
        }
        result.setRedirectUrl(respDataJSON.getString("transaction_url"))

    }


    RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount payment = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>
        log.info("WanYinScript_notify_resp:{}", JSON.toJSONString(resMap))
        String dataStr = decryptByPrivateKey( resMap.get("data"), payment.getPrivateKey())
        log.info("WanYinScript_notify_data:{}",dataStr)
        JSONObject json = JSONObject.parseObject(dataStr)
        String orderId = json.getString("merchant_order_no")
        if (StringUtils.isNotEmpty(orderId)) {
            return this.payQuery(okHttpUtil, payment, orderId, args[4])
        }
        return null
    }

    RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String

        JSONObject params = new JSONObject()
        params.put("merchant_order_no", orderId)

        String data = encryptByPublicKey(params.toJSONString(), account.getPublicKey());

        String sign = sign(data, account.getPrivateKey());

        Map<String, String> paramsMap = new LinkedHashMap<>()
        paramsMap.put("merchant_code", account.getMerchantCode())
        paramsMap.put("data", data)
        paramsMap.put("sign", sign)

        log.info("WanYinScript_query_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                getRequestHeard("", "", orderId, GlActionEnum.RECHARGE_QUERY.getCode(), account.getChannelId(), account.getChannelName())
        String resStr = okHttpUtil.post(account.getPayUrl() + "/rsa/query-order", paramsMap , requestHeader)
        log.info("WanYinScript_query_resp:{}", resStr)
        resStr =resStr.replace("\\", "\\\\")
        JSONObject responseJSON = JSONObject.parseObject(resStr)

        if (responseJSON == null || responseJSON.getString("status") != "1" ) {
            return null
        }

        String dataStr = decryptByPrivateKey( responseJSON.getString("data") , account.getPrivateKey())
        log.info("WanYinScript_query_dataStr:{}", dataStr)
        JSONObject respDataJSON = JSONObject.parseObject(dataStr)
        //S = 通过，P = 申请中
        if (respDataJSON != null && "S" == respDataJSON.getString("trans_status")) {
            RechargeNotify pay = new RechargeNotify()
            pay.setOrderId(orderId)
            pay.setAmount(respDataJSON.getBigDecimal("amount"))
            pay.setFee(BigDecimal.ZERO)
            pay.setThirdOrderId("")
            String rsp = "{\"error_msg\":\"\",\"status\":\"1\"}";
            pay.setRsp(rsp)
            return pay
        }
        return null
    }


    void cancel(Object[] args) throws GlobalException {

    }





    /**
     * 是否为内部渠道
     *
     * @param args
     * @return
     */
    public boolean innerpay(Object[] args) {
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
                || paymentId == FundConstant.PaymentType.ALI_TRANSFER
                || paymentId == FundConstant.PaymentType.UNION_TRANSFER) {
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
        return FundConstant.ShowType.NORMAL
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
