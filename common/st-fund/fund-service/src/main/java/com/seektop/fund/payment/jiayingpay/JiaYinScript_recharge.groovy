package com.seektop.fund.payment.jiayingpay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
import com.seektop.common.utils.DateUtils
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
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode

/**
 * 嘉银支付
 * @auth  joy
 * @date 20202-12-24
 */

class JiaYinScript_recharge {

    private static final Logger log = LoggerFactory.getLogger(JiaYinScript_recharge.class)

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

        prepare(merchant, payment, req, result)
    }

    private void prepare(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, RechargePrepareDO req, GlRechargeResult result) {

        Map<String, String> paramMap = new HashMap<>()
        paramMap.put("pay_memberid", payment.getMerchantCode())
        paramMap.put("pay_orderid", req.getOrderId())
        paramMap.put("pay_applydate", DateUtils.format(req.getCreateDate(), DateUtils.YYYY_MM_DD_HH_MM_SS))
        paramMap.put("pay_bankcode", "946")//卡卡
        paramMap.put("pay_notifyurl", payment.getNotifyUrl() + merchant.getId())
        paramMap.put("pay_callbackurl", payment.getNotifyUrl() + merchant.getId())
        paramMap.put("pay_amount", req.getAmount().setScale(2, RoundingMode.DOWN).toString())

        String toSign = MD5.toAscii(paramMap) + "&key=" + payment.getPrivateKey()
        String pay_md5sign = MD5.md5(toSign).toUpperCase()
        paramMap.put("pay_md5sign", pay_md5sign)
        paramMap.put("pay_productname", "CZ")
        // 网关及支付宝转银联需要实名
        if (FundConstant.PaymentType.BANKCARD_TRANSFER == merchant.getPaymentId()
                || FundConstant.PaymentType.ALI_TRANSFER == merchant.getPaymentId()) {
            paramMap.put("pay_attach", req.getFromCardUserName())
        }

        log.info("JiaYinScript_Prepare_Params_11225: {}", JSON.toJSONString(paramMap))
        GlRequestHeader requestHeader =
                getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode(), payment.getChannelId(), payment.getChannelName())
        String restr = okHttpUtil.post(payment.getPayUrl() + "/Pay_Index.html", paramMap, 10L, requestHeader)
        log.info("JiaYinScript_Prepare_Resp_11225: {}", restr.substring(0,20))
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
        log.info("JiaYinScript_Notify_resMap:{}", JSON.toJSONString(resMap))
        String orderid = resMap.get("orderid")
        if (StringUtils.isNotEmpty(orderid)) {
            return payQuery(okHttpUtil, payment, orderid, args[4])
        }
        return null
    }

    RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount payment = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String
        String keyValue = payment.getPrivateKey() // 商家密钥

        String pay_memberid = payment.getMerchantCode()
        String pay_orderid = orderId

        Map<String, String> paramMap = new HashMap<>()
        paramMap.put("pay_memberid", pay_memberid)
        paramMap.put("pay_orderid", pay_orderid)

        String toSign = MD5.toAscii(paramMap) + "&key=" + keyValue
        String pay_md5sign = MD5.md5(toSign).toUpperCase()
        paramMap.put("pay_md5sign", pay_md5sign)

        log.info("JiaYinScript_Query_Params: {}", JSON.toJSONString(paramMap))
        GlRequestHeader requestHeader =
                getRequestHeard(null, null, orderId, GlActionEnum.RECHARGE_QUERY.getCode(), payment.getChannelId(), payment.getChannelName())
        String result = okHttpUtil.post(payment.getPayUrl() + "/Pay_Trade_query.html", paramMap, 10L, requestHeader)
        log.info("JiaYinScript_Query_resStr: {}", result)

        JSONObject json = JSON.parseObject(result)
        if (json == null) {
            return null
        }
        //NOTPAY-未支付 SUCCESS已支付
        String returncode = json.getString("returncode")
        String trade_state = json.getString("trade_state")
        if (("00" != returncode) || "SUCCESS" != trade_state) {
            return null
        }
        RechargeNotify pay = new RechargeNotify()
        pay.setAmount(json.getBigDecimal("amount").setScale(0, RoundingMode.UP))
        pay.setFee(BigDecimal.ZERO)
        pay.setOrderId(orderId)
        pay.setThirdOrderId(json.getString("transaction_id"))
        pay.setRsp("OK")
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