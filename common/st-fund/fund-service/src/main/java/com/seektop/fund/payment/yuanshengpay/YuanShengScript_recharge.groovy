package com.seektop.fund.payment.yuanshengpay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
import com.seektop.common.utils.HtmlTemplateUtils
import com.seektop.common.utils.MD5
import com.seektop.constant.FundConstant
import com.seektop.enumerate.GlActionEnum
import com.seektop.exception.GlobalException
import com.seektop.fund.model.GlPaymentMerchantApp
import com.seektop.fund.model.GlPaymentMerchantaccount
import com.seektop.fund.payment.GlRechargeResult
import com.seektop.fund.payment.RechargeNotify
import com.seektop.fund.payment.RechargePrepareDO
import org.apache.commons.codec.binary.Base64
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.util.ObjectUtils

import java.math.RoundingMode
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec

/**
 * @desc 源盛支付
 * @date 2021-06-10
 * @auth joy
 */
public class YuanShengScript_recharge {


    private static final Logger log = LoggerFactory.getLogger(YuanShengScript_recharge.class)

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
        String payType = ""
        if (FundConstant.PaymentType.ALI_TRANSFER == merchant.getPaymentId()) {
            payType = "1"//支付宝转帐，
        } else if (FundConstant.PaymentType.BANKCARD_TRANSFER == merchant.getPaymentId()) {
            payType = "3"//卡卡
        }
        if (StringUtils.isEmpty(payType)) {
            result.setErrorMsg("支付方式不支持")
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            return
        }
        prepareToScan(merchant, account, req, result, payType)
    }


    private void prepareToScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result, String payType) throws GlobalException {
        Map<String, String> params = new LinkedHashMap<>()
        //公共参数
        params.put("charset", "1")
        params.put("accessType", "1")
        params.put("merchantId", account.getMerchantCode())
        params.put("signType", "3")
        params.put("notifyUrl", account.getNotifyUrl() + merchant.getId())
        params.put("pageUrl", account.getNotifyUrl() + merchant.getId())
        params.put("version", "v1.0")
        params.put("language", "1")
        params.put("timestamp", System.currentTimeMillis().toString())

        //业务参数
        params.put("order_id",req.getOrderId())
        params.put("amount", req.getAmount().multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.DOWN).toString())
        params.put("amt_type","CNY")
        params.put("card_Type","1")
        params.put("gate_id","1")
        params.put("goods_id","recharge")
        params.put("phone","13611111111")

        Map<String, String> params2 = new LinkedHashMap<>()
        for (Map.Entry<String,String> param : params.entrySet()) {
            params2.put(param.getKey(),URLEncoder.encode(param.getValue(), "UTF-8"))
        }
        String sign = signSHA1(MD5.toAscii(params2).getBytes(), account.getPrivateKey())
        params.put("signMsg", sign)

        log.info("YuanShengScript_recharge_prepare_params:{}", JSON.toJSONString(params))
        String formStr = HtmlTemplateUtils.getPost(account.getPayUrl() + "/svrmer/tdpay-web-mer-portal/tdpay/umpay/aliPay.do", params);
        result.setMessage(formStr);
    }



    public RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>
        log.info("YuanShengScript_notify_resp:{}", JSON.toJSONString(resMap))
        String orderId = resMap.get("orderNo")
        if (StringUtils.isNotEmpty(orderId)) {
            return this.payQuery(okHttpUtil, account, orderId, args[4])
        }
        return null
    }

    public RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String

        Map<String, String> params = new LinkedHashMap<>()
        //公共参数
        params.put("charset", "utf-8")
        params.put("accessType", "1")
        params.put("merchantId", account.getMerchantCode())
        params.put("signType", "3")
        params.put("version", "v1.0")
        params.put("language", "1")
        params.put("timestamp", System.currentTimeMillis().toString())

        //业务参数
        params.put("order_id", orderId)

        Map<String, String> params2 = new LinkedHashMap<>()
        for (Map.Entry<String,String> param : params.entrySet()) {
            params2.put(param.getKey(),URLEncoder.encode(param.getValue(), "UTF-8"))
        }
        String sign = signSHA1(MD5.toAscii(params2).getBytes(), account.getPrivateKey())
        params.put("signMsg", sign)

        log.info("YuanShengScript_query_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = getRequestHeard(null, null, orderId, GlActionEnum.RECHARGE_QUERY.getCode(), account.getChannelId(), account.getChannelName())
        String resStr = okHttpUtil.post(account.getPayUrl() + "/svrmer/tdpay-web-mer-portal/tdpay/umpay/qryOrdSts.do", params, requestHeader)
        log.info("YuanShengScript_query_resp:{}", resStr)
        if (StringUtils.isEmpty(resStr)) {
            return null
        }
        JSONObject json = JSON.parseObject(resStr)
        if (ObjectUtils.isEmpty(json) || "01000000" != json.getString("rspCod")) {
            return null
        }
        // 00-成功  01-失败  02-未支付
        if ("00" == (json.getString("trade_state"))) {
            RechargeNotify pay = new RechargeNotify()
            pay.setAmount(json.getBigDecimal("trade_amt").divide(BigDecimal.valueOf(100)).setScale(2, RoundingMode.DOWN))
            pay.setFee(BigDecimal.ZERO)
            pay.setOrderId(orderId)
            pay.setThirdOrderId(json.getString("log_no"))
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


    public static String signSHA1(byte[] data, String privateKey) throws Exception {
        byte[] keyBytes = Base64.decodeBase64(privateKey);
        PKCS8EncodedKeySpec pkcs8KeySpec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PrivateKey privateK = keyFactory.generatePrivate(pkcs8KeySpec);
        Signature signature = Signature.getInstance("SHA1WithRSA");
        signature.initSign(privateK);
        signature.update(data);
        return Base64.encodeBase64URLSafeString(signature.sign());
    }
}