package com.seektop.fund.payment.wanlianPay

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
import org.apache.commons.codec.digest.HmacUtils
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.util.ObjectUtils

import java.math.RoundingMode
/**
 * @desc 万联支付
 * @date 2021-09-17
 * @auth otto
 */
public class WanLianScript_recharge {

    private static final Logger log = LoggerFactory.getLogger(WanLianScript_recharge.class)
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
        String gateway = ""
        if (FundConstant.PaymentType.BANKCARD_TRANSFER == merchant.getPaymentId()) {
            gateway = "1"
        }else if(FundConstant.PaymentType.ALI_PAY == merchant.getPaymentId()){
            gateway = "3"
        }else if(FundConstant.PaymentType.WECHAT_PAY == merchant.getPaymentId()){
            gateway = "2"
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
        params.put("app_id", account.getMerchantCode())
        params.put("user_id", req.getUserId()+"")
        params.put("payer_name", req.getUsername())
        params.put("customer_order_no",req.getOrderId())
        params.put("payment_type", gateway)
        params.put("cash", req.getAmount().setScale(2, RoundingMode.DOWN).toString())
        params.put("callback_url", account.getNotifyUrl() + merchant.getId())
        params.put("timestamp", System.currentTimeSeconds()+"")

        String toSign = MD5.toAscii(params) + "&key=" + account.getPrivateKey()
        println("toSign:"+toSign)
        String data_sign = MD5.md5(toSign)

        params.put("data_sign", data_sign)

        log.info("WanLianScript_recharge_prepare_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode(), account.getChannelId(), account.getChannelName())

        String resStr = okHttpUtil.post(account.getPayUrl() + "/integration/interface/create_task_order", params, requestHeader)

        log.info("WanLianScript_recharge_prepare_resp:{}", resStr)

        if (StringUtils.isEmpty(resStr)) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("网络请求超时，稍后重试")
            return
        }
        JSONObject json = JSONObject.parseObject(resStr)
        if (json == null ) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg("网络请求超时，稍后重试")
            return
        }
        JSONObject dataJSON = json.getJSONObject("data");
        if (dataJSON == null || StringUtils.isEmpty(dataJSON.getString("pay_address"))) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg(json.getString("description"))
            return
        }
        result.setRedirectUrl(dataJSON.getString("pay_address"))
        result.setThirdOrderId(dataJSON.getString("order_code"))  //放三方的訂單號
    }

    public RechargeNotify notify(Object[] args) throws GlobalException {
        log.info("WanLianScript_notify_args:{}", args)
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>
        log.info("WanLianScript_notify_resp:{}", resMap)
        JSONObject json = JSON.parseObject(resMap.get("reqBody"));
        String orderId = json.getJSONObject("customer_order_no").toString()
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
        params.put("app_id", account.getMerchantCode())
        params.put("timestamp", System.currentTimeSeconds()+"")
        params.put("customer_order_no",orderId)

        String toSign = MD5.toAscii(params) + "&key=" + account.getPrivateKey()
        params.put("data_sign", MD5.md5(toSign))


        log.info("WanLianScript_query_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = getRequestHeard(null, null, orderId, GlActionEnum.RECHARGE_QUERY.getCode(), account.getChannelId(), account.getChannelName())
        String resStr = okHttpUtil.post(account.getPayUrl() + "/integration/interface/get_task_order_status", params, requestHeader)

        log.info("WanLianScript_query_resp:{}", resStr)
        if (StringUtils.isEmpty(resStr)) {
            return null
        }
        JSONObject json = JSON.parseObject(resStr)
        if (json == null  || ObjectUtils.isEmpty(json.getJSONObject("data"))) {
            return null
        }
        JSONObject dataJSON = json.getJSONObject("data");
        //waiting            已创建，等待付款，订单处理中
        //completed          已完成，成功支付
        //cancelled          已取消, 未支付的，在有效期内，未核实到款项
        //not_found          不存在，未创建成功的，未查询到订单
        if (!ObjectUtils.isEmpty(dataJSON) && ("completed" == (dataJSON.getString("status")))) {
            RechargeNotify pay = new RechargeNotify()
            pay.setAmount(dataJSON.getBigDecimal("cash").setScale(2, RoundingMode.DOWN))
            pay.setFee(BigDecimal.ZERO)
            pay.setOrderId(orderId)
            pay.setRsp("{\"data\": {\"success\": true}} ")
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
        return false  //: false 走 submit 接口(跳轉本司充值頁面)  / true 走 transfer(跳轉收銀台)
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

    public String sign (String secretKey, String data) { // 利用 apache 工具类 HmacUtils
        byte[] bytes = HmacUtils.hmacSha1(secretKey, data);
        return Base64.getEncoder().encodeToString(bytes);
    }

}