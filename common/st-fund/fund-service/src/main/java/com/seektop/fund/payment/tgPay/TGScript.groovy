package com.seektop.fund.payment.tgPay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
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
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.apache.commons.lang.StringUtils
import org.apache.commons.lang3.exception.ExceptionUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.math.RoundingMode
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException
import java.util.concurrent.TimeUnit

/**
 * TGPay-USDT支付
 * https://www.topgate.io/saasAdmin/index.html#/login
 * yuxdsadas12345@163.com
 * fyrrsq2quq8nlxv4
 */

public class TGScript {

    private static final Logger log = LoggerFactory.getLogger(TGScript.class)

    private OkHttpUtil okHttpUtil

    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness

    private OkHttpClient okHttpClient

    /**
     * 封装支付请求参数
     *
     * @param merchant
     * @param payment
     * @param req
     * @param result
     */
    public void pay(Object[] args) {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantApp merchantApp = args[1] as GlPaymentMerchantApp
        GlPaymentMerchantaccount merchantaccount = args[2] as GlPaymentMerchantaccount
        RechargePrepareDO prepareDO = args[3] as RechargePrepareDO
        GlRechargeResult result = args[4] as GlRechargeResult
        this.okHttpClient = BaseScript.getResource(args[5], ResourceEnum.OkHttpClient) as OkHttpClient

        Map<String, String> params = new HashMap<String, Object>()
        params.put("merchantId", merchantaccount.getMerchantCode())
        params.put("timestamp", prepareDO.getCreateDate().getTime().toString())
        params.put("signatureMethod", "HmacSHA256")
        params.put("signatureVersion", "1")
        params.put("jUserId", prepareDO.getUserId().toString())
        params.put("jUserIp", prepareDO.getIp())
        params.put("jOrderId", prepareDO.getOrderId())
        params.put("orderType", "1")
        params.put("payWay", "DigitalPay")
        params.put("amount", prepareDO.getAmount().setScale(2, RoundingMode.DOWN).toString())
        params.put("currency", "CNY")
        params.put("jExtra", "CZ")
        params.put("notifyUrl", merchantaccount.getNotifyUrl() + merchantApp.getId())

        String toSign = MD5.toAscii(params)
        String sign = this.getSign(toSign, merchantaccount.getPrivateKey())
        params.put("signature", sign)
        GlRequestHeader requestHeader = this.getRequestHeard(prepareDO.getUserId().toString(), prepareDO.getUsername(), prepareDO.getOrderId(), GlActionEnum.RECHARGE.getCode(), merchantaccount.getChannelId(), merchantaccount.getChannelName())
        log.info("TGScript_Prepare_params = {}", params)
        String resStr = okHttpUtil.post(merchantaccount.getPayUrl() + "/tgpay_exapi/v2/order/createOrder", params, 10L, requestHeader)
        JSONObject json = JSON.parseObject(resStr)
        log.info("TGScript_Prepare_resStr = {},{}", prepareDO.getOrderId(), resStr)
        if (null == json) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("网络请求超时，稍后重试")
            return
        }
        if (json.getInteger("code") != 0) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg("商户异常")
            return
        }

        JSONObject dataJson = json.getJSONObject("data")
        String orderId = dataJson.getString("orderId")

        Map<String, String> p1 = new HashMap<String, Object>()
        p1.put("invoice", orderId)
        String payCoin = "USDT"

        if (prepareDO.getProtocol() == "ERC20") {
            payCoin = "USDTERC"
        } else if (prepareDO.getProtocol() == "OMNI") {
            payCoin = "USDT"
        }
        p1.put("payCoin", payCoin)
        p1.put("amount", prepareDO.getAmount().setScale(0, RoundingMode.DOWN).toString())

        String payUrl = "http://h5.topgate.io"
        log.info("TGScript_commitRechargeOrder_req= {},{}", prepareDO.getOrderId(), p1)
        String resStr1 = this.get(payUrl + "/paymentapi/commitRechargeOrder", p1, 10L, requestHeader, okHttpUtil)
        JSONObject json1 = JSON.parseObject(resStr1)
        log.info("TGScript_commitRechargeOrder_resp= {},{}", prepareDO.getOrderId(), json1)
        if (null == json1) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("网络请求超时，稍后重试")
            return
        }

        if (json1.getString("code") != "0" || !json1.getBoolean("success")) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg(json1.getString("message"))
            return
        }

        Map<String, String> p2 = new HashMap<String, Object>()
        p2.put("invoice", orderId)
        p2.put("type", "3")
        log.info("TGScript_refresh_req= {},{}", prepareDO.getOrderId(), p2)
        String resStr2 = this.get(payUrl + "/paymentapi/refresh", p2, 10L, requestHeader, okHttpUtil)
        JSONObject json2 = JSON.parseObject(resStr2)
        log.info("TGScript_refresh_resp= {},{}", prepareDO.getOrderId(), json2)
        if (null == json2) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("网络请求超时，稍后重试")
            return
        }
        if (json2.getString("code") != "0" || !json2.getBoolean("success")) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg("商户异常")
            return
        }

        result.setErrorCode(FundConstant.RechargeErrorCode.NORMAL)
        json2 = json2.getJSONObject("data")
        BlockInfo blockInfo = new BlockInfo()
        blockInfo.setDigitalAmount(json2.getBigDecimal("payAmount").setScale(4, RoundingMode.DOWN))
        blockInfo.setProtocol(prepareDO.getProtocol())
        blockInfo.setBlockAddress(json2.getString("payAddress"))
        blockInfo.setRate(json2.getBigDecimal("orderRate"))
        blockInfo.setExpiredDate(json2.getDate("expireTime"))
        result.setThirdOrderId(orderId)
        result.setBlockInfo(blockInfo)
    }

    /**
     * 解析支付结果
     *
     * @param merchant
     * @param payment
     * @param resMap
     * @return
     */
    public RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount payment = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>
        log.info("TGScript_notify_resMap = {}", JSON.toJSONString(resMap))
        String tgOrderId = resMap.get("orderId")
        String orderId = resMap.get("jOrderId")
        if (StringUtils.isNotEmpty(tgOrderId) && StringUtils.isNotEmpty(orderId)) {
            return payQuery(okHttpUtil, payment, tgOrderId, orderId)
        }
        return null
    }


    public RechargeNotify payQuery(Object[] args) throws GlobalException {
        //   订单查询URL：/v2/order/queryOrder
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount payment = args[1] as GlPaymentMerchantaccount
        String tgOrderId = args[2] as String
        String orderId = args[3] as String

        Map<String, String> params = new HashMap<String, Object>()
        params.put("merchantId", payment.getMerchantCode())
        params.put("timestamp", System.currentTimeMillis().toString())
        params.put("signatureMethod", "HmacSHA256")
        params.put("signatureVersion", "1")
        params.put("orderId", tgOrderId)

        String toSign = MD5.toAscii(params)
        String sign = this.getSign(toSign, payment.getPrivateKey())
        params.put("signature", sign)
        GlRequestHeader requestHeader = this.getRequestHeard(null, null, orderId, GlActionEnum.RECHARGE.getCode(), payment.getChannelId(), payment.getChannelName())
        log.info("TGScript_Query_params = {},{}", orderId, JSON.toJSONString(params))
        String resStr = okHttpUtil.post(payment.getPayUrl() + "/tgpay_exapi/v2/order/queryOrder", params, 15L, requestHeader)
        JSONObject json = JSON.parseObject(resStr)
        log.info("TGScript_Query_resStr = {},{}", orderId, resStr)
        if (null == json || json.getInteger("code") != 0) {
            return null
        }
        JSONObject dataJson = json.getJSONObject("data")
        log.info("TGScript_Query_dataJson = {},{}", orderId, dataJson)
        if (dataJson.getInteger("status") != 3) {
            return null
        }
        RechargeNotify pay = new RechargeNotify()
        pay.setAmount(dataJson.getBigDecimal("actualAmount"))//paid_amount实际支付金额
        pay.setFee(BigDecimal.ZERO)
        pay.setOrderId(dataJson.getString("jOrderId"))
        pay.setThirdOrderId(dataJson.getString("orderId"))

        pay.setRsp("{\"code\": 0,\"message\": \"ok\",\"data\": {}}")
        return pay
    }

    /**
     * 解析页面跳转结果
     *
     * @param merchant
     * @param payment
     * @param resMap
     * @return
     */
    public RechargeNotify result(Object[] args) throws GlobalException {
        return notify(args)
    }


    WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        String keyValue = merchantAccount.getPrivateKey() // 商家密钥

        WithdrawResult result = new WithdrawResult()
        result.setOrderId(req.getOrderId())

        Map<String, String> params = new HashMap<String, Object>()
        params.put("merchantId", merchantAccount.getMerchantCode())
        params.put("timestamp", System.currentTimeMillis().toString())
        params.put("signatureMethod", "HmacSHA256")
        params.put("signatureVersion", "1")

        params.put("jUserId", req.getUserId().toString())
        params.put("jUserIp", req.getIp())
        params.put("jOrderId", req.getOrderId())
        params.put("orderType", "2")
        params.put("payWay", "DigitalPay")
        params.put("amount", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString())
        params.put("currency", "CNY")
        params.put("jExtra", "TX")
        params.put("notifyUrl", merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId())
        String withdrawCoin = ""
        if (req.getCardNo() == "ERC20") {
            withdrawCoin = "USDTERC"
        } else if (req.getCardNo() == "OMNI") {
            withdrawCoin = "USDT"
        }
        if (withdrawCoin == "") {
            result.setReqData(JSON.toJSONString(req))
            result.setResData("协议地址异常")
            result.setValid(false)
            result.setMessage("协议地址异常")
            return result
        }

        params.put("withdrawCoin", withdrawCoin)
        params.put("withdrawAddr", req.getAddress())

        String toSign = MD5.toAscii(params)
        String sign = this.getSign(toSign, keyValue)
        params.put("signature", sign)
        GlRequestHeader requestHeader =
                this.getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(), merchantAccount.getChannelId(), merchantAccount.getChannelName())

        log.info("TGScript_Transfer_params = {}", params)
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/tgpay_exapi/v2/order/preOrder", params, 15L, requestHeader)
        JSONObject json = JSON.parseObject(resStr)
        log.info("TGScript_Transfer_resStr = {},{}", req.getOrderId(), resStr)

        result.setReqData(JSON.toJSONString(params))
        result.setResData(resStr)
        if (null == json || json.getInteger("code") != 0) {
            result.setValid(false)
            String message = "API异常:请联系出款商户确认订单."
            if (null != json && StringUtils.isNotEmpty(json.getString("message"))) {
                message = json.getString("message")
            }
            result.setMessage(message)
            return result
        }

        JSONObject dataJson = json.getJSONObject("data")
        String thirdOrderId = dataJson.getString("orderId")
        BigDecimal rate = dataJson.getBigDecimal("rate")
        BigDecimal usdtAmount = dataJson.getBigDecimal("gotAmount")

        //调用订单确认接口
        boolean commit = this.withdrawOrderCommit(okHttpUtil, req, merchantAccount, thirdOrderId)
        if (commit) {//订单确认成功
            result.setValid(true)
            result.setThirdOrderId(thirdOrderId)
            result.setRate(rate)
            result.setUsdtAmount(usdtAmount)
        } else {
            result.setValid(false)
            result.setMessage("出款异常：联系商户核实，避免重复出款")
        }
        return result
    }

    boolean withdrawOrderCommit(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdraw req = args[1] as GlWithdraw
        GlWithdrawMerchantAccount merchantAccount = args[2] as GlWithdrawMerchantAccount
        String orderId = args[3] as String

        String keyValue = merchantAccount.getPrivateKey()

        Map<String, String> params = new HashMap<String, Object>()
        params.put("merchantId", merchantAccount.getMerchantCode())
        params.put("timestamp", System.currentTimeMillis().toString())
        params.put("signatureMethod", "HmacSHA256")
        params.put("signatureVersion", "1")
        params.put("orderId", orderId)
        params.put("orderType", "2")

        String toSign = MD5.toAscii(params)
        String sign = this.getSign(toSign, keyValue)
        params.put("signature", sign)
        GlRequestHeader requestHeader =
                this.getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(),
                        merchantAccount.getChannelId(), merchantAccount.getChannelName())

        log.info("TGScript_Transfer_commitOrder_params = {},{}", req.getOrderId(), params)
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/tgpay_exapi/v2/order/commitOrder", params, 15L, requestHeader)
        JSONObject json = JSON.parseObject(resStr)
        log.info("TGScript_Transfer_commitOrder_resStr = {},{}", req.getOrderId(), resStr)
        if (null != json && json.getInteger("code") == 0) {
            return true
        }
        return false
    }


    WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        Map<String, String> resMap = args[2] as Map<String, String>
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        log.info("TGScript_WithdrawNotify_resMap = {}", JSON.toJSONString(resMap))
        String tgOrderId = resMap.get("orderId")
        String orderId = resMap.get("jOrderId")

        if (StringUtils.isNotEmpty(tgOrderId) && StringUtils.isNotEmpty(orderId)) {
            return withdrawQuery(okHttpUtil, merchant, tgOrderId, orderId, args[3])
        } else {
            return null
        }
    }


    WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {

        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        String tgOrderId = args[2] as String
        String orderId = args[3] as String

        Map<String, String> params = new HashMap<String, Object>()
        params.put("merchantId", merchantAccount.getMerchantCode())
        params.put("timestamp", System.currentTimeMillis().toString())
        params.put("signatureMethod", "HmacSHA256")
        params.put("signatureVersion", "1")
        params.put("orderId", tgOrderId)

        String toSign = MD5.toAscii(params)
        String sign = this.getSign(toSign, merchantAccount.getPrivateKey())
        params.put("signature", sign)
        GlRequestHeader requestHeader =
                this.getRequestHeard(null, null, orderId, GlActionEnum.WITHDRAW_QUERY.getCode(), merchantAccount.getChannelId(), merchantAccount.getChannelName())

        log.info("TGScript_WithdrawQuery_params = {},{}", orderId, JSON.toJSONString(params))
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/tgpay_exapi/v2/order/queryOrder", params, 15L, requestHeader)
        JSONObject json = JSON.parseObject(resStr)
        log.info("TGScript_WithdrawQuery_resStr = {},{}", orderId, resStr)
        if (null == json || json.getInteger("code") != 0) {
            return null
        }
        JSONObject dataJson = json.getJSONObject("data")

        WithdrawNotify notify = new WithdrawNotify()
        notify.setMerchantCode(merchantAccount.getMerchantCode())
        notify.setMerchantId(merchantAccount.getMerchantId())
        notify.setMerchantName(merchantAccount.getChannelName())
        notify.setOrderId(orderId)
        notify.setThirdOrderId(tgOrderId)
        if (dataJson.getInteger("status") == 3) {
            notify.setStatus(0)
            notify.setRsp("{\"code\": 0,\"message\": \"ok\",\"data\": {}}")
        } else if (dataJson.getInteger("status") == 4) {
            notify.setStatus(1)
            notify.setRsp("{\"code\": 0,\"message\": \"ok\",\"data\": {}}")
        } else {
            notify.setStatus(2)
        }
        return notify
    }


    BigDecimal balanceQuery(Object[] args) throws GlobalException {
        return BigDecimal.valueOf(-1)
    }


    private GlRequestHeader getRequestHeard(String userId, String userName, String orderId, String code, Integer channelId, String channelName) {
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(code)
                .channelId(channelId.toString())
                .channelName(channelName)
                .userId(userId)
                .userName(userName)
                .tradeId(orderId)
                .build()
        return requestHeader
    }

    static String getSign(String value, String accessToken)
            throws NoSuchAlgorithmException, InvalidKeyException, UnsupportedEncodingException {
        Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
        SecretKeySpec secret_key = new SecretKeySpec(accessToken.getBytes(), "HmacSHA256")
        sha256_HMAC.init(secret_key);

        byte[] bytes = sha256_HMAC.doFinal(value.getBytes());

        return byteArrayToHexString(bytes)
    }

    static String byteArrayToHexString(byte[] b) {
        StringBuilder hs = new StringBuilder()
        String stmp
        for (int n = 0; b != null && n < b.length; n++) {
            stmp = Integer.toHexString(b[n] & 0XFF)
            if (stmp.length() == 1)
                hs.append('0')
            hs.append(stmp)
        }
        return hs.toString().toUpperCase()
    }

    /**
     * 是否为内部渠道
     *
     * @param args
     * @return
     */
    public boolean innerpay(Object[] args) {
        return true
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
     * get  (供其他方法重载)
     *
     * @param url
     * @param paramsMap
     * @param timeOutSeconds
     * @param requestHeader
     * @return
     */
    private String get(String url, Map<String, String> paramsMap, Long timeOutSeconds, GlRequestHeader requestHeader, OkHttpUtil okHttpUtil) {
        Request request = null;
        //判断参数是否存在
        if (paramsMap != null && !paramsMap.isEmpty()) {
            url = okHttpUtil.getQueryString(url, paramsMap).toString();
        }
        //判断是否需要请求头
        request = new Request.Builder().url(url).headers(okHttpUtil.getHeadersByDto(requestHeader)).build();

        return execNewCall(request, timeOutSeconds);
    }

    /**
     * 自定义超时时间
     *
     * @param request
     * @return
     */
    private String execNewCall(Request request, long connectTimeOutSeconds) {
        Response response = null;
        try {
            OkHttpClient client = okHttpClient.newBuilder()
                    .connectTimeout(connectTimeOutSeconds, TimeUnit.SECONDS)
                    .writeTimeout(connectTimeOutSeconds, TimeUnit.SECONDS)
                    .retryOnConnectionFailure(true)
                    .readTimeout(connectTimeOutSeconds, TimeUnit.SECONDS).build();

            response = client.newCall(request).execute();
            if (response.isSuccessful()) {
                return response.body().string();
            }
        } catch (Exception e) {
            log.error("okhttp3 put error >> ex = {}", ExceptionUtils.getStackTrace(e));
        } finally {
            if (response != null) {
                response.close();
            }
        }
        return "";
    }
}