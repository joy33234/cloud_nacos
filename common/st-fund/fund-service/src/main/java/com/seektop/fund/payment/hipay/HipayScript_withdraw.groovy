package com.seektop.fund.payment.hipay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
import com.seektop.enumerate.GlActionEnum
import com.seektop.exception.GlobalException
import com.seektop.fund.business.GlPaymentChannelBankBusiness
import com.seektop.fund.model.GlWithdraw
import com.seektop.fund.model.GlWithdrawMerchantAccount
import com.seektop.fund.payment.PaymentMerchantEnum
import com.seektop.fund.payment.WithdrawNotify
import com.seektop.fund.payment.WithdrawResult
import com.seektop.fund.payment.cfpay.StringExtension
import com.seektop.fund.payment.groovy.BaseScript
import com.seektop.fund.payment.groovy.ResourceEnum
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.math.RoundingMode
import java.security.InvalidKeyException

/**
 * HipayScript  接口
 *
 * @author joy
 */
public class HipayScript_withdraw {

    private static final Logger log = LoggerFactory.getLogger(HipayScript_withdraw.class)

    private OkHttpUtil okHttpUtil

    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness


    public WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

        Map<String, Object> params = new LinkedHashMap<String, Object>()
        params.put("code", req.getOrderId())
        params.put("bank_code", glPaymentChannelBankBusiness.getBankCode(req.getBankId(), merchantAccount.getChannelId()))
        params.put("bank", glPaymentChannelBankBusiness.getBankName(req.getBankId(), merchantAccount.getChannelId()))
        params.put("bank_branch", "上海市")
        params.put("account_number", req.getCardNo())
        params.put("name", req.getName())
        params.put("amount", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString())
        params.put("callback", merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId())
        params.put("_signature", getSign(params, merchantAccount.getPrivateKey()))

        Map<String, String> headParams = new HashMap<String, String>()
        headParams.put("Authorization", "Bearer " + merchantAccount.getPrivateKey())
        headParams.put("Accept", "application/json")
        headParams.put("content-type", "application/json")


        log.info("HiPayScript_doTransfer_resMap = {}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = this.getRequestHeard(req.getUserId() + "", req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode())
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/api/payment-transaction", params, requestHeader, headParams)

        log.info("HiPayScript_doTransfer_resStr = {}", resStr)

        WithdrawResult result = new WithdrawResult()
        result.setOrderId(req.getOrderId())
        result.setReqData(JSON.toJSONString(params))
        result.setResData(resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || json.getString("state") != "new") {
            result.setValid(false)
            result.setMessage(json == null ? "API异常:请联系出款商户确认订单." : json.getString("message"))
            return result
        }
        result.setValid(true)
        result.setMessage("")
        return result
    }

    public WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        Map<String, String> resMap = args[2] as Map<String, String>
        log.info("HiPayScript_transfer_notify_resMap = {}", JSON.toJSONString(resMap))
        String orderId = resMap.get("code")
        String thirdOrderId = resMap.get("uuid")
        if (StringUtils.isNotEmpty(orderId) && StringUtils.isNotEmpty(thirdOrderId)) {
            return withdrawQuery(okHttpUtil, merchant, orderId, thirdOrderId)
        } else {
            return null
        }
    }

    public WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2]
        String thirdOrderId = args[3]
        Map<String, String> headParams = new HashMap<String, String>()
        headParams.put("Authorization", "Bearer " + merchant.getPrivateKey())
        headParams.put("Accept", "application/json")
        headParams.put("content-type", "application/json")


        Map<String, Object> params = new LinkedHashMap<String, Object>()
        params.put("code", orderId)
        params.put("uuid", thirdOrderId)//平台订单号

        log.info("HiPayScript_TransferQuery_Params = {}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = this.getRequestHeard("", "", orderId, GlActionEnum.WITHDRAW_QUERY.getCode())
        String resStr = okHttpUtil.get(merchant.getPayUrl() + "/api/payment-transaction/" + orderId, params, requestHeader, headParams)
        log.info("HiPayScript_TransferQuery_resStr = {}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        WithdrawNotify notify = new WithdrawNotify()
        if (json != null) {
            notify.setAmount(json.getBigDecimal("amount"))
            notify.setMerchantCode(merchant.getMerchantCode())
            notify.setMerchantId(merchant.getMerchantId())
            notify.setMerchantName(merchant.getChannelName())
            notify.setOrderId(json.getString("code"))
            notify.setThirdOrderId("")
            //new, processing, completed, failed, rejected, refund
            if (json.getString("state") == ("completed")) {
                notify.setStatus(0)
                notify.setRsp("ok")
            } else if (json.getString("state") == ("failed") || json.getString("state") == ("rejected")
                    || json.getString("state") == ("refund")) {
                notify.setStatus(1)
                notify.setRsp("ok")
            } else {
                notify.setStatus(2)
            }
        }
        return notify
    }

    public BigDecimal balanceQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        Map<String, String> headParams = new HashMap<String, String>()
        headParams.put("Authorization", "Bearer " + merchantAccount.getPrivateKey())
        headParams.put("Accept", "application/json")

        log.info("HiPayScript_queryBalance_headParams = {}", headParams)
        GlRequestHeader requestHeader = this.getRequestHeard("", "", "", GlActionEnum.PAYMENT_QUERY_BALANCE.getCode())
        String resStr = okHttpUtil.get(merchantAccount.getPayUrl() + "/api/payment-transaction/balance", null, requestHeader, headParams)
        log.info("HiPayScript_queryBalance_resStr = {}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json != null) {
            return json.getBigDecimal("balance") == null ? BigDecimal.ZERO : json.getBigDecimal("balance")
        }
        return BigDecimal.ZERO
    }


    /**
     * 签名
     *
     * @param value
     * @param accessToken
     * @return
     * @throws NoSuchAlgorithmException
     * @throws InvalidKeyException* @throws UnsupportedEncodingException
     */

    public String getSign(LinkedHashMap<String, String> values, String accessToken) {
        LinkedHashMap<String, String> temp = new LinkedHashMap<String, String>();
        for (String key : values.keySet()) {
            String value = values.get(key);
            if (!key.startsWith("_") && value != null && !value.isEmpty()) {
                temp.put((String) key, value);
            }
        }

        return make(encode(temp), accessToken);
    }

    public String make(String data, String accessToken) {
        try {
            Mac sha256 = Mac.getInstance("HmacSHA256");
            sha256.init(new SecretKeySpec(accessToken.getBytes(), "HmacSHA256"));

            return base64UrlSafe(Base64.getEncoder().encodeToString(sha256.doFinal(data.getBytes())));
        } catch (Exception e) {
            e.printStackTrace();

            return "";
        }
    }

    private String base64UrlSafe(String hash) {
        return hash.replace("+", "-").replace("/", "_").replace("=", "");
    }

    public static String encode(LinkedHashMap<String, String> data) {
        StringBuilder json = new StringBuilder();

        json.append("{");
        for (Object key : data.keySet()) {
            json.append(getJSONValue((String) key) + ":");
            json.append(getJSONValue(data.get(key)));
            json.append(",");
        }
        json.deleteCharAt(json.length() - 1);
        json.append("}");

        return json.toString();
    }

    private static String getJSONValue(String s) {
        return s == null ? "null" : "\"" + StringExtension.utf8ToUnicode(addSlashes(s)) + "\"";
    }

    private static String addSlashes(String s) {
        s = s.replaceAll("\\\\", "\\\\\\\\");
        s = s.replaceAll("\\n", "\\\\n");
        s = s.replaceAll("\\r", "\\\\r");
        s = s.replaceAll("\\00", "\\\\0");
        s = s.replaceAll("'", "\\\\'");
        s = s.replaceAll("/", "\\\\/");

        return s;
    }


    /**
     * 获取头部信息
     *
     * @param userId
     * @param userName
     * @param orderId
     * @return
     */
    private GlRequestHeader getRequestHeard(String userId, String userName, String orderId, String code) {
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(code)
                .channelId(PaymentMerchantEnum.HI_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.HI_PAY.getPaymentName())
                .userId(userId)
                .userName(userName)
                .tradeId(orderId)
                .build()
        return requestHeader
    }
}
