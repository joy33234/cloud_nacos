package com.seektop.fund.payment.yonghengUSDT

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
import com.seektop.common.redis.RedisService
import com.seektop.common.utils.MD5
import com.seektop.constant.RedisKeyHelper
import com.seektop.enumerate.GlActionEnum
import com.seektop.exception.GlobalException
import com.seektop.fund.model.GlWithdraw
import com.seektop.fund.model.GlWithdrawMerchantAccount
import com.seektop.fund.payment.WithdrawNotify
import com.seektop.fund.payment.WithdrawResult
import com.seektop.fund.payment.groovy.ResourceEnum
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode

class YonghengUSDTScript_withdraw {

    private static final Logger log = LoggerFactory.getLogger(YonghengUSDTScript_withdraw.class)

    private OkHttpUtil okHttpUtil

    private RedisService redisService

    WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw

        this.redisService = com.seektop.fund.payment.groovy.BaseScript.getResource(args[3], ResourceEnum.RedisService) as RedisService

        BigDecimal usdtRate = redisService.get(RedisKeyHelper.WITHDRAW_USDT_RATE, BigDecimal.class);
        BigDecimal amount = req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN);
        BigDecimal usdtAmount = amount.divide(usdtRate, 2, RoundingMode.DOWN)

        JSONObject params = new JSONObject(new LinkedHashMap())
        params.put("pay_customer_id", merchantAccount.getMerchantCode())
        params.put("pay_apply_date", (System.currentTimeMillis() / 1000) + "")
        params.put("pay_order_id", req.getOrderId())
        params.put("pay_notify_url", merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId())
        params.put("pay_amount", usdtAmount.toString())

        String toSign = params.toJSONString() + merchantAccount.getPrivateKey()
        params.put("pay_md5_sign", MD5.md5(toSign))
        params.put("pay_account_name", req.getName())
        params.put("pay_card_no", req.getAddress())
        params.put("pay_bank_name", "USDT")

        log.info("YonghengUSDTScript_Transfer_Params = {}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(), merchantAccount.getChannelId(), merchantAccount.getChannelName())
        String resStr = okHttpUtil.postJSON(merchantAccount.getPayUrl() + "/api/payments/pay_order", params.toJSONString(), requestHeader)
        log.info("YonghengUSDTScript_Transfer_resStr = {}", resStr)

        WithdrawResult result = new WithdrawResult()
        result.setOrderId(req.getOrderId())
        result.setReqData(JSON.toJSONString(params))
        result.setResData(resStr)
        result.setRate(usdtRate)
        result.setUsdtAmount(usdtAmount)

        if (StringUtils.isEmpty(resStr)) {
            result.setValid(false)
            result.setMessage("API异常:请联系出款商户确认订单.")
            return result
        }

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || "0" != json.getString("code")) {
            result.setValid(false)
            result.setMessage(json == null ? "API异常:请联系出款商户确认订单." : json.getString("message"))
            return result
        }
        result.setValid(true)
        result.setMessage(json.getString("message"))
        result.setThirdOrderId(json.getJSONObject("data").getString("transaction_id"))
        return result
    }

    WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        Map<String, String> resMap = args[2] as Map<String, String>
        log.info("YonghengUSDTScript_Transfer_Notify_resMap = {}", JSON.toJSONString(resMap))
        JSONObject json = JSON.parseObject(resMap.get("reqBody"));
        String orderid = json.getString("order_id")// 商户订单号
        if (StringUtils.isNotEmpty(orderid)) {
            return withdrawQuery(okHttpUtil, merchant, orderid, args[3])
        }
        return null
    }

    WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2]

        JSONObject params = new JSONObject(new LinkedHashMap())
        params.put("pay_customer_id", merchant.getMerchantCode())
        params.put("pay_apply_date", (System.currentTimeMillis() / 1000) + "")
        params.put("pay_order_id", orderId)

        String toSign = params.toJSONString() + merchant.getPrivateKey()
        params.put("pay_md5_sign", MD5.md5(toSign))

        log.info("YonghengUSDTScript_Transfer_Query_Params = {}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                getRequestHeard(null, null, orderId, GlActionEnum.WITHDRAW_QUERY.getCode(), merchant.getChannelId(), merchant.getChannelName())
        String resStr = okHttpUtil.postJSON(merchant.getPayUrl() + "/api/payments/query_transaction", params.toJSONString(), requestHeader)
        log.info("YonghengUSDTScript_Transfer_Query_resStr = {}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || json.getString("code") == null || json.getString("code") != "0") {
            return null
        }
        JSONObject dataJSON = json.getJSONObject("data")
        if (dataJSON == null) {
            return null
        }
        //0:未处理  1:处理中  2:已打款  3:已驳回冲正  4:核实不成功  5:余额不⾜
        Integer status = dataJSON.getInteger("status")
        WithdrawNotify notify = new WithdrawNotify()
        notify.setAmount(json.getBigDecimal("amount"))
        notify.setActualAmount(json.getBigDecimal("amount"))
        notify.setMerchantCode(merchant.getMerchantCode())
        notify.setMerchantId(merchant.getMerchantId())
        notify.setMerchantName(merchant.getChannelName())
        notify.setOrderId(orderId)
        notify.setRemark(json.getString("message"))
        if (status == 2) {
            notify.setStatus(0)
            notify.setRsp("OK")
        } else if (status == 3 || status == 4 || status == 5) {
            notify.setStatus(1)
        } else {
            notify.setStatus(2)
        }
        notify.setThirdOrderId(dataJSON.getString("payment_id"))
        return notify
    }


    BigDecimal balanceQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount

        JSONObject params = new JSONObject(new LinkedHashMap())
        params.put("pay_customer_id", merchantAccount.getMerchantCode())
        params.put("pay_apply_date", System.currentTimeMillis() / 1000 + "")

        String toSign = params.toJSONString() + merchantAccount.getPrivateKey()
        params.put("pay_md5_sign", MD5.md5(toSign))


        log.info("YonghengUSDTScript_Query_Balance_Params = {}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                getRequestHeard(null, null, null, GlActionEnum.PAYMENT_QUERY_BALANCE.getCode(), merchantAccount.getChannelId(), merchantAccount.getChannelName())
        String resStr = okHttpUtil.postJSON(merchantAccount.getPayUrl() + "/api/payments/balance", params.toJSONString(), requestHeader)
        log.info("YonghengUSDTScript_Query_Balance_resStr = {}", resStr)


        JSONObject json = JSON.parseObject(resStr)
        if (json == null || json.getInteger("code") != 0) {
            return BigDecimal.ZERO
        }
        BigDecimal balance = json.getJSONObject("data").getBigDecimal("balance")
        return balance == null ? BigDecimal.ZERO : balance
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


}