package com.seektop.fund.payment.yuanshengpay

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
import org.apache.commons.codec.binary.Base64
import org.apache.commons.lang3.ObjectUtils
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec

/**
 * @desc 源盛支付
 * @date 2021-06-10
 * @auth joy
 */
public class YuanShengScript_withdraw {


    private static final Logger log = LoggerFactory.getLogger(YuanShengScript_withdraw.class)

    private OkHttpUtil okHttpUtil

    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness


    public WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount account = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        this.glPaymentChannelBankBusiness = com.seektop.fund.payment.groovy.BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

        Map<String, String> params = new LinkedHashMap<>()
        //公共参数
        params.put("charset", "1")
        params.put("version", "v1.0")
        params.put("language", "1")
        params.put("signType", "3")
        params.put("timestamp", System.currentTimeMillis().toString())

        //业务参数
        params.put("accessType", "1")
        params.put("merchantId", account.getMerchantCode())
        params.put("merchantNme", "st")
        params.put("orderNo",req.getOrderId())
        params.put("bankNo", glPaymentChannelBankBusiness.getBankCode(req.getBankId(), account.getChannelId()))
        params.put("acctNo", req.getCardNo())
        params.put("acctName", req.getName())
        params.put("acctType", "1")
        params.put("acctPhone", "13611111111")
        params.put("ccy", "CNY")
        params.put("transAmt", req.getAmount().subtract(req.getFee()).multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.DOWN).toString())
        params.put("transUsage","withdraw")
        params.put("notifyUrl", account.getNotifyUrl() + account.getMerchantId())


        Map<String, String> params2 = new LinkedHashMap<>()
        for (Map.Entry<String,String> param : params.entrySet()) {
            params2.put(param.getKey(),URLEncoder.encode(param.getValue(), "UTF-8"))
        }
        String sign = signSHA1(MD5.toAscii(params2).getBytes(), account.getPrivateKey())
        params.put("signMsg", sign)

        log.info("YuanShengScript_doTransfer_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                this.getRequestHeard(req.getUserId() + "", req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(), account.getChannelId(), account.getChannelName())
        String resStr = okHttpUtil.post(account.getPayUrl() + "/svrmer/tdpay-web-mer-portal/tdpay/webmer/singlePay.do", params, requestHeader)
        log.info("YuanShengScript_doTransfer_resp:{}", resStr)
        JSONObject json = JSON.parseObject(resStr)

        WithdrawResult result = new WithdrawResult()
        result.setOrderId(req.getOrderId())
        result.setReqData(JSON.toJSONString(params))
        result.setResData(resStr)
        if (StringUtils.isEmpty(resStr)) {
            result.setValid(false)
            result.setMessage("API异常:请联系出款商户确认订单.")
            return result
        }

        if ("01000000" != (json.getString("rspCod")) || ObjectUtils.isEmpty(json.getString("orderId"))) {
            result.setValid(false)
            result.setMessage(json.getString("rspMsg"))
            return result
        }
        req.setMerchantId(account.getMerchantId())
        result.setValid(true)
        result.setMessage(json.getString("rspMsg"))
        return result
    }

    public WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        Map<String, String> resMap = args[2] as Map<String, String>
        log.info("YuanShengScript_doTransferNotify_resp:{}", JSON.toJSONString(resMap))
        String orderId = resMap.get("orderNo");
        if (StringUtils.isEmpty(orderId)) {
            JSONObject json = JSON.parseObject(resMap.get("reqBody"))
            orderId = json.getString("orderNo")
        }
        if (StringUtils.isNotEmpty(orderId)) {
            return this.withdrawQuery(okHttpUtil, merchant, orderId, args[3])
        }
        return null
    }

    public WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2]

        Map<String, String> params = new LinkedHashMap<>()
        //公共参数
        params.put("charset", "1")
        params.put("version", "v1.0")
        params.put("language", "1")
        params.put("signType", "3")
        params.put("timestamp", System.currentTimeMillis().toString())

        //业务参数
        params.put("accessType", "1")
        params.put("merchantId", merchant.getMerchantCode())
        params.put("merchantNme", "st")
        params.put("orderNo", orderId)

        Map<String, String> params2 = new LinkedHashMap<>()
        for (Map.Entry<String,String> param : params.entrySet()) {
            params2.put(param.getKey(),URLEncoder.encode(param.getValue(), "UTF-8"))
        }
        String sign = signSHA1(MD5.toAscii(params2).getBytes(), merchant.getPrivateKey())
        params.put("signMsg", sign)

        log.info("YuanShengScript_doTransferQuery_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                this.getRequestHeard("", "", orderId, GlActionEnum.WITHDRAW_QUERY.getCode(), merchant.getChannelId(), merchant.getChannelName())
        String resStr = okHttpUtil.post(merchant.getPayUrl() + "/svrmer/tdpay-web-mer-portal/tdpay/webmer/singlePayQuery.do", params, requestHeader)
        log.info("YuanShengScript_doTransferQuery_resp:{}", resStr)
        if (StringUtils.isEmpty(resStr)) {
            return null
        }
        JSONObject json = JSON.parseObject(resStr)
        if (json == null || "01000000" != (json.getString("rspCod"))) {
            return null
        }

        WithdrawNotify notify = new WithdrawNotify()
        notify.setMerchantCode(merchant.getMerchantCode())
        notify.setMerchantId(merchant.getMerchantId())
        notify.setMerchantName(merchant.getChannelName())
        notify.setOrderId(orderId)
        notify.setThirdOrderId(json.getString("orderId"))
        //00-交易成功；01-交易失败；03-支付中,待查；
        if (json.getString("transStatus") == ("00")) {
            notify.setStatus(0)
        } else if (json.getString("transStatus") == ("01")) {
            notify.setStatus(1)
        } else {
            notify.setStatus(2)
        }
        log.info(JSON.toJSONString(notify))
        return notify
    }

    public BigDecimal balanceQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount

        Map<String, String> params = new LinkedHashMap<>()
        //公共参数
        params.put("charset", "1")
        params.put("version", "v1.0")
        params.put("language", "1")
        params.put("signType", "3")
        params.put("timestamp", System.currentTimeMillis().toString())

        //业务参数
        params.put("accessType", "1")
        params.put("merchantId", merchantAccount.getMerchantCode())

        Map<String, String> params2 = new LinkedHashMap<>()
        for (Map.Entry<String,String> param : params.entrySet()) {
            params2.put(param.getKey(),URLEncoder.encode(param.getValue(), "UTF-8"))
        }
        String sign = signSHA1(MD5.toAscii(params2).getBytes(), merchantAccount.getPrivateKey())
        params.put("signMsg", sign)

        log.info("YuanShengScript_queryBalance_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                getRequestHeard("", "", "", GlActionEnum.PAYMENT_QUERY_BALANCE.getCode(), merchantAccount.getChannelId(), merchantAccount.getChannelName())

        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/svrmer/tdpay-web-mer-portal/tdpay/batpay/CibDf/merAccBalQuery.do", params, requestHeader)
        log.info("YuanShengScript_queryBalance_resp:{}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || "01000000" != (json.getString("rspCod"))) {
            return BigDecimal.ZERO
        }
        String amount = json.getString("balAmt").replaceAll(",","");
        return StringUtils.isEmpty(amount) ? BigDecimal.ZERO : new BigDecimal(amount);
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

    public static String signSHA1(byte[] data, String privateKey) throws Exception {
        byte[] keyBytes = Base64.decodeBase64(privateKey);
        PKCS8EncodedKeySpec pkcs8KeySpec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PrivateKey privateK = keyFactory.generatePrivate(pkcs8KeySpec);
        Signature signature = Signature.getInstance("SHA1WithRSA");
        signature.initSign(privateK);
        signature.update(data);
        return Base64.encodeBase64URLSafeString(signature.sign());
    }
}