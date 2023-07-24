package com.seektop.fund.payment.koingPay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
import com.seektop.enumerate.GlActionEnum
import com.seektop.exception.GlobalException
import com.seektop.fund.business.GlPaymentChannelBankBusiness
import com.seektop.fund.model.GlWithdraw
import com.seektop.fund.model.GlWithdrawMerchantAccount
import com.seektop.fund.payment.WithdrawNotify
import com.seektop.fund.payment.WithdrawResult
import com.seektop.fund.payment.groovy.ResourceEnum
import org.apache.commons.codec.digest.HmacUtils
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.util.ObjectUtils

import java.math.RoundingMode

/**
 * Koing支付
 * @auth  joy
 * @date 2021-07-29
 */

class KoingScript_withdraw {

    private static final Logger log = LoggerFactory.getLogger(KoingScript_withdraw.class)

    private OkHttpUtil okHttpUtil

    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness


    WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        this.glPaymentChannelBankBusiness = com.seektop.fund.payment.groovy.BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

        Map<String, String> paramMap = new HashMap<>()
        paramMap.put("merchantId", merchantAccount.getMerchantCode());
        paramMap.put("merchantOrderId", req.getOrderId());
        paramMap.put("merchantUserId", req.getUserId().toString());
        paramMap.put("method", "BANK_TRANSFER");
        paramMap.put("amount", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString());
        paramMap.put("remark", "withdraw");
        paramMap.put("bankCode", glPaymentChannelBankBusiness.getBankCode(req.getBankId(), merchantAccount.getChannelId()));
        paramMap.put("bankAccountName", req.getName());
        paramMap.put("bankAccountNumber", req.getCardNo());
        paramMap.put("callbackUrl",  merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId());
        paramMap.put("nonce", UUID.randomUUID().toString());
        paramMap.put("signature", calculateSignature(merchantAccount.getPrivateKey(), paramMap));
        
        log.info("KoingScript_Transfer_Params: {}", JSON.toJSONString(paramMap))
        GlRequestHeader requestHeader =
                getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(),merchantAccount.getChannelId(),merchantAccount.getChannelName())
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/payout", paramMap,  requestHeader)
        log.info("KoingScript_Transfer_resStr: {}", resStr)

        WithdrawResult result = new WithdrawResult()
        result.setOrderId(req.getOrderId())
        result.setReqData(JSON.toJSONString(paramMap))
        result.setResData(resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null) {
            result.setValid(false)
            result.setMessage("API异常:请联系出款商户确认订单.")
            return result
        }
        if (StringUtils.isEmpty(json.getString("status"))) {
            result.setValid(false)
            result.setMessage("API异常:请联系出款商户确认订单.")
            return result
        }
        result.setValid(true)
        result.setMessage(json.getString("Message"))
        result.setThirdOrderId(json.getString("id"))
        return result
    }

    WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        Map<String, String> resMap = args[2] as Map<String, String>
        log.info("KoingScript_Transfer_Notify_resMap:{}", JSON.toJSONString(resMap))
        JSONObject json = JSON.parseObject(resMap.get("reqBody"));
        String orderid = json.getString("merchantOrderId")
        if (StringUtils.isNotEmpty(orderid)) {
            return withdrawQuery(okHttpUtil, merchant, orderid, args[3])
        }
        return null
    }

    WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2]
        Map<String, String> paramMap = new HashMap<>()
        paramMap.put("merchantId", merchant.getMerchantCode())
        paramMap.put("merchantOrderId", orderId)
        paramMap.put("nonce", UUID.randomUUID().toString());

        paramMap.put("signature", calculateSignature(merchant.getPrivateKey(), paramMap));

        log.info("KoingScript_Transfer_Query_Params: {}", JSON.toJSONString(paramMap))
        GlRequestHeader requestHeader =
                getRequestHeard(null, null, orderId, GlActionEnum.WITHDRAW_QUERY.getCode(),merchant.getChannelId(),merchant.getChannelName())
        String resStr = okHttpUtil.get(merchant.getPayUrl() + "/payout", paramMap, 30L, requestHeader)
        log.info("KoingScript_Transfer_Query_resStr: {}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null ||  StringUtils.isEmpty(json.getString("status"))) {
            return null
        }
        //1：代付成功；  2：处理中；  3：失败
        String status = json.getString("status")
        WithdrawNotify notify = new WithdrawNotify()
        notify.setMerchantCode(merchant.getMerchantCode())
        notify.setMerchantId(merchant.getMerchantId())
        notify.setMerchantName(merchant.getChannelName())
        notify.setOrderId(orderId)
        notify.setRemark("")
        if (status == "SUCCESS") {
            notify.setStatus(0)
            notify.setRsp("200")
        } else if (status == "FAILED") {
            notify.setStatus(1)
            notify.setRsp("200")
        } else {
            notify.setStatus(2)
        }
        notify.setThirdOrderId(json.getString("id"))
        return notify
    }


    BigDecimal balanceQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount

        Map<String, String> paramMap = new HashMap<>()
        paramMap.put("merchantId", merchantAccount.getMerchantCode())
        paramMap.put("nonce", UUID.randomUUID().toString());
        paramMap.put("signature", calculateSignature(merchantAccount.getPrivateKey(), paramMap));

        log.info("KoingScript_Query_Balance_Params: {}", JSON.toJSONString(paramMap))
        GlRequestHeader requestHeader =
                getRequestHeard(null, null, null, GlActionEnum.PAYMENT_QUERY_BALANCE.getCode(),merchantAccount.getChannelId(),merchantAccount.getChannelName())
        String resStr = okHttpUtil.get(merchantAccount.getPayUrl() + "/balance", paramMap,  requestHeader)
        log.info("KoingScript_Query_Balance_resStr: {}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null ) {
            return BigDecimal.ZERO
        }
        BigDecimal balance = json.getBigDecimal("availableBalance")
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

    /**
     * 计算消息签名
     *
     * @param merchantSecretKey 商户密钥，开户后由在线支付系统提供
     * @param messageData       发送或者接收到的消息中的所有参数
     * @return 计算出的签名结果
     */
    public String calculateSignature(String merchantSecretKey,
                                     Map<String, String> messageData) {
        List<Map.Entry<String, Object>> infoIds = new ArrayList<>(messageData.entrySet())
        // 对所有传入参数按照字段名的 ASCII 码从小到大排序（字典序）
        Collections.sort(infoIds, Comparator.comparing({ o -> (o as Map.Entry).getKey() }))
        StringBuffer sign = new StringBuffer()
        for (Map.Entry<String, Object> item : infoIds) {
            String k = item.getKey()
            if (!org.springframework.util.StringUtils.isEmpty(item.getKey())) {
                Object v = item.getValue()
                if (null != v && !ObjectUtils.isEmpty(v)) {
                    sign.append( v + "|")
                }
            }
        }
        // 调用 HMAC_SHA256 算法计算签名
        return new HmacUtils("HmacSHA256", merchantSecretKey).hmacHex(sign.deleteCharAt(sign.length() - 1).toString());
    }
}