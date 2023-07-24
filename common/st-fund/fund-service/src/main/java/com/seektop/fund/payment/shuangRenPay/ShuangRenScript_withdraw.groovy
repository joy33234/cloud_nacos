package com.seektop.fund.payment.shuangRenPay

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
import com.seektop.fund.payment.groovy.BaseScript
import com.seektop.fund.payment.groovy.ResourceEnum
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode

/**
 * @desc 双人代付
 * @date 2021-11-11
 * @auth Otto
 */
class ShuangRenScript_withdraw {

    private static final Logger log = LoggerFactory.getLogger(ShuangRenScript_withdraw.class)

    private static final String SERVER_WITHDRAW_URL = "/api/order/wd"
    private static final String SERVER_QUERY_URL = "/api/query/withdraw"
    private static final String SERVER_BALANCE_URL = "/api/query/balance"
    private OkHttpUtil okHttpUtil

    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness

    WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

        Map<String, String> params = new HashMap<>();

        params.put("biz_code", merchantAccount.getMerchantCode())
        params.put("biz_order_code", req.getOrderId())
        params.put("order_amount", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString())
        params.put("card_name", req.getName())
        params.put("card_no", req.getCardNo())
        params.put("bank_name", req.getBankName())
        params.put("bank_branch", "支行")
        params.put("notify_url", merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId())

        String toSign = MD5.toAscii(params) + "&key=" + merchantAccount.getPrivateKey();
        params.put("sign", MD5.md5(toSign).toUpperCase())

        log.info("ShuangRenScript_Transfer_params: {}", params)
        GlRequestHeader requestHeader = getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(), merchantAccount.getChannelId(), merchantAccount.getChannelName())
        String resStr = okHttpUtil.postJSON(merchantAccount.getPayUrl() + SERVER_WITHDRAW_URL, JSON.toJSONString(params), requestHeader)
        log.info("ShuangRenScript_Transfer_resStr: {}  , orderid :{}", resStr , req.getOrderId())

        WithdrawResult result = new WithdrawResult()
        result.setOrderId(req.getOrderId())
        result.setReqData(JSON.toJSONString(params))
        result.setResData(resStr)

        JSONObject json = JSON.parseObject(resStr)

        if (json == null ) {
            result.setValid(false)
            result.setMessage( "API异常:请联系出款商户确认订单." )
            return result
        }

        //status: 200 下单成功 ，其余失败
        if ( 200 != json.getInteger("status")) {
            result.setValid(false)
            result.setMessage( json.getString("msg") == null ? "三方下单失败" : json.getString("msg") )
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
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        log.info("ShuangRenScript_WithdrawNotify_resMap:{}", JSON.toJSONString(resMap))

        JSONObject json = JSONObject.parseObject(resMap.get("reqBody"));

        Map<String, Object> map = (Map<String, Object>) json
        String sign = map.get("sign"); //对方回传的的 sign
        map.remove("sign");

        String md5Sign = MD5.toAscii(map) + "&key=" + merchant.getPrivateKey();
        md5Sign = MD5.md5(md5Sign).toUpperCase();
        String orderId = json.getString("biz_order_code")

        if (StringUtils.isNotEmpty(orderId) && sign == md5Sign) {
            return withdrawQuery(okHttpUtil, merchant, orderId)

        } else {
            log.info("ShuangRenScript_WithdrawNotify_Sign: 回调资料错误或验签失败，我方签名为：{}" , md5Sign )
            return null

        }
    }

    WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2] as String

        Map<String, String> params = new HashMap<String, String>()
        params.put("biz_code", merchant.getMerchantCode())
        params.put("biz_order_code", orderId)

        String toSign = MD5.toAscii(params) + "&key=" + merchant.getPrivateKey();
        params.put("sign", MD5.md5(toSign).toUpperCase())

        log.info("ShuangRenScript_TransferQuery_reqMap:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = getRequestHeard(null, null, orderId, GlActionEnum.WITHDRAW_QUERY.getCode(), merchant.getChannelId(), merchant.getChannelName())

        String resStr = okHttpUtil.postJSON(merchant.getPayUrl() + SERVER_QUERY_URL, JSON.toJSONString(params), requestHeader)
        log.info("ShuangRenScript_TransferQuery_resStr:{}", resStr)

        if (StringUtils.isEmpty(resStr)) {
            return null
        }
        JSONObject json = JSON.parseObject(resStr)
        WithdrawNotify notify = new WithdrawNotify()

        // 网关返回码： 200=成功，其他失败
        if ("200" == json.getString("status")) {

            notify.setMerchantCode(merchant.getMerchantCode())
            notify.setMerchantId(merchant.getMerchantId())
            notify.setMerchantName(merchant.getChannelName())
            notify.setOrderId(orderId)
            notify.setThirdOrderId("")

            //  1=待审核  2=出款成功  3=出款拒绝
            String payStatus = json.getString("order_status")
            if (payStatus == "2") {
                notify.setStatus(0)
                notify.setRsp("OK")

            } else if (payStatus == "3") {
                notify.setStatus(1)
                notify.setRsp("OK")

            } else {
                notify.setStatus(2)
            }
        }
        return notify
    }

    BigDecimal balanceQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[2], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

        Map<String, String> params = new HashMap<>()
        params.put("biz_code", merchantAccount.getMerchantCode())

        String toSign =  MD5.toAscii(params)+ "&key=" + merchantAccount.getPrivateKey();
        params.put("sign", MD5.md5(toSign).toUpperCase())

        log.info("ShuangRenScript_QueryBalance_reqMap: {}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                getRequestHeard("", "", "", GlActionEnum.PAYMENT_QUERY_BALANCE.getCode(), merchantAccount.getChannelId(), merchantAccount.getChannelName())

        String resStr = okHttpUtil.postJSON(merchantAccount.getPayUrl() + SERVER_BALANCE_URL, JSON.toJSONString(params), requestHeader)
        log.info("ShuangRenScript_QueryBalance_resStr: {}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        //网关返回码：成功:200，失败：400
        if (json == null || json.getString("status") != "200") {
            return BigDecimal.ZERO
        }

        BigDecimal balance = json.getBigDecimal("balance")
        return balance == null ? BigDecimal.ZERO : balance
    }

    private static GlRequestHeader getRequestHeard(String userId, String userName, String orderId, String code, Integer channelId, String channelName) {
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