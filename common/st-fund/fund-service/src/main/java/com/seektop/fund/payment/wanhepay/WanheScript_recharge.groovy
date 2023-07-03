package com.seektop.fund.payment.wanhepay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
import com.seektop.common.utils.DateUtils
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

class WanheScript_recharge {

    private static final Logger log = LoggerFactory.getLogger(WanheScript_recharge.class)

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

        String payType = ""
        if (merchant.getPaymentId() == FundConstant.PaymentType.BANKCARD_TRANSFER) {
            payType = "203"
        } else if (merchant.getPaymentId() == FundConstant.PaymentType.ALI_TRANSFER) {
            payType = "205"
        } else if (merchant.getPaymentId() == FundConstant.PaymentType.UNION_TRANSFER) {
            payType = "207"
        } else if (merchant.getPaymentId() == FundConstant.PaymentType.ONLINE_PAY) {
            payType = "209"
        }
        if (StringUtils.isNotEmpty(payType)) {
            prepareScan(merchant, payment, req, result, payType)
        }
    }

    private void prepareScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, RechargePrepareDO req, GlRechargeResult result, String payType) {
        try {
            Map<String, String> DataContentParms = new HashMap<String, String>()
            DataContentParms.put("oid_partner", payment.getMerchantCode())
            DataContentParms.put("pay_type", payType)
            DataContentParms.put("user_id", req.getUserId() + "")
            DataContentParms.put("sign_type", "MD5")
            DataContentParms.put("name_goods", "CZ")
            DataContentParms.put("no_order", req.getOrderId())
            DataContentParms.put("time_order", DateUtils.format(req.getCreateDate(), DateUtils.YYYYMMDDHHMMSS))
            DataContentParms.put("money_order", req.getAmount().setScale(2, RoundingMode.DOWN) + "")
            DataContentParms.put("notify_url", payment.getNotifyUrl() + merchant.getId())
            DataContentParms.put("return_type", "url")

            String toSign = MD5.toAscii(DataContentParms) + payment.getPrivateKey()
            DataContentParms.put("sign", MD5.md5(toSign))

            log.info("WanheScript_Prepare_Params:{}", JSON.toJSONString(DataContentParms))
            GlRequestHeader requestHeader =
                    getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode(), payment.getChannelId(), payment.getChannelName())
            log.info("WanheScript_Prepare_requestHeader:{}", JSON.toJSONString(requestHeader))
            String restr = okHttpUtil.post(payment.getPayUrl() + "/gateway/bankgateway/pay", DataContentParms, 10L, requestHeader)
            log.info("WanheScript_Prepare_resStr:{}", restr)

            JSONObject json = JSON.parseObject(restr);
            if (json == null) {
                result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
                result.setErrorMsg("网络请求超时，稍后重试")
                return
            }
            if (json.getString("ret_code") != "0000"
                    || StringUtils.isEmpty(json.getString("redirect_url"))) {
                result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
                result.setErrorMsg("商户异常")
                return
            }
            result.setRedirectUrl(json.getString("redirect_url"))
        } catch (Exception e) {
            result.setErrorCode(1)
            result.setErrorMsg("创建订单失败")
            return
        }
    }

    RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount payment = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>

        log.info("WanheScript_Notify_resMap:{}", JSON.toJSONString(resMap))
        JSONObject json = JSONObject.parseObject(resMap.get("reqBody"))
        String orderid = json.getString("no_order")
        if (StringUtils.isNotEmpty(orderid)) {
            return payQuery(okHttpUtil, payment, orderid, args[4])
        }
        return null
    }

    RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String

        Map<String, String> DataContentParms = new HashMap<String, String>()
        DataContentParms.put("oid_partner", account.getMerchantCode())
        DataContentParms.put("sign_type", "MD5")
        DataContentParms.put("no_order", orderId)

        String toSign = MD5.toAscii(DataContentParms) + account.getPrivateKey()
        DataContentParms.put("sign", MD5.md5(toSign))

        log.info("WanheScript_Query_reqMap:{}", JSON.toJSONString(DataContentParms))
        GlRequestHeader requestHeader = getRequestHeard(null, null, orderId, GlActionEnum.RECHARGE_QUERY.getCode(), account.getChannelId(), account.getChannelName())
        String resStr = okHttpUtil.post(account.getPayUrl() + "/check/checkorder/checkorderresult", DataContentParms, 10L, requestHeader)
        log.info("WanheScript_Query_resStr:{}", resStr)

        JSONObject json = JSON.parseObject(resStr)

        //支付完成：1   等待付款：2    支付关闭：3。
        if (json != null && "1" == json.getString("ret_code")) {// 0: 处理中   1：成功    2：失败
            RechargeNotify pay = new RechargeNotify()
            pay.setAmount(json.getBigDecimal("money_order").setScale(2, RoundingMode.DOWN))
            pay.setFee(BigDecimal.ZERO)
            pay.setOrderId(orderId)
            pay.setThirdOrderId("")
            pay.setRsp("{\"ret_code\":\"0000\",\"ret_msg\":\"\"}")
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
        return 0
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