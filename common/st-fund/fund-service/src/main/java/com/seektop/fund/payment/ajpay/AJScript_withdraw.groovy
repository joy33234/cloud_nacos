package com.seektop.fund.payment.ajpay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONArray
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
import org.apache.commons.lang3.ObjectUtils
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode

/**
 * @desc 卡卡内充支付
 * @date 2021-06-22
 * @auth joy
 */
public class AJScript_withdraw {


    private static final Logger log = LoggerFactory.getLogger(AJScript_withdraw.class)

    private OkHttpUtil okHttpUtil

    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness


    public WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount account = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        this.glPaymentChannelBankBusiness = com.seektop.fund.payment.groovy.BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

        Map<String, String> params = new LinkedHashMap<>()
        params.put("mchid", account.getMerchantCode())
        params.put("addtime", System.currentTimeSeconds().toString())
        params.put("bankcode", "unionpay")
        params.put("callback_url", account.getNotifyUrl() + account.getMerchantId())

        JSONArray jsonArray = new JSONArray();
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("out_trade_no",req.getOrderId())
        jsonObject.put("amount",req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString())
        jsonObject.put("accountname",req.getName())
        jsonObject.put("bankname",glPaymentChannelBankBusiness.getBankName(req.getBankId(), account.getChannelId()))
        jsonObject.put("cardnumber",req.getCardNo())
        jsonObject.put("subbranch","上海市")
        jsonObject.put("province","上海市")
        jsonObject.put("city","上海市")
        jsonObject.put("mobile","13611111111")
        jsonObject.put("attach","")
        jsonArray.add(jsonObject)
        params.put("list", jsonArray.toJSONString())

        String toSign = MD5.toAscii(params)  + "&key=" + account.getPrivateKey()
        params.put("sign", MD5.md5(toSign).toUpperCase())

        log.info("AjScript_doTransfer_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                this.getRequestHeard(req.getUserId() + "", req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(), account.getChannelId(), account.getChannelName())

        String resStr = okHttpUtil.post(account.getPayUrl() + "/Payment_index.html", params, requestHeader)
        log.info("AjScript_doTransfer_resp:{}", resStr)

        WithdrawResult result = new WithdrawResult()
        result.setOrderId(req.getOrderId())
        result.setReqData(JSON.toJSONString(params))
        result.setResData(resStr)

        if (StringUtils.isEmpty(resStr)) {
            result.setValid(false)
            result.setMessage("API异常:请联系出款商户确认订单.")
            return result
        }
        JSONObject json = JSON.parseObject(resStr)
        if (json == null) {
            result.setValid(false)
            result.setMessage(json.getString("message"))
            return result
        }
        if (json.getString("status") != "success" || ObjectUtils.isEmpty(json.getJSONObject("data"))
                || json.getJSONObject("data").getString("status") != "1") {//提交状态,1为成功,0为失败
            result.setValid(false)
            result.setMessage(json.getJSONObject("data").getString("msg"))
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
        log.info("AjScript_doTransferNotify_resp:{}", JSON.toJSONString(resMap))
        String orderId = resMap.get("out_trade_no")
        if (StringUtils.isEmpty(orderId)) {
            orderId = JSONObject.parseObject(resMap.get("reqBody")).getString("out_trade_no");
        }
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
        params.put("mchid", merchant.getMerchantCode())
        params.put("out_trade_no", orderId)
        params.put("applytime", System.currentTimeSeconds().toString())

        String toSign = MD5.toAscii(params)  + "&key=" + merchant.getPrivateKey()
        params.put("sign", MD5.md5(toSign).toUpperCase())

        log.info("AjScript_doTransferQuery_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                this.getRequestHeard("", "", orderId, GlActionEnum.WITHDRAW_QUERY.getCode(), merchant.getChannelId(), merchant.getChannelName())
        String resStr = okHttpUtil.post(merchant.getPayUrl() + "/Payment_dfpay_query.html", params, requestHeader)
        log.info("AjScript_doTransferQuery_resp:{}", resStr)
        if (StringUtils.isEmpty(resStr)) {
            return null
        }
        JSONObject json = JSON.parseObject(resStr)
        //请求状态,0为失败,1为成功；
        if (json == null || json.getString("status") != "1" || ObjectUtils.isEmpty(json.getJSONObject("data"))) {
            return null
        }
        JSONObject dataJSON = json.getJSONObject("data")
        WithdrawNotify notify = new WithdrawNotify()
        notify.setMerchantCode(merchant.getMerchantCode())
        notify.setMerchantId(merchant.getMerchantId())
        notify.setMerchantName(merchant.getChannelName())
        notify.setOrderId(orderId)
        notify.setThirdOrderId(dataJSON.getString("orderid"))
        // 代付状态,0或1为等待付款,2为已完成,3为处理中,4为已驳回,5为待核对
        if (dataJSON.getString("status") == "2") {
            notify.setStatus(0)
            notify.setRsp("ok")
        } else if (dataJSON.getString("status") == "4") {
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
        params.put("mchid", merchantAccount.getMerchantCode())
        params.put("timestamp", System.currentTimeSeconds().toString())

        String toSign = MD5.toAscii(params)  + "&key=" + merchantAccount.getPrivateKey()
        params.put("sign", MD5.md5(toSign).toUpperCase())

        log.info("AjScript_Query_Balance_Params: {}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                getRequestHeard(null, null, null, GlActionEnum.PAYMENT_QUERY_BALANCE.getCode(),merchantAccount.getChannelId(),merchantAccount.getChannelName())
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/Payment_dfpay_balance.html", params,  requestHeader)
        log.info("AjScript_Query_Balance_resStr: {}", resStr)

        if (StringUtils.isEmpty(resStr)) {
            return BigDecimal.ZERO
        }
        JSONObject json = JSON.parseObject(resStr)
        //请求状态,0为失败,1为成功；
        if (json == null || "1" != (json.getString("status")) || ObjectUtils.isEmpty(json.getJSONObject("data"))) {
            return null
        }
        BigDecimal balance = json.getJSONObject("data").getBigDecimal("balance")
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