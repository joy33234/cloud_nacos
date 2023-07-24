package com.seektop.fund.payment.yingfupay

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
import com.seektop.fund.payment.GlRechargeResult
import com.seektop.fund.payment.RechargeNotify
import com.seektop.fund.payment.RechargePrepareDO
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode


/**
 * 盈富支付 - 新系统v2
 * @author joy
 * @date 2021-05-05
 */
public class YingfuScriptV2_recharge {

    private static final Logger log = LoggerFactory.getLogger(YingfuScriptV2_recharge.class)

    private OkHttpUtil okHttpUtil


    public RechargeNotify result(Object[] args) throws GlobalException {
        return null
    }

    public void pay(Object[] args) {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantApp merchant = args[1] as GlPaymentMerchantApp
        GlPaymentMerchantaccount account = args[2] as GlPaymentMerchantaccount
        RechargePrepareDO req = args[3] as RechargePrepareDO
        GlRechargeResult result = args[4] as GlRechargeResult

        String productId = "";
        if (merchant.getPaymentId() == FundConstant.PaymentType.BANKCARD_TRANSFER) {
            productId = "8029"
        }
        if (StringUtils.isEmpty(productId)) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("支付方式不支持，请更换")
            return
        }
        prepareToScan(merchant, account, req, result, productId)
    }

    private void prepareToScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result, String productId)  {
        Map<String, String> params = new HashMap<>()
        params.put("mchId", account.getMerchantCode())
        params.put("appId", account.getPublicKey())
        params.put("productId", productId)
        params.put("mchOrderNo", req.getOrderId())
        params.put("currency", "cny")
        params.put("amount", req.getAmount().multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.DOWN).toString())
        params.put("notifyUrl", account.getNotifyUrl() + merchant.getId())
        params.put("subject", "recharge")
        params.put("body", "recharge")

        String toSign = MD5.toAscii(params) + "&key=" + account.getPrivateKey()
        params.put("sign", MD5.md5(toSign).toUpperCase())

        GlRequestHeader requestHeader = getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode(), account.getChannelId(), account.getChannelName())
        log.info("YingfuScript_recharge_prepare_params = {}", JSONObject.toJSONString(params))
        String resStr = okHttpUtil.post(account.getPayUrl() + "/api/pay/create_order", params, requestHeader)
        log.info("YingfuScript_recharge_prepare_resp = {}", resStr)

        JSONObject json = JSONObject.parseObject(resStr)
        if (json == null) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("网络请求超时，稍后重试")
            return
        }
        if ("SUCCESS" != (json.getString("retCode")) || StringUtils.isEmpty(json.getString("payUrl"))) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg(json.getString("errDes") == null ? "创建订单失败" : json.getString("errDes"))
            return
        }
        result.setRedirectUrl(json.getString("payUrl"))
    }


    public RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>
        log.info("YingfuScript_notify = {}", JSONObject.toJSONString(resMap))
        String orderId = resMap.get("mchOrderNo")
        if (StringUtils.isNotEmpty(orderId)) {
            return this.payQuery(okHttpUtil, account, orderId)
        }
        return null
    }

    public RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String
        Map<String, String> params = new HashMap<>()
        params.put("mchId", account.getMerchantCode())
        params.put("appId", account.getPublicKey())
        params.put("mchOrderNo", orderId)
        String toSign = MD5.toAscii(params) + "&key=" + account.getPrivateKey()
        params.put("sign", MD5.md5(toSign).toUpperCase())

        log.info("YingfuScript_query_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = this.getRequestHeard("", "", orderId, GlActionEnum.RECHARGE_QUERY.getCode(), account.getChannelId(), account.getChannelName())

        String resp = okHttpUtil.post(account.getPayUrl() + "/api/pay/query_order", params, requestHeader)
        log.info("YingfuScript_query_resp:{}", resp)

        JSONObject json = JSONObject.parseObject(resp)
        if (json == null ||  "SUCCESS" != json.getString("retCode")) {
            return null
        }
        //支付状态,0-订单生成, 1-支付中, 2-支付成功, 3-业务处理完成
        if ("2" == json.getString("status") ) {
            RechargeNotify pay = new RechargeNotify()
            pay.setAmount(json.getBigDecimal("amount").divide(BigDecimal.valueOf(100)).setScale(2, RoundingMode.DOWN))
            pay.setFee(BigDecimal.ZERO)
            pay.setOrderId(orderId)
            pay.setThirdOrderId(json.getString("payOrderId"))
            return pay
        }
        return null
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

    /**
     * 获取头部信息
     *
     * @param userId
     * @param userName
     * @param orderId
     * @return
     */
    private GlRequestHeader getRequestHeard(String userId, String userName, String orderId, String code, Integer channelId, String channelName) {
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