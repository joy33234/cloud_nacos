package com.seektop.fund.payment.hengchengpay

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
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode

/**
 * @desc 恒成支付
 * @auth Otto
 * @date 2022-03-11
 */

public class HengChengScript_withdraw {
    private static final Logger log = LoggerFactory.getLogger(HengChengScript_withdraw.class)

    private OkHttpUtil okHttpUtil
    private static final String SERVER_WITHDRAW_URL = "/api/merchant/withdraw"
    private static final String SERVER_QUERY_URL = "/api/merchant/withdraw/query"
    private static final String SERVER_BALANCE_URL = "/api/merchant/info"

    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness

    WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount account = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        this.glPaymentChannelBankBusiness = com.seektop.fund.payment.groovy.BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        WithdrawResult result = new WithdrawResult()

        Map<String, Object> DataContentParms = new TreeMap<>()
        DataContentParms.put("merchant", account.getMerchantCode())
        DataContentParms.put("requestReference", req.getOrderId())
        DataContentParms.put("merchantBank", glPaymentChannelBankBusiness.getBankCode(req.getBankId(), account.getChannelId()))
        DataContentParms.put("merchantBankCardRealname", req.getName())
        DataContentParms.put("merchantBankCardAccount", req.getCardNo())
        DataContentParms.put("merchantBankCardProvince", "上海市")
        DataContentParms.put("merchantBankCardCity", "上海市")
        DataContentParms.put("merchantBankCardBranch", "上海市")
        DataContentParms.put("amount", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString())
        DataContentParms.put("callback", account.getNotifyUrl() + account.getMerchantId())

        String toSign = MD5.toSign(DataContentParms) + "&key=" + account.getPrivateKey()
        DataContentParms.put("sign", MD5.md5(toSign).toUpperCase())

        log.info("HengChengScript_Transfer_params: {}", JSON.toJSONString(DataContentParms))
        GlRequestHeader requestHeader =
                this.getRequestHeard(req.getUserId() + "", req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(), account.getChannelId(), account.getChannelName())
        String resStr = okHttpUtil.post(account.getPayUrl() + SERVER_WITHDRAW_URL, DataContentParms, requestHeader)
        log.info("HengChengScript_Transfer_resStr: {}", resStr)

        result.setOrderId(req.getOrderId())
        result.setReqData(JSON.toJSONString(DataContentParms))
        result.setResData(resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null) {
            result.setValid(false)
            result.setMessage("API异常:请联系出款商户确认订单.")
            return result
        }
        if (json.getBoolean("success") != true) {
            result.setValid(false)
            result.setMessage(json.getString("message"))
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

        log.info("HengChengScript_withdrawNotify_resMap:{}", JSON.toJSONString(resMap))
        String orderId = resMap.get("requestReference")

        Map<String,String> signMap = new LinkedHashMap();
        signMap.put("reference",resMap.get("reference"));
        signMap.put("amount",resMap.get("amount"));
        signMap.put("success",resMap.get("success"));
        signMap.put("fee",resMap.get("fee"));
        signMap.put("requestReference",resMap.get("requestReference"));
        signMap.put("feeRate",resMap.get("feeRate"));

        String toSign = MD5.toAscii(signMap) + "&key=" + merchant.getPrivateKey()
        toSign = MD5.md5(toSign).toUpperCase()

        if (org.apache.commons.lang3.StringUtils.isNotEmpty(orderId) && toSign == resMap.get("sign")) {
            return withdrawQuery(okHttpUtil, merchant, orderId, resMap.get("reference"))
        }
        log.info("HengChengScript_withdraw_Sign: 回调资料错误或验签失败，orderId：{}", orderId)
        return null;
    }


    WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2]
        String thirdOrderId = args[3]

        Map<String, Object> DataContentParms = new TreeMap<>()
        DataContentParms.put("merchant", merchant.getMerchantCode())
        DataContentParms.put("requestReference", orderId)
        DataContentParms.put("reference", thirdOrderId)

        String toSign = MD5.toSign(DataContentParms) + "&key=" + merchant.getPrivateKey()
        DataContentParms.put("sign", MD5.md5(toSign).toUpperCase())

        log.info("HengChengScript_TransferQuery_order:{}", JSON.toJSONString(DataContentParms))
        GlRequestHeader requestHeader =
                this.getRequestHeard("", "", orderId, GlActionEnum.WITHDRAW_QUERY.getCode(), merchant.getChannelId(), merchant.getChannelName())
        String resStr = okHttpUtil.post(merchant.getPayUrl() + SERVER_QUERY_URL, DataContentParms, requestHeader)
        log.info("HengChengScript_TransferQuery_resStr:{}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || json.getBoolean("success") != true) {
            return null
        }
        JSONObject dataJSON = json.getJSONObject("data")
        if (dataJSON == null) {
            return null
        }
        WithdrawNotify notify = new WithdrawNotify()
        notify.setMerchantCode(merchant.getMerchantCode())
        notify.setMerchantId(merchant.getMerchantId())
        notify.setMerchantName(merchant.getChannelName())
        notify.setOrderId(orderId)
        //状态。[
        //PENDING，待处理。
        //LOCKED，在处理。
        //AUDITED，已支付。
        //REFUSED，已拒绝。
        //]
        String status = dataJSON.getString("status");
        if (status == "AUDITED") {
            notify.setStatus(0)
            notify.setRsp("success")

        } else if (status == "REFUSED") {
            notify.setStatus(1)
            notify.setRsp("success")

        } else {
            notify.setStatus(2)

        }
        return notify
    }


    BigDecimal balanceQuery(Object[] args) {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount

        Map<String, String> DataContentParams = new TreeMap<>()
        DataContentParams.put("merchant", merchantAccount.getMerchantCode())

        String toSign = MD5.toSign(DataContentParams) + "&key=" + merchantAccount.getPrivateKey()
        DataContentParams.put("sign", MD5.md5(toSign).toUpperCase())

        GlRequestHeader requestHeader =
                getRequestHeard("", "", "", GlActionEnum.PAYMENT_QUERY_BALANCE.getCode(), merchantAccount.getChannelId(), merchantAccount.getChannelName())

        log.info("HengChengScript_QueryBalance_reqMap: {}", JSON.toJSONString(DataContentParams))
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + SERVER_BALANCE_URL, DataContentParams, requestHeader)
        log.info("HengChengScript_QueryBalance_resStr: {}", resStr)

        JSONObject responJSON = JSON.parseObject(resStr)
        if (responJSON == null || responJSON.getBoolean("success") != true) {
            return BigDecimal.ZERO
        }
        JSONObject dataJSON = responJSON.getJSONObject("data")
        if (dataJSON != null) {
            return dataJSON.getBigDecimal("wallet") == null ? BigDecimal.ZERO : dataJSON.getBigDecimal("wallet")
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
