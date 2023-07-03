package com.seektop.fund.payment.zbpay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
import com.seektop.common.utils.DateUtils
import com.seektop.common.utils.HtmlTemplateUtils
import com.seektop.common.utils.MD5
import com.seektop.constant.FundConstant
import com.seektop.constant.ProjectConstant
import com.seektop.enumerate.GlActionEnum
import com.seektop.exception.GlobalException
import com.seektop.fund.business.GlPaymentChannelBankBusiness
import com.seektop.fund.model.GlPaymentMerchantApp
import com.seektop.fund.model.GlPaymentMerchantaccount
import com.seektop.fund.payment.*
import com.seektop.fund.payment.groovy.ResourceEnum
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode

class ZbScript {

    private static final Logger log = LoggerFactory.getLogger(ZbScript.class)

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
        this.glPaymentChannelBankBusiness = com.seektop.fund.payment.groovy.BaseScript.getResource(args[5], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        if (FundConstant.PaymentType.ALI_PAY == merchant.getPaymentId()
                || FundConstant.PaymentType.WECHAT_PAY == merchant.getPaymentId()
                || FundConstant.PaymentType.ONLINE_PAY == merchant.getPaymentId()
                || FundConstant.PaymentType.UNIONPAY_SACN == merchant.getPaymentId()
                || FundConstant.PaymentType.JD_PAY == merchant.getPaymentId()) {
            prepareScan(merchant, payment, req, result)
        } else if (FundConstant.PaymentType.QUICK_PAY == merchant.getPaymentId()) {
            prepareToKuaiJie(merchant, payment, req, result)
        }
    }

    private void prepareScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, RechargePrepareDO req, GlRechargeResult result) {
        String merchantid = payment.getMerchantCode()
        String paytype = ""
        String bankCode = glPaymentChannelBankBusiness.getBankCode(req.getBankId(), payment.getChannelId())
        if (merchant.getPaymentId() == FundConstant.PaymentType.ONLINE_PAY) { // 网银支付
            paytype = bankCode
        } else if (merchant.getPaymentId() == FundConstant.PaymentType.ALI_PAY) { // 支付宝扫码
            paytype = PayType.ZHIFUBAO_SCAN.getCode()
            if (req.getClientType() != 0) {
                paytype = PayType.ZHIFUBAO_H5.getCode()
            }
        } else if (merchant.getPaymentId() == FundConstant.PaymentType.UNIONPAY_SACN) { // 银联扫码
            paytype = PayType.UNION_SCAN.getCode()
        } else if (merchant.getPaymentId() == FundConstant.PaymentType.WECHAT_PAY) {
            paytype = PayType.WEIXIN_SCAN.getCode()
            if (req.getClientType() != ProjectConstant.ClientType.PC) {
                paytype = PayType.WEIXIN_H5.getCode()
            }
        } else if (merchant.getPaymentId() == FundConstant.PaymentType.JD_PAY) {
            paytype = PayType.JD_SCAN.getCode()
            if (req.getClientType() != ProjectConstant.ClientType.PC) {
                paytype = PayType.JD_H5.getCode()
            }
        }
        String amount = req.getAmount().setScale(2, RoundingMode.DOWN).toString()
        String orderid = req.getOrderId()
        String notifyurl = payment.getNotifyUrl() + merchant.getId()
        String request_time = DateUtils.format(new Date(), DateUtils.YYYYMMDDHHMMSS)
        String returnurl = payment.getResultUrl() + merchant.getId()
        String desc = "CZ"
        String key = payment.getPrivateKey()

        String signStr = String.format("merchantid=%s&paytype=%s&amount=%s&orderid=%s&notifyurl=%s&request_time=%s&key=%s", merchantid,
                paytype, amount, orderid, notifyurl, request_time, key)
        String sign = MD5.md5(signStr)

        Map<String, String> reqData = new HashMap<String, String>()
        reqData.put("merchantid", merchantid)
        reqData.put("paytype", paytype)
        reqData.put("amount", amount)
        reqData.put("orderid", orderid)
        reqData.put("notifyurl", notifyurl)
        reqData.put("request_time", request_time)
        reqData.put("returnurl", returnurl)
        reqData.put("desc", desc)
        reqData.put("sign", sign)
        String html = HtmlTemplateUtils.getPost(payment.getPayUrl() + "/GateWay/Pay", reqData)
        result.setMessage(html)
        log.info("ZbScript_Prepare_Html:{}", result.getMessage())
    }

    static void prepareToKuaiJie(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, RechargePrepareDO req,
                                 GlRechargeResult result) {
        String merchantid = payment.getMerchantCode()
        String amount = String.valueOf(req.getAmount().intValue())
        String orderid = req.getOrderId()
        String notifyurl = payment.getNotifyUrl() + merchant.getId()
        String request_time = DateUtils.format(new Date(), DateUtils.YYYYMMDDHHMMSS)
        String returnurl = payment.getResultUrl() + merchant.getId()
        String desc = "CZ" // 备注消息
        String key = payment.getPublicKey()

        String signStr = String.format("merchantid=%s&amount=%s&orderid=%s&notifyurl=%s&request_time=%s&key=%s",
                merchantid, amount, orderid, notifyurl, request_time, key)
        String sign = MD5.md5(signStr)

        Map<String, String> reqData = new HashMap()
        reqData.put("merchantid", merchantid)
        reqData.put("amount", amount)
        reqData.put("orderid", orderid)
        reqData.put("notifyurl", notifyurl)
        reqData.put("request_time", request_time)
        reqData.put("returnurl", returnurl)
        reqData.put("desc", desc)
        reqData.put("sign", sign)
        String responseData = HtmlTemplateUtils.getPost(payment.getPayUrl() + "/FastPay/Index", reqData)
        result.setMessage(responseData)
        log.info("ZbScript_Prepare_Html:{}", result.getMessage())
    }

    RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount payment = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>
        this.glPaymentChannelBankBusiness = com.seektop.fund.payment.groovy.BaseScript.getResource(args[4], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        String orderid = resMap.get("orderid")
        String result = resMap.get("result")
        String amount = resMap.get("amount")
        String systemorderid = resMap.get("systemorderid")
        String completetime = resMap.get("completetime")
        String sign = resMap.get("sign")

        String signStr = String.format("orderid=%s&result=%s&amount=%s&systemorderid=%s&completetime=%s&key=%s",
                orderid, result, amount, systemorderid, completetime, payment.getPublicKey())
        String signLocal = MD5.md5(signStr)
        if (signLocal == sign) {
            return payQuery(okHttpUtil, payment, orderid, args[4])
        }
        return null
    }

    RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String
        this.glPaymentChannelBankBusiness = com.seektop.fund.payment.groovy.BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        String merchantid = account.getMerchantCode()
        String orderid = orderId
        String signStr = String.format("orderid=%s&merchantid=%s&key=%s", orderid, merchantid, account.getPrivateKey())
        String sign = MD5.md5(signStr)

        Map<String, String> reqData = new HashMap()
        reqData.put("merchantid", merchantid)
        reqData.put("orderid", orderid)
        reqData.put("sign", sign)
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.RECHARGE_QUERY.getCode())
                .channelId(PaymentMerchantEnum.ZB_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.ZB_PAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId(orderId)
                .build()
        String resStr = okHttpUtil.get(account.getPayUrl() + "/GateWay/Query", reqData, requestHeader)
        log.info("ZbScript_Query_resStr:{}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (null == json || !json.getString("code") == "0") {
            return null
        }

        JSONObject data = json.getJSONObject("obj")
        if (null == data || !data.getString("result") == "1") {
            return null
        }

        RechargeNotify pay = new RechargeNotify()
        pay.setAmount(data.getBigDecimal("amount"))
        pay.setFee(BigDecimal.ZERO)
        pay.setOrderId(orderId)
        pay.setThirdOrderId(data.getString("systemorderid"))
        return pay
    }


    WithdrawResult withdraw(Object[] args) throws GlobalException {
        return null
    }

    WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        return null
    }

    WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        return null
    }

    BigDecimal balanceQuery(Object[] args) throws GlobalException {
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
}
