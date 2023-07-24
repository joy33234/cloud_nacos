package com.seektop.fund.payment.epay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
import com.seektop.common.utils.DateUtils
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
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.util.ObjectUtils

import java.math.RoundingMode

/**
 * epay支付
 * @auth joy
 * @date 2021-10-16
 */

class EpayScript_recharge {

    private static final Logger log = LoggerFactory.getLogger(EpayScript_recharge.class)

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
            payType = "bankpay"
        }
        if (StringUtils.isNotEmpty(payType)) {
            prepareScan(merchant, payment, req, result, payType)
        } else {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("该充值方式暂不支持")
            return
        }
    }

    private void prepareScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, RechargePrepareDO req, GlRechargeResult result, String service) {
        Map<String, String> DataContentParms = new HashMap<String, String>()
        DataContentParms.put("appId", payment.getMerchantCode())
        DataContentParms.put("orderId", req.getOrderId())
        DataContentParms.put("amount", req.getAmount().setScale(2, RoundingMode.DOWN) + "")
        DataContentParms.put("remark", "CZ")
        DataContentParms.put("time", DateUtils.format(req.getCreateDate(), DateUtils.YYYY_MM_DD_HH_MM_SS))
        DataContentParms.put("notify", payment.getNotifyUrl() + merchant.getId())
        DataContentParms.put("service", service)
        DataContentParms.put("secret", payment.getPrivateKey())
        if (merchant.getClientType() == ProjectConstant.ClientType.PC) {
            DataContentParms.put("way", "pc")
        } else {
            DataContentParms.put("way", "h5")
        }
        DataContentParms.put("payName", req.getFromCardUserName())
        String toSign = MD5.toAscii(DataContentParms)
        DataContentParms.put("sign", MD5.md5(toSign))
        DataContentParms.remove("secret")

        log.info("EpayScript_Prepare_Params:{}", JSON.toJSONString(DataContentParms))
        GlRequestHeader requestHeader =
                getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode(), payment.getChannelId(), payment.getChannelName())
        String restr = okHttpUtil.post(payment.getPayUrl() + "/service/bankOrder", DataContentParms, 30L, requestHeader)
        log.info("EpayScript_Prepare_resStr:{}", restr)

        JSONObject json = JSON.parseObject(restr);
        if (json == null) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("网络请求超时，稍后重试")
            return
        }
        if (json.getString("code") != "1000") {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg("请求失败:" + json.getString("message"))
            return
        }
        String bankName = json.getString("bankName")
        String bankNo = json.getString("bankNo")
        String cardholder = json.getString("cardholder")
        String bankAddr = json.getString("bankAddr")

        if (StringUtils.isEmpty(bankName) || StringUtils.isEmpty(bankNo) || StringUtils.isEmpty(cardholder)) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg("商户异常" + json.getString("message"))
            return
        }
        BankInfo bankInfo = new BankInfo()
        bankInfo.setName(cardholder)
        bankInfo.setBankId(-1)
        bankInfo.setBankName(bankName)
        bankInfo.setBankBranchName(bankAddr)
        bankInfo.setCardNo(bankNo)
        result.setBankInfo(bankInfo)
        result.setThirdOrderId(json.getString("sn"))
    }

    RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount payment = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>

        log.info("EpayScript_Notify_resMap:{}", JSON.toJSONString(resMap))
        String orderid = resMap.get("orderId")
        String thirdOrderId = resMap.get("sn")

        String originalSign = resMap.remove("sign")
        resMap.put("secret", payment.getPrivateKey())
        String sign = MD5.md5( MD5.toAscii(resMap))

        if (StringUtils.isNotEmpty(orderid) && StringUtils.isNotEmpty(thirdOrderId)  && sign == originalSign) {
            return payQuery(okHttpUtil, payment, orderid, thirdOrderId)
        }
        return null
    }

    RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String
        String thirdOrderId = args[3] as String

        Map<String, String> DataContentParms = new HashMap<String, String>()
        DataContentParms.put("appId", account.getMerchantCode())
        DataContentParms.put("time", DateUtils.format(new Date(), DateUtils.YYYY_MM_DD_HH_MM_SS))
        DataContentParms.put("sn", thirdOrderId)
        DataContentParms.put("secret", account.getPrivateKey())

        String toSign = MD5.toAscii(DataContentParms)
        DataContentParms.put("sign", MD5.md5(toSign))
        DataContentParms.remove("secret")

        log.info("EpayScript_Query_reqMap:{}", JSON.toJSONString(DataContentParms))
        GlRequestHeader requestHeader = getRequestHeard(null, null, orderId, GlActionEnum.RECHARGE_QUERY.getCode(), account.getChannelId(), account.getChannelName())
        String resStr = okHttpUtil.post(account.getPayUrl() + "/service/queryOrder", DataContentParms, 30L, requestHeader)
        log.info("EpayScript_Query_resStr:{}", resStr)

        JSONObject json = JSON.parseObject(resStr)

        //支付状态 SUCCESS=支付成功，UNPAY=未支付
        if (json != null && "SUCCESS" == json.getString("orderStatus") && !ObjectUtils.isEmpty(json.getString("finish"))) {
            RechargeNotify pay = new RechargeNotify()
            pay.setAmount(json.getBigDecimal("amount").setScale(2, RoundingMode.DOWN))
            pay.setFee(BigDecimal.ZERO)
            pay.setOrderId(orderId)
            pay.setThirdOrderId(thirdOrderId)
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
}