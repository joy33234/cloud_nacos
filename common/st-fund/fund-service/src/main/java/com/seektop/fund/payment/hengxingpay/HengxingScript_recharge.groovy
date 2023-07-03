package com.seektop.fund.payment.hengxingpay

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

/**
 * 恒星支付
 * @auth joy
 * @date 2021-05-14
 */

public class HengxingScript_recharge {

    private static final Logger log = LoggerFactory.getLogger(HengxingScript_recharge.class)

    private OkHttpUtil okHttpUtil

    /**
     * 封装支付请求参数
     *
     * @param merchant
     * @param payment
     * @param req
     * @param result
     */
    public void pay(Object[] args) {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantApp merchant = args[1] as GlPaymentMerchantApp
        GlPaymentMerchantaccount payment = args[2] as GlPaymentMerchantaccount
        RechargePrepareDO req = args[3] as RechargePrepareDO
        GlRechargeResult result = args[4] as GlRechargeResult

        if (FundConstant.PaymentType.QQ_PAY == merchant.getPaymentId()) {
            result.setRedirectUrl("https://www.google.com")
            return
        }

        Map<String, String> DataContentParms = new LinkedHashMap<>()
        DataContentParms.put("amount", req.getAmount().setScale(2, RoundingMode.DOWN).toString())
        DataContentParms.put("merchantNum", payment.getMerchantCode())
        DataContentParms.put("merchantOrderNum", req.getOrderId())
        DataContentParms.put("payWayType", "1001")//卡卡
        String toSign = MD5.toSign(DataContentParms) + "&key=" + payment.getPrivateKey()
        DataContentParms.put("sign", MD5.md5(toSign))

        DataContentParms.put("callbackUrl", payment.getNotifyUrl() + merchant.getId())
        DataContentParms.put("userCardName", req.getFromCardUserName())
        DataContentParms.put("userRequestIp", req.getIp())
        DataContentParms.put("jumpType", "2")//3返回收款银行卡信息，只有在1001或1002时有效

        log.info("HengxingScript_Prepare_Params:{}", JSON.toJSONString(DataContentParms))
        GlRequestHeader requestHeader = this.getRequestHeard(req.getUserId() + "", req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode())
        String restr = okHttpUtil.post(payment.getPayUrl() + "/api/merchant-api/api-recharge/request", DataContentParms,30L, requestHeader)
        log.info("HengxingScript_Prepare_resStr:{}", restr)

        JSONObject json = JSON.parseObject(restr)
        if (json == null) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("网络请求超时，稍后重试")
            return
        }
        if (!json.getBoolean("success") || StringUtils.isEmpty(json.getString("data"))) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg(json.getString("message"))
            return
        }
        result.setRedirectUrl(json.getString("data"))

    }

    /**
     * 解析支付结果
     *
     * @param merchant
     * @param payment
     * @param resMap
     * @return
     */
    public RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount payment = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>
        log.info("HengxingScript_Notify_resMap:{}", JSON.toJSONString(resMap))
        JSONObject json = JSON.parseObject(resMap.get("reqBody"))
        String orderid = json.getString("merchantOrderNum")
        if (StringUtils.isNotEmpty(orderid)) {
            return payQuery(okHttpUtil, payment, orderid)
        } else {
            return null
        }
    }

    public RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String
        Map<String, String> DataContentParms = new LinkedHashMap<>()
        DataContentParms.put("merchantNum", account.getMerchantCode())
        DataContentParms.put("merchantOrderNum", orderId)
        String toSign = MD5.toSign(DataContentParms) + "&key=" + account.getPrivateKey()
        DataContentParms.put("sign", MD5.md5(toSign))

        log.info("HengxingScript_Query_reqMap:{}", JSON.toJSONString(DataContentParms))
        GlRequestHeader requestHeader = this.getRequestHeard("", "", orderId, GlActionEnum.RECHARGE_QUERY.getCode())
        String resStr = okHttpUtil.postJSON(account.getPayUrl() + "/api/merchant-api/api-recharge/get", JSON.toJSONString(DataContentParms), requestHeader)
        log.info("HengxingScript_Query_resStr:{}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || !json.getBoolean("success")) {
            return null
        }
        JSONObject dataJSON = json.getJSONObject("data")
        //1待支付，2已完成，3已撤单，4已失败
        if (dataJSON != null && dataJSON.getString("status") == "2") {
            RechargeNotify pay = new RechargeNotify()
            pay.setAmount(dataJSON.getBigDecimal("receiveAmount").setScale(2, RoundingMode.DOWN))
            pay.setFee(BigDecimal.ZERO)
            pay.setOrderId(orderId)
            pay.setThirdOrderId("")
            pay.setRsp("SUCCESS")
            return pay
        }
        return null
    }

    /**
     * 解析页面跳转结果
     *
     * @param merchant
     * @param payment
     * @param resMap
     * @return
     */
    public RechargeNotify result(Object[] args) throws GlobalException {
        return notify(args)
    }

/**
 * 获取头部信息
 *
 * @param userId
 * @param userName
 * @param orderId
 * @return
 */
    private GlRequestHeader getRequestHeard(String userId, String userName, String orderId, String code) {
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(code)
                .channelId(PaymentMerchantEnum.HENGXING_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.HENGXING_PAY.getPaymentName())
                .userId(userId)
                .userName(userName)
                .tradeId(orderId)
                .build()
        return requestHeader
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
}