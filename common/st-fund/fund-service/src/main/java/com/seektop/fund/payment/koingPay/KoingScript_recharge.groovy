package com.seektop.fund.payment.koingPay

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
import org.apache.commons.codec.digest.HmacUtils
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.util.ObjectUtils

import java.math.RoundingMode

/**
 * Koing支付
 * @auth  joy
 * @date 2021-07-29
 */

class KoingScript_recharge {

    private static final Logger log = LoggerFactory.getLogger(KoingScript_recharge.class)

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

        prepare(merchant, payment, req, result)
    }

    private void prepare(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, RechargePrepareDO req, GlRechargeResult result) {

        Map<String, String> params = new HashMap<>()
        params.put("merchantId", payment.getMerchantCode());
        params.put("merchantOrderId", req.getOrderId());
        params.put("merchantUserId", req.getUserId().toString());
        params.put("method", "BANK_TRANSFER");
        params.put("amount", req.getAmount().setScale(2, RoundingMode.DOWN).toString());
        params.put("remark", "recharge");
        params.put("redirectUrl", payment.getNotifyUrl() + merchant.getId());
        params.put("callbackUrl", payment.getNotifyUrl() + merchant.getId());
        params.put("nonce", UUID.randomUUID().toString());
        params.put("depositName", req.getFromCardUserName());

        params.put("signature", calculateSignature(payment.getPrivateKey(), params));
        
        log.info("KoingScript_Prepare_Params: {}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode(), payment.getChannelId(), payment.getChannelName())
        String restr = okHttpUtil.post(payment.getPayUrl() + "/deposit", params, 30L, requestHeader)
        log.info("KoingScript_Prepare_Resp: {}", restr)
        JSONObject json = JSONObject.parseObject(restr);
        if (json == null) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("网络请求超时，稍后重试")
            return
        }
        if (StringUtils.isEmpty(json.getString("status")) || StringUtils.isEmpty(json.getString("paymentUrl"))) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg("三方返回数据错误")
            return
        }
       result.setRedirectUrl(json.getString("paymentUrl"))
    }

    RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount payment = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>
        log.info("KoingScript_Notify_resMap:{}", JSON.toJSONString(resMap))
        JSONObject json = JSON.parseObject(resMap.get("reqBody"));
        String orderid = json.getString("merchantOrderId")
        if (StringUtils.isNotEmpty(orderid) ) {
            return payQuery(okHttpUtil, payment, orderid, args[4])
        }
        return null
    }

    RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount payment = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String

        Map<String, String> paramMap = new HashMap<>()
        paramMap.put("merchantId", payment.getMerchantCode())
        paramMap.put("merchantOrderId", orderId)
        paramMap.put("nonce", UUID.randomUUID().toString());
        paramMap.put("signature", calculateSignature(payment.getPrivateKey(), paramMap));
        log.info("KoingScript_Query_Params: {}", JSON.toJSONString(paramMap))
        GlRequestHeader requestHeader =
                getRequestHeard(null, null, orderId, GlActionEnum.RECHARGE_QUERY.getCode(), payment.getChannelId(), payment.getChannelName())
        String result = okHttpUtil.get(payment.getPayUrl() + "/deposit", paramMap, 30L, requestHeader)
        log.info("KoingScript_Query_resStr: {}", result)

        JSONObject json = JSON.parseObject(result)
        if (json == null) {
            return null
        }
        //SUCCESS  ， PENDING
        String Status = json.getString("status")
        if ("SUCCESS" != Status) {
            return null
        }
        RechargeNotify pay = new RechargeNotify()
        pay.setAmount(json.getBigDecimal("amount").setScale(2, RoundingMode.DOWN))
        pay.setFee(BigDecimal.ZERO)
        pay.setOrderId(orderId)
        pay.setRsp("200")
        return pay
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


    /**
     * 获取头部信息
     *
     * @param userId
     * @param userName
     * @param orderId
     * @return
     */
    private GlRequestHeader getRequestHeard(String userId, String userName, String orderId, String code, Integer channelId, String channleName) {
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(code)
                .channelId(channelId + "")
                .channelName(channleName)
                .userId(userId)
                .userName(userName)
                .tradeId(orderId)
                .build()
        return requestHeader
    }

    /**
     * 计算消息签名
     *
     * @param merchantSecretKey 商户密钥，开户后由在线支付系统提供
     * @param messageData       发送或者接收到的消息中的所有参数
     * @return 计算出的签名结果
     */
    public String calculateSignature(String merchantSecretKey,
                                            Map<String, String> messageData) {
        List<Map.Entry<String, Object>> infoIds = new ArrayList<>(messageData.entrySet())
        // 对所有传入参数按照字段名的 ASCII 码从小到大排序（字典序）
        Collections.sort(infoIds, Comparator.comparing({ o -> (o as Map.Entry).getKey() }))
        StringBuffer sign = new StringBuffer()
        for (Map.Entry<String, Object> item : infoIds) {
            String k = item.getKey()
            if (!org.springframework.util.StringUtils.isEmpty(item.getKey())) {
                Object v = item.getValue()
                if (null != v && !ObjectUtils.isEmpty(v)) {
                    sign.append( v + "|")
                }
            }
        }
        // 调用 HMAC_SHA256 算法计算签名
        return new HmacUtils("HmacSHA256", merchantSecretKey).hmacHex(sign.deleteCharAt(sign.length() - 1).toString());
    }
}