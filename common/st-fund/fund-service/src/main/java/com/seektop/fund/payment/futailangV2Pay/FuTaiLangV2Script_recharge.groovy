package com.seektop.fund.payment.futailangV2Pay

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
import org.springframework.util.ObjectUtils

import java.math.RoundingMode

class FuTaiLangV2Script_recharge {

    private static final Logger log = LoggerFactory.getLogger(FuTaiLangV2Script_recharge.class)

    private OkHttpUtil okHttpUtil

    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness

    RechargeNotify result(Object[] args) throws GlobalException {
        return notify(args)
    }

    void pay(Object[] args) {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantApp merchant = args[1] as GlPaymentMerchantApp
        GlPaymentMerchantaccount account = args[2] as GlPaymentMerchantaccount
        RechargePrepareDO req = args[3] as RechargePrepareDO
        GlRechargeResult result = args[4] as GlRechargeResult
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[5], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

        String paymentType = ""
        if (merchant.getPaymentId() == FundConstant.PaymentType.ALI_PAY) {
            paymentType = account.getPublicKey()
        }
        if (StringUtils.isNotEmpty(paymentType)) {
            prepareScan(merchant, account, req, result, paymentType)
        } else {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("该充值方式暂不支持")
            return
        }
    }

    private void prepareScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result, String pay_channel_id) {

        Map<String, String> paramMap = new HashMap<>()
        paramMap.put("pay_customer_id", account.getMerchantCode())
        paramMap.put("pay_order_id", req.getOrderId())
        paramMap.put("pay_apply_date", System.currentTimeSeconds().toString())
        paramMap.put("pay_notify_url", account.getNotifyUrl() + merchant.getId())
        paramMap.put("pay_amount", req.getAmount().setScale(2, RoundingMode.DOWN).toString())
        paramMap.put("pay_channel_id", pay_channel_id)
        paramMap.put("user_name", req.getFromCardUserName())

        String toSign =  MD5.toAscii(paramMap) + "&key=" + account.getPrivateKey()
        paramMap.put("pay_md5_sign", MD5.md5(toSign).toUpperCase())

        log.info("FuTaiLangV2Script_Prepare_Params: {}", JSON.toJSONString(paramMap))
        GlRequestHeader requestHeader =
                getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode(), account.getChannelId(), account.getChannelName())

        String restr = okHttpUtil.postJSON(account.getPayUrl() + "/api/pay_order", JSON.toJSONString(paramMap),  requestHeader)
        JSONObject json = JSON.parseObject(restr)
        log.info("FuTaiLangV2Script_Prepare_Resp: {}", json)
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
        if (ObjectUtils.isEmpty(dataJSON)) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg(json.getString("message"))
            return
        }
        result.setThirdOrderId(dataJSON.getString("transaction_id"))
        result.setRedirectUrl(dataJSON.getString("view_url"))

    }

    RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>
        log.info("FuTaiLangV2Script_Notify_resMap:{}", JSON.toJSONString(resMap))
        JSONObject json = JSONObject.parseObject(resMap.get("reqBody"))
        if (StringUtils.isNotEmpty(json.getString("order_id"))) {
            return payQuery(okHttpUtil, account, json.getString("order_id"), args[4])
        }
        return null
    }

    RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String

        Map<String, String> paramMap = new HashMap<>()
        paramMap.put("pay_customer_id", account.getMerchantCode())
        paramMap.put("pay_order_id", orderId)
        paramMap.put("pay_apply_date", System.currentTimeSeconds().toString())

        String toSign =  MD5.toAscii(paramMap) + "&key=" + account.getPrivateKey()
        paramMap.put("pay_md5_sign", MD5.md5(toSign).toUpperCase())

        log.info("FuTaiLangV2Script_Query_Params: {}", JSON.toJSONString(paramMap))
        GlRequestHeader requestHeader =
                getRequestHeard(null, null, orderId, GlActionEnum.RECHARGE_QUERY.getCode(), account.getChannelId(), account.getChannelName())
        String result = okHttpUtil.postJSON(account.getPayUrl() + "/api/query_transaction", JSON.toJSONString(paramMap), requestHeader)
        log.info("FuTaiLangV2Script_Query_resStr: {}", result)

        JSONObject json = JSON.parseObject(result)
        if (org.apache.commons.lang3.ObjectUtils.isEmpty(json) || "0" != json.getString("code")) {
            return null
        }
        JSONObject dataJSON = json.getJSONObject("data");
        String status = dataJSON.getString("status")
        // 0：未处理   1：成功，未回调   2：成功，已回调   3：失败   4：失败，订单金额不相符  5：提单失
        if (dataJSON.getString("status") == "1" || dataJSON.getString("status") == "2") {
            RechargeNotify pay = new RechargeNotify()
            pay.setAmount(dataJSON.getBigDecimal("real_amount").setScale(2, RoundingMode.DOWN))//paid_amount实际支付金额
            pay.setFee(BigDecimal.ZERO)
            pay.setOrderId(orderId)
            pay.setThirdOrderId(json.getString("transaction_id"))
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
        return false
    }

    /**
     * 根据支付方式判断-转帐是否需要实名
     *
     * @param args
     * @return
     */
    public boolean needName(Object[] args) {
        return true
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