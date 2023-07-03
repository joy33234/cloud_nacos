package com.seektop.fund.payment.audiPay

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
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode

/**
 * @desc 奥迪代付
 * @date 2021-10-02
 * @auth Otto
 */
class AudiScript_withdraw {

    private static final Logger log = LoggerFactory.getLogger(AudiScript_withdraw.class)

    private static final String SERVER_WITHDRAW_URL = "/gateway/repay/index"
    private static final String SERVER_QUERY_URL = "/gateway/query/repay"
    private static final String SERVER_BALANCE_URL = "/gateway/query/queryAmount"
    private OkHttpUtil okHttpUtil

    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness

    WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        this.glPaymentChannelBankBusiness = com.seektop.fund.payment.groovy.BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

        Map<String, String> params = new HashMap<>()
        params.put("merchId", merchantAccount.getMerchantCode())
        params.put("money", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString())
        params.put("userId", req.getUserId()+"")
        params.put("orderId", req.getOrderId())
        params.put("name", req.getName())
        params.put("bank", glPaymentChannelBankBusiness.getBankName(req.getBankId(), merchantAccount.getChannelId()))
        params.put("card", req.getCardNo())
        params.put("time",  DateUtils.format(new Date(), DateUtils.YYYYMMDDHHMMSS))
        params.put("signType", "MD5")
        params.put("notifyUrl", merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId())

        String toSign = MD5.toAscii(params) + "&md5_key=" + merchantAccount.getPrivateKey()
        params.put("sign", MD5.md5(toSign))

        log.info("AudiScript_Transfer_params: {}", params)
        GlRequestHeader requestHeader = getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(), merchantAccount.getChannelId(), merchantAccount.getChannelName())

        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + SERVER_WITHDRAW_URL, params, requestHeader)
        log.info("AudiScript_Transfer_resStr: {}", resStr)

        WithdrawResult result = new WithdrawResult()
        result.setOrderId(req.getOrderId())
        result.setReqData(JSON.toJSONString(params))
        result.setResData(resStr)

        JSONObject json = JSON.parseObject(resStr)
        //code: 处理中:PROCESSING  成功:SUCCESS 失败:FAILED or 四位数字  ex {"code":"4008","msg":"余额不足"}
        if (json == null || ( "PROCESSING" != json.getString("code") && "SUCCESS" != json.getString("code") ) ) {
            result.setValid(false)
            result.setMessage(json == null ? "API异常:请联系出款商户确认订单." : json.getString("msg"))
            return result
        }
        
        result.setValid(true)
        result.setMessage("")
        result.setThirdOrderId("")
        return result
    }

    WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        Map<String, String> resMap = args[2] as Map<String, String>
        this.glPaymentChannelBankBusiness = com.seektop.fund.payment.groovy.BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

        log.info("AudiScript_WithdrawNotify_resMap:{}", JSON.toJSONString(resMap))
        JSONObject json = JSONObject.parseObject(resMap.get("reqBody"))
        String orderid = json.getString("orderId")
        if (org.apache.commons.lang.StringUtils.isNotEmpty(orderid)) {
            return withdrawQuery(okHttpUtil, merchant, orderid )

        } else {
            return null

        }
    }

    WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2] as String

        Map<String, String> params = new HashMap<String, String>()
        params.put("merchId", merchant.getMerchantCode())
        params.put("time",  DateUtils.format(new Date(), DateUtils.YYYYMMDDHHMMSS))
        params.put("orderId", orderId)
        params.put("signType", "MD5")

        String toSign = MD5.toAscii(params) + "&md5_key=" + merchant.getPrivateKey()
        params.put("sign", MD5.md5(toSign))

        log.info("AudiScript_TransferQuery_reqMap:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = getRequestHeard(null, null, orderId, GlActionEnum.WITHDRAW_QUERY.getCode(), merchant.getChannelId(), merchant.getChannelName())
        String resStr = okHttpUtil.post(merchant.getPayUrl() + SERVER_QUERY_URL, params, requestHeader)

        log.info("AudiScript_TransferQuery_resStr:{}", resStr)
        JSONObject json = JSON.parseObject(resStr)
        if (json == null) {
            return null
        }
        WithdrawNotify notify = new WithdrawNotify()

        if ("0000" == json.getString("code")) {
            notify.setMerchantCode(merchant.getMerchantCode())
            notify.setMerchantId(merchant.getMerchantId())
            notify.setMerchantName(merchant.getChannelName())
            notify.setOrderId(orderId)
            notify.setThirdOrderId("")

            String payStatus = json.getString("state")
            //1-成功 2-处理中 7-关闭
            if (payStatus == "1") {
                notify.setStatus(0)
                notify.setRsp("{\"code\":\"0000\",\"msg\": \"接收成功\"}")

            } else if (payStatus == "7") {
                notify.setStatus(1)
                notify.setRsp("{\"code\":\"0000\",\"msg\": \"接收成功\"}")

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

        Map<String, String> params = new HashMap<>()
        params.put("merchId", merchantAccount.getMerchantCode())
        params.put("time", DateUtils.format(new Date(), DateUtils.YYYYMMDDHHMMSS))
        params.put("signType", "MD5")

        String toSign = MD5.toAscii(params) + "&md5_key=" + merchantAccount.getPrivateKey()
        params.put("sign", MD5.md5(toSign))

        log.info("AudiScript_QueryBalance_reqMap: {}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                getRequestHeard("", "", "", GlActionEnum.PAYMENT_QUERY_BALANCE.getCode(), merchantAccount.getChannelId(), merchantAccount.getChannelName())

        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + SERVER_BALANCE_URL, params, requestHeader)
        log.info("AudiScript_QueryBalance_resStr: {}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        //网关返回码：0000 代表成功
        if (json == null || json.getString("code") != "0000") {
            return BigDecimal.ZERO
        }

        BigDecimal balance = json.getBigDecimal("money")
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