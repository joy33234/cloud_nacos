package com.seektop.fund.payment.gtV3PayPay

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
class GtV3PayScript_recharge {

    private static final Logger log = LoggerFactory.getLogger(GtV3PayScript_recharge.class)

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
            payType = "10115"  //支付宝H5

        } else if (merchant.getPaymentId() == FundConstant.PaymentType.ALI_PAY) {
            payType = "10115"  //支付宝H5

        }else if (merchant.getPaymentId() == FundConstant.PaymentType.QUICK_WECHAT_PAY) {
            payType = "10118"  //微信H5
        }
        else if (merchant.getPaymentId() == FundConstant.PaymentType.WECHAT_PAY) {
            payType = "10118"  //微信H5

        }else if( merchant.getPaymentId() == FundConstant.PaymentType.BANKCARD_TRANSFER){
            payType = "10119"     //卡转卡

        }

        if (StringUtils.isEmpty(payType)) {
            result.setErrorMsg("支付方式不支持")
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            return
        }
        prepareScan(merchant, payment, req, result, payType )
    }

    private void prepareScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, RechargePrepareDO req, GlRechargeResult result, String payType ) {

        String payUrl = "/pay/platform"

        Map<String, String> DataContentParms = new HashMap<String, String>()
        DataContentParms.put("merchantNo", payment.getMerchantCode())
        DataContentParms.put("signType", "md5")
        DataContentParms.put("outTradeNo", req.getOrderId())
        DataContentParms.put("notifyUrl", payment.getNotifyUrl() + merchant.getId())
        DataContentParms.put("amount", req.getAmount().setScale(2, RoundingMode.DOWN) + "")
        DataContentParms.put("payment", payType)

        if(merchant.getPaymentId() != FundConstant.PaymentType.BANKCARD_TRANSFER){
            DataContentParms.put("fontUrl", payment.getNotifyUrl() + merchant.getId()) //卡转卡，fontUrl 不加入签名
            DataContentParms.put("member",  req.getUsername())

        }else{
            DataContentParms.put("member",  req.getFromCardUserName())
            payUrl = "/pay/netbank" //银行卡另外接口
        }

        if (req.getClientType() == ProjectConstant.ClientType.PC) {
            DataContentParms.put("sence", "page")
        } else {
            DataContentParms.put("sence", "wap")
        }

        String toSign = MD5.toAscii(DataContentParms) + payment.getPrivateKey()
        DataContentParms.put("sign", MD5.md5(toSign))
        DataContentParms.put("clientIp", req.getIp())
        DataContentParms.put("fontUrl", payment.getNotifyUrl() + merchant.getId())

        log.info("GtV3PayScript_Prepare_Params:{},url:{}", JSON.toJSONString(DataContentParms) , payment.getPayUrl() + payUrl)
        GlRequestHeader requestHeader = getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode(), payment.getChannelId(), payment.getChannelName())
        String restr = okHttpUtil.post(payment.getPayUrl() + payUrl , DataContentParms, 30L, requestHeader)
        log.info("GtV3PayScript_Prepare_resStr:{},orderId:{}", restr ,req.getOrderId() )

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
        if (StringUtils.isEmpty(dataJSON.getString("result"))) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg(StringUtils.isEmpty(dataJSON.getString("message")) ? "支付商家未出码，请先使用别的充值方式或稍候再支付" : dataJSON.getString("message") )
            return
        }

        result.setRedirectUrl(dataJSON.getString("result"))
        result.setThirdOrderId(dataJSON.getString("transNo"))

    }

    RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount payment = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>
        log.info("GtV3PayScript_Notify_resMap:{}", JSON.toJSONString(resMap))
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
        log.info("GtV3PayScript_Notify_Sign 回调有误或验签失败 orderId :{}", params.get("outTradeNo") )
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