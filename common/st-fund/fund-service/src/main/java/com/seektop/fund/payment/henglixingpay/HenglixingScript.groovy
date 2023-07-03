package com.seektop.fund.payment.henglixingpay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
import com.seektop.constant.FundConstant
import com.seektop.constant.ProjectConstant
import com.seektop.enumerate.GlActionEnum
import com.seektop.exception.GlobalException
import com.seektop.fund.model.GlPaymentMerchantApp
import com.seektop.fund.model.GlPaymentMerchantaccount
import com.seektop.fund.model.GlWithdraw
import com.seektop.fund.model.GlWithdrawMerchantAccount
import com.seektop.fund.payment.*
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode
import java.nio.charset.StandardCharsets

class HenglixingScript {

    private static final Logger log = LoggerFactory.getLogger(HenglixingScript.class)

    private OkHttpUtil okHttpUtil

    RechargeNotify result(Object[] args) throws GlobalException {
        return null
    }

    void pay(Object[] args) {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantApp merchant = args[1] as GlPaymentMerchantApp
        GlPaymentMerchantaccount account = args[2] as GlPaymentMerchantaccount
        RechargePrepareDO req = args[3] as RechargePrepareDO
        GlRechargeResult result = args[4] as GlRechargeResult
        String payType = null
        if (merchant.getPaymentId() == FundConstant.PaymentType.UNIONPAY_SACN) {
            payType = "cloud_qr2"
        } else if (merchant.getPaymentId() == FundConstant.PaymentType.ALI_PAY) {
            if (req.getClientType() == ProjectConstant.ClientType.PC) {
                payType = "ali_gm"
            } else {
                payType = "ali_xj"
            }
        } else if (merchant.getPaymentId() == FundConstant.PaymentType.QUICK_ALI_PAY) {
            payType = "ali_gm"
        }
        prepareToScan(merchant, account, req, result, payType)
    }

    private void prepareToScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result, String payType) throws GlobalException {
        Map<String, String> params = new HashMap<>()
        Map<String, String> data = new TreeMap<>()
        data.put("orderId", req.getOrderId())
        data.put("amount", (req.getAmount() * new BigDecimal(100)).setScale(0, RoundingMode.DOWN).toString())
        data.put("returnUrl", account.getResultUrl() + merchant.getId())
        data.put("merchantCode", account.getMerchantCode())
        data.put("version", "1")
        data.put("notifyUrl", account.getNotifyUrl() + merchant.getId())
        data.put("terminalIp", "0.0.0.0")
        data.put("body", "Recharge")
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.RECHARGE.getCode())
                .channelId(PaymentMerchantEnum.HENGLIXING_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.HENGLIXING_PAY.getPaymentName())
                .userId(req.getUserId() + "")
                .userName(req.getUsername())
                .tradeId(req.getOrderId())
                .build()
        try {
            String dataStr = URLEncoder.encode(toBase64(JSONObject.toJSONString(data)), "utf-8")
            params.put("data", dataStr)
            params.put("sign", DigestUtils.md5Hex((JSONObject.toJSONString(data) + account.getPrivateKey()).getBytes(StandardCharsets.UTF_8)))
            log.info("HenglixingScript_recharge_prepare_params:{}", JSONObject.toJSONString(data))
            String payUrl = okHttpUtil.post(account.getPayUrl() + "/pay/perpare", params, requestHeader)
            log.info("HenglixingScript_recharge_prepare_stage_one_resp:{}", payUrl)
            JSONObject payJson = JSONObject.parseObject(payUrl)
            if ("true" == (payJson.getString("success"))) {
                JSONObject dataRespJson = JSONObject.parseObject(parseBase64(URLDecoder.decode(payJson.getString("data"), "utf-8")))
                Map<String, String> dataPay = new HashMap<>()
                dataPay.put("merchantCode", account.getMerchantCode())
                dataPay.put("tranId", dataRespJson.getString("tranId"))
                dataPay.put("version", req.getOrderId())
                dataPay.put("way", payType)
                String dataPayStr = URLEncoder.encode(toBase64(JSONObject.toJSONString(dataPay)), "utf-8")
                Map<String, String> paramPay = new HashMap<>()
                paramPay.put("sign", DigestUtils.md5Hex((JSONObject.toJSONString(dataPay) + account.getPrivateKey()).getBytes(StandardCharsets.UTF_8)))
                paramPay.put("data", dataPayStr)
                payUrl = okHttpUtil.post(account.getPayUrl() + "/pay/post", paramPay, requestHeader)
                dataRespJson = JSONObject.parseObject(payUrl)
                dataRespJson = JSONObject.parseObject(parseBase64(URLDecoder.decode(dataRespJson.getString("data"), "utf-8")))
                log.info("HenglixingScript_recharge_prepare_stage_two_resp:{}", dataRespJson.toJSONString())
                if (dataRespJson.containsKey("payParams")) {
                    result.setRedirectUrl(dataRespJson.getJSONObject("payParams").getString("codeUrl"))
                } else {
                    result.setErrorCode(1)
                    result.setErrorMsg("创建订单失败，稍后重试")
                    return
                }
            }
        } catch (Exception e) {
            result.setErrorCode(1)
            result.setErrorMsg("创建订单失败，请更换充值方式")
        }
    }


    RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>
        try {
            JSONObject dataRespJson = JSONObject.parseObject(parseBase64(URLDecoder.decode(resMap.get("data"), "utf-8")))
            log.info("HenglixingScript_notify_resp:{}", dataRespJson.toJSONString())
            String orderId = dataRespJson.getString("orderId")
            if (null != orderId && "" != (orderId)) {
                return this.payQuery(okHttpUtil, account, orderId)
            }
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace()
        }
        return null
    }

    RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String
        Map<String, String> params = new HashMap<>()
        Map<String, String> dataPay = new HashMap<>()
        dataPay.put("merchantCode", account.getMerchantCode())
        dataPay.put("orderId", orderId)
        dataPay.put("version", "1")
        try {
            String dataStr = URLEncoder.encode(toBase64(JSONObject.toJSONString(dataPay)), "utf-8")
            params.put("sign", DigestUtils.md5Hex((JSONObject.toJSONString(dataPay) + account.getPrivateKey()).getBytes(StandardCharsets.UTF_8)))
            params.put("data", dataStr)
            log.info("HenglixingScript_query_params:{}", JSON.toJSONString(params))
            GlRequestHeader requestHeader = GlRequestHeader.builder()
                    .action(GlActionEnum.RECHARGE_QUERY.getCode())
                    .channelId(PaymentMerchantEnum.HENGLIXING_PAY.getCode() + "")
                    .channelName(PaymentMerchantEnum.HENGLIXING_PAY.getPaymentName())
                    .userId("")
                    .userName("")
                    .tradeId(orderId)
                    .build()
            String resp = okHttpUtil.post(account.getPayUrl() + "/pay/query", params, requestHeader)
            JSONObject json = JSONObject.parseObject(resp)
            JSONObject dataRespJson = JSONObject.parseObject(parseBase64(URLDecoder.decode(json.getString("data"), "utf-8")))
            log.info("HenglixingScript_query_resp:{}", dataRespJson.toJSONString())
            if ("true" == (json.getString("success")) && "1" == (dataRespJson.getString("status"))) {
                RechargeNotify pay = new RechargeNotify()
                pay.setAmount(dataRespJson.getBigDecimal("amount").divide(new BigDecimal(100)).setScale(2, RoundingMode.DOWN))
                pay.setFee(BigDecimal.ZERO)
                pay.setOrderId(orderId)
                pay.setThirdOrderId("")
                return pay
            }
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace()
        }
        return null
    }

    WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        Map<String, String> params = new HashMap<>()
        Map<String, String> data = new TreeMap<>()
        data.put("orderNo", req.getOrderId())
        data.put("amount", req.getAmount().subtract(req.getFee()).multiply(new BigDecimal(100)).setScale(0, RoundingMode.DOWN).toString())
        data.put("bankName", req.getBankName())
        data.put("cardNumber", req.getCardNo())
        data.put("accountName", req.getName())
        data.put("province", "上海市")
        data.put("city", "上海市")
        data.put("version", "1")
        data.put("merchantCode", merchantAccount.getMerchantCode())
        try {
            log.info("HenglixingScript_doTransfer_params:{}", JSONObject.toJSONString(data))
            String dataStr = URLEncoder.encode(toBase64(JSONObject.toJSONString(data)), "utf-8")
            params.put("data", dataStr)
            params.put("sign", DigestUtils.md5Hex((JSONObject.toJSONString(data) + merchantAccount.getPrivateKey()).getBytes(StandardCharsets.UTF_8)))
            GlRequestHeader requestHeader = GlRequestHeader.builder()
                    .action(GlActionEnum.WITHDRAW.getCode())
                    .channelId(PaymentMerchantEnum.HENGLIXING_PAY.getCode() + "")
                    .channelName(PaymentMerchantEnum.HENGLIXING_PAY.getPaymentName())
                    .userId(req.getUserId() + "")
                    .userName(req.getUsername())
                    .tradeId(req.getOrderId())
                    .build()
            String resp = okHttpUtil.post(merchantAccount.getPayUrl() + "/pay/withdraw", params, requestHeader)
            log.info("Henglixing_Script_doTransfer_resp:{}", resp)
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
            if ("true" != (json.getString("success"))) {
                result.setValid(false)
                result.setMessage(json.getString("msg"))
                return result
            }
            req.setMerchantId(merchantAccount.getMerchantId())
            result.setValid(true)
            result.setMessage(json.getString("msg"))
            return result
        } catch (Exception e) {
            e.printStackTrace()
        }
        return null
    }

    WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        return null
    }

    public WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2]
        Map<String, String> params = new HashMap<>()
        Map<String, String> dataPay = new HashMap<>()
        dataPay.put("merchantCode", merchant.getMerchantCode())
        dataPay.put("orderNo", orderId)
        dataPay.put("version", "1")
        try {
            String dataStr = URLEncoder.encode(toBase64(JSONObject.toJSONString(dataPay)), "utf-8")
            params.put("sign", DigestUtils.md5Hex((JSONObject.toJSONString(dataPay) + merchant.getPrivateKey()).getBytes(StandardCharsets.UTF_8)))
            params.put("data", dataStr)
            log.info("HenglixingScript_doTransferQuery_params:{}", JSON.toJSONString(params))
            GlRequestHeader requestHeader = GlRequestHeader.builder()
                    .action(GlActionEnum.WITHDRAW_QUERY.getCode())
                    .channelId(PaymentMerchantEnum.HENGLIXING_PAY.getCode() + "")
                    .channelName(PaymentMerchantEnum.HENGLIXING_PAY.getPaymentName())
                    .userId("")
                    .userName("")
                    .tradeId(orderId)
                    .build()
            String resp = okHttpUtil.post(merchant.getPayUrl() + "/withdraw/query", params, requestHeader)
            JSONObject json = JSONObject.parseObject(resp)
            JSONObject dataRespJson = JSONObject.parseObject(parseBase64(URLDecoder.decode(json.getString("data"), "utf-8")))
            log.info("HenglixingScript_doTransferQuery_resp:{}", dataRespJson.toJSONString())
            if ("true" != (json.getString("success"))) {
                return null
            }
            WithdrawNotify notify = new WithdrawNotify()
            notify.setAmount(dataRespJson.getBigDecimal("amount").divide(new BigDecimal(100)).setScale(2, RoundingMode.DOWN))
            notify.setMerchantCode(merchant.getMerchantCode())
            notify.setMerchantId(merchant.getMerchantId())
            notify.setMerchantName(merchant.getChannelName())
            notify.setOrderId(dataRespJson.getString("orderNo"))
            notify.setThirdOrderId("")
            if (dataRespJson.getString("status") == ("1")) {
                notify.setStatus(0)
            } else if (dataRespJson.getString("status") == ("2")) {
                notify.setStatus(1)
            } else {
                notify.setStatus(2)
            }
            return notify
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace()
        }
        return null
    }

    BigDecimal balanceQuery(Object[] args) throws GlobalException {
        return new BigDecimal(-1)
    }

    static String toBase64(String str) {
        byte[] b = null
        String s = null
        try {
            b = str.getBytes(StandardCharsets.UTF_8)
        } catch (Exception e) {
            e.printStackTrace()
        }
        if (b != null) {
            s = Base64.getEncoder().encode(b).toString()
        }
        return s
    }

    // 解密
    public static String parseBase64(String s) {
        byte[] b
        String result = null
        if (s != null) {
            Base64.Decoder decoder = Base64.getDecoder()
            try {
                b = decoder.decode(s)
                result = new String(b, StandardCharsets.UTF_8)
            } catch (Exception e) {
                e.printStackTrace()
            }
        }
        return result
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
