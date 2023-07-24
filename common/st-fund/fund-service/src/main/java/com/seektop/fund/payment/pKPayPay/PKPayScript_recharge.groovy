package com.seektop.fund.payment.pKPayPay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
import com.seektop.common.utils.RSASignature
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
 * @desc PK支付
 * @auth Otto
 * @date 2022-02-11
 */
class PKPayScript_recharge {

    private static final Logger log = LoggerFactory.getLogger(PKPayScript_recharge.class)

    private OkHttpUtil okHttpUtil
    private  final String PAY_URL =  "/api/unifiedorder"
    private  final String QUERY_URL =  "/api/payQuery"

    RechargeNotify result(Object[] args) throws GlobalException {
        return notify(args)
    }

    void pay(Object[] args) {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantApp merchant = args[1] as GlPaymentMerchantApp
        GlPaymentMerchantaccount payment = args[2] as GlPaymentMerchantaccount
        RechargePrepareDO req = args[3] as RechargePrepareDO
        GlRechargeResult result = args[4] as GlRechargeResult
        String payType = ""
        if (merchant.getPaymentId() == FundConstant.PaymentType.BANKCARD_TRANSFER) {
            payType = "KZK" //卡转卡
        } else if (merchant.getPaymentId() == FundConstant.PaymentType.QUICK_ALI_PAY) {
            payType = "ZFBHF" //支付宝话费
        } else if (merchant.getPaymentId() == FundConstant.PaymentType.QUICK_WECHAT_PAY) {
            payType = "WXHF" //微信话费
        } else if (merchant.getPaymentId() == FundConstant.PaymentType.ALI_PAY) {
            payType = "ZFBH5" //支付宝
        } else if (merchant.getPaymentId() == FundConstant.PaymentType.WECHAT_PAY) {
            payType = "WXH5" //微信
        }
        if (StringUtils.isNotEmpty(payType)) {
            prepareScan(merchant, payment, req, result, payType)
        }
    }

    private void prepareScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, RechargePrepareDO req, GlRechargeResult result, String payType) {
        Map<String, String> params = new HashMap<String, String>()
        params.put("mchId", payment.getMerchantCode())
        params.put("outTradeNo", req.getOrderId())
        params.put("tradeType", payType)
        params.put("nonceStr", System.currentTimeMillis()+"")
        params.put("notifyUrl", payment.getNotifyUrl() + merchant.getId())
        params.put("payAmount", req.getAmount().setScale(2, RoundingMode.DOWN) + "")
        params.put("payName", req.getFromCardUserName())

        String toSign = getSignStr(params)
        String sign = RSASignature.sign(toSign, payment.getPrivateKey());
        params.put("sign", sign)
        
        log.info("PKPayScript_Prepare_Params:{} url: {}", JSON.toJSONString(params) , payment.getPayUrl())
        GlRequestHeader requestHeader = getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode(), payment.getChannelId(), payment.getChannelName())
        String restr = okHttpUtil.post(payment.getPayUrl() + PAY_URL, params,  requestHeader)
        log.info("PKPayScript_Prepare_resStr:{} , orderId :{}" , restr, req.getOrderId())

        JSONObject json = JSON.parseObject(restr);
        if (json == null) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("网络请求超时，稍后重试")
            return
        }
        if (json.getInteger("code") != 200 ) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg(json.getString("message"))
            return
        }

        result.setRedirectUrl(json.getString("redirect") )

    }

    RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount payment = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>

        log.info("PKPayScript_Notify_resMap:{}", JSON.toJSONString(resMap))
        String orderid = resMap.get("outTradeNo")

        StringBuffer s = new StringBuffer();

        s.append("mchId=" + resMap.get("mchId") + "&");
        s.append("outTradeNo=" + resMap.get("outTradeNo")+ "&");
        s.append("payAmount=" + resMap.get("payAmount") + "&");
        s.append("transactionId=" + resMap.get("transactionId") + "&");
        s.append("nonceStr=" + resMap.get("nonceStr") + "&");
        s.append("success=" + resMap.get("success"));

        if (RSASignature.doCheck(s.toString(), resMap.get("sign"), payment.getPublicKey())){
            return payQuery(okHttpUtil, payment, orderid)

        }
        log.info("PKPayScript_RechargeNotify_Sign: 回调资料错误或验签失败 单号：{}", orderid)
        return null

    }

    RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String

        Map<String, String> params = new HashMap<String, String>()
        params.put("mchId", account.getMerchantCode())
        params.put("orderId", orderId)

        StringBuffer s= new StringBuffer();
        s.append("mchId=" + account.getMerchantCode() + "&");
        s.append("orderId=" + orderId);
        String sign = RSASignature.sign(s.toString(),account.getPrivateKey());

        params.put("sign", sign)

        log.info("PKPayScript_Query_reqMap:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = getRequestHeard(null, null, orderId, GlActionEnum.RECHARGE_QUERY.getCode(), account.getChannelId(), account.getChannelName())
        String resStr = okHttpUtil.post(account.getPayUrl() + QUERY_URL, params, 30L, requestHeader)
        log.info("PKPayScript_Query_resStr:{}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || "true" != json.getString("success")) {
            return null
        }

        //0：未处理；1：成功为返回；2：成功已返回
        if ( 1 == json.getInteger("status") || 2 == json.getInteger("status")) {
            RechargeNotify pay = new RechargeNotify()
            pay.setAmount(json.getBigDecimal("payAmount").setScale(2, RoundingMode.DOWN))
            pay.setFee(BigDecimal.ZERO)
            pay.setOrderId(orderId)
            pay.setRsp("OK")
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
        return true
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


    private String getSignStr (HashMap<String, String> params ){
        StringBuffer s = new StringBuffer();
        s.append("mchId=" + params.get("mchId") + "&");
        s.append("outTradeNo=" +  params.get("outTradeNo")+ "&");
        s.append("payAmount=" +  params.get("payAmount") + "&");
        s.append("nonceStr=" +  params.get("nonceStr") + "&");
        s.append("tradeType=" +  params.get("tradeType") + "&");
        s.append("notifyUrl=" +  params.get("notifyUrl"));

        return s.toString() ;
    }



}
