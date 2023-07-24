package com.seektop.fund.payment.kelepay

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

public class KeLeScript_recharge {

    private static final Logger log = LoggerFactory.getLogger(KeLeScript_recharge.class)

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

        String pay_type = "";
        if (merchant.getPaymentId() == FundConstant.PaymentType.BANKCARD_TRANSFER) {
            pay_type = "cardtocard"
        } else if (merchant.getPaymentId() == FundConstant.PaymentType.UNION_TRANSFER) {
            pay_type = "cloudtocard"
        }
        prepareToScan(merchant, account, req, result, pay_type)
    }

    private void prepareToScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result, String pay_type)  {
        Map<String, String> params = new HashMap<>()
        params.put("appid", account.getMerchantCode())
        params.put("pay_type", pay_type)
        params.put("out_trade_no", req.getOrderId())
        params.put("out_uid", req.getUserId().toString())
        params.put("amount", req.getAmount().setScale(2, RoundingMode.DOWN).toString())
        params.put("callback_url", account.getNotifyUrl() + merchant.getId())
        params.put("success_url", account.getNotifyUrl() + merchant.getId())
        params.put("error_url", account.getNotifyUrl() + merchant.getId())
        params.put("version", "v1.1")

        String toSign = MD5.toAscii(params) + "&key=" + account.getPrivateKey()
        params.put("sign", MD5.md5(toSign).toUpperCase())
        params.put("ip", req.getIp())

        GlRequestHeader requestHeader =
                getRequestHeard(req.getUserId().toString(), req.getUsername() ,req.getOrderId(), GlActionEnum.RECHARGE.getCode(), account.getChannelId(), account.getChannelName())

        log.info("KeleScrit_recharge_prepare_params = {}", JSONObject.toJSONString(params))
        String resStr = okHttpUtil.post(account.getPayUrl() + "/index/unifiedorder?format=json", params, requestHeader)
        log.info("KeleScrit_recharge_prepare_resp = {}", resStr)

        if (StringUtils.isEmpty(resStr)) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("网络请求超时，稍后重试")
            return
        }
        JSONObject json = JSONObject.parseObject(resStr)
        if (json.getString("code") != "200") {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg(json.getString("msg") == null ? "创建订单失败" : json.getString("msg"))
            return
        }
        JSONObject dataJSON = json.getJSONObject("data")
        if (dataJSON == null || StringUtils.isEmpty(json.getString("url"))) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg(json.getString("msg") == null ? "创建订单失败" : json.getString("msg"))
            return
        }
        result.setRedirectUrl(json.getString("url"))
    }


    public RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>
        log.info("KeleScrit_notify = {}", JSONObject.toJSONString(resMap))
        String orderId = resMap.get("coke_out_trade_no")
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
        params.put("appid", account.getMerchantCode())
        params.put("out_trade_no", orderId)
        String toSign = MD5.toAscii(params) + "&key=" + account.getPrivateKey()
        params.put("sign", MD5.md5(toSign).toUpperCase())

        log.info("KeleScrit_query_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                getRequestHeard("", "" ,orderId , GlActionEnum.RECHARGE_QUERY.getCode(), account.getChannelId(), account.getChannelName())

        String resp = okHttpUtil.post(account.getPayUrl() + "/index/getorder", params, requestHeader)
        log.info("KeleScrit_query_resp:{}", resp)

        JSONObject json = JSONObject.parseObject(resp)
        if (json == null ||  "200" != json.getString("code")) {
            return null
        }
        JSONObject dataJSON = json.getJSONArray("data").get(0);
        //支付状态,2：未支付 3：订单超时 4：支付完成
        if (dataJSON != null && "4" == dataJSON.getString("status") ) {
            RechargeNotify pay = new RechargeNotify()
            pay.setAmount(dataJSON.getBigDecimal("amount").setScale(2, RoundingMode.DOWN))
            pay.setFee(BigDecimal.ZERO)
            pay.setOrderId(orderId)
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