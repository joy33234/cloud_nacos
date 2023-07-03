package com.seektop.fund.payment.wayPay

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
import com.seektop.fund.payment.groovy.BaseScript
import com.seektop.fund.payment.groovy.ResourceEnum
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode

/**
 * @desc 威支付
 * @date 2021-10-23
 * @auth Otto
 */
class WayPayScript_withdraw {

    private static final Logger log = LoggerFactory.getLogger(WayPayScript_withdraw.class)

    private static final String SERVER_WITHDRAW_URL =  "/Apipay"  //所有接口网址一样
    private static final String SERVER_QUERY_URL =  "/Apipay"
    private static final String SERVER_BALANCE_URL =  "/Apipay"
    private OkHttpUtil okHttpUtil

    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness

    WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

        Map<String, String> params = new HashMap<>()
        params.put("userid", merchantAccount.getMerchantCode())
        params.put("action", "withdraw")
        params.put("notifyurl", merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId())

        Map<String, String> dataParams = new HashMap<>()
        dataParams.put("orderno", req.getOrderId())
        dataParams.put("date", DateUtils.format(new Date(), DateUtils.YYYYMMDDHHMMSS))
        dataParams.put("amount", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString())
        dataParams.put("bank", glPaymentChannelBankBusiness.getBankName(req.getBankId(), merchantAccount.getChannelId()))
        dataParams.put("account", req.getCardNo())
        dataParams.put("name", req.getName())
        dataParams.put("subbranch", "上海支行")

        params.put("content","["+ JSON.toJSONString(dataParams)+"]")

        String toSign = merchantAccount.getMerchantCode() + params.get("action") +params.get("content") + merchantAccount.getPrivateKey()
        params.put("sign", MD5.md5(toSign))

        log.info("WayPayScript_Transfer_params: {}  ", params)
        GlRequestHeader requestHeader = getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(), merchantAccount.getChannelId(), merchantAccount.getChannelName())

        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + SERVER_WITHDRAW_URL, params, requestHeader)
        log.info("WayPayScript_Transfer_resStr: {} ", resStr )

        WithdrawResult result = new WithdrawResult()
        result.setOrderId(req.getOrderId())
        result.setReqData(JSON.toJSONString(params))
        result.setResData(resStr)

        JSONObject json = JSON.parseObject(resStr)
        //1 成功
        if (json == null || 1 != json.getInteger("status")) {
            result.setValid(false)
            result.setMessage(json == null ? "API异常:请联系出款商户确认订单." : json.getString("msg"))
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

        log.info("WayPayScript_WithdrawNotify_resMap:{}", JSON.toJSONString(resMap))
        String orderId = resMap.get("orderno");

        String toSign = resMap.get("userid") + orderId + resMap.get("outorder") + resMap.get("status") +resMap.get("amount") + resMap.get("fee");
        toSign = toSign + resMap.get("account") + resMap.get("name") + resMap.get("bank") + merchant.getPrivateKey();

        String md5Sign = MD5.md5(toSign);
        if (StringUtils.isNotEmpty(orderId) && md5Sign == resMap.get("sign")) {
            return withdrawQuery(okHttpUtil, merchant, orderId)

        } else {
            log.info("WayPayScript_WithdrawNotify_sign: 回调资料错误或验签失败，单号：{}", orderId )
            return null

        }
    }

    WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2] as String

        Map<String, String> params = new HashMap<String, String>()
        params.put("userid", merchant.getMerchantCode())
        params.put("action", "withdrawquery")
        params.put("orderno", orderId)
        params.put("sign", MD5.md5(merchant.getMerchantCode() + "withdrawquery" +orderId+ merchant.getPrivateKey() ))

        log.info("WayPayScript_TransferQuery_reqMap:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = getRequestHeard(null, null, orderId, GlActionEnum.WITHDRAW_QUERY.getCode(), merchant.getChannelId(), merchant.getChannelName())

        String resStr = okHttpUtil.post(merchant.getPayUrl() + SERVER_QUERY_URL, params, requestHeader)
        log.info("WayPayScript_TransferQuery_resStr:{} ", resStr )

        if (StringUtils.isEmpty(resStr)) {
            return null
        }
        JSONObject json = JSON.parseObject(resStr)
        WithdrawNotify notify = new WithdrawNotify()

        // 成功【1】 失败【0】
        if ( 1 == json.getInteger("status")) {
            notify.setMerchantCode(merchant.getMerchantCode())
            notify.setMerchantId(merchant.getMerchantId())
            notify.setMerchantName(merchant.getChannelName())
            notify.setOrderId(orderId)
            notify.setThirdOrderId("")

            String orderstatus = json.getJSONObject("content").getString("orderstatus")

            //【0】申请提现中【1】已支付【2】冻结【3】已驳回
            if (orderstatus == "1" ) {
                notify.setStatus(0)
                notify.setRsp("success")

            } else if (orderstatus == "2" || orderstatus == "3" ) {
                notify.setStatus(1)
                notify.setRsp("success")

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

        String date = DateUtils.format(new Date(), DateUtils.YYYYMMDDHHMMSS) ;
        Map<String, String> params = new HashMap<>()
        params.put("userid", merchantAccount.getMerchantCode())
        params.put("date", date)
        params.put("action", "balance")

        String toSign = MD5.md5(merchantAccount.getMerchantCode() + date + "balance" + merchantAccount.getPrivateKey())
        params.put("sign", toSign)

        log.info("WayPayScript_QueryBalance_reqMap: {}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                getRequestHeard("", "", "", GlActionEnum.PAYMENT_QUERY_BALANCE.getCode(), merchantAccount.getChannelId(), merchantAccount.getChannelName())

        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + SERVER_BALANCE_URL, params, requestHeader)
        log.info("WayPayScript_QueryBalance_resStr: {}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        //成功【1】 失败【0】
        if (json == null || json.getString("status") != "1") {
            return BigDecimal.ZERO
        }

        BigDecimal balance = json.getBigDecimal("money")
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