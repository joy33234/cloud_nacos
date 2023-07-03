package com.seektop.fund.payment.hengxingpay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
import com.seektop.common.utils.MD5
import com.seektop.constant.FundConstant
import com.seektop.constant.ProjectConstant
import com.seektop.enumerate.GlActionEnum
import com.seektop.exception.GlobalException
import com.seektop.fund.model.GlPaymentMerchantApp
import com.seektop.fund.model.GlPaymentMerchantaccount
import com.seektop.fund.payment.*
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode

/**
 * 恒星支付
 */

public class HengxingScript {

    private static final Logger log = LoggerFactory.getLogger(HengxingScript.class)

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
        Map<String, String> DataContentParms = new LinkedHashMap<>()
        DataContentParms.put("mcnNum", payment.getMerchantCode())
        DataContentParms.put("orderId", req.getOrderId())
        DataContentParms.put("backUrl", payment.getNotifyUrl() + merchant.getId())
        if (req.getClientType() == ProjectConstant.ClientType.PC) {//PC
            DataContentParms.put("payType", "2")
        } else {//移动
            DataContentParms.put("payType", "12")
        }
        DataContentParms.put("amount", (req.getAmount() * new BigDecimal(100)).setScale(0, RoundingMode.DOWN).toString())

        String toSign = MD5.toSign(DataContentParms) + "&secreyKey=" + payment.getPrivateKey()
        DataContentParms.put("ip", req.getIp())
        DataContentParms.put("sign", MD5.md5(toSign).toUpperCase())

        log.info("HengxingScript_Prepare_Params:{}", JSON.toJSONString(DataContentParms))
        GlRequestHeader requestHeader = this.getRequestHeard(req.getUserId() + "", req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode())
        String restr = okHttpUtil.postJSON(payment.getPayUrl() + "/api/v1/pay_qrcode.api", JSON.toJSONString(DataContentParms), requestHeader)
        log.info("HengxingScript_Prepare_resStr:{}", restr)

        JSONObject json = JSON.parseObject(restr)
        if (json == null) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("网络请求超时，稍后重试")
            return
        }
        if (json.getString("status") != ("0")) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg("商户异常,请联系客服")
            return
        }
        result.setErrorCode(FundConstant.RechargeErrorCode.NORMAL)
        result.setRedirectUrl(json.getString("qrCode"))

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
        String orderid = json.getString("orderId")
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
        DataContentParms.put("mcnNum", account.getMerchantCode())
        DataContentParms.put("orderId", orderId)

        String toSign = MD5.toSign(DataContentParms) + "&secreyKey=" + account.getPrivateKey()
        DataContentParms.put("sign", MD5.md5(toSign).toUpperCase())

        log.info("HengxingScript_Query_reqMap:{}", JSON.toJSONString(DataContentParms))
        GlRequestHeader requestHeader = this.getRequestHeard("", "", orderId, GlActionEnum.RECHARGE_QUERY.getCode())
        String resStr = okHttpUtil.postJSON(account.getPayUrl() + "/api/v1/query_record.api", JSON.toJSONString(DataContentParms), requestHeader)
        log.info("HengxingScript_Query_resStr:{}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || json.getString("status") != ("0")) {
            return null
        }
        json = json.getJSONObject("content")
        if (json != null && "1" == (json.getString("payStatus"))) {// 成功：1 支付失败：2
            RechargeNotify pay = new RechargeNotify()
            pay.setAmount(json.getBigDecimal("amount").divide(new BigDecimal(100)).setScale(0, RoundingMode.DOWN))
            pay.setFee(BigDecimal.ZERO)
            pay.setOrderId(orderId)
            pay.setThirdOrderId("")
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


    WithdrawResult withdraw(Object[] args) throws GlobalException {
        return null
    }


    WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        return null
    }


    WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        return null
    }


    BigDecimal balanceQuery(Object[] args) throws GlobalException {
        return null
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
}