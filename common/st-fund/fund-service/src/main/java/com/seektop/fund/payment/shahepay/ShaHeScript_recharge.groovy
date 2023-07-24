//package com.seektop.fund.payment.shahepay
//
//import com.alibaba.fastjson.JSON
//import com.alibaba.fastjson.JSONObject
//import com.seektop.common.http.GlRequestHeader
//import com.seektop.common.http.OkHttpUtil
//import com.seektop.constant.FundConstant
//import com.seektop.enumerate.GlActionEnum
//import com.seektop.exception.GlobalException
//import com.seektop.fund.model.GlPaymentMerchantApp
//import com.seektop.fund.model.GlPaymentMerchantaccount
//import com.seektop.fund.payment.GlRechargeResult
//import com.seektop.fund.payment.RechargeNotify
//import com.seektop.fund.payment.RechargePrepareDO
//import com.sun.org.apache.xerces.internal.impl.dv.util.Base64
//import org.apache.commons.lang3.StringUtils
//import org.apache.commons.ssl.PKCS8Key
//import org.slf4j.Logger
//import org.slf4j.LoggerFactory
//import org.springframework.util.ObjectUtils
//
//import java.math.RoundingMode
//import java.security.*
//import java.security.spec.PKCS8EncodedKeySpec
//import java.text.SimpleDateFormat
//
///**
// * @desc 沙盒支付
// * @date 2021-06-19
// * @auth joy
// */
//public class ShaHeScript_recharge {
//
//
//    private static final Logger log = LoggerFactory.getLogger(ShaHeScript_recharge.class)
//
//    private OkHttpUtil okHttpUtil
//
//
//    public RechargeNotify result(Object[] args) throws GlobalException {
//        return null
//    }
//
//    public void pay(Object[] args) {
//        this.okHttpUtil = args[0] as OkHttpUtil
//        GlPaymentMerchantApp merchant = args[1] as GlPaymentMerchantApp
//        GlPaymentMerchantaccount account = args[2] as GlPaymentMerchantaccount
//        RechargePrepareDO req = args[3] as RechargePrepareDO
//        GlRechargeResult result = args[4] as GlRechargeResult
//        String payType = ""
//        if (FundConstant.PaymentType.BANKCARD_TRANSFER == merchant.getPaymentId()) {
//            payType = "bank"//卡卡
//        }
//        if (StringUtils.isEmpty(payType)) {
//            result.setErrorMsg("支付方式不支持")
//            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
//            return
//        }
//        prepare(merchant, account, req, result, payType)
//    }
//
//    private void prepare(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result, String payType) throws GlobalException {
//        JSONObject paramJSON = new JSONObject();
//        paramJSON.put("account_name", account.getMerchantCode())
//        paramJSON.put("merchant_order_id",req.getOrderId())
//        paramJSON.put("total_amount",req.getAmount().setScale(2, RoundingMode.DOWN).toString())
//        paramJSON.put("timestamp", getTimeStr(req.getCreateDate()))
//        paramJSON.put("notify_url", account.getNotifyUrl() + merchant.getId())
//        paramJSON.put("subject", "recharge")
//        paramJSON.put("guest_real_name", req.getFromCardUserName())
//        paramJSON.put("payment_method", payType)
//
//        Map<String, String> params = new LinkedHashMap<>()
//        try {
//            String privatekey = account.getPrivateKey().replaceAll("-----BEGIN RSA PRIVATE KEY-----","-----BEGIN RSA PRIVATE KEY-----\n")
//
//            String sign = sign(getPrivateKey(privatekey.getBytes()), paramJSON.toJSONString());
//            params.put("data", paramJSON.toJSONString())
//            params.put("signature", sign);
//        } catch (Exception e){
//            log.error("ShaHeScript_sign_error",e)
//        }
//        log.info("ShaHeScript_recharge_prepare_params:{}", JSON.toJSONString(params))
//        GlRequestHeader requestHeader =
//                getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode(), account.getChannelId(), account.getChannelName())
//
//        String resStr = okHttpUtil.postJSON(account.getPayUrl() + "/merchant_api/v1/orders/payment", JSON.toJSONString(params), requestHeader)
//        log.info("ShaHeScript_recharge_prepare_resp:{}", resStr)
//        if (StringUtils.isEmpty(resStr)) {
//            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
//            result.setErrorMsg("网络请求超时，稍后重试")
//            return
//        }
//        JSONObject json = JSONObject.parseObject(resStr)
//        if (json == null || ObjectUtils.isEmpty(json.getString("payment_url"))) {
//            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
//            result.setErrorMsg(json.getString("message"))
//            return
//        }
//        result.setRedirectUrl(json.getString("payment_url"))
//        result.setThirdOrderId(json.getString("id"))
//    }
//
//
//    public RechargeNotify notify(Object[] args) throws GlobalException {
//        this.okHttpUtil = args[0] as OkHttpUtil
//        GlPaymentMerchantaccount account = args[2] as GlPaymentMerchantaccount
//        Map<String, String> resMap = args[3] as Map<String, String>
//        log.info("ShaHeScript_notify_resp:{}", JSON.toJSONString(resMap))
//        JSONObject json = JSON.parseObject(resMap.get("data"))
//        String orderStr = json.getString("order")
//        JSONObject orderJSON = JSON.parseObject(orderStr)
//        String orderId = orderJSON.getString("merchant_order_id")
//        String thirdOrderId = orderJSON.getString("id")
//        if (StringUtils.isNotEmpty(orderId) && StringUtils.isNotEmpty(thirdOrderId)) {
//            return this.payQuery(okHttpUtil, account, orderId, thirdOrderId, args[4])
//        }
//        return null
//    }
//
//    public RechargeNotify payQuery(Object[] args) throws GlobalException {
//        this.okHttpUtil = args[0] as OkHttpUtil
//        GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
//        String orderId = args[2] as String
//        String thirdOrderId = args[3] as String
//
//        JSONObject paramJSON = new JSONObject();
//        paramJSON.put("account_name", account.getMerchantCode())
//        paramJSON.put("merchant_order_id", orderId)
//        paramJSON.put("order_id", thirdOrderId)
//        paramJSON.put("timestamp", getTimeStr(new Date()))
//
//        String privatekey = account.getPrivateKey().replaceAll("-----BEGIN RSA PRIVATE KEY-----","-----BEGIN RSA PRIVATE KEY-----\n")
//
//        Map<String, String> params = new LinkedHashMap<>()
//        String sign = sign(getPrivateKey(privatekey.getBytes()), paramJSON.toJSONString());
//        params.put("data", paramJSON.toJSONString())
//        params.put("signature", sign);
//
//        log.info("ShaHeScript_query_params:{}", JSON.toJSONString(params))
//        GlRequestHeader requestHeader = getRequestHeard(null, null, orderId, GlActionEnum.RECHARGE_QUERY.getCode(), account.getChannelId(), account.getChannelName())
//        String resStr = okHttpUtil.post(account.getPayUrl() + "/merchant_api/v1/orders/query", params, requestHeader)
//        log.info("ShaHeScript_query_resp:{}", resStr)
//        if (StringUtils.isEmpty(resStr)) {
//            return null
//        }
//        JSONObject json = JSON.parseObject(resStr)
//        if (json == null) {
//            return null
//        }
//        // 支付状态: init 代表订单刚建立，completed 代表订单已交易完成
//        if ("completed" == json.getString("status")) {
//            RechargeNotify pay = new RechargeNotify()
//            pay.setAmount(json.getBigDecimal("total_amount").setScale(2, RoundingMode.DOWN))
//            pay.setFee(BigDecimal.ZERO)
//            pay.setOrderId(orderId)
//            pay.setThirdOrderId(json.getString("id"))
//            return pay
//        }
//        return null
//    }
//
//
//    void cancel(Object[] args) throws GlobalException {
//
//    }
//
//    private static String getTimeStr(Date date) {
//        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");
//        return sdf.format(date);
//    }
//
//
//    /**
//     * 是否为内部渠道
//     *
//     * @param args
//     * @return
//     */
//    public boolean innerpay(Object[] args) {
//        return false
//    }
//
//    /**
//     * 根据支付方式判断-转帐是否需要实名
//     *
//     * @param args
//     * @return
//     */
//    public boolean needName(Object[] args) {
//        Integer paymentId = args[1] as Integer
//        if (paymentId == FundConstant.PaymentType.BANKCARD_TRANSFER
//                || paymentId == FundConstant.PaymentType.ALI_TRANSFER
//                || paymentId == FundConstant.PaymentType.UNION_TRANSFER) {
//            return true
//        }
//        return false
//    }
//
//    /**
//     * 充值是否需要卡号
//     *
//     * @param args
//     * @return
//     */
//    public boolean needCard(Object[] args) {
//        return false
//    }
//
//    /**
//     * 是否显示充值订单祥情
//     *
//     * @param args
//     * @return
//     */
//    public Integer showType(Object[] args) {
//        return FundConstant.ShowType.NORMAL
//    }
//
//    private static GlRequestHeader getRequestHeard(String userId, String userName, String orderId, String code, Integer channelId, String channelName) {
//        GlRequestHeader requestHeader = GlRequestHeader.builder()
//                .action(code)
//                .channelId(channelId + "")
//                .channelName(channelName)
//                .userId(userId)
//                .userName(userName)
//                .tradeId(orderId)
//                .build()
//        return requestHeader
//    }
//
//    private static PrivateKey getPrivateKey(byte[] encryptedPKInfo)
//            throws GeneralSecurityException, IOException {
//        PKCS8Key pkcs8 = new PKCS8Key(new ByteArrayInputStream(encryptedPKInfo), "BABE033admin".toCharArray());
//        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(pkcs8.getDecryptedBytes());
//        return KeyFactory.getInstance("RSA").generatePrivate(spec);
//    }
//
//
//    private static String sign(PrivateKey privateKey, String message) throws NoSuchAlgorithmException, InvalidKeyException, SignatureException,
//            UnsupportedEncodingException {
//        Signature sign = Signature.getInstance("SHA256withRSA");
//        sign.initSign(privateKey);
//        sign.update(message.getBytes("UTF-8"));
//        return Base64.encode(sign.sign());
//    }
//
//
//}