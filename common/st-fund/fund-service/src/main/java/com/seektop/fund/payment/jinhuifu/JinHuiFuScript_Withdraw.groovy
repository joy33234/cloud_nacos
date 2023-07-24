package com.seektop.fund.payment.jinhuifu

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONArray
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
import com.seektop.common.utils.MD5
import com.seektop.enumerate.GlActionEnum
import com.seektop.exception.GlobalException
import com.seektop.fund.business.GlPaymentChannelBankBusiness
import com.seektop.fund.mapper.GlWithdrawMapper
import com.seektop.fund.model.GlWithdraw
import com.seektop.fund.model.GlWithdrawMerchantAccount
import com.seektop.fund.payment.PaymentMerchantEnum
import com.seektop.fund.payment.WithdrawNotify
import com.seektop.fund.payment.WithdrawResult
import com.seektop.fund.payment.groovy.BaseScript
import com.seektop.fund.payment.groovy.ResourceEnum
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode

/**
 * 金汇富支付
 */

public class JinHuiFuScript_Withdraw {

    private static final Logger log = LoggerFactory.getLogger(JinHuiFuScript_Withdraw.class)

    private GlWithdrawMapper glWithdrawMapper

    private OkHttpUtil okHttpUtil

    private GlPaymentChannelBankBusiness paymentChannelBankBusiness

    WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        this.paymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        Map<String, String> params = new HashMap<>()
        params.put("merchant_code", merchantAccount.getMerchantCode())
        params.put("order_id", req.getOrderId())
        params.put("amount", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString())
        params.put("name", req.getName())
        params.put("bank", paymentChannelBankBusiness.getBankName(req.getBankId(), merchantAccount.getChannelId()))
        params.put("branch", "Shanghai")
        params.put("accountnumber", req.getCardNo())
        params.put("callback_url", merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId())
        String time = System.currentTimeMillis().toString()
        params.put("timestamp", time.toString().substring(0, time.length() - 3))

        String toSign = MD5.toAscii(params) + "&" + merchantAccount.getPrivateKey()
        params.put("sign", MD5.md5(toSign))

        log.info("JinHuiFu_Script_Transfer_params = {}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = this.getRequestHeard(req.getUserId() + "", req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode())
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/v4/merchant/withdraw", params, requestHeader)
        log.info("JinHuiFu_Script_Transfer_resStr = {}", resStr)


        WithdrawResult result = new WithdrawResult()
        result.setOrderId(req.getOrderId())
        result.setReqData(JSON.toJSONString(params))
        result.setResData(resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || "true" != (json.getString("status"))) {
            result.setValid(false)
            result.setMessage(json == null ? "API异常:请联系出款商户确认订单." : json.getString("message"))
            return result
        }
        result.setValid(true)
        result.setMessage("")
        return result
    }

    //回调有两步  第一步是批准待出款  状态：APPROVED    第二步是已完成出款 状态：DISPENSED
    WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        Map<String, String> resMap = args[2] as Map<String, String>
        log.info("JinHuiFu_Script_Notify_resMap:{}", JSON.toJSONString(resMap))
        JSONObject json = JSONObject.parseObject(resMap.get("reqBody"))
        String orderId = json.getString("order_id")
        if (StringUtils.isNotEmpty(orderId)) {
            return withdrawQuery(okHttpUtil, merchant, orderId, args[3])
        } else {
            return null
        }
    }

    WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2]
        this.glWithdrawMapper = BaseScript.getResource(args[3], ResourceEnum.GlWithdrawMapper) as GlWithdrawMapper
        GlWithdraw glWithdraw = glWithdrawMapper.selectByPrimaryKey(orderId)
        if (glWithdraw == null) {
            return null
        }
        Map<String, String> params = new HashMap<String, String>()
        params.put("merchant_code", merchant.getMerchantCode())
        params.put("order_id", orderId)

        String toSign = MD5.toAscii(params) + "&" + merchant.getPrivateKey()
        params.put("sign", MD5.md5(toSign))

        log.info("JinHuiFu_Script_TransferQuery_reqMap:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = this.getRequestHeard("", "", orderId, GlActionEnum.WITHDRAW_QUERY.getCode())
        String resStr = okHttpUtil.post(merchant.getPayUrl() + "/v4/merchant/withdraw/query", params, requestHeader)
        log.info("JinHuiFu_Script_TransferQuery_resStr:{}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null) {
            return null
        }
        WithdrawNotify notify = new WithdrawNotify()
        if ("true" == (json.getString("status"))) {
            JSONArray dataArr = json.getJSONArray("data")
            JSONObject dataJson = dataArr.getJSONObject(0)
            notify.setAmount(glWithdraw.getAmount().subtract(glWithdraw.getFee()))
            notify.setMerchantCode(merchant.getMerchantCode())
            notify.setMerchantId(merchant.getMerchantId())
            notify.setMerchantName(merchant.getChannelName())
            notify.setOrderId(orderId)
            notify.setThirdOrderId("")
            //"REJECTED/APPROVED/PENDING/DISPENSED //拒绝/批准待出款/待支付/已完成出款    商户返回出款状态：0成功，1失败,2处理中
            if (dataJson.getString("status").equalsIgnoreCase("DISPENSED")) {
                notify.setStatus(0)
            } else if (dataJson.getString("status").equalsIgnoreCase("REJECTED")) {
                notify.setStatus(1)
            } else {
                notify.setStatus(2)
            }
        }
        return notify
    }


    BigDecimal balanceQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        Map<String, String> params = new HashMap<>()
        params.put("merchant_code", merchantAccount.getMerchantCode())
        String time = System.currentTimeMillis().toString()
        params.put("time", time.toString().substring(0, time.length() - 3))

        String toSign = MD5.toAscii(params) + "&" + merchantAccount.getPrivateKey()
        params.put("sign", MD5.md5(toSign))

        log.info("JinHuiFu_Script_QueryBalance_reqMap = {}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = this.getRequestHeard("", "", "", GlActionEnum.PAYMENT_QUERY_BALANCE.getCode())
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/v4/merchant/balance", params, requestHeader)
        log.info("JinHuiFu_Script_QueryBalance_resStr = {}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json != null && "true" == (json.getString("status"))) {
            JSONArray dataArr = json.getJSONArray("data")
            JSONObject dataJson = dataArr.getJSONObject(0)
            if (dataJson != null) {
                return dataJson.getBigDecimal("balance") == null ? BigDecimal.ZERO : dataJson.getBigDecimal("balance")
            }
        }
        return BigDecimal.ZERO
    }


    /**
     * 获取头部信息
     *
     * @param userId
     * @param userName
     * @param orderId
     * @return
     */
    private GlRequestHeader getRequestHeard(String userId, String userName, String orderId, String code) {
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(code)
                .channelId(PaymentMerchantEnum.JINHUIFU.getCode() + "")
                .channelName(PaymentMerchantEnum.ZHONGQIFU_PAY.getPaymentName())
                .userId(userId)
                .userName(userName)
                .tradeId(orderId)
                .build()
        return requestHeader
    }
}