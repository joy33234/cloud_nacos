package com.seektop.fund.payment.jinyipay

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

import static com.seektop.fund.payment.groovy.BaseScript.getResource

/**
 * @desc 金鐿支付
 * @auth Otto
 * @date 2022-04-07
 */

public class JinYiScript_withdraw {
    private static final Logger log = LoggerFactory.getLogger(JinYiScript_withdraw.class)

    private OkHttpUtil okHttpUtil
    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness

    WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount account = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        this.glPaymentChannelBankBusiness = getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        WithdrawResult result = new WithdrawResult()

        Map<String, Object> params = new LinkedHashMap<>()
        params.put("business_type", "W001")
        params.put("user_id", account.getMerchantCode())
        params.put("order_no", req.getOrderId())
        params.put("price", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString())
        params.put("bankaccount", req.getCardNo())
        String toSign = MD5.toSign(params) + "&key=" + account.getPrivateKey()
        params.put("sign", MD5.md5(toSign))

        params.put("notify_url", account.getNotifyUrl() + account.getMerchantId())
        params.put("notify_type", "1")
        params.put("currency", "CNY")
        params.put("bankaccountname", req.getName())
        params.put("bankname", req.getBankName())

        log.info("JinYiScript_Transfer_params: {} , url:{} ", JSON.toJSONString(params), account.getPayUrl())
        GlRequestHeader requestHeader =
                this.getRequestHeard(req.getUserId() + "", req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(), account.getChannelId(), account.getChannelName())
        String resStr = okHttpUtil.post(account.getPayUrl(), params, requestHeader)
        log.info("JinYiScript_Transfer_resStr: {} ", resStr)

        result.setOrderId(req.getOrderId())
        result.setReqData(JSON.toJSONString(params))
        result.setResData(resStr)
        JSONObject json = JSON.parseObject(resStr)
        if (json == null) {
            result.setValid(false)
            result.setMessage("API异常:请联系出款商户确认订单.")
            return result
        }
        if (json.getString("status") != "1") {
            result.setValid(false)
            result.setMessage(json.getString("msg"))
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

        log.info("JinYiScript_withdrawNotify_resMap:{}", JSON.toJSONString(resMap))

        String orderId = resMap.get("order_no")

        Map<String, String> signMap = new LinkedHashMap();
        signMap.put("currency", resMap.get("currency"));
        signMap.put("status", resMap.get("status"));
        signMap.put("user_id", resMap.get("user_id"));
        signMap.put("order_no", resMap.get("order_no"));
        signMap.put("price", resMap.get("price"));
        signMap.put("fee", resMap.get("fee"));
        signMap.put("bankaccount", resMap.get("bankaccount"));
        signMap.put("bankaccountname", resMap.get("bankaccountname"));
        signMap.put("bankname", resMap.get("bankname"));

        String toSign = MD5.toSign(signMap) + "&key=" + merchant.getPrivateKey()
        toSign = MD5.md5(toSign)

        if (org.apache.commons.lang3.StringUtils.isNotEmpty(orderId) && toSign == resMap.get("sign")) {
            return withdrawQuery(okHttpUtil, merchant, orderId)
        }
        log.info("JinYiScript_withdraw_Sign: 回调资料错误或验签失败，orderId：{}", orderId)
        return null;
    }


    WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2]

        Map<String, Object> DataContentParms = new LinkedHashMap<>()
        DataContentParms.put("business_type", "W002")
        DataContentParms.put("user_id", merchant.getMerchantCode())
        DataContentParms.put("order_no", orderId)

        String toSign = MD5.toSign(DataContentParms) + "&key=" + merchant.getPrivateKey()
        DataContentParms.put("sign", MD5.md5(toSign))

        log.info("JinYiScript_TransferQuery_order:{}", JSON.toJSONString(DataContentParms))
        GlRequestHeader requestHeader =
                this.getRequestHeard("", "", orderId, GlActionEnum.WITHDRAW_QUERY.getCode(), merchant.getChannelId(), merchant.getChannelName())
        String resStr = okHttpUtil.post(merchant.getPayUrl(), DataContentParms, requestHeader)
        log.info("JinYiScript_TransferQuery_resStr:{}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || json.getString("status") != "1") {
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

        //0 已申请
        //1已支付
        //2處理中
        //3支付失败
        if (dataJSON.getString("order_status") == "1") {
            notify.setStatus(0)
            notify.setRsp("success")

        } else if (dataJSON.getString("order_status") == "3") {
            notify.setStatus(1)
            notify.setRsp("success")

        } else {
            notify.setStatus(2)
            notify.setRsp("success")
        }
        return notify
    }


    BigDecimal balanceQuery(Object[] args) {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount

        Map<String, String> DataContentParams = new LinkedHashMap<>()
        DataContentParams.put("business_type", "B001")
        DataContentParams.put("user_id", merchantAccount.getMerchantCode())
        DataContentParams.put("timestamp", System.currentTimeSeconds().toString())

        String toSign = MD5.toSign(DataContentParams) + "&key=" + merchantAccount.getPrivateKey()
        DataContentParams.put("sign", MD5.md5(toSign))

        GlRequestHeader requestHeader =
                getRequestHeard("", "", "", GlActionEnum.PAYMENT_QUERY_BALANCE.getCode(), merchantAccount.getChannelId(), merchantAccount.getChannelName())

        log.info("JinYiScript_QueryBalance_reqMap: {}", JSON.toJSONString(DataContentParams))
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl(), DataContentParams, requestHeader)
        log.info("JinYiScript_QueryBalance_resStr: {}", resStr)

        JSONObject responJSON = JSON.parseObject(resStr)
        if (responJSON == null || responJSON.getString("status") != "1") {
            return BigDecimal.ZERO ;
        }
        return responJSON.getBigDecimal("balance") == null ? BigDecimal.ZERO : responJSON.getBigDecimal("balance").setScale(2, RoundingMode.DOWN)


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