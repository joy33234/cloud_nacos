package com.seektop.fund.payment.cuicanxingPay

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
 * @desc 璀璨星支付
 * @date 2022-03-29
 * @auth Redan
 */
public class CuicanxingScript_recharge {

    private static final Logger log = LoggerFactory.getLogger(CuicanxingScript_recharge.class)
    private OkHttpUtil okHttpUtil
    private final String Pay_URL =  "/api/order/placeOrder"
    private final String QUERY_URL =  "/api/order/queryOrder"

    private String getSign( Map<String, Object> params,String key){
        String toSign = MD5.toAscii(params) + "&secretKey=" + key
        String data_sign = MD5.md5(toSign)
        return data_sign.toLowerCase();
    }

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

        if (FundConstant.PaymentType.ALI_PAY == merchant.getPaymentId()) {
            gateway = "0" //支付宝
        }else if (FundConstant.PaymentType.UNIONPAY_SACN == merchant.getPaymentId()) {
            gateway = "2" // 银联扫码支付
        }else if (FundConstant.PaymentType.BANKCARD_TRANSFER == merchant.getPaymentId()) {
            gateway = "6" //卡转卡
        }else if (FundConstant.PaymentType.WECHAT_PAY == merchant.getPaymentId()) {
            gateway = "10" //wechat
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

        params.put("merchno", account.getMerchantCode())
        params.put("orderId",req.getOrderId())
        params.put("payType", gateway);
        params.put("amount", req.getAmount().setScale(2, RoundingMode.DOWN).toString())
        params.put("requestCurrency", "1")
        params.put("asyncUrl", account.getNotifyUrl() + merchant.getId())
        params.put("syncUrl", account.getNotifyUrl() + merchant.getId())
        params.put("requestTime", DateUtils.format(new Date(), DateUtils.YYYYMMDDHHMMSS));
        params.put("apiVersion", "2")
        params.put("sign",  getSign(params,account.getPrivateKey()))

        log.info("CuicanxingScript_recharge_prepare_params:{}", JSON.toJSONString(params))
        String resStr = okHttpUtil.post(account.getPayUrl()+Pay_URL, params)
        log.info("CuicanxingScript_recharge_prepare_resp:{}  , orderId:{}", resStr ,req.getOrderId())

        if (StringUtils.isEmpty(resStr)) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("网络请求超时，稍后重试")
            return
        }
        result.setMessage(resStr)

    }

    public RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[2] as GlPaymentMerchantaccount

        Map<String, String> resMap = args[3] as Map<String, String>
        log.info("CuicanxingScript_notify_resp:{}", JSON.toJSON(resMap))

        String orderId = resMap.get("orderId") ;
        if (StringUtils.isNotEmpty(orderId)) {
            return this.payQuery(okHttpUtil, account, orderId ) ;
        }
        log.info("CuicanxingScript_notify_Sign: 回调资料错误或验签失败，orderId ：{}" , orderId )
        return null
    }

    public RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String

        Map<String, String> params = new LinkedHashMap<>()
        params.put("merchno", account.getMerchantCode())
        params.put("orderId",orderId)
        params.put("requestTime", DateUtils.format(new Date(), DateUtils.YYYYMMDDHHMMSS));
        params.put("apiVersion", "2")
        params.put("sign", getSign(params,account.getPrivateKey()))

        log.info("CuicanxingScript_query_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = getRequestHeard(null, null, orderId, GlActionEnum.RECHARGE_QUERY.getCode(), account.getChannelId(), account.getChannelName())
        String resStr = okHttpUtil.post(account.getPayUrl() + QUERY_URL, params, requestHeader)

        log.info("CuicanxingScript_query_resp:{}", resStr)
        JSONObject json = JSON.parseObject(resStr)
        if (json == null ) {
            return null
        }
        // 未支付: 0
        //支付成功: 2
        //失败: 3
        if ("2" == (json.getJSONObject("content").getString("status"))) {
            log.info("CuicanxingScript_query_resp_done:{}",json)
            RechargeNotify pay = new RechargeNotify()
            pay.setAmount(json.getJSONObject("content").getBigDecimal("amount").setScale(2, RoundingMode.DOWN))
            pay.setFee(BigDecimal.ZERO)
            pay.setOrderId(json.getJSONObject("content").getString("orderId"))
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