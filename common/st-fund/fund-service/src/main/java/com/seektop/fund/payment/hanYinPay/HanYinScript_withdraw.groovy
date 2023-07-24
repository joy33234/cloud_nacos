package com.seektop.fund.payment.hanYinPay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
import com.seektop.common.utils.BigDecimalUtils
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
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode

/**
 * @desc 瀚银代付
 * @date 2021-09-29
 * @auth Otto
 */
class HanYinScript_withdraw {

    private static final Logger log = LoggerFactory.getLogger(HanYinScript_withdraw.class)

    private static final String SERVER_WITHDRAW_URL = "/Xxpay/api/v1/dftransRequest"
    private static final String SERVER_QUERY_URL = "/Xxpay/api/v1/dftradeQuery"
    private static final String SERVER_BALANCE_URL = "/Xxpay/api/v1/balanceRequest"
    private OkHttpUtil okHttpUtil

    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness

    WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

        Map<String, String> DataContentParms = new HashMap<>()
        DataContentParms.put("mch_id", merchantAccount.getMerchantCode())
        DataContentParms.put("trans_money", req.getAmount().subtract(req.getFee()).multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.DOWN)+"")
        DataContentParms.put("service", "BL_WAP_DF_DZ")
        DataContentParms.put("out_trade_no", req.getOrderId())
        DataContentParms.put("account_name", req.getName())
        DataContentParms.put("bank_card", req.getCardNo())
        DataContentParms.put("bankCode", glPaymentChannelBankBusiness.getBankCode(req.getBankId(), merchantAccount.getChannelId()))
        DataContentParms.put("notify_url", merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId())

        String toSign = MD5.toAscii(DataContentParms) +"&key="+ merchantAccount.getPrivateKey()
        DataContentParms.put("sign", MD5.md5(toSign).toUpperCase())

        log.info("HanYinScript_Transfer_params: {}", DataContentParms)
        GlRequestHeader requestHeader = getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(), merchantAccount.getChannelId(), merchantAccount.getChannelName())

        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + SERVER_WITHDRAW_URL, DataContentParms, requestHeader)
        log.info("HanYinScript_Transfer_resStr: {}", resStr)

        WithdrawResult result = new WithdrawResult()
        result.setOrderId(req.getOrderId())
        result.setReqData(JSON.toJSONString(DataContentParms))
        result.setResData(resStr)

        JSONObject json = JSON.parseObject(resStr)
        //ret_code:通知状态  SUCCESS /FAIL
        if (json == null || "SUCCESS" != json.getString("ret_code")) {
            result.setValid(false)
            result.setMessage(json == null ? "API异常:请联系出款商户确认订单." : json.getString("ret_message"))
            return result
        }

        //tradeStatus: 订单状态  1-处理中，2-失败，3-成功
        if ("2" == json.getString("tradeStatus")) {
            result.setValid(false)
            result.setMessage(json.getString("tradeMessage"))
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

        log.info("HanYinScript_WithdrawNotify_resMap:{}", JSON.toJSONString(resMap))
        String orderid = resMap.get("out_trade_no")
        if (org.apache.commons.lang.StringUtils.isNotEmpty(orderid)) {
            return withdrawQuery(okHttpUtil, merchant, orderid)

        } else {
            return null

        }
    }

    WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2] as String

        Map<String, String> DataContentParms = new HashMap<String, String>()
        DataContentParms.put("mch_id", merchant.getMerchantCode())
        DataContentParms.put("out_trade_no", orderId)
        String toSign = MD5.toAscii(DataContentParms) + "&key=" +merchant.getPrivateKey()
        DataContentParms.put("sign", MD5.md5(toSign))

        log.info("HanYinScript_TransferQuery_reqMap:{}", JSON.toJSONString(DataContentParms))
        GlRequestHeader requestHeader = getRequestHeard(null, null, orderId, GlActionEnum.WITHDRAW_QUERY.getCode(), merchant.getChannelId(), merchant.getChannelName())

        String resStr = okHttpUtil.post(merchant.getPayUrl() + SERVER_QUERY_URL, DataContentParms ,  requestHeader)
        log.info("HanYinScript_TransferQuery_resStr:{}", resStr)
        JSONObject json = JSON.parseObject(resStr)
        if (json == null) {
            return null
        }
        WithdrawNotify notify = new WithdrawNotify()

        //ret_code : SUCCESS/FAIL
        if (json.getString("ret_code") == "SUCCESS") {
            String payStatus =json.getString("payStatus")
            notify.setMerchantCode(merchant.getMerchantCode())
            notify.setMerchantId(merchant.getMerchantId())
            notify.setMerchantName(merchant.getChannelName())
            notify.setOrderId(orderId)
            notify.setThirdOrderId("")

            //1-处理中，2-失败，3-成功
            if (payStatus == "3" ) {
                notify.setStatus(0)
                notify.setRsp("success")

            } else if (payStatus== "2" ) {
                notify.setStatus(1)
                notify.setRsp("success")

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

        Map<String, String> DataContentParms = new HashMap<>()
        DataContentParms.put("mch_id", merchantAccount.getMerchantCode())
        DataContentParms.put("service", "BL_WAP")
        DataContentParms.put("out_trade_no", "testOrder001")

        String toSign = MD5.toAscii(DataContentParms) + "&key="+merchantAccount.getPrivateKey()
        DataContentParms.put("sign", MD5.md5(toSign))

        log.info("HanYinScript_QueryBalance_reqMap: {}", JSON.toJSONString(DataContentParms))
        GlRequestHeader requestHeader =
                getRequestHeard("", "", "", GlActionEnum.PAYMENT_QUERY_BALANCE.getCode(), merchantAccount.getChannelId(), merchantAccount.getChannelName())

        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + SERVER_BALANCE_URL, DataContentParms , requestHeader)
        log.info("HanYinScript_QueryBalance_resStr: {}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || json.getString("ret_code") != "SUCCESS") {
            return BigDecimal.ZERO
        }

        BigDecimal balance = json.getBigDecimal("totalbalance")
        balance = balance.divide(BigDecimalUtils.HUNDRED) //分 > 元
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