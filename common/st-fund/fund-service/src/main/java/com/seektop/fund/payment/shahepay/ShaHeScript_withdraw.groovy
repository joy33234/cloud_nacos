//package com.seektop.fund.payment.shahepay
//
//import com.alibaba.fastjson.JSON
//import com.alibaba.fastjson.JSONObject
//import com.seektop.common.http.GlRequestHeader
//import com.seektop.common.http.OkHttpUtil
//import com.seektop.enumerate.GlActionEnum
//import com.seektop.exception.GlobalException
//import com.seektop.fund.business.GlPaymentChannelBankBusiness
//import com.seektop.fund.model.GlWithdraw
//import com.seektop.fund.model.GlWithdrawMerchantAccount
//import com.seektop.fund.payment.WithdrawNotify
//import com.seektop.fund.payment.WithdrawResult
//import com.seektop.fund.payment.groovy.BaseScript
//import com.seektop.fund.payment.groovy.ResourceEnum
//import com.sun.org.apache.xerces.internal.impl.dv.util.Base64
//import org.apache.commons.lang3.StringUtils
//import org.apache.commons.ssl.PKCS8Key
//import org.slf4j.Logger
//import org.slf4j.LoggerFactory
//
//import java.math.RoundingMode
//import java.security.*
//import java.security.spec.PKCS8EncodedKeySpec
//import java.text.SimpleDateFormat
//
///**
// * @desc 沙盒支付
// * @date 2021-06-15
// * @auth joy
// */
//public class ShaHeScript_withdraw {
//
//
//    private static final Logger log = LoggerFactory.getLogger(ShaHeScript_withdraw.class)
//
//    private OkHttpUtil okHttpUtil
//
//    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness
//
//
//    public WithdrawResult withdraw(Object[] args) throws GlobalException {
//        this.okHttpUtil = args[0] as OkHttpUtil
//        GlWithdrawMerchantAccount account = args[1] as GlWithdrawMerchantAccount
//        GlWithdraw req = args[2] as GlWithdraw
//        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
//
//
//        JSONObject paramJSON = new JSONObject();
//        paramJSON.put("account_name", account.getMerchantCode())
//        paramJSON.put("merchant_order_id", req.getOrderId())
//        paramJSON.put("total_amount", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString())
//        paramJSON.put("timestamp", getTimeStr(new Date()))
//        paramJSON.put("notify_url", account.getNotifyUrl() + account.getMerchantId())
//        paramJSON.put("bank_name", glPaymentChannelBankBusiness.getBankName(req.getBankId(), account.getChannelId()))
//        paramJSON.put("bank_province_name", "上海市")
//        paramJSON.put("bank_city_name", "上海市")
//        paramJSON.put("bank_account_no", req.getCardNo())
//        paramJSON.put("bank_account_type", "personal")
//        paramJSON.put("bank_account_name", req.getName())
//
//        String privatekey = account.getPrivateKey().replaceAll("-----BEGIN RSA PRIVATE KEY-----","-----BEGIN RSA PRIVATE KEY-----\n")
//        Map<String, String> params = new LinkedHashMap<>()
//        String sign = sign(getPrivateKey(privatekey.getBytes()), paramJSON.toJSONString());
//        params.put("data", paramJSON.toJSONString())
//        params.put("signature", sign);
//
//        log.info("ShaHeScript_doTransfer_params:{}", JSON.toJSONString(params))
//        GlRequestHeader requestHeader =
//                this.getRequestHeard(req.getUserId() + "", req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(), account.getChannelId(), account.getChannelName())
//        String resStr = okHttpUtil.post(account.getPayUrl() + "/merchant_api/v1/orders/payment_transfer", params, requestHeader)
//        log.info("ShaHeScript_doTransfer_resp:{}", resStr)
//
//        WithdrawResult result = new WithdrawResult()
//        result.setOrderId(req.getOrderId())
//        result.setReqData(JSON.toJSONString(params))
//        result.setResData(resStr)
//
//        if (StringUtils.isEmpty(resStr)) {
//            result.setValid(false)
//            result.setMessage("API异常:请联系出款商户确认订单.")
//            return result
//        }
//        JSONObject json = JSON.parseObject(resStr)
//        if (json == null || StringUtils.isEmpty(json.getString("id"))) {
//            result.setValid(false)
//            result.setMessage(json.getString("message"))
//            return result
//        }
//        result.setValid(true)
//        result.setMessage("ok")
//        return result
//    }
//
//    public WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
//        this.okHttpUtil = args[0] as OkHttpUtil
//        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
//        Map<String, String> resMap = args[2] as Map<String, String>
//        log.info("ShaHeScript_doTransferNotify_resp:{}", JSON.toJSONString(resMap))
//        JSONObject json = JSON.parseObject(resMap.get("data"))
//        String orderStr = json.getString("order")
//        JSONObject orderJSON = JSON.parseObject(orderStr)
//        String orderId = orderJSON.getString("merchant_order_id")
//        String thirdOrderId = orderJSON.getString("id")
//        if (StringUtils.isNotEmpty(orderId) && StringUtils.isNotEmpty(thirdOrderId)) {
//            return this.withdrawQuery(okHttpUtil, merchant, orderId, thirdOrderId)
//        }
//        return null
//    }
//
//    public WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
//        this.okHttpUtil = args[0] as OkHttpUtil
//        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
//        String orderId = args[2]
//        String thirdOrderId = args[3]
//
//        JSONObject paramJSON = new JSONObject();
//        paramJSON.put("account_name", merchant.getMerchantCode())
//        paramJSON.put("merchant_order_id", orderId)
//        paramJSON.put("timestamp", getTimeStr(new Date()))
//
//        String privatekey = merchant.getPrivateKey().replaceAll("-----BEGIN RSA PRIVATE KEY-----","-----BEGIN RSA PRIVATE KEY-----\n")
//
//        Map<String, String> params = new LinkedHashMap<>()
//        String sign = sign(getPrivateKey(privatekey.getBytes()), paramJSON.toJSONString());
//        params.put("data", paramJSON.toJSONString())
//        params.put("signature", sign);
//
//        log.info("ShaHeScript_doTransferQuery_params:{}", JSON.toJSONString(params))
//        GlRequestHeader requestHeader =
//                this.getRequestHeard("", "", orderId, GlActionEnum.WITHDRAW_QUERY.getCode(), merchant.getChannelId(), merchant.getChannelName())
//        String resStr = okHttpUtil.post(merchant.getPayUrl() + "/merchant_api/v1/orders/payment_transfer_query", params, requestHeader)
//        log.info("ShaHeScript_doTransferQuery_resp:{}", resStr)
//        if (StringUtils.isEmpty(resStr)) {
//            return null
//        }
//        JSONObject json = JSON.parseObject(resStr)
//        if (json == null || StringUtils.isEmpty(json.getString("id"))) {
//            return null
//        }
//        WithdrawNotify notify = new WithdrawNotify()
//        notify.setMerchantCode(merchant.getMerchantCode())
//        notify.setMerchantId(merchant.getMerchantId())
//        notify.setMerchantName(merchant.getChannelName())
//        notify.setOrderId(orderId)
//        notify.setThirdOrderId(thirdOrderId)
//        // 支付状态:交易状态，init: 订单刚建立，pending_processing：待处理，completed: 交易完成，processing: 处理中, failed: 失败
//        if (json.getString("status") == "completed") {
//            notify.setStatus(0)
//        } else if (json.getString("status") == "failed") {
//            notify.setStatus(1)
//        } else {
//            notify.setStatus(2)
//        }
//        return notify
//    }
//
//    public BigDecimal balanceQuery(Object[] args) throws GlobalException {
//        this.okHttpUtil = args[0] as OkHttpUtil
//        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
//
//        JSONObject paramJSON = new JSONObject();
//        paramJSON.put("account_name", merchantAccount.getMerchantCode())
//        paramJSON.put("timestamp", getTimeStr(new Date()))
//
//        String privatekey = merchantAccount.getPrivateKey().replaceAll("-----BEGIN RSA PRIVATE KEY-----","-----BEGIN RSA PRIVATE KEY-----\n")
//
//        Map<String, String> params = new LinkedHashMap<>()
//        String sign = sign(getPrivateKey(privatekey.getBytes()), paramJSON.toJSONString());
//        params.put("data", paramJSON.toJSONString())
//        params.put("signature", sign);
//
//        log.info("ShaHeScript_Query_Balance_Params: {}", JSON.toJSONString(params))
//        GlRequestHeader requestHeader =
//                getRequestHeard(null, null, null, GlActionEnum.PAYMENT_QUERY_BALANCE.getCode(),merchantAccount.getChannelId(),merchantAccount.getChannelName())
//        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/merchant_api/v1/balances/query", params,  requestHeader)
//        log.info("ShaHeScript_Query_Balance_resStr: {}", resStr)
//        JSONObject json = JSON.parseObject(resStr)
//        if (json == null) {
//            return null
//        }
//        BigDecimal balance = json.getBigDecimal("balance")
//        return balance == null ? BigDecimal.ZERO : balance
//    }
//
//
//
//    /**
//     * 获取头部信息
//     *
//     * @param userId
//     * @param userName
//     * @param orderId
//     * @return
//     */
//    private GlRequestHeader getRequestHeard(String userId, String userName, String orderId, String code, Integer channelId, String channelName) {
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
//    private static String getTimeStr(Date date) {
//        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");
//        return sdf.format(date);
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
//}