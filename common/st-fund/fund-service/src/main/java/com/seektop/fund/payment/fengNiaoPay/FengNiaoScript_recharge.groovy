package com.seektop.fund.payment.fengNiaoPay

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
import org.springframework.util.ObjectUtils

import java.math.RoundingMode
/**
 * @desc 蜂鸟支付
 * @date 2021-12-04
 * @auth Otto
 */
public class FengNiaoScript_recharge {


    private static final Logger log = LoggerFactory.getLogger(FengNiaoScript_recharge.class)

    private OkHttpUtil okHttpUtil
    private  final String PAY_URL =  "/?c=Pay&"
    private  final String QUERY_URL =  "/?c=Pay&a=query&"

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
        if (FundConstant.PaymentType.ALI_PAY == merchant.getPaymentId()) {
            payType = "18"  //支付宝口令红包
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
        params.put("order_sn",req.getOrderId())
        params.put("ptype", payType )
        params.put("money", req.getAmount().setScale(2, RoundingMode.DOWN).toString())
        params.put("goods_desc", "recharge")
        params.put("client_ip", req.getIp())
        params.put("format", "url")
        params.put("notify_url", account.getNotifyUrl() + merchant.getId())
        params.put("time",System.currentTimeSeconds()+"")

        String toSign = MD5.toAscii(params) + "&key=" + account.getPrivateKey();
        params.put("sign", MD5.md5(toSign))

        log.info("FengNiaoScript_recharge_prepare_params:{} , url:{} ", JSON.toJSONString(params) , account.getPayUrl()+PAY_URL)
        GlRequestHeader requestHeader =
                getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode(), account.getChannelId(), account.getChannelName())

        String resStr = okHttpUtil.post(  account.getPayUrl()+ PAY_URL, params, requestHeader)
        log.info("FengNiaoScript_recharge_prepare_resp:{}", resStr)
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
        result.setRedirectUrl(dataJSON.getString("qrcode"))
    }


    public RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>
        log.info("FengNiaoScript_notify_resp:{}", JSON.toJSONString(resMap))

        String orderId = resMap.get("sh_order")
        String thirdSign = resMap.get("sign")

        Map<String, String> signMap = new LinkedHashMap<>();
        signMap.put("money",resMap.get("money"))
        signMap.put("pt_order",resMap.get("pt_order"))
        signMap.put("sh_order",orderId)
        signMap.put("time",resMap.get("time"))
        signMap.put("status",resMap.get("status"))

        String sign = MD5.toAscii(signMap) + "&key=" + account.getPrivateKey();
        sign = MD5.md5(sign);

        if (StringUtils.isNotEmpty(orderId) && sign == thirdSign ) {
            return this.payQuery(okHttpUtil, account, orderId )
        }
        log.info("FengNiaoScript_notify_Sign:回調錯誤或驗簽失敗 ,orderId:{}",orderId )

        return null
    }

    public RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String


        Map<String, String> params = new LinkedHashMap<>()
        params.put("mch_id", account.getMerchantCode())
        params.put("out_order_sn", orderId)
        params.put("time", System.currentTimeSeconds().toString())

        String sign = MD5.toAscii(params) + "&key=" + account.getPrivateKey();
        sign = MD5.md5(sign);
        params.put("sign", sign)

        log.info("FengNiaoScript_query_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = getRequestHeard(null, null, orderId, GlActionEnum.RECHARGE_QUERY.getCode(), account.getChannelId(), account.getChannelName())
        String resStr = okHttpUtil.post(account.getPayUrl() + QUERY_URL , params, requestHeader)
        log.info("FengNiaoScript_query_resp:{}", resStr)
        if (StringUtils.isEmpty(resStr)) {
            return null
        }
        JSONObject json = JSON.parseObject(resStr)
        if (json == null || json.getString("code") != "1") {
            return null
        }
        JSONObject dataJSON = json.getJSONObject("data")
        //  status = 9 为支付成功
        if (!ObjectUtils.isEmpty(dataJSON) && "9" == dataJSON.getString("status")) {
            RechargeNotify pay = new RechargeNotify()
            pay.setAmount(dataJSON.getBigDecimal("money").setScale(2, RoundingMode.DOWN))
            pay.setFee(BigDecimal.ZERO)
            pay.setThirdOrderId(dataJSON.getString("order_sn"))
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

}