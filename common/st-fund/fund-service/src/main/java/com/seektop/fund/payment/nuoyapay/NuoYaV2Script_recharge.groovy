package com.seektop.fund.payment.nuoyapay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
import com.seektop.common.utils.DateUtils
import com.seektop.common.utils.MD5
import com.seektop.constant.FundConstant
import com.seektop.enumerate.GlActionEnum
import com.seektop.exception.GlobalException
import com.seektop.fund.model.GlPaymentMerchantApp
import com.seektop.fund.model.GlPaymentMerchantaccount
import com.seektop.fund.payment.BankInfo
import com.seektop.fund.payment.GlRechargeResult
import com.seektop.fund.payment.RechargeNotify
import com.seektop.fund.payment.RechargePrepareDO
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
public class NuoYaV2Script_recharge {


    private static final Logger log = LoggerFactory.getLogger(NuoYaV2Script_recharge.class)

    private OkHttpUtil okHttpUtil


    public RechargeNotify result(Object[] args) throws GlobalException {
        return null
    }

    public void pay(Object[] args) {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantApp merchant = args[1] as GlPaymentMerchantApp
        GlPaymentMerchantaccount account = args[2] as GlPaymentMerchantaccount
        RechargePrepareDO req = args[3] as RechargePrepareDO
        GlRechargeResult result = args[4] as GlRechargeResult
        String channel = ""
         if (FundConstant.PaymentType.BANKCARD_TRANSFER == merchant.getPaymentId()) {
             channel = "001001001"//卡卡
        }
        if (StringUtils.isEmpty(channel)) {
            result.setErrorMsg("支付方式不支持")
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            return
        }
        prepareToScan(merchant, account, req, result, channel)
    }


    private void prepareToScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result, String channel) throws GlobalException {
        Map<String, String> params = new LinkedHashMap<>()
        params.put("merchantNo", account.getMerchantCode())
        params.put("merchantUser", req.getUserId().toString())
        params.put("merchantOrder",req.getOrderId())
        params.put("channel",channel)
        params.put("amount", req.getAmount().setScale(2, RoundingMode.DOWN).toString())
        params.put("currency", "CNY")
        String dateTime = DateUtils.format(new Date(), DateUtils.YYYYMMDDHHMMSS);
        params.put("dateTime", dateTime.substring(2,dateTime.length()))
        params.put("signature", getSign(params, account.getPrivateKey()))
        params.put("payerName", req.getFromCardUserName())
        params.put("getPayInfo", "Y")

        Map<String, String> headParams = new HashMap<>();
        headParams.put("Content-Type", "application/x-www-form-urlencoded")
        headParams.put("charset", "utf-8")
        String token = getToken(account);
        if (StringUtils.isEmpty(token)) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg("获取三方token异常")
            return
        }
        headParams.put("Authorization", "Bearer " + token)

        log.info("NuoYaScript_recharge_prepare_headParams:{}", JSON.toJSONString(headParams))
        log.info("NuoYaScript_recharge_prepare_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode(), account.getChannelId(), account.getChannelName())

        String resStr = okHttpUtil.post(account.getPayUrl() + "/api/transaction/deposit", params, requestHeader, headParams)
        log.info("NuoYaScript_recharge_prepare_resp:{}", resStr)
        if (StringUtils.isEmpty(resStr)) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("网络请求超时，稍后重试")
            return
        }
        JSONObject json = JSONObject.parseObject(resStr)
        if (json == null || "0" != json.getString("code")) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg(json.getString("message"))
            return
        }
        JSONObject dataJSON = json.getJSONObject("data");
        if (ObjectUtils.isEmpty(json.getJSONObject("data"))|| ObjectUtils.isEmpty(dataJSON.getJSONObject("detail"))) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg(json.getString("message"))
            return
        }
        JSONObject detail = dataJSON.getJSONObject("detail");
        BankInfo bankInfo = new BankInfo();
        bankInfo.setName(detail.getString("card_owner"))
        bankInfo.setBankId(-1)
        bankInfo.setBankName(detail.getString("bank"))
        bankInfo.setCardNo(detail.getString("card_no"))
        result.setBankInfo(bankInfo)
    }


    public RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>
        log.info("NuoYaScript_notify_resp:{}", JSON.toJSONString(resMap))
        JSONObject json = JSON.parseObject(resMap.get("reqBody"));
        String orderId = json.getString("merchantOrder")
        if (StringUtils.isNotEmpty(orderId)) {
            return this.payQuery(okHttpUtil, account, orderId, args[4])
        }
        return null
    }

    public RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String


        Map<String, String> params = new LinkedHashMap<>()
        params.put("merchantNo", account.getMerchantCode())
        params.put("merchantOrder", orderId)
        String dateTime = DateUtils.format(new Date(), DateUtils.YYYYMMDDHHMMSS);
        params.put("dateTime", dateTime.substring(2,dateTime.length()))
        params.put("signature", getSign(params,account.getPrivateKey()))

        Map<String, String> headParams = new HashMap<>();
        headParams.put("Content-Type", "application/x-www-form-urlencoded")
        headParams.put("charset", "utf-8")

        String token = getToken(account);
        if (StringUtils.isEmpty(token)) {
            return null
        }
        headParams.put("Authorization", "Bearer " + token)

        log.info("NuoYaScript_query_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = getRequestHeard(null, null, orderId, GlActionEnum.RECHARGE_QUERY.getCode(), account.getChannelId(), account.getChannelName())
        String resStr = okHttpUtil.post(account.getPayUrl() + "/api/transaction/deposit/verify ", params, requestHeader, headParams)
        log.info("NuoYaScript_query_resp:{}", resStr)
        if (StringUtils.isEmpty(resStr)) {
            return null
        }
        JSONObject json = JSON.parseObject(resStr)
        if (json == null || json.getString("code") != "0") {
            return null
        }
        JSONObject dataJSON = json.getJSONObject("data");

        // 支付状态:status  0:新建訂單  1:交易進行中  2:交易成功  3:交易失敗  4:交易已過期失效  5:發生例外錯誤
        if (!ObjectUtils.isEmpty(dataJSON) && dataJSON.getString("status") == "2" && dataJSON.getBoolean("isSuccess")) {
            RechargeNotify pay = new RechargeNotify()
            pay.setAmount(dataJSON.getBigDecimal("realAmount").setScale(2, RoundingMode.DOWN))
            pay.setFee(BigDecimal.ZERO)
            pay.setOrderId(orderId)
            pay.setRsp("SUCCESS")
            pay.setThirdOrderId(dataJSON.getString("orderNo"))
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
        Integer paymentId = args[1] as Integer
        if (paymentId == FundConstant.PaymentType.BANKCARD_TRANSFER
                || paymentId == FundConstant.PaymentType.ALI_TRANSFER
                || paymentId == FundConstant.PaymentType.UNION_TRANSFER) {
            return true
        }
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
        return FundConstant.ShowType.DETAIL
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

    public String getToken(GlPaymentMerchantaccount account) {
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