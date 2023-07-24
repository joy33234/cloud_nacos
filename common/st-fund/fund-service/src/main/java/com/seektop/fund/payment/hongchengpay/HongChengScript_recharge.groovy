package com.seektop.fund.payment.hongchengpay

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
import org.apache.tomcat.util.codec.binary.Base64
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.util.ObjectUtils

import java.math.RoundingMode
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec

/**
 * @desc 鸿成支付
 * @auth joy
 * @date 2021-06-11
 */

public class HongChengScript_recharge {

    private static final Logger log = LoggerFactory.getLogger(HongChengScript_recharge.class)

    private OkHttpUtil okHttpUtil

    public RechargeNotify result(Object[] args) throws GlobalException {
        return notify(args)
    }

    public void pay(Object[] args) {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantApp merchant = args[1] as GlPaymentMerchantApp
        GlPaymentMerchantaccount account = args[2] as GlPaymentMerchantaccount
        RechargePrepareDO req = args[3] as RechargePrepareDO
        GlRechargeResult result = args[4] as GlRechargeResult
        
        Map<String, String> params = new TreeMap<>()
        params.put("account_id", account.getMerchantCode())
        params.put("content_type", "json")
        params.put("thoroughfare", "service_auto")
        params.put("out_trade_no", req.getOrderId())
        params.put("robin", "2")
        params.put("callback_url", account.getNotifyUrl() + merchant.getId())
        params.put("success_url", account.getNotifyUrl() + merchant.getId())
        params.put("error_url", account.getNotifyUrl() + merchant.getId())
        params.put("type", "3")
        params.put("amount", req.getAmount().setScale(2, RoundingMode.DOWN) + "")
        log.info("toSing:{},key:{}", MD5.toAscii(params),account.getPrivateKey())

        String sign =  signSHA1(MD5.toAscii(params).getBytes(),  account.getPrivateKey())
        log.info("sign:{}",sign)
        params.put("sign", URLEncoder.encode(sign,"utf8"))
        log.info("HongChengScript_recharge_prepare_params = {}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode(), account.getChannelId(), account.getChannelName())

        String payUrl = account.getPayUrl() + "/gateway/index/checkpoint.do"
        String resStr = okHttpUtil.post(payUrl, params, 30L, requestHeader)
        log.info("HongChengScript_recharge_prepare_resp = {}", resStr)
        if (StringUtils.isEmpty(resStr)) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("网络请求超时，稍后重试")
            return
        }
        JSONObject json = JSON.parseObject(resStr)
        if ("200" != (json.getString("code")) ) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg(json.getString("msg"))
            return
        }
        JSONObject dataJSON = json.getJSONObject("data")

        BankInfo bankInfo = new BankInfo()
        bankInfo.setName(dataJSON.getString("name"))
        bankInfo.setBankId(-1)
        bankInfo.setBankName(dataJSON.getString("bank_name"))
        bankInfo.setBankBranchName(dataJSON.getString("bank_bm"))
        bankInfo.setCardNo(dataJSON.getString("bank_id"))
        result.setBankInfo(bankInfo)
    }

    public RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>
        log.info("HongChengScript_notify_resp:{}", JSON.toJSONString(resMap))
        String orderId = resMap.get("out_trade_no")
        String thirdOrderId = resMap.get("trade_no")
        BigDecimal realAmount = new BigDecimal(resMap.get("amount"))
        if (StringUtils.isNotEmpty(orderId) && StringUtils.isNotEmpty(thirdOrderId) && !ObjectUtils.isEmpty(realAmount)) {
            return this.payQuery(okHttpUtil, account, orderId,thirdOrderId, realAmount)
        }
        return null
    }

    public RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String
        String thirdOrderId = args[3] as String
        BigDecimal realAmount = args[4] as BigDecimal

        Map<String, String> params = new LinkedHashMap<>()
        params.put("ddh", orderId)

        log.info("HongChengScript_query_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                getRequestHeard(null, null, orderId, GlActionEnum.RECHARGE_QUERY.getCode(), account.getChannelId(), account.getChannelName())

        String queryUrl = account.getPayUrl() + "/server/api/orderQuery"
        String resStr = okHttpUtil.post(queryUrl, params,  requestHeader)
        log.info("HongChengScript_query_resp:{}", resStr)
        if (StringUtils.isEmpty(resStr)) {
            return null
        }
        JSONObject json = JSON.parseObject(resStr)
        if (ObjectUtils.isEmpty(json) || "200" != (json.getString("code"))) {
            return null
        }
        // 订单状态：2未支付 3订单超时 4 订单已支付
        if ("4" == json.getString("msg")) {
            RechargeNotify pay = new RechargeNotify()
            pay.setAmount(realAmount.setScale(2, RoundingMode.UP))
            pay.setFee(BigDecimal.ZERO)
            pay.setOrderId(orderId)
            pay.setThirdOrderId(thirdOrderId)
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


    public static String signSHA1(byte[] data, String privateKey) throws Exception {
        byte[] keyBytes = org.apache.commons.codec.binary.Base64.decodeBase64(privateKey);
        PKCS8EncodedKeySpec pkcs8KeySpec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PrivateKey privateK = keyFactory.generatePrivate(pkcs8KeySpec);
        Signature signature = Signature.getInstance("SHA1WithRSA");
        signature.initSign(privateK);
        signature.update(data);
        return Base64.encodeBase64String(signature.sign());
    }

}