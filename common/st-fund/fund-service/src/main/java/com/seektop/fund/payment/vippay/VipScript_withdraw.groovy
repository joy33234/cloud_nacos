package com.seektop.fund.payment.vippay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
import com.seektop.common.utils.MD5
import com.seektop.enumerate.GlActionEnum
import com.seektop.exception.GlobalException
import com.seektop.fund.business.GlPaymentChannelBankBusiness
import com.seektop.fund.model.GlWithdraw
import com.seektop.fund.model.GlWithdrawMerchantAccount
import com.seektop.fund.payment.WithdrawNotify
import com.seektop.fund.payment.WithdrawResult
import com.seektop.fund.payment.groovy.BaseScript
import com.seektop.fund.payment.groovy.ResourceEnum
import org.apache.commons.lang3.ObjectUtils
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode

/**
 * @desc  VIP支付
 * @auth joy
 * @date 2021-06-25
 */
class VipScript_withdraw {

    private static final Logger log = LoggerFactory.getLogger(VipScript_withdraw.class)

    private OkHttpUtil okHttpUtil

    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness


    WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

        Map<String, String> paramMap = new LinkedHashMap<>()
        paramMap.put("pay_customer_id", merchantAccount.getMerchantCode())
        paramMap.put("pay_apply_date", System.currentTimeSeconds().toString())
        paramMap.put("pay_order_id", req.getOrderId())
        paramMap.put("pay_notify_url", merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId())
        paramMap.put("pay_amount", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString())
        paramMap.put("pay_account_name", req.getName())
        paramMap.put("pay_card_no", req.getCardNo())
        paramMap.put("pay_bank_name", req.getBankName())
        paramMap.put("pay_sub_branch", "上海市")
        paramMap.put("pay_city", "上海市")

        String toSign = MD5.toAscii(paramMap) + "&key=" + merchantAccount.getPrivateKey()
        paramMap.put("pay_md5_sign", MD5.md5(toSign).toUpperCase())

        log.info("VipScript_Transfer_Params: {}", JSON.toJSONString(paramMap))
        GlRequestHeader requestHeader =
                getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(), merchantAccount.getChannelId(), merchantAccount.getChannelName())
        String resStr = okHttpUtil.postJSON(merchantAccount.getPayUrl() + "/api/payments/pay_order", JSON.toJSONString(paramMap),  requestHeader)
        log.info("VipScript_Transfer_resStr: {}", resStr)

        WithdrawResult result = new WithdrawResult()
        result.setOrderId(req.getOrderId())
        result.setReqData(JSON.toJSONString(paramMap))
        result.setResData(resStr)

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
        JSONObject dataJSON = json.getJSONObject("data")
        if (dataJSON == null) {
            result.setValid(false)
            result.setMessage(json == null ? "API异常:请联系出款商户确认订单." : json.getString("message"))
            return result
        }
        result.setValid(true)
        result.setMessage(json.getString("msg"))
        result.setThirdOrderId(dataJSON.getString("transaction_id"))
        return result
    }

    WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        Map<String, String> resMap = args[2] as Map<String, String>
        log.info("VipScript_Transfer_Notify_resMap:{}", JSON.toJSONString(resMap))
        JSONObject json = JSONObject.parseObject(resMap.get("reqBody"));
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
        Map<String, String> paramMap = new LinkedHashMap<>()
        paramMap.put("pay_customer_id", merchant.getMerchantCode())
        paramMap.put("pay_apply_date", System.currentTimeSeconds().toString())
        paramMap.put("pay_order_id", orderId)

        String toSign = MD5.toAscii(paramMap) + "&key=" + merchant.getPrivateKey()
        paramMap.put("pay_md5_sign", MD5.md5(toSign).toUpperCase())

        log.info("VipScript_Transfer_Query_Params: {}", JSON.toJSONString(paramMap))
        GlRequestHeader requestHeader =
                getRequestHeard(null, null, orderId, GlActionEnum.WITHDRAW_QUERY.getCode(), merchant.getChannelId(), merchant.getChannelName())
        String resStr = okHttpUtil.postJSON(merchant.getPayUrl() + "/api/payments/query_transaction", JSON.toJSONString(paramMap),  requestHeader)
        log.info("VipScript_Transfer_Query_resStr: {}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null ||  json.getString("code") != "0") {
            return null
        }

        JSONObject dataJSON = json.getJSONObject("data")
        if (dataJSON == null) {
            return null
        }
        //0 : 未处理    1：处理中    2:已打款   3：已驳回冲正   4：核实不成功  5：余额不足
        Integer refCode = dataJSON.getInteger("status")
        WithdrawNotify notify = new WithdrawNotify()
        notify.setAmount(dataJSON.getBigDecimal("amount"))
        notify.setMerchantCode(merchant.getMerchantCode())
        notify.setMerchantId(merchant.getMerchantId())
        notify.setMerchantName(merchant.getChannelName())
        notify.setOrderId(orderId)
        if (refCode == 2) {
            notify.setStatus(0)
            notify.setRsp("OK")
        } else if (refCode == 3) {
            notify.setStatus(1)
            notify.setRsp("OK")
        } else {
            notify.setStatus(2)
        }
        notify.setThirdOrderId(dataJSON.getString("payment_id"))
        return notify
    }


    BigDecimal balanceQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount

        Map<String, String> paramMap = new LinkedHashMap<>()
        paramMap.put("pay_customer_id", merchantAccount.getMerchantCode())
        paramMap.put("pay_apply_date", System.currentTimeSeconds())

        String toSign = MD5.toAscii(paramMap) + "&key=" + merchantAccount.getPrivateKey()
        paramMap.put("pay_md5_sign", MD5.md5(toSign).toUpperCase())

        log.info("VipScript_Query_Balance_Params: {}", JSON.toJSONString(paramMap))
        GlRequestHeader requestHeader =
                getRequestHeard(null, null, null, GlActionEnum.PAYMENT_QUERY_BALANCE.getCode(),merchantAccount.getChannelId(), merchantAccount.getChannelName())
        String resStr = okHttpUtil.postJSON(merchantAccount.getPayUrl() + "/api/payments/balance", JSON.toJSONString(paramMap),  requestHeader)
        log.info("VipScript_Query_Balance_resStr: {}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || json.getString("code") != "0") {
            return BigDecimal.ZERO
        }
        JSONObject dataJSON = json.getJSONObject("data")
        if (ObjectUtils.isNotEmpty(dataJSON)) {
            return dataJSON.getBigDecimal("balance") == null ? BigDecimal.ZERO : dataJSON.getBigDecimal("balance");
        }
        return  BigDecimal.ZERO
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
}