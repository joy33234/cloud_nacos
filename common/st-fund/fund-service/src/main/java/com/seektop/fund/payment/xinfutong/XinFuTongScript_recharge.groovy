package com.seektop.fund.payment.xinfutong

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
import com.seektop.fund.payment.PaymentMerchantEnum
import com.seektop.fund.payment.RechargeNotify
import com.seektop.fund.payment.RechargePrepareDO
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode

class XinFuTongScript_recharge {

    private static final Logger log = LoggerFactory.getLogger(XinFuTongScript_recharge.class)

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
        String service = ""
        if (merchant.getPaymentId() == FundConstant.PaymentType.BANKCARD_TRANSFER) {
            service = "10101"
        } else if (merchant.getPaymentId() == FundConstant.PaymentType.UNION_TRANSFER) {
            service = "10103"
        } else if (merchant.getPaymentId() == FundConstant.PaymentType.ALI_TRANSFER) {
            service = "10108"
        }
        if (org.apache.commons.lang.StringUtils.isNotEmpty(service)) {
            prepareScan(merchant, payment, req, result, service)
        } else {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("该充值方式暂不支持")
            return
        }
    }

    private void prepareScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, RechargePrepareDO req, GlRechargeResult result, String service) {
        Map<String, String> DataContentParms = new HashMap<String, String>()
        DataContentParms.put("partner", payment.getMerchantCode())
        DataContentParms.put("service", service)
        DataContentParms.put("tradeNo", req.getOrderId())
        DataContentParms.put("amount", req.getAmount().setScale(2, RoundingMode.DOWN) + "")
        DataContentParms.put("notifyUrl", payment.getNotifyUrl() + merchant.getId())
        DataContentParms.put("resultType", "json")

        String toSign = MD5.toAscii(DataContentParms) + "&" + payment.getPrivateKey()
        DataContentParms.put("sign", MD5.md5(toSign))

        log.info("XinFuTongScript_Prepare_Params = {}", JSON.toJSONString(DataContentParms))
        GlRequestHeader requestHeader = getRequestHeard(req.getUserId() + "", req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode())
        String restr = okHttpUtil.post(payment.getPayUrl() + "/unionOrder", DataContentParms, requestHeader)
        log.info("XinFuTongScript_Prepare_resStr = {}", restr)

        if (StringUtils.isEmpty(restr)) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("创建订单失败,稍后重试")
            return
        }

        JSONObject json = JSON.parseObject(restr)
        if (json == null || json.getString("isSuccess") != "T"
                || StringUtils.isEmpty(json.getString("url"))) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg("创建订单失败,稍后重试")
            return
        }
        result.setRedirectUrl(json.getString("url"))
    }

    RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount payment = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>
        log.info("XinFuTongScript_Notify_resMap = {}", JSON.toJSONString(resMap))
        String orderid = resMap.get("outTradeNo")
        if (StringUtils.isNotEmpty(orderid)) {
            return payQuery(okHttpUtil, payment, orderid, args[4])
        } else {
            return null
        }
    }

    RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String
        Map<String, String> DataContentParms = new HashMap<String, String>()
        DataContentParms.put("partner", account.getMerchantCode())
        DataContentParms.put("service", "10302")
        DataContentParms.put("outTradeNo", orderId)

        String toSign = MD5.toAscii(DataContentParms) + "&" + account.getPrivateKey()
        DataContentParms.put("sign", MD5.md5(toSign))

        log.info("XinFuTongScript_Query_reqMap = {}", JSON.toJSONString(DataContentParms))
        GlRequestHeader requestHeader = getRequestHeard("", "", orderId, GlActionEnum.RECHARGE_QUERY.getCode())
        String resStr = okHttpUtil.post(account.getPayUrl() + "/orderQuery", DataContentParms, requestHeader)
        log.info("XinFuTongScript_Query_resStr = {}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null) {
            return null
        }
        if ("T" == json.getString("isSuccess") && "1" == json.getString("status")) {// 0: 处理中   1：成功    2：失败
            RechargeNotify pay = new RechargeNotify()
            pay.setAmount(json.getBigDecimal("amount").setScale(2, RoundingMode.DOWN))
            pay.setFee(BigDecimal.ZERO)
            pay.setOrderId(orderId)
            pay.setThirdOrderId("")
            return pay
        }
        return null
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

    void cancel(Object[] args) throws GlobalException {

    }


    private static GlRequestHeader getRequestHeard(String userId, String userName, String orderId, String code) {
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(code)
                .channelId(PaymentMerchantEnum.XINFUTONG_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.XINFUTONG_PAY.getPaymentName())
                .userId(userId)
                .userName(userName)
                .tradeId(orderId)
                .build()
        return requestHeader
    }
}
