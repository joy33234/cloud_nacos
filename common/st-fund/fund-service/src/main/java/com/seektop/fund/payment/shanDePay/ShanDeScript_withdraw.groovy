package com.seektop.fund.payment.shanDePay

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
 * @desc 衫德代付
 * @date 2021-11-09
 * @auth Otto
 */
class ShanDeScript_withdraw {

    private static final Logger log = LoggerFactory.getLogger(ShanDeScript_withdraw.class)

    private static final String SERVER_WITHDRAW_URL = "/api/df/apply"
    private static final String SERVER_QUERY_URL = "/api/df/search"
    private static final String SERVER_BALANCE_URL = "/api/df/balance"
    private OkHttpUtil okHttpUtil

    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness

    WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        this.glPaymentChannelBankBusiness = com.seektop.fund.payment.groovy.BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

        Map<String, String> params = new HashMap<>()
        String nonce_str = System.currentTimeSeconds()+"";
        params.put("appid", merchantAccount.getMerchantCode())
        params.put("df_sn", req.getOrderId())
        params.put("money", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString())
        params.put("bankname", req.getBankName())
        params.put("accountname", req.getName())
        params.put("cardnumber", req.getCardNo())
        params.put("nonce_str", nonce_str)

        String toSign = merchantAccount.getMerchantCode() + merchantAccount.getPrivateKey() + nonce_str
        params.put("signature", MD5.md5(toSign))

        log.info("ShanDeScript_Transfer_params: {}", params)
        GlRequestHeader requestHeader = getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(), merchantAccount.getChannelId(), merchantAccount.getChannelName())
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + SERVER_WITHDRAW_URL, params, requestHeader)
        log.info("ShanDeScript_Transfer_resStr: {} , orderId:{}", resStr , req.getOrderId())

        WithdrawResult result = new WithdrawResult()
        result.setOrderId(req.getOrderId())
        result.setReqData(JSON.toJSONString(params))
        result.setResData(resStr)

        JSONObject json = JSON.parseObject(resStr)
        //code: 1 下单成功 ，其余失败
        if (json == null || "1" != json.getString("code")) {
            result.setValid(false)
            result.setMessage(json == null ? "API异常:请联系出款商户确认订单." : json.getString("msg"))
            return result
        }

        result.setValid(true)
        result.setMessage(json.getString("data"))
        return result
    }

    WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        Map<String, String> resMap = args[2] as Map<String, String>
        this.glPaymentChannelBankBusiness = com.seektop.fund.payment.groovy.BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

        //三方表示无主动回调功能 2021/11/10
        log.info("ShanDeScript_WithdrawNotify_resMap:{}", JSON.toJSONString(resMap))
        String orderId = resMap.get("orderId");
        if (StringUtils.isNotEmpty(orderId)  ) {
            return withdrawQuery(okHttpUtil, merchant, orderId )

        }
        return null
    }

    WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2] as String

        String nonce_str = System.currentTimeSeconds();
        Map<String, String> params = new HashMap<String, String>()
        params.put("appid", merchant.getMerchantCode())
        params.put("nonce_str", nonce_str)
        params.put("df_sn", orderId)

        String toSign =  merchant.getMerchantCode() + merchant.getPrivateKey() + nonce_str
        params.put("signature", MD5.md5(toSign))

        log.info("ShanDeScript_TransferQuery_reqMap:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = getRequestHeard(null, null, orderId, GlActionEnum.WITHDRAW_QUERY.getCode(), merchant.getChannelId(), merchant.getChannelName())

        String resStr = okHttpUtil.post(merchant.getPayUrl() + SERVER_QUERY_URL, params, requestHeader)
        log.info("ShanDeScript_TransferQuery_resStr:{}", resStr)

        if (StringUtils.isEmpty(resStr)) {
            return null
        }
        JSONObject json = JSON.parseObject(resStr)
        WithdrawNotify notify = new WithdrawNotify()

        // 网关返回码： 1-成功，其他-失败
        if ( "1" == json.getString("code")) {
            notify.setMerchantCode(merchant.getMerchantCode())
            notify.setMerchantId(merchant.getMerchantId())
            notify.setMerchantName(merchant.getChannelName())
            notify.setOrderId(orderId)
            notify.setThirdOrderId("")

            JSONObject dataJson = json.getJSONObject("data")
            //0：处理中,1：处理成功,2-失败
            String payStatus = dataJson.getString("status")
            if (payStatus == "1" ) {
                notify.setStatus(0)
                notify.setRsp("SUCCESS")

            } else if (payStatus == "2" ) {
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
        this.glPaymentChannelBankBusiness = com.seektop.fund.payment.groovy.BaseScript.getResource(args[2], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        String nonce_str = System.currentTimeSeconds()+"";

        Map<String, String> params = new HashMap<>()
        params.put("appid", merchantAccount.getMerchantCode())
        params.put("nonce_str", nonce_str)

        String toSign = merchantAccount.getMerchantCode() + merchantAccount.getPrivateKey() + nonce_str
        params.put("signature", MD5.md5(toSign))

        log.info("ShanDeScript_QueryBalance_reqMap: {}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                getRequestHeard("", "", "", GlActionEnum.PAYMENT_QUERY_BALANCE.getCode(), merchantAccount.getChannelId(), merchantAccount.getChannelName())

        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + SERVER_BALANCE_URL, params, requestHeader)
        log.info("ShanDeScript_QueryBalance_resStr: {}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        //网关返回码：1=成功，其他失败
        if (json == null || json.getString("code") != "1") {
            return BigDecimal.ZERO
        }
        JSONObject dataJson = json.getJSONObject("data")

        BigDecimal balance = dataJson.getBigDecimal("balance")
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