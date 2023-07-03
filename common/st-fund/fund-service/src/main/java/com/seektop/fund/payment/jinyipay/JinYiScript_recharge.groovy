package com.seektop.fund.payment.jinyipay

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
 * @desc 金镱支付
 * @auth Otto
 * @date 2022-04-07
 */

class JinYiScript_recharge {

    private static final Logger log = LoggerFactory.getLogger(JinYiScript_recharge.class)

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
        if (merchant.getPaymentId() == FundConstant.PaymentType.UNION_PAY) {
            paymentType = "JYP009"  //快捷支付(云闪付)
        } else if (merchant.getPaymentId() == FundConstant.PaymentType.QUICK_QQ_PAY) {
            paymentType = "JYP022"  //QQ红包(极速QQ)
        } else if (merchant.getPaymentId() == FundConstant.PaymentType.QUICK_ALI_PAY) {
            paymentType = "JYP016"  //支付宝QQ(极速支付宝)
        } else if (merchant.getPaymentId() == FundConstant.PaymentType.ALI_H5) {
            paymentType = "JYP013"  //支付宝H5
        } else if (merchant.getPaymentId() == FundConstant.PaymentType.QUICK_WECHAT_PAY) {
            paymentType = "JYP005"  //微信H5
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
        Map<String, String> params = new LinkedHashMap<>();
        params.put("user_id", payment.getMerchantCode())
        params.put("order_no", req.getOrderId())
        params.put("price", req.getAmount().setScale(2, RoundingMode.DOWN).toString())
        params.put("notify_url", payment.getNotifyUrl() + merchant.getId())
        params.put("pay_type", paymentType)
        params.put("currency", "CNY")
        String toSign = MD5.toSign(params) + "&key=" + payment.getPrivateKey()
        params.put("sign", MD5.md5(toSign))

        params.put("back_url", payment.getNotifyUrl())
        params.put("business_type", "A001")
        params.put("client_ip", dealWithIp(req.getIp()))
        params.put("notify_type", "1")

        log.info("JinYiScript_Prepare_Params:{} ,url:{}", JSON.toJSONString(params), payment.getPayUrl())
        GlRequestHeader requestHeader =
                getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode(), payment.getChannelId(), payment.getChannelName())
        String restr = okHttpUtil.post(payment.getPayUrl(), params, requestHeader)

        log.info("JinYiScript_Prepare_resStr:{} , orderId:{}", restr, req.getOrderId())
        JSONObject json = JSON.parseObject(restr);
        if (json == null) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("网络请求超时，稍后重试")
            return
        }

        if (json.getString("status") != "1") {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg("请求失败:" + json.getString("error_msg"))
            return
        }

        if (StringUtils.isEmpty(json.getString("payurl"))) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg("商家未出码，请稍后重新下单");
            return
        }
        result.setRedirectUrl(json.getString("payurl"))
    }

    RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount payment = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>

        log.info("JinYiScript_Notify_resMap:{}", JSON.toJSONString(resMap))
        String orderId = resMap.get("order_no")

        Map<String, String> signMap = new LinkedHashMap<>();
        signMap.put("user_id", resMap.get("user_id"));
        signMap.put("order_no", resMap.get("order_no"));
        signMap.put("price", resMap.get("price"));
        signMap.put("pay_price", resMap.get("pay_price"));
        signMap.put("status", resMap.get("status"));
        signMap.put("currency", resMap.get("currency"));

        String toSign = MD5.toSign(signMap) + "&key=" + payment.getPrivateKey()
        toSign = MD5.md5(toSign)

        if (StringUtils.isNotEmpty(orderId) && toSign == resMap.get("sign")) {
            return payQuery(okHttpUtil, payment, orderId)
        }
        log.info("JinYiScript_notify_Sign: 回调资料错误或验签失败，orderId：{}", orderId)
        return null

    }

    RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String

        Map<String, String> params = new LinkedHashMap<>()
        params.put("user_id", account.getMerchantCode())
        params.put("order_no", orderId)
        params.put("business_type", "D001")

        String toSign = MD5.toSign(params) + "&key=" + account.getPrivateKey()
        params.put("sign", MD5.md5(toSign))

        log.info("JinYiScript_Query_reqMap:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = getRequestHeard(null, null, orderId, GlActionEnum.RECHARGE_QUERY.getCode(), account.getChannelId(), account.getChannelName())
        String resStr = okHttpUtil.post(account.getPayUrl(), params, requestHeader)
        log.info("JinYiScript_Query_resStr:{}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || json.getString("status") != "1") {
            return null
        }

        //未支付 0
        //支付成功 1
        if ("1" == json.getString("pay_status")) {
            RechargeNotify pay = new RechargeNotify()
            pay.setAmount(json.getBigDecimal("pay_price").setScale(2, RoundingMode.DOWN))
            pay.setFee(BigDecimal.ZERO)
            pay.setOrderId(orderId)
            pay.setThirdOrderId(json.getString("sysorder_no"))
            pay.setRsp("success")
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

    String dealWithIp(String ip) {
        //三方不收ipv6, 如果是ipv6格式，塞假ip
        if (StringUtils.isEmpty(ip) || ip.length() > 16) {
            return "111.111.111.111"
        }
        return ip;

    }

}
