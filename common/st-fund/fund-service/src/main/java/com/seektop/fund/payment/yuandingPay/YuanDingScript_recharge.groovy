package com.seektop.fund.payment.yuandingPay

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
import org.apache.commons.lang3.ObjectUtils
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode

/**
 * 元鼎支付
 * @auth joy
 * @date 2021-07-20
 */

class YuanDingScript_recharge {

    private static final Logger log = LoggerFactory.getLogger(YuanDingScript_recharge.class)

    private OkHttpUtil okHttpUtil

    RechargeNotify result(Object[] args) throws GlobalException {
        return notify(args)
    }

    void pay(Object[] args) {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantApp merchant = args[1] as GlPaymentMerchantApp
        GlPaymentMerchantaccount payment = args[2] as GlPaymentMerchantaccount
        RechargePrepareDO req = args[3] as RechargePrepareDO
        GlRechargeResult result = args[4] as GlRechargeResult

        String PayTypeId = ""
        if (merchant.getPaymentId() == FundConstant.PaymentType.BANKCARD_TRANSFER) {
            PayTypeId = "bank_auto"
        }
        if (StringUtils.isNotEmpty(PayTypeId)) {
            prepareScan(merchant, payment, req, result, PayTypeId)
        } else {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("该充值方式暂不支持")
            return
        }
    }

    private void prepareScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, RechargePrepareDO req, GlRechargeResult result, String PayTypeId) {
        Map<String, String> params = new HashMap<String, String>()
        params.put("userid", payment.getMerchantCode())
        params.put("orderno", req.getOrderId())
        params.put("desc", "recharge")
        params.put("amount", req.getAmount().setScale(2, RoundingMode.DOWN) + "")
        params.put("notifyurl", payment.getNotifyUrl() + merchant.getId())
        params.put("backurl", payment.getNotifyUrl() + merchant.getId())
        params.put("paytype", PayTypeId)
        params.put("acname", req.getFromCardUserName())
        params.put("userip", req.getIp())
        params.put("currency", "CNY")

        String toSign = params.get("userid") + params.get("orderno") + params.get("amount") + params.get("notifyurl") + payment.getPrivateKey()
        params.put("sign", MD5.md5(toSign))

        log.info("YuanDingScript_Prepare_Params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode(), payment.getChannelId(), payment.getChannelName())
        String restr = okHttpUtil.post(payment.getPayUrl() + "/Apipay", params,  requestHeader)
        log.info("YuanDingScript_Prepare_resStr:{}", restr)

        JSONObject json = JSON.parseObject(restr);
        if (json == null) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("网络请求超时，稍后重试")
            return
        }
        if (json.getString("status") != "1" || StringUtils.isEmpty(json.getString("payurl"))) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg("请求失败:" + json.getString("error"))
            return
        }
        result.setRedirectUrl(json.getString("payurl"))
    }

    RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount payment = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>

        log.info("YuanDingScript_Notify_resMap:{}", JSON.toJSONString(resMap))
        String orderid = resMap.get("orderno")
        if (StringUtils.isNotEmpty(orderid)) {
            return payQuery(okHttpUtil, payment, orderid)
        }
        return null
    }

    RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String

        Map<String, String> params = new HashMap<String, String>()
        params.put("userid", account.getMerchantCode())
        params.put("orderno", orderId)
        params.put("action", "orderquery")

        String toSign = params.get("userid") + params.get("orderno") + params.get("action") + account.getPrivateKey()
        params.put("sign", MD5.md5(toSign))

        log.info("YuanDingScript_Query_reqMap:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = getRequestHeard(null, null, orderId, GlActionEnum.RECHARGE_QUERY.getCode(), account.getChannelId(), account.getChannelName())
        String resStr = okHttpUtil.post(account.getPayUrl() + "/Apipay", params,  requestHeader)
        log.info("YuanDingScript_Query_resStr:{}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (ObjectUtils.isEmpty(json)) {
            return null
        }
        //支付状态 状态=1 支付成功 状态=0 未支付
        if ("1" == json.getString("status")) {
            RechargeNotify pay = new RechargeNotify()
            pay.setAmount(json.getBigDecimal("realamount").setScale(2, RoundingMode.DOWN))
            pay.setFee(BigDecimal.ZERO)
            pay.setOrderId(orderId.toUpperCase())
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
        return FundConstant.ShowType.NORMAL
    }

    /**
     * 充值USDT汇率
     *
     * @param args
     * @return
     */
    public BigDecimal paymentRate(Object[] args) {
        return null
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