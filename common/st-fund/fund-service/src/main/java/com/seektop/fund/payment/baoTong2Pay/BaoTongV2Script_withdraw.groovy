package com.seektop.fund.payment.baoTong2Pay

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
import com.seektop.fund.payment.groovy.ResourceEnum
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode

/**
 * 宝通支付 - 2
 * @author Otto
 * @date 2021-11-21
 */
public class BaoTongV2Script_withdraw {

    private static final Logger log = LoggerFactory.getLogger(BaoTongV2Script_withdraw.class)

    private OkHttpUtil okHttpUtil
    private static final String SERVER_WITHDRAW_URL = "/api/obpay/transfer"
    private static final String SERVER_QUERY_URL = "/api/obpay/getinterorderV2"
    private static final String SERVER_BALANCE_URL = "/api/agentpay/query_balance"

    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness

    WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        this.glPaymentChannelBankBusiness = com.seektop.fund.payment.groovy.BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

        Map<String, String> DataContentParms = new HashMap<>()
        DataContentParms.put("mchId", merchantAccount.getMerchantCode())
        DataContentParms.put("mchOrderNo", req.getOrderId())
        DataContentParms.put("amount", req.getAmount().subtract(req.getFee()).multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.DOWN).toString())
        DataContentParms.put("trueName", req.getName())
        DataContentParms.put("cardNo", req.getCardNo())
        DataContentParms.put("bankType", glPaymentChannelBankBusiness.getBankCode(req.getBankId(), merchantAccount.getChannelId()))
        DataContentParms.put("notifyUrl", merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId())

        String toSign = MD5.toAscii(DataContentParms) + "&key=" +  merchantAccount.getPrivateKey()
        DataContentParms.put("sign", MD5.md5(toSign))

        log.info("BaoTongV2Script_Transfer_params: {}", JSON.toJSONString(DataContentParms))
        GlRequestHeader requestHeader = getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(), merchantAccount.getChannelId(), merchantAccount.getChannelName())

        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + SERVER_WITHDRAW_URL, DataContentParms, 30L, requestHeader)
        log.info("BaoTongV2Script_Transfer_resStr: {}", resStr)

        WithdrawResult result = new WithdrawResult()
        result.setOrderId(req.getOrderId())
        result.setReqData(JSON.toJSONString(DataContentParms))
        result.setResData(resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || "SUCCESS" != json.getString("retCode")) {
            result.setValid(false)
            result.setMessage(json == null ? "API异常:请联系出款商户确认订单." : json.getString("retMsg"))
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
        this.glPaymentChannelBankBusiness = com.seektop.fund.payment.groovy.BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        log.info("BaoTongV2Script_WithdrawNotify_resMap:{}", JSON.toJSONString(resMap))
        String orderid = resMap.get("mchOrderNo")

        Map<String, String> signMap  = new LinkedHashMap<>()
        signMap.put("mchId",resMap.get("mchId"))
        signMap.put("mchOrderNo",resMap.get("mchOrderNo"))
        signMap.put("amount",resMap.get("amount"))
        signMap.put("orderNo",resMap.get("orderNo"))
        signMap.put("status",resMap.get("status"))
        signMap.put("backType",resMap.get("backType"))
        signMap.put("tradeTime",resMap.get("tradeTime"))
        signMap.put("remark",resMap.get("remark"))

        String toSign = MD5.toAscii(signMap) + "&key=" + merchant.getPrivateKey()
        toSign = MD5.md5(toSign).toUpperCase();

        if (org.apache.commons.lang3.StringUtils.isNotEmpty(orderid) && toSign == resMap.get("sign")) {
            return withdrawQuery(okHttpUtil, merchant, orderid )
        }
            log.info("BaoTongV2Script_WithdrawNotify_Sign: 回调资料错误或验签失败 单号：{}", orderid)
            return null

    }


    WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2] as String

        Map<String, String> DataContentParms = new HashMap<String, String>()
        DataContentParms.put("mchId", merchant.getMerchantCode())
        DataContentParms.put("mchOrderNo", orderId)

        String toSign = MD5.toAscii(DataContentParms) + "&key=" + merchant.getPrivateKey()
        DataContentParms.put("sign", MD5.md5(toSign))

        log.info("BaoTongV2Script_TransferQuery_reqMap:{}", JSON.toJSONString(DataContentParms))
        GlRequestHeader requestHeader = getRequestHeard(null, null, orderId, GlActionEnum.WITHDRAW_QUERY.getCode(), merchant.getChannelId(), merchant.getChannelName())
        String resStr = okHttpUtil.post(merchant.getPayUrl() + SERVER_QUERY_URL, DataContentParms, 30L, requestHeader)
        log.info("BaoTongV2Script_TransferQuery_resStr:{}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || json.getString("retCode") != "SUCCESS") {
            return null
        }
        JSONObject dataJSON = json.getJSONObject("data")
        WithdrawNotify notify = new WithdrawNotify()
        if (dataJSON != null) {
            notify.setMerchantCode(merchant.getMerchantCode())
            notify.setMerchantId(merchant.getMerchantId())
            notify.setMerchantName(merchant.getChannelName())
            notify.setOrderId(orderId)
            //0处理中 1处理中 2支付成功 3支付失败 4联系工作人员确认订单
            if (dataJSON.getString("state") == "2") {
                notify.setStatus(0)

            } else if (dataJSON.getString("state") == "3") {
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
        this.glPaymentChannelBankBusiness = com.seektop.fund.payment.groovy.BaseScript.getResource(args[2], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        Map<String, String> DataContentParms = new HashMap<>()
        DataContentParms.put("mchId", merchantAccount.getMerchantCode())
        DataContentParms.put("reqTime", DateUtils.format(new Date(), DateUtils.YYYYMMDDHHMMSS))

        String toSign = MD5.toAscii(DataContentParms) + "&key=" + merchantAccount.getPrivateKey()
        DataContentParms.put("sign", MD5.md5(toSign))

        log.info("BaoTongV2Script_QueryBalance_reqMap: {}", JSON.toJSONString(DataContentParms))
        GlRequestHeader requestHeader =
                getRequestHeard("", "", "", GlActionEnum.PAYMENT_QUERY_BALANCE.getCode(), merchantAccount.getChannelId(), merchantAccount.getChannelName())
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + SERVER_BALANCE_URL, DataContentParms, 30L, requestHeader)
        log.info("BaoTongV2Script_QueryBalance_resStr: {}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || json.getString("retCode") != "SUCCESS") {
            return BigDecimal.ZERO
            return json.getBigDecimal("money") == null ? BigDecimal.ZERO : json.getBigDecimal("money")
        }
        BigDecimal balance = json.getBigDecimal("availableAgentpayBalance").divide(BigDecimal.valueOf(100))
        return balance == null ? BigDecimal.ZERO : balance
    }

    private GlRequestHeader getRequestHeard(String userId, String userName, String orderId, String code, Integer channelId, String channleName) {
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(code)
                .channelId(channelId + "")
                .channelName(channleName)
                .userId(userId)
                .userName(userName)
                .tradeId(orderId)
                .build()
        return requestHeader
    }



}