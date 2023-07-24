package com.seektop.fund.payment.elephantpay

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
import com.seektop.fund.payment.groovy.BaseScript
import com.seektop.fund.payment.groovy.ResourceEnum
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode

/**
 * 大象支付
 * @auth  joy
 * @date 20202-12-30
 */

class ElephantScript_recharge {

    private static final Logger log = LoggerFactory.getLogger(ElephantScript_recharge.class)

    private OkHttpUtil okHttpUtil

    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness

    RechargeNotify result(Object[] args) throws GlobalException {
        return notify(args)
    }

    void pay(Object[] args) {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantApp merchant = args[1] as GlPaymentMerchantApp
        GlPaymentMerchantaccount payment = args[2] as GlPaymentMerchantaccount
        RechargePrepareDO req = args[3] as RechargePrepareDO
        GlRechargeResult result = args[4] as GlRechargeResult
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[5], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        String ptype = ""
        if (merchant.getPaymentId() == FundConstant.PaymentType.ALI_PAY) {
            ptype = "1"
        }

        if (StringUtils.isNotEmpty(ptype)) {
            prepare(merchant, payment, req, result, ptype)
        } else {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("支付方式不支持，请更换")
            return
        }


    }

    private void prepare(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, RechargePrepareDO req, GlRechargeResult result, String ptype) {

        Map<String, String> paramMap = new HashMap<>()
        paramMap.put("mch_id", payment.getMerchantCode())
        paramMap.put("order_sn", req.getOrderId())
        paramMap.put("ptype", ptype)
        paramMap.put("money", req.getAmount().setScale(2, RoundingMode.DOWN).toString())
        paramMap.put("goods_desc", "CZ")
        paramMap.put("client_ip", req.getIp())
        paramMap.put("format", "page")
        paramMap.put("notify_url", payment.getNotifyUrl() + merchant.getId())
        paramMap.put("time", System.currentTimeMillis()+"")

        String toSign = MD5.toAscii(paramMap) + "&key=" + payment.getPrivateKey()
        paramMap.put("sign", MD5.md5(toSign).toLowerCase())

        log.info("ElephantScript_Prepare_Params: {}", JSON.toJSONString(paramMap))
        GlRequestHeader requestHeader =
                getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode(), payment.getChannelId(), payment.getChannelName())
        String restr = okHttpUtil.post(payment.getPayUrl() + "/order/place", paramMap, 10L, requestHeader)
        log.info("ElephantScript_Prepare_Resp: {}", restr.substring(0,20))
        if (StringUtils.isEmpty(restr)) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("网络请求超时，稍后重试")
            return
        }
        result.setMessage(restr)
    }

    RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount payment = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>
        log.info("ElephantScript_Notify_resMap:{}", JSON.toJSONString(resMap))
        String orderid = resMap.get("sh_order")
        if (StringUtils.isNotEmpty(orderid)) {
            return payQuery(okHttpUtil, payment, orderid, args[4])
        }
        return null
    }

    RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount payment = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String

        Map<String, String> paramMap = new HashMap<>()
        paramMap.put("mch_id", payment.getMerchantCode())
        paramMap.put("out_order_sn", orderId)
        paramMap.put("time", System.currentTimeMillis()+"")

        String toSign = MD5.toAscii(paramMap) + "&key=" + payment.getPrivateKey()
        paramMap.put("sign", MD5.md5(toSign).toLowerCase())

        log.info("ElephantScript_Query_Params: {}", JSON.toJSONString(paramMap))
        GlRequestHeader requestHeader =
                getRequestHeard(null, null, orderId, GlActionEnum.RECHARGE_QUERY.getCode(), payment.getChannelId(), payment.getChannelName())
        String result = okHttpUtil.post(payment.getPayUrl() + "/order/query/", paramMap, 10L, requestHeader)
        log.info("ElephantScript_Query_resStr: {}", result)

        JSONObject json = JSON.parseObject(result)
        if (json == null) {
            return null
        }
        JSONObject dataJSON = json.getJSONObject("data")
        if (dataJSON == null) {
            return null
        }
        //0已提交  1已接单  2超时补单   3订单失败   4交易完成  5未接单
        String code = json.getString("code")
        String status = dataJSON.getString("status")
        if (("1" != code) || "4" != status) {
            return null
        }
        RechargeNotify pay = new RechargeNotify()
        pay.setAmount(dataJSON.getBigDecimal("money").setScale(2, RoundingMode.DOWN))
        pay.setFee(BigDecimal.ZERO)
        pay.setOrderId(orderId)
        pay.setThirdOrderId(dataJSON.getString("orderSn"))
        return pay
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
}