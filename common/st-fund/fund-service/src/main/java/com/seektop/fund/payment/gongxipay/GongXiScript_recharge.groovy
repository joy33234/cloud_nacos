package com.seektop.fund.payment.gongxipay

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
import com.seektop.fund.payment.henglixingwxpay.Base64Util
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.util.ObjectUtils

import java.math.RoundingMode

/**
 * @desc 恭喜支付
 * @date 2021-09-15
 * @auth joy
 */
public class GongXiScript_recharge {


    private static final Logger log = LoggerFactory.getLogger(GongXiScript_recharge.class)

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
        if (FundConstant.PaymentType.QUICK_WECHAT_PAY == merchant.getPaymentId()) {
            payType = "2"
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
        params.put("mch_id", account.getMerchantCode())
        params.put("pay_name", req.getFromCardUserName())

        //相容极速微信通道
        if( merchant.getRemark().contains("微信") && merchant.getRemark().contains("红包") && FundConstant.PaymentType.QUICK_WECHAT_PAY == merchant.getPaymentId() ){
            params.put("ptype", "13" ) //微信群红包

        }else{
            params.put("ptype", payType )

        }
        params.put("order_sn",req.getOrderId())
        params.put("money", req.getAmount().setScale(2, RoundingMode.DOWN).toString())
        params.put("goods_desc", "recharge")
        params.put("client_ip", req.getIp())
        params.put("format", "json")
        params.put("notify_url", account.getNotifyUrl() + merchant.getId())
        params.put("time", req.getCreateDate().getTime().toString())

        String toSign = URLDecoder.decode(MD5.toAscii(params), "utf-8") + "&key=" + account.getPrivateKey();
        params.put("sign", MD5.md5(toSign))

        String crypted = getCrypted(JSON.toJSONString(params), account.getPublicKey().replaceAll(" ",""));
        log.info("crypted:{}",crypted)

        log.info("GongXiScript_recharge_prepare_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode(), account.getChannelId(), account.getChannelName())

        String resStr = okHttpUtil.postJSON(account.getPayUrl() + ":7898/?c=Pay&crypted=" + crypted, JSON.toJSONString(params), requestHeader)
        log.info("GongXiScript_recharge_prepare_resp:{}", resStr)
        if (StringUtils.isEmpty(resStr)) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("网络请求超时，稍后重试")
            return
        }
        JSONObject json = JSONObject.parseObject(resStr)
        if (json == null || "1" != json.getString("code")) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg(json.getString("msg"))
            return
        }
        JSONObject dataJSON = json.getJSONObject("data");
        if (ObjectUtils.isEmpty(dataJSON)) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg(json.getString("msg"))
            return
        }
        result.setRedirectUrl(dataJSON.getString("pay_url"))
    }


    public RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>
        log.info("GongXiScript_notify_resp:{}", JSON.toJSONString(resMap))

        String orderId = resMap.get("sh_order")
        if (StringUtils.isEmpty(orderId)) {
            JSONObject json = JSON.parseObject(resMap.get("reqBody"));
            orderId = json.getString("sh_order")
        }
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
        params.put("mch_id", account.getMerchantCode())
        params.put("out_order_sn", orderId)
        params.put("time", System.currentTimeMillis().toString())
        params.put("sign", MD5.md5(MD5.toAscii(params) + "&key=" + account.getPrivateKey()))

        String crypted = getCrypted(JSON.toJSONString(params), account.getPublicKey().replaceAll(" ",""));
        log.info("crypted:{}",crypted)

        log.info("GongXiScript_query_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = getRequestHeard(null, null, orderId, GlActionEnum.RECHARGE_QUERY.getCode(), account.getChannelId(), account.getChannelName())
        String resStr = okHttpUtil.postJSON(account.getPayUrl() + ":7899/?c=Pay&a=query&crypted=" + crypted, JSON.toJSONString(params), requestHeader)
        log.info("GongXiScript_query_resp:{}", resStr)
        if (StringUtils.isEmpty(resStr)) {
            return null
        }
        JSONObject json = JSON.parseObject(resStr)
        if (json == null || json.getString("code") != "1") {
            return null
        }
        JSONObject dataJSON = json.getJSONObject("data")
        // 支付状态:status  status = 9 为支付成功
        if (!ObjectUtils.isEmpty(dataJSON) && "9" == dataJSON.getString("status")) {
            RechargeNotify pay = new RechargeNotify()
            pay.setAmount(dataJSON.getBigDecimal("money").setScale(2, RoundingMode.DOWN))
            pay.setFee(BigDecimal.ZERO)
            pay.setOrderId(orderId)
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


    private static String getCrypted(String jsonStr, String publickey) {
        String data = Base64Util.encode(jsonStr.getBytes())
        byte[] arr = com.seektop.fund.payment.lefupay.RSAUtils.encryptByPublicKey(data.getBytes(), publickey)
        return Base64Util.encode(arr)
    }
}