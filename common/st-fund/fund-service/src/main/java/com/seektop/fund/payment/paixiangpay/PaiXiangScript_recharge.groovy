package com.seektop.fund.payment.paixiangpay

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
import com.seektop.fund.payment.BankInfo
import com.seektop.fund.payment.GlRechargeResult
import com.seektop.fund.payment.RechargeNotify
import com.seektop.fund.payment.RechargePrepareDO
import com.seektop.fund.payment.groovy.BaseScript
import com.seektop.fund.payment.groovy.ResourceEnum
import org.apache.commons.lang3.ObjectUtils
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode

/**
 * 派翔支付
 * @auth joy
 * @date 2021-06-01
 *
 */
class PaiXiangScript_recharge {

    private static final Logger log = LoggerFactory.getLogger(PaiXiangScript_recharge.class)

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

        String paymentType = ""
        if (merchant.getPaymentId() == FundConstant.PaymentType.BANKCARD_TRANSFER) {
            paymentType = "929"
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
        String keyValue = payment.getPrivateKey() // 商家密钥

        Map<String, String> paramMap = new HashMap<>()
        paramMap.put("pay_memberid", payment.getMerchantCode())
        paramMap.put("pay_orderid", req.getOrderId())
        paramMap.put("pay_applydate", DateUtils.format(req.getCreateDate(), DateUtils.YYYY_MM_DD_HH_MM_SS))
        paramMap.put("pay_bankcode", paymentType)
        paramMap.put("pay_callbackurl", payment.getResultUrl() + merchant.getId())
        paramMap.put("pay_notifyurl", payment.getResultUrl() + merchant.getId())
        paramMap.put("pay_amount", req.getAmount().setScale(2, RoundingMode.DOWN).toString())

        String toSign = MD5.toAscii(paramMap) + "&key=" + keyValue
        paramMap.put("pay_md5sign", MD5.md5(toSign).toUpperCase())
        paramMap.put("pay_productname", "CZ")
        paramMap.put("return_type", "json")//返回数据格式
        paramMap.put("username", req.getFromCardUserName())//付款人

        log.info("PaiXiangScript_Prepare_Params: {}", JSON.toJSONString(paramMap))
        GlRequestHeader requestHeader =
                getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode(), payment.getChannelId(), payment.getChannelName())
        String restr = okHttpUtil.post(payment.getPayUrl() + "/Pay_Index.html", paramMap,  requestHeader)
        log.info("PaiXiangScript_Prepare_Resp: {}", restr)
        if (StringUtils.isEmpty(restr)) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("网络请求超时，稍后重试")
            return
        }
        JSONObject json = JSONObject.parseObject(restr)
        if (ObjectUtils.isEmpty(json) || json.getString("code") != "200") {
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
        BankInfo bankInfo = new BankInfo()
        bankInfo.setName(dataJSON.getString("account_name"))
        bankInfo.setBankId(-1)
        bankInfo.setBankName(dataJSON.getString("bank_name"))
        bankInfo.setBankBranchName(dataJSON.getString("bank_subbranch"))
        bankInfo.setCardNo(dataJSON.getString("bank_card"))
        result.setBankInfo(bankInfo)
    }

    RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount payment = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>
        log.info("PaiXiangScript_Notify_resMap:{}", JSON.toJSONString(resMap))
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

        Map<String, String> paramMap = new HashMap<>()
        paramMap.put("pay_memberid", payment.getMerchantCode())
        paramMap.put("pay_orderid", orderId)
        String toSign = MD5.toAscii(paramMap) + "&key=" + keyValue
        paramMap.put("pay_md5sign", MD5.md5(toSign).toUpperCase())

        String queryUrl = payment.getPayUrl() + "/Pay_Trade_query.html"
        log.info("PaiXiangScript_Query_Params: {}", JSON.toJSONString(paramMap))
        GlRequestHeader requestHeader =
                getRequestHeard(null, null, orderId, GlActionEnum.RECHARGE_QUERY.getCode(), payment.getChannelId(), payment.getChannelName())

        String result = okHttpUtil.post(queryUrl, paramMap, 30L, requestHeader)
        log.info("PaiXiangScript_Query_resStr: {}", result)

        JSONObject json = JSON.parseObject(result)
        if (json == null || "00" != json.getString("returncode") || json.getString("trade_state") != "SUCCESS") {
            return null
        }

        //支付完成：SUCCESS：支付成功，NOTPAY：未支付
        RechargeNotify pay = new RechargeNotify()
        pay.setAmount(json.getBigDecimal("amount").setScale(2, RoundingMode.DOWN))
        pay.setFee(BigDecimal.ZERO)
        pay.setOrderId(orderId)
        pay.setThirdOrderId(json.getString("transaction_id"))
        pay.setRsp("OK")
        return pay
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
        Integer paymentId = args[1] as Integer
        if (paymentId == FundConstant.PaymentType.BANKCARD_TRANSFER
                || paymentId == FundConstant.PaymentType.ALI_TRANSFER
                || paymentId == FundConstant.PaymentType.UNION_TRANSFER) {
            return true
        }
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
        Integer paymentId = args[1] as Integer
        if (paymentId == FundConstant.PaymentType.BANKCARD_TRANSFER
                || paymentId == FundConstant.PaymentType.ALI_TRANSFER
                || paymentId == FundConstant.PaymentType.UNION_TRANSFER) {
            return FundConstant.ShowType.DETAIL
        }
        return FundConstant.ShowType.NORMAL
    }
}