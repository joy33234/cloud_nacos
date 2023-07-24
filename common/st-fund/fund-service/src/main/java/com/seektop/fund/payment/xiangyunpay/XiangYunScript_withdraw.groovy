package com.seektop.fund.payment.xiangyunpay

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
 * @desc 祥云支付
 * @date 2021-09-17
 * @auth joy
 */
public class XiangYunScript_withdraw {


    private static final Logger log = LoggerFactory.getLogger(XiangYunScript_withdraw.class)

    private OkHttpUtil okHttpUtil

    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness


    public WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount account = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

        Map<String, String> params = new LinkedHashMap<>()
        params.put("Amount", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString())
        params.put("BankCardBankName", glPaymentChannelBankBusiness.getBankName(req.getBankId(), account.getChannelId()))
        params.put("BankCardNumber", req.getCardNo())
        params.put("BankCardRealName", req.getName())
        params.put("MerchantId", account.getMerchantCode())
        params.put("MerchantUniqueOrderId", req.getOrderId())
        params.put("NotifyUrl", account.getNotifyUrl() + account.getMerchantId())
        params.put("Timestamp", DateUtils.format(new Date(), DateUtils.YYYYMMDDHHMMSS))
        params.put("WithdrawTypeId", "0")
        params.put("Sign", MD5.md5(MD5.toAscii(params) + account.getPrivateKey()))

        log.info("XiangYunScript_doTransfer_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                this.getRequestHeard(req.getUserId() + "", req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(), account.getChannelId(), account.getChannelName())

        String resStr = okHttpUtil.post(account.getPayUrl() + "/InterfaceV5/CreateWithdrawOrder/", params, requestHeader)
        log.info("XiangYunScript_doTransfer_resp:{}", resStr)
        JSONObject json = JSON.parseObject(resStr)

        WithdrawResult result = new WithdrawResult()
        result.setOrderId(req.getOrderId())
        result.setReqData(JSON.toJSONString(params))
        result.setResData(resStr)
        if (StringUtils.isEmpty(resStr)) {
            result.setValid(false)
            result.setMessage("API异常:请联系出款商户确认订单.")
            return result
        }
        if ("0" != (json.getString("Code"))) {
            result.setValid(false)
            result.setMessage(json.getString("Message"))
            return result
        }
        result.setValid(true)
        result.setMessage("ok")
        return result
    }

    public WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        Map<String, String> resMap = args[2] as Map<String, String>
        log.info("XiangYunScript_doTransferNotify_resp:{}", JSON.toJSONString(resMap))
        String orderId = resMap.get("MerchantUniqueOrderId")
        if (StringUtils.isNotEmpty(orderId)) {
            return this.withdrawQuery(okHttpUtil, merchant, orderId, args[3])
        }
        return null
    }

    public WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2]

        Map<String, String> params = new LinkedHashMap<>()
        params.put("MerchantId", merchant.getMerchantCode())
        params.put("MerchantUniqueOrderId", orderId)
        params.put("Timestamp", DateUtils.format(new Date(), DateUtils.YYYYMMDDHHMMSS))
        params.put("sign", MD5.md5(MD5.toAscii(params) + merchant.getPrivateKey()))

        log.info("XiangYunScript_doTransferQuery_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                this.getRequestHeard("", "", orderId, GlActionEnum.WITHDRAW_QUERY.getCode(), merchant.getChannelId(), merchant.getChannelName())
        String resStr = okHttpUtil.postJSON(merchant.getPayUrl() + "/InterfaceV6/QueryWithdrawOrder/", JSONObject.toJSONString(params), requestHeader)
        log.info("XiangYunScript_doTransferQuery_resp:{}", resStr)
        if (StringUtils.isEmpty(resStr)) {
            return null
        }
        JSONObject json = JSON.parseObject(resStr)
        if ("0" != (json.getString("Code"))) {
            return null
        }
        WithdrawNotify notify = new WithdrawNotify()
        notify.setMerchantCode(merchant.getMerchantCode())
        notify.setMerchantId(merchant.getMerchantId())
        notify.setMerchantName(merchant.getChannelName())
        notify.setOrderId(orderId)
        notify.setThirdOrderId(json.getString("WithdrawOrderId"))
        // 支付状态:status  0 处理中  100 已完成 (已成功)  -90 已撤销（已失败）  -10 订单号不存在
        if (json.getString("WithdrawOrderStatus") == ("100")) {
            notify.setStatus(0)
            notify.setRsp("SUCCESS")
        } else if (json.getString("WithdrawOrderStatus") == ("-90")) {
            notify.setStatus(1)
            notify.setRsp("SUCCESS")
        } else {
            notify.setStatus(2)
        }
        return notify
    }

    public BigDecimal balanceQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount

        Map<String, String> params = new LinkedHashMap<>()
        params.put("MerchantId", merchantAccount.getMerchantCode())
        params.put("Timestamp", DateUtils.format(new Date(), DateUtils.YYYYMMDDHHMMSS))
        params.put("Sign", MD5.md5(MD5.toAscii(params) + merchantAccount.getPrivateKey()))

        log.info("XiangYunScript_Query_Balance_Params: {}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                getRequestHeard(null, null, null, GlActionEnum.PAYMENT_QUERY_BALANCE.getCode(),merchantAccount.getChannelId(),merchantAccount.getChannelName())
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/InterfaceV6/GetBalanceAmount/", params,  requestHeader)
        log.info("XiangYunScript_Query_Balance_resStr: {}", resStr)

        if (StringUtils.isEmpty(resStr)) {
            return BigDecimal.ZERO
        }
        JSONObject json = JSON.parseObject(resStr)
        if ("0" != (json.getString("Code"))) {
            return null
        }
        BigDecimal balance = json.getBigDecimal("BalanceAmount")
        return balance == null ? BigDecimal.ZERO : balance
    }



    /**
     * 获取头部信息
     *
     * @param userId
     * @param userName
     * @param orderId
     * @return
     */
    private GlRequestHeader getRequestHeard(String userId, String userName, String orderId, String code, Integer channelId, String channelName) {
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