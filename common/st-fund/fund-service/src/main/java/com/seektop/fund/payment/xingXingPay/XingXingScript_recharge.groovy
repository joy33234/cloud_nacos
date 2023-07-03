package com.seektop.fund.payment.xingXingPay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
import com.seektop.common.utils.DateUtils
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

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.math.RoundingMode
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException
/**
 * @desc 星星支付
 * @date 2021-11-27
 * @auth otto
 */
public class XingXingScript_recharge {

    private static final Logger log = LoggerFactory.getLogger(XingXingScript_recharge.class)
    private OkHttpUtil okHttpUtil

    private  final String PAY_URL =  "/Service/uniorder"
    private  final String QUERY_URL =  "/Service/getOrder"

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
            gateway = "BankCard" //卡转卡
        }

        if (StringUtils.isEmpty(gateway)) {
            result.setErrorMsg("支付方式不支持")
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            return
        }
        prepareToScan(merchant, account, req, result, gateway)
    }

    private void prepareToScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result, String gateway) throws GlobalException {

        Map<String, String> params = new HashMap<>()
        params.put("version", "1.0")
        params.put("mch_id", account.getMerchantCode())
        params.put("pay_type", gateway )
        params.put("user_ip", req.getIp() )
        params.put("user_uid", req.getUserId()+"")
        params.put("fee_type", "CNY")
        params.put("user_name","TOm")
        params.put("out_trade_no",req.getOrderId())
        params.put("total_fee", req.getAmount().multiply(100).setScale(0, RoundingMode.DOWN).toString()) //單位：分
        params.put("scene_info", "4")
        params.put("notify_url", account.getNotifyUrl() + merchant.getId() )
        params.put("time_start", DateUtils.format(new Date(), DateUtils.YYYY_MM_DD_HH_MM_SS) )

        String toSign = MD5.toAscii(params);
        toSign = getSign(toSign ,account.getPrivateKey())
        toSign = toSign.replace("\r\n","")
        params.put("sign", toSign )

        GlRequestHeader requestHeader =
                getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode(), account.getChannelId(), account.getChannelName())
        log.info("XingXingScript_recharge_prepare_params:{} , url :{}", params, account.getPayUrl() + PAY_URL )

        String resStr = okHttpUtil.post(account.getPayUrl() + PAY_URL, params, requestHeader)
        log.info("XingXingScript_recharge_prepare_resp:{} , orderId:{} ", resStr  , req.getOrderId())
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

        //200 是接口通信情况标识，非处理成功与否的标识。
        if ( "200" != json.getString("code")  ) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg(json.getString("msg"))
            return
        }


        JSONObject dataJSON = json.getJSONObject("data")

        if ( StringUtils.isEmpty(dataJSON.getString("qrcode"))) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg("支付商家未出码")
            return
        }

        result.setThirdOrderId(dataJSON.getString("order_no"))
        result.setRedirectUrl(dataJSON.getString("qrcode"))
    }

    public RechargeNotify notify(Object[] args) throws GlobalException {

        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>
        log.info("XingXingScript_notify_resp:{}", resMap)

        String sign = resMap.get("sign")

        Map<String, Object> signMap =new LinkedHashMap<>();
        signMap.put("order_no",resMap.get("order_no"))
        signMap.put("amount",resMap.get("amount"))
        signMap.put("mch_id",resMap.get("mch_id"))
        signMap.put("error_url",resMap.get("error_url"))
        signMap.put("update_time",resMap.get("update_time"))
        signMap.put("out_trade_no",resMap.get("out_trade_no"))
        signMap.put("total_fee",resMap.get("total_fee"))
        signMap.put("total_fee",resMap.get("total_fee"))
        signMap.put("pay_type",resMap.get("pay_type"))
        signMap.put("attach",resMap.get("attach"))
        signMap.put("status",resMap.get("status"))
        signMap.put("success_url",resMap.get("success_url"))

        String toSign = MD5.toAscii(signMap);
        toSign = getSign(toSign ,account.getPrivateKey())
        toSign = toSign.replace("\r\n","")

        if (StringUtils.isNotEmpty( resMap.get("out_trade_no")) && sign == toSign) {
            return this.payQuery(okHttpUtil, account,  resMap.get("out_trade_no"))

        }
        log.info("XingXingScript_notify_Sign: 回调资料错误或验签失败，orderId：{}" ,  resMap.get("out_trade_no") )

        return null
    }

    public RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String

        Map<String, String> params = new LinkedHashMap<>()
        params.put("mch_id", account.getMerchantCode())
        params.put("out_trade_no",orderId)

        String toSign = MD5.toAscii(params);
        toSign = getSign(toSign ,account.getPrivateKey())
        toSign = toSign.replace("\r\n","")
        params.put("sign",toSign)

        log.info("XingXingScript_query_params:{} url:{} ", params ,account.getPayUrl() + QUERY_URL)
        GlRequestHeader requestHeader = getRequestHeard(null, null, orderId, GlActionEnum.RECHARGE_QUERY.getCode(), account.getChannelId(), account.getChannelName())
        String resStr = okHttpUtil.post(account.getPayUrl() + QUERY_URL, params, requestHeader)

        log.info("XingXingScript_query_resp:{}", resStr)
        if (StringUtils.isEmpty(resStr)) {
            return null
        }

        JSONObject json = JSON.parseObject(resStr)
        //200 成功，其餘失敗
        if (json == null  || 200 != json.getInteger("code") ) {
            return null
        }

        JSONObject dataJSON = json.getJSONObject("data")
        Integer orderState  = dataJSON.getInteger("status")

        //1处理中，2成功，其它失败
        if ( 2 == orderState ) {
            RechargeNotify pay = new RechargeNotify()
            pay.setAmount(dataJSON.getBigDecimal("total_fee"))
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
        return false;
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
        return FundConstant.ShowType.NORMAL;
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

    public String getSign(String value, String accessToken)
            throws NoSuchAlgorithmException, InvalidKeyException, UnsupportedEncodingException {
        Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
        SecretKeySpec secret_key = new SecretKeySpec(accessToken.getBytes(), "HmacSHA256")
        sha256_HMAC.init(secret_key);
        byte[] bytes = sha256_HMAC.doFinal(value.getBytes());
        return org.apache.commons.net.util.Base64.encodeBase64String(bytes)
    }


}