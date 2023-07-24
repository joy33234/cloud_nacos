package com.seektop.fund.payment.winnerpay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
import com.seektop.enumerate.GlActionEnum
import com.seektop.exception.GlobalException
import com.seektop.fund.model.GlWithdraw
import com.seektop.fund.model.GlWithdrawMerchantAccount
import com.seektop.fund.payment.WithdrawNotify
import com.seektop.fund.payment.WithdrawResult
import com.seektop.fund.payment.cfpay.MerchantJSON
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.math.RoundingMode
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException

/**
 * Winnerpay 接口
 *
 * @author joy
 */
public class WinnerScript_Withdraw {

    private static final Logger log = LoggerFactory.getLogger(WinnerScript_Withdraw.class)

    private OkHttpUtil okHttpUtil

    public WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        Map<String, Object> params = new LinkedHashMap<String, Object>()
        params.put("application_amount", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString())
        params.put("bank", req.getBankName())
        params.put("bank_branch", "上海市")
        params.put("card_sn", req.getCardNo())
        params.put("out_trade_no", req.getOrderId())
        params.put("owner", req.getName())

        params.put("sign", getSign(MerchantJSON.encode(params), merchantAccount.getPrivateKey()))

        Map<String, String> headParams = new HashMap<String, String>()
        headParams.put("Authorization", "Bearer " + merchantAccount.getPrivateKey())
        headParams.put("Accept", "application/json")
        headParams.put("content-type", "application/json")


        log.info("WinnerScript_doTransfer_resMap = {}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = this.getRequestHeard(req.getUserId() + "", req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(), merchantAccount.getChannelId().toString(), merchantAccount.getChannelName())
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/api/withdraw", params, requestHeader, headParams)
        log.info("WinnerScript_doTransfer_resStr = {}", resStr)

        WithdrawResult result = new WithdrawResult()
        result.setOrderId(req.getOrderId())
        result.setReqData(JSON.toJSONString(params))
        result.setResData(resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || "200" != (json.getString("code"))) {
            result.setValid(false)
            result.setMessage(json == null ? "API异常:请联系出款商户确认订单." : json.getString("message"))
            return result
        }
        result.setValid(true)
        result.setMessage(json.getString("message"))
        return result
    }

    public WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        Map<String, String> resMap = args[2] as Map<String, String>
        log.info("WinnerScript_transfer_notify_resMap = {}", JSON.toJSONString(resMap))
        JSONObject json = JSON.parseObject(resMap.get("reqBody"))
        String orderId = json.getString("out_trade_no")
        if (StringUtils.isNotEmpty(orderId)) {
            return withdrawQuery(okHttpUtil, merchant, orderId)
        } else {
            return null
        }
    }

    public WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2]
        Map<String, String> headParams = new HashMap<String, String>()
        headParams.put("Authorization", "Bearer " + merchant.getPrivateKey())
        headParams.put("Accept", "application/json")
        headParams.put("content-type", "application/json")


        log.info("WinnerScript_TransferQuery_orderId = {}", orderId)
        GlRequestHeader requestHeader = this.getRequestHeard("", "", orderId, GlActionEnum.WITHDRAW_QUERY.getCode(), merchant.getChannelId().toString(), merchant.getChannelName())
        String resStr = okHttpUtil.get(merchant.getPayUrl() + "/api/withdraw/" + orderId, null, requestHeader, headParams)
        log.info("WinnerScript_TransferQuery_resStr = {}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || json.getString("code") != ("200")) {
            return null
        }
        JSONObject dataJSON = json.getJSONObject("result")
        WithdrawNotify notify = new WithdrawNotify()
        if (dataJSON != null) {
            notify.setAmount(dataJSON.getBigDecimal("application_amount"))
            notify.setMerchantCode(merchant.getMerchantCode())
            notify.setMerchantId(merchant.getMerchantId())
            notify.setMerchantName(merchant.getChannelName())
            notify.setOrderId(dataJSON.getString("out_trade_no"))
            notify.setThirdOrderId("")
            if (dataJSON.getString("status") == ("success")) {
//订单状态progress=>申请中 withdrawing => 下发中 success => 成功 failed => 退回    商户返回出款状态：0成功，1失败,2处理中
                notify.setStatus(0)
            } else if (dataJSON.getString("status") == ("failed")) {
                notify.setStatus(1)
            } else {
                notify.setStatus(2)
            }
        }
        return notify
    }

    public BigDecimal balanceQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        Map<String, String> headParams = new HashMap<String, String>()
        headParams.put("Authorization", "Bearer " + merchantAccount.getPrivateKey())
        headParams.put("Accept", "application/json")

        log.info("WinnerScript_queryBalance_headParams = {}", headParams)
        GlRequestHeader requestHeader = this.getRequestHeard("", "", "", GlActionEnum.PAYMENT_QUERY_BALANCE.getCode(), merchantAccount.getChannelId().toString(), merchantAccount.getChannelName())
        String resStr = okHttpUtil.get(merchantAccount.getPayUrl() + "/api/withdraw/balance", null, requestHeader, headParams)
        log.info("WinnerScript_queryBalance_resStr = {}", resStr)

        JSONObject json = JSON.parseObject(resStr)


        if (json != null && StringUtils.isNotEmpty(json.getString("balance"))) {
            String balanceStr = json.getString("balance").replaceAll(",", "")
            return new BigDecimal(balanceStr)
        }
        return BigDecimal.ZERO
    }


    /**
     * 签名
     *
     * @param value
     * @param accessToken
     * @return
     * @throws NoSuchAlgorithmException
     * @throws InvalidKeyException* @throws UnsupportedEncodingException
     */
    public String getSign(String value, String accessToken)
            throws NoSuchAlgorithmException, InvalidKeyException, UnsupportedEncodingException {
        Base64.Encoder encoder = Base64.getEncoder()
        Mac sha256 = Mac.getInstance("HmacSHA256")
        sha256.init(new SecretKeySpec(accessToken.getBytes("UTF8"), "HmacSHA256"))

        return encoder.encodeToString(sha256.doFinal(value.getBytes("UTF8")))
    }

    /**
     * 获取头部信息
     *
     * @param userId
     * @param userName
     * @param orderId
     * @return
     */
    private GlRequestHeader getRequestHeard(String userId, String userName, String orderId, String code, String channelId, String channelName) {
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(code)
                .channelId(channelId)
                .channelName(channelName)
                .userId(userId)
                .userName(userName)
                .tradeId(orderId)
                .build()
        return requestHeader
    }
}
