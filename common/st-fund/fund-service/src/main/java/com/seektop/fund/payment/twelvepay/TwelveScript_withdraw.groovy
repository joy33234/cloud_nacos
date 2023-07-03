package com.seektop.fund.payment.twelvepay

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
import org.apache.commons.lang3.ObjectUtils
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode

/**
 * @desc 12支付
 * @date 2021-07-09
 * @auth joy
 */
public class TwelveScript_withdraw {


    private static final Logger log = LoggerFactory.getLogger(TwelveScript_withdraw.class)

    private OkHttpUtil okHttpUtil

    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness


    public WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount account = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        this.glPaymentChannelBankBusiness = com.seektop.fund.payment.groovy.BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

        Map<String, String> params = new LinkedHashMap<>()
        params.put("orderid", req.getOrderId())
        params.put("mname", account.getMerchantCode())
        params.put("mid", account.getMerchantCode())
        params.put("money", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString())
        params.put("banknum", req.getCardNo())
        params.put("bankname", req.getName())
        params.put("types", "1")
        params.put("banktype", glPaymentChannelBankBusiness.getBankCode(req.getBankId(), account.getChannelId()))
        params.put("remark", "withdraw")
        params.put("returnurl",  account.getNotifyUrl() + account.getMerchantId())
        params.put("countrys", "china")
        params.put("ip", req.getIp())

        String toSign = account.getPrivateKey() + params.get("mid") + params.get("mname")  + params.get("money") + params.get("orderid") + params.get("banknum") + params.get("bankname") + params.get("returnurl") + params.get("countrys")
        params.put("sign", MD5.md5(toSign))

        log.info("TwelveScript_doTransfer_params：{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                this.getRequestHeard(req.getUserId() + "", req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(), account.getChannelId(), account.getChannelName())

        String resStr = okHttpUtil.post(account.getPayUrl() + "/withdraw", params, requestHeader)
        log.info("TwelveScript_doTransfer_resp:{}", resStr)
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
        JSONObject dataJSON = json.getJSONObject("data")
        if ("1" != json.getString("code") || StringUtils.isEmpty(dataJSON.getString("platformid"))) {
            result.setValid(false)
            result.setMessage(json.getString("msg"))
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
        log.info("TwelveScript_doTransferNotify_resp:{}", JSON.toJSONString(resMap))
        String orderId = resMap.get("orderid")
        String money = resMap.get("money")
        if (StringUtils.isNotEmpty(orderId) && StringUtils.isNotEmpty(money)) {
            return this.withdrawQuery(okHttpUtil, merchant, orderId, money, args[3])
        }
        return null
    }

    public WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2] as String
        String money = args[3] as String

        Map<String, String> params = new LinkedHashMap<>()
        params.put("orderid", orderId)
        params.put("mname", merchant.getMerchantCode())
        params.put("mid", merchant.getMerchantCode())
        params.put("countrys", "china")
        params.put("money", money)

        String toSign = merchant.getPrivateKey() + params.get("mid") + params.get("mname") + params.get("countrys")  + params.get("money") + params.get("orderid")
        params.put("sign", MD5.md5(toSign))

        log.info("TwelveScript_doTransferQuery_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                this.getRequestHeard("", "", orderId, GlActionEnum.WITHDRAW_QUERY.getCode(), merchant.getChannelId(), merchant.getChannelName())
        String resStr = okHttpUtil.post(merchant.getPayUrl() + "/wcheckorder",params , requestHeader)
        log.info("TwelveScript_doTransferQuery_resp:{}", resStr)
        if (StringUtils.isEmpty(resStr)) {
            return null
        }
        JSONObject json = JSON.parseObject(resStr)
        if (json == null || json.getString("code") != "1") {
            return null
        }
        JSONObject dataJSON = json.getJSONObject("data")
        if (ObjectUtils.isEmpty(dataJSON)) {
            return null
        }
        WithdrawNotify notify = new WithdrawNotify()
        notify.setMerchantCode(merchant.getMerchantCode())
        notify.setMerchantId(merchant.getMerchantId())
        notify.setMerchantName(merchant.getChannelName())
        notify.setOrderId(orderId)
        notify.setThirdOrderId(dataJSON.getString("platformid"))
        // 支付状态:status 状态 0 创建 1处理中 2成功 3 失败 5冲正
        if (dataJSON.getString("mstatus") == ("2")) {
            notify.setStatus(0)
            notify.setRsp("ok")
        } else if (dataJSON.getString("mstatus") == ("3") || json.getString("mstatus") == "5") {
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
        params.put("ip", "1.1.1.1")
        params.put("mname", merchantAccount.getMerchantCode())
        params.put("mid", merchantAccount.getMerchantCode())
        params.put("types", "666")
        params.put("countrys", "china")

        String toSign = merchantAccount.getPrivateKey() + params.get("mid") + params.get("mname") + params.get("types") + params.get("ip")
        params.put("sign", MD5.md5(toSign))

        log.info("TwelveScript_Query_Balance_Params: {}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                getRequestHeard(null, null, null, GlActionEnum.PAYMENT_QUERY_BALANCE.getCode(),merchantAccount.getChannelId(),merchantAccount.getChannelName())
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/samount", params,  requestHeader)
        log.info("TwelveScript_Query_Balance_resStr: {}", resStr)

        if (StringUtils.isEmpty(resStr)) {
            return BigDecimal.ZERO
        }
        JSONObject json = JSON.parseObject(resStr)
        if (json == null || "1" != (json.getString("code"))) {
            return BigDecimal.ZERO
        }
        JSONObject dataJSON = json.getJSONObject("data")
        if (ObjectUtils.isEmpty(dataJSON) || ObjectUtils.isEmpty(dataJSON.getBigDecimal("mbalance"))) {
            return BigDecimal.ZERO
        }
        return dataJSON.getBigDecimal("mbalance")
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