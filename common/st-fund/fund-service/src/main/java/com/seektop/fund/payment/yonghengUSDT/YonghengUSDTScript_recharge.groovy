package com.seektop.fund.payment.yonghengUSDT

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
import com.seektop.fund.payment.BlockInfo
import com.seektop.fund.payment.GlRechargeResult
import com.seektop.fund.payment.RechargeNotify
import com.seektop.fund.payment.RechargePrepareDO
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode

class YonghengUSDTScript_recharge {

    private static final Logger log = LoggerFactory.getLogger(YonghengUSDTScript_recharge.class)

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
        prepareScan(merchant, payment, req, result)
    }

    private void prepareScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, RechargePrepareDO req, GlRechargeResult result) {

        JSONObject params = new JSONObject(new LinkedHashMap())
        params.put("pay_customer_id", payment.getMerchantCode())
        params.put("pay_apply_date", System.currentTimeMillis() / 1000 + "")
        params.put("pay_order_id", req.getOrderId())
        params.put("pay_notify_url", payment.getNotifyUrl() + merchant.getId())
        params.put("pay_amount", req.getAmount().setScale(2, RoundingMode.DOWN).toString())
        params.put("pay_channel_id", "84")

        String toSign = params.toJSONString() + payment.getPrivateKey()
        params.put("pay_md5_sign", MD5.md5(toSign))

        params.put("pay_product_name", "recharge")
        params.put("user_name", req.getFromCardUserName())

        log.info("YonghengUSDTScript_Prepare_Params = {}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode(), payment.getChannelId(), payment.getChannelName())

        String restr = okHttpUtil.postJSON(payment.getPayUrl() + "/api/pay_order", params.toJSONString(), requestHeader)
        JSONObject json = JSON.parseObject(restr)
        log.info("YonghengUSDTScript_Prepare_Resp = {}", json)
        if (json == null) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("网络请求超时，稍后重试")
            return
        }
        if (json.getString("code") != "0") {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg(json.getString("msg"))
            return
        }
        JSONObject dataJSON = json.getJSONObject("data")

        BlockInfo blockInfo = new BlockInfo()
        blockInfo.setOwner(dataJSON.getString("bank_owner"))
        blockInfo.setDigitalAmount(dataJSON.getBigDecimal("display_price"))
        blockInfo.setProtocol(req.getProtocol())
        blockInfo.setBlockAddress(dataJSON.getString("bank_no"))
        blockInfo.setRate(dataJSON.getBigDecimal("rate"))
        blockInfo.setExpiredDate(dataJSON.getDate("expired"))
        result.setThirdOrderId(dataJSON.getString("transaction_id"))
        result.setBlockInfo(blockInfo)
    }

    RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount payment = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>
        log.info("YonghengUSDTScript_Notify_resMap = {}", JSON.toJSONString(resMap))
        JSONObject json = JSON.parseObject(resMap.get("reqBody"));
        String orderid = json.getString("order_id")
        if (StringUtils.isNotEmpty(orderid)) {
            return payQuery(okHttpUtil, payment, orderid, args[4])
        }
        return null
    }

    RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount payment = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String

        JSONObject params = new JSONObject(new LinkedHashMap())
        params.put("pay_customer_id", payment.getMerchantCode())
        params.put("pay_apply_date", System.currentTimeMillis() / 1000 + "")
        params.put("pay_order_id", orderId)

        String toSign = params.toJSONString() + payment.getPrivateKey()
        params.put("pay_md5_sign", MD5.md5(toSign))

        String queryUrl = payment.getPayUrl() + "/api/query_transaction"
        log.info("YonghengUSDTScript_Query_Params = {}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = getRequestHeard(null, null, orderId, GlActionEnum.RECHARGE_QUERY.getCode(), payment.getChannelId(), payment.getChannelName())
        String result = okHttpUtil.postJSON(queryUrl, params.toJSONString(), requestHeader)
        log.info("YonghengUSDTScript_Query_resStr = {}", result)

        JSONObject json = JSON.parseObject(result)
        if (json == null || "0" != json.getString("code")) {
            return null
        }

        JSONObject dataJSON = json.getJSONObject("data")
        //0 未处理 1 成功，未返回 2 成功，已返回 3 失败，逾期失效 4 失败，订单⾦额不相符 5 失败，订单异常
        if (dataJSON == null || (dataJSON.getInteger("status") != 1 && dataJSON.getInteger("status") != 2)) {
            return null
        }
        RechargeNotify pay = new RechargeNotify()
        BigDecimal rate = dataJSON.getJSONObject("rc_feedback").getBigDecimal("rate")
        pay.setAmount(dataJSON.getBigDecimal("real_amount").multiply(rate).setScale(2, RoundingMode.DOWN))
        pay.setFee(BigDecimal.ZERO)
        pay.setOrderId(orderId)
        pay.setThirdOrderId(dataJSON.getString("transaction_id"))
        pay.setRsp("OK")
        return pay
    }


    void cancel(Object[] args) throws GlobalException {

    }


    private static GlRequestHeader getRequestHeard(String userId, String userName, String orderId, String code, int channelId, String channelName) {
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
        return FundConstant.ShowType.DIGITAL
    }
}