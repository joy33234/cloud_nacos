package com.seektop.fund.payment.yiBayPay

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
 * @desc 亿贝支付
 * @date 2021-10-21
 * @auth otto
 */
public class YiBayScript_recharge {

    private static final Logger log = LoggerFactory.getLogger(YiBayScript_recharge.class)
    private OkHttpUtil okHttpUtil

    private  final String PAY_URL =  "/app/selectGetQrcode"
    private  final String QUERY_URL =  "/app/checktheorder"

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
        if (FundConstant.PaymentType.BANKCARD_TRANSFER == merchant.getPaymentId()) {
            gateway = "2" //卡转卡
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
        String amount = req.getAmount().setScale(0, RoundingMode.DOWN).toString() ;
        String qrcode_type = "4" //固定值
        String merchanturl = account.getNotifyUrl() + merchant.getId() ;

        params.put("qrcode_type", qrcode_type)
        params.put("merchantnum",req.getOrderId())
        params.put("bzmoneyid", amount)
        params.put("loginname", account.getMerchantCode())
        params.put("merchanturl", merchanturl)
        params.put("qudao_type", gateway )
        params.put("playername", req.getFromCardUserName() )

        String toSign =  qrcode_type + req.getOrderId() + amount + account.getMerchantCode() + merchanturl + gateway+"{"+ account.getPrivateKey()+"}"
        params.put("sign",  MD5.md5(toSign))

        log.info("YiBayScript_recharge_prepare_params:{}", params)
        GlRequestHeader requestHeader =
                getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode(), account.getChannelId(), account.getChannelName())

        String resStr = okHttpUtil.post(account.getPayUrl() + PAY_URL, params, requestHeader)
        log.info("YiBayScript_recharge_prepare_resp:{} , orderId:{}" , resStr ,req.getOrderId() )

        if (StringUtils.isEmpty(resStr)) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("网络请求超时，稍后重试")
            return
        }

        JSONObject json = JSONObject.parseObject(resStr)
        if (json == null ) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg("api接口异常，稍后重试")
            return
        }

        //0：请求成功；1：请求失败
        if ("0" != json.getString("systate")  ) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg(json.getString("msg"))
            return
        }

        JSONObject dataJSON = json.getJSONObject("data");
        result.setRedirectUrl(dataJSON.get("url"))
        result.setThirdOrderId(dataJSON.get("ptorder"))

    }


    public RechargeNotify notify(Object[] args) throws GlobalException {

        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>

        log.info("YiBayScript_notify_resp:{}", resMap)
        JSONObject json = JSONObject.parseObject(resMap.get("reqBody"));
        String orderId = json.getString("merchantnum")

        String toSign = orderId + json.getString("turemoney") + json.getString("qrcode_type")  + json.getString("merchanturl") ;
        toSign = toSign + json.getString("ptorder") + json.getString("qudao_type") + "{" + account.getPrivateKey() +"}";

        if (StringUtils.isNotEmpty(orderId) && MD5.md5(toSign) == json.getString("sign") ) {
            return this.payQuery(okHttpUtil, account, orderId )

        } else {
            log.info("YiBayPayScript_WithdrawNotify_sign: 回调资料错误或验签失败，单号：{}" ,orderId)
            return null

        }
        return null
    }

    public RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String

        Map<String, String> params = new LinkedHashMap<>()
        params.put("loginname", account.getMerchantCode())
        params.put("merchantnum",orderId)

        log.info("YiBayScript_query_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = getRequestHeard(null, null, orderId, GlActionEnum.RECHARGE_QUERY.getCode(), account.getChannelId(), account.getChannelName())
        String resStr = okHttpUtil.post(account.getPayUrl() + QUERY_URL, params, requestHeader)

        log.info("YiBayScript_query_resp:{}", resStr)
        if (StringUtils.isEmpty(resStr)) {
            return null
        }

        JSONObject json = JSON.parseObject(resStr)
        if (json == null  || "0" != json.getString("systate") ) {
            return null
        }

        JSONObject dataJSON = json.getJSONObject("data");

        String toSign = dataJSON.getString("turemoney") + dataJSON.getString("merchantnum") +  dataJSON.getString("state") + dataJSON.getString("endtime") ;
        toSign = toSign + dataJSON.getString("qrcode_type") + dataJSON.getString("qudao_type")  + "{"+account.getPrivateKey()+"}" ;

        if( MD5.md5(toSign) != dataJSON.getString("sign")){
            log.info("YiBayScript_payQuery_sign:{} 验签失败 , 我方签名: {} , 三方签名:{} ",  orderId , MD5.md5(toSign) , dataJSON.getString("sign") )
            return null
        }

        // 0 未到账； 1：已到账； 2：失效订单
        if ( "1" == dataJSON.getString("state") ) {
            RechargeNotify pay = new RechargeNotify()
            pay.setAmount(dataJSON.getBigDecimal("turemoney").setScale(2, RoundingMode.DOWN))
            pay.setFee(BigDecimal.ZERO)
            pay.setOrderId(orderId)
            pay.setRsp("SUCCESS")
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

}