package com.seektop.fund.payment.applePay

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
import com.seektop.fund.payment.GlRechargeResult
import com.seektop.fund.payment.RechargeNotify
import com.seektop.fund.payment.RechargePrepareDO
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.util.ObjectUtils

import java.math.RoundingMode

/**
 * @desc 苹果支付
 * @auth joy
 * @date 2021-10-01
 */

public class AppleScript_recharge {

    private static final Logger log = LoggerFactory.getLogger(AppleScript_recharge.class)

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

        String payType = ""
        if (merchant.getPaymentId() == FundConstant.PaymentType.BANKCARD_TRANSFER) {
            payType = "bankCard"
        } else if (merchant.getPaymentId() == FundConstant.PaymentType.ALI_TRANSFER) {
            payType = "alipay2BankCard"
        } else if (merchant.getPaymentId() == FundConstant.PaymentType.QUICK_WECHAT_PAY) {
            payType = "wechat"
        }
        if (StringUtils.isEmpty(payType)) {
            result.setErrorMsg("支付方式不支持")
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            return
        }

        Map<String, String> params = new TreeMap<>()
        params.put("merchantNum", account.getMerchantCode())
        params.put("orderNo", req.getOrderId())
        params.put("amount", req.getAmount().setScale(2, RoundingMode.DOWN) + "")
        params.put("payType", payType)

        params.put("notifyUrl", account.getNotifyUrl() + merchant.getId())
        params.put("returnUrl", account.getResultUrl() + merchant.getId())

        StringBuilder toSign = new StringBuilder();
        toSign.append("merchant:").append(params.get("merchantNum"))
        .append("order:").append(params.get("orderNo"))
        .append("amount:").append(params.get("amount"))
        .append("noti:").append(params.get("notifyUrl"))
        .append("xiangyunxiyou").append(account.getPrivateKey());

        params.put("sign", MD5.md5(toSign.toString()))
        log.info("AppleScript_recharge_prepare_params = {}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode(), account.getChannelId(), account.getChannelName())
        String payUrl = account.getPayUrl() + "/api/startOrder"

        String resStr = okHttpUtil.post(payUrl, params,  requestHeader)
        log.info("AppleScript_recharge_prepare_resp = {}", resStr)
        if (StringUtils.isEmpty(resStr)) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("网络请求超时，稍后重试")
            return
        }
        JSONObject json = JSON.parseObject(resStr)
        if ("200" != (json.getString("code")) ) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg(json.getString("msg"))
            return
        }
        JSONObject dataJSON = json.getJSONObject("data")
        if (dataJSON == null || StringUtils.isEmpty(dataJSON.getString("payUrl"))) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg(json.getString("msg"))
            return
        }
        result.setRedirectUrl(dataJSON.getString("payUrl"))
    }

    public RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>
        log.info("AppleScript_notify_resp:{}", JSON.toJSONString(resMap))
        String orderId = resMap.get("orderNo")
        String thirdOrderId = resMap.get("platformOrderNo")
        if (StringUtils.isNotEmpty(orderId)) {
            return this.payQuery(okHttpUtil, account, orderId, thirdOrderId)
        }
        return null
    }

    public RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String
        String thirdOrderId = args[3] as String

        Map<String, String> params = new LinkedHashMap<>()
        params.put("merchantNum", account.getMerchantCode())
        params.put("merchantOrderNo", orderId)

        String toSign = params.get("merchantNum") + params.get("merchantOrderNo")  + account.getPrivateKey()
        params.put("sign", MD5.md5(toSign))
        log.info("AppleScript_query_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                getRequestHeard(null, null, orderId, GlActionEnum.RECHARGE_QUERY.getCode(), account.getChannelId(), account.getChannelName())

        String queryUrl = account.getPayUrl() + "/api/getOrderInfo"
        String resStr = okHttpUtil.get(queryUrl, params,  requestHeader)
        log.info("AppleScript_query_resp:{}", resStr)
        if (StringUtils.isEmpty(resStr)) {
            return null
        }
        JSONObject json = JSON.parseObject(resStr)
        if (ObjectUtils.isEmpty(json) || "200" != (json.getString("code"))) {
            return null
        }
        JSONObject dataJSON = json.getJSONObject("data")
        // 请求成功 1：等待接单，2：已接单，4：已支付，5：超时取消  8: 补单
        if (dataJSON != null && ("4" == (dataJSON.getString("orderState")) || "8" == (dataJSON.getString("orderState")))) {
            RechargeNotify pay = new RechargeNotify()
            pay.setAmount(dataJSON.getBigDecimal("amount").setScale(0, RoundingMode.UP))
            pay.setFee(BigDecimal.ZERO)
            pay.setOrderId(orderId)
            pay.setThirdOrderId(thirdOrderId)
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
        return false
    }

    /**
     * 根据支付方式判断-转帐是否需要实名
     *
     * @param args
     * @return
     */
    public boolean needName(Object[] args) {
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
        return FundConstant.ShowType.NORMAL
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