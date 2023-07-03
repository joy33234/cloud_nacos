package com.seektop.fund.payment.winnerpay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
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
 * Winnerpay 接口
 *
 * @author joy
 */
public class WinnerScript_Recharge {

    private static final Logger log = LoggerFactory.getLogger(WinnerScript_Recharge.class)

    private OkHttpUtil okHttpUtil

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
        GlPaymentMerchantApp merchant = args[1] as GlPaymentMerchantApp
        GlPaymentMerchantaccount merchantaccount = args[2] as GlPaymentMerchantaccount
        RechargePrepareDO req = args[3] as RechargePrepareDO
        GlRechargeResult result = args[4] as GlRechargeResult
        Map<String, Object> params = new HashMap<String, Object>()
        params.put("amount", req.getAmount().setScale(2, RoundingMode.DOWN) + "")
        params.put("out_trade_no", req.getOrderId())
        params.put("notify_url", merchantaccount.getNotifyUrl() + merchant.getId())
        params.put("paid_name", req.getFromCardUserName())

        Map<String, String> headParams = new HashMap<String, String>()
        headParams.put("Authorization", "Bearer " + merchantaccount.getPrivateKey())
        headParams.put("Accept", "application/json")
        headParams.put("content-type", "application/json")

        log.info("WinnerScript_Prepare_resMap = {}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = this.getRequestHeard(req.getUserId() + "", req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode(), merchant.getChannelId().toString(), merchant.getChannelName())
        String resStr = okHttpUtil.postJSON(merchantaccount.getPayUrl() + "/api/transaction", JSON.toJSONString(params), headParams, requestHeader)
        log.info("WinnerScript_Prepare_resStr = {}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("创建订单失败，稍后重试")
            return
        }
        String redirectUrl = json.getString("uri")
        String orderId = json.getString("out_trade_no")
        if (StringUtils.isEmpty(redirectUrl) || StringUtils.isEmpty(orderId) || orderId != (req.getOrderId())) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg(json.getString("message"))
            return
        }

        result.setThirdOrderId(json.getString("trade_no"))
        result.setRedirectUrl(redirectUrl)
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
        GlPaymentMerchantaccount account = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>
        log.info("WinnerScript_notify_resp = {}", JSON.toJSONString(resMap))
        JSONObject json = JSON.parseObject(resMap.get("reqBody"))
        String thirdOrderId = json.getString("trade_no")
        String orderId = json.getString("out_trade_no")
        if (StringUtils.isNotEmpty(orderId) && StringUtils.isNotEmpty(thirdOrderId)) {
            return this.payQuery(okHttpUtil, account, orderId)
        }
        return null
    }

    public RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String
        log.info("WinnerScript_query_params_orderId = {}", orderId)

        Map<String, String> headParams = new HashMap<String, String>()
        headParams.put("Authorization", "Bearer " + account.getPrivateKey())
        headParams.put("Accept", "application/json")

        GlRequestHeader requestHeader = this.getRequestHeard("", "", orderId, GlActionEnum.RECHARGE_QUERY.getCode(), account.getChannelId().toString(), account.getChannelName())

        String resStr = okHttpUtil.get(account.getPayUrl() + "/api/transaction/" + orderId, null, requestHeader, headParams)
        log.info("WinnerScript_query_resStr = {}", resStr)


        JSONObject json = JSON.parseObject(resStr)
        if (json == null) {
            return null
        }
        // 订单状态判断标准:  success => 成功  progress => 进行中 timeout => 逾时
        if (json.getString("status") == ("success")) {
            RechargeNotify pay = new RechargeNotify()
            pay.setAmount(json.getBigDecimal("amount"))
            pay.setFee(BigDecimal.ZERO)
            pay.setOrderId(orderId)
            pay.setThirdOrderId(json.getString("trade_no"))
            return pay
        }
        return null
    }


    /**
     * 解析页面跳转结果
     *
     * @param merchant
     * @param payment
     * @param resMap
     * @return
     */
    public RechargeNotify result(Object[] args) throws GlobalException {
        return notify(args)
    }


    /**
     * 签名
     *
     * @param value
     * @param accessToken
     * @return
     * @throws NoSuchAlgorithmException
     * @throws InvalidKeyException* @throws UnsupportedEncodingException
     */
    public String getSign(String value, String accessToken)
            throws NoSuchAlgorithmException, InvalidKeyException, UnsupportedEncodingException {
        Base64.Encoder encoder = Base64.getEncoder()
        Mac sha256 = Mac.getInstance("HmacSHA256")
        sha256.init(new SecretKeySpec(accessToken.getBytes("UTF8"), "HmacSHA256"))

        return encoder.encodeToString(sha256.doFinal(value.getBytes("UTF8")))
    }

    /**
     * 获取头部信息
     *
     * @param userId
     * @param userName
     * @param orderId
     * @return
     */
    private GlRequestHeader getRequestHeard(String userId, String userName, String orderId, String code, String channelId, String channelName) {
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(code)
                .channelId(channelId)
                .channelName(channelName)
                .userId(userId)
                .userName(userName)
                .tradeId(orderId)
                .build()
        return requestHeader
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
                || paymentId == FundConstant.PaymentType.ALI_TRANSFER) {
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
}
