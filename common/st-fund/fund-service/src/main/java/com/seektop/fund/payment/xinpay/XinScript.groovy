package com.seektop.fund.payment.xinpay

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
import org.apache.commons.codec.binary.Base64
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode

class XinScript {

    private static final Logger log = LoggerFactory.getLogger(XinScript.class)

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

    private void prepareScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, RechargePrepareDO req, GlRechargeResult result) {
        String keyValue = payment.getPrivateKey() // 商家密钥
        // 商家设置用户购买商品的支付信息
        String version = "V1"
        String mer_no = payment.getMerchantCode() // 商户编号
        String mer_order_no = req.getOrderId() // 商户订单号
        String ccy_no = "CNY" // 交易币种
        String order_amount = String.valueOf(req.getAmount().multiply(BigDecimal.valueOf(100)).intValue()) // 支付金额
        String busi_code = "100401"
        if (FundConstant.PaymentType.UNIONPAY_SACN == merchant.getPaymentId()) {
            busi_code = "100501"
        } else if (FundConstant.PaymentType.ALI_PAY == merchant.getPaymentId()) {
            busi_code = "100201"
        } else if (FundConstant.PaymentType.JD_PAY == merchant.getPaymentId()) {
            busi_code = "100601"
        } else if (FundConstant.PaymentType.QQ_PAY == merchant.getPaymentId()) {
            busi_code = "100801"
        }
        String goods = "CZ"
        String bg_url = payment.getNotifyUrl() + merchant.getId() // 商户接收支付成功数据的地址
        String page_url = payment.getResultUrl() + merchant.getId()

        StringBuilder toSign = new StringBuilder()
        toSign.append("bg_url=").append(bg_url).append("&")
        toSign.append("busi_code=").append(busi_code).append("&")
        toSign.append("ccy_no=").append(ccy_no).append("&")
        toSign.append("goods=").append(goods).append("&")
        toSign.append("mer_no=").append(mer_no).append("&")
        toSign.append("mer_order_no=").append(mer_order_no).append("&")
        toSign.append("order_amount=").append(order_amount).append("&")
        if (FundConstant.PaymentType.UNIONPAY_SACN != merchant.getPaymentId()
                && FundConstant.PaymentType.ALI_PAY != merchant.getPaymentId()
                && FundConstant.PaymentType.JD_PAY != merchant.getPaymentId()
                && FundConstant.PaymentType.QQ_PAY != merchant.getPaymentId()) {
            toSign.append("page_url=").append(page_url).append("&")
        }
        toSign.append("version=").append(version).append("&")
        toSign.append("key=").append(keyValue)
        String sign = MD5.md5(toSign.toString())

        if (FundConstant.PaymentType.UNIONPAY_SACN == merchant.getPaymentId()
                || FundConstant.PaymentType.ALI_PAY == merchant.getPaymentId()
                || FundConstant.PaymentType.JD_PAY == merchant.getPaymentId()
                || FundConstant.PaymentType.QQ_PAY == merchant.getPaymentId()) {
            JSONObject jsonParam = new JSONObject()
            jsonParam.put("version", version)
            jsonParam.put("mer_no", mer_no)
            jsonParam.put("mer_order_no", mer_order_no)
            jsonParam.put("ccy_no", ccy_no)
            jsonParam.put("order_amount", order_amount)
            jsonParam.put("busi_code", busi_code)
            jsonParam.put("goods", goods)
            jsonParam.put("bg_url", bg_url)
            jsonParam.put("sign", sign)
            log.info("XinScript_prepare_jsonParam: {}", jsonParam.toJSONString())
            GlRequestHeader requestHeader = GlRequestHeader.builder()
                    .action(GlActionEnum.RECHARGE.getCode())
                    .channelId(PaymentMerchantEnum.XIN_PAY.getCode() + "")
                    .channelName(PaymentMerchantEnum.XIN_PAY.getPaymentName())
                    .userId(req.getUserId() + "")
                    .userName(req.getUsername())
                    .tradeId(req.getOrderId())
                    .build()

            String resStr = okHttpUtil.postJSON(payment.getPayUrl() + "/pay/orderPay", jsonParam.toString(), requestHeader)
            log.info("XinScript_prepare_resStr: {}", resStr)
            if (StringUtils.isEmpty(resStr)) {
                result.setErrorCode(1)
                result.setErrorMsg("订单创建失败，稍后重试")
                return
            }
            JSONObject json = JSON.parseObject(resStr)

            String code_url = json.getString("code_url")
            if (StringUtils.isEmpty(code_url)) {
                result.setErrorCode(1)
                result.setErrorMsg("订单创建失败，稍后重试")
                return
            }
            result.setMessage(HtmlTemplateUtils.getQRCode(new String(Base64.decodeBase64(code_url))))
        } else {
            Map<String, String> paramMap = new HashMap<>()
            paramMap.put("version", version)
            paramMap.put("mer_no", mer_no)
            paramMap.put("mer_order_no", mer_order_no)
            paramMap.put("ccy_no", ccy_no)
            paramMap.put("order_amount", order_amount)
            paramMap.put("busi_code", busi_code)
            paramMap.put("goods", goods)
            paramMap.put("bg_url", bg_url)
            paramMap.put("page_url", page_url)
            paramMap.put("sign", sign)
            log.info("XinScript_prepare_paramMap: {}", JSON.toJSONString(paramMap))
            result.setMessage(HtmlTemplateUtils.getPost(payment.getPayUrl() + "/forward/orderPay", paramMap))
            log.info("XinScript_prepare_message: {}", result.getMessage())
        }
    }

    RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount payment = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[4], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        log.info("XinScript_recharge_notify_resMap:{}", JSON.toJSONString(resMap))
        String mer_order_no = resMap.get("mer_order_no")// 商户订单号
        if (StringUtils.isEmpty(mer_order_no)) {
            return null
        }
        return payQuery(okHttpUtil, payment, mer_order_no, args[4])
    }

    RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount payment = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        String keyValue = payment.getPrivateKey() // 商家密钥
        // 商家设置用户购买商品的支付信息
        String mer_no = payment.getMerchantCode() // 商户编号
        String mer_order_no = orderId // 商户订单号
        String request_no = String.valueOf(System.currentTimeMillis())
        String request_time = DateUtils.format(new Date(), "yyyyMMddHHmmss")

        StringBuilder toSign = new StringBuilder()
        toSign.append("mer_no=").append(mer_no).append("&")
        toSign.append("mer_order_no=").append(mer_order_no).append("&")
        toSign.append("request_no=").append(request_no).append("&")
        toSign.append("request_time=").append(request_time).append("&")
        toSign.append("key=").append(keyValue)
        String sign = MD5.md5(toSign.toString()).toUpperCase()

        JSONObject paramMap = new JSONObject()
        paramMap.put("mer_no", mer_no)
        paramMap.put("mer_order_no", mer_order_no)
        paramMap.put("request_no", request_no)
        paramMap.put("request_time", request_time)
        paramMap.put("sign", sign)
        log.info("XinScript_query_paramMap: {}", paramMap.toJSONString())
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.RECHARGE_QUERY.getCode())
                .channelId(PaymentMerchantEnum.XIN_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.XIN_PAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId(orderId)
                .build()
        String resStr = okHttpUtil.postJSON(payment.getPayUrl() + "/pay/orderQuery", paramMap.toString(), requestHeader)

        log.info("XinScript_query_resStr: {}", resStr)
        JSONObject json = JSON.parseObject(resStr)
        if (json == null) {
            return null
        }

        String query_status = json.getString("query_status")
        String order_status = json.getString("order_status")

        if ("SUCCESS".equalsIgnoreCase(query_status) && "SUCCESS".equalsIgnoreCase(order_status)) {
            RechargeNotify pay = new RechargeNotify()
            pay.setAmount(json.getBigDecimal("order_amount").divide(BigDecimal.valueOf(100)).setScale(2, RoundingMode.DOWN))
            pay.setFee(BigDecimal.ZERO)
            pay.setOrderId(orderId)
            pay.setThirdOrderId(json.getString("order_no"))
            return pay
        }
        return null
    }


    WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        String mer_no = merchantAccount.getMerchantCode() // 商户编号
        String mer_order_no = req.getOrderId() // 商户订单号
        String acc_type = "1"
        String acc_no = req.getCardNo().trim()
        String acc_name = req.getName().trim()
        String ccy_no = "CNY"
        String order_amount = req.getAmount().subtract(req.getFee()).multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.DOWN).toString()
        String province = "上海市"
        String city = "上海市"
        String asy_url = merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId()

        Map<String, String> paramMap = new HashMap<>()
        paramMap.put("mer_no", mer_no)
        paramMap.put("mer_order_no", mer_order_no)
        paramMap.put("acc_type", acc_type)
        paramMap.put("acc_no", acc_no)
        paramMap.put("acc_name", acc_name)
        paramMap.put("ccy_no", ccy_no)
        paramMap.put("order_amount", order_amount)
        paramMap.put("province", province)
        paramMap.put("city", city)
        paramMap.put("asy_url", asy_url)

        String sign = MD5.md5(MD5.toAscii(paramMap) + "&key=" + merchantAccount.getPrivateKey()).toUpperCase()
        paramMap.put("sign", sign)
        log.info("XinScript_doWithdraw_paramMap: {}", JSON.toJSONString(paramMap))
        WithdrawResult result = new WithdrawResult()
        result.setOrderId(req.getOrderId())
        result.setReqData(JSON.toJSONString(paramMap))
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.WITHDRAW.getCode())
                .channelId(PaymentMerchantEnum.XIN_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.XIN_PAY.getPaymentName())
                .userId(req.getUserId().toString())
                .userName(req.getUsername())
                .tradeId(req.getOrderId())
                .build()
        String resStr = okHttpUtil.postJSON(merchantAccount.getPayUrl() + "/withdraw/singleOrder", JSON.toJSONString(paramMap), requestHeader)
        log.info("XinScript_withdraw_resStr: {}", resStr)

        result.setResData(resStr)
        if (StringUtils.isEmpty(resStr)) {
            result.setValid(false)
            result.setMessage("API异常:请联系出款商户确认订单.")
            return result
        }

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || json.getString("status") == null || "SUCCESS" != json.getString("status")) {
            result.setValid(false)
            result.setMessage(json == null ? "API异常:请联系出款商户确认订单." : json.getString("err_msg"))
            return result
        }

        String status = json.getString("status")

        result.setValid("SUCCESS" == status)
        result.setMessage(json.getString("err_msg"))
        result.setThirdOrderId(json.getString("order_no"))
        return result
    }

    WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        Map<String, String> resMap = args[2] as Map<String, String>
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        log.info("XinScript_doTransfer_notify_resMap:{}", JSON.toJSONString(resMap))
        String orderId = resMap.get("mer_order_no")
        if (StringUtils.isNotEmpty(orderId)) {
            return withdrawQuery(okHttpUtil, merchant, orderId, args[3])
        }
        return null
    }

    WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2]
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        // 商家设置用户购买商品的支付信息
        String request_no = String.valueOf(System.currentTimeMillis())
        String request_time = DateUtils.format(new Date(), "yyyyMMddHHmmss")
        String mer_no = merchant.getMerchantCode() // 商户编号
        String mer_order_no = orderId // 商户订单号

        StringBuilder toSign = new StringBuilder()
        toSign.append("mer_no=").append(mer_no).append("&")
        toSign.append("mer_order_no=").append(mer_order_no).append("&")
        toSign.append("request_no=").append(request_no).append("&")
        toSign.append("request_time=").append(request_time).append("&")
        toSign.append("key=").append(merchant.getPrivateKey())
        log.info("XinScript_TransferQuery_toSign: {}", toSign.toString())
        String sign = MD5.md5(toSign.toString()).toUpperCase()

        JSONObject paramMap = new JSONObject()
        paramMap.put("mer_no", mer_no)
        paramMap.put("mer_order_no", mer_order_no)
        paramMap.put("request_no", request_no)
        paramMap.put("request_time", request_time)
        paramMap.put("sign", sign)
        log.info("XinScript_TransferQuery_param: {}", paramMap.toJSONString())
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.WITHDRAW_QUERY.getCode())
                .channelId(PaymentMerchantEnum.XIN_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.XIN_PAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId(orderId)
                .build()
        String resStr = okHttpUtil.postJSON(merchant.getPayUrl() + "/withdraw/singleQuery", paramMap.toJSONString(), requestHeader)

        log.info("XinScript_TransferQuery_resStr: {}", resStr)
        JSONObject json = JSON.parseObject(resStr)
        if (json == null) {
            return null
        }

        String query_status = json.getString("query_status")
        String status = json.getString("status")

        if ("SUCCESS".equalsIgnoreCase(query_status)) {
            WithdrawNotify notify = new WithdrawNotify()
            notify.setAmount(json.getBigDecimal("order_amount").divide(BigDecimal.valueOf(100)).setScale(2, RoundingMode.DOWN))
            notify.setMerchantCode(merchant.getMerchantCode())
            notify.setMerchantId(merchant.getMerchantId())
            notify.setMerchantName(merchant.getChannelName())
            notify.setOrderId(orderId)
            notify.setRemark(json.getString("query_err_msg"))
            notify.setThirdOrderId(json.getString("order_no"))
            if (status == "SUCCESS") {
                notify.setStatus(0)
            } else if (status == "FAIL") {
                notify.setStatus(1)
            } else {
                notify.setStatus(2)
            }
            notify.setSuccessTime(new Date())
            return notify

        }
        return null
    }


    BigDecimal balanceQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[2], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        String keyValue = merchantAccount.getPrivateKey() // 商家密钥
        // 商家设置用户购买商品的支付信息
        String mer_no = merchantAccount.getMerchantCode() // 商户编号
        String request_no = String.valueOf(System.currentTimeMillis())
        String request_time = DateUtils.format(new Date(), "yyyyMMddHHmmss")

        StringBuilder toSign = new StringBuilder()
        toSign.append("mer_no=").append(mer_no).append("&")
        toSign.append("request_no=").append(request_no).append("&")
        toSign.append("request_time=").append(request_time).append("&")
        toSign.append("key=").append(keyValue)
        log.info("XinScript_balance_query_toSign: {}", toSign.toString())
        String sign = MD5.md5(toSign.toString()).toUpperCase()

        Map<String, String> paramMap = new HashMap<>()
        paramMap.put("mer_no", mer_no)
        paramMap.put("request_no", request_no)
        paramMap.put("request_time", request_time)
        paramMap.put("sign", sign)
        log.info("XinScript_balance_req_param: {}", JSONObject.toJSONString(paramMap))
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.PAYMENT_QUERY_BALANCE.getCode())
                .channelId(PaymentMerchantEnum.XIN_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.XIN_PAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId("")
                .build()
        String resStr = okHttpUtil.postJSON(merchantAccount.getPayUrl() + "/withdraw/balanceQuery", JSON.toJSONString(paramMap), requestHeader)

        log.info("XinScript_balance_resStr: {}", resStr)
        JSONObject json = JSON.parseObject(resStr)
        if (json == null) {
            return null
        }

        BigDecimal balance = json.getBigDecimal("balance")
        return balance == null ? BigDecimal.ZERO : balance.divide(new BigDecimal(100)).setScale(2, RoundingMode.DOWN)
    }


    void cancel(Object[] args) throws GlobalException {

    }
}
