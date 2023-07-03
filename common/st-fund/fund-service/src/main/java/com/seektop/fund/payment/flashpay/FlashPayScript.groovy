package com.seektop.fund.payment.flashpay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
import com.seektop.common.utils.DateUtils
import com.seektop.common.utils.MD5
import com.seektop.enumerate.GlActionEnum
import com.seektop.exception.GlobalException
import com.seektop.fund.business.GlPaymentChannelBankBusiness
import com.seektop.fund.model.GlWithdraw
import com.seektop.fund.model.GlWithdrawMerchantAccount
import com.seektop.fund.payment.PaymentMerchantEnum
import com.seektop.fund.payment.RechargeNotify
import com.seektop.fund.payment.WithdrawNotify
import com.seektop.fund.payment.WithdrawResult
import com.seektop.fund.payment.groovy.BaseScript
import com.seektop.fund.payment.groovy.ResourceEnum
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.util.ObjectUtils

import java.math.RoundingMode

public class FlashPayScript {

    private static final Logger log = LoggerFactory.getLogger(FlashPayScript.class)

    private OkHttpUtil okHttpUtil

    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness


    void pay(Object[] args) {

    }


    RechargeNotify notify(Object[] args) throws GlobalException {
        return null
    }


    RechargeNotify payQuery(Object[] args) throws GlobalException {
        return null
    }


    RechargeNotify result(Object[] args) throws GlobalException {
        return notify(args)
    }

    WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        GlWithdraw withdraw = args[2] as GlWithdraw
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

        Map<String, Object> params = new HashMap<>()
        params.put("amount", withdraw.getAmount().subtract(withdraw.getFee()).setScale(2, RoundingMode.DOWN))
        params.put("app_id", merchantAccount.getMerchantCode())
        params.put("merchant_order_id", withdraw.getOrderId())
        params.put("notify_url", merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId())
        params.put("receive_bank_name", withdraw.getBankName())
        params.put("receive_card_holder", withdraw.getName())
        params.put("receive_card_number", withdraw.getCardNo())
        params.put("receive_sub_bank_name", "上海市")

        String toSign = this.toAscii(params) + "&key=" + merchantAccount.getPrivateKey()
        params.put("sign", MD5.md5(toSign))
        log.info("FlashPayScript_doTransfer_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.WITHDRAW.getCode())
                .channelId(PaymentMerchantEnum.FLASH_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.FLASH_PAY.getPaymentName())
                .userId(withdraw.getUserId() + "")
                .userName(withdraw.getUsername())
                .tradeId(withdraw.getOrderId())
                .build()
        String resStr = okHttpUtil.postJSON(merchantAccount.getPayUrl() + "/api/create_withdraw", JSON.toJSONString(params), requestHeader)
        log.info("FlashPayScript_doTransfer_resp:{}", resStr)

        WithdrawResult result = new WithdrawResult()
        result.setOrderId(withdraw.getOrderId())
        result.setReqData(JSON.toJSONString(params))
        result.setResData(resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null) {
            result.setValid(false)
            result.setMessage("API异常:请联系出款商户确认订单.")
            return result
        }
        if (json.getString("status") != ("1")) {
            result.setValid(false)
            result.setMessage(json.getString("message"))
            return result
        }
        result.setValid(true)
        result.setMessage("")
        return result
    }

    public WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        Map<String, String> resMap = args[2] as Map<String, String>
        log.info("FlashPayScript_doTransferNotify_resp:{}", JSON.toJSONString(resMap))
        JSONObject json = JSONObject.parseObject(resMap.get("reqBody"))
        if (json != null && StringUtils.isNotEmpty(json.getString("merchant_order_id"))) {
            return this.withdrawQuery(okHttpUtil, merchant, json.getString("merchant_order_id"))
        }
        return null
    }

    WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2]
        Map<String, Object> params = new HashMap<>()
        params.put("app_id", merchant.getMerchantCode())
        params.put("merchant_order_id", orderId)

        String toSign = this.toAscii(params) + "&key=" + merchant.getPrivateKey()
        params.put("sign", MD5.md5(toSign))

        log.info("FlashPayScript_doTransferQuery_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.WITHDRAW_QUERY.getCode())
                .channelId(PaymentMerchantEnum.FLASH_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.FLASH_PAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId(orderId)
                .build()
        String resStr = okHttpUtil.postJSON(merchant.getPayUrl() + "/api/query_withdraw", JSON.toJSONString(params), requestHeader)
        log.info("FlashPayScript_doTransferQuery_resp:{}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null) {
            return null
        }
        WithdrawNotify notify = new WithdrawNotify()
        notify.setMerchantCode(merchant.getMerchantCode())
        notify.setMerchantId(merchant.getMerchantId())
        notify.setMerchantName(merchant.getChannelName())
        notify.setOrderId(orderId)
        notify.setThirdOrderId("")
        //订单状态判断标准： 1 等待处理 2 处理中  3 成功 4 失败 5 成功但已返款
        if (json.getString("status") == ("3")) {
            // 商户返回出款状态：0成功，1失败,2处理中
            notify.setStatus(0)
            notify.setAmount(json.getBigDecimal("amount").setScale(2, RoundingMode.DOWN))
        } else if (json.getString("status") == ("4")) {
            notify.setStatus(1)
        } else {
            notify.setStatus(2)
        }
        return notify
    }

    BigDecimal balanceQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        Map<String, Object> params = new HashMap<>()
        params.put("app_id", merchantAccount.getMerchantCode())
        params.put("random_str", DateUtils.format(new Date(), DateUtils.YYYYMMDDHHMMSS).substring(8))
        String toSign = this.toAscii(params) + "&key=" + merchantAccount.getPrivateKey()
        params.put("sign", MD5.md5(toSign))

        log.info("FlashPayScript_QueryBalance_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.PAYMENT_QUERY_BALANCE.getCode())
                .channelId(PaymentMerchantEnum.FLASH_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.FLASH_PAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId("")
                .build()
        String resStr = okHttpUtil.postJSON(merchantAccount.getPayUrl() + "/api/query_balance", JSON.toJSONString(params), requestHeader)
        log.info("FlashPayScript_QueryBalance_resStr:{}", resStr)
        if (null == resStr) {
            return BigDecimal.ZERO
        }
        JSONObject json = JSON.parseObject(resStr)
        if (json == null) {
            return BigDecimal.ZERO
        }
        return json.getBigDecimal("balance")
    }


    /**
     * ASCII排序
     *
     * @param parameters
     * @return
     */
    String toAscii(Map<String, Object> parameters) {
        List<Map.Entry<String, Object>> infoIds = new ArrayList<>(parameters.entrySet())
        // 对所有传入参数按照字段名的 ASCII 码从小到大排序（字典序）
        Collections.sort(infoIds, Comparator.comparing({ o -> (o as Map.Entry).getKey() }))
        StringBuffer sign = new StringBuffer()
        for (Map.Entry<String, Object> item : infoIds) {
            String k = item.getKey()
            if (!org.springframework.util.StringUtils.isEmpty(item.getKey())) {
                Object v = item.getValue()
                if (null != v && !ObjectUtils.isEmpty(v)) {
                    sign.append(k + "=" + v + "&")
                }
            }
        }
        return sign.deleteCharAt(sign.length() - 1).toString()
    }
}