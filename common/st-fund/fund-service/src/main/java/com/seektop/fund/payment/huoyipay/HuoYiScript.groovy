package com.seektop.fund.payment.huoyipay

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
import com.seektop.fund.payment.*
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode

/**
 * 火翼支付
 *
 * @author walter
 */
public class HuoYiScript {

    private static final Logger log = LoggerFactory.getLogger(HuoYiScript.class)

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
        String service = ""
        if (merchant.getPaymentId() == FundConstant.PaymentType.ONLINE_PAY) {
            service = "105"
        }
        if (StringUtils.isNotEmpty(service)) {
            prepareScan(merchant, payment, req, result, service)
        }
    }

    public void prepareScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, RechargePrepareDO req, GlRechargeResult result, String service) {
        try {
            Map<String, String> params = new HashMap<String, String>()
            params.put("mchId", payment.getMerchantCode())
            params.put("productId", service)
            params.put("mchOrderNo", req.getOrderId())
            params.put("amount", (req.getAmount() * new BigDecimal(100)).setScale(0, RoundingMode.DOWN).toString())
            params.put("returnUrl", payment.getNotifyUrl() + merchant.getId())
            params.put("notifyUrl", payment.getNotifyUrl() + merchant.getId())
            params.put("subject", "recharge")
            params.put("body", "recharge")
            params.put("clientIp", req.getIp())

            String toSign = MD5.toAscii(params) + "&key=" + payment.getPrivateKey()
            params.put("sign", MD5.md5(toSign).toUpperCase())

            log.info("HuoYiScript_Prepare_Params:{}", JSON.toJSONString(params))
            GlRequestHeader requestHeader = this.getRequestHeard(req.getUserId() + "", req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode())
            String restr = okHttpUtil.post(payment.getPayUrl() + "/api/pay/create", params, requestHeader)
            log.info("HuoYiScript_Prepare_resStr:{}", restr)

            JSONObject json = JSONObject.parseObject(restr)

            if (json == null || json.getString("retCode") != ("SUCCESS") || StringUtils.isEmpty(json.getString("payUrl"))) {
                result.setErrorCode(1)
                result.setErrorMsg("创建订单失败")
                return
            }
            result.setRedirectUrl(json.getString("payUrl"))

        } catch (Exception e) {
            result.setErrorCode(1)
            result.setErrorMsg("创建订单失败,请更换充值方式")
            return
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
        GlPaymentMerchantApp merchantaccount = args[1] as GlPaymentMerchantApp
        Map<String, String> resMap = args[3] as Map<String, String>
        log.info("HuoYiScript_Notify_resMap:{}", JSON.toJSONString(resMap))
        JSONObject json = JSONObject.parseObject(resMap.get("reqBody"))
        String orderId
        if (json == null) {
            orderId = resMap.get("mchOrderNo")
        } else {
            orderId = json.getString("mchOrderNo")
        }
        if (StringUtils.isNotEmpty(orderId)) {
            return payQuery(okHttpUtil, merchantaccount, orderId)
        } else {
            return null
        }
    }


    public RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String
        Map<String, String> params = new HashMap<String, String>()
        params.put("mchId", account.getMerchantCode())
        params.put("mchOrderNo", orderId)

        String toSign = MD5.toAscii(params) + "&key=" + account.getPrivateKey()
        params.put("sign", MD5.md5(toSign).toUpperCase())

        log.info("HuoYiScript_Query_reqMap:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = this.getRequestHeard("", "", orderId, GlActionEnum.RECHARGE_QUERY.getCode())
        String resStr = okHttpUtil.post(account.getPayUrl() + "/api/pay/query", params, requestHeader)
        log.info("HuoYiScript_Query_resStr:{}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || "SUCCESS" != (json.getString("retCode"))) {
            return null
        }
        // 支付状态,0=订单生成,1=支付中,2=支付成功,3=业务处理完成
        if ("2" == (json.getString("status")) || "3" == (json.getString("status"))) {
            RechargeNotify pay = new RechargeNotify()
            pay.setAmount(json.getBigDecimal("amount").divide(new BigDecimal(100)).setScale(2, RoundingMode.DOWN))
            pay.setFee(BigDecimal.ZERO)
            pay.setOrderId(orderId)
            pay.setThirdOrderId(json.getString("payOrderId"))
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
                .channelId(PaymentMerchantEnum.HUOYI_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.HUOYI_PAY.getPaymentName())
                .userId(userId)
                .userName(userName)
                .tradeId(orderId)
                .build()
        return requestHeader
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
}