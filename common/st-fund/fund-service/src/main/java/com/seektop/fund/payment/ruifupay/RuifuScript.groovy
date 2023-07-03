package com.seektop.fund.payment.ruifupay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.OkHttpUtil
import com.seektop.common.utils.HtmlTemplateUtils
import com.seektop.common.utils.MD5
import com.seektop.constant.FundConstant
import com.seektop.exception.GlobalException
import com.seektop.fund.model.GlPaymentMerchantApp
import com.seektop.fund.model.GlPaymentMerchantaccount
import com.seektop.fund.model.GlWithdraw
import com.seektop.fund.model.GlWithdrawMerchantAccount
import com.seektop.fund.payment.*
import org.apache.commons.lang.StringUtils
import org.apache.http.HttpEntity
import org.apache.http.HttpResponse
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.apache.http.util.EntityUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 睿付支付
 */
public class RuifuScript {

    private static final Logger log = LoggerFactory.getLogger(RuifuScript.class)

    private OkHttpUtil okHttpUtil

    public RechargeNotify result(Object[] args) throws GlobalException {
        return notify(args)
    }

    public void pay(Object[] args) {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantApp merchant = args[1] as GlPaymentMerchantApp
        GlPaymentMerchantaccount account = args[2] as GlPaymentMerchantaccount
        RechargePrepareDO req = args[3] as RechargePrepareDO
        GlRechargeResult result = args[4] as GlRechargeResult
        if (FundConstant.PaymentType.UNION_PAY == merchant.getPaymentId() || FundConstant.PaymentType.ALI_PAY == merchant.getPaymentId()
                || FundConstant.PaymentType.WECHAT_PAY == merchant.getPaymentId() || FundConstant.PaymentType.QUICK_PAY == merchant.getPaymentId()
                || FundConstant.PaymentType.BANKCARD_TRANSFER == merchant.getPaymentId() || FundConstant.PaymentType.ALI_TRANSFER == merchant.getPaymentId()) {
            String payType
            if (FundConstant.PaymentType.UNION_PAY == merchant.getPaymentId()) {
                payType = "100501"
            } else if (FundConstant.PaymentType.ALI_PAY == merchant.getPaymentId()) {
                payType = "100202"
            } else if (FundConstant.PaymentType.QUICK_PAY == merchant.getPaymentId()) {
                payType = "100401"
            } else if (FundConstant.PaymentType.BANKCARD_TRANSFER == merchant.getPaymentId()) {
                payType = "100402"
            } else if (FundConstant.PaymentType.ALI_TRANSFER == merchant.getPaymentId()) {
                payType = "100404"
            } else {
                payType = "100102"
            }
            prepareToScan(merchant, account, req, result, payType)
        }
    }

    private void prepareToScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result, String payType) throws GlobalException {
        Map<String, String> params = new HashMap<>()
        params.put("mer_no", account.getMerchantCode())
        params.put("mer_order_no", req.getOrderId())
        params.put("order_amount", (req.getAmount() * new BigDecimal(100)).setScale(0, RoundingMode.DOWN).toString())
        params.put("busi_code", payType)
        params.put("goods", "Recharge")
        params.put("bg_url", account.getNotifyUrl() + merchant.getId())
        if (FundConstant.PaymentType.BANKCARD_TRANSFER == merchant.getPaymentId() || FundConstant.PaymentType.ALI_TRANSFER == merchant.getPaymentId()) {
            params.put("realName", req.getFromCardUserName())
        }
        String toSign = MD5.toAscii(params)
        toSign += "&key=" + account.getPrivateKey()
        params.put("sign", MD5.md5(toSign).toUpperCase())
        log.info("RuifuScript_prepare_params:{}", JSON.toJSONString(params))
        String resp


        if (FundConstant.PaymentType.QUICK_PAY == merchant.getPaymentId() || FundConstant.PaymentType.BANKCARD_TRANSFER == merchant.getPaymentId()
                || FundConstant.PaymentType.ALI_TRANSFER == merchant.getPaymentId()) {
            resp = HtmlTemplateUtils.getPost(account.getPayUrl() + "/payGateway/payFor", params)
            log.info("RuifuScript_prepare_resp:{}", resp)
            result.setMessage(resp)
        } else {
            String payUrl = account.getPayUrl()
            if (payUrl.contains("https")) {
                payUrl = payUrl.replace("https", "http")
            }
            resp = doPost(payUrl + "/paid/payOrd", JSON.toJSONString(params), "UTF-8")
            log.info("RuifuScript_prepare_resp:{}", resp)
            JSONObject json = JSONObject.parseObject(resp)
            if ("SUCCESS" != (json.getString("status"))) {
                result.setErrorCode(1)
                result.setErrorMsg("订单创建失败，稍后重试")
                return
            }
            Base64.Decoder decoder = Base64.getDecoder()
            byte[] b = decoder.decode(json.getString("code_url"))
            String url = new String(b, StandardCharsets.UTF_8)
            if (FundConstant.PaymentType.ALI_PAY == merchant.getPaymentId()
                    || FundConstant.PaymentType.WECHAT_PAY == merchant.getPaymentId()) {
                result.setRedirectUrl(url)
            } else {
                result.setMessage(HtmlTemplateUtils.getQRCode(url))
            }
        }
    }


    public RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>
        log.info("RuifuScript_notify_resp:{}", JSON.toJSONString(resMap))
        String orderId = resMap.get("mer_order_no")
        if (null != orderId && "" != (orderId)) {
            return this.payQuery(okHttpUtil, account, orderId)
        }
        return null
    }

    public RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String
        Map<String, String> params = new HashMap<>()
        params.put("mer_no", account.getMerchantCode())
        params.put("mer_order_no", orderId)
        params.put("request_no", System.currentTimeMillis() + "")
        params.put("request_time", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")))
        String toSign = MD5.toAscii(params)
        toSign += "&key=" + account.getPrivateKey()
        params.put("sign", MD5.md5(toSign).toUpperCase())
        log.info("RuifuScript_query_params:{}", JSON.toJSONString(params))
        String payUrl = account.getPayUrl()
        if (payUrl.contains("https")) {
            payUrl = payUrl.replace("https", "http")
        }
        String resp = doPost(payUrl + "/paid/queryOrd", JSON.toJSONString(params), "UTF-8")
        log.info("RuifuScript_query_resp:{}", resp)
        JSONObject josn = JSONObject.parseObject(resp)
        if ("SUCCESS" == (josn.getString("query_status")) && "SUCCESS" == (josn.getString("order_status"))) {
            RechargeNotify pay = new RechargeNotify()
            pay.setAmount(josn.getBigDecimal("pay_amount").divide(new BigDecimal(100)).setScale(2, RoundingMode.DOWN))
            pay.setFee(BigDecimal.ZERO)
            pay.setOrderId(orderId)
            pay.setThirdOrderId(josn.getString("order_no"))
            return pay
        }
        return null
    }

    public WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        Map<String, String> params = new HashMap<>()
        params.put("mer_no", merchantAccount.getMerchantCode())
        params.put("mer_order_no", req.getOrderId())
        params.put("acc_type", "1")
        params.put("acc_no", req.getCardNo())
        params.put("acc_name", req.getName())
        params.put("order_amount", (req.getAmount().subtract(req.getFee()) * new BigDecimal(100)).setScale(0, RoundingMode.DOWN).toString())
        params.put("province", "上海市")
        params.put("city", "上海市")
        params.put("bg_url", merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId())
        String toSign = MD5.toAscii(params)
        toSign += "&key=" + merchantAccount.getPrivateKey()
        params.put("sign", MD5.md5(toSign).toUpperCase())
        log.info("RuifuScript_doTransfer_params:{}", JSON.toJSONString(params))
        String resp = doPost(merchantAccount.getPayUrl() + "/remit/transOrd", JSON.toJSONString(params), "UTF-8")
        log.info("RuifuScript_doTransfer_resp:{}", resp)
        WithdrawResult result = new WithdrawResult()
        result.setOrderId(req.getOrderId())
        result.setReqData(JSON.toJSONString(params))
        result.setResData(resp)
        if (StringUtils.isEmpty(resp)) {
            result.setValid(false)
            result.setMessage("API异常:请联系出款商户确认订单.")
            return result
        }


        JSONObject json = JSONObject.parseObject(resp)
        if ("SUCCESS" != (json.getString("status"))) {
            result.setValid(false)
            result.setMessage(json.getString("err_msg"))
            return result
        }
        result.setValid(true)
        result.setMessage(json.getString("err_msg"))
        return result

    }

    public WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        Map<String, String> resMap = args[2] as Map<String, String>
        log.info("RuifuScript_doTransferNotify_resp:{}", JSON.toJSONString(resMap))
        String orderId = resMap.get("mer_order_no")
        if (null != orderId && "" != (orderId)) {
            return withdrawQuery(okHttpUtil, merchant, orderId)
        }
        return null
    }

    public WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2]
        Map<String, String> params = new HashMap<>()
        params.put("mer_no", merchant.getMerchantCode())
        params.put("mer_order_no", orderId)
        params.put("request_no", System.currentTimeMillis() + "")
        params.put("request_time", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")))
        String toSign = MD5.toAscii(params)
        toSign += "&key=" + merchant.getPrivateKey()
        params.put("sign", MD5.md5(toSign).toUpperCase())
        log.info("RuifuScript_doTransferQuery_params:{}", JSON.toJSONString(params))
        String resp = doPost(merchant.getPayUrl() + "/remit/queryOrd", JSON.toJSONString(params), "UTF-8")
        log.info("RuifuScript_doTransferQuery_resp:{}", resp)
        JSONObject json = JSONObject.parseObject(resp)
        if ("SUCCESS" != (json.getString("query_status"))) {
            return null
        }
        WithdrawNotify notify = new WithdrawNotify()
        notify.setAmount(json.getBigDecimal("order_amount").divide(new BigDecimal(100)))
        notify.setMerchantCode(merchant.getMerchantCode())
        notify.setMerchantId(merchant.getMerchantId())
        notify.setMerchantName(merchant.getChannelName())
        notify.setOrderId(json.getString("mer_order_no"))
        notify.setThirdOrderId(json.getString("order_no"))
        if (json.getString("status") == ("SUCCESS")) {
            notify.setStatus(0)
        } else if (json.getString("status") == ("FAIL")) {
            notify.setStatus(1)
        } else {
            notify.setStatus(2)
        }
        return notify
    }

    public BigDecimal balanceQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        Map<String, String> params = new HashMap<>()
        params.put("mer_no", merchantAccount.getMerchantCode())
        params.put("request_no", System.currentTimeMillis() + "")
        params.put("request_time", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")))
        String toSign = MD5.toAscii(params)
        toSign += "&key=" + merchantAccount.getPrivateKey()
        params.put("sign", MD5.md5(toSign).toUpperCase())
        log.info("RuifuScript_queryBalance_params:{}", JSON.toJSONString(params))
        String resp = doPost(merchantAccount.getPayUrl() + "/remit/queryBala", JSON.toJSONString(params), "UTF-8")
        log.info("RuifuScript_queryBalance_resp:{}", resp)
        JSONObject json = JSONObject.parseObject(resp)
        if (!json.getBoolean("status")) {
            return BigDecimal.ZERO
        }
        return json.getBigDecimal("balance").divide(new BigDecimal(100)).setScale(2, RoundingMode.DOWN)
    }

    private static String doPost(String url, String param, String charset) {
        CloseableHttpClient httpClient = HttpClients.createDefault()
        HttpPost httpPost = new HttpPost(url)
        try {
            if (param != null) {
                httpPost.setEntity(new StringEntity(param, charset))
            }
            httpPost.setHeader("Content-type", "application/json")
            HttpResponse response = httpClient.execute(httpPost)

            log.info(response.toString())
            HttpEntity entity = response.getEntity()
            return EntityUtils.toString(entity, charset)
        } catch (Exception e) {
            log.error(e.getMessage())
        } finally {
            try {
                httpClient.close()
            } catch (IOException e) {
            }
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
                || paymentId == FundConstant.PaymentType.ALI_TRANSFER) {
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
}
