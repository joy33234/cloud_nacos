package com.seektop.fund.payment.jinmaipay

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
import org.springframework.util.ObjectUtils

import java.math.RoundingMode

/**
 * 金迈支付
 * @author joy
 * @date 2021-09-11
 */
public class JinMaiScript_withdraw {


    private static final Logger log = LoggerFactory.getLogger(JinMaiScript_withdraw.class)

    private OkHttpUtil okHttpUtil

    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness


    WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        this.glPaymentChannelBankBusiness = com.seektop.fund.payment.groovy.BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

        Map<String, String> paramMap = new HashMap<>()
        paramMap.put("mchId", merchantAccount.getMerchantCode())
        paramMap.put("mchOrderNo", req.getOrderId())
        paramMap.put("productId", "501")
        paramMap.put("amount", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString())
        paramMap.put("bankName", req.getBankName())
        paramMap.put("subbRanch", "上海支行")
        paramMap.put("accountName", req.getName())
        paramMap.put("cardNumber", req.getCardNo())
        paramMap.put("notifyUrl", merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId())
        String toSign = MD5.toAscii(paramMap) + "&key=" + merchantAccount.getPrivateKey()
        log.info("toSign:{}",toSign)
        paramMap.put("sign", MD5.md5(toSign).toUpperCase())

        log.info("JinMaiScript_Transfer_Params: {}", JSON.toJSONString(paramMap))
        GlRequestHeader requestHeader =
                getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(),merchantAccount.getChannelId(),merchantAccount.getChannelName())
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/Payment_Dfpay_add", paramMap,  requestHeader)
        log.info("JinMaiScript_Transfer_resStr: {}", resStr)

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
        if (json == null || "success" != json.getString("status") || ObjectUtils.isEmpty(json.getString("payOrderId"))) {
            result.setValid(false)
            result.setMessage(json == null ? "API异常:请联系出款商户确认订单." : json.getString("msg"))
            return result
        }
        result.setValid(true)
        result.setMessage(json.getString("msg"))
        result.setThirdOrderId(json.getString("payOrderId"))
        return result
    }

    WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        Map<String, String> resMap = args[2] as Map<String, String>
        log.info("JinMaiScript_Transfer_Notify_resMap:{}", JSON.toJSONString(resMap))
        String orderid = resMap.get("mchOrderNo")// 商户订单号
        String thirdOrderId = resMap.get("payOrderId")// 商户订单号
        if (StringUtils.isNotEmpty(orderid) && StringUtils.isNotEmpty(thirdOrderId)) {
            return withdrawQuery(okHttpUtil, merchant, orderid, thirdOrderId, args[3])
        }
        return null
    }

    WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2]
        String thirdOrderId = args[3]
        String keyValue = merchant.getPrivateKey() // 商家密钥
        Map<String, String> paramMap = new HashMap<>()
        paramMap.put("mchId", merchant.getMerchantCode())
        paramMap.put("payOrderId", thirdOrderId)

        String signInfo = MD5.toAscii(paramMap) + "&key=" + keyValue
        paramMap.put("sign", MD5.md5(signInfo).toUpperCase())

        log.info("JinMaiScript_Transfer_Query_Params: {}", JSON.toJSONString(paramMap))
        GlRequestHeader requestHeader =
                getRequestHeard(null, null, orderId, GlActionEnum.WITHDRAW_QUERY.getCode(),merchant.getChannelId(),merchant.getChannelName())
        String resStr = okHttpUtil.get(merchant.getPayUrl() + "/Payment_Dfpay_query", paramMap,  requestHeader)
        log.info("JinMaiScript_Transfer_Query_resStr: {}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || json.getString("status") != "success") {
            return null
        }
        //状态:1为成功 2为失败 3为处理中
        Integer status = json.getInteger("refCode")
        WithdrawNotify notify = new WithdrawNotify()
        notify.setMerchantCode(merchant.getMerchantCode())
        notify.setMerchantId(merchant.getMerchantId())
        notify.setMerchantName(merchant.getChannelName())
        notify.setOrderId(orderId)
        notify.setRemark("")
        if (status == 1) {
            notify.setStatus(0)
        } else if (status == 2) {
            notify.setStatus(1)
        } else {
            notify.setStatus(2)
        }
        notify.setThirdOrderId(json.getString("payOrderId"))
        return notify
    }


    BigDecimal balanceQuery(Object[] args) throws GlobalException {

        return new BigDecimal(9999999)
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