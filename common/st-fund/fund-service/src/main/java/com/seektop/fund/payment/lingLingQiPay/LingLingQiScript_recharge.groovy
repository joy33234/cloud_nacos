package com.seektop.fund.payment.lingLingQiPay

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
 * @desc 007支付
 * @date 2021-10-16
 * @auth otto
 */
public class LingLingQiScript_recharge {

    private static final Logger log = LoggerFactory.getLogger(LingLingQiScript_recharge.class)
    private OkHttpUtil okHttpUtil

    private  final String PAY_URL =  "/api/order/pay"
    private  final String QUERY_URL =  "/api/query/order"

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
            gateway = "303" //卡转卡
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
        params.put("biz_code", account.getMerchantCode())
        params.put("biz_order_code",req.getOrderId())
        params.put("pass_code", gateway )
        params.put("order_amount", req.getAmount().setScale(2, RoundingMode.DOWN).toString())
        params.put("notify_url", account.getNotifyUrl() + merchant.getId() )
        params.put("user_name", req.getFromCardUserName() )
        params.put("user_note", "remark" )

        String toSign = MD5.toAscii(params) + "&key=" + account.getPrivateKey() ;
        params.put("sign",  MD5.md5(toSign).toUpperCase())

        log.info("LingLingQi_recharge_prepare_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode(), account.getChannelId(), account.getChannelName())

        String resStr = okHttpUtil.postJSON(account.getPayUrl() + PAY_URL, JSON.toJSONString(params), requestHeader)
        log.info("LingLingQi_recharge_prepare_resp:{}", resStr)

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

        //200：请求成功
        if (200 != json.getInteger("status")  ) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg(json.getString("msg"))
            return
        }

        BankInfo bankInfo = new BankInfo();
        bankInfo.setName(json.getString("account_name"))
        bankInfo.setBankId(-1)
        bankInfo.setBankName(json.getString("bank_name"))
        bankInfo.setBankBranchName("")
        bankInfo.setCardNo(json.getString("account_no"))
        result.setBankInfo(bankInfo)

    }

    public RechargeNotify notify(Object[] args) throws GlobalException {
        log.info("LingLingQi_notify_args:{}", args)

        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>
        log.info("LingLingQi_notify_resp:{}", resMap)

        JSONObject json = JSONObject.parseObject(resMap.get("reqBody"));
        Map<String, Object> map = (Map<String, Object>) json
        String sign = map.get("sign");
        map.remove("sign");

        String md5Sign = MD5.toAscii(map) + "&key=" + account.getPrivateKey();
        md5Sign = MD5.md5(md5Sign).toUpperCase();
        String orderId = json.getString("biz_order_code")

        if (StringUtils.isNotEmpty(orderId) && sign == md5Sign) {
            return this.payQuery(okHttpUtil, account, orderId)

        }
        return null
    }

    public RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String

        Map<String, String> params = new LinkedHashMap<>()
        params.put("biz_code", account.getMerchantCode())
        params.put("biz_order_code",orderId)

        String md5Sign = MD5.toAscii(params) + "&key=" + account.getPrivateKey();
        params.put("sign", MD5.md5(md5Sign).toUpperCase())

        log.info("LingLingQi_query_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = getRequestHeard(null, null, orderId, GlActionEnum.RECHARGE_QUERY.getCode(), account.getChannelId(), account.getChannelName())
        String resStr = okHttpUtil.postJSON(account.getPayUrl() + QUERY_URL, JSON.toJSONString(params), requestHeader)

        log.info("LingLingQi_query_resp:{}", resStr)
        if (StringUtils.isEmpty(resStr)) {
            return null
        }

        JSONObject json = JSON.parseObject(resStr)
        if (json == null  || "200" != json.getString("status") ) {
            return null
        }

        // 订单状态：2=待付款  3=付款成功  4=超时关闭  5=付款成功但回调失败
        if ( "3" == json.getString("order_status") || "5" == json.getString("order_status")  ) {
            RechargeNotify pay = new RechargeNotify()
            pay.setAmount(json.getBigDecimal("order_actual"))
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