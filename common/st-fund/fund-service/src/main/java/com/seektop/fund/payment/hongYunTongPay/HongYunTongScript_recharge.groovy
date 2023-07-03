package com.seektop.fund.payment.hongYunTongPay

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

import java.math.RoundingMode
/**
 * @desc 鸿运通支付
 * @date 2021-11-12
 * @auth otto
 */
public class HongYunTongScript_recharge {

    private static final Logger log = LoggerFactory.getLogger(HongYunTongScript_recharge.class)
    private OkHttpUtil okHttpUtil

    private  final String PAY_URL =  "/InterfaceV5/CreatePayOrder/"
    private  final String QUERY_URL =  "/InterfaceV5/QueryPayOrder/"

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
            gateway = "kzk" //卡转卡

        } else if (FundConstant.PaymentType.QUICK_ALI_PAY == merchant.getPaymentId()) {
            gateway = "zfb" //支付宝

        } else if (FundConstant.PaymentType.QUICK_WECHAT_PAY == merchant.getPaymentId()) {
            gateway = "wxhb" //微信红包

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
        params.put("Amount", req.getAmount().setScale(2, RoundingMode.DOWN).toString())
        params.put("Ip", req.getIp())
        params.put("MerchantId", account.getMerchantCode())
        params.put("MerchantUniqueOrderId",req.getOrderId())
        params.put("NotifyUrl", account.getNotifyUrl() + merchant.getId()) 
        params.put("PayTypeId", gateway )
        params.put("Remark", "HongYunTong")
        params.put("ReturnUrl", account.getNotifyUrl())

        String toSign = MD5.toAscii(params) + account.getPrivateKey()
        params.put("Sign",  MD5.md5(toSign))

        log.info("HongYunTongScript_recharge_prepare_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode(), account.getChannelId(), account.getChannelName())

        String resStr = okHttpUtil.post(account.getPayUrl() + PAY_URL, params, requestHeader)
        log.info("HongYunTongScript_recharge_prepare_resp:{}  , orderId:{}", resStr ,req.getOrderId())

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

        if ("0" != json.getString("Code")  ) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg(json.getString("MessageForUser"))
            return
        }

        if ( StringUtils.isEmpty(json.getString("Url"))) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg("支付商家未出码")
            return
        }
        result.setRedirectUrl(json.getString("Url"))
    }


    public RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[2] as GlPaymentMerchantaccount

        Map<String, String> resMap = args[3] as Map<String, String>
        Map<String, String> signMap  = new LinkedHashMap<>();

        signMap.put("FinishTime",resMap.get("FinishTime"))
        signMap.put("MerchantUniqueOrderId",resMap.get("MerchantUniqueOrderId"))
        signMap.put("PayOrderStatus",resMap.get("PayOrderStatus"))
        signMap.put("Amount",resMap.get("Amount"))
        signMap.put("MerchantId",resMap.get("MerchantId"))
        signMap.put("Timestamp",resMap.get("Timestamp"))
        signMap.put("Remark",resMap.get("Remark"))

        String thirdSign = resMap.get("Sign")
        String toSign = MD5.toAscii(signMap)  + account.getPrivateKey();
        toSign = MD5.md5(toSign);

        log.info("HongYunTongScript_notify_resp:{}", resMap)
        String orderId = resMap.get("MerchantUniqueOrderId") ;
        if (StringUtils.isNotEmpty(orderId) && toSign == thirdSign) {
            return this.payQuery(okHttpUtil, account, orderId ) ;
        }
        log.info("HongYunTongScript_notify_Sign: 回调资料错误或验签失败，orderId ：{}" , orderId )

        return null
    }

    public RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String

        Map<String, String> params = new LinkedHashMap<>()
        params.put("MerchantId", account.getMerchantCode())
        params.put("Timestamp", DateUtils.format(new Date(), DateUtils.YYYYMMDDHHMMSS))
        params.put("MerchantUniqueOrderId",orderId)

        String toSign = MD5.toAscii(params) + account.getPrivateKey()
        params.put("Sign", MD5.md5(toSign))

        log.info("HongYunTongScript_query_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = getRequestHeard(null, null, orderId, GlActionEnum.RECHARGE_QUERY.getCode(), account.getChannelId(), account.getChannelName())
        String resStr = okHttpUtil.post(account.getPayUrl() + QUERY_URL, params, requestHeader)

        log.info("HongYunTongScript_query_resp:{}", resStr)
        if (StringUtils.isEmpty(resStr)) {
            return null
        }

        JSONObject json = JSON.parseObject(resStr)
        if (json == null  || "0" != json.getString("Code") ) {
            return null
        }

        // 0 待支付      100 支付成功    -90 支付失败    -10 订单号不存在
        if ( "100" == (json.getString("PayOrderStatus")) ) {
            RechargeNotify pay = new RechargeNotify()
            pay.setAmount(json.getBigDecimal("RealAmount").setScale(2, RoundingMode.DOWN))
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


}