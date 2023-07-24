package com.seektop.fund.payment.xingXingPay

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
import com.seektop.fund.payment.groovy.BaseScript
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
 * @desc 星星支付
 * @date 2021-11-27
 * @auth otto
 */

public class XingXingScript_withdraw {

    private static final Logger log = LoggerFactory.getLogger(XingXingScript_withdraw.class)
    private OkHttpUtil okHttpUtil
    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness

    private static final String SERVER_WITHDRAW_URL = "/Transfers/uniorder"
    private static final String SERVER_QUERY_URL = "/Transfers/getOrder"
    private static final String SERVER_BALANCE_URL = "/Transfers/getBalance"


    WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount account = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

        Map<String, Object> DataContentParms = new LinkedHashMap<>()
        DataContentParms.put("version", "1.0")
        DataContentParms.put("mch_id", account.getMerchantCode())
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
        DataContentParms.put("time_start", DateUtils.format(new Date(), DateUtils.YYYY_MM_DD_HH_MM_SS))

        String toSign = MD5.toAscii(DataContentParms);

        String sign = getSign(toSign, account.getPrivateKey())
        sign = sign.replace("\r\n", "") //防止进入对方系统验签失败
        DataContentParms.put("sign", sign)

        log.info("XingXingScript_Transfer_params: {},url:{}", JSON.toJSONString(DataContentParms) ,account.getPayUrl() )
        GlRequestHeader requestHeader =
                this.getRequestHeard(req.getUserId() + "", req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(), account.getChannelId(), account.getChannelName())

        String resStr = okHttpUtil.post(account.getPayUrl() + SERVER_WITHDRAW_URL, DataContentParms, requestHeader)
        log.info("XingXingScript_Transfer_resStr: {},orderId:{}", resStr)

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
        log.info("XingXingScript_withdrawNotify_resMap:{}", JSON.toJSONString(resMap))

        Map<String, String> signMap = new HashMap<String,String>();
        signMap.put("order_no",resMap.get("order_no"))
        signMap.put("update_time",resMap.get("update_time"))
        signMap.put("out_trade_no",resMap.get("out_trade_no"))
        signMap.put("user_name",resMap.get("user_name"))
        signMap.put("total_fee",resMap.get("total_fee"))
        signMap.put("fee_type",resMap.get("fee_type"))
        signMap.put("mch_id",resMap.get("mch_id"))
        signMap.put("status",resMap.get("status"))
        signMap.put("bank_account",resMap.get("bank_account"))

        String toSign = MD5.toAscii(signMap);
        String sign = getSign(toSign, merchant.getPrivateKey())

        String thirdSign = resMap.get("sign")
        String orderId = resMap.get("out_trade_no")

        if (StringUtils.isNotEmpty(orderId) && sign == thirdSign ) {
            return withdrawQuery(okHttpUtil, merchant, orderId)
        }
        log.info("XingXingScript_withdrawNotify_Sign: 单号为空或验签失败 {}", orderId )
    }


    WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2]

        Map<String, Object> DataContentParms = new LinkedHashMap<>()
        DataContentParms.put("mch_id", merchant.getMerchantCode())
        DataContentParms.put("out_trade_no", orderId)

        String sign = getSign(MD5.toAscii(DataContentParms), merchant.getPrivateKey())
        sign = sign.replace("\r\n", "")
        DataContentParms.put("sign", sign)

        log.info("XingXingScript_TransferQuery_order:{}", JSON.toJSONString(orderId))
        GlRequestHeader requestHeader =
                this.getRequestHeard("", "", orderId, GlActionEnum.WITHDRAW_QUERY.getCode(), merchant.getChannelId(), merchant.getChannelName())

        String resStr = okHttpUtil.post(merchant.getPayUrl() + SERVER_QUERY_URL, DataContentParms, requestHeader)
        log.info("XingXingScript_TransferQuery_resStr:{}", resStr)

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

        //1处理中，2成功，其它失败
        if (dataJSON.getInteger("status") == 2) {
            notify.setStatus(0)
            notify.setRsp("success")

        } else if (dataJSON.getInteger("status") == 1 ) {
            notify.setStatus(2)

        } else {
            notify.setStatus(1)
            notify.setRsp("success")

        }
        return notify
    }


    BigDecimal balanceQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[2], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

        Map<String, String> DataContentParams = new HashMap<>()
        DataContentParams.put("mch_id", merchantAccount.getMerchantCode())
        DataContentParams.put("time_start", DateUtils.format(new Date(), DateUtils.YYYY_MM_DD_HH_MM_SS))

        GlRequestHeader requestHeader =
                getRequestHeard("", "", "", GlActionEnum.PAYMENT_QUERY_BALANCE.getCode(), merchantAccount.getChannelId(), merchantAccount.getChannelName())
        String sign = getSign(MD5.toAscii(DataContentParams), merchantAccount.getPrivateKey())
        sign = sign.replace("\r\n", "")
        DataContentParams.put("sign", sign)

        log.info("XingXingScript_QueryBalance_reqMap: {}", JSON.toJSONString(DataContentParams))
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + SERVER_BALANCE_URL, DataContentParams, requestHeader)
        log.info("XingXingScript_QueryBalance_resStr: {}", resStr)

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