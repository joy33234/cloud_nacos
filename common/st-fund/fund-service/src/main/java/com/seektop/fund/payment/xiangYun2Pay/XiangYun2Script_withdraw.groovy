package com.seektop.fund.payment.xiangYun2Pay

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
 * @desc 祥云代付(新系统)
 * @date 2021-10-12
 * @auth Otto
 */
class XiangYun2Script_withdraw {

    private static final Logger log = LoggerFactory.getLogger(XiangYun2Script_withdraw.class)

    private static final String SERVER_WITHDRAW_URL = "/InterfaceV5/CreateWithdrawOrder/"
    private static final String SERVER_QUERY_URL = "/InterfaceV6/QueryWithdrawOrder/"
    private static final String SERVER_BALANCE_URL = "/InterfaceV6/GetBalanceAmount/"
    private OkHttpUtil okHttpUtil

    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness

    WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

        Map<String, String> params = new HashMap<>()
        params.put("Amount", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString())
        params.put("BankCardBankName", glPaymentChannelBankBusiness.getBankName(req.getBankId(), merchantAccount.getChannelId()))
        params.put("BankCardNumber", req.getCardNo())
        params.put("BankCardRealName", req.getName())
        params.put("MerchantId", merchantAccount.getMerchantCode())
        params.put("MerchantUniqueOrderId", req.getOrderId())
        params.put("NotifyUrl", merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId())
        params.put("Timestamp", DateUtils.format(new Date(), DateUtils.YYYYMMDDHHMMSS))
        params.put("WithdrawTypeId", "0")

        String toSign = MD5.toAscii(params) + merchantAccount.getPrivateKey()
        params.put("Sign", MD5.md5(toSign))

        log.info("XiangYunScript_Transfer_params: {}", params)
        GlRequestHeader requestHeader = getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(), merchantAccount.getChannelId(), merchantAccount.getChannelName())

        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + SERVER_WITHDRAW_URL, params, requestHeader)
        log.info("XiangYunScript_Transfer_resStr: {}", resStr)

        WithdrawResult result = new WithdrawResult()
        result.setOrderId(req.getOrderId())
        result.setReqData(JSON.toJSONString(params))
        result.setResData(resStr)

        JSONObject json = JSON.parseObject(resStr)
        //Code: 0 下单成功 ，其余失败
        if (json == null || "0" != json.getString("Code")) {
            result.setValid(false)
            result.setMessage(json == null ? "API异常:请联系出款商户确认订单." : json.getString("Message"))
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

        log.info("XiangYunScript_WithdrawNotify_resMap:{}", JSON.toJSONString(resMap))

        String orderId = resMap.get("MerchantUniqueOrderId");
        if (StringUtils.isNotEmpty(orderId)) {
            return withdrawQuery(okHttpUtil, merchant, orderId )

        } else {
            return null

        }
    }

    WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2] as String

        Map<String, String> params = new HashMap<String, String>()
        params.put("MerchantId", merchant.getMerchantCode())
        params.put("Timestamp", DateUtils.format(new Date(), DateUtils.YYYYMMDDHHMMSS))
        params.put("MerchantUniqueOrderId", orderId)

        String toSign = MD5.toAscii(params) + merchant.getPrivateKey()
        params.put("Sign", MD5.md5(toSign))

        log.info("XiangYunScript_TransferQuery_reqMap:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = getRequestHeard(null, null, orderId, GlActionEnum.WITHDRAW_QUERY.getCode(), merchant.getChannelId(), merchant.getChannelName())

        String resStr = okHttpUtil.post(merchant.getPayUrl() + SERVER_QUERY_URL, params, requestHeader)
        log.info("XiangYunScript_TransferQuery_resStr:{}", resStr)

        if (StringUtils.isEmpty(resStr)) {
            return null
        }
        JSONObject json = JSON.parseObject(resStr)
        WithdrawNotify notify = new WithdrawNotify()

        // 网关返回码： 0=成功，其他失败
        if ( "0" == json.getString("Code")) {
            notify.setMerchantCode(merchant.getMerchantCode())
            notify.setMerchantId(merchant.getMerchantId())
            notify.setMerchantName(merchant.getChannelName())
            notify.setOrderId(orderId)
            notify.setThirdOrderId("")

            //0 处理中
            //100 已完成 (已成功)
            //-90 已撤销（已失败）
            //-10 订单号不存在
            String payStatus = json.getString("WithdrawOrderStatus")
            if (payStatus == "100" ) {
                notify.setStatus(0)
                notify.setRsp("SUCCESS")

            } else if (payStatus == "-90" ) {
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
        params.put("MerchantId", merchantAccount.getMerchantCode())
        params.put("Timestamp", DateUtils.format(new Date(), DateUtils.YYYYMMDDHHMMSS))

        String toSign = MD5.toAscii(params) + merchantAccount.getPrivateKey()
        params.put("Sign", MD5.md5(toSign))

        log.info("XiangYunScript_QueryBalance_reqMap: {}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                getRequestHeard("", "", "", GlActionEnum.PAYMENT_QUERY_BALANCE.getCode(), merchantAccount.getChannelId(), merchantAccount.getChannelName())

        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + SERVER_BALANCE_URL, params, requestHeader)
        log.info("XiangYunScript_QueryBalance_resStr: {}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        //网关返回码：0=成功，其他失败
        if (json == null || json.getString("Code") != "0") {
            return BigDecimal.ZERO
        }

        BigDecimal balance = json.getBigDecimal("BalanceAmount")
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