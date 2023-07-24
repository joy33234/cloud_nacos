package com.seektop.fund.payment.winPay

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
import com.seektop.fund.payment.BankInfo
import com.seektop.fund.payment.GlRechargeResult
import com.seektop.fund.payment.RechargeNotify
import com.seektop.fund.payment.RechargePrepareDO
import org.apache.commons.lang3.ObjectUtils
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode

/**
 * win支付
 * @auth  joy
 * @date 2021-08-07
 */

class WinScript_recharge {

    private static final Logger log = LoggerFactory.getLogger(WinScript_recharge.class)

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

        prepare(merchant, payment, req, result)
    }

    private void prepare(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, RechargePrepareDO req, GlRechargeResult result) {

        Map<String, String> paramMap = new HashMap<>()
        paramMap.put("notify_url", payment.getNotifyUrl() + merchant.getId())
        paramMap.put("return_url", payment.getNotifyUrl() + merchant.getId())
        paramMap.put("order_id", req.getOrderId())
        paramMap.put("order_amount", req.getAmount().setScale(2, RoundingMode.DOWN).toString())
        paramMap.put("pay_type", "k2k")//卡卡
        String toSign = MD5.toAscii(paramMap) + "&key=" + payment.getPrivateKey()
        String pay_md5sign = MD5.md5(toSign).toUpperCase()
        paramMap.put("sign", pay_md5sign)

        paramMap.put("user_id", payment.getMerchantCode())
        paramMap.put("sign_type", "MD5")
        paramMap.put("method", "topay")
        paramMap.put("client_ip", req.getIp())
        if (req.getClientType() == ProjectConstant.ClientType.PC) {
            paramMap.put("client_system", "pc")
        } else {
            paramMap.put("client_system", "ios")
        }
        paramMap.put("order_name", req.getFromCardUserName())

        log.info("WinScript_Prepare_Params: {}", JSON.toJSONString(paramMap))
        GlRequestHeader requestHeader =
                getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode(), payment.getChannelId(), payment.getChannelName())
        String restr = okHttpUtil.post(payment.getPayUrl() + "/gateway", paramMap,  requestHeader)
        log.info("WinScript_Prepare_Resp: {}", restr)
        if (StringUtils.isEmpty(restr)) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("网络请求超时，稍后重试")
            return
        }
        JSONObject json = JSONObject.parseObject(restr)
        if (ObjectUtils.isEmpty(json) || json.getString("code") != "10000") {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg(json.getString("message"))
            return
        }
        JSONObject dataJSON = json.getJSONObject("params")
        if (ObjectUtils.isEmpty(dataJSON) || ObjectUtils.isEmpty(dataJSON.getString("bank_card_no"))) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg(json.getString("message"))
            return
        }
        BankInfo bankInfo = new BankInfo()
        bankInfo.setName(dataJSON.getString("card_owner"))
        bankInfo.setBankId(-1)
        bankInfo.setBankName(dataJSON.getString("bank_name"))
        bankInfo.setCardNo(dataJSON.getString("bank_card_no"))
        result.setBankInfo(bankInfo)
    }

    RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount payment = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>
        log.info("WinScript_Notify_resMap:{}", JSON.toJSONString(resMap))
        String orderid = resMap.get("order_id")
        String thirdOderId = resMap.get("trade_no")
        if (StringUtils.isNotEmpty(orderid) && StringUtils.isNotEmpty(thirdOderId)) {
            return payQuery(okHttpUtil, payment, orderid,thirdOderId, args[4])
        }
        return null
    }

    RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount payment = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String
        String thirdOderId = args[3] as String
        String keyValue = payment.getPrivateKey() // 商家密钥

        String pay_memberid = payment.getMerchantCode()
        String pay_orderid = orderId

        Map<String, String> paramMap = new HashMap<>()
        paramMap.put("trade_no", thirdOderId)
        String toSign = MD5.toAscii(paramMap) + "&key=" + keyValue
        String pay_md5sign = MD5.md5(toSign).toUpperCase()
        paramMap.put("sign", pay_md5sign)

        paramMap.put("user_id", pay_memberid)
        paramMap.put("method", "orderQuery")

        log.info("WinScript_Query_Params: {}", JSON.toJSONString(paramMap))
        GlRequestHeader requestHeader =
                getRequestHeard(null, null, orderId, GlActionEnum.RECHARGE_QUERY.getCode(), payment.getChannelId(), payment.getChannelName())
        String result = okHttpUtil.post(payment.getPayUrl() + "/gateway", paramMap, 30L, requestHeader)
        log.info("WinScript_Query_resStr: {}", result)

        JSONObject json = JSON.parseObject(result)
        if (json == null) {
            return null
        }
        //订单支付状态， WAIT：等待支付；SUCCESS:支付成功；FAIL:支付失败
        String code = json.getString("code")
        String trade_state = json.getString("trade_status")
        if (("10000" != code) || "SUCCESS" != trade_state) {
            return null
        }
        RechargeNotify pay = new RechargeNotify()
        pay.setAmount(json.getBigDecimal("buyer_pay_amount").setScale(0, RoundingMode.UP))
        pay.setFee(BigDecimal.ZERO)
        pay.setOrderId(orderId)
        pay.setThirdOrderId(json.getString("trade_no"))
        pay.setRsp("SUCCESS")
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
        return true
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
        return FundConstant.ShowType.DETAIL
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