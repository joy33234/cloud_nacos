package com.seektop.fund.payment.tiankongpay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
import com.seektop.common.utils.MD5
import com.seektop.constant.FundConstant
import com.seektop.enumerate.GlActionEnum
import com.seektop.exception.GlobalException
import com.seektop.fund.model.GlPaymentMerchantApp
import com.seektop.fund.model.GlPaymentMerchantaccount
import com.seektop.fund.payment.GlRechargeResult
import com.seektop.fund.payment.RechargeNotify
import com.seektop.fund.payment.RechargePrepareDO
import org.apache.commons.codec.digest.DigestUtils
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
/**
 * @desc 天空支付
 * @auth Otto
 * @date 2022-03-15
 */
class TianKongScript_recharge {

    private static final Logger log = LoggerFactory.getLogger(TianKongScript_recharge.class)

    private OkHttpUtil okHttpUtil
    private  final String PAY_URL =  "/merchant/payment"
    private  final String QUERY_URL =  "/merchant/order/payment"
    private static final String SERVER_TOKEN_URL = "/merchant/token"  //取得token
    private static final String RANDOM_CODE_URL = "/merchant/random_code" //取得 random code

    RechargeNotify result(Object[] args) throws GlobalException {
        return notify(args)
    }

    void pay(Object[] args) {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantApp merchant = args[1] as GlPaymentMerchantApp
        GlPaymentMerchantaccount payment = args[2] as GlPaymentMerchantaccount
        RechargePrepareDO req = args[3] as RechargePrepareDO
        GlRechargeResult result = args[4] as GlRechargeResult
        String payType = ""
        if (merchant.getPaymentId() == FundConstant.PaymentType.BANKCARD_TRANSFER) {
            payType = "bank2bank" //卡转卡

        } else if (merchant.getPaymentId() == FundConstant.PaymentType.QUICK_ALI_PAY){
            payType = "alipay" //支付寶

        } else if(merchant.getPaymentId() == FundConstant.PaymentType.WECHAT_PAY) {
            payType = "wechatpay" //微信

        }
        if (StringUtils.isNotEmpty(payType)) {
            prepareScan(merchant, payment, req, result, payType)
        }
    }

    private void prepareScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, RechargePrepareDO req, GlRechargeResult result, String payType) {

        //下单需将token 放入 header
        String randomCode  = getRandomCode(payment.getMerchantCode() ,payment.getPayUrl());
        String token = getToken(randomCode ,payment.getMerchantCode(),payment.getPublicKey() ,payment.getPayUrl())
        if (token == "" || randomCode =="" ) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg("网络请求超时，获取token失败，稍后重试")
            return
        }

        Map<String, String> headerMap = new HashMap<>()
        headerMap.put("Authorization", token)

        Map<String, String> params = new LinkedHashMap<String, String>()
        params.put("user_id", payment.getMerchantCode())
        params.put("order_id", req.getOrderId())
        params.put("amount", req.getAmount().setScale(2, RoundingMode.DOWN).toString())
        params.put("channel", payType)
        params.put("currency", "CNY")
        params.put("bank_code", "BOS")
        params.put("callback_url", payment.getNotifyUrl() + merchant.getId())
        params.put("redirect_url", payment.getNotifyUrl() )
        params.put("timestamp", System.currentTimeSeconds().toString())
        params.put("sign", genSign(MD5.toAscii(params) + "&" + payment.getPublicKey(), payment.getPrivateKey()))
        params.put("real_name",  req.getFromCardUserName())

        log.info("TianKongScript_Prepare_Params:{} url: {}", JSON.toJSONString(params) , payment.getPayUrl())
        String aesString = aes256Encode(new JSONObject(params).toString(),payment.getPublicKey())
        GlRequestHeader requestHeader = getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode(), payment.getChannelId(), payment.getChannelName())
        String resStr = okHttpUtil.postText(payment.getPayUrl() + PAY_URL, aesString, headerMap,requestHeader)
        log.info("TianKongScript_Prepare_resStr:{} , orderId :{}" , resStr, req.getOrderId())

        JSONObject json = JSON.parseObject(resStr);
        if (json == null) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("网络请求超时，稍后重试")
            return
        }
        if (json.getString("code") != "1000" ) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg(json.getString("message"))
            return
        }
        JSONObject dataJson = json.getJSONObject("data");
        if (dataJson == null || StringUtils.isEmpty(dataJson.getString("pay_url"))  ) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg(json.getString("message") == null ? "支付商未出码" : json.getString("message") )
            return
        }

        result.setRedirectUrl(dataJson.getString("pay_url"))

    }

    RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount payment = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>

        log.info("TianKongScript_Notify_resMap:{}", JSON.toJSONString(resMap))
        JSONObject jsonObj = JSON.parseObject(resMap.get("reqBody"));
        String orderId = jsonObj.getString("order_id") ;

        if ( orderId != null ){
            return payQuery(okHttpUtil, payment, orderId)

        }
        log.info("TianKongScript_RechargeNotify_Sign: 回调资料错误或验签失败 单号：{}", orderId)
        return null

    }

    RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String

        String randomCode  = getRandomCode(account.getMerchantCode() ,account.getPayUrl());
        String token = getToken(randomCode ,account.getMerchantCode(),account.getPublicKey() ,account.getPayUrl())

        Map<String, String> params = new HashMap<String, String>()
        params.put("user_id", account.getMerchantCode())
        params.put("order_id", orderId)
        params.put("sign", genSign(MD5.toAscii(params) + "&" + account.getPublicKey(), account.getPrivateKey()))

        Map<String, String> headerMap = new HashMap<>()
        headerMap.put("Authorization", token)

        String aesString = aes256Encode(new JSONObject(params).toString(),account.getPublicKey())
        log.info("TianKongScript_Query_reqMap:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = getRequestHeard(null, null, orderId, GlActionEnum.RECHARGE_QUERY.getCode(), account.getChannelId(), account.getChannelName())
        String resStr = okHttpUtil.postText(account.getPayUrl() + QUERY_URL, aesString, headerMap,requestHeader)
        log.info("TianKongScript_Query_resStr:{}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || "1000" != json.getString("code")) {
            return null
        }
        JSONObject dataJson = json.getJSONObject("data");

        //0000 =未处理 1000 =处理中 2000 =成功 3000 =失败 4000 =订单异常
        if ( "2000" == dataJson.getString("status") ) {
            RechargeNotify pay = new RechargeNotify()
            pay.setAmount(dataJson.getBigDecimal("accept_amount").setScale(2, RoundingMode.DOWN))
            pay.setFee(BigDecimal.ZERO)
            pay.setOrderId(orderId)
            pay.setRsp("SUCCESS")
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
        return FundConstant.ShowType.NORMAL
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

}