package com.seektop.fund.payment.huanYu3Pay

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
import com.seektop.fund.payment.WithdrawNotify
import com.seektop.fund.payment.WithdrawResult
import com.seektop.fund.payment.groovy.ResourceEnum
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode

/**
 * @desc 环宇V3代付
 * @date 2021-11-13
 * @auth Otto
 */
class HuanYu3Script_withdraw {

    private static final Logger log = LoggerFactory.getLogger(HuanYu3Script_withdraw.class)

    private static final String SERVER_WITHDRAW_URL = "/pay/order/acp"
    private static final String SERVER_QUERY_URL = "/pay/order/acp/query"
    private static final String SERVER_BALANCE_URL = "/pay/api/balance/query"
    private OkHttpUtil okHttpUtil

    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness

    WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        this.glPaymentChannelBankBusiness = com.seektop.fund.payment.groovy.BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

        Map<String, Object> params = new LinkedHashMap<>()

        params.put("merchNo", merchantAccount.getMerchantCode())
        params.put("orderNo", req.getOrderId())
        params.put("outChannel", "acp")
        params.put("bankCode", glPaymentChannelBankBusiness.getBankCode(req.getBankId(), merchantAccount.getChannelId()))
        params.put("bankName", glPaymentChannelBankBusiness.getBankName(req.getBankId(), merchantAccount.getChannelId()))
        params.put("bankNo", req.getCardNo())
        params.put("acctName", req.getName())
        params.put("certNo", "43333333330000") //身分证字号
        params.put("mobile", "18888888888")    //手机号码
        params.put("userId", req.getUserId() + "")
        params.put("title", "withdraw")
        params.put("product", "withdraw")
        params.put("amount", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString())
        params.put("currency", "CNY")
        params.put("notifyUrl", merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId())
        params.put("reqTime", DateUtils.format(new Date(), DateUtils.YYYYMMDDHHMMSS))

        String context = JSON.toJSONString(params)

        Map<String, Object> reqParams = new LinkedHashMap<>()
        reqParams.put("sign", MD5.md5(context + merchantAccount.getPrivateKey()))
        reqParams.put("context", context.getBytes("UTF-8"))
        reqParams.put("encryptType", "MD5")

        log.info("HuanYu3Script_Transfer_params: {}", JSON.toJSONString(reqParams))
        GlRequestHeader requestHeader = getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(), merchantAccount.getChannelId(), merchantAccount.getChannelName())

        String resStr = okHttpUtil.postJSON(merchantAccount.getPayUrl() + SERVER_WITHDRAW_URL, JSON.toJSONString(reqParams), requestHeader)
        log.info("HuanYu3Script_Transfer_resStr: {}  , orderid :{}", resStr, req.getOrderId())

        WithdrawResult result = new WithdrawResult()
        result.setOrderId(req.getOrderId())
        result.setReqData(JSON.toJSONString(params))
        result.setResData(resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null) {
            result.setValid(false)
            result.setMessage("API异常:请联系出款商户确认订单.")
            return result
        }

        // code: 0 下单成功 ，其余失败
        if (0 != json.getInteger("code")) {
            result.setValid(false)
            result.setMessage(json.getString("msg") == null ? "三方下单失败" : json.getString("msg"))
            return result
        }

        String contextStr = new String(json.getBytes("context"), "UTF-8")
        JSONObject contextJson = JSONObject.parseObject(contextStr)

        if ("0" != contextJson.getString("orderState")) {
            result.setValid(false)
            result.setMessage("三方回传错误")
            return result
        }

        result.setValid(true)
        result.setMessage("")
        return result
    }

    WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        Map<String, String> resMap = args[2] as Map<String, String>
        this.glPaymentChannelBankBusiness = com.seektop.fund.payment.groovy.BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        log.info("HuanYu3Script_WithdrawNotify_resMap:{}", resMap)

        JSONObject json = JSONObject.parseObject(resMap.get("reqBody"));

        Map<String, Object> map = (Map<String, Object>) json
        String sign = map.get("sign");

        String contextStr = new String(json.getBytes("context"), "UTF-8")
        JSONObject contextJson = JSONObject.parseObject(contextStr)

        String md5Sign = MD5.md5(contextStr + merchant.getPrivateKey());
        String orderId = contextJson.getString("orderNo")

        if (StringUtils.isNotEmpty(orderId) && sign == md5Sign) {
            return withdrawQuery(okHttpUtil, merchant, orderId)

        }
        log.info("HuanYu3Script_WithdrawNotify_Sign: 回调资料错误或验签失败 单号：{}", orderId)
        return null


    }

    WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2] as String

        Map<String, String> params = new HashMap<String, String>()
        params.put("merchNo", merchant.getMerchantCode())
        params.put("orderNo", orderId)

        String context = JSON.toJSONString(params);
        Map<String, String> reqParams = new LinkedHashMap<>()
        reqParams.put("context", context.getBytes("UTF-8"))
        reqParams.put("sign", MD5.md5(context + merchant.getPrivateKey()))
        reqParams.put("encryptType", "MD5")

        log.info("HuanYu3Script_TransferQuery_reqMap:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = getRequestHeard(null, null, orderId, GlActionEnum.WITHDRAW_QUERY.getCode(), merchant.getChannelId(), merchant.getChannelName())

        String resStr = okHttpUtil.postJSON(merchant.getPayUrl() + SERVER_QUERY_URL, JSON.toJSONString(reqParams), requestHeader)
        log.info("HuanYu3Script_TransferQuery_resStr:{} ", resStr)

        if (StringUtils.isEmpty(resStr)) {
            return null
        }
        JSONObject json = JSON.parseObject(resStr)
        WithdrawNotify notify = new WithdrawNotify()

        // code 返回码： 0=成功，其他失败
        if (0 == json.getInteger("code")) {
            notify.setMerchantCode(merchant.getMerchantCode())
            notify.setMerchantId(merchant.getMerchantId())
            notify.setMerchantName(merchant.getChannelName())
            notify.setOrderId(orderId)
            notify.setThirdOrderId("")

            String contextStr = new String(json.getBytes("context"), "UTF-8")
            JSONObject contextJson = JSONObject.parseObject(contextStr)
            log.info("HuanYu3Script_TransferQuery_resStr_context :{} ", contextJson)

            //0＝下单成功 1＝支付成功  3=处理中  2.4＝失败
            String payStatus = contextJson.getString("orderState")
            if (payStatus == "1") {
                notify.setStatus(0)
                notify.setRsp("ok")

            } else if (payStatus == "2" || payStatus == "4") {
                notify.setStatus(1)
                notify.setRsp("ok")

            } else {
                notify.setStatus(2)
            }
        }
        return notify
    }

    BigDecimal balanceQuery(Object[] args) throws Exception {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        this.glPaymentChannelBankBusiness = com.seektop.fund.payment.groovy.BaseScript.getResource(args[2], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

        Map<String, String> params = new HashMap<>()
        params.put("merchNo", merchantAccount.getMerchantCode())

        String context = JSON.toJSONString(params);
        Map<String, String> reqParams = new LinkedHashMap<>()
        reqParams.put("context", context.getBytes("UTF-8"))
        reqParams.put("sign", MD5.md5(context + merchantAccount.getPrivateKey()))
        reqParams.put("encryptType", "MD5")

        log.info("HuanYu3Script_QueryBalance_reqMap: {}", JSON.toJSONString(params))
        log.info("HuanYu3Script_QueryBalance_reqParams: {}", JSON.toJSONString(reqParams))
        GlRequestHeader requestHeader =
                getRequestHeard("", "", "", GlActionEnum.PAYMENT_QUERY_BALANCE.getCode(), merchantAccount.getChannelId(), merchantAccount.getChannelName())

        String resStr = okHttpUtil.postJSON(merchantAccount.getPayUrl() + SERVER_BALANCE_URL, JSON.toJSONString(reqParams), requestHeader)
        log.info("HuanYu3Script_QueryBalance_resStr: {}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        //"code":0 成功
        if (json == null || json.getInteger("code") != 0) {
            return BigDecimal.ZERO
        }
        String contextStr = new String(json.getBytes("context"), "UTF-8")
        JSONObject contextJson = JSONObject.parseObject(contextStr)
        BigDecimal balance = contextJson.getBigDecimal("availBal")

        return balance == null ? BigDecimal.ZERO : balance
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