package com.seektop.fund.payment.gtpay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
import com.seektop.common.utils.DateUtils
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
import org.apache.commons.lang.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException

/**
 * GT支付
 */

public class GTScript {
    private static final Logger log = LoggerFactory.getLogger(GTScript.class)

    private OkHttpUtil okHttpUtil

    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness


    WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount account = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        this.glPaymentChannelBankBusiness = com.seektop.fund.payment.groovy.BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

        Map<String, Object> DataContentParms = new LinkedHashMap<>()
        DataContentParms.put("mch_id", account.getMerchantCode())
        DataContentParms.put("version", "1.0")
        DataContentParms.put("pay_type", "bank")
        DataContentParms.put("user_ip", req.getIp().toString())
        DataContentParms.put("user_uid", req.getUserId().toString())
        DataContentParms.put("user_name", req.getName())
        DataContentParms.put("fee_type", "CNY")
        DataContentParms.put("bank_code", glPaymentChannelBankBusiness.getBankCode(req.getBankId(), account.getChannelId()))
        DataContentParms.put("bank_account", req.getCardNo())
        DataContentParms.put("out_trade_no", req.getOrderId())
        DataContentParms.put("total_fee", req.getAmount().subtract(req.getFee()).multiply(BigDecimal.valueOf(100)).setScale(0).toString())
        DataContentParms.put("is_notify", "1")
        DataContentParms.put("notify_url", account.getNotifyUrl() + account.getMerchantId())
        DataContentParms.put("time_start", DateUtils.format(req.getCreateDate(), DateUtils.YYYY_MM_DD_HH_MM_SS))

        String toSign = MD5.toAscii(DataContentParms);

        String sign = getSign(toSign, account.getPrivateKey())

        DataContentParms.put("sign", sign)

        log.info("GTScript_Transfer_params: {}", JSON.toJSONString(DataContentParms))
        GlRequestHeader requestHeader =
                this.getRequestHeard(req.getUserId() + "", req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(), account.getChannelId(), account.getChannelName())
        String resStr = okHttpUtil.post(account.getPayUrl() + "/Transfers/uniorder", DataContentParms, requestHeader)
        log.info("GTScript_Transfer_resStr: {}", resStr)


        WithdrawResult result = new WithdrawResult()
        result.setOrderId(req.getOrderId())
        result.setReqData(JSON.toJSONString(DataContentParms))
        result.setResData(resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null) {
            result.setValid(false)
            result.setMessage("API异常:请联系出款商户确认订单.")
            return result
        }
        if ("200" != json.getString("code") || !json.getString("msg").equals("success")) {
            result.setValid(false)
            result.setMessage(json.getString("msg"))
            return result
        }

        JSONObject dataJson = json.getJSONObject("data")
        if (null == dataJson || dataJson.getInteger("status") != 1) {
            result.setValid(false)
            result.setMessage(json.getString("msg"))
            return result
        }

        result.setValid(true)
        result.setMessage(json.getString("msg"))
        result.setThirdOrderId(dataJson.getString("order_no"))
        return result
    }


    WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        Map<String, String> resMap = args[2] as Map<String, String>
        log.info("GTScript_withdrawNotify_resMap:{}", JSON.toJSONString(resMap))

        String orderId = resMap.get("out_trade_no")
        if (StringUtils.isNotEmpty(orderId)) {
            return withdrawQuery(okHttpUtil, merchant, orderId)
        }
    }


    WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2]

        Map<String, Object> DataContentParms = new LinkedHashMap<>()
        DataContentParms.put("mch_id", merchant.getMerchantCode())
        DataContentParms.put("out_trade_no", orderId)

        String sign = getSign(MD5.toAscii(DataContentParms), merchant.getPrivateKey())
        DataContentParms.put("sign", sign)

        log.info("GTScript_TransferQuery_order:{}", JSON.toJSONString(orderId))
        GlRequestHeader requestHeader =
                this.getRequestHeard("", "", orderId, GlActionEnum.WITHDRAW_QUERY.getCode(), merchant.getChannelId(), merchant.getChannelName())
        String resStr = okHttpUtil.post(merchant.getPayUrl() + "/Transfers/getOrder", DataContentParms, requestHeader)
        log.info("GTScript_TransferQuery_resStr:{}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || json.getInteger("code").intValue() != 200) {
            return null
        }
        JSONObject dataJSON = json.getJSONObject("data")
        if (dataJSON == null) {
            return null
        }
        log.info(JSON.toJSONString(dataJSON))
        WithdrawNotify notify = new WithdrawNotify()
        notify.setMerchantCode(merchant.getMerchantCode())
        notify.setMerchantId(merchant.getMerchantId())
        notify.setMerchantName(merchant.getChannelName())
        notify.setOrderId(dataJSON.getString("out_trade_no"))
        notify.setThirdOrderId(dataJSON.getString("order_no"))
        if (dataJSON.getInteger("status") == 2) {//订单状态判断标准：1 处理中 2 已处理 3 已驳回 4 已退单
            notify.setStatus(0)
        } else if (dataJSON.getInteger("status") == 3 || dataJSON.getInteger("status") == 4) {
            notify.setStatus(1)
        } else {
            notify.setStatus(2)
        }
        log.info(JSON.toJSONString(notify))
        return notify
    }


    BigDecimal balanceQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        this.glPaymentChannelBankBusiness = com.seektop.fund.payment.groovy.BaseScript.getResource(args[2], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

        Map<String, String> DataContentParams = new HashMap<>()
        DataContentParams.put("mch_id", merchantAccount.getMerchantCode())
        DataContentParams.put("time_start", DateUtils.format(new Date(), DateUtils.YYYY_MM_DD_HH_MM_SS))

        GlRequestHeader requestHeader =
                getRequestHeard("", "", "", GlActionEnum.PAYMENT_QUERY_BALANCE.getCode(), merchantAccount.getChannelId(), merchantAccount.getChannelName())
        String sign = getSign(MD5.toAscii(DataContentParams), merchantAccount.getPrivateKey())
        DataContentParams.put("sign", sign)

        log.info("GT_Script_QueryBalance_reqMap: {}", JSON.toJSONString(DataContentParams))
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/Transfers/getBalance", DataContentParams, requestHeader)
        log.info("GT_Script_QueryBalance_resStr: {}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json != null && "200" == json.getString("code")) {
            JSONObject dataJSON = json.getJSONObject("data")
            if (dataJSON == null) {
                return BigDecimal.ZERO
            }
            return dataJSON.getBigDecimal("balance") == null ? BigDecimal.ZERO : dataJSON.getBigDecimal("balance")
        }
        return BigDecimal.ZERO
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

    public String getSign(String value, String accessToken)
            throws NoSuchAlgorithmException, InvalidKeyException, UnsupportedEncodingException {
        Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
        SecretKeySpec secret_key = new SecretKeySpec(accessToken.getBytes(), "HmacSHA256")
        sha256_HMAC.init(secret_key);
        byte[] bytes = sha256_HMAC.doFinal(value.getBytes());
        return Base64.encodeBase64String(bytes)
    }


}