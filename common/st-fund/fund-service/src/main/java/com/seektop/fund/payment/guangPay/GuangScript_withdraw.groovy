package com.seektop.fund.payment.guangPay

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
import com.seektop.fund.payment.groovy.BaseScript
import com.seektop.fund.payment.groovy.ResourceEnum
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode

/**
 * @desc 光代付
 * @date 2021-10-29
 * @auth Otto
 */
class GuangScript_withdraw {

    private static final Logger log = LoggerFactory.getLogger(GuangScript_withdraw.class)

    private static final String SERVER_WITHDRAW_URL = "/api/mch/cashout"
    private static final String SERVER_QUERY_URL = "/api/mch/cashquery"
    private static final String SERVER_BALANCE_URL = "/api/mch/balance"
    private OkHttpUtil okHttpUtil

    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness

    WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

        Map<String, String> params = new HashMap<>()
        params.put("mch_id", merchantAccount.getMerchantCode())
        params.put("timestamp", DateUtils.format(new Date(), DateUtils.YYYY_MM_DD_HH_MM_SS))
        params.put("money", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString())
        params.put("bank_name", glPaymentChannelBankBusiness.getBankName(req.getBankId(), merchantAccount.getChannelId()))
        params.put("acct_name", req.getName())
        params.put("acct_no", req.getCardNo())
        params.put("out_id", req.getOrderId())
        params.put("notifyurl", merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId())

        String toSign = MD5.toAscii(params) + "&key=" + merchantAccount.getPrivateKey()
        params.put("sign", MD5.md5(toSign).toUpperCase())

        log.info("GuangScript_Transfer_params: {}", params)
        GlRequestHeader requestHeader = getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(), merchantAccount.getChannelId(), merchantAccount.getChannelName())
        String resStr = okHttpUtil.postJSON(merchantAccount.getPayUrl() + SERVER_WITHDRAW_URL, JSON.toJSONString(params), requestHeader)
        log.info("GuangScript_Transfer_resStr: {}", resStr)

        WithdrawResult result = new WithdrawResult()
        result.setOrderId(req.getOrderId())
        result.setReqData(JSON.toJSONString(params))
        result.setResData(resStr)

        JSONObject json = JSON.parseObject(resStr)
        //code: 0 下单成功 ，其余失败
        if (json == null || 0 != json.getInteger("code")) {
            result.setValid(false)
            result.setMessage(json == null ? "API异常:请联系出款商户确认订单." : json.getString("msg"))
            return result
        }

        result.setValid(true)
        result.setMessage("")
        result.setThirdOrderId(json.getJSONObject("data").getString("cash_id"))
        return result
    }

    WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount account = args[1] as GlWithdrawMerchantAccount
        Map<String, String> resMap = args[2] as Map<String, String>

        log.info("GuangScript_WithdrawNotify_resMap:{}", JSON.toJSONString(resMap))
        JSONObject json = JSONObject.parseObject(resMap.get("reqBody"))
        Map<String, String> signMap =(Map<String, String>) json;
        String thirdSign = signMap.remove("sign");

        String toSign = MD5.toAscii(signMap) + "&key=" + account.getPrivateKey();
        toSign = MD5.md5(toSign).toUpperCase();

        if ( StringUtils.isNotEmpty(signMap.get("out_cash_id")) && toSign == thirdSign ) {
            return withdrawQuery(okHttpUtil, account,  signMap.get("out_cash_id") )

        }
        log.info( "GuangScript_withdrawNotify_Sign: 回调资料错误或验签失败，单号：{}",  signMap.get("out_cash_id") )
        return null


    }

    WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2] as String

        Map<String, String> params = new HashMap<String, String>()
        params.put("mch_id", merchant.getMerchantCode())
        params.put("timestamp", DateUtils.format(new Date(), DateUtils.YYYY_MM_DD_HH_MM_SS))
        params.put("out_cash_id", orderId)

        String toSign = MD5.toAscii(params) + "&key=" + merchant.getPrivateKey()
        params.put("sign", MD5.md5(toSign))

        log.info("GuangScript_TransferQuery_reqMap:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = getRequestHeard(null, null, orderId, GlActionEnum.WITHDRAW_QUERY.getCode(), merchant.getChannelId(), merchant.getChannelName())

        String resStr = okHttpUtil.postJSON(merchant.getPayUrl() + SERVER_QUERY_URL, JSON.toJSONString(params), requestHeader)
        log.info("GuangScript_TransferQuery_resStr:{}", resStr)
        JSONObject json = JSON.parseObject(resStr)
        if (json == null) {
            return null
        }
        WithdrawNotify notify = new WithdrawNotify()

        // 网关返回码： 0=成功，其他失败
        if (0 == json.getInteger("code")) {
            notify.setMerchantCode(merchant.getMerchantCode())
            notify.setMerchantId(merchant.getMerchantId())
            notify.setMerchantName(merchant.getChannelName())
            notify.setOrderId(orderId)
            notify.setThirdOrderId("")

            if (json.getJSONObject("data") == null) {
                notify.setStatus(2)
                return notify
            }

            Integer payStatus = json.getJSONObject("data").getInteger("state")
            //1=待审，2=驳回，3=成功，4=指派，5=异常
            if (payStatus == 3 ) {
                notify.setStatus(0)
                notify.setRsp("SUCCESS")

            } else if (payStatus == 2 || payStatus == 5 ) {
                notify.setStatus(1)
                notify.setRsp("SUCCESS")

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
        params.put("mch_id", merchantAccount.getMerchantCode())
        params.put("timestamp", DateUtils.format(new Date(), DateUtils.YYYY_MM_DD_HH_MM_SS))

        String toSign = MD5.toAscii(params) + "&key=" + merchantAccount.getPrivateKey()
        params.put("sign", MD5.md5(toSign))

        log.info("GuangScript_QueryBalance_reqMap: {}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                getRequestHeard("", "", "", GlActionEnum.PAYMENT_QUERY_BALANCE.getCode(), merchantAccount.getChannelId(), merchantAccount.getChannelName())

        String resStr = okHttpUtil.postJSON(merchantAccount.getPayUrl() + SERVER_BALANCE_URL, JSONObject.toJSONString(params), requestHeader)
        log.info("GuangScript_QueryBalance_resStr: {}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        //网关返回码：0=成功，其他失败
        if (json == null || json.getInteger("code") != 0) {
            return BigDecimal.ZERO
        }

        BigDecimal balance = json.getJSONObject("data").getBigDecimal("real_balance")
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