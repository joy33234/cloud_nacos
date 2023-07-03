package com.seektop.fund.payment.qingpingguopay

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
 * @desc 青苹果支付
 * @auth joy
 * @date 2021-05-10
 */

public class QingPingGuoScript_recharge {

    private static final Logger log = LoggerFactory.getLogger(QingPingGuoScript_recharge.class)

    private OkHttpUtil okHttpUtil

    public RechargeNotify result(Object[] args) throws GlobalException {
        return notify(args)
    }

    public void pay(Object[] args) {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantApp merchant = args[1] as GlPaymentMerchantApp
        GlPaymentMerchantaccount account = args[2] as GlPaymentMerchantaccount
        RechargePrepareDO req = args[3] as RechargePrepareDO
        GlRechargeResult result = args[4] as GlRechargeResult
        Map<String, String> params = new LinkedHashMap<>()
        params.put("merchantId", account.getMerchantCode())
        params.put("merchantOrderId", req.getOrderId())
        params.put("orderAmount", req.getAmount().setScale(2, RoundingMode.DOWN) + "")
        params.put("notifyUrl", account.getNotifyUrl() + merchant.getId())
        if (merchant.getPaymentId() == FundConstant.PaymentType.BANKCARD_TRANSFER) {
            params.put("channelType", "BANK_PAY")
        }
        params.put("remark", "recharge")
        params.put("ip", req.getIp())

        String toSign = MD5.toSign(params) + account.getPrivateKey()
        params.put("sign", MD5.md5(toSign))
        params.put("jsonResult", "1")
        params.put("returnUrl", account.getResultUrl() + merchant.getId())
        
        log.info("QingPingGuoScript_recharge_prepare_params = {}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode(), account.getChannelId(), account.getChannelName())
        String resStr = okHttpUtil.post(account.getPayUrl() + "/PaymentGetway/OrderRquest", params, 30L, requestHeader)
        log.info("QingPingGuoScript_recharge_prepare_resp = {}", resStr)
        if (StringUtils.isEmpty(resStr)) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("网络请求超时，稍后重试")
            return
        }
        JSONObject json = JSON.parseObject(resStr)
        if ("true" != (json.getString("Success"))) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg(json.getString("ErrorMessage"))
            return
        }
        BankInfo bankInfo = new BankInfo();
        bankInfo.setName(json.getString("BankName"))
        bankInfo.setBankId(-1)
        bankInfo.setBankName(json.getString("BankType"))
        bankInfo.setCardNo(json.getString("BankAccount"))
        result.setBankInfo(bankInfo)
        result.setAmount(json.getBigDecimal("PayAmount"))

    }

    public RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>
        log.info("QingPingGuoScript_notify_resp:{}", JSON.toJSONString(resMap))
        String orderId = resMap.get("merchantOrderId")
        String thirdOrderId = resMap.get("systemOrderId")
        String orderAmount = resMap.get("orderAmount")
        if (StringUtils.isNotEmpty(orderId)) {
            return this.payQuery(okHttpUtil, account, orderId, thirdOrderId, orderAmount)
        }
        return null
    }

    public RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String
        String thirdOrderId = args[3] as String
        String orderAmount = args[4] as String

        Map<String, String> params = new LinkedHashMap<>()
        params.put("merchantId", account.getMerchantCode())
        params.put("merchantOrderId", orderId)
        params.put("orderAmount", orderAmount)

        String toSign = MD5.toSign(params) + account.getPrivateKey()
        params.put("sign", MD5.md5(toSign))
        params.remove("merchantKey")
        log.info("QingPingGuoScript_query_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                getRequestHeard(null, null, orderId, GlActionEnum.RECHARGE_QUERY.getCode(), account.getChannelId(), account.getChannelName())
        String resStr = okHttpUtil.post(account.getPayUrl() + "/PaymentGetway/OrderQuery", params,  requestHeader)
        log.info("QingPingGuoScript_query_resp:{}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (ObjectUtils.isEmpty(json)) {
            return null
        }
        // 请求成功     支付成功的订单 ErrorCode=00 ErrorMessage为空
        if ("00" == json.getString("ErrorCode") && StringUtils.isEmpty(json.getString("ErrorMessage"))) {
            RechargeNotify pay = new RechargeNotify()
            pay.setAmount(json.getBigDecimal("OrderAmount").setScale(0, RoundingMode.UP))
            pay.setFee(BigDecimal.ZERO)
            pay.setOrderId(orderId)
            pay.setThirdOrderId(thirdOrderId)
            pay.setRsp("OK")
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

    /**
     * 获取头部信息
     *
     * @param userId
     * @param userName
     * @param orderId
     * @return
     */
    private GlRequestHeader getRequestHeard(String userId, String userName, String orderId, String code, Integer channelId, String channleName) {
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(code)
                .channelId(channelId + "")
                .channelName(channleName)
                .userId(userId)
                .userName(userName)
                .tradeId(orderId)
                .build()
        return requestHeader
    }
}