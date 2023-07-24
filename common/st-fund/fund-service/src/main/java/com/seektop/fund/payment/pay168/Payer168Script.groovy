package com.seektop.fund.payment.pay168

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
import com.seektop.fund.payment.groovy.ResourceEnum
import org.apache.commons.lang.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode

class Payer168Script {

    private static final Logger log = LoggerFactory.getLogger(Payer168Script.class)

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
        prepareScan(merchant, payment, req, result)
    }

    private void prepareScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, RechargePrepareDO req, GlRechargeResult result) {
        String keyValue = payment.getPrivateKey() // 商家密钥

        String pay_memberid = payment.getMerchantCode()
        String pay_orderid = req.getOrderId()
        String pay_applydate = DateUtils.format(req.getCreateDate(), DateUtils.YYYY_MM_DD_HH_MM_SS)
        String pay_notifyurl = payment.getNotifyUrl() + merchant.getId()
        String pay_amount = req.getAmount().setScale(2, RoundingMode.DOWN).toString()
        String pay_productname = "CZ"//商品名称  必填不参与签名

        JSONObject payTypeJSON = JSON.parseObject(payment.getPublicKey())
        if (payTypeJSON == null) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("商户公钥配置错误")
            return
        }
        String pay_bankcode = payTypeJSON.getString(merchant.getPaymentId() + "")

        Map<String, String> paramMap = new HashMap<>()
        paramMap.put("pay_memberid", pay_memberid)
        paramMap.put("pay_orderid", pay_orderid)
        paramMap.put("pay_applydate", pay_applydate)
        paramMap.put("pay_bankcode", pay_bankcode)
        paramMap.put("pay_notifyurl", pay_notifyurl)
        paramMap.put("pay_amount", pay_amount)
        String toSign = MD5.toAscii(paramMap)
        toSign = toSign + "&key=" + keyValue

        String pay_md5sign = MD5.md5(toSign).toUpperCase()

        paramMap.put("pay_md5sign", pay_md5sign)
        paramMap.put("pay_productname", pay_productname)

        log.info("Payer168Script_Prepare_Params: {}", JSON.toJSONString(paramMap))
        result.setMessage(HtmlTemplateUtils.getPost(payment.getPayUrl() + "/Pay_Index.html", paramMap));
    }

    RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount payment = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>
        log.info("Payer168Script_Notify_resMap:{}", JSON.toJSONString(resMap))
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

        String toSign = MD5.toAscii(paramMap)
        toSign = toSign + "&key=" + keyValue
        log.info("Payer168Script_Query_toSign: {}", toSign)
        String pay_md5sign = MD5.md5(toSign).toUpperCase()
        paramMap.put("pay_md5sign", pay_md5sign)
        String queryUrl = payment.getPayUrl() + "/Pay_Trade_query.html"
        log.info("Payer168Script_Query_Params: {}", JSON.toJSONString(paramMap))

        if (queryUrl.contains("https")) {
            queryUrl = queryUrl.replace("https", "http")
        }
        GlRequestHeader requestHeader = getRequestHeard(null, null, orderId, GlActionEnum.RECHARGE_QUERY.getCode())
        String result = okHttpUtil.post(queryUrl, paramMap, requestHeader)
        log.info("Payer168Script_Query_resStr: {}", result)

        JSONObject json = JSON.parseObject(result)
        if (json == null) {
            return null
        }
        String returnCode = json.getString("returncode")
        String tradeState = json.getString("trade_state")
        if (!returnCode.equals("00") || !tradeState.equals("SUCCESS")) {
            return null
        }
        RechargeNotify pay = new RechargeNotify()
        pay.setAmount(json.getBigDecimal("amount"))//paid_amount实际支付金额
        pay.setFee(BigDecimal.ZERO)
        pay.setOrderId(orderId)
        pay.setThirdOrderId(json.getString("transaction_id"))
        log.info("Payer168Script_Query_pay:{}", JSON.toJSONString(pay))
        return pay
    }


    WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        this.glPaymentChannelBankBusiness = com.seektop.fund.payment.groovy.BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        String keyValue = merchantAccount.getPrivateKey() // 商家密钥

        String pay_memberid = merchantAccount.getMerchantCode()//商户号
        String pay_out_trade_no = req.getOrderId()
        String pay_money = req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString()
        String pay_bankname = glPaymentChannelBankBusiness.getBankName(req.getBankId(), merchantAccount.getChannelId())
        String pay_subbranch = "支行"
        String pay_accountname = req.getName()
        String pay_cardnumber = req.getCardNo()
        String pay_province = "上海市" //省
        String pay_city = "上海市"//城市
        String pay_notifyurl = merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId()
        Map<String, String> paramMap = new HashMap<>()
        paramMap.put("pay_memberid", pay_memberid)
        paramMap.put("pay_out_trade_no", pay_out_trade_no)
        paramMap.put("pay_money", pay_money)
        paramMap.put("pay_bankname", pay_bankname)
        paramMap.put("pay_subbranch", pay_subbranch)
        paramMap.put("pay_accountname", pay_accountname)
        paramMap.put("pay_cardnumber", pay_cardnumber)
        paramMap.put("pay_province", pay_province)
        paramMap.put("pay_city", pay_city)
        paramMap.put("pay_notifyurl", pay_notifyurl)
        String toSign = MD5.toAscii(paramMap)
        toSign = toSign + "&key=" + keyValue
        String pay_md5sign = MD5.md5(toSign).toUpperCase()
        paramMap.put("pay_md5sign", pay_md5sign)
        log.info("Payer168Script_Transfer_Params: {}", JSON.toJSONString(paramMap))
        GlRequestHeader requestHeader = getRequestHeard(req.getUserId(), req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode())
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/Payment_Dfpay_add.html", paramMap, requestHeader)
        log.info("Payer168Script_Transfer_resStr: {}", resStr)

        WithdrawResult result = new WithdrawResult()
        result.setOrderId(req.getOrderId())
        result.setReqData(JSON.toJSONString(paramMap))
        result.setResData(resStr)

        if (org.apache.commons.lang.StringUtils.isEmpty(resStr)) {
            result.setValid(false)
            result.setMessage("API异常:请联系出款商户确认订单.")
            return result
        }

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || "success" != json.getString("status")) {
            result.setValid(false)
            result.setMessage(json == null ? "API异常:请联系出款商户确认订单." : json.getString("msg"))
            return result
        }
        result.setValid(true)
        result.setMessage(json.getString("msg"))
        result.setThirdOrderId(json.getString("transaction_id"))
        return result
    }

    WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        Map<String, String> resMap = args[2] as Map<String, String>
        log.info("Payer168Script_Transfer_Notify_resMap:{}", JSON.toJSONString(resMap))
        String orderid = resMap.get("orderid")// 商户订单号
        if (StringUtils.isNotEmpty(orderid)) {
            return withdrawQuery(okHttpUtil, merchant, orderid, args[3])
        }
        return null
    }

    WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2]
        String keyValue = merchant.getPrivateKey() // 商家密钥
        Map<String, String> paramMap = new HashMap<>()
        paramMap.put("pay_memberid", merchant.getMerchantCode())
        paramMap.put("pay_out_trade_no", orderId)

        String signInfo = MD5.toAscii(paramMap)
        signInfo = signInfo + "&key=" + keyValue
        log.info("Payer168Script_Transfer_Query_toSign: {}", signInfo)
        String pay_md5sign = MD5.md5(signInfo).toUpperCase()
        paramMap.put("pay_md5sign", pay_md5sign)

        log.info("Payer168Script_Transfer_Query_Params: {}", JSON.toJSONString(paramMap))
        GlRequestHeader requestHeader = getRequestHeard(null, null, orderId, GlActionEnum.WITHDRAW_QUERY.getCode())
        String resStr = okHttpUtil.post(merchant.getPayUrl() + "/Payment_Dfpay_query.html", paramMap, requestHeader)
        log.info("Payer168Script_Transfer_Query_resStr: {}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || json.getString("status") == null || json.getString("status") != "success") {
            return null
        }

        Integer refCode = json.getInteger("refCode")
        WithdrawNotify notify = new WithdrawNotify()
        notify.setAmount(json.getBigDecimal("amount"))
        notify.setMerchantCode(merchant.getMerchantCode())
        notify.setMerchantId(merchant.getMerchantId())
        notify.setMerchantName(merchant.getChannelName())
        notify.setOrderId(orderId)
        notify.setRemark(json.getString("refMsg"))
        if (refCode == 1) {
            notify.setStatus(0)
        } else if (refCode == 2) {
            notify.setStatus(1)
        } else {
            notify.setStatus(2)
        }
        notify.setThirdOrderId(json.getString("out_trade_no"))
        return notify
    }


    BigDecimal balanceQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        String keyValue = merchantAccount.getPrivateKey() // 商家密钥
        String pay_noncestr = UUID.randomUUID().toString().replace("-", "")
        String pay_memberid = merchantAccount.getMerchantCode()

        Map<String, String> paramMap = new HashMap<>()
        paramMap.put("pay_memberid", pay_memberid)
        paramMap.put("pay_noncestr", pay_noncestr)

        String toSign = MD5.toAscii(paramMap)
        toSign = toSign + "&key=" + keyValue
        String pay_md5sign = MD5.md5(toSign.toString()).toUpperCase()
        paramMap.put("pay_md5sign", pay_md5sign)

        log.info("Payer168Script_Query_Balance_Params: {}", JSON.toJSONString(paramMap))
        GlRequestHeader requestHeader = getRequestHeard(null, null, null, GlActionEnum.PAYMENT_QUERY_BALANCE.getCode())
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/Pay_Balance_query.html", paramMap, requestHeader)
        log.info("Payer168Script_Query_Balance_resStr: {}", resStr)

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

    private static GlRequestHeader getRequestHeard(String userId, String userName, String orderId, String code) {
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(code)
                .channelId(PaymentMerchantEnum.P168_PAY.getCode().toString())
                .channelName(PaymentMerchantEnum.P168_PAY.getPaymentName())
                .userId(userId)
                .userName(userName)
                .tradeId(orderId)
                .build()
        return requestHeader
    }
}