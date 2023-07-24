package com.seektop.fund.payment.yuMiPay

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

import java.math.RoundingMode
/**
 * @desc 玉米支付
 * @date 2021-10-20
 * @auth otto
 */
public class YuMiScript_recharge {

    private static final Logger log = LoggerFactory.getLogger(YuMiScript_recharge.class)
    private OkHttpUtil okHttpUtil

    private  final String PAY_URL =  "/api/pglite/sporder/sigorder"
    private  final String QUERY_URL =  "/api/pglite/sporder/sigquery"

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
        //三方后台自行配置是扫码还是话费
        if (FundConstant.PaymentType.QUICK_WECHAT_PAY == merchant.getPaymentId()) {
            gateway = "40960" //微信话费 （极速微信支付）

        }else if (FundConstant.PaymentType.WECHAT_PAY == merchant.getPaymentId() ){
            gateway = "20480" //微信支付

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
        params.put("spid", account.getMerchantCode())
        params.put("amount", req.getAmount().multiply(new BigDecimal(100)).setScale(0, RoundingMode.DOWN).toString()); //单位：分
        params.put("timestamp", System.currentTimeMillis()+"")
        params.put("cpparam",req.getOrderId())

        String toSign = MD5.toSign(params) + "&key=" + account.getPublicKey()
        String data_sign = MD5.md5(toSign)
        params.put("ordertype", gateway)
        params.put("cburl", account.getNotifyUrl() + merchant.getId())
        params.put("sig", data_sign)

        log.info("YuMiScript_recharge_prepare_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode(), account.getChannelId(), account.getChannelName())
        String resStr = okHttpUtil.postJSON(account.getPayUrl() + PAY_URL, JSON.toJSONString(params), requestHeader)
        log.info("YuMiScript_recharge_prepare_resp:{}", resStr)

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

        if ( 100 != json.getInteger("status")  ) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg(json.getString("message"))
            return
        }

        if ( StringUtils.isEmpty(json.getString("qrhtml"))) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg("支付商家未出码")
            return
        }

        result.setRedirectUrl(json.getString("qrhtml"))
        result.setThirdOrderId(json.getString("orderid"))
    }

    public RechargeNotify notify(Object[] args) throws GlobalException {
        log.info("YuMiScript_notify_args:{}", args)
        this.okHttpUtil = args[0] as OkHttpUtil

        GlPaymentMerchantaccount account = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>
        log.info("YuMiScript_notify_resp:{}", resMap)

        JSONObject json = JSONObject.parseObject(resMap.get("reqBody"));
        Map<String, Object> map = (Map<String, Object>) json
        String thirdSign = map.get("sig");

        Map<String, String> signMap = new LinkedHashMap<>();
        signMap.put("spid",map.get("spid"));
        signMap.put("orderid",map.get("orderid"));

        String toSign = MD5.toSign(signMap) + "&key=" + account.getPublicKey()
        toSign = MD5.md5(toSign)

        if (StringUtils.isNotEmpty( map.get("cpparam")) && toSign == thirdSign ) {
            return this.payQuery(okHttpUtil, account, map.get("cpparam")   )

        }else{
            log.info("YuMiScript_notify_Sign: 回调资料错误或验签失败，单号：{}" , map.get("cpparam") )
        }
        return null
    }

    public RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String

        Map<String, String> params = new LinkedHashMap<>()

        params.put("orderid", "NONE")
        params.put("timestamp", System.currentTimeMillis()+"")
        String toSign = MD5.toSign(params) + "&key=" + account.getPublicKey()
        params.put("sig", MD5.md5(toSign))
        params.put("spid", account.getMerchantCode())
        params.put("cpparam", orderId)

        log.info("YuMiScript_query_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = getRequestHeard(null, null, orderId, GlActionEnum.RECHARGE_QUERY.getCode(), account.getChannelId(), account.getChannelName())
        String resStr = okHttpUtil.postJSON(account.getPayUrl() + QUERY_URL, JSON.toJSONString(params), requestHeader)

        log.info("YuMiScript_query_resp:{}", resStr)
        if (StringUtils.isEmpty(resStr)) {
            return null
        }

        JSONObject json = JSON.parseObject(resStr)
        if (json == null  || 100 != json.getInteger("status") ) {
            return null
        }

        //pending	支付中
        //genqrok	支付中(生成支付链接成功)
        //failed	支付失败
        //successful	支付成功
        if ( "successful" == (json.getString("order_status_str")) ) {
            RechargeNotify pay = new RechargeNotify()
            pay.setAmount(json.getBigDecimal("amount_req").divide(BigDecimal.valueOf(100)))
            pay.setFee(BigDecimal.ZERO)
            pay.setOrderId(orderId)
            pay.setRsp("SUCCESS")
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