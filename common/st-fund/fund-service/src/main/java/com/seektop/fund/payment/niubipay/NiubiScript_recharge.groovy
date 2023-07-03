package com.seektop.fund.payment.niubipay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
import com.seektop.common.redis.RedisService
import com.seektop.constant.FundConstant
import com.seektop.constant.RedisKeyHelper
import com.seektop.enumerate.GlActionEnum
import com.seektop.exception.GlobalException
import com.seektop.fund.model.GlPaymentMerchantApp
import com.seektop.fund.model.GlPaymentMerchantaccount
import com.seektop.fund.payment.BlockInfo
import com.seektop.fund.payment.GlRechargeResult
import com.seektop.fund.payment.RechargeNotify
import com.seektop.fund.payment.RechargePrepareDO
import com.seektop.fund.payment.groovy.BaseScript
import com.seektop.fund.payment.groovy.ResourceEnum
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode

/**
 * @desc 牛币支付
 * @date 20210522
 * @author joy
 */
public class NiubiScript_recharge {

    private static final Logger log = LoggerFactory.getLogger(NiubiScript_recharge.class)

    private OkHttpUtil okHttpUtil

    private RedisService redisService

    /**
     * 封装支付请求参数
     *
     * @param merchant
     * @param merchantaccount
     * @param req
     * @param result
     */
    public void pay(Object[] args) {
        this.okHttpUtil = args[0] as OkHttpUtil
        this.redisService = BaseScript.getResource(args[5], ResourceEnum.RedisService) as RedisService

        GlPaymentMerchantApp merchant = args[1] as GlPaymentMerchantApp
        GlPaymentMerchantaccount merchantaccount = args[2] as GlPaymentMerchantaccount
        RechargePrepareDO req = args[3] as RechargePrepareDO
        GlRechargeResult result = args[4] as GlRechargeResult

        Map<String, Object> params = new HashMap<String, Object>()
        params.put("amount", req.getAmount().setScale(2, RoundingMode.DOWN) + "")
        params.put("merchantId", merchant.getMerchantCode())
        params.put("orderId", req.getOrderId())
        params.put("paymentTypeId", req.getPaymentTypeId())
        params.put("userId", req.getUserId().toString())
        params.put("reallyName", req.getFromCardUserName())
        params.put("callback", merchantaccount.getNotifyUrl() + merchant.getId())
        params.put("username", req.getUsername())

        Map<String, String> headParams = new HashMap<String, String>()
        headParams.put("appId", merchantaccount.getPublicKey())
        headParams.put("secretKey",merchantaccount.getPrivateKey())

        GlRequestHeader requestHeader = getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode(), merchantaccount.getChannelId(), merchantaccount.getChannelName())
        log.info("NiubiScript_Prepare_resMap:{}", JSON.toJSONString(params))
        String resStr = okHttpUtil.post(merchantaccount.getPayUrl() + "/api/partner/trade/order/submit/buy/order", params, requestHeader , headParams)
        log.info("NiubiScript_Prepare_resStr:{}", resStr)
        JSONObject json = JSON.parseObject(resStr)

        if (json == null || json.getString("code") != "1") {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("网络请求超时，稍后重试")
            return
        }
        JSONObject dataJSON = json.getJSONObject("data");
        if (dataJSON == null || StringUtils.isEmpty(dataJSON.getString("redirectUrl"))) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg(StringUtils.isEmpty(json.getString("message")) ? "商户异常" : json.getString("message"))
            return
        }
        BlockInfo blockInfo = new BlockInfo()
        blockInfo.setDigitalAmount(dataJSON.getBigDecimal("amount"))
        blockInfo.setRate(dataJSON.getBigDecimal("price"))
        result.setBlockInfo(blockInfo)

        result.setRedirectUrl(dataJSON.getString("redirectUrl"))
        result.setThirdOrderId(dataJSON.getString("orderId"));

        redisService.set(RedisKeyHelper.RECHARGE_ORDER_STATUS + req.getOrderId(), dataJSON.getString("redirectUrl"),dataJSON.getInteger("ttl"));
    }

    /**
     * 支付结果
     *
     * @param merchant
     * @param account
     * @param resMap
     * @return
     */
    public RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        Map<String, String> resMap = args[3] as Map<String, String>
        GlPaymentMerchantaccount account = args[2] as GlPaymentMerchantaccount
        log.info("NiubiScript_notify_resp:{}", JSON.toJSONString(resMap))
        String orderId = resMap.get("merchantOrderId")
        if (StringUtils.isNotEmpty(orderId) ) {
            return this.payQuery(okHttpUtil, account, orderId)
        }
        return null
    }

    public RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String
        log.info("NiubiScript_query_params_orderId:{}", orderId)

        Map<String, Object> params = new HashMap<String, Object>()
        params.put("merchantId", account.getMerchantCode())
        params.put("orderId", orderId)

        Map<String, String> headParams = new HashMap<String, String>()
        headParams.put("appId", account.getPublicKey())
        headParams.put("secretKey",account.getPrivateKey())

        GlRequestHeader requestHeader = getRequestHeard("", "", orderId, GlActionEnum.RECHARGE.getCode(), account.getChannelId(), account.getChannelName())
        log.info("NiubiScript_query_resMap:{}", JSON.toJSONString(params))
        String resStr = okHttpUtil.post(account.getPayUrl() + "/api/partner/trade/order/get/buy/order", params, requestHeader, headParams)
        log.info("NiubiScript_query_resStr:{}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || json.getString("code") != "1") {
            return null
        }
        JSONObject dataJSON = json.getJSONObject("data")
        // 订单状态判断标准:  0:待付款   1:已取消   2:待确认  3:已完成
        if (dataJSON != null && dataJSON.getString("status") == "3") {
            RechargeNotify pay = new RechargeNotify()
            pay.setAmount(dataJSON.getBigDecimal("paymentAmount").setScale(2, RoundingMode.DOWN))
            pay.setFee(BigDecimal.ZERO)
            pay.setOrderId(orderId)
            pay.setThirdOrderId(dataJSON.getString("dealOrderId"))
            pay.setRealRate(dataJSON.getBigDecimal("price").setScale(4, RoundingMode.DOWN))
            pay.setPayDigitalAmount(dataJSON.getBigDecimal("amount").setScale(4, RoundingMode.DOWN))
            return pay
        }
        return null
    }


    private PaymentInfo payments(Object[] args){
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
        BigDecimal amount = args[2] as BigDecimal

        PaymentInfo paymentInfo = new PaymentInfo();
        Map<String, Object> params = new HashMap<String, Object>()
        params.put("amount", amount.setScale(2, RoundingMode.DOWN) + "")
        params.put("merchantId", account.getMerchantCode())

        Map<String, String> headParams = new HashMap<String, String>()
        headParams.put("appId", account.getPublicKey())
        headParams.put("secretKey",account.getPrivateKey())

        log.info("NiubiScript_payments_resStr:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = getRequestHeard("", "", "", GlActionEnum.QUERY_PAYMENT.getCode(), account.getChannelId(), account.getChannelName())
        String resStr = okHttpUtil.post(account.getPayUrl() + "/api/partner/trade/order/get/buy/payment/types", params, requestHeader, headParams )
        log.info("NiubiScript_payments_resStr:{}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || json.getString("code") != "1") {
            paymentInfo.setErrosMessage(json.getString("message"))
            return paymentInfo
        }
        JSONObject dataJSON = json.getJSONObject("data");
        if (dataJSON == null) {
            paymentInfo.setErrosMessage("返回数据异常，请联系客服")
            return paymentInfo
        }
        return JSONObject.parseObject(dataJSON.toJSONString(), PaymentInfo.class);

    }

    /**
     * 获取头部信息
     *
     * @param userId
     * @param userName
     * @param orderId
     * @return
     */
    private static GlRequestHeader getRequestHeard(String userId, String userName, String orderId, String code, Integer channelId, String channelName) {
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

    /**
     * 充值USDT汇率
     *
     * @param args
     * @return
     */
    public BigDecimal paymentRate(Object[] args) {
        return null
    }




}
