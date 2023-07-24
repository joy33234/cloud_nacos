package com.seektop.fund.payment.xGTPay

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
 * @desc UGT代付
 * @date 2021-11-21
 * @auth Otto
 */
class UGTPayScript_withdraw {

    private static final Logger log = LoggerFactory.getLogger(UGTPayScript_withdraw.class)

    private static final String SERVER_WITHDRAW_URL = "/remit/api"
    private static final String SERVER_QUERY_URL = "/remit/query"
    private static final String SERVER_BALANCE_URL = "/merchant/overage/query"
    private OkHttpUtil okHttpUtil

    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness

    WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

        Map<String, String> params = new HashMap<>();

        params.put("merchantNo", merchantAccount.getMerchantCode())
        params.put("money", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString())
        params.put("type", "BANKCARD")
        params.put("outOrder", req.getOrderId())
        params.put("callbackUrl", merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId())
        params.put("number", req.getCardNo())
        params.put("receiver", req.getName())
        params.put("bank", req.getBankName())
        params.put("cardType", "0")
        params.put("signType", "MD5")

        String toSign = MD5.toAscii(params) +  merchantAccount.getPrivateKey();
        params.put("sign", MD5.md5(toSign).toUpperCase())

        log.info("UGTPayScript_Transfer_params: {} , url: {}", params, merchantAccount.getPayUrl())
        GlRequestHeader requestHeader = getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(), merchantAccount.getChannelId(), merchantAccount.getChannelName())

        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + SERVER_WITHDRAW_URL, params, requestHeader)
        log.info("UGTPayScript_Transfer_resStr: {} , orderId :{}", resStr , req.getOrderId())

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

        //1000表示成功，其余表示请求失败
        if ( 1000 != json.getInteger("code")) {
            result.setValid(false)
            result.setMessage( json.getString("message") == null ? "三方下单失败" : json.getString("message") )
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
        log.info("UGTPayScript_WithdrawNotify_resMap:{}", JSON.toJSONString(resMap))

        JSONObject jsonObj = JSON.parseObject(resMap.get("reqBody"));
        Map<String, String> signMap  =(Map<String, String>) jsonObj;
        String thirdSign = signMap.remove("sign")
        String status  = signMap.get("status")

        signMap.remove("message")
        String md5Sign = MD5.toAscii(signMap) + merchant.getPrivateKey()

        md5Sign = MD5.md5(md5Sign).toUpperCase();
        String orderId = signMap.get("outTradeNo")

        if (StringUtils.isNotEmpty(orderId) && thirdSign == md5Sign) {
            return withdrawQuery(okHttpUtil, merchant, orderId , status)

        }
            log.info("UGTPayScript_WithdrawNotify_Sign: 回调资料错误或验签失败，orderId：{}" , orderId )
            return null


    }

    WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2] as String
        String notifyStatus = args[3] as String

        Map<String, String> params = new HashMap<String, String>()
        params.put("merchantNo", merchant.getMerchantCode())
        params.put("outOrder", orderId)

        String toSign = MD5.toAscii(params) + merchant.getPrivateKey();
        params.put("sign", MD5.md5(toSign).toUpperCase())

        log.info("UGTPayScript_TransferQuery_reqMap:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = getRequestHeard(null, null, orderId, GlActionEnum.WITHDRAW_QUERY.getCode(), merchant.getChannelId(), merchant.getChannelName())

        String resStr = okHttpUtil.post(merchant.getPayUrl() + SERVER_QUERY_URL, params, requestHeader)
        log.info("UGTPayScript_TransferQuery_resStr:{}", resStr)

        if (StringUtils.isEmpty(resStr)) {
            return null
        }
        JSONObject json = JSON.parseObject(resStr)
        WithdrawNotify notify = new WithdrawNotify()

        // 状态 1000表示查询成功
        if ( 1000 == json.getInteger("code")) {
            notify.setMerchantCode(merchant.getMerchantCode())
            notify.setMerchantId(merchant.getMerchantId())
            notify.setMerchantName(merchant.getChannelName())
            notify.setOrderId(orderId)
            notify.setThirdOrderId("")

            JSONObject dataJson = json.getJSONObject("data");

            //SUBMIT：已提交
            //ACCEPT：受理中
            //REJECT：驳回
            //SUCCESS：结算成功
            //ERROR：异常代付订单
            //CALLBACKFAIL：通知商户失败
            String payStatus = dataJson.getString("status")
            if (payStatus == "SUCCESS") {
                notify.setStatus(0)
                notify.setRsp("success")

            } else if (payStatus == "ERROR"  || payStatus == "REJECT" ) {
                notify.setStatus(1)
                notify.setRsp("success")

            } else if (payStatus == "CALLBACKFAIL"){
                if( notifyStatus == "SUCCESS"){
                    notify.setStatus(0)
                    notify.setRsp("success")

                } else if (notifyStatus == "ERROR" || notifyStatus == "REJECT" ){
                    notify.setStatus(1)
                    notify.setRsp("success")

                }
                    notify.setStatus(2)


            }else{
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
        params.put("merchantNo", merchantAccount.getMerchantCode())

        String toSign =  MD5.toAscii(params)+  merchantAccount.getPrivateKey();
        params.put("sign", MD5.md5(toSign).toUpperCase())

        log.info("UGTPayScript_QueryBalance_reqMap: {}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                getRequestHeard("", "", "", GlActionEnum.PAYMENT_QUERY_BALANCE.getCode(), merchantAccount.getChannelId(), merchantAccount.getChannelName())

        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + SERVER_BALANCE_URL, params, requestHeader)
        log.info("UGTPayScript_QueryBalance_resStr: {}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        //网关返回码：1000
        if (json == null || json.getInteger("code") != 1000) {
            return BigDecimal.ZERO
        }

        JSONObject dataJson = json.getJSONObject("data")
        BigDecimal balance = dataJson.getBigDecimal("money")
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