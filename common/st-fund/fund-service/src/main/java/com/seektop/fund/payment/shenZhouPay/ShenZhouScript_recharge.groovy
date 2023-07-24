package com.seektop.fund.payment.shenZhouPay

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
import com.seektop.fund.payment.BankInfo
import com.seektop.fund.payment.GlRechargeResult
import com.seektop.fund.payment.RechargeNotify
import com.seektop.fund.payment.RechargePrepareDO
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode
/**
 * @desc 神州支付
 * @date 2021-12-08
 * @auth Otto
 */
public class ShenZhouScript_recharge {

    private static final Logger log = LoggerFactory.getLogger(ShenZhouScript_recharge.class)

    private OkHttpUtil okHttpUtil

    private  final String PAY_URL =  "/api/pay/unifiedorder"
    private  final String QUERY_URL =  "/api/pay/search"

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
            payType = "card" //卡卡
        }
        if (StringUtils.isEmpty(payType)) {
            result.setErrorMsg("支付方式不支持")
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            return
        }
        prepareToScan(merchant, account, req, result, payType)
    }


    private void prepareToScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result, String payType) throws GlobalException {

        String nonce_str = System.currentTimeMillis()+"" ;
        Map<String, String> params = new HashMap<>()
        params.put("appid", account.getMerchantCode())
        params.put("order_sn", req.getOrderId())
        params.put("pay_amount", req.getAmount().setScale(2, RoundingMode.DOWN).toString())
        params.put("pay_username", req.getFromCardUserName())
        params.put("product_name", payType)
        params.put("product_desc", "pay")
        params.put("notifyurl", account.getNotifyUrl() + merchant.getId())
        params.put("callbackurl", account.getNotifyUrl() )
        params.put("nonce_str", nonce_str )

        String toSign = account.getMerchantCode() + account.getPrivateKey() + nonce_str
        params.put("signature", MD5.md5(toSign))

        log.info("ShenZhouScript_recharge_prepare_Params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode(), account.getChannelId(), account.getChannelName())
        String resStr = okHttpUtil.post(account.getPayUrl() + PAY_URL, params, requestHeader)
        log.info("ShenZhouScript_recharge_prepare_resp:{} , orderId :{}", resStr , req.getOrderId() )
        if (StringUtils.isEmpty(resStr)) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("网络请求超时，稍后重试")
            return
        }
        JSONObject json = JSONObject.parseObject(resStr)
        if (json == null || "1" != json.getString("code")) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg(json == null  ? "通道出错，请更换金额或重试" : json.getString("msg"))
            return
        }
        JSONObject dataJSON = json.getJSONObject("data");
        if (dataJSON == null) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg(json.getString("msg"))
            return
        }

        BankInfo bankInfo = new BankInfo()
        bankInfo.setName(dataJSON.getString("accountname"))
        bankInfo.setBankId(-1)
        bankInfo.setBankName(dataJSON.getString("bankname"))
        bankInfo.setCardNo(dataJSON.getString("cardnumber"))
        result.setBankInfo(bankInfo)

    }

    public RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>

        log.info("ShenZhouScript_notify_resp:{}", JSON.toJSONString(resMap))
        String orderId = resMap.get("order_sn")

        String sign =  resMap.get("appid")  + account.getPrivateKey() + resMap.get("nonce_str")
        sign = MD5.md5(sign);
        if (StringUtils.isNotEmpty(orderId) && sign ==  resMap.get("signature") ) {
            return this.payQuery(okHttpUtil, account, orderId)
        }

        log.info("ShenZhouScript_notify_Sign: 回调资料错误或验签失败，单号：{}" , orderId )

        return null
    }

    public RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String
        String noncceStr =  System.currentTimeMillis()+"" ;

        Map<String, String> params = new HashMap<>()
        params.put("appid", account.getMerchantCode())
        params.put("order_sn", orderId)
        params.put("nonce_str", noncceStr)

        String toSign = account.getMerchantCode() + account.getPrivateKey() + noncceStr
        params.put("signature", MD5.md5(toSign))

        log.info("ShenZhouScript_query_headParams:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = getRequestHeard(null, null, orderId, GlActionEnum.RECHARGE_QUERY.getCode(), account.getChannelId(), account.getChannelName())
        String resStr = okHttpUtil.post(account.getPayUrl() + QUERY_URL , params , 30l , requestHeader)
        log.info("ShenZhouScript_query_resp:{}", resStr)
        if (StringUtils.isEmpty(resStr)) {
            return null
        }
        JSONObject json = JSON.parseObject(resStr)
        if (json == null || "1" != json.getString("code")) {
            return null
        }
        JSONObject dataJSON = json.getJSONObject("data")
        // 1：支付成功,0：未支付
        if ("1" == (dataJSON.getString("status"))) {
            RechargeNotify pay = new RechargeNotify()
            pay.setAmount(dataJSON.getBigDecimal("money").setScale(2, RoundingMode.DOWN))
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
        return true
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
        return FundConstant.ShowType.DETAIL
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