package com.seektop.fund.payment.xiHongShi2Pay

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

import java.math.RoundingMode

/**
 * @desc 西虹市代付(二代系统)
 * @date 2021-10-12
 * @auth Otto
 */
class XiHongShi2Script_withdraw {

    private static final Logger log = LoggerFactory.getLogger(XiHongShi2Script_withdraw.class)

    private static final String SERVER_WITHDRAW_URL = "/app/DFPayFun"
    private static final String SERVER_QUERY_URL = "/app/DFPayCheckOrder"
    private static final String SERVER_BALANCE_URL = "/api/selectDFMerchantMoney"
    private OkHttpUtil okHttpUtil

    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness

    WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        this.glPaymentChannelBankBusiness = com.seektop.fund.payment.groovy.BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

        Map<String, String> params = new HashMap<>()
        String money = req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString();
        String skbankid = glPaymentChannelBankBusiness.getBankCode(req.getBankId(), merchantAccount.getChannelId()) ;
        String hdurli = merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId();

        params.put("dfusername", merchantAccount.getMerchantCode())
        params.put("money", money)
        params.put("orderno", req.getOrderId())
        params.put("skcardno", req.getCardNo())
        params.put("skcardname", req.getName())
        params.put("skbankid", skbankid)
        params.put("hdurli", hdurli)

        String toSign = merchantAccount.getMerchantCode() + money + req.getOrderId() + req.getCardNo();
        toSign = toSign + req.getName() + skbankid + hdurli + "{"+merchantAccount.getPrivateKey()+"}";
        params.put("sign", MD5.md5(toSign))

        log.info("XiHongShiScript_Transfer_params: {}", params)
        GlRequestHeader requestHeader = getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(), merchantAccount.getChannelId(), merchantAccount.getChannelName())

        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + SERVER_WITHDRAW_URL, params, requestHeader)
        log.info("XiHongShiScript_Transfer_resStr: {}", resStr)

        WithdrawResult result = new WithdrawResult()
        result.setOrderId(req.getOrderId())
        result.setReqData(JSON.toJSONString(params))
        result.setResData(resStr)

        JSONObject json = JSON.parseObject(resStr)
        //systate: 0 下单成功 ，其余失败
        if (json == null || "0" != json.getString("systate")) {
            result.setValid(false)
            result.setMessage(json == null ? "API异常:请联系出款商户确认订单." : json.getString("Message"))
            return result
        }

        result.setValid(true)
        result.setMessage("")
        println(result)
        return result
    }

    WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        Map<String, String> resMap = args[2] as Map<String, String>
        this.glPaymentChannelBankBusiness = com.seektop.fund.payment.groovy.BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        log.info("XiHongShiScript_WithdrawNotify_resMap:{}", JSON.toJSONString(resMap))

        String orderId = JSON.parseObject(resMap.get("reqBody")).getString("orderno");
        if (StringUtils.isNotEmpty(orderId)) {
            return withdrawQuery(okHttpUtil, merchant, orderId)

        } else {
            return null

        }
    }

    WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2] as String

        Map<String, String> params = new HashMap<String, String>()
        params.put("loginname", merchant.getMerchantCode())
        params.put("orderno", orderId)

        log.info("XiHongShiScript_TransferQuery_reqMap:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = getRequestHeard(null, null, orderId, GlActionEnum.WITHDRAW_QUERY.getCode(), merchant.getChannelId(), merchant.getChannelName())

        String resStr = okHttpUtil.post(merchant.getPayUrl() + SERVER_QUERY_URL, params, requestHeader)
        log.info("XiHongShiScript_TransferQuery_resStr:{}", resStr)

        if (StringUtils.isEmpty(resStr)) {
            return null
        }
        JSONObject json = JSON.parseObject(resStr)
        WithdrawNotify notify = new WithdrawNotify()

        // 网关返回码： 0=成功，其他失败
        if ("0" == json.getString("systate")) {
            JSONObject dataJSON = json.getJSONObject("data")

            String toSign = dataJSON.getString("orderno") + dataJSON.getString("orderstate") + dataJSON.getString("truedfmoney");
            toSign = toSign + dataJSON.getString("endtime") + "{" + merchant.getPrivateKey() + "}";

            if (MD5.md5(toSign) != dataJSON.getString("sign")) {
                log.info("XiangYunScript_withdrawQuery _sign:{}, 验签失败 , 我方签名: {} , 三方签名:{} ", orderId, MD5.md5(toSign), dataJSON.getString("sign"))
                return null
            }

            notify.setMerchantCode(merchant.getMerchantCode())
            notify.setMerchantId(merchant.getMerchantId())
            notify.setMerchantName(merchant.getChannelName())
            notify.setOrderId(orderId)
            notify.setThirdOrderId("")

            // 0处理中 1已处理 2失败已退回  4待审查
            String payStatus = dataJSON.getString("orderstate")
            if (payStatus == "1") {
                notify.setStatus(0)
                notify.setRsp("SUCCESS")

            } else if (payStatus == "2" ) {
                notify.setStatus(1)
                notify.setRsp("SUCCESS")

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

        Map<String, String> params = new HashMap<>()
        params.put("loginname", merchantAccount.getMerchantCode())

        String toSign = merchantAccount.getMerchantCode() + "{" + merchantAccount.getPrivateKey() + "}"
        println(toSign)
        params.put("sign", MD5.md5(toSign))

        log.info("XiHongShiScript_QueryBalance_reqMap: {}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                getRequestHeard("", "", "", GlActionEnum.PAYMENT_QUERY_BALANCE.getCode(), merchantAccount.getChannelId(), merchantAccount.getChannelName())

        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + SERVER_BALANCE_URL, params, requestHeader)
        log.info("XiHongShiScript_QueryBalance_resStr: {}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        //网关返回码：0=成功，其他失败
        if (json == null || json.getString("systate") != "0") {
            return BigDecimal.ZERO
        }

        BigDecimal balance = json.getJSONObject("data").getBigDecimal("money")
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