package com.seektop.fund.payment.xinxingpay

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
import com.seektop.fund.payment.groovy.BaseScript
import com.seektop.fund.payment.groovy.ResourceEnum
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode

/**
 * @desc 新星支付
 * @date 2021-06-15
 * @auth joy
 */
public class XinXingScript_withdraw {


    private static final Logger log = LoggerFactory.getLogger(XinXingScript_withdraw.class)

    private OkHttpUtil okHttpUtil

    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness


    public WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount account = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

        Map<String, String> params = new LinkedHashMap<>()

        params.put("version", "1.0")
        params.put("merchantId", account.getMerchantCode())
        params.put("batchNo", req.getOrderId())
        params.put("batchAmt", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString())
        params.put("defaultBank", glPaymentChannelBankBusiness.getBankCode(req.getBankId(), account.getChannelId()))
        params.put("accountNum", req.getCardNo())
        params.put("accountName", req.getName())
        params.put("remark", "withdraw")
        params.put("province", "Shanghai")
        params.put("city", "Shanghai")
        params.put("subbranch", "上海支行")
        params.put("notifyUrl", account.getNotifyUrl() + account.getMerchantId())

        String toSign = MD5.toAscii(params)  + account.getPrivateKey()
        params.put("sign", MD5.md5(toSign))
        params.put("signType", "MD5")


        log.info("XinXingScript_doTransfer_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                this.getRequestHeard(req.getUserId() + "", req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(), account.getChannelId(), account.getChannelName())

        String resStr = okHttpUtil.post(account.getPayUrl() + "/batchDirect.html", params, requestHeader)
        log.info("XinXingScript_doTransfer_resp:{}", resStr)
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
        if ("0000" != (json.getString("errCode"))) {
            result.setValid(false)
            result.setMessage(json.getString("errMsg"))
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
        log.info("XinXingScript_doTransferNotify_resp:{}", JSON.toJSONString(resMap))
        String orderId = resMap.get("batchNo")
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
        params.put("version", "1.0")
        params.put("merchantId", merchant.getMerchantCode())
        params.put("batchNo", orderId)

        String toSign = MD5.toAscii(params)  + merchant.getPrivateKey()
        params.put("sign", MD5.md5(toSign))
        params.put("signType", "MD5")

        log.info("XinXingScript_doTransferQuery_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                this.getRequestHeard("", "", orderId, GlActionEnum.WITHDRAW_QUERY.getCode(), merchant.getChannelId(), merchant.getChannelName())
        String resStr = okHttpUtil.post(merchant.getPayUrl() + "/batchQuery.html", params, requestHeader)
        log.info("XinXingScript_doTransferQuery_resp:{}", resStr)
        if (StringUtils.isEmpty(resStr)) {
            return null
        }
        JSONObject json = JSON.parseObject(resStr)
        if (json == null) {
            return null
        }

        WithdrawNotify notify = new WithdrawNotify()
        notify.setMerchantCode(merchant.getMerchantCode())
        notify.setMerchantId(merchant.getMerchantId())
        notify.setMerchantName(merchant.getChannelName())
        notify.setOrderId(orderId)
        notify.setThirdOrderId(json.getString("transactionId"))
        // 支付状态:errCode  0000：执行成功     E415：代付订单已驳回
        if (json.getString("errCode") == ("0000")) {
            notify.setStatus(0)
            notify.setRsp("ok")
        } else if (json.getString("errCode") == ("E415")) {
            notify.setStatus(1)
            notify.setRsp("ok")
        } else {
            notify.setStatus(2)
        }
        return notify
    }

    public BigDecimal balanceQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount

        Map<String, String> params = new LinkedHashMap<>()
        params.put("version", "1.0")
        params.put("merchantId", merchantAccount.getMerchantCode())

        String toSign = MD5.toAscii(params)  + merchantAccount.getPrivateKey()
        params.put("sign", MD5.md5(toSign))
        params.put("signType", "MD5")

        log.info("XinXingScript_Query_Balance_Params: {}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                getRequestHeard(null, null, null, GlActionEnum.PAYMENT_QUERY_BALANCE.getCode(),merchantAccount.getChannelId(),merchantAccount.getChannelName())
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/queryBlance.html", params,  requestHeader)
        log.info("XinXingScript_Query_Balance_resStr: {}", resStr)

        if (StringUtils.isEmpty(resStr)) {
            return BigDecimal.ZERO
        }
        JSONObject json = JSON.parseObject(resStr)
        if (json == null || "0000" != (json.getString("errCode"))) {
            return null
        }
        BigDecimal balance = json.getBigDecimal("totalBlance")
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