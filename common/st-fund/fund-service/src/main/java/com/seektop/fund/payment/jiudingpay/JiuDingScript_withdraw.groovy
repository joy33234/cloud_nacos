package com.seektop.fund.payment.jiudingpay

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
import com.seektop.fund.payment.groovy.ResourceEnum
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode

/**
 * 九鼎支付
 * @author joy
 * @date 20201218
 */
public class JiuDingScript_withdraw {

    private static final Logger log = LoggerFactory.getLogger(JiuDingScript_withdraw.class)

    private OkHttpUtil okHttpUtil

    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness


    WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount account = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw

        Map<String, String> params = new HashMap<String, String>()
        params.put("merchantNum", account.getMerchantCode())
        params.put("orderNo", req.getOrderId())
        params.put("amount", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString())
        params.put("notifyUrl", account.getNotifyUrl() + account.getMerchantId())
        params.put("channelCode", "bankCard")

        String toSign = account.getMerchantCode() + req.getOrderId() + params.get("amount") + params.get("notifyUrl") + account.getPrivateKey()
        params.put("sign", MD5.md5(toSign))

        params.put("accountHolder", req.getName())
        params.put("bankCardAccount", req.getCardNo())
        params.put("openAccountBank", req.getBankName())

        log.info("JiuDingScriptTransfer_params: {}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(), account.getChannelId(), account.getChannelName())
        String resStr = okHttpUtil.post(account.getPayUrl() + "/api/startPayForAnotherOrder", params, requestHeader)
        log.info("JiuDingScriptTransfer_resStr: {}", resStr)


        WithdrawResult result = new WithdrawResult()
        result.setOrderId(req.getOrderId())
        result.setReqData(JSON.toJSONString(params))
        result.setResData(resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || json.getString("code") != ("200")) {
            result.setValid(false)
            result.setMessage(json == null ? "API异常:请联系出款商户确认订单." : json.getString("msg"))
            return result
        }
        result.setValid(true)
        result.setMessage(json.getString("msg"))
        return result
    }

    WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        Map<String, String> resMap = args[2] as Map<String, String>
        log.info("JiuDingScriptNotify_resMap:{}", JSON.toJSONString(resMap))
        String orderid = resMap.get("orderNo")
        if (StringUtils.isNotEmpty(orderid)) {
            return withdrawQuery(okHttpUtil, merchant, orderid, args[3])
        } else {
            return null
        }
    }

    WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2]
        this.glPaymentChannelBankBusiness = com.seektop.fund.payment.groovy.BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        Map<String, String> params = new HashMap<String, String>()
        params.put("merchantNum", merchant.getMerchantCode())
        params.put("merchantOrderNo", orderId)

        String toSign = merchant.getMerchantCode() + orderId + merchant.getPrivateKey()
        params.put("sign", MD5.md5(toSign))

        log.info("JiuDingScriptTransferQuery_reqMap:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = getRequestHeard(null, null, orderId, GlActionEnum.WITHDRAW_QUERY.getCode(), merchant.getChannelId(), merchant.getChannelName())
        String resStr = okHttpUtil.get(merchant.getPayUrl() + "/api/getPayForAnotherOrderInfo", params, requestHeader)
        log.info("JiuDingScriptTransferQuery_resStr:{}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || json.getString("code") != "200") {
            return null
        }
        JSONObject dataJSON = json.getJSONObject("data")
        if (dataJSON == null){
            return null
        }
        WithdrawNotify notify = new WithdrawNotify()
        notify.setAmount(dataJSON.getBigDecimal("amount"))
        notify.setMerchantCode(merchant.getMerchantCode())
        notify.setMerchantId(merchant.getMerchantId())
        notify.setMerchantName(merchant.getChannelName())
        notify.setOrderId(orderId)
        notify.setThirdOrderId(dataJSON.getString("orderNo"))
        //1：等待接单，2：已接单，3：已完成，4：已取消，5：超时取消，6：异常退回
        if (dataJSON.getString("orderState") == "3") {
            notify.setStatus(0)
        } else if (dataJSON.getString("orderState") == "4" || dataJSON.getString("orderState") == "5"
                || dataJSON.getString("orderState") == "6") {
            notify.setStatus(1)
        } else {
            notify.setStatus(2)
        }
        log.info(JSON.toJSONString(notify))
        return notify
    }


    BigDecimal balanceQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        this.glPaymentChannelBankBusiness = com.seektop.fund.payment.groovy.BaseScript.getResource(args[2], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        Map<String, String> params = new HashMap<>()
        params.put("merchantNum", merchantAccount.getMerchantCode())

        String toSign = merchantAccount.getMerchantCode() + merchantAccount.getPrivateKey()
        params.put("sign", MD5.md5(toSign))

        log.info("JiuDingScriptQueryBalance_reqMap: {}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                getRequestHeard(null, null, null, GlActionEnum.PAYMENT_QUERY_BALANCE.getCode(), merchantAccount.getChannelId(), merchantAccount.getChannelName())
        String resStr = okHttpUtil.get(merchantAccount.getPayUrl() + "/api/getBalance", params, requestHeader)
        log.info("JiuDingScriptQueryBalance_resStr: {}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json != null && json.getString("code") == "200") {
            JSONObject dataJSON = json.getJSONObject("data")
            if (dataJSON == null) {
                return BigDecimal.ZERO
            }
            return dataJSON.getBigDecimal("balance") == null ? BigDecimal.ZERO : dataJSON.getBigDecimal("balance")
        }
        return BigDecimal.ZERO
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
