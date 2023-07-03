package com.seektop.fund.payment.xinxingpay

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
 * @desc 新星支付
 * @date 2021-06-15
 * @auth joy
 */
public class XinXingScript_recharge {


    private static final Logger log = LoggerFactory.getLogger(XinXingScript_recharge.class)

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
        String payType = ""
        if (FundConstant.PaymentType.BANKCARD_TRANSFER == merchant.getPaymentId()) {
            payType = "168"//卡卡
        }
        if (StringUtils.isEmpty(payType)) {
            result.setErrorMsg("支付方式不支持")
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            return
        }
        prepareToScan(merchant, account, req, result, payType)
    }


    private void prepareToScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result, String payType) throws GlobalException {
        Map<String, String> params = new LinkedHashMap<>()

        params.put("service", "directPay")
        params.put("merchantId", account.getMerchantCode())
        params.put("notifyUrl", account.getNotifyUrl() + merchant.getId())
        params.put("inputCharset", "UTF-8")
        params.put("resType", "json")
        params.put("outOrderId",req.getOrderId())
        params.put("subject","recharge")
        params.put("body","recharge")
        params.put("transAmt",req.getAmount().setScale(2, RoundingMode.DOWN).toString())
        params.put("payMethod", payType)
        params.put("channel", "B2C")
        params.put("cardAttr", "01")
        params.put("attach", "recharge")
        params.put("transName", req.getFromCardUserName())

        String toSign = MD5.toAscii(params)  + account.getPrivateKey()
        params.put("sign", MD5.md5(toSign))
        params.put("signType", "MD5")

        log.info("XinXingScript_recharge_prepare_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode(), account.getChannelId(), account.getChannelName())

        String resStr = okHttpUtil.post(account.getPayUrl() + "/serviceDirect.html", params, requestHeader)
        log.info("XinXingScript_recharge_prepare_resp:{}", resStr)
        if (StringUtils.isEmpty(resStr)) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("网络请求超时，稍后重试")
            return
        }
        JSONObject json = JSONObject.parseObject(resStr)
        if (json == null || !json.getBoolean("success") || ObjectUtils.isEmpty(json.getJSONObject("data"))) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg(json.getString("msg"))
            return
        }
        JSONObject dataJSON = json.getJSONObject("data");
        BankInfo bankInfo = new BankInfo();
        bankInfo.setName(dataJSON.getString("cardName"))
        bankInfo.setBankId(-1)
        bankInfo.setBankName(dataJSON.getString("bankName"))
        bankInfo.setBankBranchName(dataJSON.getString("location"))
        bankInfo.setCardNo(dataJSON.getString("cardNumber"))
        result.setBankInfo(bankInfo)
    }


    public RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>
        log.info("XinXingScript_notify_resp:{}", JSON.toJSONString(resMap))
        String orderId = resMap.get("OrderId")
        String PayMoney = resMap.get("PayMoney")
        String PayMethod = resMap.get("PayMethod")
        if (StringUtils.isNotEmpty(orderId) && StringUtils.isNotEmpty(PayMoney) && StringUtils.isNotEmpty(PayMethod)) {
            return this.payQuery(okHttpUtil, account, orderId, PayMoney, PayMethod, args[4])
        }
        return null
    }

    public RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String
        String orderAmount = args[3] as String
        String payMethod = args[4] as String

        Map<String, String> params = new LinkedHashMap<>()
        params.put("merchantId", account.getMerchantCode())
        params.put("orderId", orderId)
        params.put("payMoney", orderAmount)
        params.put("payMethod", payMethod)

        String toSign = MD5.toAscii(params)  + account.getPrivateKey()
        params.put("sign", MD5.md5(toSign))
        params.put("signType", "MD5")

        log.info("XinXingScript_query_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = getRequestHeard(null, null, orderId, GlActionEnum.RECHARGE_QUERY.getCode(), account.getChannelId(), account.getChannelName())
        String resStr = okHttpUtil.post(account.getPayUrl() + "/queryDirect.html", params, requestHeader)
        log.info("XinXingScript_query_resp:{}", resStr)
        if (StringUtils.isEmpty(resStr)) {
            return null
        }
        JSONObject json = JSON.parseObject(resStr)
        if (json == null || ObjectUtils.isEmpty(json.getJSONObject("data"))) {
            return null
        }
        JSONObject dataJSON = json.getJSONObject("data")
        // 支付状态:flag 0:处理中   1： 已支付
        if ("1" == (dataJSON.getString("flag"))) {
            RechargeNotify pay = new RechargeNotify()
            pay.setAmount(dataJSON.getBigDecimal("payMoney").setScale(2, RoundingMode.DOWN))
            pay.setFee(BigDecimal.ZERO)
            pay.setOrderId(orderId)
            pay.setRsp("ok")
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
}