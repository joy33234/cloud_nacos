package com.seektop.fund.payment.chaoFanV2Pay

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
 * @desc 超凡v2支付
 * @date 2021-12-07
 * @auth Otto
 */
public class ChaoFanV2Script_recharge {

    private static final Logger log = LoggerFactory.getLogger(ChaoFanV2Script_recharge.class)

    private OkHttpUtil okHttpUtil
    private  final String PAY_URL =  "/gateway"
    private  final String QUERY_URL =  "/gateway"

    public RechargeNotify result(Object[] args) throws GlobalException {
        return null
    }

    public void pay(Object[] args) {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantApp merchant = args[1] as GlPaymentMerchantApp
        GlPaymentMerchantaccount account = args[2] as GlPaymentMerchantaccount
        RechargePrepareDO req = args[3] as RechargePrepareDO
        GlRechargeResult result = args[4] as GlRechargeResult
        String payType = ""
        if (FundConstant.PaymentType.BANKCARD_TRANSFER == merchant.getPaymentId()) {
            payType = "pay.alipay.more"  //小额卡转卡
        }
        if (StringUtils.isEmpty(payType)) {
            result.setErrorMsg("支付方式不支持")
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            return
        }
        prepareToScan(merchant, account, req, result, payType)
    }


    private void prepareToScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result, String payType) throws GlobalException {
        Date date = new Date();
        Map<String, String> params = new LinkedHashMap<>()
        params.put("service", payType )
        params.put("version", "1.0" )
        params.put("merchantId", account.getMerchantCode())
        params.put("orderNo",req.getOrderId())
        params.put("tradeDate", DateUtils.format(date, DateUtils.YYYYMMDD))
        params.put("tradeTime", DateUtils.format(date, DateUtils.HHMMSS))
        params.put("amount", req.getAmount().multiply(100).setScale(0, RoundingMode.DOWN).toString())
        params.put("clientIp", req.getIp())
        params.put("merchantUrl", account.getNotifyUrl() )
        params.put("notifyUrl", account.getNotifyUrl() + merchant.getId())
        params.put("resultType", "json")
        params.put("key", account.getPrivateKey())

        String toSign = MD5.toAscii(params)
        params.put("sign", MD5.md5(toSign))
        params.remove("key")
        
        log.info("ChaoFanScript_recharge_prepare_params:{} , url:{} ", JSON.toJSONString(params) , account.getPayUrl()+PAY_URL)
        GlRequestHeader requestHeader =
                getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode(), account.getChannelId(), account.getChannelName())

        String resStr = okHttpUtil.post(  account.getPayUrl()+ PAY_URL, params, requestHeader)
        log.info("ChaoFanScript_recharge_prepare_resp:{}", resStr)
        if (StringUtils.isEmpty(resStr)) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("网络请求超时，稍后重试")
            return
        }
        JSONObject json = JSONObject.parseObject(resStr)
        if (json == null || "0001" != json.getString("repCode")) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg(json.getString("repMsg"))
            return
        }

        result.setRedirectUrl(json.getString("resultUrl"))
    }


    public RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>
        log.info("ChaoFanScript_notify_resp:{}", JSON.toJSONString(resMap))

        JSONObject jsonObj = JSON.parseObject(resMap.get("reqBody"));

        Map<String, String> signMap  =(Map<String, String>) jsonObj;
        String orderId =signMap.get("orderNo")
        String thirdSign = signMap.remove("sign")
        signMap.put("key",account.getPrivateKey())

        String sign = MD5.md5(MD5.toAscii(signMap))

        if (StringUtils.isNotEmpty(orderId) && sign == thirdSign ) {
            return this.payQuery(okHttpUtil, account, orderId )
        }
        log.info("ChaoFanScript_notify_Sign:回调错误或验签失败 ,orderId:{}",orderId )

        return null
    }

    public RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String
        Date date = new Date();

        Map<String, String> params = new LinkedHashMap<>()
        params.put("service", "trade.query")
        params.put("version", "1.0")
        params.put("merchantId", account.getMerchantCode())
        params.put("orderNo", orderId)
        params.put("tradeDate", DateUtils.format(date, DateUtils.YYYYMMDD))
        params.put("tradeTime", DateUtils.format(date, DateUtils.HHMMSS))
        params.put("resultType", "json")
        params.put("key", account.getPrivateKey())

        String sign = MD5.toAscii(params)
        sign = MD5.md5(sign);
        params.put("sign", sign)
        params.remove("key")

        log.info("ChaoFanScript_query_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = getRequestHeard(null, null, orderId, GlActionEnum.RECHARGE_QUERY.getCode(), account.getChannelId(), account.getChannelName())
        String resStr = okHttpUtil.post(account.getPayUrl() + QUERY_URL , params, 30l, requestHeader)
        log.info("ChaoFanScript_query_resp:{}", resStr)
        if (StringUtils.isEmpty(resStr)) {
            return null
        }
        JSONObject json = JSON.parseObject(resStr)
        if (json == null || json.getString("repCode") != "0001") {
            return null
        }
        // 只有当返回码为讯息成功时才返回
        //0：未支付
        //1：支付成功
        //4：支付失败
        //5：订单不存在或已过期
        if ( "1" == json.getString("resultCode")) {
            RechargeNotify pay = new RechargeNotify()
            pay.setAmount(json.getBigDecimal("amount").divide(100).setScale(2, RoundingMode.DOWN))
            pay.setFee(BigDecimal.ZERO)
            pay.setThirdOrderId(json.getString("orderNo"))
            pay.setOrderId(orderId)
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




}