package com.seektop.fund.payment.fengWoPay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
import com.seektop.common.utils.MD5
import com.seektop.constant.FundConstant
import com.seektop.enumerate.GlActionEnum
import com.seektop.exception.GlobalException
import com.seektop.fund.model.GlPaymentMerchantApp
import com.seektop.fund.model.GlPaymentMerchantaccount
import com.seektop.fund.payment.GlRechargeResult
import com.seektop.fund.payment.RechargeNotify
import com.seektop.fund.payment.RechargePrepareDO
import org.apache.commons.codec.digest.HmacUtils
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.util.ObjectUtils

import java.math.RoundingMode
/**
 * @desc 蜂窝支付
 * @date 2021-12-12
 * @auth Otto
 */
public class FengWoScript_recharge {

    private static final Logger log = LoggerFactory.getLogger(FengWoScript_recharge.class)

    private OkHttpUtil okHttpUtil
    private  final String PAY_URL =  "/api/v1/deposits"
    private  final String QUERY_URL =  "/api/v1/deposits/query"

    public RechargeNotify result(Object[] args) throws GlobalException {
        return null
    }

    public void pay(Object[] args) {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantApp merchant = args[1] as GlPaymentMerchantApp
        GlPaymentMerchantaccount account = args[2] as GlPaymentMerchantaccount
        RechargePrepareDO req = args[3] as RechargePrepareDO
        GlRechargeResult result = args[4] as GlRechargeResult
        String gateway = ""
        if (FundConstant.PaymentType.BANKCARD_TRANSFER == merchant.getPaymentId()) {
            gateway = "bank"//卡卡

        }else if(FundConstant.PaymentType.ALI_PAY == merchant.getPaymentId()){
            gateway = "alipay"//支付宝红包

        }
        if (StringUtils.isEmpty(gateway)) {
            result.setErrorMsg("支付方式不支持")
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            return
        }
        prepareToScan(merchant, account, req, result, gateway)
    }


    private void prepareToScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result, String gateway) throws GlobalException {

        Map<String, String> params = new LinkedHashMap<>()
        params.put("mid", account.getMerchantCode())
        params.put("time", System.currentTimeSeconds().toString())
        params.put("amount", req.getAmount().setScale(2, RoundingMode.DOWN).toString())
        params.put("currency", "CNY")
        params.put("order_no",req.getOrderId())
        params.put("gateway", gateway)
        params.put("ip", req.getIp())
        params.put("notify_url", account.getNotifyUrl() + merchant.getId())
        params.put("name", req.getFromCardUserName())
        params.put("sign", sign(account.getPrivateKey(), MD5.toAscii(params)))

        Map<String, String> headParams = new HashMap<String, String>()
        headParams.put("Authorization", "api-key " + account.getPublicKey())
        headParams.put("content-type", "application/json")

        log.info("FengWoScript_recharge_prepare_params:{} ,url:{} ", JSON.toJSONString(params) , account.getPayUrl() + PAY_URL)
        GlRequestHeader requestHeader =
                getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode(), account.getChannelId(), account.getChannelName())
        String resStr = okHttpUtil.postJSON(account.getPayUrl() + PAY_URL, JSON.toJSONString(params), headParams, requestHeader)
        log.info("FengWoScript_recharge_prepare_resp:{} ,orderId :{} ", resStr ,req.getOrderId() )
        if (StringUtils.isEmpty(resStr)) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("网络请求超时，稍后重试")
            return
        }
        JSONObject json = JSONObject.parseObject(resStr)
        if (json == null || "200" != json.getString("code")) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg(json.getString("message"))
            return
        }
        JSONObject dataJSON = json.getJSONObject("data");
        if (dataJSON == null || StringUtils.isEmpty(dataJSON.getString("url"))) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg("商家未出码")
            return
        }
            result.setRedirectUrl(dataJSON.getString("url"))
    }

    public RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>
        log.info("FengWoScript_notify_resp:{}", JSON.toJSONString(resMap))
        JSONObject json = JSON.parseObject(resMap.get("reqBody"));

        Map<String, String> signMap  =(Map<String, String>) json.getJSONObject("data");
        String orderId = signMap.get("order_no")
        String thirdSign= signMap.remove("sign")
        String sign =sign(account.getPrivateKey(), MD5.toAscii(signMap))

        if (StringUtils.isNotEmpty(orderId) || thirdSign == sign ) {
            return this.payQuery(okHttpUtil, account, orderId, args[4])
        }

        log.info("FengWoScript_notify_Sign 回调有误或验签失败 orderId :{}", orderId )
        return null
    }

    public RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String

        Map<String, String> params = new LinkedHashMap<>()
        params.put("mid", account.getMerchantCode())
        params.put("time", System.currentTimeSeconds().toString())
        params.put("order_no",orderId)
        params.put("sign", sign(account.getPrivateKey(), MD5.toAscii(params)))

        Map<String, String> headParams = new HashMap<String, String>()
        headParams.put("Authorization", "api-key " + account.getPublicKey())
        headParams.put("content-type", "application/json")

        log.info("FengWoScript_query_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = getRequestHeard(null, null, orderId, GlActionEnum.RECHARGE_QUERY.getCode(), account.getChannelId(), account.getChannelName())
        String resStr = okHttpUtil.postJSON(account.getPayUrl() + QUERY_URL, JSON.toJSONString(params), headParams, requestHeader)
        log.info("FengWoScript_query_resp:{}", resStr)
        if (StringUtils.isEmpty(resStr)) {
            return null
        }
        JSONObject json = JSON.parseObject(resStr)
        if (json == null || json.getString("code") != "200" || ObjectUtils.isEmpty(json.getJSONObject("data"))) {
            return null
        }
        JSONObject dataJSON = json.getJSONObject("data");
        // 支付状态:status  成功: succeeded 失败: failed 超时未支付: expired
        if (!ObjectUtils.isEmpty(dataJSON) && ("succeeded" == (dataJSON.getString("status")))) {
            RechargeNotify pay = new RechargeNotify()
            pay.setAmount(dataJSON.getBigDecimal("actual_amount").setScale(2, RoundingMode.DOWN))
            pay.setFee(BigDecimal.ZERO)
            pay.setOrderId(orderId)
            pay.setRsp("ok")
            pay.setThirdOrderId(dataJSON.getString("no"))
            return pay
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

    public String sign (String secretKey, String data) { // 利用 apache 工具类 HmacUtils
        byte[] bytes = HmacUtils.hmacSha1(secretKey, data);
        return Base64.getEncoder().encodeToString(bytes);
    }

}
