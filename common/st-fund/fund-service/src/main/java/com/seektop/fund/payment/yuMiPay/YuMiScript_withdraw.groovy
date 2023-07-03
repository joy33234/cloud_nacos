package com.seektop.fund.payment.yuMiPay

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

import java.math.RoundingMode
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.spec.InvalidKeySpecException
import java.security.spec.PKCS8EncodedKeySpec

/**
 * @desc 玉米代付
 * @date 2021-10-20
 * @auth Otto
 */
class YuMiScript_withdraw {

    private static final Logger log = LoggerFactory.getLogger(YuMiScript_withdraw.class)

    private static final String SERVER_WITHDRAW_URL = "/api/pglite/sporder/sigwithdraw"
    private static final String SERVER_QUERY_URL = "/api/pglite/sporder/sigquery"
    private static final String SERVER_BALANCE_URL = "/api/pglite/spset/sigquery"
    private OkHttpUtil okHttpUtil

    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness

    WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

        Map<String, String> params = new LinkedHashMap<>()
        String reqTimestamp = System.currentTimeMillis() + "";
        params.put("spid", merchantAccount.getMerchantCode())
        params.put("amount", req.getAmount().subtract(req.getFee()).multiply(new BigDecimal(100)).setScale(0, RoundingMode.DOWN).toString())
        //单位 分
        params.put("ordertype", "5008")
        params.put("timestamp", reqTimestamp)
        params.put("cpparam", req.getOrderId())
        String toSign = MD5.toSign(params)
        String rsaKey = merchantAccount.getPrivateKey().replace("\n", "").replace(" ", "");

        params.put("bankcard_number", req.getCardNo())
        params.put("bankcard_name", glPaymentChannelBankBusiness.getBankName(req.getBankId(), merchantAccount.getChannelId()))
        params.put("bankcard_owner", req.getName())
        params.put("bankcard_branch", "上海分行")
        String rsaToSign = MD5.toSign(params)

        params.put("rsig", rsaSign(rsaToSign, rsaKey));
        params.put("cburl", merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId())

        params.put("sig", MD5.md5(toSign + "&key=" + merchantAccount.getPublicKey()))
        log.info("YuMiScript_Transfer_params: {}", params)
        GlRequestHeader requestHeader = getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(), merchantAccount.getChannelId(), merchantAccount.getChannelName())

        String resStr = okHttpUtil.postJSON(merchantAccount.getPayUrl() + SERVER_WITHDRAW_URL, JSON.toJSONString(params), requestHeader)
        log.info("YuMiScript_Transfer_resStr: {}", resStr)

        WithdrawResult result = new WithdrawResult()
        result.setOrderId(req.getOrderId())
        result.setReqData(JSON.toJSONString(params))
        result.setResData(resStr)

        JSONObject json = JSON.parseObject(resStr);

        //status: 100 下单成功
        if (json == null || 100 != json.getInteger("status")) {
            result.setValid(false)
            result.setMessage(json == null ? "API异常:请联系出款商户确认订单." : json.getString("message"))
            return result
        }

        result.setValid(true)
        result.setMessage("")
        result.setThirdOrderId(json.getString("orderid"))
        return result
    }

    WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        //备注：三方表示提现没有主动回调 2021/10/16
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        Map<String, String> resMap = args[2] as Map<String, String>
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

        log.info("YuMiScript_WithdrawNotify_resMap:{}", JSON.toJSONString(resMap))

        if (StringUtils.isNotEmpty(resMap.get("orderId"))) {
            return withdrawQuery(okHttpUtil, merchant, resMap.get("orderId"))

        }
    }

    WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2] as String

        Map<String, String> params = new LinkedHashMap<String, String>()
        params.put("orderid", "NONE")
        params.put("timestamp", System.currentTimeMillis() + "")
        String toSign = MD5.toSign(params) + "&key=" + merchant.getPublicKey()

        params.put("sig", MD5.md5(toSign))
        params.put("spid", merchant.getMerchantCode())
        params.put("cpparam", orderId)

        log.info("YuMiScript_TransferQuery_reqMap:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = getRequestHeard(null, null, orderId, GlActionEnum.WITHDRAW_QUERY.getCode(), merchant.getChannelId(), merchant.getChannelName())

        String resStr = okHttpUtil.postJSON(merchant.getPayUrl() + SERVER_QUERY_URL, JSON.toJSONString(params), requestHeader)
        log.info("YuMiScript_TransferQuery_resStr:{}", resStr)

        if (StringUtils.isEmpty(resStr)) {
            return null
        }

        JSONObject json = JSON.parseObject(resStr)
        WithdrawNotify notify = new WithdrawNotify()
        // 网关返回码： 0=成功，其他失败
        if (100 == json.getInteger("status")) {
            notify.setMerchantCode(merchant.getMerchantCode())
            notify.setMerchantId(merchant.getMerchantId())
            notify.setMerchantName(merchant.getChannelName())
            notify.setOrderId(orderId)
            notify.setThirdOrderId("")

            String payStatus = json.getString("order_status_str")

            //pending	 处理中
            //failed	 失败
            //successful 成功
            //cancelled  取消
            if (payStatus == "successful") {
                notify.setStatus(0)
                notify.setRsp("SUCCESS")

            } else if (payStatus == "failed" || payStatus == "cancelled") {
                notify.setStatus(1)
                notify.setRsp("SUCCESS")

            } else {
                notify.setStatus(2)

            }
        }
        return notify
    }

    BigDecimal balanceQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[2], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

        Map<String, String> params = new LinkedHashMap<>()
        params.put("spid", merchantAccount.getMerchantCode())
        params.put("timestamp", System.currentTimeMillis() + "")

        String toSign = MD5.toSign(params) + "&key=" + merchantAccount.getPublicKey()
        params.put("sig", MD5.md5(toSign))

        log.info("YuMiScript_QueryBalance_reqMap: {}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                getRequestHeard("", "", "", GlActionEnum.PAYMENT_QUERY_BALANCE.getCode(), merchantAccount.getChannelId(), merchantAccount.getChannelName())

        String resStr = okHttpUtil.postJSON(merchantAccount.getPayUrl() + SERVER_BALANCE_URL, JSONObject.toJSONString(params), requestHeader)
        log.info("YuMiScript_QueryBalance_resStr: {}", resStr)

        JSONObject json = JSON.parseObject(resStr)

        //网关返回码：100=成功，其他失败
        if (json == null || json.getInteger("status") != 100) {
            return BigDecimal.ZERO
        }

        BigDecimal balance = json.getBigDecimal("balance").divide(BigDecimal.valueOf(100)).setScale(2, RoundingMode.DOWN);
        return balance == null ? BigDecimal.ZERO : balance
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


    // 获取私钥
    private static PrivateKey getPrivateKeyFromPKCS8(byte[] data) throws Exception {
        KeyFactory keyFactory = KeyFactory.getInstance("RSA")
        byte[] encodedKey = org.apache.commons.net.util.Base64.decodeBase64(data)
        return keyFactory.generatePrivate(new PKCS8EncodedKeySpec(encodedKey))
    }

    // 生成签名
    static String rsaSign(String content, String privateKey) {
        try {
            PrivateKey priKey = getPrivateKeyFromPKCS8(privateKey.getBytes())
            java.security.Signature signature = java.security.Signature.getInstance("SHA256WithRSA")
            signature.initSign(priKey)
            signature.update(content.getBytes("utf-8"))
            byte[] signed = signature.sign()
            return new String(org.apache.commons.net.util.Base64.encodeBase64(signed))
        } catch (InvalidKeySpecException ie) {
            log.warn("玉米代付 RSA私钥格式不正确", ie)
        } catch (Exception e) {
            log.warn("玉米代付 RSA签名失败:", e)
        }
        return ""
    }

}