package com.seektop.fund.payment.vippay

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
import com.seektop.fund.payment.BankInfo
import com.seektop.fund.payment.GlRechargeResult
import com.seektop.fund.payment.RechargeNotify
import com.seektop.fund.payment.RechargePrepareDO
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode

/**
 * @desc  VIP支付
 * @auth joy
 * @date 2021-06-25
 */
class VipScript_recharge {

    private static final Logger log = LoggerFactory.getLogger(VipScript_recharge.class)

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
        prepareScan(merchant, payment, req, result)
    }

    private void prepareScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, RechargePrepareDO req, GlRechargeResult result) {
        Map<String, String> paramMap = new LinkedHashMap<>()
        paramMap.put("pay_amount", req.getAmount().setScale(2, RoundingMode.DOWN).toString())
        paramMap.put("pay_apply_date", System.currentTimeSeconds().toString())
        paramMap.put("pay_channel_id", payment.getPublicKey())
        paramMap.put("pay_customer_id", payment.getMerchantCode())
        paramMap.put("pay_notify_url", payment.getNotifyUrl() + merchant.getId())
        paramMap.put("pay_order_id", req.getOrderId())



        // 网关及支付宝转银联需要实名
        if (FundConstant.PaymentType.BANKCARD_TRANSFER == merchant.getPaymentId()
                || FundConstant.PaymentType.ALI_TRANSFER == merchant.getPaymentId()) {
            paramMap.put("user_name", req.getFromCardUserName())
        }

        String toSign = MD5.toAscii(paramMap) + "&key=" + payment.getPrivateKey()
        paramMap.put("pay_md5_sign", MD5.md5(toSign).toUpperCase())

        log.info("VipScript_Prepare_Params: {}", JSON.toJSONString(paramMap))
        GlRequestHeader requestHeader =
                getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode(),payment.getChannelId(), payment.getChannelName())
        String restr = okHttpUtil.postJSON(payment.getPayUrl() + "/api/pay_order", JSON.toJSONString(paramMap),  requestHeader)
        JSONObject json = JSON.parseObject(restr)
        log.info("VipScript_Prepare_Resp: {}", json)
        if (json == null) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("网络请求超时，稍后重试")
            return
        }
        if (json.getString("code") != "0") {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg(json.getString("message"))
            return
        }

        JSONObject dataJSON = json.getJSONObject("data")

        result.setErrorCode(FundConstant.RechargeErrorCode.NORMAL)
        BankInfo bankInfo = new BankInfo()
        bankInfo.setName(dataJSON.getString("bank_owner"))
        bankInfo.setBankId(-1)
        bankInfo.setBankName(dataJSON.getString("bank_name"))
        bankInfo.setBankBranchName(dataJSON.getString("bank_from"))
        bankInfo.setCardNo(dataJSON.getString("bank_no"))
        result.setBankInfo(bankInfo)

        result.setThirdOrderId(dataJSON.getString("transaction_id"))
        result.setAmount(dataJSON.getBigDecimal("real_price"))

    }

    RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount payment = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>
        log.info("VipScript_Notify_resMap:{}", JSON.toJSONString(resMap))
        JSONObject json = JSONObject.parseObject(resMap.get("reqBody"));
        String orderid = json.getString("order_id")
        if (StringUtils.isNotEmpty(orderid)) {
            return payQuery(okHttpUtil, payment, orderid, args[4])
        }
        return null
    }

    RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount payment = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String

        Map<String, String> paramMap = new LinkedHashMap<>()
        paramMap.put("pay_apply_date", System.currentTimeSeconds().toString())
        paramMap.put("pay_customer_id", payment.getMerchantCode())
        paramMap.put("pay_order_id", orderId)

        String toSign = MD5.toAscii(paramMap) + "&key=" + payment.getPrivateKey()
        paramMap.put("pay_md5_sign", MD5.md5(toSign).toUpperCase())

        String queryUrl = payment.getPayUrl() + "/api/query_transaction"
        log.info("VipScript_Query_Params: {}", JSON.toJSONString(paramMap))
        GlRequestHeader requestHeader =
                getRequestHeard(null, null, orderId, GlActionEnum.RECHARGE_QUERY.getCode(),payment.getChannelId(), payment.getChannelName())
        String result = okHttpUtil.postJSON(queryUrl, JSON.toJSONString(paramMap),  requestHeader)
        log.info("VipScript_Query_resStr: {}", result)

        JSONObject json = JSON.parseObject(result)
        if (json == null) {
            return null
        }
        String code = json.getString("code")
        JSONObject dataJSON = json.getJSONObject("data")

        if (dataJSON == null || "0" != code) {
            return null
        }
        //0:未处理   1:成功，未返回  2:成功，已返回  3：失败，逾期失效   4：失败，订单金额不相符  5：失败，订单异常
        if (dataJSON.getString("status") == "1" || dataJSON.getString("status") == "2") {
            RechargeNotify pay = new RechargeNotify()
            pay.setAmount(dataJSON.getBigDecimal("real_amount").setScale(2, RoundingMode.DOWN))
            pay.setFee(BigDecimal.ZERO)
            pay.setOrderId(orderId)
            pay.setThirdOrderId(dataJSON.getString("transaction_id"))
            pay.setRsp("OK")
            return pay
        }
        return null
    }



    void cancel(Object[] args) throws GlobalException {

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
}