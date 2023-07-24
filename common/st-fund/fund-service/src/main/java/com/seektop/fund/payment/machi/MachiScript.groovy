package com.seektop.fund.payment.machi

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
import com.seektop.fund.model.GlWithdraw
import com.seektop.fund.model.GlWithdrawMerchantAccount
import com.seektop.fund.payment.*
import com.seektop.fund.payment.groovy.ResourceEnum
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode

class MachiScript {

    private static final Logger log = LoggerFactory.getLogger(MachiScript.class)

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
        prepareScan(merchant, payment, req, result)
    }

    private void prepareScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result) {
        String subType = null
        if (FundConstant.PaymentType.ONLINE_PAY == merchant.getPaymentId()
                || FundConstant.PaymentType.BANKCARD_TRANSFER == merchant.getPaymentId()) {//网银与银行卡调用相同接口
            prepareToWangyin(merchant, account, req, result)
        } else if (FundConstant.PaymentType.QUICK_PAY == merchant.getPaymentId()) {
            prepareToQuickPay(merchant, account, req, result)
        } else if (FundConstant.PaymentType.WECHAT_PAY == merchant.getPaymentId()) {
            if (req.getClientType() == ProjectConstant.ClientType.PC) {
                subType = "31"
                prepareToScanPay(merchant, account, req, result, subType)
            } else {
                subType = "41"
                prepareToH5(merchant, account, req, result, subType)
            }

        } else if (FundConstant.PaymentType.QQ_PAY == merchant.getPaymentId()) {
            if (req.getClientType() == ProjectConstant.ClientType.PC) {
                subType = "33"
                prepareToScanPay(merchant, account, req, result, subType)
            } else {
                subType = "43"
                prepareToH5(merchant, account, req, result, subType)
            }
        } else if (FundConstant.PaymentType.JD_PAY == merchant.getPaymentId()) {
            if (req.getClientType() == ProjectConstant.ClientType.PC) {
                subType = "35"
                prepareToScanPay(merchant, account, req, result, subType)
            } else {
                subType = "45"
                prepareToH5(merchant, account, req, result, subType)
            }
        } else if (FundConstant.PaymentType.ALI_PAY == merchant.getPaymentId()) {
            if (req.getClientType() == ProjectConstant.ClientType.PC) {
                subType = "32"
                prepareToScanPay(merchant, account, req, result, subType)
            } else {
                subType = "42"
                prepareToH5(merchant, account, req, result, subType)
            }
        } else if (FundConstant.PaymentType.UNION_PAY == merchant.getPaymentId()) {
            subType = "34"
            prepareToScanPay(merchant, account, req, result, subType)

        }
    }



    private void prepareToWangyin(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result) {
        String keyValue = account.getPrivateKey()
        Map<String, String> paramMap = new HashMap<>()
        paramMap.put("txnType", "01")    // 报文类型
        paramMap.put("txnSubType", "21") // 报文子类
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
        paramMap.put("txnAmt", req.getAmount().multiply(BigDecimal.valueOf(100)).setScale(0).toString())   //充值金额 *100 单位为分
        paramMap.put("currencyCode", "156")    //交易币种 默认156
        paramMap.put("cardType", "DT01")    // 卡类型 暂只支持借记卡：DT01
        paramMap.put("bankNum", glPaymentChannelBankBusiness.getBankCode(req.getBankId(), account.getChannelId()))    // 银行代码
        paramMap.put("timeStamp", DateUtils.format(new Date(), DateUtils.YYYYMMDDHHMMSS))
        String toSign = MD5.toAscii(paramMap)
        toSign = toSign + "&k=" + keyValue
        String sign = MD5.md5(toSign).toLowerCase()
        paramMap.put("mac", sign)

        log.info("MachiScript_Wangyin_prepare_paramMap", JSON.toJSONString(paramMap))
        result.setMessage(HtmlTemplateUtils.getPost(account.getPayUrl(), paramMap))
        log.info("MachiScript_Wangyin_prepare_post_html", result.getMessage())
    }

    private void prepareToQuickPay(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result) {
        String keyValue = account.getPrivateKey()
        Map<String, String> paramMap = new HashMap<>()
        paramMap.put("txnType", "01")    // 报文类型
        paramMap.put("txnSubType", "22") // 报文子类
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
        paramMap.put("timeStamp", DateUtils.format(new Date(), DateUtils.YYYYMMDDHHMMSS))
        String toSign = MD5.toAscii(paramMap)
        toSign = toSign + "&k=" + keyValue
        String sign = MD5.md5(toSign).toLowerCase()
        paramMap.put("mac", sign)

        log.info("MachiScript_quickPay_prepare_paramMap: {}", JSON.toJSONString(paramMap))
        result.setMessage(HtmlTemplateUtils.getPost(account.getPayUrl(), paramMap))
        log.info("MachiScript_quickPay_prepare_post_html: {}", result.getMessage())
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
        paramMap.put("timeStamp", DateUtils.format(new Date(), DateUtils.YYYYMMDDHHMMSS))
        String toSign = MD5.toAscii(paramMap)
        toSign = toSign + "&k=" + keyValue
        String sign = MD5.md5(toSign).toLowerCase()
        paramMap.put("mac", sign)

        log.info("MachiScript_scanPay_prepare_paramMap: {}", JSON.toJSONString(paramMap))
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.RECHARGE.getCode())
                .channelId(PaymentMerchantEnum.MACHI_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.MACHI_PAY.getPaymentName())
                .userId(req.getUserId() + "")
                .userName(req.getUsername())
                .tradeId(req.getOrderId())
                .build()
        String resStr = okHttpUtil.post(account.getPayUrl(), paramMap, requestHeader)
        log.info("MachiScript_scanPay_prepare_resStr: {}", resStr)

        JSONObject resObj = JSON.parseObject(resStr)
        if (resObj == null || ("0000" != resObj.getString("respCode")) || StringUtils.isEmpty(resObj.getString("codeImgUrl"))) {
            throw new RuntimeException("创建订单失败")
        }
        result.setRedirectUrl(resObj.getString("codeImgUrl"))
    }

    private void prepareToH5(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result, String subType) {
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
        paramMap.put("clientIp", req.getIp())
        paramMap.put("sceneBizType", "WAP")
        paramMap.put("wapUrl", "https://www.ballbet.com/")
        paramMap.put("wapName", "Ballbet")
        paramMap.put("timeStamp", DateUtils.format(new Date(), DateUtils.YYYYMMDDHHMMSS))
        String toSign = MD5.toAscii(paramMap)
        toSign = toSign + "&k=" + keyValue
        String sign = MD5.md5(toSign).toLowerCase()
        paramMap.put("mac", sign)

        log.info("MachiScript_H5_prepare_paramMap: {}", JSON.toJSONString(paramMap))
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.RECHARGE.getCode())
                .channelId(PaymentMerchantEnum.MACHI_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.MACHI_PAY.getPaymentName())
                .userId(req.getUserId() + "")
                .userName(req.getUsername())
                .tradeId(req.getOrderId())
                .build()
        String resStr = okHttpUtil.post(account.getPayUrl(), paramMap, requestHeader)
        log.info("MachiScript_H5_prepare_resStr: {}", resStr)

        JSONObject resObj = JSON.parseObject(resStr)
        if (resObj == null || ("0000" != resObj.getString("respCode")) || StringUtils.isEmpty(resObj.getString("codePageUrl"))) {
            throw new RuntimeException("订单创建失败")
        }
        result.setRedirectUrl(resObj.getString("codePageUrl"))
    }

    private void prepareToUnionPay(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result) {
        String keyValue = account.getPrivateKey()
        Map<String, String> paramMap = new HashMap<>()
        paramMap.put("txnType", "01")    // 报文类型
        paramMap.put("txnSubType", "23") // 报文子类
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
        paramMap.put("timeStamp", DateUtils.format(new Date(), DateUtils.YYYYMMDDHHMMSS))
        String toSign = MD5.toAscii(paramMap)
        toSign = toSign + "&k=" + keyValue
        String sign = MD5.md5(toSign).toLowerCase()
        paramMap.put("mac", sign)

        log.info("MachiScript_scanPay_prepare_paramMap: {}", JSON.toJSONString(paramMap))
        String resStr = HtmlTemplateUtils.getPost(account.getPayUrl(), paramMap)
        log.info("MachiScript_scanPay_prepare_resStr: {}", resStr)

        result.setMessage(resStr)
    }

    RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount payment = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>
        this.glPaymentChannelBankBusiness = com.seektop.fund.payment.groovy.BaseScript.getResource(args[4], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        log.info("MachiScript_Notify_resMap:{}",  JSON.toJSONString(resMap))
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
        this.glPaymentChannelBankBusiness = com.seektop.fund.payment.groovy.BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
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
        String toSign = MD5.toAscii(paramMap)
        toSign = toSign + "&k=" + keyValue

        String sign = MD5.md5(toSign).toLowerCase()
        paramMap.put("mac", sign)

        log.info("MachiScript_Query_paramMap: {}", JSON.toJSONString(paramMap))
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.RECHARGE_QUERY.getCode())
                .channelId(PaymentMerchantEnum.MACHI_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.MACHI_PAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId(orderParam[0])
                .build()
        String result = okHttpUtil.post(account.getPayUrl(), paramMap, requestHeader)
        log.info("MachiScript_Query_resStr: {}", result)

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
        pay.setAmount(BigDecimal.valueOf(Double.valueOf(json.getString("txnAmt"))).divide(BigDecimal.valueOf(100)))
        pay.setFee(BigDecimal.ZERO)
        pay.setOrderId(orderParam[0])
        pay.setThirdOrderId(json.getString("txnId"))
        return pay
    }



    WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount account = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        this.glPaymentChannelBankBusiness = com.seektop.fund.payment.groovy.BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        String keyValue = account.getPrivateKey()
        Map<String, String> paramMap = new HashMap<>()
        paramMap.put("txnType", "52")
        paramMap.put("txnSubType", "10")
        paramMap.put("secpVer", "icp3-1.1")
        paramMap.put("secpMode", "perm")
        paramMap.put("macKeyId", account.getMerchantCode())
        paramMap.put("orderDate", DateUtils.format(req.getCreateDate(), DateUtils.YYYYMMDD))
        paramMap.put("orderTime", DateUtils.format(req.getCreateDate(), DateUtils.HHMMSS))
        paramMap.put("merId", account.getMerchantCode())
        paramMap.put("orderId", req.getOrderId())
        paramMap.put("txnAmt", req.getAmount().subtract(req.getFee()).multiply(BigDecimal.valueOf(100)).setScale(0).toString())
        paramMap.put("currencyCode", "156")
        paramMap.put("accName", req.getName())
        paramMap.put("accNum", req.getCardNo())
        paramMap.put("bankNum", glPaymentChannelBankBusiness.getBankCode(req.getBankId(), account.getChannelId())) //银行Code
        paramMap.put("bankName", req.getBankName())
        paramMap.put("notifyUrl", account.getNotifyUrl() + account.getMerchantId())
        paramMap.put("timeStamp", DateUtils.format(new Date(), DateUtils.YYYYMMDDHHMMSS))
        String toSign = MD5.toAscii(paramMap)
        toSign = toSign + "&k=" + keyValue
        String sign = MD5.md5(toSign).toLowerCase()
        paramMap.put("mac", sign)

        log.info("MachiScript_Transfer_paramMap: {}", JSON.toJSONString(paramMap))
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.WITHDRAW.getCode())
                .channelId(PaymentMerchantEnum.MACHI_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.MACHI_PAY.getPaymentName())
                .userId(req.getUserId().toString())
                .userName(req.getUsername())
                .tradeId(req.getOrderId())
                .build()
        String resStr = okHttpUtil.post(account.getPayUrl(), paramMap, requestHeader)
        log.info("MachiScript_Transfer_resStr: {}", resStr)

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
        if (json == null || "0000" != json.getString("respCode")) {
            result.setValid(false)
            result.setMessage(json == null ? "API异常:请联系出款商户确认订单." : json.getString("respMsg"))
            return result
        }
        String txnStatus = json.getString("txnStatus")
        result.setValid("01" == txnStatus || "10" == txnStatus)
        result.setMessage(json.getString("respMsg"))
        return result
    }

    WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        Map<String, String> resMap = args[2] as Map<String, String>
        this.glPaymentChannelBankBusiness = com.seektop.fund.payment.groovy.BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        log.info("MachiScript_Transfer_notify_resp:{}", JSON.toJSONString(resMap))
        String merOrderNo = resMap.get("orderId")// 商户订单号
        String orderDate = resMap.get("orderDate")

        if (resMap.get("respCode") == "0000" && StringUtils.isNotEmpty(merOrderNo)) {
            return withdrawQuery(okHttpUtil, merchant, merOrderNo + "-" + orderDate, args[3])
        }
        return null
    }

    WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2]
        this.glPaymentChannelBankBusiness = com.seektop.fund.payment.groovy.BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        String keyValue = merchant.getPrivateKey()
        String[] orderParam = orderId.split("-")
        Map<String, String> paramMap = new HashMap<>()
        paramMap.put("txnType", "00")
        paramMap.put("txnSubType", "50")
        paramMap.put("secpVer", "icp3-1.1")
        paramMap.put("secpMode", "perm")
        paramMap.put("macKeyId", merchant.getMerchantCode())
        paramMap.put("merId", merchant.getMerchantCode())
        paramMap.put("orderId", orderParam[0])
        paramMap.put("orderDate", orderParam[1])
        paramMap.put("timeStamp", DateUtils.format(new Date(), DateUtils.YYYYMMDDHHMMSS))
        String toSign = MD5.toAscii(paramMap)
        toSign = toSign + "&k=" + keyValue
        String sign = MD5.md5(toSign).toLowerCase()
        paramMap.put("mac", sign)

        log.info("MachiScript_TransferQuery_paramMap: {}", JSON.toJSONString(paramMap))
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.WITHDRAW_QUERY.getCode())
                .channelId(PaymentMerchantEnum.MACHI_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.MACHI_PAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId(orderParam[0])
                .build()
        String resStr = okHttpUtil.post(merchant.getPayUrl(), paramMap, requestHeader)
        log.info("MachiScript_TransferQuery_resStr: {}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || json.getString("respCode") == null || json.getString("respCode") != "0000") {
            return null
        }
        String TradeStatus = json.getString("txnStatus")

        WithdrawNotify notify = new WithdrawNotify()
        notify.setAmount(json.getBigDecimal("txnAmt"))
        notify.setMerchantCode(merchant.getMerchantCode())
        notify.setMerchantId(merchant.getMerchantId())
        notify.setMerchantName(merchant.getChannelName())
        notify.setOrderId(orderParam[0])
        notify.setRemark(json.getString("extInfo"))
        if (TradeStatus == "10") {//01---处理中  10---交易成功 20---交易失败
            notify.setStatus(0)
        } else if (TradeStatus == "20") {
            notify.setStatus(1)
        } else {
            notify.setStatus(2)
        }
        return notify
    }


    BigDecimal balanceQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount account = args[1] as GlWithdrawMerchantAccount
        this.glPaymentChannelBankBusiness = com.seektop.fund.payment.groovy.BaseScript.getResource(args[2], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        String keyValue = account.getPrivateKey()
        Map<String, String> paramMap = new HashMap<>()
        paramMap.put("txnType", "00")
        paramMap.put("txnSubType", "90")
        paramMap.put("secpVer", "icp3-1.1")
        paramMap.put("secpMode", "perm")
        paramMap.put("macKeyId", account.getMerchantCode())
        paramMap.put("merId", account.getMerchantCode())
        paramMap.put("accCat", "00")
        paramMap.put("timeStamp", DateUtils.format(new Date(), DateUtils.YYYYMMDDHHMMSS))
        String toSign = MD5.toAscii(paramMap)
        toSign = toSign + "&k=" + keyValue
        String sign = MD5.md5(toSign).toLowerCase()
        paramMap.put("mac", sign)

        log.info("MachiScript_query_balance_param: {}", JSON.toJSONString(paramMap))
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.PAYMENT_QUERY_BALANCE.getCode())
                .channelId(PaymentMerchantEnum.MACHI_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.MACHI_PAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId("")
                .build()
        String resStr = okHttpUtil.post(account.getPayUrl(), paramMap, requestHeader)
        log.info("MachiScript_query_balance_resStr: {}", resStr)
        if (StringUtils.isEmpty(resStr)) {
            return BigDecimal.ZERO
        }
        JSONObject json = JSON.parseObject(resStr)
        if (json == null || json.getString("respCode") == null || "0000" != json.getString("respCode")) {
            return BigDecimal.ZERO
        }
        BigDecimal Balance = json.getBigDecimal("balance").divide(BigDecimal.valueOf(100)).setScale(2, RoundingMode.DOWN)
        return Balance == null ? BigDecimal.ZERO : Balance
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
        return 0
    }
}