package com.seektop.fund.payment.zhousipay

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
 * @desc  宙斯支付
 * @date 2021-04-15
 * @auth joy
 */
public class ZhousiScript_withdraw {


    private static final Logger log = LoggerFactory.getLogger(ZhousiScript_withdraw.class)

    private OkHttpUtil okHttpUtil

    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness


    public WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount account = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        this.glPaymentChannelBankBusiness = com.seektop.fund.payment.groovy.BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

        Map<String, String> params = new LinkedHashMap<>()
        params.put("client_id", account.getMerchantCode())
        params.put("slug", "CTC")
        params.put("order_id", req.getOrderId())
        params.put("amount", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString())
        params.put("bank_code", glPaymentChannelBankBusiness.getBankCode(req.getBankId(), account.getChannelId()))
        params.put("bank_name", glPaymentChannelBankBusiness.getBankName(req.getBankId(), account.getChannelId()))
        params.put("bank_branch", "上海市")
        params.put("province", "上海市")
        params.put("city", "上海市")
        params.put("bank_account_name", req.getName())
        params.put("bank_account", req.getCardNo())
        params.put("key",account.getPrivateKey())
        params.put("sign", MD5.md5(MD5.toAscii(params)))
        params.remove("key")


        log.info("ZhousiScript_doTransfer_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                this.getRequestHeard(req.getUserId() + "", req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(), account.getChannelId(), account.getChannelName())

        String resStr = okHttpUtil.postJSON(account.getPayUrl() + "/replace", JSON.toJSONString(params), requestHeader)
        log.info("ZhousiScript_doTransfer_resp:{}", resStr)
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

        if ("success" != (json.getString("result"))) {
            result.setValid(false)
            result.setMessage(json.getString("error"))
            return result
        }
        req.setMerchantId(account.getMerchantId())
        result.setValid(true)
        result.setMessage("ok")
        return result
    }

    public WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        Map<String, String> resMap = args[2] as Map<String, String>
        log.info("ZhousiScript_doTransferNotify_resp:{}", JSON.toJSONString(resMap))
        JSONObject json = JSONObject.parseObject(resMap.get("reqBody"))
        String orderId = json.getString("order_id")
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
        params.put("client_id", merchant.getMerchantCode())
        params.put("order_id", orderId)
        params.put("key",merchant.getPrivateKey())
        params.put("sign", MD5.md5(MD5.toAscii(params)))
        params.remove("key")

        log.info("ZhousiScript_doTransferQuery_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                this.getRequestHeard("", "", orderId, GlActionEnum.WITHDRAW_QUERY.getCode(), merchant.getChannelId(), merchant.getChannelName())
        String resStr = okHttpUtil.postJSON(merchant.getPayUrl() + "/transaction_check", JSONObject.toJSONString(params), requestHeader)
        log.info("ZhousiScript_doTransferQuery_resp:{}", resStr)
        if (StringUtils.isEmpty(resStr)) {
            return null
        }
        JSONObject json = JSON.parseObject(resStr)
        if ("success" != (json.getString("result"))) {
            return null
        }
        JSONObject dataJSON = json.getJSONObject("data")
        if (dataJSON == null) {
            return null
        }
        WithdrawNotify notify = new WithdrawNotify()
        notify.setAmount(dataJSON.getBigDecimal("actual_amount"))
        notify.setMerchantCode(merchant.getMerchantCode())
        notify.setMerchantId(merchant.getMerchantId())
        notify.setMerchantName(merchant.getChannelName())
        notify.setOrderId(orderId)
        notify.setThirdOrderId(dataJSON.getString("transaction_id"))
        // 支付状态:status  0:等待  1:成功  2:逾期  3:处理中  4:取消  5:冲正
        if (dataJSON.getString("status") == ("1")) {
            notify.setStatus(0)
            notify.setRsp("{\"result\":\"success\"}")
        } else if (dataJSON.getString("status") == ("4") || dataJSON.getString("status") == ("5")) {
            notify.setStatus(1)
            notify.setRsp("{\"result\":\"success\"}")
        } else {
            notify.setStatus(2)
        }
        return notify
    }

    public BigDecimal balanceQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount

        Map<String, String> params = new LinkedHashMap<>()
        params.put("client_id", merchantAccount.getMerchantCode())
        params.put("key",merchantAccount.getPrivateKey())
        params.put("sign", MD5.md5(MD5.toAscii(params)))
        params.remove("key")

        log.info("ZhousiScript_Query_Balance_Params: {}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                getRequestHeard(null, null, null, GlActionEnum.PAYMENT_QUERY_BALANCE.getCode(),merchantAccount.getChannelId(),merchantAccount.getChannelName())
        String resStr = okHttpUtil.postJSON(merchantAccount.getPayUrl() + "/balance", JSONObject.toJSONString(params),  requestHeader)
        log.info("ZhousiScript_Query_Balance_resStr: {}", resStr)

        if (StringUtils.isEmpty(resStr)) {
            return BigDecimal.ZERO
        }
        JSONObject json = JSON.parseObject(resStr)
        if ("success" != (json.getString("result"))) {
            return null
        }
        JSONObject dataJSON = json.getJSONObject("data");
        if (dataJSON == null) {
            return BigDecimal.ZERO
        }
        BigDecimal balance = dataJSON.getBigDecimal("balance")
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