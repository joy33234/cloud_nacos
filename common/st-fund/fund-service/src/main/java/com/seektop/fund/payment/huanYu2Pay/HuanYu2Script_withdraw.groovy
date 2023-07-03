package com.seektop.fund.payment.huanYu2Pay

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
 * @desc 寰宇支付
 * @date 2021-10-07
 * @auth Otto
 */
public class HuanYu2Script_withdraw {

    private static final Logger log = LoggerFactory.getLogger(HuanYu2Script_withdraw.class)
    private OkHttpUtil okHttpUtil
    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness

    private static final String SERVER_WITHDRAW_URL = "/payout/createOrder"
    private static final String SERVER_QUERY_URL = "/inquiry/payoutOrder"
    private static final String BALANCE_QUERY_URL = "/inquiry/getMerBalance"

    public WithdrawResult withdraw(Object[] args) throws GlobalException {

        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount account = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

        Map<String, String> params = new LinkedHashMap<>()
        String orderAmount = req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString()
        String bankCode = glPaymentChannelBankBusiness.getBankCode(req.getBankId(), account.getChannelId())
        params.put("merNo", account.getMerchantCode())
        params.put("tradeNo", req.getOrderId())
        params.put("cType", "Payout2")
        params.put("bankCode", bankCode)
        params.put("bankCardNo", req.getCardNo())
        params.put("orderAmount", orderAmount)
        params.put("accountName", req.getName())
        params.put("notifyUrl", account.getNotifyUrl() + account.getMerchantId())
        params.put("sign", MD5.md5(account.getMerchantCode()  +  req.getOrderId() + bankCode + orderAmount + account.getPrivateKey() ));

        log.info("HuanYu2Script_doTransfer_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                this.getRequestHeard(req.getUserId() + "", req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(), account.getChannelId(), account.getChannelName())

        String resStr = okHttpUtil.post(account.getPayUrl() + SERVER_WITHDRAW_URL, params, requestHeader)
        log.info("HuanYu2Script_doTransfer_resp:{}", resStr)
        JSONObject json = JSON.parseObject(resStr)

        WithdrawResult result = new WithdrawResult()
        result.setOrderId(req.getOrderId())
        result.setReqData(JSON.toJSONString(params))
        result.setResData(resStr)

        if (StringUtils.isEmpty(resStr)) {
            result.setValid(false)
            result.setMessage("API异常:请联系出款商户确认订单.")
            return result
        }
        //1成功 其余失败
        if (1 != (json.getInteger("Success"))) {
            result.setValid(false)
            result.setMessage(URLDecoder.decode(json.getString("Message"), "utf-8"))
            return result
        }
        req.setMerchantId(account.getMerchantId())
        result.setValid(true)
        result.setMessage("下单成功，等待三方出款")
        return result
    }

    public WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        Map<String, String> resMap = args[2] as Map<String, String>

        log.info("HuanYu2Script_doTransferNotify_resp:{}", JSON.toJSONString(resMap))
        if (StringUtils.isNotEmpty(resMap.get("tradeNo"))) {
            return this.withdrawQuery(okHttpUtil, merchant, resMap.get("tradeNo"), args[3])
        }
        return null
    }

    public WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2]

        Map<String, String> reqParams = new LinkedHashMap<>()
        reqParams.put("tradeNo", orderId)
        reqParams.put("merNo", merchant.getMerchantCode())
        reqParams.put("sign", MD5.md5(merchant.getMerchantCode()+orderId+merchant.getPrivateKey()))

        log.info("HuanYu2Script_doTransferQuery_params:{}", JSON.toJSONString(reqParams))
        GlRequestHeader requestHeader =
                this.getRequestHeard("", "", orderId, GlActionEnum.WITHDRAW_QUERY.getCode(), merchant.getChannelId(), merchant.getChannelName())
        String resStr = okHttpUtil.post(merchant.getPayUrl() + SERVER_QUERY_URL, reqParams, requestHeader)
        log.info("HuanYu2Script_doTransferQuery_resp:{}", resStr)
        if (StringUtils.isEmpty(resStr)) {
            return null
        }
        JSONObject json = JSON.parseObject(resStr)

        if ( 1 != (json.getInteger("Success"))) {
            return null
        }

        if ( null == json.getInteger("orderAmount")) {
            return null
        }

        WithdrawNotify notify = new WithdrawNotify()
        notify.setMerchantCode(merchant.getMerchantCode())
        notify.setMerchantId(merchant.getMerchantId())
        notify.setMerchantName(merchant.getChannelName())
        notify.setOrderId(orderId)
        notify.setThirdOrderId("")

        //-2:建单失败 -1:上分失败 0:处理中 1:上分成功 8:API审核 9:后台审核
        if (json.getInteger("status") == 1 ) {
            notify.setStatus(0)
            notify.setRsp("SUCCESS");

        } else if (json.getInteger("status") == -2 || json.getInteger("status") == -1 ) {
            notify.setStatus(1)
            notify.setRsp("SUCCESS");

        } else {
            notify.setStatus(2)

        }
        return notify
    }

    public BigDecimal balanceQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount

        Map<String, String> reqParams = new LinkedHashMap<>()
        String datetime = DateUtils.format(new Date(), DateUtils.YYYYMMDDHHMMSS);
        reqParams.put("merNo", merchantAccount.getMerchantCode())
        reqParams.put("datetime", datetime)
        reqParams.put("sign", MD5.md5( merchantAccount.getMerchantCode() + datetime + merchantAccount.getPrivateKey()));

        log.info("HuanYu2Script_queryBalance_params:{}", JSON.toJSONString(reqParams))
        GlRequestHeader requestHeader =
                getRequestHeard("", "", "", GlActionEnum.PAYMENT_QUERY_BALANCE.getCode(), merchantAccount.getChannelId(), merchantAccount.getChannelName())
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + BALANCE_QUERY_URL, reqParams, requestHeader)
        log.info("HuanYu2Script_queryBalance_resp:{}", resStr)

        JSONObject json = JSONObject.parseObject(resStr)
        if (1 == (json.getInteger("Success"))  ) {
            return json.getBigDecimal("Balance") == null ? BigDecimal.ZERO :json.getBigDecimal("Balance")
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