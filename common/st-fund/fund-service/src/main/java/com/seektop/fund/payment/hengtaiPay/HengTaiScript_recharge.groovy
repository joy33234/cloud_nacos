package com.seektop.fund.payment.hengtaiPay

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

import java.math.RoundingMode

/**
 * 恒泰支付
 * @auth joy
 * @date 2021-07-21
 */

class HengTaiScript_recharge {

    private static final Logger log = LoggerFactory.getLogger(HengTaiScript_recharge.class)

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

        String paymentType = ""
        if (merchant.getPaymentId() == FundConstant.PaymentType.BANKCARD_TRANSFER) {
            paymentType = "6"
        }
        if (StringUtils.isNotEmpty(paymentType)) {
            prepareScan(merchant, payment, req, result, paymentType)
        } else {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("该充值方式暂不支持")
            return
        }
    }

    private void prepareScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, RechargePrepareDO req, GlRechargeResult result, String paymentType) {
        Map<String, String> DataContentParms = new TreeMap<>()
        DataContentParms.put("merchant", payment.getMerchantCode())
        DataContentParms.put("amount", req.getAmount().setScale(2, RoundingMode.DOWN) + "")
        DataContentParms.put("paymentType", paymentType)
        DataContentParms.put("username", req.getUsername())
        DataContentParms.put("depositRealname", req.getFromCardUserName())
        DataContentParms.put("callback", payment.getNotifyUrl() + merchant.getId())
        DataContentParms.put("paymentReference", req.getOrderId())

        String toSign = MD5.toSign(DataContentParms) + "&key=" + payment.getPrivateKey()
        DataContentParms.put("sign", MD5.md5(toSign).toUpperCase())

        log.info("HengTaiScript_Prepare_Params:{}", JSON.toJSONString(DataContentParms))
        GlRequestHeader requestHeader =
                getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode(), payment.getChannelId(), payment.getChannelName())
        String restr = okHttpUtil.post(payment.getPayUrl() + "/api/deposit/page", DataContentParms,  requestHeader)
        log.info("HengTaiScript_Prepare_resStr:{}", restr)

        JSONObject json = JSON.parseObject(restr);
        if (json == null) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("网络请求超时，稍后重试")
            return
        }
        if (json.getString("code") != "1") {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg("请求失败:" + json.getString("message"))
            return
        }

        JSONObject dataJSON = json.getJSONObject("data");
        if (dataJSON == null || StringUtils.isEmpty(dataJSON.getString("redirect"))) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg("商户异常" + json.getString("message"))
            return
        }
        result.setRedirectUrl(dataJSON.getString("redirect"))
    }

    RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount payment = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>

        log.info("HengTaiScript_Notify_resMap:{}", JSON.toJSONString(resMap))

        String orderid = resMap.get("paymentReference")
        String thirdOrderId = resMap.get("reference")
        if (StringUtils.isNotEmpty(orderid)) {
            return payQuery(okHttpUtil, payment, orderid,thirdOrderId)
        }
        return null
    }

    RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String
        String thirdOrderId = args[3] as String

        Map<String, String> DataContentParms = new TreeMap<String, String>()
        DataContentParms.put("merchant", account.getMerchantCode())
        DataContentParms.put("paymentReference", orderId)

        String toSign = MD5.toSign(DataContentParms) + "&key=" + account.getPrivateKey()
        DataContentParms.put("sign", MD5.md5(toSign).toUpperCase())

        log.info("HengTaiScript_Query_reqMap:{}", JSON.toJSONString(DataContentParms))
        GlRequestHeader requestHeader = getRequestHeard(null, null, orderId, GlActionEnum.RECHARGE_QUERY.getCode(), account.getChannelId(), account.getChannelName())
        String resStr = okHttpUtil.post(account.getPayUrl() + "/api/deal/query", DataContentParms,  requestHeader)
        log.info("HengTaiScript_Query_resStr:{}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null) {
            return null
        }
        JSONObject dataJSON = json.getJSONObject("data");
        if (dataJSON == null) {
            return null
        }
        //订单状态。0，待处理。1，成功。2，失败
        if (dataJSON != null && "1" == dataJSON.getString("statusSt")) {
            RechargeNotify pay = new RechargeNotify()
            pay.setAmount(dataJSON.getBigDecimal("revisedPrice").setScale(2, RoundingMode.DOWN))
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