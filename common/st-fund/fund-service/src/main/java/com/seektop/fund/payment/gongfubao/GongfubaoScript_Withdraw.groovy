package com.seektop.fund.payment.gongfubao

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
import com.seektop.fund.payment.RechargeNotify
import com.seektop.fund.payment.WithdrawNotify
import com.seektop.fund.payment.WithdrawResult
import com.seektop.fund.payment.groovy.BaseScript
import com.seektop.fund.payment.groovy.ResourceEnum
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode

public class GongfubaoScript_Withdraw {

    private static final Logger log = LoggerFactory.getLogger(GongfubaoScript_Withdraw.class)

    private OkHttpUtil okHttpUtil

    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness

    public RechargeNotify result(Object[] args) throws GlobalException {
        return notify(args)
    }


    WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount account = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

        Map<String, Object> DataContentParms = new LinkedHashMap<>()
        DataContentParms.put("apply_to_amount", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString())
        DataContentParms.put("bank", glPaymentChannelBankBusiness.getBankName(req.getBankId(), account.getChannelId()))
        DataContentParms.put("bank_branch", "上海市")
        DataContentParms.put("bank_name", req.getName())
        DataContentParms.put("bank_number", req.getCardNo())
        DataContentParms.put("notify_url", account.getNotifyUrl() + account.getMerchantId())
        DataContentParms.put("settlement_no", req.getOrderId())

        String toSign = JSONObject.toJSONString(DataContentParms) + account.getPrivateKey()
        DataContentParms.put("signature", MD5.md5(toSign))

        Map<String, String> headParams = new HashMap<String, String>()
        headParams.put("Authorization", "Bearer " + account.getPublicKey())
        headParams.put("Content-Type", "application/x-www-form-urlencoded")

        log.info("GongfubaoScript_Transfer_params = {}", JSON.toJSONString(DataContentParms))
        GlRequestHeader requestHeader =
                this.getRequestHeard(req.getUserId() + "", req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(), account.getChannelId(), account.getChannelName())
        String resStr = okHttpUtil.post(account.getPayUrl() + "/api/settlement", DataContentParms, requestHeader, headParams)
        log.info("GongfubaoScript_Transfer_resStr = {}", resStr)


        WithdrawResult result = new WithdrawResult()
        result.setOrderId(req.getOrderId())
        result.setReqData(JSON.toJSONString(DataContentParms))
        result.setResData(resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || "true" != json.getString("success")) {
            result.setValid(false)
            result.setMessage(json == null ? "API异常:请联系出款商户确认订单." : json.getString("message"))
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
        log.info("GongfubaoPayer_withdraw_Notify_resMap:{}", JSON.toJSONString(resMap))
        JSONObject json = JSONObject.parseObject(resMap.get("reqBody"))
        String orderId = json.getString("settlement_no")
        if (StringUtils.isEmpty(orderId)) {
            return null
        }
        return withdrawQuery(this.okHttpUtil, merchant, orderId, args[3])
    }

    WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2]

        Map<String, String> headParams = new HashMap<String, String>()
        headParams.put("Authorization", "Bearer " + merchant.getPublicKey())

        log.info("GongfubaoScript_TransferQuery_order = {}", JSON.toJSONString(orderId))
        GlRequestHeader requestHeader =
                this.getRequestHeard("", "", orderId, GlActionEnum.WITHDRAW_QUERY.getCode(), merchant.getChannelId(), merchant.getChannelName())
        String resStr = okHttpUtil.get(merchant.getPayUrl() + "/api/settlement/" + orderId, new HashMap<>(), requestHeader, headParams)
        log.info("GongfubaoScript_TransferQuery_resStr = {}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || json.getInteger("code").intValue() != 200) {
            return null
        }
        WithdrawNotify notify = new WithdrawNotify()
        if (json.getBoolean("status")) {
            notify.setMerchantCode(merchant.getMerchantCode())
            notify.setMerchantId(merchant.getMerchantId())
            notify.setMerchantName(merchant.getChannelName())
            notify.setOrderId(orderId)
            notify.setThirdOrderId("")
            //订单状态判断标准：'apply', 'success', 'fail', 'not found'
            if (json.getString("settlement_status") == "success") {
                notify.setStatus(0)
            } else if (json.getString("settlement_status") == "fail") {
                notify.setStatus(1)
            } else {
                notify.setStatus(2)
            }
        }
        return notify
    }


    BigDecimal balanceQuery(Object[] args) throws GlobalException {
        return BigDecimal.valueOf(-1)
    }

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