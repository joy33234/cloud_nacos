package com.seektop.fund.payment.wutuobangPay

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
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.util.ObjectUtils

import java.math.RoundingMode

/**
 * 乌托邦支付
 * @author joy
 * @date 2021-07-15
 */
public class WuTuoBangScript_withdraw {


    private static final Logger log = LoggerFactory.getLogger(WuTuoBangScript_withdraw.class)

    private OkHttpUtil okHttpUtil

    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness


    WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        this.glPaymentChannelBankBusiness = com.seektop.fund.payment.groovy.BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

        Map<String, String> paramMap = new HashMap<>()
        paramMap.put("infoId", merchantAccount.getMerchantCode())
        paramMap.put("mchOrderNo", req.getOrderId())
        paramMap.put("settAmount", req.getAmount().subtract(req.getFee()).multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.DOWN).toString())
        paramMap.put("accountType", "1")
        paramMap.put("bankName", req.getBankName())
        paramMap.put("accountName", req.getName())
        paramMap.put("accountNo", req.getCardNo())
        paramMap.put("notifyUrl", merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId())

        String toSign = MD5.toAscii(paramMap) + "&key=" + merchantAccount.getPrivateKey()
        paramMap.put("sign", MD5.md5(toSign).toUpperCase())

        log.info("WuTuoBangScript_Transfer_Params: {}", JSON.toJSONString(paramMap))
        GlRequestHeader requestHeader =
                getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(),merchantAccount.getChannelId(),merchantAccount.getChannelName())
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/api/sett/create_order", paramMap,  requestHeader)
        log.info("WuTuoBangScript_Transfer_resStr: {}", resStr)

        WithdrawResult result = new WithdrawResult()
        result.setOrderId(req.getOrderId())
        result.setReqData(JSON.toJSONString(paramMap))
        result.setResData(resStr)

        if (StringUtils.isEmpty(resStr)) {
            result.setValid(false)
            result.setMessage("API异常:请联系出款商户确认订单.")
            return result
        }

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || "SUCCESS" != json.getString("retCode") || ObjectUtils.isEmpty(json.getString("settOrderId"))) {
            result.setValid(false)
            result.setMessage(json == null ? "API异常:请联系出款商户确认订单." : json.getString("retMsg"))
            return result
        }
        result.setValid(true)
        result.setMessage(json.getString("retMsg"))
        result.setThirdOrderId(json.getString("settOrderId"))
        return result
    }

    WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        Map<String, String> resMap = args[2] as Map<String, String>
        log.info("WuTuoBangScript_Transfer_Notify_resMap:{}", JSON.toJSONString(resMap))
        String orderid = resMap.get("mchOrderNo")// 商户订单号
        if (StringUtils.isNotEmpty(orderid)) {
            return withdrawQuery(okHttpUtil, merchant, orderid, args[3])
        }
        return null
    }

    WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2]
        String keyValue = merchant.getPrivateKey() // 商家密钥
        Map<String, String> paramMap = new HashMap<>()
        paramMap.put("infoId", merchant.getMerchantCode())
        paramMap.put("mchOrderNo", orderId)
        paramMap.put("reqTime", DateUtils.format(new Date(), DateUtils.YYYYMMDDHHMMSS))

        String signInfo = MD5.toAscii(paramMap) + "&key=" + keyValue
        paramMap.put("sign", MD5.md5(signInfo).toUpperCase())

        log.info("WuTuoBangScript_Transfer_Query_Params: {}", JSON.toJSONString(paramMap))
        GlRequestHeader requestHeader =
                getRequestHeard(null, null, orderId, GlActionEnum.WITHDRAW_QUERY.getCode(),merchant.getChannelId(),merchant.getChannelName())
        String resStr = okHttpUtil.post(merchant.getPayUrl() + "/api/sett/query_order", paramMap, 30L, requestHeader)
        log.info("WuTuoBangScript_Transfer_Query_resStr: {}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || json.getString("status") == null || json.getString("retCode") != "SUCCESS") {
            return null
        }
        //状态:1-等待审核,2-已审核,3-审核不通过,4-打款中,5-打款成功,6-打款失败
        Integer status = json.getInteger("status")
        WithdrawNotify notify = new WithdrawNotify()
        notify.setMerchantCode(merchant.getMerchantCode())
        notify.setMerchantId(merchant.getMerchantId())
        notify.setMerchantName(merchant.getChannelName())
        notify.setOrderId(orderId)
        notify.setRemark("")
        if (status == 5) {
            notify.setStatus(0)
        } else if (status == 6) {
            notify.setStatus(1)
        } else {
            notify.setStatus(2)
        }
        notify.setThirdOrderId(json.getString("settOrderId"))
        return notify
    }


    BigDecimal balanceQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount

        Map<String, String> paramMap = new HashMap<>()
        paramMap.put("infoId", merchantAccount.getMerchantCode())
        paramMap.put("reqTime", DateUtils.format(new Date(), DateUtils.YYYYMMDDHHMMSS))
        String toSign = MD5.toAscii(paramMap) + "&key=" + merchantAccount.getPrivateKey()
        paramMap.put("sign", MD5.md5(toSign).toUpperCase())

        log.info("WuTuoBangScript_Query_Balance_Params: {}", JSON.toJSONString(paramMap))
        GlRequestHeader requestHeader =
                getRequestHeard(null, null, null, GlActionEnum.PAYMENT_QUERY_BALANCE.getCode(),merchantAccount.getChannelId(),merchantAccount.getChannelName())
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/api/mch/query_balance",paramMap, requestHeader)
        log.info("WuTuoBangScript_Query_Balance_resStr: {}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || json.getString("retCode") != "SUCCESS") {
            return BigDecimal.ZERO
        }
        BigDecimal balance = json.getBigDecimal("availableBalance")
        return balance == null ? BigDecimal.ZERO : balance.divide(BigDecimal.valueOf(100))
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