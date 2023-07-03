package com.seektop.fund.payment.upay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
import com.seektop.common.utils.DateUtils
import com.seektop.common.utils.HtmlTemplateUtils
import com.seektop.common.utils.MD5
import com.seektop.constant.FundConstant
import com.seektop.enumerate.GlActionEnum
import com.seektop.exception.GlobalException
import com.seektop.fund.business.GlPaymentChannelBankBusiness
import com.seektop.fund.model.GlPaymentMerchantApp
import com.seektop.fund.model.GlPaymentMerchantaccount
import com.seektop.fund.model.GlWithdraw
import com.seektop.fund.model.GlWithdrawMerchantAccount
import com.seektop.fund.payment.*
import com.seektop.fund.payment.groovy.BaseScript
import com.seektop.fund.payment.groovy.ResourceEnum
import org.apache.commons.lang.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode

/**
 * UPAY
 *
 * @author walter
 */
public class UPAYScript {

    private static final Logger log = LoggerFactory.getLogger(UPAYScript.class)

    private OkHttpUtil okHttpUtil

    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness

    /**
     * 封装支付请求参数
     *
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
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[5], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        String pay_bankcode = null
        if (FundConstant.PaymentType.ONLINE_PAY == merchant.getPaymentId()) {// 网银
            pay_bankcode = "1092"
        } else if (FundConstant.PaymentType.ALI_PAY == merchant.getPaymentId()) {//支付宝
            pay_bankcode = "1012"
        } else if (FundConstant.PaymentType.WECHAT_PAY == merchant.getPaymentId()) { // 微信
            pay_bankcode = "1453"
        } else if (FundConstant.PaymentType.UNION_PAY == merchant.getPaymentId()) { // 云闪付
            pay_bankcode = "1093"
        }

        if (org.springframework.util.StringUtils.isEmpty(pay_bankcode)) {
            return
        }

        String keyValue = payment.getPrivateKey()

        Map<String, String> paramMap = new HashMap<>()
        paramMap.put("pay_memberid", payment.getMerchantCode())
        paramMap.put("pay_orderid", req.getOrderId())
        paramMap.put("pay_applydate", DateUtils.format(req.getCreateDate(), DateUtils.YYYY_MM_DD_HH_MM_SS))
        paramMap.put("pay_bankcode", pay_bankcode)
        paramMap.put("pay_notifyurl", payment.getNotifyUrl() + merchant.getId())
        paramMap.put("pay_callbackurl", payment.getResultUrl() + merchant.getId())
        paramMap.put("pay_amount", req.getAmount().setScale(2, RoundingMode.DOWN).toString())

        String toSign = MD5.toAscii(paramMap)
        toSign = toSign + "&key=" + keyValue
        String sign = MD5.md5(toSign).toUpperCase()

        paramMap.put("pay_md5sign", sign)
        paramMap.put("pay_productname", "CZ")
        log.info("UPAYScript_Prepare_paramMap: {}", JSON.toJSONString(paramMap))
        result.setMessage(HtmlTemplateUtils.getPost(payment.getPayUrl() + "/Pay_Index.html", paramMap))
        log.info("UPAYScript_Prepare_message: {}", JSON.toJSONString(paramMap))

    }


    /**
     * 支付返回结果校验
     *
     * @param payment
     * @param resMap
     * @return
     */
    public RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount payment = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[4], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        log.info("UPAYScript_Notify_resMap:{}", JSON.toJSONString(resMap))
        String orderid = resMap.get("orderid")
        String returncode = resMap.get("returncode")
        if (StringUtils.isNotEmpty(orderid) && StringUtils.isNotEmpty(returncode) && "00" == (returncode)) {
            return payQuery(okHttpUtil, payment, orderid, args[4])
        }
        return null
    }

    public RechargeNotify result(Object[] args) throws GlobalException {
        return notify(args)
    }

    public RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount payment = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        String keyValue = payment.getPrivateKey() // 商家密钥

        Map<String, String> paramMap = new HashMap<>()
        paramMap.put("pay_memberid", payment.getMerchantCode())
        paramMap.put("pay_orderid", orderId)

        String toSign = MD5.toAscii(paramMap)
        toSign = toSign + "&key=" + keyValue
        String pay_md5sign = MD5.md5(toSign).toUpperCase()
        paramMap.put("pay_md5sign", pay_md5sign)
        String queryUrl = payment.getPayUrl() + "/Pay_Trade_query.html"
        log.info("UPAYScript_Query_paramMap: {}", orderId, JSON.toJSONString(paramMap))
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.DEPOSIT_QUERY.getCode())
                .channelId(PaymentMerchantEnum.UPAY.getCode() + "")
                .channelName(PaymentMerchantEnum.UPAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId(orderId)
                .build()
        String result = okHttpUtil.post(queryUrl, paramMap, requestHeader)
        log.info("UPAYScript_Query_result: {}", orderId, result)

        JSONObject json = JSON.parseObject(result)
        if (json == null) {
            return null
        }
        String returncode = json.getString("returncode")
        String trade_state = json.getString("trade_state")
        if (("00" != (returncode)) || "SUCCESS" != (trade_state)) {
            return null
        }
        RechargeNotify pay = new RechargeNotify()
        pay.setAmount(json.getBigDecimal("amounts"))
        pay.setFee(BigDecimal.ZERO)
        pay.setOrderId(orderId)
        pay.setThirdOrderId(json.getString("transaction_id"))
        return pay
    }

    public WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        String keyValue = merchantAccount.getPrivateKey() // 商家密钥


        Map<String, String> paramMap = new HashMap<>()
        paramMap.put("pay_memberid", merchantAccount.getMerchantCode())
        paramMap.put("pay_out_trade_no", req.getOrderId())
        paramMap.put("pay_money", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString())
        paramMap.put("pay_bankname", glPaymentChannelBankBusiness.getBankName(req.getBankId(), merchantAccount.getChannelId()))
        paramMap.put("pay_subbranch", "支行")
        paramMap.put("pay_accountname", req.getName())
        paramMap.put("pay_cardnumber", req.getCardNo())
        paramMap.put("pay_province", "上海市")
        paramMap.put("pay_city", "上海市")
        paramMap.put("pay_notifyurl", merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId())
        String toSign = MD5.toAscii(paramMap)
        toSign = toSign + "&key=" + keyValue
        String pay_md5sign = MD5.md5(toSign).toUpperCase()
        paramMap.put("pay_md5sign", pay_md5sign)
        log.info("UPAYScript_Transfer_Params: {}", JSON.toJSONString(paramMap))
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.WITHDRAW.getCode())
                .channelId(PaymentMerchantEnum.UPAY.getCode() + "")
                .channelName(PaymentMerchantEnum.UPAY.getPaymentName())
                .userId(req.getUserId().toString())
                .userName(req.getUsername())
                .tradeId(req.getOrderId())
                .build()
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/Payment_Dfpay_add.html", paramMap, requestHeader)
        log.info("UPAYScript_Transfer_resStr: {}", resStr)

        WithdrawResult result = new WithdrawResult()
        result.setOrderId(req.getOrderId())
        result.setReqData(JSON.toJSONString(paramMap))
        result.setResData(resStr)

        if (StringUtils.isEmpty(resStr)) {
            result.setValid(false)
            result.setMessage("API异常:请联系出款商户确认订单.")
            return result
        }

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || "success" != (json.getString("status"))) {
            result.setValid(false)
            result.setMessage(json == null ? "API异常:请联系出款商户确认订单." : json.getString("msg"))
            return result
        }
        result.setValid(true)
        result.setMessage(json.getString("msg"))
        result.setThirdOrderId(json.getString("transaction_id"))
        return result
    }

    public WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        Map<String, String> resMap = args[2] as Map<String, String>
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        log.info("UPAYScript_Transfer_Notify_resMap:{}", JSON.toJSONString(resMap))
        String orderid = resMap.get("orderid")// 商户订单号
        String returncode = resMap.get("returncode")//交易状态
        if (StringUtils.isNotEmpty(returncode) && "00" == (returncode) && StringUtils.isNotEmpty(orderid)) {
            return withdrawQuery(okHttpUtil, merchant, orderid, args[3])
        }
        return null
    }

    public WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2]
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        String keyValue = merchant.getPrivateKey() // 商家密钥

        Map<String, String> paramMap = new HashMap<>()
        paramMap.put("pay_memberid", merchant.getMerchantCode())
        paramMap.put("pay_out_trade_no", orderId)

        String toSign = MD5.toAscii(paramMap)
        toSign = toSign + "&key=" + keyValue
        String pay_md5sign = MD5.md5(toSign).toUpperCase()
        paramMap.put("pay_md5sign", pay_md5sign)

        log.info("UPAYScript_Transfer_Query_Param: {}", JSON.toJSONString(paramMap))
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.WITHDRAW_QUERY.getCode())
                .channelId(PaymentMerchantEnum.UPAY.getCode() + "")
                .channelName(PaymentMerchantEnum.UPAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId(orderId)
                .build()
        String resStr = okHttpUtil.post(merchant.getPayUrl() + "/Payment_Dfpay_query.html", paramMap, requestHeader)
        log.info("UPAYScript_Transfer_Query_resStr: {}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || json.getString("status") == null || json.getString("status") != ("success")) {
            return null
        }

        String refCode = json.getString("refCode")

        WithdrawNotify notify = new WithdrawNotify()
        notify.setAmount(json.getBigDecimal("amount"))
        notify.setMerchantCode(merchant.getMerchantCode())
        notify.setMerchantId(merchant.getMerchantId())
        notify.setMerchantName(merchant.getChannelName())
        notify.setOrderId(orderId)
        notify.setRemark(json.getString("refMsg"))
        notify.setThirdOrderId(json.getString("out_trade_no"))

        if (refCode == ("1")) {
            notify.setStatus(0)
        } else if (refCode == ("2")) {
            notify.setStatus(1)
        } else {
            notify.setStatus(2)
        }

        return notify
    }

    public BigDecimal balanceQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[2], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        String keyValue = merchantAccount.getPrivateKey() // 商家密钥

        Map<String, String> paramMap = new HashMap<>()
        paramMap.put("pay_memberid", merchantAccount.getMerchantCode())
        paramMap.put("pay_noncestr", UUID.randomUUID().toString().replace("-", ""))

        String toSign = MD5.toAscii(paramMap)
        toSign = toSign + "&key=" + keyValue
        String pay_md5sign = MD5.md5(toSign).toUpperCase()
        paramMap.put("pay_md5sign", pay_md5sign)

        log.info("UPAYScript_Query_Balance_Param: {}", JSON.toJSONString(paramMap))
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.PAYMENT_QUERY_BALANCE.getCode())
                .channelId(PaymentMerchantEnum.UPAY.getCode() + "")
                .channelName(PaymentMerchantEnum.UPAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId("")
                .build()
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/Pay_Balance_query.html", paramMap, requestHeader)
        log.info("UPAYScript_Query_Balance_resStr: {}", resStr)

        if (StringUtils.isEmpty(resStr)) {
            return BigDecimal.ZERO
        }
        JSONObject json = JSON.parseObject(resStr)
        if (json == null) {
            return BigDecimal.ZERO
        }
        BigDecimal balance = json.getBigDecimal("balance")
        return balance == null ? BigDecimal.ZERO : balance
    }

}
