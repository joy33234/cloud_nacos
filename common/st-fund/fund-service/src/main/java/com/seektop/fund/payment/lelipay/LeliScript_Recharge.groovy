package com.seektop.fund.payment.lelipay

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

class LeliScript_Recharge {

    private static final Logger log = LoggerFactory.getLogger(LeliScript_Recharge.class)

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
        prepareScan(merchant, payment, req, result)
    }

    private void prepareScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result) {
        String subType = null
        if (FundConstant.PaymentType.ALI_TRANSFER == merchant.getPaymentId()) {
            if (req.getClientType() == ProjectConstant.ClientType.PC) {
                subType = "32"
            } else {
                subType = "42"
            }
        } else if (FundConstant.PaymentType.BANKCARD_TRANSFER == merchant.getPaymentId()){
            subType = "21"
        } else if (FundConstant.PaymentType.UNION_TRANSFER == merchant.getPaymentId()){
            subType = "23"
        } else if (FundConstant.PaymentType.WECHAT_PAY == merchant.getPaymentId()){
            if (req.getClientType() == ProjectConstant.ClientType.PC) {
                subType = "31"
            } else {
                subType = "41"
            }
        } else if (FundConstant.PaymentType.ALI_PAY == merchant.getPaymentId()){
            if (req.getClientType() == ProjectConstant.ClientType.PC) {
                subType = "32"
            } else {
                subType = "42"
            }
        }
        if (StringUtils.isEmpty(subType)){
            result.setErrorMsg("支付方式不支持，请联系技术人员");
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            return
        }
        prepareToScanPay(merchant, account, req, result, subType)
    }



    private void prepareToScanPay(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result, String subType) {
        String keyValue = account.getPrivateKey()
        Map<String, String> paramMap = new HashMap<>()
        paramMap.put("txnType", "01")    // 报文类型
        paramMap.put("txnSubType", subType) // 报文子类
        paramMap.put("secpVer", "icp3-1.1")    // 安全协议版本
        paramMap.put("secpMode", "perm")   // 安全协议类型
        paramMap.put("macKeyId", account.getMerchantCode())   // 秘钥识别,与商户号相同
        paramMap.put("orderDate", DateUtils.format(req.getCreateDate(), DateUtils.YYYYMMDD))  // 下单日期
        paramMap.put("orderTime", DateUtils.format(req.getCreateDate(), DateUtils.HHMMSS))  // 下单时间
        paramMap.put("merId", account.getMerchantCode())  // 商户代号
        paramMap.put("orderId", req.getOrderId())  // 商户订单号
        paramMap.put("pageReturnUrl", account.getResultUrl() + merchant.getId())   //交易结果通知地址
        paramMap.put("notifyUrl", account.getNotifyUrl() + merchant.getId())   // 交易结果后台通知地址
        paramMap.put("productTitle", "CZ") //商品名称
        paramMap.put("txnAmt", req.getAmount().multiply(BigDecimal.valueOf(100)).setScale(0).toString())   //充值金额 单位为分
        paramMap.put("currencyCode", "156")    //交易币种 默认156
        if ((FundConstant.PaymentType.ALI_PAY == merchant.getPaymentId() || FundConstant.PaymentType.ALI_TRANSFER == merchant.getPaymentId())
                && req.getClientType() != ProjectConstant.ClientType.PC) {
            paramMap.put("clientIp", req.getIp())
        }
        paramMap.put("timeStamp", DateUtils.format(new Date(), DateUtils.YYYYMMDDHHMMSS))
        if (FundConstant.PaymentType.BANKCARD_TRANSFER == merchant.getPaymentId()
            || FundConstant.PaymentType.ALI_TRANSFER == merchant.getPaymentId()
            || FundConstant.PaymentType.UNION_TRANSFER == merchant.getPaymentId()) {
            paramMap.put("accName", req.getFromCardUserName())

        }
        if (FundConstant.PaymentType.BANKCARD_TRANSFER == merchant.getPaymentId() ) {
            paramMap.put("bankNum", "03080000")//glPaymentChannelBankBusiness.getBankCode(req.getBankId(), account.getChannelId())
        }

        String toSign = MD5.toAscii(paramMap) + "&k=" + keyValue
        String sign = MD5.md5(toSign).toLowerCase()
        paramMap.put("mac", sign)

        log.info("LeliScript_scanPay_prepare_paramMap: {}", JSON.toJSONString(paramMap))
        GlRequestHeader requestHeader =
                getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode(), account.getChannelId(), account.getChannelName())
        String resStr = okHttpUtil.post(account.getPayUrl() + "/lelipay-gateway-onl/txn", paramMap, requestHeader)
        log.info("LeliScript_scanPay_prepare_resStr: {}", resStr)

        if (StringUtils.isEmpty(resStr)) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("网络请求超时，稍后重试")
            return
        }

        if (FundConstant.PaymentType.BANKCARD_TRANSFER == merchant.getPaymentId()) {
            result.setMessage(resStr)
            return
        }

        JSONObject resObj = JSON.parseObject(resStr)
        if (resObj == null) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("网络请求超时，稍后重试")
            return
        }
        if ("0000" != resObj.getString("respCode") || StringUtils.isEmpty(resObj.getString("codeImgUrl"))) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg("商户异常")
            return
        }
        result.setRedirectUrl(resObj.getString("codeImgUrl"))
        result.setThirdOrderId(resObj.getString("txnId"))
    }


    RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount payment = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[4], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        log.info("LeliScript_Notify_resMap:{}", JSON.toJSONString(resMap))
        String orderId = resMap.get("orderId")// 商户订单号
        String orderDate = resMap.get("orderDate")
        String respCode = resMap.get("respCode")
        if ("0000" == respCode && StringUtils.isNotEmpty(orderId)) {
            return payQuery(okHttpUtil, payment, orderId + "-" + orderDate, args[4])
        }
        return null
    }

    RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        String keyValue = account.getPrivateKey()
        String[] orderParam = orderId.split("-")
        Map<String, String> paramMap = new HashMap<>()
        paramMap.put("txnType", "00")    // 报文类型
        paramMap.put("txnSubType", "10") // 报文子类
        paramMap.put("secpVer", "icp3-1.1")    // 安全协议版本
        paramMap.put("secpMode", "perm")   // 安全协议类型
        paramMap.put("macKeyId", account.getMerchantCode())   // 秘钥识别,与商户号相同
        paramMap.put("merId", account.getMerchantCode())  // 商户代号
        paramMap.put("orderId", orderParam[0])  // 商户订单号
        paramMap.put("orderDate", orderParam[1])    //下单日期
        paramMap.put("timeStamp", DateUtils.format(new Date(), DateUtils.YYYYMMDDHHMMSS))
        String toSign = MD5.toAscii(paramMap) + "&k=" + keyValue

        String sign = MD5.md5(toSign).toLowerCase()
        paramMap.put("mac", sign)

        log.info("LeliScript_Query_paramMap: {}", JSON.toJSONString(paramMap))
        GlRequestHeader requestHeader = getRequestHeard(null, null, orderId, GlActionEnum.RECHARGE_QUERY.getCode(), account.getChannelId(), account.getChannelName())
        String result = okHttpUtil.post(account.getPayUrl() + "/lelipay-gateway-onl/txn", paramMap, requestHeader)
        log.info("LeliScript_Query_resStr: {}", result)

        JSONObject json = JSON.parseObject(result)
        if (json == null || json.getString("respCode") == null) {
            return null
        }
        String respCode = json.getString("respCode")
        String txnStatus = json.getString("txnStatus")
        if (("0000" != respCode) || ("10" != txnStatus)) {
            return null
        }
        RechargeNotify pay = new RechargeNotify()
        pay.setAmount((json.getBigDecimal("txnAmt")).divide(BigDecimal.valueOf(100)).setScale(2, RoundingMode.DOWN))
        pay.setFee(BigDecimal.ZERO)
        pay.setOrderId(orderParam[0])
        pay.setThirdOrderId(json.getString("txnId"))
        return pay
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