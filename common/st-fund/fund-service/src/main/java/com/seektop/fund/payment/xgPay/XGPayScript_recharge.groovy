package com.seektop.fund.payment.xgPay

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
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode
/**
 * @desc XGPay支付
 * @date 2021-10-22
 * @auth otto
 */
public class XGPayScript_recharge {

    private static final Logger log = LoggerFactory.getLogger(XGPayScript_recharge.class)
    private OkHttpUtil okHttpUtil

    private  final String PAY_URL =  "/api"
    private  final String QUERY_URL =  "/orderquery"

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
        if (FundConstant.PaymentType.QUICK_WECHAT_PAY == merchant.getPaymentId()) {
            gateway = "117" //微信红包
        }

        if (StringUtils.isEmpty(gateway)) {
            result.setErrorMsg("支付方式不支持")
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            return
        }
        prepareToScan(merchant, account, req, result, gateway)
    }


    private void prepareToScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result, String gateway) throws GlobalException {

        Map<String, Object> params = new LinkedHashMap<>()
        params.put("money", req.getAmount().setScale(2, RoundingMode.DOWN).toString())
        params.put("order",req.getOrderId())
        params.put("sid", account.getMerchantCode())
        String toSign = "miyao="+account.getPrivateKey() + "&"+ MD5.toSign(params) ;
        params.put("sign",  MD5.md5(toSign))

        params.put("paytype", gateway)
        params.put("notify_url", account.getNotifyUrl() + merchant.getId())
        params.put("return_url", account.getNotifyUrl())
        params.put("name", "微信红包")
        params.put("date", System.currentTimeMillis()+"")

        log.info("XGPayScript_recharge_prepare_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode(), account.getChannelId(), account.getChannelName())
        String resStr = okHttpUtil.post(account.getPayUrl() + PAY_URL, params, requestHeader)
        log.info("XGPayScript_recharge_prepare_resp:{}", resStr)

        if (StringUtils.isEmpty(resStr)) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("网络请求超时，稍后重试")
            return
        }

        JSONObject json = JSONObject.parseObject(resStr)
        if (json == null ) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg("api接口异常，稍后重试")
            return
        }
            //1 成功 其余失败
        if ("1" != json.getString("code")  ) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg(json.getString("message"))
            return
        }

        if ( StringUtils.isEmpty(json.getString("payurl"))) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg("支付商家未出码")
            return
        }
        result.setRedirectUrl(json.getString("payurl"))
    }

    public RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>
        log.info("XGPayScript_notify_resp:{}", resMap)

        JSONObject json = JSONObject.parseObject(resMap.get("reqBody"));
        Map<String, Object> notifyMap = (Map<String, Object>) json

        Map<String, Object> signMap = new LinkedHashMap<>();
        signMap.put("miyao",account.getPrivateKey())
        signMap.put("money",notifyMap.get("money"))
        signMap.put("order",notifyMap.get("order"))
        signMap.put("out_order",notifyMap.get("out_order"))
        signMap.put("sid",notifyMap.get("sid"))
        signMap.put("status",notifyMap.get("status"))

        String md5Sign = MD5.md5(MD5.toAscii(signMap))

        if (StringUtils.isNotEmpty(notifyMap.get("out_order")) && md5Sign == notifyMap.get("sign") ) {
            return this.payQuery(okHttpUtil, account, notifyMap.get("out_order") );

        } else {
            log.info("XGPayScript_notify_sign: 回调资料错误或验签失败，单号：{}" , notifyMap.get("out_order") )
            return null
        }
        return null
    }

    public RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String

        Map<String, String> params = new LinkedHashMap<>()
        params.put("sid", account.getMerchantCode())
        params.put("order",orderId)

        String toSign =  account.getMerchantCode() + orderId + account.getPrivateKey()
        params.put("sign", MD5.md5(toSign))

        log.info("XGPayScript_query_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = getRequestHeard(null, null, orderId, GlActionEnum.RECHARGE_QUERY.getCode(), account.getChannelId(), account.getChannelName())

        String resStr = okHttpUtil.post(account.getPayUrl() + QUERY_URL, params, requestHeader)

        log.info("XGPayScript_query_resp:{}", resStr)
        if (StringUtils.isEmpty(resStr)) {
            return null
        }

        JSONObject json = JSON.parseObject(resStr)
        if (json == null  || "1" != json.getString("code") ) {
            return null
        }
        JSONObject dataJson = json.getJSONObject("data")

        // 【1支付成功】【0支付异常】
        if ( 1 == (dataJson.getInteger("status")) ) {
            RechargeNotify pay = new RechargeNotify()
            pay.setAmount(dataJson.getBigDecimal("money").setScale(2, RoundingMode.DOWN))
            pay.setFee(BigDecimal.ZERO)
            pay.setOrderId(orderId)
            pay.setRsp("success")
            
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

}