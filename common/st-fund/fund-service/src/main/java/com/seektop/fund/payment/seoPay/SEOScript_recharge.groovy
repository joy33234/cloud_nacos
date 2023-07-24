package com.seektop.fund.payment.seoPay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
import com.seektop.common.utils.MD5
import com.seektop.constant.FundConstant
import com.seektop.constant.ProjectConstant
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
 * @auth joy
 * @desc 质远支付
 */
class SEOScript_recharge {

    private static final Logger log = LoggerFactory.getLogger(SEOScript_recharge.class)

    private OkHttpUtil okHttpUtil


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
        if (merchant.getPaymentId() == FundConstant.PaymentType.QUICK_ALI_PAY) {
            payType = "10115"
        } else if (merchant.getPaymentId() == FundConstant.PaymentType.QUICK_WECHAT_PAY) {
            payType = "10118"
        }
        if (StringUtils.isNotEmpty(payType)) {
            prepareScan(merchant, payment, req, result, payType)
        }
    }

    private void prepareScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, RechargePrepareDO req, GlRechargeResult result, String payType) {
        Map<String, String> DataContentParms = new HashMap<String, String>()
        DataContentParms.put("merchantNo", payment.getMerchantCode())
        DataContentParms.put("signType", "md5")
        DataContentParms.put("outTradeNo", req.getOrderId())
        DataContentParms.put("notifyUrl", payment.getNotifyUrl() + merchant.getId())
        DataContentParms.put("fontUrl", payment.getNotifyUrl() + merchant.getId())
        DataContentParms.put("amount", req.getAmount().setScale(2, RoundingMode.DOWN) + "")
        DataContentParms.put("payment", payType)
        DataContentParms.put("member", req.getUserId().toString())
        if (req.getClientType() == ProjectConstant.ClientType.PC) {
            DataContentParms.put("sence", "page")
        } else {
            DataContentParms.put("sence", "wap")
        }

        String toSign = MD5.toAscii(DataContentParms) + payment.getPrivateKey()
        DataContentParms.put("sign", MD5.md5(toSign))
        DataContentParms.put("clientIp", req.getIp())

        log.info("SeoScript_Prepare_Params:{}", JSON.toJSONString(DataContentParms))
        GlRequestHeader requestHeader = getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode(), payment.getChannelId(), payment.getChannelName())
        String restr = okHttpUtil.post(payment.getPayUrl() + "/pay/platform", DataContentParms, 30L, requestHeader)
        log.info("SeoScript_Prepare_resStr:{}", restr)

        JSONObject json = JSON.parseObject(restr);
        if (json == null) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("网络请求超时，稍后重试")
            return
        }
        JSONObject dataJSON = json.getJSONObject("data")
        if (dataJSON == null || json.getString("code") != "1000") {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg(json.getString("message"))
            return
        }
       result.setRedirectUrl(dataJSON.getString("result"))
        result.setThirdOrderId(dataJSON.getString("transNo"))

    }

    public static void main(String[] args) {
        Map<String, String> params = new HashMap<String, String>()
        params.put("1","2");

        String a = params.remove("1");
        log.info(a)
    }

    RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount payment = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>
        log.info("SeoScript_Notify_resMap:{}", JSON.toJSONString(resMap))
        JSONObject jsonObject = JSON.parseObject(resMap.get("reqBody"));

        Map<String, String> params = new HashMap<String, String>()
        for (Map.Entry<String, String> entry : jsonObject.entrySet()) {
            params.put(entry.getKey(), entry.getValue())
        }
        String originalSign = params.remove("signValue");
        String calculateSign = MD5.md5(MD5.toAscii(params) + payment.getPrivateKey())
        if (StringUtils.equals(originalSign, calculateSign) && StringUtils.isNotEmpty(jsonObject.getString("transactTime")) ) {
            RechargeNotify pay = new RechargeNotify()
            pay.setAmount(jsonObject.getBigDecimal("amount").setScale(2, RoundingMode.DOWN))
            pay.setFee(BigDecimal.ZERO)
            pay.setOrderId(jsonObject.getString("outTradeNo"))
            pay.setRsp("SUCCESS")
            return pay
        }
        return null
    }

    RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil

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