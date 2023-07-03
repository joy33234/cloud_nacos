package com.seektop.fund.payment.xinhuopay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
import com.seektop.common.utils.HtmlTemplateUtils
import com.seektop.constant.FundConstant
import com.seektop.enumerate.GlActionEnum
import com.seektop.exception.GlobalException
import com.seektop.fund.business.GlPaymentChannelBankBusiness
import com.seektop.fund.model.GlPaymentMerchantApp
import com.seektop.fund.model.GlPaymentMerchantaccount
import com.seektop.fund.payment.*
import com.seektop.fund.payment.groovy.BaseScript
import com.seektop.fund.payment.groovy.ResourceEnum
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode

/**
 * 新火支付
 *
 * @author walter
 */

public class XinHuoScript {

    private static final Logger log = LoggerFactory.getLogger(XinHuoScript.class)

    private OkHttpUtil okHttpUtil

    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness


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
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[5], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        if (merchant.getPaymentId() == FundConstant.PaymentType.ONLINE_PAY) {
            prepareScan(merchant, payment, req, result)
        }
    }

    public void prepareScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, RechargePrepareDO req, GlRechargeResult result) {
        try {
            Map<String, String> params = new HashMap<String, String>()
            params.put("totalFee", req.getAmount().setScale(2, RoundingMode.DOWN).toString())
            params.put("body", "recharge")
            params.put("charset", "utf-8")
            params.put("defaultbank", "")
            params.put("isApp", "web")
            params.put("merchantId", payment.getMerchantCode())
            params.put("notifyUrl", payment.getNotifyUrl() + merchant.getId())
            params.put("orderNo", req.getOrderId())
            params.put("paymentType", "1")
            params.put("paymethod", "directPay")
            params.put("returnUrl", payment.getNotifyUrl() + merchant.getId())
            params.put("service", "online_pay")
            params.put("title", "recharge")

            String shastr = UtilSign.GetSHAstr(params, payment.getPrivateKey())
            params.put("signType", "SHA")
            params.put("sign", shastr)

            log.info("XinHuoScript_Prepare_Params:{}", JSON.toJSONString(params))
            String restr = HtmlTemplateUtils.getPost(payment.getPayUrl() + "/payment/v1/order/" + payment.getMerchantCode() + "-" + req.getOrderId(), params)
            log.info("XinHuoScript_Prepare_resStr:{}", restr)
            if (StringUtils.isEmpty(restr)) {
                result.setErrorCode(1)
                result.setErrorMsg("订单创建失败，稍后重试")
                return
            }
            result.setMessage(restr)

        } catch (Exception e) {
            e.printStackTrace()
        }
    }


    /**
     * 解析支付结果
     *
     * @param merchant
     * @param merchantaccount
     * @param resMap
     * @return
     */
    public RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount merchantaccount = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[4], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        log.info("XinHuoScript_Notify_resMap:{}", JSON.toJSONString(resMap))
        JSONObject json = JSONObject.parseObject(resMap.get("reqBody"))
        String orderId
        if (json == null) {
            orderId = resMap.get("order_no")
        } else {
            orderId = json.getString("order_no")
        }
        if (StringUtils.isNotEmpty(orderId)) {
            return payQuery(okHttpUtil, merchantaccount, orderId, args[4])
        } else {
            return null
        }
    }

    public RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        Map<String, String> params = new HashMap<String, String>()
        params.put("merchantId", account.getMerchantCode())
        params.put("orderNo", orderId)
        params.put("charset", "utf-8")

        String shastr = UtilSign.GetSHAstr(params, account.getPrivateKey())
        params.put("signType", "SHA")
        params.put("sign", shastr)

        String url = account.getPayUrl()
        if (url.contains("https")) {
            url = url.replace("https", "http")
        }
        log.info("XinHuoScript_Query_reqMap:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = this.getRequestHeard("", "", orderId, GlActionEnum.RECHARGE_QUERY.getCode())
        String resStr = okHttpUtil.get(url + "/payment/v1/order/" + account.getMerchantCode() + "-" + orderId, params, requestHeader)
        log.info("XinHuoScript_Query_resStr:{}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || "S0001" != (json.getString("respCode"))) {
            return null
        }
        if ("completed" == (json.getString("status"))) {//wait：等待支付，completed：支付成功，failed：支付失败
            RechargeNotify pay = new RechargeNotify()
            pay.setAmount(json.getBigDecimal("amount").setScale(2, RoundingMode.DOWN))
            pay.setFee(BigDecimal.ZERO)
            pay.setOrderId(orderId)
            pay.setThirdOrderId(json.getString("tradeNo"))
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
                .channelId(PaymentMerchantEnum.XINHUO_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.XINHUO_PAY.getPaymentName())
                .userId(userId)
                .userName(userName)
                .tradeId(orderId)
                .build()
        return requestHeader
    }
}
