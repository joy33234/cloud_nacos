package com.seektop.fund.payment.nuoyapay

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
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.util.ObjectUtils

import java.math.RoundingMode

/**
 * @desc 诺亚支付
 * @date 2021-09-14
 * @auth joy
 */
public class NuoYaV2Script_withdraw {


    private static final Logger log = LoggerFactory.getLogger(NuoYaV2Script_withdraw.class)

    private OkHttpUtil okHttpUtil

    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness


    public WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount account = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        this.glPaymentChannelBankBusiness = com.seektop.fund.payment.groovy.BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        WithdrawResult result = new WithdrawResult()
        result.setOrderId(req.getOrderId())


        Map<String, String> params = new LinkedHashMap<>()
        params.put("merchantNo", account.getMerchantCode())
        params.put("merchantOrder", req.getOrderId())
        params.put("channel", "001001101")
        params.put("amount", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString())
        params.put("currency", "CNY")
        params.put("bankCode", glPaymentChannelBankBusiness.getBankCode(req.getBankId(), account.getChannelId()))
        params.put("bankAccountName", req.getName())
        params.put("bankAccountNo", req.getCardNo())
        String dateTime = DateUtils.format(new Date(), DateUtils.YYYYMMDDHHMMSS);
        params.put("dateTime", dateTime.substring(2,dateTime.length()))
        params.put("signature", getSign(params, account.getPrivateKey()))
        params.put("bankBranch", "上海市")

        Map<String, String> headParams = new HashMap<>();
        headParams.put("Content-Type", "application/x-www-form-urlencoded")
        headParams.put("charset", "utf-8")

        result.setReqData(JSON.toJSONString(params))
        String token = getToken(account);
        if (StringUtils.isEmpty(token)) {
            result.setResData("")
            result.setValid(false)
            result.setMessage("获取三方token异常")
            return result
        }
        headParams.put("Authorization", "Bearer " + token)

        log.info("NuoYaScript_doTransfer_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                this.getRequestHeard(req.getUserId() + "", req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(), account.getChannelId(), account.getChannelName())

        String resStr = okHttpUtil.post(account.getPayUrl() + "/api/transaction/withdrawal ", params, requestHeader, headParams)
        log.info("NuoYaScript_doTransfer_resp:{}", resStr)
        JSONObject json = JSON.parseObject(resStr)


        if (StringUtils.isEmpty(resStr)) {
            result.setValid(false)
            result.setMessage("API异常:请联系出款商户确认订单.")
            return result
        }

        if ("0" != (json.getString("code"))) {
            result.setValid(false)
            result.setMessage(json.getString("message"))
            return result
        }
        JSONObject dataJSON = json.getJSONObject("data");
        if (ObjectUtils.isEmpty(dataJSON)) {
            result.setValid(false)
            result.setMessage(json.getString("message"))
            return result
        }
        result.setThirdOrderId(dataJSON.getString("orderNo"))
        result.setValid(true)
        result.setMessage("ok")
        return result
    }

    public WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        Map<String, String> resMap = args[2] as Map<String, String>
        log.info("NuoYaScript_doTransferNotify_resp:{}", JSON.toJSONString(resMap))
        JSONObject json = JSONObject.parseObject(resMap.get("reqBody"))
        String orderId = json.getString("merchantOrder")
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
        params.put("merchantNo", merchant.getMerchantCode())
        params.put("merchantOrder", orderId)
        String dateTime = DateUtils.format(new Date(), DateUtils.YYYYMMDDHHMMSS);
        params.put("dateTime", dateTime.substring(2,dateTime.length()))
        params.put("signature", getSign(params, merchant.getPrivateKey()))

        Map<String, String> headParams = new HashMap<>();
        headParams.put("Content-Type", "application/x-www-form-urlencoded")
        headParams.put("charset", "utf-8")
        String token = getToken(merchant);
        if (StringUtils.isEmpty(token)) {
            return null
        }
        headParams.put("Authorization", "Bearer " + getToken(merchant))

        log.info("NuoYaScript_doTransferQuery_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                this.getRequestHeard("", "", orderId, GlActionEnum.WITHDRAW_QUERY.getCode(), merchant.getChannelId(), merchant.getChannelName())
        String resStr = okHttpUtil.post(merchant.getPayUrl() + "/api/transaction/withdrawal/verify", params, requestHeader, headParams)
        log.info("NuoYaScript_doTransferQuery_resp:{}", resStr)
        if (StringUtils.isEmpty(resStr)) {
            return null
        }
        JSONObject json = JSON.parseObject(resStr)
        if (ObjectUtils.isEmpty(json) || "0" != (json.getString("code"))) {
            return null
        }
        JSONObject dataJSON = json.getJSONObject("data")
        if (dataJSON == null) {
            return null
        }
        WithdrawNotify notify = new WithdrawNotify()
        notify.setMerchantCode(merchant.getMerchantCode())
        notify.setMerchantId(merchant.getMerchantId())
        notify.setMerchantName(merchant.getChannelName())
        notify.setOrderId(orderId)
        notify.setThirdOrderId(dataJSON.getString("orderNo"))
        // 支付状态:status  0:新建訂單  1:交易進行中  2:交易成功  3:交易失敗  4:交易已過期失效  5:發生例外錯誤
        if (dataJSON.getString("status") == ("2")) {
            notify.setStatus(0)
            notify.setRsp("SUCCESS")
        } else if (dataJSON.getString("status") == ("3") || dataJSON.getString("status") == ("7")
            || dataJSON.getString("status") == ("8")) {
            notify.setStatus(1)
            notify.setRsp("SUCCESS")
        } else {
            notify.setStatus(2)
        }
        return notify
    }

    public BigDecimal balanceQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount

        Map<String, String> params = new LinkedHashMap<>()
        params.put("merchantNo", merchantAccount.getMerchantCode())
        String dateTime = DateUtils.format(new Date(), DateUtils.YYYYMMDDHHMMSS);
        params.put("dateTime", dateTime.substring(2,dateTime.length()))
        params.put("signature", getSign(params, merchantAccount.getPrivateKey()))

        Map<String, String> headParams = new HashMap<>();
        headParams.put("Content-Type", "application/x-www-form-urlencoded")
        headParams.put("charset", "utf-8")
        headParams.put("Authorization", "Bearer " + getToken(merchantAccount))

        log.info("NuoYaScript_Query_Balance_Params: {}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                getRequestHeard(null, null, null, GlActionEnum.PAYMENT_QUERY_BALANCE.getCode(),merchantAccount.getChannelId(),merchantAccount.getChannelName())
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/api/get/balance", params,  requestHeader, headParams)
        log.info("NuoYaScript_Query_Balance_resStr: {}", resStr)

        if (StringUtils.isEmpty(resStr)) {
            return BigDecimal.ZERO
        }
        JSONObject json = JSON.parseObject(resStr)
        if (ObjectUtils.isEmpty(json) || "0" != (json.getString("code"))) {
            return null
        }
        JSONObject dataJSON = json.getJSONObject("data");
        if (dataJSON == null) {
            return BigDecimal.ZERO
        }
        BigDecimal balance = dataJSON.getBigDecimal("balance")
        return balance == null ? BigDecimal.ZERO : balance
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

    public String getToken(GlWithdrawMerchantAccount account) {
        Map<String, String> params = new LinkedHashMap<>()
        params.put("merchantNo", account.getMerchantCode())
        String dateTime = DateUtils.format(new Date(), DateUtils.YYYYMMDDHHMMSS);
        params.put("dateTime", dateTime.substring(2,dateTime.length()))
        params.put("signature", getSign(params, account.getPublicKey()))
        GlRequestHeader requestHeader =
                getRequestHeard("", "", "", GlActionEnum.RECHARGE.getCode(), account.getChannelId(), account.getChannelName())

        Map<String, String> headParams = new HashMap<>();
        headParams.put("Content-Type", "application/x-www-form-urlencoded")
        headParams.put("charset", "utf-8")

        log.info("NuoYaScript_token_params:{}:{}", JSON.toJSONString(params), JSON.toJSONString(headParams))
        String resStr = okHttpUtil.post(account.getPayUrl() + "/api/get/auth", params, requestHeader, headParams)
        log.info("NuoYaScript_token_resp:{}", resStr)

        JSONObject json = JSONObject.parseObject(resStr);
        if (ObjectUtils.isEmpty(json) || json.getString("code") != "0") {
            return null
        }
        JSONObject dataJSON = json.getJSONObject("data");
        if (ObjectUtils.isEmpty(dataJSON) || StringUtils.isEmpty(dataJSON.getString("auth_token"))) {
            return null
        }
        return dataJSON.getString("auth_token");
    }

    /**
     * 计算签名
     * @param params
     * @param privateKey
     * @return
     */
    private String getSign(Map<String, String> params, String privateKey) {
        StringBuffer sign = new StringBuffer()
        for (Map.Entry<String, String> item : params.entrySet()) {
            if (StringUtils.isNotEmpty(item.getKey())) {
                sign.append(item.getValue())
            }
        }
        sign.append(privateKey)
        return MD5.md5(sign.toString())
    }
}