package com.seektop.fund.payment.pKPayPay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
import com.seektop.common.utils.RSASignature
import com.seektop.enumerate.GlActionEnum
import com.seektop.exception.GlobalException
import com.seektop.fund.business.GlPaymentChannelBankBusiness
import com.seektop.fund.model.GlWithdraw
import com.seektop.fund.model.GlWithdrawMerchantAccount
import com.seektop.fund.payment.WithdrawNotify
import com.seektop.fund.payment.WithdrawResult
import com.seektop.fund.payment.groovy.BaseScript
import com.seektop.fund.payment.groovy.ResourceEnum
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode

/**
 * @desc PK支付
 * @auth Otto
 * @date 2022-02-11
 */
public class PKPayScript_withdraw {

    private static final Logger log = LoggerFactory.getLogger(PKPayScript_withdraw.class)

    private OkHttpUtil okHttpUtil
    private static final String SERVER_WITHDRAW_URL = "/api/agentPay/draw"
    private static final String SERVER_QUERY_URL = "/api/agentPay/query"
    private static final String SERVER_BALANCE_URL = "/api/payQuery/balance"

    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness

    WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

        Map<String, String> params = new HashMap<>()
        params.put("mchId", merchantAccount.getMerchantCode())
        params.put("banknumber", req.getCardNo())
        params.put("bankfullname", req.getName())
        params.put("tkmoney", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString())
        params.put("orderid", req.getOrderId())
        params.put("notifyurl", merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId())
        params.put("type", "1")

        StringBuffer s = new StringBuffer();
        s.append("mchId=" + params.get("mchId") + "&");
        s.append("orderid=" + params.get("orderid") + "&");
        s.append("tkmoney=" + params.get("tkmoney")+ "&");
        s.append("banknumber=" + params.get("banknumber"));

        String sign = RSASignature.sign(s.toString(), merchantAccount.getPrivateKey());
        params.put("sign", sign)

        log.info("PKPayScript_Transfer_params: {}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(), merchantAccount.getChannelId(), merchantAccount.getChannelName())

        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + SERVER_WITHDRAW_URL, params,  requestHeader)
        log.info("PKPayScript_Transfer_resStr: {}", resStr)

        WithdrawResult result = new WithdrawResult()
        result.setOrderId(req.getOrderId())
        result.setReqData(JSON.toJSONString(params))
        result.setResData(resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || 0 != json.getInteger("code")) {
            result.setValid(false)
            result.setMessage(json == null ? "API异常:请联系出款商户确认订单." : json.getString("msg"))
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
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        log.info("PKPayScript_WithdrawNotify_resMap:{}", JSON.toJSONString(resMap))
        String orderid = resMap.get("orderid")

        StringBuffer s = new StringBuffer();
        s.append("orderid=" + orderid+ "&");
        s.append("mchId=" + resMap.get("mchId") + "&");
        s.append("status=" + resMap.get("status"));

        if ( RSASignature.doCheck(s.toString(), resMap.get("sign"), merchant.getPublicKey())) {
            return withdrawQuery(okHttpUtil, merchant, orderid )
        }
            log.info("PKPayScript_WithdrawNotify_Sign: 回调资料错误或验签失败 单号：{}", orderid)
            return null

    }


    WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2] as String

        Map<String, String> params = new HashMap<String, String>()
        params.put("mchId", merchant.getMerchantCode())
        params.put("orderid", orderId)

        StringBuffer s = new StringBuffer();
        s.append("mchId=" + params.get("mchId") + "&");
        s.append("orderid=" + orderId);
        String sign = RSASignature.sign(s.toString(),merchant.getPrivateKey());
        params.put("sign", sign)

        log.info("PKPayScript_TransferQuery_reqMap:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = getRequestHeard(null, null, orderId, GlActionEnum.WITHDRAW_QUERY.getCode(), merchant.getChannelId(), merchant.getChannelName())
        String resStr = okHttpUtil.post(merchant.getPayUrl() + SERVER_QUERY_URL, params, 30L, requestHeader)
        log.info("PKPayScript_TransferQuery_resStr:{}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if ( json.getInteger("code") != 0) {
            return null
        }
        WithdrawNotify notify = new WithdrawNotify()
        if (json.getInteger("status") != null) {
            notify.setMerchantCode(merchant.getMerchantCode())
            notify.setMerchantId(merchant.getMerchantId())
            notify.setMerchantName(merchant.getChannelName())
            notify.setOrderId(orderId)

            //0：未处理，1：处理中，2，已打款，3：已驳回
            if (json.getInteger("status") == 2) {
                notify.setStatus(0)
                notify.setRsp("OK")

            } else if (json.getInteger("status") == 3) {
                notify.setStatus(1)
                notify.setRsp("OK")

            } else {
                notify.setStatus(2)

            }
        }
        return notify
    }


    BigDecimal balanceQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[2], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        Map<String, String> params = new HashMap<>()
        params.put("mchId", merchantAccount.getMerchantCode())
        params.put("nonceStr", "value")

        String s = "mchId=" + merchantAccount.getMerchantCode() + "&nonceStr=value"
        String sign = RSASignature.sign(s,merchantAccount.getPrivateKey());
        params.put("sign", sign)

        log.info("PKPayScript_QueryBalance_reqMap: {}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                getRequestHeard("", "", "", GlActionEnum.PAYMENT_QUERY_BALANCE.getCode(), merchantAccount.getChannelId(), merchantAccount.getChannelName())
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + SERVER_BALANCE_URL, params, 30L, requestHeader)
        log.info("PKPayScript_QueryBalance_resStr: {}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || json.getString("success") != "true") {
            return BigDecimal.ZERO
        }

        BigDecimal balance = json.getBigDecimal("balance").setScale(2, RoundingMode.DOWN)
        return balance == null ? BigDecimal.ZERO : balance
    }

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
