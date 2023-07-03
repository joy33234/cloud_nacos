package com.seektop.fund.payment.ubpay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
import com.seektop.common.utils.MD5
import com.seektop.enumerate.GlActionEnum
import com.seektop.exception.GlobalException
import com.seektop.fund.business.withdraw.GlWithdrawBusiness
import com.seektop.fund.business.withdraw.GlWithdrawReceiveInfoBusiness
import com.seektop.fund.model.GlWithdraw
import com.seektop.fund.model.GlWithdrawMerchantAccount
import com.seektop.fund.model.GlWithdrawReceiveInfo
import com.seektop.fund.payment.WithdrawNotify
import com.seektop.fund.payment.WithdrawResult
import com.seektop.fund.payment.groovy.ResourceEnum
import org.apache.commons.lang.StringUtils
import org.apache.commons.lang3.ObjectUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode

/**
 * UBPAY
 * USDT商户
 *
 * @author joy
 * @Date 2021-01-09
 */
public class UbPayScript_Withdraw {

    private static final Logger log = LoggerFactory.getLogger(UbPayScript_Withdraw.class)

    private OkHttpUtil okHttpUtil

    private GlWithdrawBusiness withdrawBusiness

    private GlWithdrawReceiveInfoBusiness glWithdrawReceiveInfoBusiness


    public WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        this.withdrawBusiness = com.seektop.fund.payment.groovy.BaseScript.getResource(args[3], ResourceEnum.GlWithdrawBusiness) as GlWithdrawBusiness
        String keyValue = merchantAccount.getPrivateKey() // 商家密钥

        BigDecimal usdtRate = withdrawBusiness.getWithdrawRate(req.getUserType());

        BigDecimal amount = req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN);
        BigDecimal usdtAmount = amount.divide(usdtRate, 2, RoundingMode.DOWN)

        Map<String, String> paramMap = new HashMap<>()
        paramMap.put("out_trade_no", req.getOrderId())
        paramMap.put("address", req.getAddress())
        paramMap.put("amount", usdtAmount)
        paramMap.put("callback_url", merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId())

        Map<String, String> head = new HashMap<>()
        head.put("Authorization","Bearer " + keyValue)

        String toSign = MD5.toAscii(paramMap) + merchantAccount.getPrivateKey() + merchantAccount.getPublicKey()
        paramMap.put("sign", MD5.md5(toSign))

        log.info("UbPayScript_Withdrawge_Transfer_paramMap = {}", JSON.toJSONString(paramMap))
        GlRequestHeader requestHeader = this.getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(), merchantAccount.getChannelId(), merchantAccount.getChannelName())
        String payUrl = merchantAccount.getPayUrl() + "/api/payment"
        String respStr = okHttpUtil.postJSON(payUrl, JSON.toJSONString(paramMap), head, requestHeader)
        log.info("UbPayScript_Withdrawge_Transfer_respStr = {}", JSON.toJSONString(respStr))

        JSONObject respJson = JSON.parseObject(respStr)
        WithdrawResult result = new WithdrawResult()
        result.setOrderId(req.getOrderId())
        result.setReqData(JSON.toJSONString(paramMap))
        result.setResData(respStr)
        result.setRate(usdtRate)
        result.setUsdtAmount(usdtAmount)

        if (StringUtils.isEmpty(respStr) || null == respJson) {
            result.setValid(false)
            result.setMessage("API异常:请联系出款商户确认订单.")
            return result
        }

        if (respJson.getBoolean("success")) {
            JSONObject dataJSON = respJson.getJSONObject("data")
            if (ObjectUtils.isNotEmpty(dataJSON) && dataJSON.getString("state") == "new") {
                result.setValid(true)
                result.setMessage("Success")
                result.setThirdOrderId(dataJSON.getString("trade_no"))
                return result
            }
        }
        result.setValid(false)
        result.setMessage(respJson.getString("message"))
        return result

    }

    public WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        Map<String, String> resMap = args[2] as Map<String, String>
        log.info("UbPayScript_Withdrawge_Notify_resMap:{}", JSON.toJSONString(resMap))
        JSONObject json = JSONObject.parseObject(resMap.get("reqBody"))
        String orderId = json.getString("out_trade_no")
        String thirdOrderId = json.getString("trade_no")
        if (StringUtils.isNotEmpty(orderId) && StringUtils.isNotEmpty(thirdOrderId)) {
            return withdrawQuery(okHttpUtil, merchant, orderId, thirdOrderId, args[3])
        }
        return null
    }




    public WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        this.glWithdrawReceiveInfoBusiness = com.seektop.fund.payment.groovy.BaseScript.getResource(args[4], ResourceEnum.GlWithdrawReceiveInfoBusiness) as GlWithdrawReceiveInfoBusiness
        String orderId = args[2] as String

        Map<String, String> head = new HashMap<>()
        head.put("Authorization","Bearer " + merchant.getPrivateKey())
        head.put("Accept", "application/json")
        head.put("Content-Type", "application/json")

        String queryUrl = merchant.getPayUrl() + "/api/payment/" + orderId

        GlRequestHeader requestHeader = this.getRequestHeard(null, null, orderId, GlActionEnum.WITHDRAW_QUERY.getCode(), merchant.getChannelId(), merchant.getChannelName())
        String respStr = okHttpUtil.get(queryUrl, null, requestHeader, head)

        JSONObject respJson = JSON.parseObject(respStr)
        log.info("UbPayScript_Withdrawge_TransferQuery_result = {}", respJson)
        if (null == respJson || !respJson.getBoolean("success")) {
            return null
        }
        JSONObject dataJson = respJson.getJSONObject("data")

        if (null != dataJson && dataJson.getString("out_trade_no").equals(orderId)) {
            WithdrawNotify notify = new WithdrawNotify()
            notify.setMerchantCode(merchant.getMerchantCode())
            notify.setMerchantId(merchant.getMerchantId())
            notify.setMerchantName(merchant.getChannelName())
            notify.setOrderId(orderId)
            notify.setRemark(dataJson.getString("notes"))
            notify.setThirdOrderId(dataJson.getString("id"))


            String status = dataJson.getString("state")
            //new => 新订单 processing => 处理中  reject => 拒绝 completed => 成功 failed => 失败 refund => 冲回
            if (status == "completed") {
                notify.setStatus(0)
                notify.setRsp("ok")
                GlWithdrawReceiveInfo receiveInfo = glWithdrawReceiveInfoBusiness.findById(orderId)
                if (receiveInfo != null) {
                    notify.setActualRate(receiveInfo.getRate())
                    notify.setActualAmount(receiveInfo.getUsdtAmount())
                }
            } else if (status == "failed" || status == "refund" || status == "reject") {
                notify.setStatus(1)
                notify.setRsp("ok")
            } else {
                notify.setStatus(2)
            }
            return notify
        }
        return null
    }

    public BigDecimal balanceQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount

        Map<String, String> head = new HashMap<>()
        head.put("Authorization","Bearer " + merchantAccount.getPrivateKey())
        head.put("Accept", "application/json")
        head.put("Content-Type", "application/json")

        GlRequestHeader requestHeader = this.getRequestHeard(null, null, null, GlActionEnum.PAYMENT_QUERY_BALANCE.getCode(), merchantAccount.getChannelId(), merchantAccount.getChannelName())
        String queryUrl = merchantAccount.getPayUrl() + "/api/balance/inquiry"
        String respStr = okHttpUtil.get(queryUrl, null, requestHeader, head)
        log.info("UbPayScript_Withdrawge_Transfer_result = {}", respStr)

        JSONObject respJson = JSON.parseObject(respStr)
        if (null == respJson || !respJson.getBoolean("success")) {
            return BigDecimal.ZERO
        }

        JSONObject dataJSON = respJson.getJSONObject("data")
        if (dataJSON == null) {
            return BigDecimal.ZERO
        }
        BigDecimal balance = dataJSON.getBigDecimal("balance")
        return balance == null ? BigDecimal.ZERO : balance
    }

    private GlRequestHeader getRequestHeard(String userId, String userName, String orderId, String code, Integer channelId, String channelName) {
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(code)
                .channelId(channelId.toString())
                .channelName(channelName)
                .userId(userId)
                .userName(userName)
                .tradeId(orderId)
                .build()
        return requestHeader
    }

}
