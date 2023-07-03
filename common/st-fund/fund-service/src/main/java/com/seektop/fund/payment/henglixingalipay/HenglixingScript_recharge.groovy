package com.seektop.fund.payment.henglixingalipay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
import com.seektop.constant.FundConstant
import com.seektop.enumerate.GlActionEnum
import com.seektop.exception.GlobalException
import com.seektop.fund.model.GlPaymentMerchantApp
import com.seektop.fund.model.GlPaymentMerchantaccount
import com.seektop.fund.payment.GlRechargeResult
import com.seektop.fund.payment.PaymentMerchantEnum
import com.seektop.fund.payment.RechargeNotify
import com.seektop.fund.payment.RechargePrepareDO
import com.seektop.fund.payment.henglixingwxpay.AESEncryptUtil
import com.seektop.fund.payment.henglixingwxpay.Md5Util
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode

public class HenglixingScript_recharge {

    private static final Logger log = LoggerFactory.getLogger(HenglixingScript_recharge.class)

    private OkHttpUtil okHttpUtil


    public RechargeNotify result(Object[] args) throws GlobalException {
        return null
    }

    public void pay(Object[] args) {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantApp merchant = args[1] as GlPaymentMerchantApp
        GlPaymentMerchantaccount account = args[2] as GlPaymentMerchantaccount
        RechargePrepareDO req = args[3] as RechargePrepareDO
        GlRechargeResult result = args[4] as GlRechargeResult
        String payWay = null
        if (merchant.getPaymentId() == FundConstant.PaymentType.ALI_TRANSFER
            || merchant.getPaymentId() == FundConstant.PaymentType.BANKCARD_TRANSFER
            || merchant.getPaymentId() == FundConstant.PaymentType.UNION_TRANSFER) {
            payWay = "bankCardCopy"
        }
        prepareToScan(merchant, account, req, result, payWay)
    }

    private void prepareToScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result, String payWay) throws GlobalException {
        Map<String, String> params = new HashMap<>()
        params.put("merchantNo", account.getMerchantCode())
        params.put("merchantOrderNo", req.getOrderId())
        params.put("orderAmount", (req.getAmount()).setScale(0, RoundingMode.DOWN).toString())
        params.put("payWay", payWay)
        params.put("notifyUrl", account.getNotifyUrl() + merchant.getId())
        params.put("returnUrl", account.getResultUrl() + merchant.getId())
        params.put("clientIp", req.getIp())
        params.put("timestamp", System.currentTimeMillis() + "")

        params.put("sign", buildSignByMd5(params,account.getPrivateKey()))

        Map<String, String> header = new HashMap<>()
        header.put("Connection","close")

        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.RECHARGE.getCode())
                .channelId(PaymentMerchantEnum.HENGLIXINGALI_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.HENGLIXINGALI_PAY.getPaymentName())
                .userId(req.getUserId() + "")
                .userName(req.getUsername())
                .tradeId(req.getOrderId())
                .build()
        log.info("HenglixingAliTransferScript_recharge_prepare_params_one = {}", JSONObject.toJSONString(params))
        String payUrl = okHttpUtil.postJSON(account.getPayUrl() + "/api/unifiedCreateOrder", JSON.toJSONString(params), header, requestHeader)
        log.info("HenglixingAliTransferScript_recharge_prepare_stage_resp_one = {}", payUrl)

        JSONObject json = JSONObject.parseObject(payUrl)
        if (json == null) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("网络请求超时，稍后重试")
            return
        }
        if ("1" == (json.getString("status")) && StringUtils.isNotEmpty(json.getString("data"))) {
            Map<String, String> params2 = new HashMap<>()
            params2.put("merchantNo", account.getMerchantCode())
            params2.put("tradeId", json.getString("data"))
            params2.put("timestamp", System.currentTimeMillis() + "")
            params2.put("sign", buildSignByMd5(params2,account.getPrivateKey()))

            log.info("HenglixingAliTransferScript_recharge_prepare_params_two = {}", JSONObject.toJSONString(params2))
            String resStr = okHttpUtil.postJSON(account.getPayUrl() + "/api/getPayLink", JSON.toJSONString(params2), header, requestHeader)
            log.info("HenglixingAliTransferScript_recharge_prepare_resp_two = {}", resStr)
            JSONObject json2 = JSONObject.parseObject(resStr)
            if (json2 == null) {
                result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
                result.setErrorMsg("网络请求超时，稍后重试")
                return
            }
            if (json2 != null && "1" == (json2.getString("status")) && StringUtils.isNotEmpty(json2.getString("data"))) {
                result.setRedirectUrl(json2.getString("data"))
            } else {
                result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
                result.setErrorMsg(json2.getString("msg") == null ? "创建订单失败" : json2.getString("msg"))
                return
            }
        } else {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg(json.getString("msg") == null ? "创建订单失败" : json.getString("msg"))
            return
        }
    }


    public RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>
        log.info("HenglixingAliTransferScript_notify_resp:{}", JSONObject.toJSONString(resMap))
        String data = AESEncryptUtil.decrypt(resMap.get("reqBody"), account.getPublicKey());
        JSONObject dataRespJson = JSONObject.parseObject(data)
        if (dataRespJson == null) {
            return null
        }
        JSONObject dataJSON = dataRespJson.getJSONObject("data");
        if (dataJSON == null) {
            return null
        }
        String orderId = dataJSON.getString("merchantOrderNo")
        String thirdOrderId = dataJSON.getString("platformOrderNo")
        if (StringUtils.isNotEmpty(orderId)) {
            return this.payQuery(okHttpUtil, account, orderId,thirdOrderId)
        }
        return null
    }

    public RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String
        String thirdOrderId = args[3] as String
        Map<String, String> params = new HashMap<>()
        params.put("merchantNo", account.getMerchantCode())
        params.put("merchantOrderNo", orderId)
        params.put("timestamp", System.currentTimeMillis() + "")
        params.put("sign", buildSignByMd5(params,account.getPrivateKey()))

        Map<String, String> header = new HashMap<>()
        header.put("Connection","close")

        log.info("HenglixingAliTransferScript_query_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.RECHARGE_QUERY.getCode())
                .channelId(PaymentMerchantEnum.HENGLIXINGALI_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.HENGLIXINGALI_PAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId(orderId)
                .build()

        String resp = okHttpUtil.postJSON(account.getPayUrl() + "/api/findOrder", JSON.toJSONString(params), header, requestHeader)
        log.info("HenglixingAliTransferScript_query_resp:{}", resp)

        JSONObject json = JSONObject.parseObject(resp)
        if (json == null || json.getJSONObject("data") == null ||  "1" != json.getString("status")) {
            return null
        }
        JSONObject dataJson = json.getJSONObject("data")
        if (dataJson != null && "payed" == dataJson.getString("payStatus") ) {
            RechargeNotify pay = new RechargeNotify()
            pay.setAmount(dataJson.getBigDecimal("orderAmount").setScale(2, RoundingMode.DOWN))
            pay.setFee(BigDecimal.ZERO)
            pay.setOrderId(orderId)
            pay.setThirdOrderId(thirdOrderId)
            return pay
        }
        return null
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


    /**
     * 生成签名
     * @param params：请求参数
     * @param secret：密钥
     *
     * 对所有请求参数和时间戳进行排序  ->  并“参数=参数值”的模式用“&”字符拼接成字符串 + 加上商家密钥 -> MD5生成sign签名
     * @return
     */
    public static String buildSignByMd5(Map<String, String> params , String secret) {

        params.remove("sign");           //去除sign参数

        StringBuilder formatParams = new StringBuilder("");

        //1, 对请求参数key按ASCII排序
        List<String> keys = new ArrayList<String>(params.keySet());
        Collections.sort(keys);

        //2, 将参数拼接成“参数=参数值&”的方式
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            String value = params.get(key);

            //为空的参数不参与签名
            if (value != null && value.length() > 0) {
                if (formatParams.toString().trim().length() > 0) {
                    formatParams.append("&");
                }

                formatParams.append(key + "=" + value);
            }

        }

        //3、请求参数拼接的字符串 + 商家密钥 -> MD5生成sign签名
        return Md5Util.md5_32(formatParams.toString() + secret);

    }
}