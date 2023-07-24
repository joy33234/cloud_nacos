package com.seektop.fund.payment.jiudingpay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
import com.seektop.common.utils.MD5
import com.seektop.constant.FundConstant
import com.seektop.enumerate.GlActionEnum
import com.seektop.exception.GlobalException
import com.seektop.fund.business.GlPaymentChannelBankBusiness
import com.seektop.fund.model.GlPaymentMerchantApp
import com.seektop.fund.model.GlPaymentMerchantaccount
import com.seektop.fund.payment.GlRechargeResult
import com.seektop.fund.payment.RechargeNotify
import com.seektop.fund.payment.RechargePrepareDO
import com.seektop.fund.payment.groovy.ResourceEnum
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode

/**
 * 九鼎支付
 * @author joy
 * @date 20201218
 */
public class JiuDingScript_recharge {

    private static final Logger log = LoggerFactory.getLogger(JiuDingScript_recharge.class)

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
        this.glPaymentChannelBankBusiness = com.seektop.fund.payment.groovy.BaseScript.getResource(args[5], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        String service = ""
        if (merchant.getPaymentId() == FundConstant.PaymentType.BANKCARD_TRANSFER) {
            service = "bankCard"
        }
        if (StringUtils.isNotEmpty(service)) {
            prepareScan(merchant, payment, req, result, service)
        } else {
            result.setErrorMsg("支付方式不支持")
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            return
        }
    }

    public void prepareScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, RechargePrepareDO req, GlRechargeResult result, String service) {
        Map<String, String> params = new HashMap<String, String>()
        params.put("merchantNum", payment.getMerchantCode())
        params.put("orderNo", req.getOrderId())
        params.put("amount", req.getAmount().setScale(2, RoundingMode.DOWN) + "")
        params.put("notifyUrl", payment.getNotifyUrl() + merchant.getId())
        params.put("returnUrl", payment.getNotifyUrl() + merchant.getId())
        params.put("payType", service)

        String toSign = payment.getMerchantCode() + req.getOrderId() + params.get("amount") + params.get("notifyUrl") + payment.getPrivateKey()
        params.put("sign", MD5.md5(toSign))

        log.info("JiuDingScript_Prepare_Params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                this.getRequestHeard(req.getUserId() + "", req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode(),payment.getChannelId(),payment.getChannelName())
        String restr = okHttpUtil.post(payment.getPayUrl() + "/api/startOrder", params, requestHeader)
        log.info("JiuDingScript_Prepare_resStr:{}", restr)

        JSONObject json = JSON.parseObject(restr)
        if (json == null) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg("网络连接超时")
            return
        }
        JSONObject dataJSON = json.getJSONObject("data")
        //状态【1代表正常】【0代表错误】
        if (json.getString("code") != ("200") ||  dataJSON == null || !json.getBoolean("success")
                || StringUtils.isEmpty(dataJSON.getString("payUrl"))) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg(StringUtils.isEmpty(json.getString("msg")) ? "创建订单失败" : json.getString("msg"))
            return
        }
        result.setRedirectUrl(dataJSON.getString("payUrl"))
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
        log.info("JiuDingScript_Notify_resMap:{}", JSON.toJSONString(resMap))
        String orderid = resMap.get("orderNo")
        if (StringUtils.isNotEmpty(orderid)) {
            return payQuery(okHttpUtil, payment, orderid, args[4])
        } else {
            return null
        }
    }

    public RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String
        Map<String, String> params = new HashMap<String, String>()
        params.put("merchantNum", account.getMerchantCode())
        params.put("merchantOrderNo", orderId)

        String toSign = account.getMerchantCode() + orderId + account.getPrivateKey()
        params.put("sign", MD5.md5(toSign))

        log.info("JiuDingScript_Query_reqMap:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                getRequestHeard("", "", orderId, GlActionEnum.RECHARGE_QUERY.getCode(), account.getChannelId(), account.getChannelName())
        String resStr = okHttpUtil.get(account.getPayUrl() + "/api/getOrderInfo", params, requestHeader)
        log.info("JiuDingScript_Query_resStr:{}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || json.getString("code") != "200") {
            return null
        }
        JSONObject dataJSON = json.getJSONObject("data")
        if (dataJSON == null) {
            return null
        }
        //状态 1：等待接单，2：已接单，4：已支付，5：超时取消
        if ("4" == (dataJSON.getString("orderState"))) {
            RechargeNotify pay = new RechargeNotify()
            pay.setAmount(dataJSON.getBigDecimal("amount").setScale(2, RoundingMode.DOWN))
            pay.setFee(BigDecimal.ZERO)
            pay.setOrderId(orderId)
            pay.setThirdOrderId("")
            log.info(JSON.toJSONString(pay))
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
